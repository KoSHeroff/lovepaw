package dev.lovepaw.model.anim;

import dev.lovepaw.model.Vec3f;

/**
 * What an animation says about one bone at one instant: rotation in radians
 * added to the bone's rest rotation, position in blocks, and a scale factor.
 */
public record BonePose(Vec3f rotation, Vec3f position, Vec3f scale) {
    public static final BonePose REST = new BonePose(Vec3f.ZERO, Vec3f.ZERO, Vec3f.ONE);

    public boolean isRest() {
        return rotation.isZero() && position.isZero() && scale.isOne();
    }

    public static BonePose lerp(BonePose from, BonePose to, float progress) {
        if (progress <= 0) {
            return from;
        }
        if (progress >= 1) {
            return to;
        }
        return new BonePose(
                lerp(from.rotation(), to.rotation(), progress),
                lerp(from.position(), to.position(), progress),
                lerp(from.scale(), to.scale(), progress));
    }

    private static Vec3f lerp(Vec3f from, Vec3f to, float progress) {
        return new Vec3f(
                from.x() + (to.x() - from.x()) * progress,
                from.y() + (to.y() - from.y()) * progress,
                from.z() + (to.z() - from.z()) * progress);
    }
}
