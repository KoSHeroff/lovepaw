package dev.lovepaw.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lovepaw.LovePaw;
import dev.lovepaw.client.PetManager;
import dev.lovepaw.client.PetResourceLoader;
import dev.lovepaw.client.screen.PetSelectScreen;
import dev.lovepaw.config.ClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client-side wiring: assets, keybind, ticking and drawing. */
public final class LovePawNeoForgeClient {
    static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.lovepaw.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.lovepaw");

    private LovePawNeoForgeClient() {
    }

    @EventBusSubscriber(modid = LovePaw.MOD_ID, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            ClientConfig.load(FMLPaths.CONFIGDIR.get());
            PetManager.get().setSender(PacketDistributor::sendToServer);
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new PetResourceLoader(FMLPaths.GAMEDIR.get()));
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MENU);
        }
    }

    @EventBusSubscriber(modid = LovePaw.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_MENU.consumeClick()) {
                minecraft.setScreen(new PetSelectScreen());
            }
            PetManager.get().tick(minecraft);
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            PetManager.get().render(
                    event.getPoseStack(),
                    minecraft.renderBuffers().bufferSource(),
                    event.getCamera(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
            minecraft.renderBuffers().bufferSource().endBatch();
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            PetManager.get().onDisconnect();
        }
    }
}
