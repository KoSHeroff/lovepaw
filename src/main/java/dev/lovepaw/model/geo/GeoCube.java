package dev.lovepaw.model.geo;

import dev.lovepaw.model.Vec3f;

import java.util.Map;

/**
 * A single box of a Bedrock geometry, still in Blockbench coordinates
 * (16 units per block, X pointing the opposite way from Java). The conversion
 * happens once in {@link GeoBaker}.
 */
public record GeoCube(
        Vec3f origin,
        Vec3f size,
        Vec3f pivot,
        Vec3f rotation,
        float inflate,
        boolean mirror,
        Vec3f boxUv,
        Map<GeoFace, GeoUv> perFaceUv
) {
    public boolean hasPerFaceUv() {
        return perFaceUv != null && !perFaceUv.isEmpty();
    }
}
