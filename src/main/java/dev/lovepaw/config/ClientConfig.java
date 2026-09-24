package dev.lovepaw.config;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

/** The player's own choices: which pet they wear, whose pets they see, and how theirs behaves. */
public final class ClientConfig {
    private static final String FILE_NAME = "lovepaw-client.json";

    private static Path file;
    private static ResourceLocation selectedPet;
    private static boolean showOtherPlayersPets = true;
    private static boolean downloadPlayerPets = true;
    private static PetOverrides overrides = PetOverrides.NONE;

    private static int revision;

    private ClientConfig() {
    }

    public static void load(Path configDir) {
        file = configDir.resolve(FILE_NAME);
        JsonObject json = JsonConfigs.read(file);

        String id = json.has("selected_pet") ? json.get("selected_pet").getAsString() : "";
        selectedPet = id.isBlank() ? null : ResourceLocation.tryParse(id);
        showOtherPlayersPets = !json.has("show_other_players_pets")
                || json.get("show_other_players_pets").getAsBoolean();
        downloadPlayerPets = !json.has("download_player_pets")
                || json.get("download_player_pets").getAsBoolean();
        overrides = PetOverrides.fromJson(
                json.has("overrides") && json.get("overrides").isJsonObject()
                        ? json.getAsJsonObject("overrides")
                        : null);
        revision++;
    }

    public static void save() {
        if (file == null) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("selected_pet", selectedPet == null ? "" : selectedPet.toString());
        json.addProperty("show_other_players_pets", showOtherPlayersPets);
        json.addProperty("download_player_pets", downloadPlayerPets);
        json.add("overrides", overrides.toJson());
        JsonConfigs.write(file, json);
    }

    public static ResourceLocation selectedPet() {
        return selectedPet;
    }

    public static void setSelectedPet(ResourceLocation petId) {
        selectedPet = petId;
        revision++;
        save();
    }

    public static boolean showOtherPlayersPets() {
        return showOtherPlayersPets;
    }

    public static void setShowOtherPlayersPets(boolean show) {
        showOtherPlayersPets = show;
        revision++;
        save();
    }

    /**
     * Whether pets this player does not have are fetched through the server
     * when somebody nearby is wearing one.
     *
     * <p>Turning it off means other players keep whatever pets are installed
     * here and nothing else: no files arrive, and the ones that would have are
     * simply not shown.
     */
    public static boolean downloadPlayerPets() {
        return downloadPlayerPets;
    }

    public static void setDownloadPlayerPets(boolean download) {
        downloadPlayerPets = download;
        revision++;
        save();
    }

    public static PetOverrides overrides() {
        return overrides;
    }

    /**
     * Records a tweak without writing to disk, so dragging a slider does not
     * rewrite the file on every frame. Call {@link #save()} when done.
     */
    public static void setOverrides(PetOverrides value) {
        overrides = value == null ? PetOverrides.NONE : value;
        revision++;
    }

    public static int revision() {
        return revision;
    }
}
