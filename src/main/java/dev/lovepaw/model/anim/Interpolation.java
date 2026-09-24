package dev.lovepaw.model.anim;

/** How a keyframe blends into the one before it. */
public enum Interpolation {
    /** Straight line between the two keyframes; Blockbench's default. */
    LINEAR,
    /** Smoothed through the neighbouring keyframes, Blockbench's "catmullrom". */
    CATMULLROM,
    /** Holds the previous value until the keyframe is reached. */
    STEP;

    public static Interpolation byName(String name) {
        if (name == null) {
            return LINEAR;
        }
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "catmullrom", "smooth" -> CATMULLROM;
            case "step" -> STEP;
            default -> LINEAR;
        };
    }
}
