package dev.lovepaw.model.geo.baked;

import java.util.List;
import java.util.Map;

/**
 * A whole model ready to draw. Bones are reachable both as a tree (for
 * rendering) and by name (for animation lookups).
 */
public record BakedGeoModel(
        String identifier,
        List<BakedBone> roots,
        Map<String, BakedBone> bonesByName,
        float textureWidth,
        float textureHeight
) {
}
