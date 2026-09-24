package dev.lovepaw.client;

import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

/** A world that is empty apart from the whole blocks put in it. */
final class TestBlocks implements PetPhysics.Space {
    private static final double EPSILON = 1.0E-6;

    private final Set<Long> solid = new HashSet<>();

    TestBlocks solid(int x, int y, int z) {
        solid.add(key(x, y, z));
        return this;
    }

    /** Ground everywhere under y = 0, out to {@code reach} blocks. */
    TestBlocks floor(int reach) {
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                solid(x, -1, z);
            }
        }
        return this;
    }

    TestBlocks floor() {
        return floor(8);
    }

    /** A wall of full blocks along x, two high. */
    TestBlocks wall(int fromX, int toX, int z, int height) {
        for (int x = fromX; x <= toX; x++) {
            for (int y = 0; y < height; y++) {
                solid(x, y, z);
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
