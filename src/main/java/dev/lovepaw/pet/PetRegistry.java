package dev.lovepaw.pet;

import dev.lovepaw.LovePaw;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every pet this client knows about, whatever it came from.
 *
 * <p>Sources are deliberately not hard-coded here: definitions are handed in by
 * whoever found them — the resource pack loader today, a downloaded-pack loader
 * later — so adding a source never means touching the picker, the renderer or
 * the network code.
 *
 * <p>This is also the seam the public add-on API will sit on: {@link #register}
 * is already the single way a pet enters the game.
 */
public final class PetRegistry {
    private static final PetRegistry INSTANCE = new PetRegistry();

    private final Map<ResourceLocation, PetDefinition> pets = new LinkedHashMap<>();
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();

    private PetRegistry() {
    }

    public static PetRegistry get() {
        return INSTANCE;
    }

    /**
     * Adds a pet, replacing one with the same id. Later sources win, which is
     * what lets a resource pack override a built-in pet.
     */
    public synchronized void register(PetDefinition definition) {
        pets.put(definition.id(), definition);
    }

    /** Drops everything from a single source, used when that source reloads. */
    public synchronized void clearSource(PetSourceKind source) {
        pets.values().removeIf(pet -> pet.source() == source);
    }

    public synchronized void clear() {
        pets.clear();
    }

    public synchronized Optional<PetDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(pets.get(id));
    }

    public synchronized boolean contains(ResourceLocation id) {
        return pets.containsKey(id);
    }

    public synchronized int size() {
        return pets.size();
    }

    /** All pets, ordered by name so the picker is stable between launches. */
    public synchronized List<PetDefinition> sorted() {
        List<PetDefinition> all = new ArrayList<>(pets.values());
        all.sort(Comparator.comparing(PetDefinition::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(pet -> pet.id().toString()));
        return all;
    }

    public synchronized Collection<PetDefinition> all() {
        return List.copyOf(pets.values());
    }

    /** Called after any source finishes loading, so open screens can refresh. */
    public void addReloadListener(Runnable listener) {
        reloadListeners.add(listener);
    }

    public void notifyReloaded() {
        LovePaw.LOGGER.info("Loaded {} pet(s)", size());
        for (Runnable listener : reloadListeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                LovePaw.LOGGER.error("Pet reload listener failed", e);
            }
        }
    }
}
