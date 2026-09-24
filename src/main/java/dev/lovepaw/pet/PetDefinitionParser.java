package dev.lovepaw.pet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
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
     * @param id     the pet's namespaced id, derived from where the file was found
     * @param folder the folder holding the file, used to resolve relative names
     */
    public static PetDefinition parse(ResourceLocation id, ResourceLocation folder, JsonObject json, PetSourceKind source) {
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
                readBehaviour(json.getAsJsonObject("behaviour")),
                source);
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

    private static PetBehaviourSettings readBehaviour(JsonObject json) {
        PetBehaviourSettings base = PetBehaviourSettings.DEFAULT;
        if (json == null) {
            return base;
        }
        return new PetBehaviourSettings(
                string(json, "type", base.type()),
                clamp(number(json, "anchor_radius", base.anchorRadius()), 1f, 48f),
                Math.max(0, number(json, "stop_distance", base.stopDistance())),
                clamp(number(json, "teleport_distance", base.teleportDistance()), 4, 64),
                clamp(number(json, "walk_speed", base.walkSpeed()), 0.01f, 1f),
                clamp(number(json, "run_speed", base.runSpeed()), 0.01f, 1f),
                Math.max(0, number(json, "run_distance", base.runDistance())),
                clamp(number(json, "gravity", base.gravity()), 0f, 1f),
                clamp(number(json, "step_height", base.stepHeight()), 0f, 3f),
                clamp(number(json, "jump_power", base.jumpPower()), 0f, 1f),
                clamp(number(json, "width", base.width()), 0.05f, 4f),
                clamp(number(json, "height", base.height()), 0.05f, 4f),
                bool(json, "can_swim", base.canSwim()),
                bool(json, "hover", base.hover()),
                clamp(number(json, "hover_height", base.hoverHeight()), 0f, 8f),
                clamp(number(json, "hover_drift", base.hoverDrift()), 0f, 4f),
                bool(json, "wander", base.wander()),
                clamp(number(json, "wander_radius", base.wanderRadius()), 1.5f, 16f),
                clamp(number(json, "wander_speed", base.wanderSpeed()), 0.01f, 1f),
                clamp(number(json, "sit_chance", base.sitChance()), 0f, 1f),
                clamp(number(json, "curiosity", base.curiosity()), 0f, 1f),
                clamp(number(json, "interest_radius", base.interestRadius()), 0f, 24f),
                clamp(number(json, "playfulness", base.playfulness()), 0f, 1f),
                clamp(number(json, "prediction_seconds", base.predictionSeconds()), 0f, 3f));
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
