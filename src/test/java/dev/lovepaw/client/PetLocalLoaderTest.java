package dev.lovepaw.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The folder a player drops a pet into. These tests run the real loader over
 * real files, because the whole point of the folder is that nothing else has
 * to be set up for a pet to work.
 */
class PetLocalLoaderTest {
    private static Path source;

    @BeforeAll
    static void findABuiltInPetToCopy() throws URISyntaxException {
        URL url = PetLocalLoaderTest.class.getResource("/assets/lovepaw/lovepaw/pets/catopillar");
        assertFalse(url == null, "the built-in pet is missing from the classpath");
        source = Path.of(url.toURI());
    }

    private static Path pet(Path folder, String name) throws IOException {
        Path directory = Files.createDirectories(folder.resolve(name));
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : files.toList()) {
                Files.copy(file, directory.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return directory;
    }

    @Test
    void readsAPetDroppedIntoTheFolder(@TempDir Path folder) throws IOException {
        pet(folder, "mypet");

        List<PetLocalLoader.LocalPet> pets = PetLocalLoader.scan(folder);

        assertEquals(1, pets.size());
        PetLocalLoader.LocalPet found = pets.get(0);
        assertEquals(ResourceLocation.fromNamespaceAndPath("local", "mypet"), found.definition().id());
        assertFalse(found.assets().model().roots().isEmpty(), "the model should be baked and ready");
        assertFalse(found.assets().animations().isEmpty(), "and its animations loaded");
        assertTrue(found.texture().length > 0, "the texture stays bytes until the render thread wants it");
    }

    @Test
    void theTextureIdCannotCollideWithAResourcePack(@TempDir Path folder) throws IOException {
        pet(folder, "mypet");

        PetLocalLoader.LocalPet found = PetLocalLoader.scan(folder).get(0);

        ResourceLocation texture = found.definition().texture();
        assertEquals("lovepaw", texture.getNamespace());
        assertTrue(texture.getPath().startsWith("local/mypet/"),
                "a folder pet's texture id has to be its own; was " + texture);
    }

    @Test
    void makesTheFolderWhenItIsNotThereYet(@TempDir Path parent) {
        Path folder = parent.resolve("lovepaw").resolve("pets");

        assertTrue(PetLocalLoader.scan(folder).isEmpty());
        assertTrue(Files.isDirectory(folder), "a player who goes looking should find the folder waiting");
    }

    @Test
    void oneBrokenPetDoesNotCostYouTheOthers(@TempDir Path folder) throws IOException {
        pet(folder, "good");
        Path broken = pet(folder, "broken");
        Files.writeString(broken.resolve("pet.json"), "{ this is not json", StandardCharsets.UTF_8);
        Files.createDirectories(folder.resolve("not-a-pet-at-all"));

        List<PetLocalLoader.LocalPet> pets = PetLocalLoader.scan(folder);

        assertEquals(1, pets.size(), "the good pet should still load");
        assertEquals("good", pets.get(0).definition().id().getPath());
    }

    @Test
    void aPetMissingOneOfItsFilesIsSkipped(@TempDir Path folder) throws IOException {
        Path directory = pet(folder, "mypet");
        Files.delete(directory.resolve("catopillar.png"));

        assertTrue(PetLocalLoader.scan(folder).isEmpty(),
                "a pet with no texture would render as purple and black, so it does not load at all");
    }

    @Test
    void theSamePetInTwoFoldersIsTheSamePet(@TempDir Path folder) throws IOException {
        pet(folder, "mine");
        pet(folder, "theirs");

        List<PetLocalLoader.LocalPet> pets = PetLocalLoader.scan(folder);

        assertEquals(2, pets.size());
        assertEquals(pets.get(0).definition().contentHash(), pets.get(1).definition().contentHash(),
                "the same files are the same pet, whatever folder the player put them in");
    }

    @Test
    void changingOneFileMakesADifferentPet(@TempDir Path folder) throws IOException {
        pet(folder, "before");
        String hash = PetLocalLoader.scan(folder).get(0).definition().contentHash();

        Path changed = pet(folder, "before");
        String json = Files.readString(changed.resolve("pet.json"), StandardCharsets.UTF_8)
                .replace("\"scale\"", "\"y_offset\": 0.5, \"scale\"");
        Files.writeString(changed.resolve("pet.json"), json, StandardCharsets.UTF_8);

        assertNotEquals(hash, PetLocalLoader.scan(folder).get(0).definition().contentHash(),
                "two pets sharing an id and nothing else is exactly what the hash is for");
    }

    @Test
    void whatElseIsLyingInTheFolderIsNotPartOfThePet(@TempDir Path folder) throws IOException {
        Path directory = pet(folder, "mypet");
        String hash = PetLocalLoader.scan(folder).get(0).definition().contentHash();

        Files.writeString(directory.resolve("notes.txt"), "work in progress", StandardCharsets.UTF_8);
        Files.copy(directory.resolve("catopillar.png"), directory.resolve("catopillar.png.bak"));

        assertEquals(hash, PetLocalLoader.scan(folder).get(0).definition().contentHash(),
                "a pet weighs what it draws with, not what its author left beside it");
    }

    @Test
    void refusesToReachOutsideItsOwnFolder(@TempDir Path folder) throws IOException {
        Path directory = pet(folder, "mypet");
        String json = Files.readString(directory.resolve("pet.json"), StandardCharsets.UTF_8)
                .replace("\"texture\": \"catopillar.png\"", "\"texture\": \"minecraft:textures/block/stone.png\"");
        Files.writeString(directory.resolve("pet.json"), json, StandardCharsets.UTF_8);

        assertTrue(PetLocalLoader.scan(folder).isEmpty(),
                "namespaced paths point into resource packs, which is not where these files live");
    }
}
