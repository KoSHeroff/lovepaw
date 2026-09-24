package dev.lovepaw.client;

import dev.lovepaw.pet.PetAnimationState;
import dev.lovepaw.pet.PetBehaviourSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetStateTest {
    private static final PetBehaviourSettings SETTINGS = PetBehaviourSettings.DEFAULT;

    @Test
    void risingOffTheGroundIsAJumpAndDroppingIsAFall() {
        assertEquals(PetAnimationState.JUMP,
                PetInstance.stateFor(SETTINGS, false, false, 0.42, 0.1, false));
        assertEquals(PetAnimationState.FALL,
                PetInstance.stateFor(SETTINGS, false, false, -0.4, 0.1, false));
    }

    @Test
    void whatItIsDoingOnTheGroundStillShowsThrough() {
        assertEquals(PetAnimationState.RUN,
                PetInstance.stateFor(SETTINGS, false, true, 0, 0.3, false));
        assertEquals(PetAnimationState.WALK,
                PetInstance.stateFor(SETTINGS, false, true, 0, 0.05, false));
        assertEquals(PetAnimationState.SIT,
                PetInstance.stateFor(SETTINGS, false, true, 0, 0, true));
        assertEquals(PetAnimationState.IDLE,
                PetInstance.stateFor(SETTINGS, false, true, 0, 0, false));
    }

    @Test
    void aSittingPetThatIsPushedIntoTheAirStopsLookingSeated() {
        assertEquals(PetAnimationState.FALL,
                PetInstance.stateFor(SETTINGS, false, false, -0.5, 0, true),
                "the sit pose has to give way, or a teleported pet keeps it in mid-air");
    }

    @Test
    void swimmingBeatsEverything() {
        assertEquals(PetAnimationState.SWIM,
                PetInstance.stateFor(SETTINGS, true, false, -0.4, 0.3, true));
    }

    @Test
    void hoveringPetsNeverJumpOrFall() {
        PetBehaviourSettings hovering = with(SETTINGS.jumpPower(), true);

        assertEquals(PetAnimationState.IDLE,
                PetInstance.stateFor(hovering, false, false, -0.9, 0, false));
        assertFalse(PetInstance.wantsJump(hovering, true, false, 10));
    }

    @Test
    void hopsOnlyAfterWalkingIntoSomethingForAMoment() {
        assertFalse(PetInstance.wantsJump(SETTINGS, true, false, 0), "not at the first brush");
        assertTrue(PetInstance.wantsJump(SETTINGS, true, false, 3), "but once it is clearly blocked");
        assertFalse(PetInstance.wantsJump(SETTINGS, false, false, 3), "never in mid-air");
        assertFalse(PetInstance.wantsJump(SETTINGS, true, true, 3), "and it paddles rather than hops");
        assertFalse(PetInstance.wantsJump(with(0, false), true, false, 3),
                "a pet whose pack sets jump_power to 0 stays on the floor");
    }

    private static PetBehaviourSettings with(float jumpPower, boolean hover) {
        PetBehaviourSettings b = PetBehaviourSettings.DEFAULT;
        return new PetBehaviourSettings(
                b.type(), b.anchorRadius(), b.stopDistance(), b.teleportDistance(),
                b.walkSpeed(), b.runSpeed(), b.runDistance(), b.gravity(), b.stepHeight(),
                jumpPower, b.width(), b.height(), b.canSwim(), hover, b.hoverHeight(),
                b.wander(), b.wanderRadius(), b.wanderSpeed(), b.sitChance(), b.predictionSeconds());
    }
}
