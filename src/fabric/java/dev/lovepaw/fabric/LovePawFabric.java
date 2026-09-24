package dev.lovepaw.fabric;

import dev.lovepaw.LovePaw;
import dev.lovepaw.net.LovePawPayloads;
import dev.lovepaw.net.ServerPetState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Fabric entry point, both sides. The server half only relays who wears what;
 * it never simulates or renders anything.
 */
public final class LovePawFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        LovePaw.init(FabricLoader.getInstance().getConfigDir());

        PayloadTypeRegistry.playC2S().register(LovePawPayloads.SelectPayload.TYPE, LovePawPayloads.SelectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LovePawPayloads.HelloPayload.TYPE, LovePawPayloads.HelloPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LovePawPayloads.StatePayload.TYPE, LovePawPayloads.StatePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(LovePawPayloads.SelectPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            LovePawPayloads.Entry entry = ServerPetState.select(player.getUUID(), payload.petId(), payload.contentHash());
            if (entry != null) {
                broadcast(player.server, new LovePawPayloads.StatePayload(List.of(entry)));
            }
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> LovePaw.onServerStarting());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerPetState.clear());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ServerPlayNetworking.send(player, new LovePawPayloads.HelloPayload(LovePaw.PROTOCOL_VERSION));
            List<LovePawPayloads.Entry> snapshot = ServerPetState.snapshot();
            if (!snapshot.isEmpty()) {
                ServerPlayNetworking.send(player, new LovePawPayloads.StatePayload(snapshot));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID owner = handler.getPlayer().getUUID();
            LovePawPayloads.Entry entry = ServerPetState.forget(owner);
            if (entry != null) {
                broadcast(server, new LovePawPayloads.StatePayload(List.of(entry)));
            }
        });
    }

    private static void broadcast(MinecraftServer server, LovePawPayloads.StatePayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, LovePawPayloads.StatePayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
