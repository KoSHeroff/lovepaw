package dev.lovepaw.model.geo;

import dev.lovepaw.model.Vec3f;

import java.util.List;

public record GeoBone(
        String name,
        String parent,
        Vec3f pivot,
        Vec3f rotation,
        boolean mirror,
        float inflate,
        List<GeoCube> cubes
) {
}
