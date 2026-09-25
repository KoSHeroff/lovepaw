package dev.lovepaw.behaviour;

import dev.lovepaw.pet.PetKind;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Maps a kind of pet to the code that moves it.
 *
 * <p>Both kinds are moved by the same behaviour today: what a cat and an allay
 * do differently is held in {@link PetKind} as numbers, not in two copies of
 * the same state machine. The lookup is here anyway because this is where an
 * add-on would put its own.
 */
public final class PetBehaviours {
    private static final Map<PetKind, Supplier<PetBehaviour>> TYPES = new EnumMap<>(PetKind.class);

    static {
        for (PetKind kind : PetKind.values()) {
            TYPES.put(kind, FollowBehaviour::new);
        }
    }

    private PetBehaviours() {
    }

    public static void register(PetKind kind, Supplier<PetBehaviour> factory) {
        TYPES.put(kind, factory);
    }

    public static PetBehaviour create(PetKind kind) {
        return TYPES.get(kind).get();
    }
}
