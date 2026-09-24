package dev.lovepaw.net;

import dev.lovepaw.config.ServerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is showing which pet, as far as this server knows.
 *
 * <p>Held in memory only: a client announces its pet right after joining, so
 * there is nothing worth persisting and nothing to migrate between versions.
 */
public final class ServerPetState {
    private static final Map<UUID, String> SELECTIONS = new ConcurrentHashMap<>();

    private ServerPetState() {
    }

    /**
     * Records a player's choice.
     *
     * @return the entry to relay to everyone, or null if the server keeps pets
     *         private or refuses this one
     */
    public static LovePawPayloads.Entry select(UUID owner, String petId) {
        String cleaned = petId == null ? "" : petId.trim();
        if (!ServerConfig.isAllowed(cleaned)) {
            return null;
        }

        if (cleaned.isEmpty()) {
            SELECTIONS.remove(owner);
        } else {
            SELECTIONS.put(owner, cleaned);
        }

        return ServerConfig.shareWithOthers() ? new LovePawPayloads.Entry(owner, cleaned) : null;
    }

    public static LovePawPayloads.Entry forget(UUID owner) {
        SELECTIONS.remove(owner);
        return ServerConfig.shareWithOthers() ? new LovePawPayloads.Entry(owner, "") : null;
    }

    /** Everything a joining player needs to render the pets already around them. */
    public static List<LovePawPayloads.Entry> snapshot() {
        if (!ServerConfig.shareWithOthers()) {
            return List.of();
        }
        List<LovePawPayloads.Entry> entries = new ArrayList<>(SELECTIONS.size());
        SELECTIONS.forEach((owner, petId) -> entries.add(new LovePawPayloads.Entry(owner, petId)));
        return List.copyOf(entries);
    }

    public static void clear() {
        SELECTIONS.clear();
    }
}
