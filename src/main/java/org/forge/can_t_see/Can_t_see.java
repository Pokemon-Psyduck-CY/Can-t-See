package org.forge.can_t_see;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.NoiseSamplingSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.surfacebuilders.SurfaceBuilder;
import net.minecraft.world.level.levelgen.surfacebuilders.SurfaceBuilderBase;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

@Mod(Can_t_see.MODID)
public class Can_t_see {
    public static final String MODID = "can_t_see";
    private static final Logger LOGGER = LogUtils.getLogger();

    // 注册方块和物品的 DeferredRegister
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 自定义维度的资源键
    public static final ResourceKey<LevelStem> CUSTOM_DIMENSION = ResourceKey.create(
            Registries.LEVEL_STEM, new ResourceLocation(MODID, "alternate_world")
    );

    // 泥土传送门方块和金锭激活器注册
    public static final RegistryObject<net.minecraft.world.level.block.Block> DIRT_PORTAL = BLOCKS.register("dirt_portal", () ->
            new net.minecraft.world.level.block.Block(
                    net.minecraft.world.level.block.BlockBehaviour.Properties.copy(Blocks.DIRT).noOcclusion()
            ) {
                // 玩家右键交互触发传送
                @Override
                public InteractionResult use(BlockState state, net.minecraft.world.level.Level world,
                                             BlockPos pos, Player player, InteractionHand hand,
                                             net.minecraft.world.phys.BlockHitResult hit) {
                    if (!world.isClientSide) {
                        ServerLevel target = ((ServerLevel) world).getServer().getLevel(CUSTOM_DIMENSION);
                        if (target != null) {
                            // 将玩家上传送到自定义维度的指定高度
                            player.teleportTo(
                                    target,
                                    pos.getX() + 0.5,
                                    target.getMinBuildHeight() + 80,
                                    pos.getZ() + 0.5,
                                    player.getYRot(), player.getXRot()
                            );
                            player.sendSystemMessage(Component.literal("§b已传送到泥土维度"));
                        }
                    }
                    return InteractionResult.SUCCESS;
                }
            }
    );
    public static final RegistryObject<Item> GOLD_ACTIVATOR = ITEMS.register("gold_activator", () ->
            new Item(new Item.Properties().tab(CreativeModeTab.TAB_MISC))
    );

    // 自定义地形生成器设置
    private static NoiseGeneratorSettings customSettings;
    private static ChunkGenerator customGenerator;
    private static boolean isFirstLoad = true;

