package dev.lovepaw.client;

import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetSourceKind;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether the pet another player is wearing is the pet we have.
 *
 * <p>This is the whole guard against a silent swap: ids are chosen by whoever
 * made the pet, so two people can hold different animals under the same name.
 */
class PetSelectionTest {
    private static PetDefinition cat;
    private static PetDefinition bee;

    @BeforeAll
    static void loadTwoPets() throws IOException {
        cat = builtIn("cat");
        bee = builtIn("catbee");
    }

    private static PetDefinition builtIn(String name) throws IOException {
        URL url = PetSelectionTest.class.getResource("/assets/lovepaw/lovepaw/pets/" + name);
        assertFalse(url == null, "the built-in pet " + name + " is missing from the classpath");
        Path folder;
        try {
            folder = Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("lovepaw", name);
        return PetBundle.read(
                id,
                ResourceLocation.fromNamespaceAndPath("lovepaw", "lovepaw/pets/" + name),
                PetSourceKind.BUILTIN,
                PetBundle.filesIn(folder)).definition();
    }

    @Test
    void theSamePetIsTheSamePet() {
        assertTrue(new PetManager.Selection(cat.id(), cat.contentHash()).matches(cat));
    }

    @Test
    void theSameNameOverDifferentFilesIsNot() {
        assertFalse(new PetManager.Selection(cat.id(), bee.contentHash()).matches(cat),
                "a cat of their own making is not the cat we have, whatever it is called");
    }

    @Test
    void aDifferentNameIsNot() {
        assertFalse(new PetManager.Selection(bee.id(), cat.contentHash()).matches(cat));
    }

    @Test
    void aClientTooOldToSendAHashIsTakenAtItsWord() {
        assertTrue(new PetManager.Selection(cat.id(), "").matches(cat),
                "refusing would hide pets that worked before the hash existed");
    }
}
