package dev.lovepaw.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lovepaw.LovePaw;
import dev.lovepaw.pet.PetContent;
import dev.lovepaw.pet.PetDefinition;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
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

    /** Where folder pets are read from, or null when the mod runs without one. */
    private final Path localFolder;

    /** Textures uploaded for folder pets, released on the next reload. */
    private final List<ResourceLocation> localTextures = new ArrayList<>();

    public PetResourceLoader(Path gameDirectory) {
        this.localFolder = gameDirectory == null ? null : PetLocalLoader.folderIn(gameDirectory);
    }

    /** A pet and its baked assets, prepared off-thread before being applied. */
    public record LoadedPet(PetDefinition definition, PetAssets assets, ResourceLocation folder) {
    }

    /** Everything one reload found, from both sources. */
    public record Loaded(List<LoadedPet> fromPacks, List<PetLocalLoader.LocalPet> fromFolder) {
    }

    @Override
    protected Loaded prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<LoadedPet> fromPacks = new ArrayList<>();

        Map<ResourceLocation, Resource> definitions = resourceManager.listResources(
                PETS_FOLDER,
                location -> location.getPath().endsWith("/" + PetContent.DEFINITION));

        for (ResourceLocation file : definitions.keySet()) {
            try {
                fromPacks.add(load(resourceManager, file));
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
        cache.clearSource(PetSourceKind.RESOURCE_PACK);
        cache.clearSource(PetSourceKind.LOCAL);
        PetOrigins.forgetSource(PetSourceKind.RESOURCE_PACK);
        PetOrigins.forgetSource(PetSourceKind.LOCAL);

        for (LoadedPet pet : loaded.fromPacks()) {
            registry.register(pet.definition());
            cache.put(pet.definition().id(), PetSourceKind.RESOURCE_PACK, pet.assets());
            PetOrigins.remember(pet.definition(), pet.folder(), null);
        }

        applyLocal(loaded.fromFolder(), registry, cache);

        registry.notifyReloaded();
    }

    /**
     * Folder pets arrive with their texture still unread bytes: it has to be
     * handed to the texture manager here, on the render thread, under the id
     * the renderer will bind.
     */
    private void applyLocal(List<PetLocalLoader.LocalPet> pets, PetRegistry registry, PetAssetCache cache) {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation stale : localTextures) {
            textures.release(stale);
        }
        localTextures.clear();

        for (PetLocalLoader.LocalPet pet : pets) {
            ResourceLocation texture = pet.definition().texture();
            try (InputStream in = new ByteArrayInputStream(pet.texture())) {
                textures.register(texture, new DynamicTexture(NativeImage.read(in)));
                localTextures.add(texture);
            } catch (IOException e) {
                LovePaw.LOGGER.error("Skipping pet {}: its texture would not load: {}",
                        pet.definition().id(), e.toString());
                continue;
            }
            registry.register(pet.definition());
            cache.put(pet.definition().id(), PetSourceKind.LOCAL, pet.assets());
            PetOrigins.remember(pet.definition(), pet.folder(), pet.directory());
        }
    }

    private static LoadedPet load(ResourceManager resourceManager, ResourceLocation file) throws IOException {
        ResourceLocation folder = folderOf(file);
        PetBundle.Loaded loaded = PetBundle.read(
                idOf(folder), folder, PetSourceKind.RESOURCE_PACK, PetBundle.inPacks(resourceManager));
        return new LoadedPet(loaded.definition(), loaded.assets(), folder);
    }

    private static ResourceLocation folderOf(ResourceLocation file) {
        String path = file.getPath();
        return ResourceLocation.fromNamespaceAndPath(
                file.getNamespace(),
                path.substring(0, path.length() - PetContent.DEFINITION.length() - 1));
    }

    private static ResourceLocation idOf(ResourceLocation folder) {
        String path = folder.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return ResourceLocation.fromNamespaceAndPath(folder.getNamespace(), name);
    }
}
