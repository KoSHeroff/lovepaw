package dev.lovepaw.model.anim;

import dev.lovepaw.model.Vec3f;
import dev.lovepaw.model.anim.molang.MolangContext;
import dev.lovepaw.model.anim.molang.MolangValue;

/**
 * One keyframe of one bone channel. Values are already in Minecraft's units and
 * sign convention (see {@link AnimationParser}), and {@code interpolation}
 * describes the segment that ends at this keyframe.
 */
public record Keyframe(float time, MolangValue x, MolangValue y, MolangValue z, Interpolation interpolation) {
    public Vec3f evaluate(MolangContext context) {
        return new Vec3f(x.get(context), y.get(context), z.get(context));
    }
}
