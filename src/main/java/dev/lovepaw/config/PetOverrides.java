package dev.lovepaw.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lovepaw.pet.PetBehaviourSettings;
import dev.lovepaw.pet.PetRenderSettings;

/**
 * The player's own tweaks to how their pet behaves, laid over whatever the pet
 * pack says.
 *
 * <p>Every field may be null, meaning "whatever the pet asks for". That is the
 * whole point: a pack author's carefully chosen numbers stay in force until the
 * player actually decides otherwise, and clearing a tweak gives the pet its own
 * behaviour back rather than some global default.
 *
 * <p>These apply to the player's own pet only. Other players' pets keep the
 * settings their own packs give them.
 */
public record PetOverrides(
        Float anchorRadius,
        Float predictionSeconds,
        Float wanderRadius,
        Float sitChance,
        Boolean wander,
        Float scale
) {
    public static final PetOverrides NONE = new PetOverrides(null, null, null, null, null, null);

    public boolean isEmpty() {
        return anchorRadius == null && predictionSeconds == null && wanderRadius == null
                && sitChance == null && wander == null && scale == null;
    }

    public PetBehaviourSettings applyTo(PetBehaviourSettings base) {
        if (isEmpty()) {
            return base;
        }
        return new PetBehaviourSettings(
                base.type(),
                anchorRadius != null ? anchorRadius : base.anchorRadius(),
                base.stopDistance(),
                base.teleportDistance(),
                base.walkSpeed(),
                base.runSpeed(),
                base.runDistance(),
                base.gravity(),
                base.stepHeight(),
                base.jumpPower(),
                base.width(),
                base.height(),
                base.canSwim(),
                base.hover(),
                base.hoverHeight(),
                wander != null ? wander : base.wander(),
                wanderRadius != null ? wanderRadius : base.wanderRadius(),
                base.wanderSpeed(),
                sitChance != null ? sitChance : base.sitChance(),
                base.curiosity(),
                base.interestRadius(),
                predictionSeconds != null ? predictionSeconds : base.predictionSeconds());
    }

    public PetRenderSettings applyTo(PetRenderSettings base) {
        if (scale == null) {
            return base;
        }
        return new PetRenderSettings(scale, base.yOffset(), base.shadowRadius(), base.glow(), base.nameplate());
    }

    public PetOverrides withAnchorRadius(Float value) {
        return new PetOverrides(value, predictionSeconds, wanderRadius, sitChance, wander, scale);
    }

    public PetOverrides withPredictionSeconds(Float value) {
        return new PetOverrides(anchorRadius, value, wanderRadius, sitChance, wander, scale);
    }

    public PetOverrides withWanderRadius(Float value) {
        return new PetOverrides(anchorRadius, predictionSeconds, value, sitChance, wander, scale);
    }

    public PetOverrides withSitChance(Float value) {
        return new PetOverrides(anchorRadius, predictionSeconds, wanderRadius, value, wander, scale);
    }

    public PetOverrides withWander(Boolean value) {
        return new PetOverrides(anchorRadius, predictionSeconds, wanderRadius, sitChance, value, scale);
    }

    public PetOverrides withScale(Float value) {
        return new PetOverrides(anchorRadius, predictionSeconds, wanderRadius, sitChance, wander, value);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        put(json, "anchor_radius", anchorRadius);
        put(json, "prediction_seconds", predictionSeconds);
        put(json, "wander_radius", wanderRadius);
        put(json, "sit_chance", sitChance);
        if (wander != null) {
            json.addProperty("wander", wander);
        }
        put(json, "scale", scale);
        return json;
    }

    public static PetOverrides fromJson(JsonObject json) {
        if (json == null) {
            return NONE;
        }
        return new PetOverrides(
                readFloat(json, "anchor_radius"),
                readFloat(json, "prediction_seconds"),
                readFloat(json, "wander_radius"),
                readFloat(json, "sit_chance"),
                readBoolean(json, "wander"),
                readFloat(json, "scale"));
    }

    private static void put(JsonObject json, String key, Float value) {
        if (value != null) {
            json.addProperty(key, value);
        }
    }

    private static Float readFloat(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : null;
    }

    private static Boolean readBoolean(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : null;
    }
}
