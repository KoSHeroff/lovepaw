package dev.lovepaw.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Baked models and animations, by pet id. Refilled on every resource reload. */
public final class PetAssetCache {
    private static final PetAssetCache INSTANCE = new PetAssetCache();

    private final Map<ResourceLocation, PetAssets> assets = new ConcurrentHashMap<>();

    private PetAssetCache() {
    }

    public static PetAssetCache get() {
        return INSTANCE;
    }

    public void put(ResourceLocation id, PetAssets petAssets) {
        assets.put(id, petAssets);
    }

    public PetAssets get(ResourceLocation id) {
        return assets.get(id);
    }

    public void clear() {
        assets.clear();
    }
}
