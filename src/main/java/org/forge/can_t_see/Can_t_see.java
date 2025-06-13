package org.forge.can_t_see;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
import net.minecraft.util.RandomSource;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

@Mod(Can_t_see.MODID)
public class Can_t_see {
    public static final String MODID = "can_t_see";
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    private static boolean isFirstLoad = true;

    public Can_t_see(FMLJavaModLoadingContext ctx) {
        IEventBus modEventBus = ctx.getModEventBus();

        modEventBus.addListener(this::commonSetup);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new WorldLoadHandler());

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        if (Config.logDirtBlock) {
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
        }

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);
        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    public static class WorldLoadHandler {
        @SubscribeEvent
        public void onWorldLoad(ServerStartingEvent event) {
            isFirstLoad = true;
        }
    }

    @Mod.EventBusSubscriber
    public static class PlayerJoinHandler {
        @SubscribeEvent
        public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
            Player player = event.getEntity();
            ServerLevel world = (ServerLevel) player.level();
            player.sendSystemMessage(Component.literal("§e" + player.getName().getString()).append(" joined the game"));
            player.sendSystemMessage(Component.literal("你还是来了，既然你来了，那就不能惧怕了"));
            player.sendSystemMessage(Component.literal("[INFO] 5L2g6IO955yL5Yiw5oiR5Lus5ZCX"));

            // 使用异步任务调度器来延迟发送消息
            player.getServer().execute(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                player.sendSystemMessage(Component.literal("§e Znana joined the game"));
                player.sendSystemMessage(Component.literal("<Znana> 快跑！这里不是Minecraft，他们要杀了你"));
                player.sendSystemMessage(Component.literal("5aaC5p6c5L2g5LiN6YKj5LmI5LiN5ZCs6K+d77yM5L2g5piv5Y+v5Lul5rS75LiL5p2l55qE77yM5Y+v5oOc5L2g5bm25LiN5oeC6L+Z5Liq6YGT55CG"));
                player.sendSystemMessage(Component.literal("§e Znana left the game"));
            });

            // 使用SwingWorker来非阻塞地弹出提示
            ShowMessageWorker showMessageWorker = new ShowMessageWorker("Don't leave, here is a fun place", "Fun Place Alert");
            showMessageWorker.execute();

            if (isFirstLoad) {
                isFirstLoad = false;
                // generateMysterySign(player, world);
            }
        }

        private static void generateMysterySign(Player player, ServerLevel world) {
            BlockPos playerPos = player.blockPosition();
            RandomSource random = RandomSource.create();

            int radius = 10 + random.nextInt(6);
            double angle = random.nextDouble() * Math.PI * 2;
            int x = playerPos.getX() + (int)(Math.cos(angle) * radius);
            int z = playerPos.getZ() + (int)(Math.sin(angle) * radius);

            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, world.getMaxBuildHeight(), z);
            while (pos.getY() > world.getMinBuildHeight()) {
                pos.move(Direction.DOWN);
                BlockState state = world.getBlockState(pos);

                if (state.isSolid() && world.isEmptyBlock(pos.above())) {
                    BlockPos signPos = pos.above();
                    Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);

                    BlockState signState = Blocks.OAK_WALL_SIGN.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.WallSignBlock.FACING, direction);

                    if (world.setBlock(signPos, signState, 3)) {
                        BlockEntity blockEntity = world.getBlockEntity(signPos);
                        if (blockEntity instanceof SignBlockEntity sign) {
                            Component[] lines = {
                                    Component.literal("5oiR5Lus5LiA55u05Zyo5L2g6ZmE6L+R"),
                                    Component.literal(""),
                                    Component.literal(""),
                                    Component.literal("")
                            };
                            SignText signText = new SignText(lines, lines, DyeColor.BLACK, false);
                            sign.setText(signText, false);
                            sign.setChanged();
                        }

                        player.sendSystemMessage(Component.literal("也许附近出现了什么...."));
                        return;
                    }
                }

            }
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

            // 确保Swing EDT已经准备好
            SwingUtilities.invokeLater(() -> {
                ShowMessageWorker showMessageWorker = new ShowMessageWorker("Don't leave, here is a fun place", "Fun Place Alert");
                showMessageWorker.execute();
            });
        }
    }
}

class ShowMessageWorker extends SwingWorker<Void, Void> {
    private final String message;
    private final String title;

    public ShowMessageWorker(String message, String title) {
        this.message = message;
        this.title = title;
    }

    @Override
    protected Void doInBackground() throws Exception {
        // 这里不需要执行任何后台任务，仅用于延迟
        for (int i = 0; i < 5; i++) {
            Thread.sleep(1000);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE);
            });
        }
        return null;
    }
}
