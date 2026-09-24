package dev.lovepaw.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetPhysicsTest {
    private static final float WIDTH = 0.6f;
    private static final float HEIGHT = 0.6f;
    private static final double EPSILON = 1.0E-6;

    private static final class Blocks implements PetPhysics.Space {
        private final Set<Long> solid = new HashSet<>();

        Blocks solid(int x, int y, int z) {
            solid.add(key(x, y, z));
            return this;
        }

        Blocks floor() {
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    solid(x, -1, z);
                }
            }
            return this;
        }

        @Override
        public boolean free(AABB box) {
            for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX - EPSILON); x++) {
                for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY - EPSILON); y++) {
                    for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ - EPSILON); z++) {
                        if (solid.contains(key(x, y, z))) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private static long key(int x, int y, int z) {
            return (((long) x & 0xFFFFF) << 40) | (((long) y & 0xFFFFF) << 20) | ((long) z & 0xFFFFF);
        }
    }

    @Test
    void walksUpAStepWithinItsStepHeight() {
        Blocks world = new Blocks().floor().solid(1, 0, 0);
        AABB box = PetPhysics.boxAt(new Vec3(0.6, 0, 0.5), WIDTH, HEIGHT);

        Vec3 moved = PetPhysics.move(world, box, new Vec3(0.3, 0, 0), 1.0);

        assertTrue(moved.x > 0.25, "should have got onto the block; moved " + moved.x);
        assertTrue(moved.y > 0.9, "and stepped up onto its top face; rose " + moved.y);
    }

    @Test
    void stopsAtAStepTooTallToWalkUp() {
        Blocks world = new Blocks().floor().solid(1, 0, 0);
        AABB box = PetPhysics.boxAt(new Vec3(0.6, 0, 0.5), WIDTH, HEIGHT);

        Vec3 moved = PetPhysics.move(world, box, new Vec3(0.3, 0, 0), 0.6);

        assertTrue(moved.x < 0.2, "should be stopped by the block; moved " + moved.x);
    }

    @Test
    void landsFlushOnTheGroundRatherThanAHairAboveIt() {
        Blocks world = new Blocks().floor();
        AABB box = PetPhysics.boxAt(new Vec3(0.5, 0.3, 0.5), WIDTH, HEIGHT);

        Vec3 moved = PetPhysics.move(world, box, new Vec3(0, -0.5, 0), 1.0);

        double gap = 0.3 + moved.y;
        assertTrue(gap < 0.01, "should come to rest on the floor; stopped " + gap + " above it");
        assertTrue(PetPhysics.onGround(world, box.move(0, moved.y, 0)),
                "and count as standing on it");
    }

    @Test
    void stopsFlushAgainstAWallToo() {
        Blocks world = new Blocks().floor().solid(1, 0, 0);
        AABB box = PetPhysics.boxAt(new Vec3(0.6, 0, 0.5), WIDTH, HEIGHT);

        Vec3 moved = PetPhysics.move(world, box, new Vec3(0.4, 0, 0), 0);

        double gap = 1 - (0.6 + WIDTH / 2 + moved.x);
        assertTrue(Math.abs(gap) < 0.01, "should end up against the wall; gap was " + gap);
    }

    @Test
    void knowsWhenItIsInsideSomethingRatherThanTouchingIt() {
        Blocks world = new Blocks().floor();
        assertFalse(PetPhysics.wedged(world, PetPhysics.boxAt(new Vec3(0.5, 0, 0.5), WIDTH, HEIGHT)),
                "standing on the floor is not being stuck in it");

        world.solid(0, 0, 0);
        assertTrue(PetPhysics.wedged(world, PetPhysics.boxAt(new Vec3(0.5, 0, 0.5), WIDTH, HEIGHT)),
                "a block placed on top of the pet leaves it wedged");
    }

    @Test
    void teleportsToTheOwnersOwnLevelRatherThanOntoTheWallBesideThem() {
        Blocks world = new Blocks().floor().solid(0, 0, -2).solid(0, 1, -2);

        Vec3 spot = PetPhysics.findTeleportSpot(world, new Vec3(0.5, 0, 0.5), WIDTH, HEIGHT, 180f);

        assertNotNull(spot, "there is plenty of floor to stand on");
        assertEquals(0, spot.y, EPSILON, "should stay on the ground, not climb the wall; was " + spot);
    }

    @Test
    void findsNowhereToStandOverThinAir() {
        Blocks world = new Blocks();

        assertEquals(null, PetPhysics.findStandingSpot(world, new Vec3(0, 0, 0), WIDTH, HEIGHT),
                "nothing to stand on means no spot, which is what keeps wandering off ledges");
    }
}
