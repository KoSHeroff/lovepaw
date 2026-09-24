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
    /** A hex SHA-256, or empty from a client that has not hashed its pet. */
    private static final int MAX_HASH_LENGTH = 64;

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

    /**
     * Client to server: "I am now showing this pet", or empty for none.
     *
     * <p>The hash goes with the id because an id alone says nothing about what
     * the pet looks like: two players can both have a {@code local:cat} and
     * mean different animals. It is what lets everyone else tell whether the
     * cat they have is this cat.
     */
    public record SelectPayload(String petId, String contentHash) implements CustomPacketPayload {
        public static final Type<SelectPayload> TYPE = new Type<>(id("select"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SelectPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), SelectPayload::petId,
                ByteBufCodecs.stringUtf8(MAX_HASH_LENGTH), SelectPayload::contentHash,
                SelectPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client to server: "send me the pet with this hash".
     *
     * <p>Nobody asks for a pet by name, only by hash, so the answer can always
     * be checked against the question.
     */
    public record PetRequestPayload(String contentHash) implements CustomPacketPayload {
        public static final Type<PetRequestPayload> TYPE = new Type<>(id("pet_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PetRequestPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_HASH_LENGTH), PetRequestPayload::contentHash,
                PetRequestPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: "you are wearing this one, send it here". The same
     * question in the other direction, since only the player wearing a pet has
     * its files.
     */
    public record PetNeededPayload(String contentHash) implements CustomPacketPayload {
        public static final Type<PetNeededPayload> TYPE = new Type<>(id("pet_needed"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PetNeededPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_HASH_LENGTH), PetNeededPayload::contentHash,
                PetNeededPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * One slice of a pet, because a pet does not fit in a packet. The hash is
     * on every slice, so slices of two pets can never be mistaken for each
     * other and what is assembled is checked against what was asked for.
     */
    public record PetChunk(String contentHash, int index, int count, byte[] data) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PetChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_HASH_LENGTH), PetChunk::contentHash,
                ByteBufCodecs.VAR_INT, PetChunk::index,
                ByteBufCodecs.VAR_INT, PetChunk::count,
                ByteBufCodecs.byteArray(PetTransfer.CHUNK_BYTES), PetChunk::data,
                PetChunk::new);
    }

    /** Client to server: part of the pet this player was asked for. */
    public record PetUploadPayload(PetChunk chunk) implements CustomPacketPayload {
        public static final Type<PetUploadPayload> TYPE = new Type<>(id("pet_upload"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PetUploadPayload> CODEC = StreamCodec.composite(
                PetChunk.CODEC, PetUploadPayload::chunk,
                PetUploadPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: part of the pet this player asked for. */
    public record PetDeliveryPayload(PetChunk chunk) implements CustomPacketPayload {
        public static final Type<PetDeliveryPayload> TYPE = new Type<>(id("pet_delivery"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PetDeliveryPayload> CODEC = StreamCodec.composite(
                PetChunk.CODEC, PetDeliveryPayload::chunk,
                PetDeliveryPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: nobody here can give you that pet. Said out loud so the
     * client stops waiting for it instead of leaving a player pet-less with no
     * explanation.
     */
    public record PetMissingPayload(String contentHash) implements CustomPacketPayload {
        public static final Type<PetMissingPayload> TYPE = new Type<>(id("pet_missing"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PetMissingPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_HASH_LENGTH), PetMissingPayload::contentHash,
                PetMissingPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One player's choice, as the server relays it. */
    public record Entry(UUID owner, String petId, String contentHash) {
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
                buffer.writeUtf(entry.contentHash(), MAX_HASH_LENGTH);
            }
        }

        private static StatePayload read(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new IllegalArgumentException("LovePaw state packet declares " + count + " entries");
            }
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(
                        buffer.readUUID(),
                        buffer.readUtf(MAX_ID_LENGTH),
                        buffer.readUtf(MAX_HASH_LENGTH)));
            }
            return new StatePayload(List.copyOf(entries));
        }
    }
}
