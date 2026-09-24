package dev.lovepaw.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lovepaw.LovePaw;
import dev.lovepaw.client.PetManager;
import dev.lovepaw.client.screen.PetSelectScreen;
import dev.lovepaw.config.ClientConfig;
import dev.lovepaw.net.LovePawPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import org.lwjgl.glfw.GLFW;

public final class LovePawFabricClient implements ClientModInitializer {
    private static KeyMapping openMenu;

    @Override
    public void onInitializeClient() {
        ClientConfig.load(FabricLoader.getInstance().getConfigDir());

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new FabricPetResourceLoader(FabricLoader.getInstance().getGameDir()));

        PetManager.get().setSender(ClientPlayNetworking::send);

        ClientPlayNetworking.registerGlobalReceiver(LovePawPayloads.HelloPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    LovePaw.LOGGER.info("Server speaks LovePaw (protocol {})", payload.protocolVersion());
                    PetManager.get().onServerHello();
                }));

        ClientPlayNetworking.registerGlobalReceiver(LovePawPayloads.StatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> PetManager.get().onStateEntries(payload.entries())));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PetManager.get().onDisconnect());

        openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.lovepaw.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.lovepaw"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenu.consumeClick()) {
                client.setScreen(new PetSelectScreen());
            }
            PetManager.get().tick(client);
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.matrixStack() == null) {
                return;
            }
            PetManager.get().render(
                    context.matrixStack(),
                    context.consumers(),
                    context.camera(),
                    context.frustum(),
                    context.tickCounter().getGameTimeDeltaPartialTick(false));
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        });
    }
}
