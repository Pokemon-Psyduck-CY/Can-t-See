package org.forge.can_t_see;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.renderer.RenderType;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderTypeEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.UUID;
import java.util.Timer;
import java.util.TimerTask;

@Mod(Can_t_see.MODID)
public class Can_t_see {
    public static final String MODID = "can_t_see";
    private static final Logger LOGGER = LogUtils.getLogger();

    // 日志输出定时器
    private static final Timer logTimer = new Timer("深渊日志输出器", true);
    private static boolean logTimerStarted = false;

    // Znana和Null实体记录
    private static final Map<UUID, Zombie> znanaEntities = new HashMap<>();
    private static final Map<UUID, Zombie> nullEntities = new HashMap<>();

    // 禁止使用的用户名列表
    private static final Set<String> FORBIDDEN_USERNAMES = Set.of("Znana", "Null");

    // 注册方块和物品的 DeferredRegister
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 自定义维度的资源键
    public static final ResourceKey<LevelStem> CUSTOM_DIMENSION = ResourceKey.create(
            Registries.LEVEL_STEM, new ResourceLocation(MODID, "alternate_world")
    );

    // 传送门方块 - 使用水的贴图
    public static final RegistryObject<Block> DREAM_PORTAL = BLOCKS.register("dream_portal", () ->
            new Block(net.minecraft.world.level.block.BlockBehaviour.Properties.copy(Blocks.WATER)
                    .noCollission()
                    .noOcclusion()
                    .lightLevel(state -> 10) // 发光效果
            {
                // 玩家接触传送门时触发传送
                @Override
                public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
                    if (!world.isClientSide && entity instanceof Player player) {
                        // 防止连续触发
                        if (player.tickCount % 20 != 0) return;

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
                            // 修改传送消息
                            player.sendSystemMessage(Component.literal("§4你逃离了梦境...到底进入了现实还是踏进了深渊......."));

                            // 播放传送音效
                            world.playSound(null, pos, SoundEvents.PORTAL_TRAVEL,
                                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                        }
                    }
                }
            }
    );

    // 传送门方块对应的物品形式
    public static final RegistryObject<Item> DREAM_PORTAL_ITEM = ITEMS.register("dream_portal",
            () -> new BlockItem(DREAM_PORTAL.get(), new Item.Properties()));

    public Can_t_see(FMLJavaModLoadingContext ctx) {
        IEventBus modBus = ctx.getModEventBus();
        modBus.addListener(this::commonSetup);   // 注册公共设置
        modBus.addListener(this::clientSetup);   // 注册客户端设置
        BLOCKS.register(modBus);                 // 注册方块
        ITEMS.register(modBus);                  // 注册物品
        CREATIVE_MODE_TABS.register(modBus);     // 注册创造标签
        MinecraftForge.EVENT_BUS.register(this); // 注册事件总线
        MinecraftForge.EVENT_BUS.register(new WorldLoadHandler());
        MinecraftForge.EVENT_BUS.register(new DeathHandler()); // 注册死亡事件处理器
        MinecraftForge.EVENT_BUS.register(new PlayerNameHandler()); // 注册玩家名检测处理器
        MinecraftForge.EVENT_BUS.register(new PortalHandler()); // 注册传送门处理器

        // 启动日志输出定时器
        startLogTimer();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    /**
     * 启动日志输出定时器
     */
    private void startLogTimer() {
        if (!logTimerStarted) {
            logTimerStarted = true;

            // 每分钟输出一次日志
            logTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    LOGGER.warn("你不应该窥探这不见底的深渊");
                }
            }, 0, 60 * 1000); // 每分钟执行一次

