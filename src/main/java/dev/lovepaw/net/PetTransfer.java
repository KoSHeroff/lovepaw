package dev.lovepaw.net;

import dev.lovepaw.pet.PetContent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A pet on its way between two players, in slices.
 *
 * <p>Both ends use this: the client that is asked for its pet cuts it up, the
 * server puts it back together, cuts it up again for whoever was waiting, and
 * they put it back together. Every slice carries the hash it belongs to, and
 * the finished pet is hashed again before anyone believes it — so a truncated,
 * mixed-up or tampered-with transfer fails instead of becoming a pet.
 */
public final class PetTransfer {
    /** Small enough for a packet in either direction, with room to spare. */
    public static final int CHUNK_BYTES = 16 * 1024;

    /** What the biggest allowed pet needs, and no more. */
    public static final int MAX_CHUNKS = 80;

    private final String contentHash;
    private final byte[][] parts;
    private int received;
    private int bytes;

    private PetTransfer(String contentHash, int count) {
        this.contentHash = contentHash;
        this.parts = new byte[count][];
    }

    /** Cuts a pet into what can be sent. */
    public static List<LovePawPayloads.PetChunk> split(PetContent content) {
        byte[] bytes = content.toBytes();
        int count = Math.max(1, (bytes.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
        List<LovePawPayloads.PetChunk> chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int from = i * CHUNK_BYTES;
            int to = Math.min(bytes.length, from + CHUNK_BYTES);
            byte[] slice = new byte[to - from];
            System.arraycopy(bytes, from, slice, 0, slice.length);
            chunks.add(new LovePawPayloads.PetChunk(content.hash(), i, count, slice));
        }
        return chunks;
    }

    /**
     * Starts collecting a pet somebody promised to send.
     *
     * @throws IOException if the promise is not one worth keeping room for
     */
    public static PetTransfer awaiting(String contentHash, int count) throws IOException {
        if (count < 1 || count > MAX_CHUNKS) {
            throw new IOException("a pet arrives in 1 to " + MAX_CHUNKS + " pieces, not " + count);
        }
        return new PetTransfer(contentHash, count);
    }

    public String contentHash() {
        return contentHash;
    }

    /**
     * Takes one slice.
     *
     * @return true once every slice has arrived
     */
    public boolean accept(LovePawPayloads.PetChunk chunk) throws IOException {
        if (!contentHash.equals(chunk.contentHash()) || chunk.count() != parts.length) {
            throw new IOException("a piece of a different pet");
        }
        if (chunk.index() < 0 || chunk.index() >= parts.length) {
            throw new IOException("piece " + chunk.index() + " of " + parts.length);
        }
        if (parts[chunk.index()] != null) {
            throw new IOException("piece " + chunk.index() + " twice");
        }
        bytes += chunk.data().length;
        if (bytes > PetContent.MAX_TOTAL_BYTES + CHUNK_BYTES) {
            throw new IOException("more bytes than a pet may weigh");
        }
        parts[chunk.index()] = chunk.data();
        return ++received == parts.length;
    }

    /**
     * The finished pet, or nothing — the bytes are only a pet if they hash to
     * the one that was asked for.
     */
    public PetContent finish() throws IOException {
        byte[] bytes = new byte[this.bytes];
        int at = 0;
        for (byte[] part : parts) {
            if (part == null) {
                throw new IOException("a piece is still missing");
            }
            System.arraycopy(part, 0, bytes, at, part.length);
            at += part.length;
        }
        PetContent content = PetContent.fromBytes(bytes);
        if (!content.is(contentHash)) {
            throw new IOException("this is not the pet that was asked for");
        }
        return content;
    }
}
