package dev.lovepaw.model.anim.molang;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The values a Molang expression can read while a pet is animating.
 *
 * <p>Only the queries that mean something for a cosmetic pet are populated;
 * anything a pack asks for that we do not know evaluates to 0 rather than
 * failing, because a missing query must never break a whole animation.
 */
public final class MolangContext {
    private final Map<String, Float> queries = new HashMap<>();
    private final Map<String, Float> variables = new HashMap<>();
    private final Random random = new Random();

    public void setQuery(String name, float value) {
        queries.put(name, value);
    }

    public float query(String name) {
        Float value = queries.get(name);
        return value != null ? value : 0f;
    }

    public void setVariable(String name, float value) {
        variables.put(name, value);
    }

    public float variable(String name) {
        Float value = variables.get(name);
        return value != null ? value : 0f;
    }

    public Random random() {
        return random;
    }

    /** Wipes per-frame state; variables survive because Molang scripts rely on that. */
    public void clearQueries() {
        queries.clear();
    }
}
