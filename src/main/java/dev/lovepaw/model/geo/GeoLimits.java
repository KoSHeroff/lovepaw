package dev.lovepaw.model.geo;

/**
 * Ceilings applied while parsing. Pet content comes from resource packs today
 * and from downloaded packs later, so a malformed or hostile model has to fail
 * loudly instead of allocating until the client dies.
 */
public record GeoLimits(int maxBones, int maxCubesPerBone, int maxTotalCubes) {
    public static final GeoLimits DEFAULT = new GeoLimits(256, 512, 2048);
}