    public Can_t_see(FMLJavaModLoadingContext ctx) {
        IEventBus modBus = ctx.getModEventBus();
        modBus.addListener(this::commonSetup);   // 注册公共设置
        modBus.addListener(this::clientSetup);   // 注册客户端设置
        BLOCKS.register(modBus);                 // 注册方块
        ITEMS.register(modBus);                  // 注册物品
        CREATIVE_MODE_TABS.register(modBus);     // 注册创造标签
        MinecraftForge.EVENT_BUS.register(this); // 注册事件总线
        MinecraftForge.EVENT_BUS.register(new WorldLoadHandler());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    /**
     * 公共设置阶段：注册自定义维度生成器
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        // 延迟到工作线程执行维度注册，确保所有注册已完成
        event.enqueueWork(() -> {
            // 噪声设置：使地形更高大、更平滑 嗯嗯嗯，这一段要是没法编译那大概就是小猪手发力了吧
            NoiseSettings noise = new NoiseSettings(
                    0,      // 最低高度
                    2032,   // 最大高度
                    384,    // 水平特征尺度
                    true,   // 是否放大噪声（增强效果）
                    8,      // 垂直噪声音阶
                    4,      // 水平噪声音阶
                    List.of(1.0, 2.0, 2.0) // 什么破1.20.1语法，我在1.12.2上都能跑
            );

            // 采样设置：控制噪声在XYZ轴的缩放和深度噪声
            NoiseSamplingSettings sampling = new NoiseSamplingSettings(
                    1.0,  // XZ缩放
                    80.0, // Y缩放（更高的山峰）
                    160.0,// 深度噪声尺度
                    0.3,  // 随机偏移
                    0.0,  // 随机密度因子
                    Optional.empty()
            );

            // 噪声路由：组合不同噪声通道
            NoiseRouter router = new NoiseRouterData(
                    ImprovedNoise.NOISE_GENERATOR,
                    noise,
                    sampling,
                    // 表面规则：使用泥土作为地表材质，嗯，大概只有薄薄的一层吧（
                    SurfaceRules.createDefaultRules(
                            new SurfaceBuilderBase(cfg -> Blocks.DIRT.defaultBlockState())
                    )
            ).router();

            // 最终的噪声生成设置 2032格高度实验
            customSettings = new NoiseGeneratorSettings(
                    noise,
                    ImprovedNoise.NOISE_GENERATOR,
                    SurfaceBuilder.DEFAULT,
                    SurfaceBuilder.DEFAULT.config(),
                    Blocks.BEDROCK.defaultBlockState(),
                    Blocks.AIR.defaultBlockState(),
                    2032,
                    router,
                    List.of(),
                    false,
                    0L,
                    true,
                    false
            );
            // 注册噪声设置到游戏
            Registry.register(
                    BuiltInRegistries.NOISE_SETTINGS,
                    new ResourceLocation(MODID, "dirt_smooth"),
                    customSettings
            );

            // 维度类型：解锁高度，支持躺平摆烂（睡觉）
            DimensionType type = new DimensionType(
                    false, false, false, true, false,
                    2032, 0, Optional.empty(), Optional.empty()
            );
            // 使用噪声基础生成器
            customGenerator = new NoiseBasedChunkGenerator(
                    customSettings,
                    SurfaceBuilder.DEFAULT.configured(SurfaceBuilder.DEFAULT.config())
            );
            // LevelStem：绑定维度类型和生成器
            LevelStem stem = new LevelStem(type, customGenerator);
            // 注册自定义维度
            Registry.register(
                    BuiltInRegistries.LEVEL_STEM,
                    new ResourceLocation(MODID, "alternate_world"),
                    stem
            );
        });
    }

    /**
     * 客户端设置阶段：注册弹窗
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("HELLO FROM CLIENT SETUP");
        LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        // 弹出 Swing 对话框提醒
        SwingUtilities.invokeLater(() -> new ShowMessageWorker(
                "Don't leave, here is a fun place", "Fun Place Alert"
        ).execute());
    }

    /**
     * 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    /**
     * 世界加载事件：重置第一次加载世界 那咋了，就是爱重置
     */
    public static class WorldLoadHandler {
        @SubscribeEvent
        public void onWorldLoad(ServerStartingEvent event) {
            isFirstLoad = true;
            NightBlindnessHandler.reset();
        }
    }

    /**
     * 玩家登录处理：
     * - 原有登录消息
     * - 定时生成神秘告标牌
     * - 第三天传送到现实维度
     */
    @Mod.EventBusSubscriber
    public static class PlayerJoinHandler {
        private static final AtomicBoolean teleported = new AtomicBoolean(false);

        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent evt) {
            Player player = evt.getEntity();
            ServerLevel world = (ServerLevel) player.level();

            // 原有登录提示
            player.sendSystemMessage(Component.literal("§e" + player.getName().getString() + " joined the game"));
            player.sendSystemMessage(Component.literal("你还是来了，既然你来了，那就不能惧怕了"));
            player.sendSystemMessage(Component.literal("[INFO] 5L2g6IO955yL5Yiw5oiR5Lus5ZCX"));

            // 延迟 1s 发送 Znana 消息
            player.getServer().execute(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                player.sendSystemMessage(Component.literal("§e Znana joined the game"));
                player.sendSystemMessage(Component.literal("<Znana> 快跑！这里不是Minecraft，他们要杀了你"));
                player.sendSystemMessage(Component.literal("5aaC5p6c5L2g5LiN6YKj5LmI5LiN5ZCs6K+d77yM5L2g5piv5Y+v5Lul5rS75LiL5p2l55qE77yM5Y+v5oOc5L2g5bm25LiN5oeC6L+Z5Liq6YGT55CG"));
                player.sendSystemMessage(Component.literal("§e Znana left the game"));
            });

            // 弹窗提醒
            new ShowMessageWorker("Don't leave, here is a fun place", "Fun Place Alert").execute();

            // 首次加载后 5 分钟生成神秘告标牌
            if (isFirstLoad) {
                isFirstLoad = false;
                new Timer().schedule(new TimerTask() {
                    @Override public void run() {
                        MinecraftServer srv = player.getServer();
                        srv.execute(() -> generateMysterySign(player, world));
                    }
                }, 5 * 60 * 1000);
            }

            // 第三天传送
            new Timer().schedule(new TimerTask() {
                @Override public void run() {
                    if (teleported.compareAndSet(false, true)) {
                        MinecraftServer srv = player.getServer();
                        srv.execute(() -> {
                            ServerLevel d = srv.getLevel(CUSTOM_DIMENSION);
                            if (d != null) {
                                player.teleportTo(d, 0.5, d.getMinBuildHeight() + 80, 0.5,
                                        player.getYRot(), player.getXRot());
                                player.sendSystemMessage(Component.literal("§b已传送到泥土维度"));
                            }
                        });
                    }
                }
            }, (long)(2 * 24000 / 20 + 20));
        }

