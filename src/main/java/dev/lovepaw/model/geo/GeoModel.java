package dev.lovepaw.model.geo;

import java.util.List;

/** Parsed, not yet baked, Bedrock geometry. */
public record GeoModel(
        String identifier,
        float textureWidth,
        float textureHeight,
        List<GeoBone> bones
) {
}
