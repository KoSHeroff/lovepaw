package dev.lovepaw.pet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lovepaw.model.anim.Animation;
import dev.lovepaw.model.anim.AnimationParser;
import dev.lovepaw.model.geo.GeoBaker;
import dev.lovepaw.model.geo.GeoParser;
import dev.lovepaw.model.geo.baked.BakedGeoModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInPetsTest {
    private static final String PETS = "/assets/lovepaw/lovepaw/pets";

    @TestFactory
    Stream<DynamicTest> everyBuiltInPetLoads() throws IOException {
        Path pets = onClasspath();
        List<Path> folders;
        try (Stream<Path> children = Files.list(pets)) {
            folders = children.filter(Files::isDirectory).sorted().toList();
        }
        assertFalse(folders.isEmpty(), "no built-in pets found under " + pets);

        return folders.stream().map(folder -> DynamicTest.dynamicTest(
                folder.getFileName().toString(), () -> checkPet(folder)));
    }

    private static Path onClasspath() {
        URL url = BuiltInPetsTest.class.getResource(PETS);
        assertFalse(url == null, "built-in pets are missing from the classpath at " + PETS);
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void checkPet(Path folder) throws IOException {
        String name = folder.getFileName().toString();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("lovepaw", name);
        JsonObject json = read(folder.resolve("pet.json"));
        PetDefinition definition = PetDefinitionParser.parse(
                id, ResourceLocation.fromNamespaceAndPath("lovepaw", "lovepaw/pets/" + name),
                json, PetSourceKind.BUILTIN);

        BakedGeoModel model = GeoBaker.bake(GeoParser.parse(read(folder.resolve(fileName(definition.model())))));
        assertFalse(model.roots().isEmpty(), "model has no bones");

        if (definition.animationFile() == null) {
            assertTrue(definition.animations().isEmpty(), "animations named but no animation_file");
            return;
        }

        Map<String, Animation> animations =
                AnimationParser.parse(read(folder.resolve(fileName(definition.animationFile()))));
        List<String> missing = new ArrayList<>();
        definition.animations().forEach((state, animationName) -> {
            if (!animations.containsKey(animationName)) {
                missing.add(state.key() + " -> " + animationName);
            }
        });
        assertTrue(missing.isEmpty(), "pet.json names animations the animation file does not have: " + missing);

        List<String> unknownBones = new ArrayList<>();
        animations.forEach((animationName, animation) -> animation.bones().keySet().forEach(bone -> {
            if (!model.bonesByName().containsKey(bone)) {
                unknownBones.add(animationName + " -> " + bone);
            }
        }));
        assertTrue(unknownBones.isEmpty(), "animations drive bones the model does not have: " + unknownBones);
    }

    private static String fileName(ResourceLocation location) {
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static JsonObject read(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
