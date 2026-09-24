package dev.lovepaw.model.geo.baked;

/** Four vertices in draw order plus the face normal. */
public record BakedQuad(BakedVertex[] vertices, float normalX, float normalY, float normalZ) {
}
