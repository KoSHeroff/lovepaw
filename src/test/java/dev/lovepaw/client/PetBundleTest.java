package dev.lovepaw.client;

import dev.lovepaw.pet.PetContent;
import dev.lovepaw.pet.PetSourceKind;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pet built out of bytes that arrived from somewhere else.
 *
 * <p>Nobody here chose these files, so this is the one place where a pet is
 * read on somebody else's say-so — and where being strict actually matters.
 */
class PetBundleTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("remote", "somebodys_pet");
    private static final ResourceLocation FOLDER =
            ResourceLocation.fromNamespaceAndPath("lovepaw", "remote/somebodys_pet");

    private static Map<String, byte[]> files;

    @BeforeAll
    static void takeAPetApart() throws IOException {
        URL url = PetBundleTest.class.getResource("/assets/lovepaw/lovepaw/pets/catopillar");
        assertFalse(url == null, "the built-in pet is missing from the classpath");
        Path folder;
        try {
            folder = Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        files = new LinkedHashMap<>(
                PetBundle.contentOf(ID, FOLDER, PetBundle.filesIn(folder)).files());
    }

    private static PetBundle.Loaded readAsRemote(Map<String, byte[]> bundle) throws IOException {
        return PetBundle.read(ID, FOLDER, PetSourceKind.REMOTE, PetBundle.of(bundle));
    }

    @Test
    void aPetSurvivesTheJourney() throws IOException {
        PetContent sent = PetContent.of(files);
        PetContent arrived = PetContent.fromBytes(sent.toBytes());

        PetBundle.Loaded loaded = readAsRemote(arrived.files());

        assertEquals(sent.hash(), loaded.definition().contentHash(),
                "what arrived is the pet that was sent");
        assertFalse(loaded.assets().model().roots().isEmpty(), "and it is a model, not just bytes");
        assertTrue(loaded.texture().length > 0);
        assertEquals(PetSourceKind.REMOTE, loaded.definition().source());
    }

    @Test
    void itsTextureCannotBeOneOfTheGamesOwn() {
        Map<String, byte[]> bundle = new LinkedHashMap<>(files);
        bundle.put(PetContent.DEFINITION, new String(bundle.get(PetContent.DEFINITION), StandardCharsets.UTF_8)
                .replace("\"texture\": \"catopillar.png\"", "\"texture\": \"minecraft:textures/block/stone.png\"")
                .getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> readAsRemote(bundle),
                "a pet naming a path inside the game would have its texture registered over one of ours");
    }

    @Test
    void aFileItDidNotSendCannotBeBorrowedFromHere() {
        Map<String, byte[]> bundle = new LinkedHashMap<>(files);
        bundle.remove("catopillar.png");

        assertThrows(IOException.class, () -> readAsRemote(bundle),
                "a pet is the files it came with, and nothing else");
    }

    @Test
    void aPetTooBigToShareIsRefused() {
        Map<String, byte[]> bundle = new LinkedHashMap<>(files);
        bundle.put("catopillar.png", new byte[PetContent.MAX_FILE_BYTES + 1]);

        assertThrows(IOException.class, () -> readAsRemote(bundle));
    }

    @Test
    void whatTheOwnerHasIsReadWithoutThoseLimits() throws IOException {
        Map<String, byte[]> bundle = new LinkedHashMap<>(files);
        bundle.put("catopillar.png", files.get("catopillar.png"));

        // The same files, read as the player's own: nothing about a pet already
        // sitting on this disk needs the limits that bound what may be sent.
        PetBundle.Loaded loaded =
                PetBundle.read(ID, FOLDER, PetSourceKind.LOCAL, PetBundle.of(bundle));

        assertEquals(PetContent.of(files).hash(), loaded.definition().contentHash());
    }
}
