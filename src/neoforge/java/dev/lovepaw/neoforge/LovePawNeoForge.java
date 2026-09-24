package dev.lovepaw.neoforge;

import dev.lovepaw.LovePaw;
import dev.lovepaw.net.LovePawPayloads;
import dev.lovepaw.net.ServerPetState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

@Mod(LovePaw.MOD_ID)
public final class LovePawNeoForge {
    public LovePawNeoForge(IEventBus modEventBus, ModContainer container) {
        LovePaw.init(FMLPaths.CONFIGDIR.get());

        modEventBus.addListener(LovePawNeoForge::registerPayloads);

        NeoForge.EVENT_BUS.addListener(LovePawNeoForge::onServerStarting);
        NeoForge.EVENT_BUS.addListener(LovePawNeoForge::onServerStopped);
        NeoForge.EVENT_BUS.addListener(LovePawNeoForge::onLogin);
        NeoForge.EVENT_BUS.addListener(LovePawNeoForge::onLogout);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(String.valueOf(LovePaw.PROTOCOL_VERSION)).optional();

        registrar.playToServer(
                LovePawPayloads.SelectPayload.TYPE,
                LovePawPayloads.SelectPayload.CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        context.enqueueWork(() -> {
                            LovePawPayloads.Entry entry = ServerPetState.select(player.getUUID(), payload.petId(), payload.contentHash());
                            if (entry != null) {
                                PacketDistributor.sendToAllPlayers(new LovePawPayloads.StatePayload(List.of(entry)));
                            }
                        });
                    }
                });

        if (FMLEnvironment.dist.isClient()) {
            ClientPayloadHandlers.register(registrar);
        } else {
            registrar.playToClient(
                    LovePawPayloads.HelloPayload.TYPE,
                    LovePawPayloads.HelloPayload.CODEC,
                    (payload, context) -> {
                    });
            registrar.playToClient(
                    LovePawPayloads.StatePayload.TYPE,
                    LovePawPayloads.StatePayload.CODEC,
                    (payload, context) -> {
                    });
        }
    }

    private static void onServerStarting(ServerStartingEvent event) {
        LovePaw.onServerStarting();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ServerPetState.clear();
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new LovePawPayloads.HelloPayload(LovePaw.PROTOCOL_VERSION));
        List<LovePawPayloads.Entry> snapshot = ServerPetState.snapshot();
        if (!snapshot.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new LovePawPayloads.StatePayload(snapshot));
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LovePawPayloads.Entry entry = ServerPetState.forget(player.getUUID());
        if (entry != null) {
            PacketDistributor.sendToAllPlayers(new LovePawPayloads.StatePayload(List.of(entry)));
        }
    }
}
