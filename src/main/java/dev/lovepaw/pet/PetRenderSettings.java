package dev.lovepaw.pet;

/**
 * How a pet is drawn.
 *
 * @param scale        multiplier applied to the whole model
 * @param yOffset      vertical nudge in blocks, for models not sitting on zero
 * @param shadowRadius radius of the drop shadow in blocks, 0 to skip it
 * @param glow         draw at full brightness, ignoring world light
 * @param nameplate    show the pet's name above it
 */
public record PetRenderSettings(float scale, float yOffset, float shadowRadius, boolean glow, boolean nameplate) {
    public static final PetRenderSettings DEFAULT = new PetRenderSettings(1f, 0f, 0.3f, false, false);
}
