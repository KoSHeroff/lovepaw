package dev.lovepaw.behaviour;

import dev.lovepaw.LovePaw;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Maps the {@code behaviour.type} in a pet.json to the code that runs it.
 *
 * <p>One behaviour exists today. The lookup is here anyway because this is
 * where an add-on mod will register its own, and because a pet asking for an
 * unknown behaviour should fall back and keep working rather than vanish.
 */
public final class PetBehaviours {
    public static final String FOLLOW = "follow";

    private static final Map<String, Supplier<PetBehaviour>> TYPES = new ConcurrentHashMap<>();

    static {
        TYPES.put(FOLLOW, FollowBehaviour::new);
    }

    private PetBehaviours() {
    }

    public static void register(String type, Supplier<PetBehaviour> factory) {
        TYPES.put(type, factory);
    }

    public static PetBehaviour create(String type) {
        Supplier<PetBehaviour> factory = TYPES.get(type);
        if (factory == null) {
            LovePaw.LOGGER.warn("Unknown pet behaviour '{}', falling back to '{}'", type, FOLLOW);
            factory = TYPES.get(FOLLOW);
        }
        return factory.get();
    }
}
