package dev.lovepaw.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lovepaw.LovePaw;
import dev.lovepaw.model.anim.Animation;
import dev.lovepaw.model.anim.AnimationParser;
import dev.lovepaw.model.geo.GeoBaker;
import dev.lovepaw.model.geo.GeoParser;
import dev.lovepaw.model.geo.baked.BakedGeoModel;
import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetDefinitionParser;
import dev.lovepaw.pet.PetSourceKind;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads pets from a plain folder in the game directory, next to the resource
 * packs but without being one.
 *
 * <p>Making a pet otherwise means building a resource pack around it: a
 * {@code pack.mcmeta}, the right nesting under a namespace, enabling it in the
 * options. Every one of those steps fails the same silent way — the pet is
 * simply missing from the picker. A folder the player drops files into removes
 * all of them, and it is where a downloaded pet will land when a catalogue
 * exists.
 *
 * <p>Reading files is all this does; the texture is handed on as a path,
 * because turning it into something the renderer can bind has to happen on the
 * render thread and this runs off it.
 */
public final class PetLocalLoader {
    /** Namespace for folder pets, so they can never collide with a pack's. */
    public static final String NAMESPACE = "local";
    private static final String DEFINITION_FILE = "pet.json";

    /** A pet found on disk, with its texture still a file on that disk. */
    public record LocalPet(PetDefinition definition, PetAssets assets, Path texture) {
    }

    private PetLocalLoader() {
    }

    /** The folder pets are read from, given the game directory. */
    public static Path folderIn(Path gameDirectory) {
        return gameDirectory.resolve("lovepaw").resolve("pets");
    }

    /**
     * Reads every pet in {@code folder}, skipping and logging the ones that do
     * not load. Creates the folder when it is missing, so that a player who
     * goes looking for it finds it already there.
     */
    public static List<LocalPet> scan(Path folder) {
        List<LocalPet> pets = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                LovePaw.LOGGER.warn("Could not create the pet folder {}: {}", folder, e.toString());
            }
            return pets;
        }

        List<Path> directories = new ArrayList<>();
        try (Stream<Path> children = Files.list(folder)) {
            children.filter(Files::isDirectory).sorted().forEach(directories::add);
        } catch (IOException e) {
            LovePaw.LOGGER.error("Could not read the pet folder {}: {}", folder, e.toString());
            return pets;
        }

        for (Path directory : directories) {
            if (!Files.isRegularFile(directory.resolve(DEFINITION_FILE))) {
                continue;
            }
            try {
                pets.add(load(directory));
            } catch (Exception e) {
                LovePaw.LOGGER.error("Skipping pet {}: {}", directory, e.getMessage());
                LovePaw.LOGGER.debug("Local pet load failure", e);
            }
        }
        return pets;
    }

    private static LocalPet load(Path directory) throws IOException {
        String name = directory.getFileName().toString();
        ResourceLocation id = ResourceLocation.tryBuild(NAMESPACE, name);
        if (id == null) {
            throw new IOException("folder name '" + name
                    + "' cannot be a pet id; use lowercase letters, digits, _ - and .");
        }
        // Assets keep their own ids under this folder, which is what the
        // texture is registered as later: unique per pet, and never a path a
        // resource pack could also claim.
        ResourceLocation assetFolder = ResourceLocation.fromNamespaceAndPath(
                LovePaw.MOD_ID, NAMESPACE + "/" + name);

        JsonObject json = readJson(directory.resolve(DEFINITION_FILE));
        requireLocalFile(json, "model", id);
        requireLocalFile(json, "texture", id);
        requireLocalFile(json, "animation_file", id);

        PetDefinition definition = PetDefinitionParser.parse(id, assetFolder, json, PetSourceKind.LOCAL);

        BakedGeoModel model = GeoBaker.bake(GeoParser.parse(readJson(fileFor(directory, definition.model()))));

        Map<String, Animation> animations = new LinkedHashMap<>();
        if (definition.animationFile() != null) {
            animations.putAll(AnimationParser.parse(readJson(fileFor(directory, definition.animationFile()))));
        }

        Path texture = fileFor(directory, definition.texture());
        return new LocalPet(definition, new PetAssets(model, Map.copyOf(animations)), texture);
    }

    /**
     * A folder pet may only point at files beside its {@code pet.json}. The
     * {@code namespace:path} form a resource pack can use would send us looking
     * inside packs, which is not where these files are.
     */
    private static void requireLocalFile(JsonObject json, String field, ResourceLocation id) throws IOException {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()) {
            return;
        }
        String value = json.get(field).getAsString();
        if (value.indexOf(':') >= 0 || value.indexOf('/') >= 0) {
            throw new IOException("pet " + id + " has '" + field + "': " + value
                    + " — a pet in this folder can only name files sitting next to its pet.json");
        }
    }

    private static Path fileFor(Path directory, ResourceLocation location) throws IOException {
        String path = location.getPath();
        Path file = directory.resolve(path.substring(path.lastIndexOf('/') + 1));
        if (!Files.isRegularFile(file)) {
            throw new IOException("missing file: " + file.getFileName());
        }
        return file;
    }

    private static JsonObject readJson(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
