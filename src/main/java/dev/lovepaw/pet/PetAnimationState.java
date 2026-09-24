package dev.lovepaw.pet;

import java.util.Locale;

/**
 * The states the built-in behaviour can put a pet in. A pack maps each one to
 * an animation name in its {@code animations} block; anything it leaves out
 * simply does not animate.
 */
public enum PetAnimationState {
    IDLE,
    WALK,
    RUN,
    JUMP,
    FALL,
    SWIM,
    SIT;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
