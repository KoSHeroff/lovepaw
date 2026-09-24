package dev.lovepaw.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lovepaw.LovePaw;
import dev.lovepaw.model.anim.Animation;
import dev.lovepaw.model.anim.AnimationParser;
import dev.lovepaw.model.geo.GeoBaker;
import dev.lovepaw.model.geo.GeoModel;
import dev.lovepaw.model.geo.GeoParser;
import dev.lovepaw.model.geo.baked.BakedGeoModel;
import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetDefinitionParser;
import dev.lovepaw.pet.PetRegistry;
import dev.lovepaw.pet.PetSourceKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds pets in resource packs and mod jars and loads them.
 *
 * <p>A pet is a folder: {@code assets/<namespace>/lovepaw/pets/<name>/} holding
 * {@code pet.json} and whatever it points at. The folder name becomes the pet's
 * id, so nothing has to be registered in code and two packs can ship pets with
 * the same name under different namespaces.
 *
 * <p>A pet that fails to load is logged and skipped. One broken pack must not
 * cost the player the pets that do work.
 */
public class PetResourceLoader extends SimplePreparableReloadListener<List<PetResourceLoader.LoadedPet>> {
    public static final String PETS_FOLDER = "lovepaw/pets";
    private static final String DEFINITION_FILE = "pet.json";

    /** A pet and its baked assets, prepared off-thread before being applied. */
    public record LoadedPet(PetDefinition definition, PetAssets assets) {
    }

    @Override
    protected List<LoadedPet> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<LoadedPet> loaded = new ArrayList<>();

        Map<ResourceLocation, Resource> definitions = resourceManager.listResources(
                PETS_FOLDER,
                location -> location.getPath().endsWith("/" + DEFINITION_FILE));

        for (Map.Entry<ResourceLocation, Resource> entry : definitions.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                loaded.add(load(resourceManager, file, entry.getValue()));
            } catch (Exception e) {
                LovePaw.LOGGER.error("Skipping pet {}: {}", file, e.getMessage());
                LovePaw.LOGGER.debug("Pet load failure", e);
            }
        }

        return loaded;
    }

    @Override
    protected void apply(List<LoadedPet> loaded, ResourceManager resourceManager, ProfilerFiller profiler) {
        PetRegistry registry = PetRegistry.get();
        registry.clearSource(PetSourceKind.RESOURCE_PACK);
        PetAssetCache cache = PetAssetCache.get();
        cache.clear();

        for (LoadedPet pet : loaded) {
            registry.register(pet.definition());
            cache.put(pet.definition().id(), pet.assets());
        }

        registry.notifyReloaded();
    }

    private static LoadedPet load(ResourceManager resourceManager, ResourceLocation file, Resource resource) throws IOException {
        ResourceLocation folder = folderOf(file);
        ResourceLocation id = idOf(folder);

        JsonObject json = readJson(resource);
        PetDefinition definition = PetDefinitionParser.parse(id, folder, json, PetSourceKind.RESOURCE_PACK);

        GeoModel geometry = GeoParser.parse(readJson(require(resourceManager, definition.model(), "model")));
        BakedGeoModel model = GeoBaker.bake(geometry);

        Map<String, Animation> animations = new LinkedHashMap<>();
        if (definition.animationFile() != null) {
            animations.putAll(AnimationParser.parse(
                    readJson(require(resourceManager, definition.animationFile(), "animation file"))));
        }

        require(resourceManager, definition.texture(), "texture");

        return new LoadedPet(definition, new PetAssets(model, Map.copyOf(animations)));
    }

    private static Resource require(ResourceManager resourceManager, ResourceLocation location, String what) throws IOException {
        return resourceManager.getResource(location)
                .orElseThrow(() -> new IOException("missing " + what + ": " + location));
    }

    private static JsonObject readJson(Resource resource) throws IOException {
        try (BufferedReader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static ResourceLocation folderOf(ResourceLocation file) {
        String path = file.getPath();
        return ResourceLocation.fromNamespaceAndPath(
                file.getNamespace(),
                path.substring(0, path.length() - DEFINITION_FILE.length() - 1));
    }

    private static ResourceLocation idOf(ResourceLocation folder) {
        String path = folder.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return ResourceLocation.fromNamespaceAndPath(folder.getNamespace(), name);
    }
}
