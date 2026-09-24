package dev.lovepaw.model.geo.baked;

import dev.lovepaw.model.Vec3f;

import java.util.List;

/**
 * A bone ready to draw. {@code pivot} is absolute in model space (blocks) and
 * {@code rotation} is the bone's rest rotation in radians, applied Z, then Y,
 * then X around the pivot. Animation adds to this rotation rather than
 * replacing it.
 */
public record BakedBone(
        String name,
        Vec3f pivot,
        Vec3f rotation,
        List<BakedCube> cubes,
        List<BakedBone> children
) {
}
