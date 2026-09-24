package dev.lovepaw.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a pet bothers to work out a route, and when it just walks. */
class PetNavigationTest {
    private static final PetPathfinder.Shape PET = new PetPathfinder.Shape(0.6f, 0.6f, 1.0);

    private static Vec3 at(double x, double z) {
        return new Vec3(x + 0.5, 0, z + 0.5);
    }

    /** A wall across the way with a single gap in it, at x = 3. */
    private static TestBlocks walled() {
        return new TestBlocks().floor(12).wall(-4, 2, 2, 2).wall(4, 8, 2, 2);
    }

    @Test
    void walksStraightAtWhatItWants() {
        PetNavigation navigation = new PetNavigation();
        TestBlocks world = new TestBlocks().floor();
        Vec3 goal = at(0, 4);

        assertEquals(goal, navigation.steer(world, at(0, 0), goal, PET, false));
        assertFalse(navigation.hasRoute(), "open ground needs no route");
    }

    @Test
    void worksOutARouteOnceItIsPlainlyStuck() {
        PetNavigation navigation = new PetNavigation();
        TestBlocks world = walled();
        Vec3 goal = at(0, 5);

        assertEquals(goal, navigation.steer(world, at(0, 0), goal, PET, false),
                "it has not noticed the wall yet, and walking into it is how it finds out");

        Vec3 heading = navigation.steer(world, at(0, 0), goal, PET, true);

        assertTrue(navigation.hasRoute(), "being stuck at a wall is what a route is for");
        assertNotEquals(goal, heading);
        assertTrue(heading.x > 0.9, "the gap is to the east, so it should head that way; was " + heading);
    }

    @Test
    void keepsTheRouteWhileTheDestinationStaysPut() {
        PetNavigation navigation = new PetNavigation();
        TestBlocks world = walled();
        Vec3 goal = at(0, 5);

        navigation.steer(world, at(0, 0), goal, PET, true);
        for (int tick = 0; tick < 5; tick++) {
            navigation.steer(world, at(0, 0), goal, PET, false);
        }

        assertTrue(navigation.hasRoute(), "a route is not rethought every tick");
    }

    @Test
    void dropsTheRouteWhenTheDestinationMoves() {
        PetNavigation navigation = new PetNavigation();
        TestBlocks world = walled();

        navigation.steer(world, at(0, 0), at(0, 5), PET, true);
        assertTrue(navigation.hasRoute());

        navigation.steer(world, at(0, 0), at(6, 5), PET, false);

        assertFalse(navigation.hasRoute(), "the old route led somewhere the owner no longer is");
    }

    @Test
    void knowsWhetherItCouldJustWalkThere() {
        TestBlocks world = walled();

        assertTrue(PetNavigation.lineIsClear(world, at(0, 0), at(0, 1), PET));
        assertFalse(PetNavigation.lineIsClear(world, at(0, 0), at(0, 5), PET),
                "the wall is in the way of that one");
    }
}
