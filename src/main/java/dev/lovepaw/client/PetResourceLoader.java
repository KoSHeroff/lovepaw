package dev.lovepaw.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds pets and loads them, from resource packs and from the player's own
 * folder.
 *
 * <p>A pack pet is a folder {@code assets/<namespace>/lovepaw/pets/<name>/}
 * holding {@code pet.json} and whatever it points at. The folder name becomes
 * the pet's id, so nothing has to be registered in code and two packs can ship
 * pets with the same name under different namespaces. A folder pet works the
 * same way but lives in {@code lovepaw/pets/} in the game directory — see
 * {@link PetLocalLoader}.
 *
 * <p>A pet that fails to load is logged and skipped. One broken pack must not
 * cost the player the pets that do work.
 */
public class PetResourceLoader extends SimplePreparableReloadListener<PetResourceLoader.Loaded> {
    public static final String PETS_FOLDER = "lovepaw/pets";
    private static final String DEFINITION_FILE = "pet.json";

    /** Where folder pets are read from, or null when the mod runs without one. */
    private final Path localFolder;

    /** Textures uploaded for folder pets, released on the next reload. */
    private final List<ResourceLocation> localTextures = new ArrayList<>();

    public PetResourceLoader(Path gameDirectory) {
        this.localFolder = gameDirectory == null ? null : PetLocalLoader.folderIn(gameDirectory);
    }

    /** A pet and its baked assets, prepared off-thread before being applied. */
    public record LoadedPet(PetDefinition definition, PetAssets assets) {
    }

    /** Everything one reload found, from both sources. */
    public record Loaded(List<LoadedPet> fromPacks, List<PetLocalLoader.LocalPet> fromFolder) {
    }

    @Override
    protected Loaded prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<LoadedPet> fromPacks = new ArrayList<>();

        Map<ResourceLocation, Resource> definitions = resourceManager.listResources(
                PETS_FOLDER,
                location -> location.getPath().endsWith("/" + DEFINITION_FILE));

        for (Map.Entry<ResourceLocation, Resource> entry : definitions.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                fromPacks.add(load(resourceManager, file, entry.getValue()));
            } catch (Exception e) {
                LovePaw.LOGGER.error("Skipping pet {}: {}", file, e.getMessage());
                LovePaw.LOGGER.debug("Pet load failure", e);
            }
        }

        List<PetLocalLoader.LocalPet> fromFolder =
                localFolder == null ? List.of() : PetLocalLoader.scan(localFolder);

        return new Loaded(fromPacks, fromFolder);
    }

    @Override
    protected void apply(Loaded loaded, ResourceManager resourceManager, ProfilerFiller profiler) {
        PetRegistry registry = PetRegistry.get();
        registry.clearSource(PetSourceKind.RESOURCE_PACK);
        registry.clearSource(PetSourceKind.LOCAL);
        PetAssetCache cache = PetAssetCache.get();
        cache.clear();

        for (LoadedPet pet : loaded.fromPacks()) {
            registry.register(pet.definition());
            cache.put(pet.definition().id(), pet.assets());
        }

        applyLocal(loaded.fromFolder(), registry, cache);

        registry.notifyReloaded();
    }

    /**
     * Folder pets arrive with their texture still on disk: it has to be handed
     * to the texture manager here, on the render thread, under the id the
     * renderer will bind.
     */
    private void applyLocal(List<PetLocalLoader.LocalPet> pets, PetRegistry registry, PetAssetCache cache) {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation stale : localTextures) {
            textures.release(stale);
        }
        localTextures.clear();

        for (PetLocalLoader.LocalPet pet : pets) {
            ResourceLocation texture = pet.definition().texture();
            try (InputStream in = Files.newInputStream(pet.texture())) {
                textures.register(texture, new DynamicTexture(NativeImage.read(in)));
                localTextures.add(texture);
            } catch (IOException e) {
                LovePaw.LOGGER.error("Skipping pet {}: its texture would not load: {}",
                        pet.definition().id(), e.toString());
                continue;
            }
            registry.register(pet.definition());
            cache.put(pet.definition().id(), pet.assets());
        }
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
