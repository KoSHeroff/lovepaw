package dev.lovepaw.model.anim;

import java.util.Map;

/** One named animation, keyed by bone name. Times are in seconds. */
public record Animation(String name, float length, LoopMode loop, Map<String, BoneAnimation> bones) {
    public BoneAnimation bone(String boneName) {
        return bones.getOrDefault(boneName, BoneAnimation.EMPTY);
    }
}
