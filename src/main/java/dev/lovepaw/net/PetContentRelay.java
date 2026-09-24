package dev.lovepaw.net;

import dev.lovepaw.LovePaw;
import dev.lovepaw.config.ServerConfig;
import dev.lovepaw.pet.PetContent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hands pets from the players who have them to the players who need them.
 *
 * <p>Players never talk to each other, so the server is the only way a pet can
 * cross between them. It carries the files without ever reading them: it does
 * not know what a model is and never loads one. All it does is ask whoever is
 * wearing a pet for its bytes, check that the bytes hash to what was asked for,
 * and pass them on to whoever was waiting.
 *
 * <p>Nothing is fetched in advance. A pet only moves when somebody actually
 * needs it, and once it has moved it is kept for as long as it is worth
 * keeping, so the tenth player to walk past costs nothing.
 */
public final class PetContentRelay {
    /** Kept in memory for whoever asks next; dropped oldest first past this. */
    private static final int MAX_CACHE_BYTES = 32 * 1024 * 1024;

    /** How many pets one player may be queued up for at once. */
    private static final int MAX_WAITING_PER_PLAYER = 16;

    /** Everything the relay needs from the server it runs on. */
    public interface Courier {
        /** Asks a player for the pet they are wearing. */
        void ask(UUID owner, String contentHash);

        /** Sends one slice of a pet to a player. */
        void give(UUID player, LovePawPayloads.PetChunk chunk);

        /** Tells a player that pet is not coming. */
        void refuse(UUID player, String contentHash);

        /** Players online right now wearing that pet. */
        Collection<UUID> wearing(String contentHash);
    }

    private final Courier courier;

    /** Pets we hold, least recently wanted first. */
    private final LinkedHashMap<String, PetContent> cache = new LinkedHashMap<>(16, 0.75f, true);
    private int cachedBytes;

    /** Who is waiting for a pet we do not have yet. */
    private final Map<String, Set<UUID>> waiting = new HashMap<>();

    /** Who we asked for it, so that nobody else's upload is believed. */
    private final Map<String, UUID> asked = new HashMap<>();

    /**
     * Who has already been asked for a pet and not produced it. Nobody is asked
     * twice for the same pet: a player whose files are gone, or whose answer
     * did not hash to what it should, would otherwise be asked forever.
     */
    private final Map<String, Set<UUID>> tried = new HashMap<>();

    /** What each player is in the middle of sending us. */
    private final Map<UUID, PetTransfer> uploads = new HashMap<>();

    public PetContentRelay(Courier courier) {
        this.courier = courier;
    }

    /** A player needs a pet they were told about. */
    public void onRequest(UUID from, String contentHash) {
        if (!ServerConfig.sharePlayerPets() || !looksLikeHash(contentHash)) {
            courier.refuse(from, contentHash);
            return;
        }

        PetContent held = cache.get(contentHash);
        if (held != null) {
            deliver(from, held);
            return;
        }

        if (countWaitingFor(from) >= MAX_WAITING_PER_PLAYER) {
            courier.refuse(from, contentHash);
            return;
        }
        waiting.computeIfAbsent(contentHash, hash -> new HashSet<>()).add(from);

        if (!asked.containsKey(contentHash)) {
            askSomeone(contentHash);
        }
    }

    /** A slice of a pet we asked for. */
    public void onChunk(UUID from, LovePawPayloads.PetChunk chunk) {
        String contentHash = chunk.contentHash();
        if (!from.equals(asked.get(contentHash))) {
            // Nobody was asked for this, so nobody gets to fill our memory with
            // it. A late slice of a pet we already have lands here too.
            return;
        }

        try {
            PetTransfer transfer = uploads.get(from);
            if (transfer == null || !transfer.contentHash().equals(contentHash)) {
                transfer = PetTransfer.awaiting(contentHash, chunk.count());
                uploads.put(from, transfer);
            }
            if (!transfer.accept(chunk)) {
                return;
            }
            uploads.remove(from);
            keep(transfer.finish());
            handOut(contentHash);
        } catch (IOException e) {
            LovePaw.LOGGER.debug("Pet {} did not come through from {}: {}", contentHash, from, e.getMessage());
            uploads.remove(from);
            asked.remove(contentHash);
            // Somebody else may be wearing the same pet and able to send it.
            askSomeone(contentHash);
        }
    }

    /** A player left, so what they owed is owed by somebody else or by nobody. */
    public void onLeave(UUID player) {
        uploads.remove(player);
        waiting.values().forEach(players -> players.remove(player));
        waiting.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        List<String> orphaned = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : asked.entrySet()) {
            if (entry.getValue().equals(player)) {
                orphaned.add(entry.getKey());
            }
        }
        for (String contentHash : orphaned) {
            asked.remove(contentHash);
            askSomeone(contentHash);
        }
    }

    public void clear() {
        cache.clear();
        cachedBytes = 0;
        waiting.clear();
        asked.clear();
        tried.clear();
        uploads.clear();
    }

    /** Pets held in memory, which is the state worth watching. */
    public int cachedPets() {
        return cache.size();
    }

    private void askSomeone(String contentHash) {
        Set<UUID> wanted = waiting.get(contentHash);
        if (wanted == null || wanted.isEmpty()) {
            return;
        }
        Set<UUID> alreadyAsked = tried.computeIfAbsent(contentHash, hash -> new HashSet<>());
        for (UUID owner : courier.wearing(contentHash)) {
            if (!wanted.contains(owner) && alreadyAsked.add(owner)) {
                asked.put(contentHash, owner);
                courier.ask(owner, contentHash);
                return;
            }
        }
        // Whoever was wearing it is gone, is one of the players asking, or has
        // already had their chance.
        wanted.forEach(player -> courier.refuse(player, contentHash));
        waiting.remove(contentHash);
        tried.remove(contentHash);
    }

    private void handOut(String contentHash) {
        asked.remove(contentHash);
        tried.remove(contentHash);
        Set<UUID> wanted = waiting.remove(contentHash);
        PetContent content = cache.get(contentHash);
        if (wanted == null || content == null) {
            return;
        }
        wanted.forEach(player -> deliver(player, content));
    }

    private void deliver(UUID player, PetContent content) {
        for (LovePawPayloads.PetChunk chunk : PetTransfer.split(content)) {
            courier.give(player, chunk);
        }
    }

    private void keep(PetContent content) {
        if (!content.isShareable()) {
            return;
        }
        if (cache.put(content.hash(), content) == null) {
            cachedBytes += content.size();
        }
        Iterator<Map.Entry<String, PetContent>> oldest = cache.entrySet().iterator();
        while (cachedBytes > MAX_CACHE_BYTES && oldest.hasNext()) {
            Map.Entry<String, PetContent> entry = oldest.next();
            if (entry.getKey().equals(content.hash())) {
                continue;
            }
            cachedBytes -= entry.getValue().size();
            oldest.remove();
        }
    }

    private int countWaitingFor(UUID player) {
        int count = 0;
        for (Set<UUID> players : waiting.values()) {
            if (players.contains(player)) {
                count++;
            }
        }
        return count;
    }

    /** Anything that is not a hash was not sent by this mod. */
    private static boolean looksLikeHash(String contentHash) {
        if (contentHash == null || contentHash.length() != 64) {
            return false;
        }
        for (int i = 0; i < contentHash.length(); i++) {
            char c = contentHash.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean letter = c >= 'a' && c <= 'f';
            if (!digit && !letter) {
                return false;
            }
        }
        return true;
    }
}
