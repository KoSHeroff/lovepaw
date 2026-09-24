package dev.lovepaw.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lovepaw.LovePaw;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a server allows. Pets are client-side content, so a server cannot verify
 * what a pet looks like — but it decides whose choice gets relayed to everyone
 * else, which is the part that affects other players.
 */
public final class ServerConfig {
    private static final String FILE_NAME = "lovepaw-server.json";

    private static boolean shareWithOthers = true;
    private static boolean sharePlayerPets = true;
    private static Set<String> allowed = Set.of();
    private static Set<String> denied = Set.of();

    private ServerConfig() {
    }

    public static void load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        JsonObject json = JsonConfigs.read(file);

        shareWithOthers = !json.has("share_with_others") || json.get("share_with_others").getAsBoolean();
        sharePlayerPets = !json.has("share_player_pets") || json.get("share_player_pets").getAsBoolean();
        allowed = readSet(json, "allowed_pets");
        denied = readSet(json, "denied_pets");

        if (!json.has("share_with_others")) {
            save(file);
        }

        LovePaw.LOGGER.info("LovePaw server config: sharing={} player pets={} allowed={} denied={}",
                shareWithOthers, sharePlayerPets, allowed.size(), denied.size());
    }

    private static void save(Path file) {
        JsonObject json = new JsonObject();
        json.addProperty("share_with_others", shareWithOthers);
        json.addProperty("share_player_pets", sharePlayerPets);
        json.add("allowed_pets", toArray(allowed));
        json.add("denied_pets", toArray(denied));
        JsonConfigs.write(file, json);
    }

    private static JsonArray toArray(Set<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static Set<String> readSet(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
                values.add(item.getAsString());
            }
        }
        return Set.copyOf(values);
    }

    /** Whether the server relays pet choices to other players at all. */
    public static boolean shareWithOthers() {
        return shareWithOthers;
    }

    /**
     * Whether the server passes the files of a player-made pet on to the
     * players who need them to see it.
     *
     * <p>Turning this off leaves everyone with the pets they installed
     * themselves, which is what a server wants if it would rather not carry
     * content it cannot look at.
     */
    public static boolean sharePlayerPets() {
        return sharePlayerPets;
    }

    /** An empty allow list means "anything that is not denied". */
    public static boolean isAllowed(String petId) {
        if (petId == null || petId.isEmpty()) {
            return true;
        }
        if (denied.contains(petId)) {
            return false;
        }
        return allowed.isEmpty() || allowed.contains(petId);
    }
}
