package dev.lovepaw.model.anim;

import dev.lovepaw.model.Vec3f;
import dev.lovepaw.model.anim.molang.MolangContext;

import java.util.List;

/** The keyframes of one channel (rotation, position or scale) of one bone. */
public record KeyframeTrack(List<Keyframe> keyframes) {
    public static final KeyframeTrack EMPTY = new KeyframeTrack(List.of());

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    /**
     * Value of this channel at {@code time} seconds, or {@code fallback} when
     * the channel holds no keyframes at all.
     */
    public Vec3f sample(float time, MolangContext context, Vec3f fallback) {
        if (keyframes.isEmpty()) {
            return fallback;
        }
        if (keyframes.size() == 1) {
            return keyframes.get(0).evaluate(context);
        }

        Keyframe first = keyframes.get(0);
        if (time <= first.time()) {
            return first.evaluate(context);
        }
        Keyframe last = keyframes.get(keyframes.size() - 1);
        if (time >= last.time()) {
            return last.evaluate(context);
        }

        int index = 0;
        for (int i = 0; i < keyframes.size() - 1; i++) {
            if (time >= keyframes.get(i).time() && time < keyframes.get(i + 1).time()) {
                index = i;
                break;
            }
        }

        Keyframe from = keyframes.get(index);
        Keyframe to = keyframes.get(index + 1);
        float span = to.time() - from.time();
        float progress = span <= 0 ? 0f : (time - from.time()) / span;

        Vec3f start = from.evaluate(context);
        Vec3f end = to.evaluate(context);

        return switch (to.interpolation()) {
            case STEP -> start;
            case CATMULLROM -> {
                Vec3f before = keyframes.get(Math.max(0, index - 1)).evaluate(context);
                Vec3f after = keyframes.get(Math.min(keyframes.size() - 1, index + 2)).evaluate(context);
                yield catmullRom(before, start, end, after, progress);
            }
            case LINEAR -> lerp(start, end, progress);
        };
    }

    private static Vec3f lerp(Vec3f start, Vec3f end, float progress) {
        return new Vec3f(
                start.x() + (end.x() - start.x()) * progress,
                start.y() + (end.y() - start.y()) * progress,
                start.z() + (end.z() - start.z()) * progress);
    }

    private static Vec3f catmullRom(Vec3f before, Vec3f start, Vec3f end, Vec3f after, float progress) {
        return new Vec3f(
                catmullRom(before.x(), start.x(), end.x(), after.x(), progress),
                catmullRom(before.y(), start.y(), end.y(), after.y(), progress),
                catmullRom(before.z(), start.z(), end.z(), after.z(), progress));
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * ((2 * p1)
                + (-p0 + p2) * t
                + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
