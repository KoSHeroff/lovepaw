package dev.lovepaw.pet;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The name a pet cannot lie about. Everything in sharing hangs off this: what a
 * client asks the server for, what it keeps on disk, and whether the cat it has
 * is the cat the other player is wearing.
 */
class PetContentTest {
    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, byte[]> cat() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("pet.json", bytes("{\"model\": \"cat.geo.json\"}"));
        files.put("cat.geo.json", bytes("{\"bones\": []}"));
        files.put("cat.png", bytes("not really a png"));
        return files;
    }

    @Test
    void theSameFilesAreTheSamePet() {
        Map<String, byte[]> other = new LinkedHashMap<>();
        cat().entrySet().stream()
                .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                .forEach(file -> other.put(file.getKey(), file.getValue().clone()));

        assertEquals(PetContent.of(cat()).hash(), PetContent.of(other).hash(),
                "the order files were read in is not part of what a pet is");
    }

    @Test
    void oneChangedByteIsADifferentPet() {
        Map<String, byte[]> changed = cat();
        changed.put("cat.png", bytes("not really a pnq"));

        assertNotEquals(PetContent.of(cat()).hash(), PetContent.of(changed).hash());
    }

    @Test
    void renamingAFileIsADifferentPet() {
        Map<String, byte[]> renamed = cat();
        renamed.put("dog.png", renamed.remove("cat.png"));

        assertNotEquals(PetContent.of(cat()).hash(), PetContent.of(renamed).hash(),
                "the definition points at a name, so the names are part of the pet");
    }

    @Test
    void movingBytesBetweenFilesIsADifferentPet() {
        Map<String, byte[]> first = new LinkedHashMap<>();
        first.put("a", bytes("one"));
        first.put("b", bytes("two"));
        Map<String, byte[]> second = new LinkedHashMap<>();
        second.put("a", bytes("onetw"));
        second.put("b", bytes("o"));

        assertNotEquals(PetContent.of(first).hash(), PetContent.of(second).hash(),
                "file boundaries are hashed, or the same bytes cut differently would pass as one pet");
    }

    @Test
    void theHashIsTheWholeDigest() {
        assertEquals(64, PetContent.of(cat()).hash().length());
    }

    @Test
    void sizeIsWhatWouldGoOverTheWire() {
        assertEquals(cat().values().stream().mapToInt(file -> file.length).sum(), PetContent.of(cat()).size());
    }
}
