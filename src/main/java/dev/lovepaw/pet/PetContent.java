package dev.lovepaw.pet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The files a pet is made of, and the name those files answer to.
 *
 * <p>A pet's id says nothing about what it looks like. Anybody can put a folder
 * called {@code cat} in their game directory, and two of them will be different
 * animals; relaying only the id means one player shows a dragon and the other
 * watches their own cat, none the wiser. Hashing the bytes gives a pet a name
 * that cannot lie, which is what tells a client whether it has the pet somebody
 * is wearing or merely something with the same name.
 *
 * <p>It is also the key everything about sharing hangs on: what to ask a server
 * for, what to cache on disk, and what to allow or refuse.
 */
public record PetContent(Map<String, byte[]> files, String hash) {
    /** Name of the definition inside a bundle, whatever it was called on disk. */
    public static final String DEFINITION = "pet.json";

    /**
     * What a pet may weigh to travel between players. A pet is a handful of
     * small files — a model, a texture, some animations — and these are roomy
     * for that. They bound what one player can make every other player receive,
     * which is the only reason they exist; a bigger pet still works at home.
     */
    public static final int MAX_FILES = 8;
    public static final int MAX_FILE_BYTES = 512 * 1024;
    public static final int MAX_TOTAL_BYTES = 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 128;

    public static PetContent of(Map<String, byte[]> files) {
        TreeMap<String, byte[]> sorted = new TreeMap<>(files);
        return new PetContent(Map.copyOf(sorted), hash(sorted));
    }

    public byte[] file(String name) {
        return files.get(name);
    }

    public int size() {
        int total = 0;
        for (byte[] bytes : files.values()) {
            total += bytes.length;
        }
        return total;
    }

    /** Whether this is the pet somebody named, rather than one like it. */
    public boolean is(String contentHash) {
        return hash.equals(contentHash);
    }

    /** True when this pet is small enough to be handed to another player. */
    public boolean isShareable() {
        if (files.size() > MAX_FILES || size() > MAX_TOTAL_BYTES) {
            return false;
        }
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (file.getKey().length() > MAX_NAME_LENGTH || file.getValue().length > MAX_FILE_BYTES) {
                return false;
            }
        }
        return true;
    }

    /** The files as one block of bytes, which is how a pet travels. */
    public byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(size() + 64);
        try (DataOutputStream data = new DataOutputStream(out)) {
            data.writeInt(files.size());
            for (Map.Entry<String, byte[]> file : new TreeMap<>(files).entrySet()) {
                data.writeUTF(file.getKey());
                data.writeInt(file.getValue().length);
                data.write(file.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("writing to memory cannot fail", e);
        }
        return out.toByteArray();
    }

    /**
     * Reads back what {@link #toBytes} wrote.
     *
     * <p>These bytes came from another player, so every count in them is read
     * as a claim: a pet that says it holds a thousand files, or one file of two
     * gigabytes, is refused before anything is allocated for it.
     */
    public static PetContent fromBytes(byte[] bytes) throws IOException {
        if (bytes.length > MAX_TOTAL_BYTES + MAX_FILES * (MAX_NAME_LENGTH + 8) + 8) {
            throw new IOException("a pet of " + bytes.length + " bytes is too big");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        int total = 0;
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int count = data.readInt();
            if (count < 1 || count > MAX_FILES) {
                throw new IOException("a pet holds between 1 and " + MAX_FILES + " files, not " + count);
            }
            for (int i = 0; i < count; i++) {
                String name = data.readUTF();
                if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                    throw new IOException("a file name of " + name.length() + " characters");
                }
                int length = data.readInt();
                if (length < 0 || length > MAX_FILE_BYTES) {
                    throw new IOException(name + " claims to be " + length + " bytes");
                }
                total += length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new IOException("a pet of over " + MAX_TOTAL_BYTES + " bytes");
                }
                byte[] file = new byte[length];
                data.readFully(file);
                if (files.put(name, file) != null) {
                    throw new IOException("two files both called " + name);
                }
            }
            if (data.read() != -1) {
                throw new IOException("there are bytes after the last file");
            }
        }
        return of(files);
    }

    /**
     * SHA-256 over every file, names included, in a fixed order. The names are
     * in the hash because a pet that points at {@code cat.png} is not the same
     * pet as one that points at {@code dog.png} holding the same picture.
     */
    private static String hash(TreeMap<String, byte[]> sorted) {
        MessageDigest digest = sha256();
        for (Map.Entry<String, byte[]> file : sorted.entrySet()) {
            digest.update(file.getKey().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(intBytes(file.getValue().length));
            digest.update(file.getValue());
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java has SHA-256", e);
        }
    }

    private static byte[] intBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }
}
