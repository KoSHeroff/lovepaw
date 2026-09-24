package dev.lovepaw.client;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a flying pet picks its height, which is its own business and not its owner's. */
class PetHoverTest {
    private static final float WIDTH = 0.5f;
    private static final float HEIGHT = 0.5f;
    private static final double LIKES = 2.0;
    private static final double DRIFT = 1.0;

    private static RandomSource random() {
        return RandomSource.create(99L);
    }

    /** Flies the pet on the spot and returns where it settles. */
    private static double settle(PetHover hover, TestBlocks world, double startY, boolean blocked) {
        return settle(hover, world, startY, blocked, 0);
    }

    private static double settle(PetHover hover, TestBlocks world, double startY, boolean blocked, double ownerY) {
        RandomSource random = random();
        double y = startY;
        for (int tick = 0; tick < 200; tick++) {
            y += hover.climb(world, new Vec3(0.5, y, 0.5), WIDTH, HEIGHT, LIKES, 0, ownerY, blocked, random);
        }
        return y;
    }

    @Test
    void holdsItsHeightOverWhateverIsUnderIt() {
        TestBlocks world = new TestBlocks().floor();

        double fromAbove = settle(new PetHover(), world, 9, false);
        double fromBelow = settle(new PetHover(), world, 0.2, false);

        assertTrue(Math.abs(fromAbove - LIKES) < 0.3, "should come down to its height; was " + fromAbove);
        assertTrue(Math.abs(fromBelow - LIKES) < 0.3, "and climb up to it; was " + fromBelow);
    }

    @Test
    void risesWhenTheGroundDoes() {
        TestBlocks world = new TestBlocks().floor();
        for (int y = 0; y < 4; y++) {                 // a pillar right under the pet
            world.solid(0, y, 0);
        }

        double settled = settle(new PetHover(), world, 9, false);

        assertTrue(Math.abs(settled - 6) < 0.3,
                "four blocks of pillar should lift it with them; was " + settled);
    }

    @Test
    void climbsWhenSomethingIsInItsWay() {
        TestBlocks world = new TestBlocks().floor();
        PetHover hover = new PetHover();
        RandomSource random = random();

        double atRest = hover.climb(world, new Vec3(0.5, LIKES, 0.5), WIDTH, HEIGHT, LIKES, 0, 0, false, random);
        double whenBlocked = hover.climb(world, new Vec3(0.5, LIKES, 0.5), WIDTH, HEIGHT, LIKES, 0, 0, true, random);

        assertTrue(Math.abs(atRest) < 0.02, "at its favourite height it should just hold station");
        assertTrue(whenBlocked > 0.05, "walled in, up is the way out; was " + whenBlocked);
    }

    @Test
    void doesNotPushItsHeadIntoTheCeiling() {
        TestBlocks world = new TestBlocks().floor();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.solid(x, 1, z);                 // a low roof, under its preferred height
            }
        }

        double settled = settle(new PetHover(), world, 0.2, false);

        assertTrue(settled < 1.0, "it has to stay under the roof; was " + settled);
    }

    @Test
    void comesUpWhenItsOwnerClimbs() {
        TestBlocks world = new TestBlocks().floor();

        double settled = settle(new PetHover(), world, LIKES, false, 20);

        assertTrue(settled > 15, "an owner on a tower should not be left behind; was " + settled);
    }

    @Test
    void wandersUpAndDownOnItsOwn() {
        TestBlocks world = new TestBlocks().floor();
        PetHover hover = new PetHover();
        RandomSource random = random();

        double y = LIKES;
        double lowest = y;
        double highest = y;
        for (int tick = 0; tick < 600; tick++) {
            y += hover.climb(world, new Vec3(0.5, y, 0.5), WIDTH, HEIGHT, LIKES, DRIFT, 0, false, random);
            lowest = Math.min(lowest, y);
            highest = Math.max(highest, y);
        }

        assertTrue(highest - lowest > 0.6,
                "a bee does not hold one height for ever; it moved " + (highest - lowest));
        assertTrue(highest - lowest < 2 * DRIFT + 0.6, "but it should stay in its band");
    }
}
