package dev.lovepaw.client;

import dev.lovepaw.pet.PetSourceKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baked models and animations, by pet id.
 *
 * <p>What each pet came from is kept alongside it, so a resource reload can put
 * back the pets that a reload actually reloads and leave the rest standing. A
 * pet another player sent us is not in any pack and could not be read again
 * from one: dropping it on F3+T would take the pet off that player's shoulder
 * until they reconnected.
 */
public final class PetAssetCache {
    private static final PetAssetCache INSTANCE = new PetAssetCache();

    private final Map<ResourceLocation, Held> assets = new ConcurrentHashMap<>();

    private record Held(PetSourceKind source, PetAssets assets) {
    }

    private PetAssetCache() {
    }

    public static PetAssetCache get() {
        return INSTANCE;
    }

    public void put(ResourceLocation id, PetSourceKind source, PetAssets petAssets) {
        assets.put(id, new Held(source, petAssets));
    }

    public PetAssets get(ResourceLocation id) {
        Held held = assets.get(id);
        return held == null ? null : held.assets();
    }

    /** Drops everything from one source, used when that source reloads. */
    public void clearSource(PetSourceKind source) {
        assets.values().removeIf(held -> held.source() == source);
    }

    public void clear() {
        assets.clear();
    }
}
