package dev.lovepaw.pet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
