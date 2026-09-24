package dev.lovepaw.model.geo.baked;

/** A single corner of a quad: position in blocks, UV already normalised to 0..1. */
public record BakedVertex(float x, float y, float z, float u, float v) {
    public BakedVertex withUv(float newU, float newV) {
        return new BakedVertex(x, y, z, newU, newV);
    }
}
