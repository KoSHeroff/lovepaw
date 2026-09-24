package dev.lovepaw.model.anim;

import dev.lovepaw.model.Vec3f;
import dev.lovepaw.model.anim.molang.MolangContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plays one animation at a time and cross-fades when it is swapped, so a pet
 * that stops walking eases into its idle instead of snapping.
 *
 * <p>One instance per live pet: it holds playback time, not shared data.
 */
public final class AnimationPlayer {
    private final Map<String, Animation> animations;

    private Animation current;
    private float time;
    private boolean finished;

    private Map<String, BonePose> transitionFrom = Map.of();
    private float transitionLength;
    private float transitionRemaining;

    private Map<String, BonePose> lastPose = Map.of();

    public AnimationPlayer(Map<String, Animation> animations) {
        this.animations = animations;
    }

    public String currentName() {
        return current == null ? null : current.name();
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * Switches to {@code name}, cross-fading over {@code transitionSeconds}.
     * Asking for the animation that is already running does nothing, so this is
     * safe to call every tick from behaviour code — including after a
     * play-once animation has ended, which otherwise restarted on every tick
     * and left the pet stuck on its first frame.
     */
    public void play(String name, float transitionSeconds) {
        if (name == null) {
            stop(transitionSeconds);
            return;
        }
        if (current != null && current.name().equals(name)) {
            return;
        }
        Animation animation = animations.get(name);
        if (animation == null) {
            return;
        }

        transitionFrom = lastPose;
        transitionLength = Math.max(0, transitionSeconds);
        transitionRemaining = transitionLength;
        current = animation;
        time = 0;
        finished = false;
    }

    public void stop(float transitionSeconds) {
        if (current == null) {
            return;
        }
        transitionFrom = lastPose;
        transitionLength = Math.max(0, transitionSeconds);
        transitionRemaining = transitionLength;
        current = null;
        time = 0;
        finished = true;
    }

    public void tick(float deltaSeconds) {
        if (transitionRemaining > 0) {
            transitionRemaining = Math.max(0, transitionRemaining - deltaSeconds);
        }
        if (current == null || finished) {
            return;
        }

        time += deltaSeconds;
        if (time >= current.length()) {
            switch (current.loop()) {
                case LOOP -> time %= current.length();
                case HOLD_ON_LAST_FRAME -> {
                    time = current.length();
                    finished = true;
                }
                case ONCE -> {
                    time = current.length();
                    finished = true;
                }
            }
        }
    }

    /** Seconds elapsed in the running animation, for {@code query.anim_time}. */
    public float animationTime() {
        return time;
    }

    /**
     * The pose of every animated bone at the current instant, blended with
     * whatever was playing before if a cross-fade is still running.
     */
    public Map<String, BonePose> pose(MolangContext context) {
        context.setQuery("anim_time", time);

        Map<String, BonePose> pose = new HashMap<>();

        boolean holding = current != null && finished && current.loop() == LoopMode.HOLD_ON_LAST_FRAME;
        if (current != null && (!finished || holding)) {
            for (Map.Entry<String, BoneAnimation> entry : current.bones().entrySet()) {
                pose.put(entry.getKey(), sample(entry.getValue(), context));
            }
        }

        float blend = transitionLength <= 0 ? 1f : 1f - (transitionRemaining / transitionLength);
        if (blend < 1f && !transitionFrom.isEmpty()) {
            Set<String> bones = new HashSet<>(pose.keySet());
            bones.addAll(transitionFrom.keySet());

            Map<String, BonePose> blended = new HashMap<>(bones.size());
            for (String bone : bones) {
                BonePose from = transitionFrom.getOrDefault(bone, BonePose.REST);
                BonePose to = pose.getOrDefault(bone, BonePose.REST);
                blended.put(bone, BonePose.lerp(from, to, blend));
            }
            pose = blended;
        }

        lastPose = pose;
        return pose;
    }

    private BonePose sample(BoneAnimation bone, MolangContext context) {
        return new BonePose(
                bone.rotation().sample(time, context, Vec3f.ZERO),
                bone.position().sample(time, context, Vec3f.ZERO),
                bone.scale().sample(time, context, Vec3f.ONE));
    }
}
