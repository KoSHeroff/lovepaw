package dev.lovepaw.pet;

/**
 * Where a pet came from. The registry keeps this so a later source — a pack
 * downloaded from a catalogue — can be added, listed and removed without
 * touching anything that already works.
 */
public enum PetSourceKind {
    /** Shipped inside the mod jar. */
    BUILTIN,
    /** Provided by a resource pack or another mod's assets. */
    RESOURCE_PACK,
    /** Loaded from the player's own lovepaw/pets folder. */
    LOCAL,
    /** Downloaded from a pet catalogue. */
    REMOTE
}
