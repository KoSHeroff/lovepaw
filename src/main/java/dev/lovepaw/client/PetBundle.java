package dev.lovepaw.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lovepaw.model.anim.Animation;
import dev.lovepaw.model.anim.AnimationParser;
import dev.lovepaw.model.geo.GeoBaker;
import dev.lovepaw.model.geo.GeoParser;
import dev.lovepaw.model.geo.baked.BakedGeoModel;
import dev.lovepaw.pet.PetContent;
import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetDefinitionParser;
import dev.lovepaw.pet.PetSourceKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a handful of files into a pet, wherever those files come from.
 *
 * <p>A resource pack, the player's own folder and — once pets are shared —
 * bytes that arrived from another player all end up here. Each one only has to
 * say how to fetch a file by name; everything after that is the same, so a pet
 * that came down the wire is read exactly as strictly as one the player put
 * there themselves, which is the case that matters.
 *
 * <p>Reading is also where a pet gets its hash: the files are collected as they
 * are read and hashed together, so the hash covers precisely what the pet draws
 * with and nothing else.
 */
public final class PetBundle {
    /** Where the files come from. */
    public interface Source {
        /**
         * @param value    the name exactly as {@code pet.json} wrote it
         * @param resolved that name resolved against the pet's own folder
         */
        byte[] read(String value, ResourceLocation resolved) throws IOException;
    }

    /**
     * A pet read and ready to register, with its texture still bytes because
     * handing it to the texture manager has to happen on the render thread.
     */
    public record Loaded(PetDefinition definition, PetAssets assets, byte[] texture, PetContent content) {
    }

    private PetBundle() {
    }

    public static Loaded read(ResourceLocation id, ResourceLocation folder,
                              PetSourceKind kind, Source source) throws IOException {
        PetContent content = contentOf(id, folder, source);
        if (kind == PetSourceKind.REMOTE && !content.isShareable()) {
            throw new IOException("a pet from another player may not weigh this much");
        }

        JsonObject json = json(content, PetContent.DEFINITION);
        PetDefinition definition = PetDefinitionParser.parse(id, folder, json, kind, content.hash());

        BakedGeoModel model = GeoBaker.bake(GeoParser.parse(json(content, nameOf(definition.model()))));

        Map<String, Animation> animations = Map.of();
        if (definition.animationFile() != null) {
            animations = Map.copyOf(AnimationParser.parse(json(content, nameOf(definition.animationFile()))));
        }

        byte[] texture = content.file(nameOf(definition.texture()));
        return new Loaded(definition, new PetAssets(model, animations), texture, content);
    }

    /**
     * The files alone, without building anything out of them. This is how a
     * pet is read again when another player asks for it: the hash it comes back
     * with is checked against the one that was promised, so a pet edited on
     * disk since it was loaded is simply not handed over.
     */
    public static PetContent contentOf(ResourceLocation id, ResourceLocation folder,
                                       Source source) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        byte[] definitionBytes = source.read(PetContent.DEFINITION, child(folder, PetContent.DEFINITION));
        collect(files, PetContent.DEFINITION, definitionBytes);

        JsonObject json = JsonParser.parseString(
                new String(definitionBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        for (PetDefinitionParser.FileRef file : PetDefinitionParser.filesOf(id, folder, json)) {
            collect(files, nameOf(file.location()), source.read(file.value(), file.location()));
        }
        return PetContent.of(files);
    }

    /** Files read straight out of a folder on disk, and nowhere else. */
    public static Source filesIn(Path directory) {
        return (value, resolved) -> {
            Path file = directory.resolve(plainName(value));
            if (!Files.isRegularFile(file)) {
                throw new IOException("missing file: " + value);
            }
            return Files.readAllBytes(file);
        };
    }

    /**
     * Files read through the resource manager, which is what lets a pack pet
     * point at another pack's model or texture the way any other asset can.
     */
    public static Source inPacks(ResourceManager resourceManager) {
        return (value, resolved) -> {
            Resource resource = resourceManager.getResource(resolved)
                    .orElseThrow(() -> new IOException("missing file: " + resolved));
            try (InputStream in = resource.open()) {
                return in.readAllBytes();
            }
        };
    }

    /**
     * A source over files already in memory, which is how a pet that arrived
     * from another player is read.
     */
    public static Source of(Map<String, byte[]> files) {
        return (value, resolved) -> {
            byte[] bytes = files.get(plainName(value));
            if (bytes == null) {
                throw new IOException("missing file: " + value);
            }
            return bytes;
        };
    }

    /** The file name a location ends in, which is how bundles key them. */
    public static String nameOf(ResourceLocation location) {
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** That same name, inside a pet's folder. */
    public static ResourceLocation child(ResourceLocation folder, String name) {
        return ResourceLocation.fromNamespaceAndPath(folder.getNamespace(), folder.getPath() + "/" + name);
    }

    /**
     * A pet that is not a resource pack may only name files sitting beside its
     * {@code pet.json}. The {@code namespace:path} form a pack is allowed would
     * otherwise let a downloaded pet claim {@code minecraft:} and have its
     * texture registered over one of the game's own.
     */
    public static String plainName(String value) throws IOException {
        if (value.indexOf(':') >= 0 || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IOException("'" + value + "' is not a file sitting next to pet.json");
        }
        return value;
    }

    private static void collect(Map<String, byte[]> files, String name, byte[] bytes) throws IOException {
        byte[] existing = files.putIfAbsent(name, bytes);
        if (existing != null && !Arrays.equals(existing, bytes)) {
            throw new IOException("two different files are both called " + name);
        }
    }

    private static JsonObject json(PetContent content, String name) {
        return JsonParser.parseString(
                new String(content.file(name), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