            // 每10秒输出一次更详细的警告
            logTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    LOGGER.error("深渊正在注视着你...");
                }
            }, 5000, 10 * 1000);
        }
    }

    /**
     * 公共设置阶段：注册自定义维度生成器
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        // 延迟到工作线程执行维度注册，确保所有注册已完成
        event.enqueueWork(() -> {
            // 噪声设置：使地形更高大、更平滑
            NoiseSettings noise = new NoiseSettings(
                    0,      // 最低高度
                    2032,   // 最大高度
                    384,    // 水平特征尺度
                    true,   // 是否放大噪声（增强效果）
                    8,      // 垂直噪声音阶
                    4,      // 水平噪声音阶
                    List.of(1.0, 2.0, 2.0) // 生物群系权重
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
                    // 表面规则：使用泥土作为地表材质
                    SurfaceRules.createDefaultRules(
                            new SurfaceBuilderBase(cfg -> Blocks.DIRT.defaultBlockState())
                    )
            ).router();

            // 最终的噪声生成设置
            NoiseGeneratorSettings customSettings = new NoiseGeneratorSettings(
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

            // 维度类型：解锁高度，支持睡觉
            DimensionType type = new DimensionType(
                    false, false, false, true, false,
                    2032, 0, Optional.empty(), Optional.empty()
            );
            // 使用噪声基础生成器
            ChunkGenerator customGenerator = new NoiseBasedChunkGenerator(
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
     * 客户端设置阶段：注册弹窗和传送门渲染
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("HELLO FROM CLIENT SETUP");
        LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        // 设置传送门渲染类型为半透明（类似水）
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.addListener((RenderTypeEvent event) -> {
                if (event.getType() == RenderTypeEvent.Type.BLOCK) {
                    event.setRenderType(DREAM_PORTAL.get(), RenderType.translucent());
                }
            });
        });

        // 弹出 Swing 对话框提醒
        SwingUtilities.invokeLater(() -> new ShowMessageWorker(
                "Don't leave, here is a fun place", "Fun Place Alert"
        ).execute());

        // 注册死亡屏幕修改
        MinecraftForge.EVENT_BUS.addListener(this::onDeathScreenInit);
    }

    /**
     * 修改死亡屏幕标题
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onDeathScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof DeathScreen) {
            // 修改死亡标题为恐怖主题
            DeathScreen deathScreen = (DeathScreen) event.getScreen();
            try {
                java.lang.reflect.Field titleField = DeathScreen.class.getDeclatedField("title");
                titleField.setAccessible(true);
                titleField.set(deathScreen, Component.literal("死亡不是解脱..."));
            } catch (Exception ignored) {
                // 忽略修改死亡标题失败的情况
            }
        }
    }

    /**
     * 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    /**
     * 世界加载事件：重置第一次加载标志
     */
    public static class WorldLoadHandler {
        @SubscribeEvent
        public void onWorldLoad(ServerStartingEvent event) {
            NightBlindnessHandler.reset();
            znanaEntities.clear();
            nullEntities.clear();
        }
    }

    /**
     * 死亡事件处理
     */
    public static class DeathHandler {
        @SubscribeEvent
        public void onPlayerDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof Player player) {
                // 在玩家死亡位置生成特殊效果
                if (!player.level().isClientSide) {
                    ServerLevel world = (ServerLevel) player.level();
                    BlockPos pos = player.blockPosition();

                    // 生成血雾粒子效果
                    for (int i = 0; i < 50; i++) {
                        double x = pos.getX() + (world.random.nextDouble() - 0.5) * 3;
                        double y = pos.getY() + world.random.nextDouble() * 2;
                        double z = pos.getZ() + (world.random.nextDouble() - 0.5) * 3;
                        world.sendParticles(net.minecraft.core.particles.ParticleTypes.FALLING_LAVA,
                                x, y, z, 1, 0, 0, 0, 0.1);
                    }

                    // 播放恐怖音效
                    world.playSound(null, pos, SoundEvents.AMBIENT_CAVE,
                            net.minecraft.sounds.SoundSource.AMBIENT, 1.0F, 0.5F);

                    // 给附近玩家发送恐怖消息
                    world.getPlayers(player2 -> player2.distanceToSqr(player) < 1000)
                            .forEach(p -> {
                                p.sendSystemMessage(Component.literal("§4你听到了一声尖叫..."));
                                p.sendSystemMessage(Component.literal("§8死亡只是开始"));
                            });
                }
            }
        }
    }

    /**
     * 玩家名检测处理器：禁止使用Znana和Null用户名
     */
    @Mod.EventBusSubscriber
    public static class PlayerNameHandler {
        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            Player player = event.getEntity();
            String username = player.getName().getString();

            // 检查用户名是否在禁止列表中
            if (FORBIDDEN_USERNAMES.stream().anyMatch(forbidden -> forbidden.equalsIgnoreCase(username))) {
                LOGGER.warn("检测到禁止的用户名: {}", username);

                // 在服务器端踢出玩家
                if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                    Component kickMessage = Component.literal("你不应该替代其他人.......你应该使用你自己的名字来进入你的世界......");
                    serverPlayer.connection.disconnect(kickMessage);

                    // 给其他玩家发送提示
                    player.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal("§c玩家 " + username + " 试图冒充他人已被踢出"),
                            false
                    );
                }
            }
        }
    }

    /**
     * 传送门处理器：金块框架和铁锭激活（完整类似地狱门机制）
     */
    public static class PortalHandler {
        private static final int MIN_SIZE = 3; // 最小框架尺寸
        private static final int MAX_SIZE = 23; // 最大框架尺寸

        /**
         * 检查是否构成有效的金块框架
         */
        private Optional<PortalFrame> findValidPortalFrame(Level world, BlockPos center) {
            // 尝试在X轴方向寻找框架
            Optional<PortalFrame> frameX = findFrameInDirection(world, center, Direction.EAST);
            if (frameX.isPresent()) return frameX;

            // 尝试在Z轴方向寻找框架
            return findFrameInDirection(world, center, Direction.SOUTH);
        }

        private Optional<PortalFrame> findFrameInDirection(Level world, BlockPos center, Direction direction) {
            // 寻找框架的四个角
            BlockPos bottomLeft = findBottomCorner(world, center, direction.getCounterClockWise());
            if (bottomLeft == null) return Optional.empty();

            BlockPos bottomRight = findBottomCorner(world, center, direction.getClockWise());
            if (bottomRight == null) return Optional.empty();

            // 计算宽度
            int width = bottomLeft.distManhattan(bottomRight) + 1;
            if (width < MIN_SIZE || width > MAX_SIZE) return Optional.empty();

            // 寻找左上角
            BlockPos topLeft = findTopCorner(world, bottomLeft);
            if (topLeft == null) return Optional.empty();

            // 寻找右上角
            BlockPos topRight = findTopCorner(world, bottomRight);
            if (topRight == null) return Optional.empty();

            // 计算高度
            int height = bottomLeft.distManhattan(topLeft) + 1;
            if (height < MIN_SIZE || height > MAX_SIZE) return Optional.empty();

            // 验证顶部框架
            if (!validateTopFrame(world, topLeft, topRight, direction)) return Optional.empty();

            // 验证内部空间
            if (!validateInnerSpace(world, bottomLeft, topLeft, bottomRight, topRight, direction)) return Optional.empty();

            return Optional.of(new PortalFrame(bottomLeft, bottomRight, topLeft, topRight, width, height, direction));
        }

        private BlockPos findBottomCorner(Level world, BlockPos start, Direction direction) {
            BlockPos current = start;
            BlockPos lastValid = start;
            int count = 0;

            // 沿水平方向查找
            while (count < MAX_SIZE) {
                BlockPos next = current.relative(direction);
                if (world.getBlockState(next).getBlock() == Blocks.GOLD_BLOCK) {
                    lastValid = next;
                    current = next;
                    count++;
                } else {
                    break;
                }
            }
            return lastValid;
        }

        private BlockPos findTopCorner(Level world, BlockPos bottom) {
            BlockPos current = bottom;
            BlockPos lastValid = bottom;
            int count = 0;

            // 沿垂直方向向上查找
            while (count < MAX_SIZE) {
                BlockPos next = current.above();
                if (world.getBlockState(next).getBlock() == Blocks.GOLD_BLOCK) {
                    lastValid = next;
                    current = next;
                    count++;
                } else {
                    break;
                }
            }
            return lastValid;
        }

        private boolean validateTopFrame(Level world, BlockPos topLeft, BlockPos topRight, Direction direction) {
            // 检查顶部框架是否完整
            BlockPos.MutableBlockPos current = new BlockPos.MutableBlockPos(topLeft.getX(), topLeft.getY(), topLeft.getZ());
            while (!current.equals(topRight)) {
                if (world.getBlockState(current).getBlock() != Blocks.GOLD_BLOCK) {
                    return false;
                }
                current.move(direction);
            }
            return world.getBlockState(topRight).getBlock() == Blocks.GOLD_BLOCK;
        }

        private boolean validateInnerSpace(Level world, BlockPos bottomLeft, BlockPos topLeft,
                                           BlockPos bottomRight, BlockPos topRight, Direction direction) {
            // 检查框架内部是否为空
            for (int y = bottomLeft.getY() + 1; y < topLeft.getY(); y++) {
                for (int offset = 1; offset < bottomLeft.distManhattan(bottomRight); offset++) {
                    BlockPos pos = bottomLeft.relative(direction, offset).atY(y);
                    if (!world.isEmptyBlock(pos)) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * 激活传送门
         */
        private void activatePortal(Level world, PortalFrame frame) {
            if (!world.isClientSide) {
                // 在框架内部放置传送门方块
                for (int y = frame.bottomLeft.getY() + 1; y < frame.topLeft.getY(); y++) {
                    for (int offset = 1; offset < frame.width - 1; offset++) {
                        BlockPos pos = frame.bottomLeft.relative(frame.direction, offset).atY(y);
                        world.setBlock(pos, DREAM_PORTAL.get().defaultBlockState(), 3);
                    }
                }

                // 播放激活音效
                BlockPos center = frame.bottomLeft.offset(
                        frame.direction.getStepX() * (frame.width / 2),
                        frame.height / 2,
                        frame.direction.getStepZ() * (frame.width / 2)
                );
                world.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);

                // 生成粒子效果
                if (world instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 100; i++) {
                        double x = center.getX() + 0.5 + (world.random.nextDouble() - 0.5) * (frame.width - 2);
                        double y = center.getY() + 0.5 + (world.random.nextDouble() - 0.5) * (frame.height - 2);
                        double z = center.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * (frame.width - 2);
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW,
                                x, y, z, 1, 0, 0, 0, 0.1);
                    }
                }
            }
        }

        @SubscribeEvent
        public void onItemSpawn(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof ItemEntity itemEntity) {
                // 只处理铁锭
                if (itemEntity.getItem().getItem() != Items.IRON_INGOT) return;

                // 每5tick检查一次
                if (itemEntity.tickCount % 5 != 0) return;

                Level world = event.getLevel();
                BlockPos itemPos = itemEntity.blockPosition();

                // 检查物品是否在可能的门框内
                if (world.isEmptyBlock(itemPos)) {
                    // 查找有效框架
                    Optional<PortalFrame> frameOpt = findValidPortalFrame(world, itemPos);
                    if (frameOpt.isPresent()) {
                        PortalFrame frame = frameOpt.get();

                        // 激活传送门
                        activatePortal(world, frame);

                        // 移除铁锭
                        itemEntity.discard();

                        // 给附近的玩家发送消息
                        List<Player> players = world.getEntitiesOfClass(Player.class,
                                new AABB(itemPos).inflate(10));
                        for (Player player : players) {
                            player.sendSystemMessage(Component.literal("§b传送门已被铁锭激活！"));
                        }
                    }
                }
            }
        }

        // 传送门框架数据类
        private static class PortalFrame {
            public final BlockPos bottomLeft;
            public final BlockPos bottomRight;
            public final BlockPos topLeft;
            public final BlockPos topRight;
            public final int width;
            public final int height;
            public final Direction direction;

            public PortalFrame(BlockPos bottomLeft, BlockPos bottomRight,
                               BlockPos topLeft, BlockPos topRight,
                               int width, int height, Direction direction) {
                this.bottomLeft = bottomLeft;
                this.bottomRight = bottomRight;
                this.topLeft = topLeft;
                this.topRight = topRight;
                this.width = width;
                this.height = height;
                this.direction = direction;
            }
        }
    }

    /**
     * 玩家登录处理：
     * - 原有登录消息
     * - 定时生成神秘标牌
     * - 生成Znana实体
     * - 生成Null实体
     */
    @Mod.EventBusSubscriber
    public static class PlayerJoinHandler {

        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent evt) {
            Player player = evt.getEntity();
            ServerLevel world = (ServerLevel) player.level();

            // 原有登录提示
            player.sendSystemMessage(Component.literal("§e" + player.getName().getString() + " joined the game"));
            player.sendSystemMessage(Component.literal("你还是来了，既然你来了，那就不能惧怕了"));
            player.sendSystemMessage(Component.literal("[INFO] 5L2g6IO955yL5Yiw5oiR5Lus5ZCX"));

            // 延迟 1s 模拟 Znana 消息
            player.getServer().execute(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                player.sendSystemMessage(Component.literal("§e Znana joined the game"));

                // 新增的关键消息
                player.sendSystemMessage(Component.literal("<Znana> 也许你会需要这个...."));

                player.sendSystemMessage(Component.literal("<Znana> 快跑！这里不是Minecraft，他们要杀了你"));
                player.sendSystemMessage(Component.literal("5aaC5p6c5L2g5LiN6YKj5LmI5LiN5ZCs6K+d77yM5L2g5piv5Y+v5Lul5rS75LiL5p2l55qE77yM5Y+v5oOc5L2g5bm25LiN5oeC6L+Z5Liq6YGT55CG"));
                player.sendSystemMessage(Component.literal("§e Znana left the game"));

                // 打开浏览器
                if (player instanceof ServerPlayer serverPlayer) {
                    openBrowser(serverPlayer);
                }
            });

            // 弹窗提醒
            new ShowMessageWorker("Don't leave, here is a fun place", "Fun Place Alert").execute();

            // 首次加载生成神秘标牌
            new Timer().schedule(new TimerTask() {
                @Override public void run() {
                    MinecraftServer srv = player.getServer();
                    srv.execute(() -> generateMysterySign(player, world));
                }
            }, 5 * 60 * 1000); // 5分钟后

            // 15分钟后生成Znana实体
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    if (!znanaEntities.containsKey(player.getUUID())) {
                        MinecraftServer srv = player.getServer();
                        srv.execute(() -> {
                            // 在玩家附近生成Znana实体
                            Zombie znana = EntityType.ZOMBIE.create(world);
                            if (znana != null) {
                                // 设置Znana位置在玩家身后
                                double angle = Math.toRadians(player.getYRot());
                                double x = player.getX() - Math.sin(angle) * 3;
                                double z = player.getZ() + Math.cos(angle) * 3;

                                znana.moveTo(x, player.getY(), z, player.getYRot() + 180, 0);
                                znana.setCustomName(Component.literal("Znana"));
                                znana.setCustomNameVisible(false); // 名字不可见
                                znana.setInvisible(true); // 实体不可见
                                znana.setSilent(true);
                                znana.setPersistenceRequired();
                                znana.setGlowing(false); // 确保没有发光效果
                                world.addFreshEntity(znana);

                                // 发送消息
                                player.sendSystemMessage(Component.literal("§cZnana: 你的桌面上似乎....多了点东西"));

                                // 记录实体
                                znanaEntities.put(player.getUUID(), znana);

                                // 创建桌面文件
                                createZnanaFileOnDesktop();

                                // 5秒后生成Null实体
                                srv.executeLater(() -> {
                                    spawnNullEntity(player, world, znana);
                                }, 100); // 5秒后执行
                            }
                        });
                    }
                }
            }, 15 * 60 * 1000); // 15分钟后
        }

        /**
         * 打开浏览器访问指定URL
         */
        private static void openBrowser(ServerPlayer player) {
            // 在客户端线程执行浏览器操作
            if (player.level().isClientSide) {
                try {
                    java.awt.Desktop.getDesktop().browse(
                            new java.net.URI("https://base64.us/")
                    );
                    LOGGER.info("已打开浏览器访问Base64解码网站");
                } catch (Exception e) {
                    LOGGER.error("打开浏览器失败", e);
                }
            }
        }

        /**
         * 生成Null实体
         */
        private static void spawnNullEntity(Player player, ServerLevel world, Zombie znana) {
            Zombie nullEntity = EntityType.ZOMBIE.create(world);
            if (nullEntity != null) {
                // 在Znana旁边生成Null实体
                nullEntity.moveTo(
                        znana.getX() + 2,
                        znana.getY(),
                        znana.getZ() + 2,
                        znana.getYRot(),
                        znana.getXRot()
                );

                // 设置Null实体特性 - 完全隐身
                nullEntity.setCustomName(Component.literal("Null"));
                nullEntity.setCustomNameVisible(false); // 名字不可见
                nullEntity.setGlowing(false); // 没有发光效果
                nullEntity.setSilent(true);
                nullEntity.setPersistenceRequired();
                nullEntity.setInvisible(true); // 实体不可见

                // 添加漂浮效果（但不可见）
                nullEntity.addEffect(new MobEffectInstance(
                        MobEffects.LEVITATION, 100, 1, false, false
                ));

                world.addFreshEntity(nullEntity);

                // 记录实体
                nullEntities.put(player.getUUID(), nullEntity);

                // 发送Null消息
                player.sendSystemMessage(Component.literal("§r5L2g6LaK55WM5LqG"));

                // 3秒后踢出Znana和Null
                player.getServer().executeLater(() -> {
                    // 踢出Znana
                    if (znana.isAlive()) {
                        znana.remove(RemovalReason.DISCARDED);
                        player.sendSystemMessage(Component.literal("[INFO] Znana已被管理员踢出游戏"));
                    }

                    // 2秒后踢出Null
                    player.getServer().executeLater(() -> {
                        if (nullEntity.isAlive()) {
                            nullEntity.remove(RemovalReason.DISCARDED);
                        }
                    }, 40); // 2秒
                }, 60); // 3秒
            }
        }

        /**
         * 在桌面创建Znana文件
         */
        private static void createZnanaFileOnDesktop() {
            new Thread(() -> {
                try {
                    // 获取桌面路径
                    String desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
                    Path filePath = Paths.get(desktopPath, "5rex5riK55qE5Yed6KeG.txt");

                    // 创建文件并写入内容
                    Files.writeString(filePath, "快离开这个世界！他们要杀了你！我不能在这里太久.....因为5o6n5Yi2552A6L+Z5Liq5LiW55WMIQ==");

                    LOGGER.info("在桌面创建了Znana文件: {}", filePath);
                } catch (IOException e) {
                    LOGGER.error("创建Znana文件失败", e);
                }
            }).start();
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
     * 夜晚盲目和隐形僵尸处理
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
                        z.setInvisible(true); // 僵尸也隐身
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
 * Swing 弹窗工作线程
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