package dev.lovepaw.client;

import dev.lovepaw.LovePaw;
import dev.lovepaw.pet.PetContent;
import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetSourceKind;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where each pet this client holds can be read from again.
 *
 * <p>When somebody else needs a pet, the server asks whoever is wearing it —
 * which means this client has to produce the exact files it was loaded from,
 * possibly an hour later. Keeping every pet's bytes in memory for that would be
 * paying all the time for something that happens rarely, so only the way back
 * to them is kept and the files are read again when they are asked for.
 *
 * <p>Reading again also settles a question memory could not: a pet edited on
 * disk since it was loaded no longer hashes to what this client announced, and
 * is not handed over as that pet.
 */
public final class PetOrigins {
    /** How to find a pet's files, whichever kind of place they live in. */
    private record Origin(ResourceLocation id, ResourceLocation folder, Path directory) {
    }

    private static final Map<String, Origin> BY_HASH = new ConcurrentHashMap<>();
    private static final Map<String, PetSourceKind> SOURCES = new ConcurrentHashMap<>();

    private PetOrigins() {
    }

    /**
     * @param folder    where the pet's own files sit, as its pet.json resolves
     *                  names against
     * @param directory that same folder on disk, or null when the files live
     *                  in a resource pack
     */
    public static void remember(PetDefinition definition, ResourceLocation folder, Path directory) {
        String hash = definition.contentHash();
        if (hash.isEmpty()) {
            return;
        }
        BY_HASH.put(hash, new Origin(definition.id(), folder, directory));
        SOURCES.put(hash, definition.source());
    }

    /** Drops everything from one source, which is about to be read again. */
    public static void forgetSource(PetSourceKind source) {
        SOURCES.entrySet().removeIf(entry -> {
            if (entry.getValue() != source) {
                return false;
            }
            BY_HASH.remove(entry.getKey());
            return true;
        });
    }

    /**
     * The files of a pet this client holds, or null if they cannot be produced
     * any more. Never returns files under a hash they do not have.
     */
    public static PetContent contentOf(String contentHash) {
        Origin origin = BY_HASH.get(contentHash);
        if (origin == null) {
            return null;
        }
        try {
            PetBundle.Source source = origin.directory() == null
                    ? PetBundle.inPacks(Minecraft.getInstance().getResourceManager())
                    : PetBundle.filesIn(origin.directory());
            PetContent content = PetBundle.contentOf(origin.id(), origin.folder(), source);
            if (!content.is(contentHash)) {
                LovePaw.LOGGER.debug("Pet {} has changed since it was loaded", origin.id());
                return null;
            }
            return content;
        } catch (Exception e) {
            LovePaw.LOGGER.debug("Could not read pet {} again: {}", origin.id(), e.toString());
            return null;
        }
    }
}
