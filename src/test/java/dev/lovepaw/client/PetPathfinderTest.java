package dev.lovepaw.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routing round things. What these pin down is the difference between a pet
 * that walks round the corner of a house and one that grinds along it until
 * the stuck rescue teleports it away.
 */
class PetPathfinderTest {
    private static final PetPathfinder.Shape PET = new PetPathfinder.Shape(0.6f, 0.6f, 1.0);
    private static final PetPathfinder.Shape SHORT_LEGS = new PetPathfinder.Shape(0.6f, 0.6f, 0.6);

    private static Vec3 at(double x, double y, double z) {
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    private static boolean passesThrough(List<Vec3> route, int x, int z) {
        return route.stream().anyMatch(p -> (int) Math.floor(p.x) == x && (int) Math.floor(p.z) == z);
    }

    @Test
    void goesRoundAWallRatherThanIntoIt() {
        // A wall across the way with one gap in it, at x = 3.
        TestBlocks world = new TestBlocks().floor(12).wall(-4, 2, 2, 2).wall(4, 8, 2, 2);

        List<Vec3> route = PetPathfinder.find(world, at(0, 0, 0), at(0, 0, 5), PET);

        assertFalse(route.isEmpty(), "there is a way round, it should be found");
        assertTrue(passesThrough(route, 3, 2), "and it goes through the gap; route was " + route);
        assertTrue(route.get(route.size() - 1).distanceTo(at(0, 0, 5)) < 1.0,
                "the route ends where it was asked to end");
    }

    @Test
    void climbsAStepOnTheWay() {
        TestBlocks world = new TestBlocks().floor(12);
        for (int z = 2; z <= 6; z++) {
            for (int x = -4; x <= 4; x++) {
                world.solid(x, 0, z);            // a plateau one block high
            }
        }

        List<Vec3> route = PetPathfinder.find(world, at(0, 0, 0), at(0, 1, 5), PET);

        assertFalse(route.isEmpty(), "one block up is a step, not a wall");
        assertTrue(route.stream().anyMatch(p -> p.y >= 1), "the route should climb; was " + route);
    }

    @Test
    void aPetWithShortLegsWalksRoundTheSameStep() {
        TestBlocks world = new TestBlocks().floor(12);
        for (int x = -4; x <= 2; x++) {
            world.solid(x, 0, 2);
        }

        List<Vec3> route = PetPathfinder.find(world, at(0, 0, 0), at(0, 0, 5), SHORT_LEGS);

        assertFalse(route.isEmpty(), "there is open ground to the east of the step");
        assertTrue(route.stream().allMatch(p -> p.y < 1),
                "a pet that cannot step up must go round instead; route was " + route);
    }

    @Test
    void refusesToSqueezeThroughADiagonalGap() {
        // The goal is walled in on every side but one corner, and that corner
        // is the join between two blocks. A pet cannot fit through a join.
        TestBlocks world = new TestBlocks().floor(12);
        int[][] around = {{1, 0}, {0, 1}, {1, 2}, {2, 1}, {2, 0}, {0, 2}, {2, 2}};
        for (int[] cell : around) {
            for (int y = 0; y < 3; y++) {
                world.solid(cell[0], y, cell[1]);
            }
        }

        List<Vec3> route = PetPathfinder.find(world, at(0, 0, 0), at(1, 0, 1), PET);

        assertTrue(route.isEmpty(), "the corner between two blocks is not a doorway; route was " + route);
    }

    @Test
    void givesUpWhenThereIsNoWayThrough() {
        TestBlocks world = new TestBlocks().floor(12);
        for (int x = -13; x <= 13; x++) {          // right across the ground
            for (int y = 0; y < 3; y++) {
                world.solid(x, y, 2);
            }
        }

        assertTrue(PetPathfinder.find(world, at(0, 0, 0), at(0, 0, 5), PET).isEmpty(),
                "a wall with no gap has no route through it");
    }

    @Test
    void doesNotSearchForEver() {
        TestBlocks world = new TestBlocks().floor(64);

        long started = System.nanoTime();
        List<Vec3> route = PetPathfinder.find(world, at(0, 0, 0), at(0, 0, 60), PET);
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertTrue(route.isEmpty(), "a destination beyond its range is not this pet's problem");
        assertTrue(millis < 200, "the search has to stay cheap enough to run on a client; took " + millis + " ms");
    }
}
