package dev.lovepaw.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lovepaw.pet.PetRenderSettings;

/**
 * The one thing about their own pet a player gets to change: how big it is.
 *
 * <p>There were once sliders for how far it wandered, how far ahead of you it
 * aimed and how often it sat down. They were a way of asking the player to
 * invent an animal, and every answer but one made the pet read as broken
 * rather than different. How a pet moves is the game's business now; how big
 * yours is is yours.
 *
 * <p>Null means "whatever the pet asks for", so clearing the tweak gives the
 * pet its own size back rather than some global default. It applies to this
 * player's own pet only — everybody else's pets stay as their authors drew them.
 */
public record PetOverrides(Float scale) {
    public static final PetOverrides NONE = new PetOverrides(null);

    public boolean isEmpty() {
        return scale == null;
    }

    public PetRenderSettings applyTo(PetRenderSettings base) {
        if (scale == null) {
            return base;
        }
        return new PetRenderSettings(scale, base.yOffset(), base.shadowRadius(), base.glow(), base.nameplate());
    }

    public PetOverrides withScale(Float value) {
        return new PetOverrides(value);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (scale != null) {
            json.addProperty("scale", scale);
        }
        return json;
    }

    public static PetOverrides fromJson(JsonObject json) {
        if (json == null) {
            return NONE;
        }
        return new PetOverrides(readFloat(json, "scale"));
    }

    private static Float readFloat(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : null;
    }
}
