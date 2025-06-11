package org.forge.can_t_see;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
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

import java.util.Random;

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

            player.sendSystemMessage(Component.literal("你还是来了，既然你来了，那就不能惧怕了"));
            player.sendSystemMessage(Component.literal("[INFO] 5L2g6IO955yL5Yiw5oiR5Lus5ZCX"));

            if (isFirstLoad) {
                isFirstLoad = false;
                Random random = new Random();

                if (random.nextFloat() < 0.5f) {
                    generateMysterySign(player, world);
                }
            }
        }

        private static void generateMysterySign(Player player, ServerLevel world) {
            BlockPos playerPos = player.blockPosition();
            Random random = new Random();

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
                            sign.setMessage(0, Component.literal("5oiR5Lus5LiA55u05Zyo5L2g6ZmE6L+R"));
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
        }
    }
}