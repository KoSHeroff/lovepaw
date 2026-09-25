package dev.lovepaw.pet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lovepaw.LovePaw;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a {@code pet.json}.
 *
 * <p>The file carries a {@code format_version}. A pet written for a newer
 * format is skipped with a clear message instead of half-loading, which is what
 * lets the format grow later without breaking older clients that meet a pet
 * from a catalogue.
 */
public final class PetDefinitionParser {
    /** The format this build understands. */
    public static final int FORMAT_VERSION = 1;

    private PetDefinitionParser() {
    }

    /**
     * A file a {@code pet.json} points at: the name as it was written, and
     * where that name lands once resolved against the pet's folder.
     */
    public record FileRef(String field, String value, ResourceLocation location) {
    }

    /**
     * Every file the pet is made of, in a fixed order.
     *
     * <p>Loading and hashing both go by this list, so a pet weighs exactly what
     * it draws with: anything else left lying in its folder is neither read nor
     * part of what identifies it.
     */
    public static List<FileRef> filesOf(ResourceLocation id, ResourceLocation folder, JsonObject json) {
        List<FileRef> files = new ArrayList<>(3);
        files.add(refOf(folder, json, id, "model"));
        files.add(refOf(folder, json, id, "texture"));
        if (string(json, "animation_file", null) != null) {
            files.add(refOf(folder, json, id, "animation_file"));
        }
        return files;
    }

    private static FileRef refOf(ResourceLocation folder, JsonObject json, ResourceLocation id, String field) {
        String value = string(json, field, null);
        return new FileRef(field, value, resolve(folder, value, id, field));
    }

    /**
     * @param id     the pet's namespaced id, derived from where the file was found
     * @param folder the folder holding the file, used to resolve relative names
     */
    public static PetDefinition parse(ResourceLocation id, ResourceLocation folder, JsonObject json,
                                      PetSourceKind source, String contentHash) {
        int formatVersion = json.has("format_version") ? json.get("format_version").getAsInt() : FORMAT_VERSION;
        if (formatVersion > FORMAT_VERSION) {
            throw new PetFormatException("pet " + id + " needs format_version " + formatVersion
                    + ", this version of LovePaw understands " + FORMAT_VERSION);
        }

        ResourceLocation model = resolve(folder, string(json, "model", null), id, "model");
        ResourceLocation texture = resolve(folder, string(json, "texture", null), id, "texture");
        String animationsValue = string(json, "animation_file", null);
        ResourceLocation animationFile = animationsValue == null ? null : resolve(folder, animationsValue, id, "animation_file");

        Map<PetAnimationState, String> animations = new EnumMap<>(PetAnimationState.class);
        if (json.has("animations") && json.get("animations").isJsonObject()) {
            JsonObject animationJson = json.getAsJsonObject("animations");
            for (PetAnimationState state : PetAnimationState.values()) {
                JsonElement element = animationJson.get(state.key());
                if (element != null && element.isJsonPrimitive()) {
                    animations.put(state, element.getAsString());
                }
            }
        }

        return new PetDefinition(
                id,
                string(json, "name", id.getPath()),
                string(json, "author", ""),
                string(json, "version", "1.0.0"),
                model,
                texture,
                animationFile,
                Map.copyOf(animations),
                readRender(json.getAsJsonObject("render")),
                readKind(id, json.getAsJsonObject("behaviour")),
                source,
                contentHash);
    }

    private static PetRenderSettings readRender(JsonObject json) {
        if (json == null) {
            return PetRenderSettings.DEFAULT;
        }
        PetRenderSettings base = PetRenderSettings.DEFAULT;
        float scale = number(json, "scale", base.scale());
        if (scale <= 0 || scale > 8) {
            throw new PetFormatException("render.scale must be between 0 and 8, got " + scale);
        }
        return new PetRenderSettings(
                scale,
                number(json, "y_offset", base.yOffset()),
                Math.max(0, number(json, "shadow_radius", base.shadowRadius())),
                bool(json, "glow", base.glow()),
                bool(json, "nameplate", base.nameplate()));
    }

    /**
     * Which animal this pet behaves like. Everything else a pack used to say
     * about behaviour is ignored: those numbers are the game's now.
     *
     * <p>A pet written before kinds existed said {@code "type": "follow"} and
     * marked a flyer with {@code "hover": true}. Both still read the way they
     * always did, so nobody's pet has to be edited to keep working.
     */
    private static PetKind readKind(ResourceLocation id, JsonObject json) {
        if (json == null) {
            return PetKind.GROUND;
        }

        String type = string(json, "type", null);
        PetKind named = PetKind.byName(type);
        if (named != null) {
            return named;
        }
        if (bool(json, "hover", false)) {
            return PetKind.FLYING;
        }
        if (!PetKind.isLegacyType(type)) {
            LovePaw.LOGGER.warn("Pet {} is a '{}', which is not a kind of pet; walking like a cat instead",
                    id, type);
        }
        return PetKind.GROUND;
    }

    private static ResourceLocation resolve(ResourceLocation folder, String value, ResourceLocation id, String field) {
        if (value == null || value.isBlank()) {
            throw new PetFormatException("pet " + id + " is missing '" + field + "'");
        }
        ResourceLocation location = value.indexOf(':') >= 0
                ? ResourceLocation.tryParse(value)
                : ResourceLocation.tryBuild(folder.getNamespace(), folder.getPath() + "/" + value);
        if (location == null) {
            throw new PetFormatException("pet " + id + " has an invalid '" + field + "': " + value);
        }
        return location;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float number(JsonObject json, String key, float fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
    }

    private static String string(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }
}
