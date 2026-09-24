package dev.lovepaw.model.anim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lovepaw.model.Vec3f;
import dev.lovepaw.model.anim.molang.MolangContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationParserTest {
    private static final float EPSILON = 1.0E-4f;

    private static Map<String, Animation> parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return AnimationParser.parse(root);
    }

    @Test
    void rotationKeyframesBecomeRadiansWithXAndYNegated() {
        Animation animation = parse("""
                {
                  "animations": {
                    "animation.test.wave": {
                      "loop": true,
                      "animation_length": 1.0,
                      "bones": {"arm": {"rotation": {"0.0": [90, 45, 30]}}}
                    }
                  }
                }
                """).get("animation.test.wave");

        Vec3f rotation = animation.bone("arm").rotation().sample(0, new MolangContext(), Vec3f.ZERO);

        assertEquals((float) Math.toRadians(-90), rotation.x(), EPSILON);
        assertEquals((float) Math.toRadians(-45), rotation.y(), EPSILON);
        assertEquals((float) Math.toRadians(30), rotation.z(), EPSILON);
    }

    @Test
    void positionKeyframesBecomeBlocksWithXNegated() {
        Animation animation = parse("""
                {
                  "animations": {
                    "animation.test.step": {
                      "animation_length": 1.0,
                      "bones": {"leg": {"position": {"0.0": [16, 8, -16]}}}
                    }
                  }
                }
                """).get("animation.test.step");

        Vec3f position = animation.bone("leg").position().sample(0, new MolangContext(), Vec3f.ZERO);

        assertEquals(-1f, position.x(), EPSILON);
        assertEquals(0.5f, position.y(), EPSILON);
        assertEquals(-1f, position.z(), EPSILON);
    }

    @Test
    void scaleKeyframesAreLeftAlone() {
        Animation animation = parse("""
                {
                  "animations": {
                    "animation.test.grow": {
                      "animation_length": 1.0,
                      "bones": {"body": {"scale": {"0.0": [2, 2, 2]}}}
                    }
                  }
                }
                """).get("animation.test.grow");

        Vec3f scale = animation.bone("body").scale().sample(0, new MolangContext(), Vec3f.ONE);
        assertEquals(2f, scale.x(), EPSILON);
    }

    @Test
    void valuesInterpolateBetweenKeyframes() {
        Animation animation = parse("""
                {
                  "animations": {
                    "animation.test.swing": {
                      "animation_length": 1.0,
                      "bones": {"leg": {"rotation": {"0.0": [0, 0, 0], "1.0": [90, 0, 0]}}}
                    }
                  }
                }
                """).get("animation.test.swing");

        KeyframeTrack track = animation.bone("leg").rotation();
        MolangContext context = new MolangContext();

        assertEquals(0f, track.sample(0f, context, Vec3f.ZERO).x(), EPSILON);
        assertEquals((float) Math.toRadians(-45), track.sample(0.5f, context, Vec3f.ZERO).x(), EPSILON);
        assertEquals((float) Math.toRadians(-90), track.sample(1f, context, Vec3f.ZERO).x(), EPSILON);
        assertEquals((float) Math.toRadians(-90), track.sample(5f, context, Vec3f.ZERO).x(), EPSILON);
    }

    @Test
    void molangKeyframesAreEvaluatedEveryTime() {
        Animation animation = parse("""
                {
                  "animations": {
                    "animation.test.tail": {
                      "animation_length": 2.0,
                      "bones": {"tail": {"rotation": ["0", "math.sin(query.anim_time * 180) * 10", "0"]}}
                    }
                  }
                }
                """).get("animation.test.tail");

        KeyframeTrack track = animation.bone("tail").rotation();
        MolangContext context = new MolangContext();

        context.setQuery("anim_time", 0f);
        assertEquals(0f, track.sample(0f, context, Vec3f.ZERO).y(), EPSILON);

        context.setQuery("anim_time", 0.5f);
        assertEquals((float) Math.toRadians(-10), track.sample(0.5f, context, Vec3f.ZERO).y(), EPSILON);
    }

    @Test
    void loopModeAndLengthAreRead() {
        Map<String, Animation> animations = parse("""
                {
                  "animations": {
                    "animation.test.loop": {"loop": true, "animation_length": 1.5, "bones": {}},
                    "animation.test.once": {"animation_length": 0.5, "bones": {}},
                    "animation.test.hold": {"loop": "hold_on_last_frame", "animation_length": 2.0, "bones": {}}
                  }
                }
                """);

        assertEquals(LoopMode.LOOP, animations.get("animation.test.loop").loop());
        assertEquals(1.5f, animations.get("animation.test.loop").length(), EPSILON);
        assertEquals(LoopMode.ONCE, animations.get("animation.test.once").loop());
        assertEquals(LoopMode.HOLD_ON_LAST_FRAME, animations.get("animation.test.hold").loop());
    }

    @Test
    void steppedKeyframesHoldUntilTheyFlip() {
        Animation animation = parse("""
                {
                  "animations": {
                    "animation.test.blink": {
                      "animation_length": 2.0,
                      "bones": {
                        "eye": {
                          "rotation": {
                            "0.0": [0, 0, 0],
                            "1.0": {"pre": [0, 0, 0], "post": [90, 0, 0]}
                          }
                        }
                      }
                    }
                  }
                }
                """).get("animation.test.blink");

        KeyframeTrack track = animation.bone("eye").rotation();
        MolangContext context = new MolangContext();

        assertEquals(0f, track.sample(0.9f, context, Vec3f.ZERO).x(), EPSILON);
        assertEquals((float) Math.toRadians(-90), track.sample(1.5f, context, Vec3f.ZERO).x(), EPSILON);
    }

    @Test
    void lengthFallsBackToTheLastKeyframe() {
        Animation animation = parse("""
                {
                  "animations": {
                    "animation.test.nolength": {
                      "bones": {"arm": {"rotation": {"0.0": [0, 0, 0], "2.5": [10, 0, 0]}}}
                    }
                  }
                }
                """).get("animation.test.nolength");

        assertTrue(animation.length() >= 2.5f);
    }
}
