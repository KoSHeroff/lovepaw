package dev.lovepaw.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetPhysicsTest {
    private static final float WIDTH = 0.6f;
    private static final float HEIGHT = 0.6f;
    private static final double EPSILON = 1.0E-6;

    @Test
    void walksUpAStepWithinItsStepHeight() {
        TestBlocks world = new TestBlocks().floor().solid(1, 0, 0);
        AABB box = PetPhysics.boxAt(new Vec3(0.6, 0, 0.5), WIDTH, HEIGHT);

        Vec3 moved = PetPhysics.move(world, box, new Vec3(0.3, 0, 0), 1.0);

        assertTrue(moved.x > 0.25, "should have got onto the block; moved " + moved.x);
        assertTrue(moved.y > 0.9, "and stepped up onto its top face; rose " + moved.y);
    }

    @Test
    void stopsAtAStepTooTallToWalkUp() {
        TestBlocks world = new TestBlocks().floor().solid(1, 0, 0);
        AABB box = PetPhysics.boxAt(new Vec3(0.6, 0, 0.5), WIDTH, HEIGHT);

        Vec3 moved = PetPhysics.move(world, box, new Vec3(0.3, 0, 0), 0.6);

        assertTrue(moved.x < 0.2, "should be stopped by the block; moved " + moved.x);
    }

    @Test
    void landsFlushOnTheGroundRatherThanAHairAboveIt() {
        TestBlocks world = new TestBlocks().floor();
        AABB box = PetPhysics.boxAt(new Vec3(0.5, 0.3, 0.5), WIDTH, HEIGHT);

        Vec3 moved = PetPhysics.move(world, box, new Vec3(0, -0.5, 0), 1.0);

        double gap = 0.3 + moved.y;
        assertTrue(gap < 0.01, "should come to rest on the floor; stopped " + gap + " above it");
        assertTrue(PetPhysics.onGround(world, box.move(0, moved.y, 0)),
                "and count as standing on it");
    }

    @Test
    void stopsFlushAgainstAWallToo() {
        TestBlocks world = new TestBlocks().floor().solid(1, 0, 0);
        AABB box = PetPhysics.boxAt(new Vec3(0.6, 0, 0.5), WIDTH, HEIGHT);

        Vec3 moved = PetPhysics.move(world, box, new Vec3(0.4, 0, 0), 0);

        double gap = 1 - (0.6 + WIDTH / 2 + moved.x);
        assertTrue(Math.abs(gap) < 0.01, "should end up against the wall; gap was " + gap);
    }

    @Test
    void knowsWhenItIsInsideSomethingRatherThanTouchingIt() {
        TestBlocks world = new TestBlocks().floor();
        assertFalse(PetPhysics.wedged(world, PetPhysics.boxAt(new Vec3(0.5, 0, 0.5), WIDTH, HEIGHT)),
                "standing on the floor is not being stuck in it");

        world.solid(0, 0, 0);
        assertTrue(PetPhysics.wedged(world, PetPhysics.boxAt(new Vec3(0.5, 0, 0.5), WIDTH, HEIGHT)),
                "a block placed on top of the pet leaves it wedged");
    }

    @Test
    void teleportsToTheOwnersOwnLevelRatherThanOntoTheWallBesideThem() {
        TestBlocks world = new TestBlocks().floor().solid(0, 0, -2).solid(0, 1, -2);

        Vec3 spot = PetPhysics.findTeleportSpot(world, new Vec3(0.5, 0, 0.5), WIDTH, HEIGHT, 180f);

        assertNotNull(spot, "there is plenty of floor to stand on");
        assertEquals(0, spot.y, EPSILON, "should stay on the ground, not climb the wall; was " + spot);
    }

    @Test
    void findsNowhereToStandOverThinAir() {
        TestBlocks world = new TestBlocks();

        assertEquals(null, PetPhysics.findStandingSpot(world, new Vec3(0, 0, 0), WIDTH, HEIGHT),
                "nothing to stand on means no spot, which is what keeps wandering off ledges");
    }
}