        // 生成神秘指示牌方法
        private static void generateMysterySign(Player player, ServerLevel world) {
            BlockPos p = player.blockPosition();
            Random r = new Random();
            int radius = 10 + r.nextInt(6);
            double angle = r.nextDouble() * Math.PI * 2;
            int x = p.getX() + (int)(Math.cos(angle) * radius);
            int z = p.getZ() + (int)(Math.sin(angle) * radius);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, world.getMaxBuildHeight(), z);
            while (m.getY() > world.getMinBuildHeight()) {
                m.move(Direction.DOWN);
                if (world.getBlockState(m).isSolid() && world.isEmptyBlock(m.above())) {
                    BlockPos sp = m.above();
                    Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
                    if (world.setBlock(sp, Blocks.OAK_WALL_SIGN.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.WallSignBlock.FACING, d), 3)) {
                        BlockEntity be = world.getBlockEntity(sp);
                        if (be instanceof SignBlockEntity sign) {
                            Component[] lines = {
                                    Component.literal("5oiR5Lus5LiA55u05Zyo5L2g6ZmE6L+R"),
                                    Component.literal(""), Component.literal(""), Component.literal("")
                            };
                            sign.setText(new SignText(lines, lines, DyeColor.BLACK, false), false);
                            sign.setChanged();
                        }
                        player.sendSystemMessage(Component.literal("也许附近出现了什么...."));
                        return;
                    }
                }
            }
        }
    }

    /**
     * 夜晚失明和隐形僵尸处理
     */
    @Mod.EventBusSubscriber(modid = MODID)
    public static class NightBlindnessHandler {
        private static boolean firstNightHandled = false;
        private static boolean entitySpawned = false;
        private static boolean secondDayMessageSent = false;
        private static final Map<Zombie, UUID> spawned = new HashMap<>();
        private static final Map<Zombie, BlockPos> lastPos = new HashMap<>();

        @SubscribeEvent public static void onWorldTick(TickEvent.WorldTickEvent e) {
            if (e.phase != Phase.END || e.world.isClientSide()) return;
            ServerLevel w = (ServerLevel) e.world;
            long full = w.getDayTime();
            long t = full % 24000;
            // 首个夜晚触发盲目和僵尸生成
            if (!firstNightHandled && t >= 13000) {
                firstNightHandled = true;
                int dur = (int)(24000 - t);
                w.players().forEach(p -> p.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS, dur, 0, false, false
                )));
                if (!entitySpawned && !w.players().isEmpty()) {
                    Player tgt = w.players().get(0);
                    Zombie z = EntityType.ZOMBIE.create(w);
                    if (z != null) {
                        z.moveTo(tgt.getX(), tgt.getY(), tgt.getZ(), tgt.getYRot(), tgt.getXRot());
                        z.setInvisible(true);
                        z.setSilent(true);
                        z.setPersistenceRequired();
                        z.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25);
                        w.addFreshEntity(z);
                        spawned.put(z, tgt.getUUID());
                        lastPos.put(z, z.blockPosition());
                        entitySpawned = true;
                    }
                }
            }
            // 夜晚僵尸跟踪玩家并播放脚步声
            if (entitySpawned && t >= 13000) {
                for (Zombie z : spawned.keySet()) {
                    Player p = w.getPlayerByUUID(spawned.get(z));
                    if (z.isAlive() && p != null) {
                        ((PathNavigation)z.getNavigation()).moveTo(p, 1.0);
                        BlockPos cur = z.blockPosition();
                        BlockPos prev = lastPos.get(z);
                        if (!cur.equals(prev)) {
                            SoundType st = w.getBlockState(cur).getSoundType();
                            w.playSound(null, cur, st.getStepSound(),
                                    net.minecraft.sounds.SoundSource.NEUTRAL,
                                    st.getVolume(), st.getPitch());
                            lastPos.put(z, cur);
                        }
                    }
                }
            }
            // 白天移除僵尸
            if (entitySpawned && t < 12000) {
                new ArrayList<>(spawned.keySet()).forEach(z -> { if (z.isAlive()) z.remove(RemovalReason.DISCARDED); });
                spawned.clear(); lastPos.clear(); entitySpawned = false;
            }
            // 第二天早晨发送恐吓信息
            if (!secondDayMessageSent && full >= 24000 && t < 12000) {
                w.players().forEach(p -> p.sendSystemMessage(Component.literal("我会杀了你")));
                w.players().forEach(p -> p.sendSystemMessage(Component.literal("5L2g6LeR5LiN5o6J5LqG")));
                secondDayMessageSent = true;
            }
        }

        // 重置状态
        public static void reset() {
            firstNightHandled = false;
            entitySpawned = false;
            secondDayMessageSent = false;
            spawned.clear(); lastPos.clear();
        }
    }
}

/**
 * 你妈的我能不能不注释了
 */
class ShowMessageWorker extends SwingWorker<Void, Void> {
    private final String message, title;
    public ShowMessageWorker(String message, String title) {
        this.message = message;
        this.title = title;
    }
    @Override protected Void doInBackground() throws Exception {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(1000);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null, message, title, JOptionPane.INFORMATION_MESSAGE
            ));
        }
        return null;
    }
}
