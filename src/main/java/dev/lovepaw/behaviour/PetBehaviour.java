package dev.lovepaw.behaviour;

/**
 * Decides what a pet does. One instance per live pet, so a behaviour may keep
 * state such as a wander target or a timer.
 */
public interface PetBehaviour {
    void tick(PetActor actor);
}
