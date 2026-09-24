package dev.lovepaw.model.anim;

/** The three animated channels of a single bone. */
public record BoneAnimation(KeyframeTrack rotation, KeyframeTrack position, KeyframeTrack scale) {
    public static final BoneAnimation EMPTY =
            new BoneAnimation(KeyframeTrack.EMPTY, KeyframeTrack.EMPTY, KeyframeTrack.EMPTY);
}
