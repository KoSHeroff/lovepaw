package dev.lovepaw.model.anim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lovepaw.model.anim.molang.MolangContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationPlayerTest {
    private static final String FILE = """
            {
              "animations": {
                "animation.test.hop": {
                  "loop": "hold_on_last_frame",
                  "animation_length": 0.5,
                  "bones": {"body": {"position": {"0.0": [0, 0, 0], "0.5": [0, 8, 0]}}}
                },
                "animation.test.idle": {
                  "loop": true,
                  "animation_length": 1.0,
                  "bones": {"body": {"position": {"0.0": [0, 0, 0], "1.0": [0, 0, 0]}}}
                }
              }
            }
            """;

    private static AnimationPlayer player() {
        JsonObject root = JsonParser.parseString(FILE).getAsJsonObject();
        Map<String, Animation> animations = AnimationParser.parse(root);
        return new AnimationPlayer(animations);
    }

    @Test
    void aPlayOnceAnimationHoldsItsLastFrameWhileTheStateLasts() {
        AnimationPlayer player = player();
        player.play("animation.test.hop", 0);

        for (int tick = 0; tick < 20; tick++) {
            player.tick(0.05f);
            player.play("animation.test.hop", 0.2f);
        }

        assertTrue(player.isFinished(), "half a second in, the hop is over");
        assertEquals(0.5f, player.animationTime(), 1.0E-4, "and it is holding the last frame");
        assertEquals(0.5f, player.pose(new MolangContext()).get("body").position().y(), 1.0E-4);
    }

    @Test
    void switchingAwayAndBackStartsItAgain() {
        AnimationPlayer player = player();
        player.play("animation.test.hop", 0);
        for (int tick = 0; tick < 20; tick++) {
            player.tick(0.05f);
        }

        player.play("animation.test.idle", 0);
        player.tick(0.05f);
        player.play("animation.test.hop", 0);

        assertEquals("animation.test.hop", player.currentName());
        assertNotEquals(0.5f, player.animationTime(), "a fresh hop starts from the top");
        assertEquals(0f, player.animationTime(), 1.0E-4);
    }
}
