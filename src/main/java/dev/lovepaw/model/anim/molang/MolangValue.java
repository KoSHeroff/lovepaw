package dev.lovepaw.model.anim.molang;

/** A compiled Molang expression, or a plain constant. */
@FunctionalInterface
public interface MolangValue {
    float get(MolangContext context);

    static MolangValue constant(float value) {
        return context -> value;
    }

    MolangValue ZERO = context -> 0f;
}
