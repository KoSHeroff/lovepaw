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
    /** A hex SHA-256 is 64 characters; anything longer is not one. */
    private static final int MAX_HASH_LENGTH = 64;

    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();

    /** What a player said they are wearing: the pet's id and which pet that is. */
    private record Selection(String petId, String contentHash) {
    }

    private ServerPetState() {
    }

    /**
     * Records a player's choice.
     *
     * @return the entry to relay to everyone, or null if the server keeps pets
     *         private or refuses this one
     */
    public static LovePawPayloads.Entry select(UUID owner, String petId, String contentHash) {
        String cleaned = petId == null ? "" : petId.trim();
        if (!ServerConfig.isAllowed(cleaned)) {
            return null;
        }
        // The hash is relayed, never trusted: the server has no way to check it
        // and no reason to, since whoever receives it compares it against the
        // pet they actually hold.
        String hash = hash(contentHash);

        if (cleaned.isEmpty()) {
            SELECTIONS.remove(owner);
        } else {
            SELECTIONS.put(owner, new Selection(cleaned, hash));
        }

        return ServerConfig.shareWithOthers() ? new LovePawPayloads.Entry(owner, cleaned, hash) : null;
    }

    public static LovePawPayloads.Entry forget(UUID owner) {
        SELECTIONS.remove(owner);
        return ServerConfig.shareWithOthers() ? new LovePawPayloads.Entry(owner, "", "") : null;
    }

    /** Keeps a client from making the server hold, or relay, anything long. */
    private static String hash(String contentHash) {
        if (contentHash == null || contentHash.length() > MAX_HASH_LENGTH) {
            return "";
        }
        return contentHash;
    }

    /**
     * Players wearing a particular pet, which is who can be asked for its
     * files. Most of the time that is whoever the requester saw wearing it,
     * but anyone else with the same pet will do just as well.
     */
    public static List<UUID> wearing(String contentHash) {
        if (contentHash == null || contentHash.isEmpty()) {
            return List.of();
        }
        List<UUID> owners = new ArrayList<>();
        SELECTIONS.forEach((owner, selection) -> {
            if (contentHash.equals(selection.contentHash())) {
                owners.add(owner);
            }
        });
        return owners;
    }

    /** Everything a joining player needs to render the pets already around them. */
    public static List<LovePawPayloads.Entry> snapshot() {
        if (!ServerConfig.shareWithOthers()) {
            return List.of();
        }
        List<LovePawPayloads.Entry> entries = new ArrayList<>(SELECTIONS.size());
        SELECTIONS.forEach((owner, selection) -> entries.add(
                new LovePawPayloads.Entry(owner, selection.petId(), selection.contentHash())));
        return List.copyOf(entries);
    }

    public static void clear() {
        SELECTIONS.clear();
    }
}
