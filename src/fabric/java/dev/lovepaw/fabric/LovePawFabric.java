package dev.lovepaw.fabric;

import dev.lovepaw.LovePaw;
import dev.lovepaw.net.LovePawPayloads;
import dev.lovepaw.net.PetContentRelay;
import dev.lovepaw.net.ServerCourier;
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
    /** The running server, for sending a pet to a player who is not in hand. */
    private static MinecraftServer server;

    private static final PetContentRelay RELAY = new PetContentRelay(new ServerCourier(
            () -> server,
            (player, payload) -> {
                if (ServerPlayNetworking.canSend(player, payload.type())) {
                    ServerPlayNetworking.send(player, payload);
                }
            }));

    @Override
    public void onInitialize() {
        LovePaw.init(FabricLoader.getInstance().getConfigDir());

        PayloadTypeRegistry.playC2S().register(LovePawPayloads.SelectPayload.TYPE, LovePawPayloads.SelectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LovePawPayloads.HelloPayload.TYPE, LovePawPayloads.HelloPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LovePawPayloads.StatePayload.TYPE, LovePawPayloads.StatePayload.CODEC);

        // Pets travel in both directions: a client asks for one, and is asked
        // in turn for the one it is wearing.
        PayloadTypeRegistry.playC2S().register(
                LovePawPayloads.PetRequestPayload.TYPE, LovePawPayloads.PetRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                LovePawPayloads.PetUploadPayload.TYPE, LovePawPayloads.PetUploadPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                LovePawPayloads.PetNeededPayload.TYPE, LovePawPayloads.PetNeededPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                LovePawPayloads.PetDeliveryPayload.TYPE, LovePawPayloads.PetDeliveryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                LovePawPayloads.PetMissingPayload.TYPE, LovePawPayloads.PetMissingPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(LovePawPayloads.SelectPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            LovePawPayloads.Entry entry = ServerPetState.select(player.getUUID(), payload.petId(), payload.contentHash());
            if (entry != null) {
                broadcast(player.server, new LovePawPayloads.StatePayload(List.of(entry)));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(LovePawPayloads.PetRequestPayload.TYPE, (payload, context) ->
                RELAY.onRequest(context.player().getUUID(), payload.contentHash()));

        ServerPlayNetworking.registerGlobalReceiver(LovePawPayloads.PetUploadPayload.TYPE, (payload, context) ->
                RELAY.onChunk(context.player().getUUID(), payload.chunk()));

        ServerLifecycleEvents.SERVER_STARTING.register(starting -> {
            server = starting;
            LovePaw.onServerStarting();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            server = null;
            ServerPetState.clear();
            RELAY.clear();
        });

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
            RELAY.onLeave(owner);
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
