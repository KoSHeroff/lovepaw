package dev.lovepaw.client;

import dev.lovepaw.pet.PetAnimationState;
import dev.lovepaw.pet.PetKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetStateTest {
    private static final PetKind WALKING = PetKind.GROUND;
    private static final PetKind FLYING = PetKind.FLYING;

    @Test
    void risingOffTheGroundIsAJumpAndDroppingIsAFall() {
        assertEquals(PetAnimationState.JUMP,
                PetInstance.stateFor(WALKING, false, false, 0.42, 0.1, false));
        assertEquals(PetAnimationState.FALL,
                PetInstance.stateFor(WALKING, false, false, -0.4, 0.1, false));
    }

    @Test
    void whatItIsDoingOnTheGroundStillShowsThrough() {
        assertEquals(PetAnimationState.RUN,
                PetInstance.stateFor(WALKING, false, true, 0, 0.3, false));
        assertEquals(PetAnimationState.WALK,
                PetInstance.stateFor(WALKING, false, true, 0, 0.05, false));
        assertEquals(PetAnimationState.SIT,
                PetInstance.stateFor(WALKING, false, true, 0, 0, true));
        assertEquals(PetAnimationState.IDLE,
                PetInstance.stateFor(WALKING, false, true, 0, 0, false));
    }

    @Test
    void aSittingPetThatIsPushedIntoTheAirStopsLookingSeated() {
        assertEquals(PetAnimationState.FALL,
                PetInstance.stateFor(WALKING, false, false, -0.5, 0, true),
                "the sit pose has to give way, or a teleported pet keeps it in mid-air");
    }

    @Test
    void swimmingBeatsEverything() {
        assertEquals(PetAnimationState.SWIM,
                PetInstance.stateFor(WALKING, true, false, -0.4, 0.3, true));
    }

    @Test
    void hoveringPetsNeverJumpOrFall() {
        assertEquals(PetAnimationState.IDLE,
                PetInstance.stateFor(FLYING, false, false, -0.9, 0, false));
        assertFalse(PetInstance.wantsJump(FLYING, true, false, 10));
    }

    @Test
    void hopsOnlyAfterWalkingIntoSomethingForAMoment() {
        assertFalse(PetInstance.wantsJump(WALKING, true, false, 0), "not at the first brush");
        assertTrue(PetInstance.wantsJump(WALKING, true, false, 3), "but once it is clearly blocked");
        assertFalse(PetInstance.wantsJump(WALKING, false, false, 3), "never in mid-air");
        assertFalse(PetInstance.wantsJump(WALKING, true, true, 3), "and it paddles rather than hops");
        assertFalse(PetInstance.wantsJump(FLYING, true, false, 3),
                "a pet that flies has no reason to hop at all");
    }
}
