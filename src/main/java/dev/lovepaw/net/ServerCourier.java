package dev.lovepaw.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The relay's hands: finds the player a packet is meant for and hands it over.
 *
 * <p>Only the sending differs between mod loaders, so that is all this asks
 * for. Everything about who gets what stays in {@link PetContentRelay}, where
 * it can be tested without a server at all.
 */
public final class ServerCourier implements PetContentRelay.Courier {
    /** How this loader sends a payload to one player. */
    public interface Post {
        void send(ServerPlayer player, CustomPacketPayload payload);
    }

    private final Supplier<MinecraftServer> server;
    private final Post post;

    public ServerCourier(Supplier<MinecraftServer> server, Post post) {
        this.server = server;
        this.post = post;
    }

    @Override
    public void ask(UUID owner, String contentHash) {
        to(owner, new LovePawPayloads.PetNeededPayload(contentHash));
    }

    @Override
    public void give(UUID player, LovePawPayloads.PetChunk chunk) {
        to(player, new LovePawPayloads.PetDeliveryPayload(chunk));
    }

    @Override
    public void refuse(UUID player, String contentHash) {
        to(player, new LovePawPayloads.PetMissingPayload(contentHash));
    }

    @Override
    public Collection<UUID> wearing(String contentHash) {
        return ServerPetState.wearing(contentHash);
    }

    private void to(UUID id, CustomPacketPayload payload) {
        MinecraftServer running = server.get();
        if (running == null) {
            return;
        }
        ServerPlayer player = running.getPlayerList().getPlayer(id);
        if (player != null) {
            post.send(player, payload);
        }
    }
}
