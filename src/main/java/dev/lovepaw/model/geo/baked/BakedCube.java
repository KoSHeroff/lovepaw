package dev.lovepaw.model.geo.baked;

import dev.lovepaw.model.Vec3f;

import java.util.List;

/**
 * A cube ready to draw. Pivot is in blocks, rotation in radians, and both are
 * already converted out of Blockbench's coordinate space by
 * {@link dev.lovepaw.model.geo.GeoBaker}.
 */
public record BakedCube(List<BakedQuad> quads, Vec3f pivot, Vec3f rotation) {
    public boolean hasRotation() {
        return !rotation.isZero();
    }
}
