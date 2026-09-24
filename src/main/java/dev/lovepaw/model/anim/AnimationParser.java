package dev.lovepaw.model.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lovepaw.model.anim.molang.MolangParser;
import dev.lovepaw.model.anim.molang.MolangValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Blockbench's animation export (.animation.json).
 *
 * <p>Keyframe values are converted here, once, into Minecraft's units and signs
 * — the same conversion {@link dev.lovepaw.model.geo.GeoBaker} applies to the
 * model, so animation and rest pose agree:
 *
 * <ul>
 *   <li>rotation: degrees to radians, X and Y negated</li>
 *   <li>position: model units to blocks, X negated</li>
 *   <li>scale: used as written</li>
 * </ul>
 *
 * <p>Times stay in seconds, which is what the format uses.
 */
public final class AnimationParser {
    private static final float PRE_POST_GAP = 0.0001f;

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final float SCALE = 1f / 16f;

    private enum Channel {
        ROTATION,
        POSITION,
        SCALE
    }

    private AnimationParser() {
    }

    public static Map<String, Animation> parse(JsonObject root) {
        Map<String, Animation> animations = new LinkedHashMap<>();
        if (!root.has("animations")) {
            return Map.of();
        }

        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("animations").entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            animations.put(entry.getKey(), parseAnimation(entry.getKey(), entry.getValue().getAsJsonObject()));
        }

        return Map.copyOf(animations);
    }

    private static Animation parseAnimation(String name, JsonObject json) {
        LoopMode loop = readLoop(json.get("loop"));

        Map<String, BoneAnimation> bones = new LinkedHashMap<>();
        float longestKeyframe = 0;

        if (json.has("bones")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("bones").entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject boneJson = entry.getValue().getAsJsonObject();
                KeyframeTrack rotation = readTrack(boneJson.get("rotation"), Channel.ROTATION);
                KeyframeTrack position = readTrack(boneJson.get("position"), Channel.POSITION);
                KeyframeTrack scale = readTrack(boneJson.get("scale"), Channel.SCALE);

                longestKeyframe = Math.max(longestKeyframe, lastTime(rotation));
                longestKeyframe = Math.max(longestKeyframe, lastTime(position));
                longestKeyframe = Math.max(longestKeyframe, lastTime(scale));

                bones.put(entry.getKey(), new BoneAnimation(rotation, position, scale));
            }
        }

        float length = json.has("animation_length")
                ? json.get("animation_length").getAsFloat()
                : longestKeyframe;
        if (length <= 0) {
            length = Math.max(longestKeyframe, 0.05f);
        }

        return new Animation(name, length, loop, Map.copyOf(bones));
    }

    private static LoopMode readLoop(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return LoopMode.ONCE;
        }
        if (element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean() ? LoopMode.LOOP : LoopMode.ONCE;
        }
        return LoopMode.fromJson(element.getAsString(), false);
    }

    private static float lastTime(KeyframeTrack track) {
        return track.isEmpty() ? 0 : track.keyframes().get(track.keyframes().size() - 1).time();
    }

    private static KeyframeTrack readTrack(JsonElement element, Channel channel) {
        if (element == null || element.isJsonNull()) {
            return KeyframeTrack.EMPTY;
        }

        List<Keyframe> keyframes = new ArrayList<>();

        if (element.isJsonObject() && !isKeyframeValue(element.getAsJsonObject())) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                float time;
                try {
                    time = Float.parseFloat(entry.getKey());
                } catch (NumberFormatException e) {
                    continue;
                }
                readTimedKeyframe(time, entry.getValue(), channel, keyframes);
            }
            keyframes.sort(Comparator.comparingDouble(Keyframe::time));
        } else {
            keyframes.add(keyframe(0, element, channel, Interpolation.LINEAR));
        }

        return new KeyframeTrack(List.copyOf(keyframes));
    }

    private static boolean isKeyframeValue(JsonObject json) {
        return json.has("pre") || json.has("post") || json.has("vector") || json.has("lerp_mode");
    }

    private static void readTimedKeyframe(float time, JsonElement element, Channel channel, List<Keyframe> out) {
        if (element.isJsonObject() && isKeyframeValue(element.getAsJsonObject())) {
            JsonObject json = element.getAsJsonObject();
            Interpolation interpolation = Interpolation.byName(
                    json.has("lerp_mode") ? json.get("lerp_mode").getAsString() : null);

            JsonElement pre = json.get("pre");
            JsonElement post = json.get("post");
            JsonElement vector = json.get("vector");

            if (pre != null && post != null) {
                out.add(keyframe(time - PRE_POST_GAP, pre, channel, interpolation));
                out.add(keyframe(time, post, channel, Interpolation.STEP));
            } else if (pre != null) {
                out.add(keyframe(time, pre, channel, interpolation));
            } else if (post != null) {
                out.add(keyframe(time, post, channel, interpolation));
            } else if (vector != null) {
                out.add(keyframe(time, vector, channel, interpolation));
            }
            return;
        }

        out.add(keyframe(time, element, channel, Interpolation.LINEAR));
    }

    private static Keyframe keyframe(float time, JsonElement element, Channel channel, Interpolation interpolation) {
        MolangValue x;
        MolangValue y;
        MolangValue z;

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            x = compile(array.size() > 0 ? array.get(0) : null);
            y = compile(array.size() > 1 ? array.get(1) : null);
            z = compile(array.size() > 2 ? array.get(2) : null);
        } else if (element.isJsonObject() && element.getAsJsonObject().has("vector")) {
            return keyframe(time, element.getAsJsonObject().get("vector"), channel, interpolation);
        } else {
            MolangValue single = compile(element);
            x = single;
            y = single;
            z = single;
        }

        return new Keyframe(time, convert(x, channel, 0), convert(y, channel, 1), convert(z, channel, 2), interpolation);
    }

    private static MolangValue compile(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return MolangValue.ZERO;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return MolangValue.constant(element.getAsFloat());
        }
        return MolangParser.compile(element.getAsString());
    }

    private static MolangValue convert(MolangValue value, Channel channel, int axis) {
        return switch (channel) {
            case ROTATION -> axis == 2
                    ? context -> value.get(context) * DEG_TO_RAD
                    : context -> -value.get(context) * DEG_TO_RAD;
            case POSITION -> axis == 0
                    ? context -> -value.get(context) * SCALE
                    : context -> value.get(context) * SCALE;
            case SCALE -> value;
        };
    }
}
