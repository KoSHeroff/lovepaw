package dev.lovepaw.neoforge;

import dev.lovepaw.LovePaw;
import dev.lovepaw.client.PetManager;
import dev.lovepaw.net.LovePawPayloads;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                LovePawPayloads.HelloPayload.TYPE,
                LovePawPayloads.HelloPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    LovePaw.LOGGER.info("Server speaks LovePaw (protocol {})", payload.protocolVersion());
                    PetManager.get().onServerHello(payload.protocolVersion());
                }));

        registrar.playToClient(
                LovePawPayloads.StatePayload.TYPE,
                LovePawPayloads.StatePayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        PetManager.get().onStateEntries(payload.entries())));
    }
}
