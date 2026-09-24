package dev.lovepaw.net;

import dev.lovepaw.LovePaw;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The whole protocol, which is deliberately tiny: the server never sends a
 * position, only who owns which pet. Everything else is simulated by each
 * client, so the mod costs a server almost nothing and still works on a server
 * that does not have it at all.
 *
 * <p>Payloads are plain vanilla {@link CustomPacketPayload}s so Fabric and
 * NeoForge exchange the exact same bytes; only registration differs per loader.
 */
public final class LovePawPayloads {
    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_ID_LENGTH = 256;

    private LovePawPayloads() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LovePaw.MOD_ID, path);
    }

    /**
     * Server to client, once after joining: "this server speaks LovePaw". The
     * client only starts talking after this, which is what keeps it silent on a
     * vanilla server instead of throwing packets at nobody.
     */
    public record HelloPayload(int protocolVersion) implements CustomPacketPayload {
        public static final Type<HelloPayload> TYPE = new Type<>(id("hello"));

        public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HelloPayload::protocolVersion,
                HelloPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: "I am now showing this pet", or empty for none. */
    public record SelectPayload(String petId) implements CustomPacketPayload {
        public static final Type<SelectPayload> TYPE = new Type<>(id("select"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SelectPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), SelectPayload::petId,
                SelectPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One player's choice, as the server relays it. */
    public record Entry(UUID owner, String petId) {
    }

    /**
     * Server to client: who is showing what. Sent in full on join and as a
     * single entry whenever somebody changes pet; an empty {@code petId} means
     * that player has no pet any more.
     */
    public record StatePayload(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(id("state"));

        public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC =
                StreamCodec.of(StatePayload::write, StatePayload::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(FriendlyByteBuf buffer, StatePayload payload) {
            List<Entry> entries = payload.entries();
            buffer.writeVarInt(Math.min(entries.size(), MAX_ENTRIES));
            int written = 0;
            for (Entry entry : entries) {
                if (written++ >= MAX_ENTRIES) {
                    break;
                }
                buffer.writeUUID(entry.owner());
                buffer.writeUtf(entry.petId(), MAX_ID_LENGTH);
            }
        }

        private static StatePayload read(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new IllegalArgumentException("LovePaw state packet declares " + count + " entries");
            }
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buffer.readUUID(), buffer.readUtf(MAX_ID_LENGTH)));
            }
            return new StatePayload(List.copyOf(entries));
        }
    }
}
