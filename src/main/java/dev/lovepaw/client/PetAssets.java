package dev.lovepaw.client;

import dev.lovepaw.model.anim.Animation;
import dev.lovepaw.model.geo.baked.BakedGeoModel;

import java.util.Map;

/**
 * The loaded, draw-ready side of a pet. Kept apart from
 * {@link dev.lovepaw.pet.PetDefinition} so nothing but the client ever holds
 * baked geometry.
 */
public record PetAssets(BakedGeoModel model, Map<String, Animation> animations) {
    public boolean hasAnimation(String name) {
        return name != null && animations.containsKey(name);
    }
}
