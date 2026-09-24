package dev.lovepaw.client;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Just enough collision for something that walks beside you.
 *
 * <p>A pet is not an entity, so none of vanilla's movement code applies to it.
 * This does the two things that actually matter: it does not walk through
 * walls, and it climbs a step instead of stopping at it. It is deliberately
 * approximate — the pet is cosmetic, and a cheap test that never wedges is
 * worth more here than exact sweeping.
 */
public final class PetPhysics {
    private static final int CONTACT_STEPS = 10;

    /**
     * The only question this code asks the world: does the pet fit here?
     *
     * <p>Narrow on purpose. It keeps the collision maths free of the level, so
     * it can be reasoned about — and tested — against a handful of blocks
     * rather than a running game.
     */
    @FunctionalInterface
    public interface Space {
        boolean free(AABB box);

        static Space of(Level level) {
            return level::noCollision;
        }
    }

    private PetPhysics() {
    }

    /**
     * Moves {@code box} by {@code motion}, sliding along whatever it hits.
     *
     * @param stepHeight how high a ledge may be for the pet to walk up it
     * @return the movement actually performed
     */
    public static Vec3 move(Space space, AABB box, Vec3 motion, double stepHeight) {
        double movedY = axis(space, box, 0, motion.y, 0);
        AABB after = box.move(0, movedY, 0);

        double movedX = axis(space, after, motion.x, 0, 0);
        after = after.move(movedX, 0, 0);

        double movedZ = axis(space, after, 0, 0, motion.z);
        after = after.move(0, 0, movedZ);

        boolean blockedX = Math.abs(movedX - motion.x) > 1.0E-4;
        boolean blockedZ = Math.abs(movedZ - motion.z) > 1.0E-4;

        if (stepHeight > 0 && (blockedX || blockedZ)) {
            Vec3 stepped = tryStep(space, box.move(0, movedY, 0), motion, stepHeight);
            if (stepped != null && horizontalLength(stepped) > horizontalLength(new Vec3(movedX, 0, movedZ))) {
                return new Vec3(stepped.x, movedY + stepped.y, stepped.z);
            }
        }

        return new Vec3(movedX, movedY, movedZ);
    }

    private static Vec3 tryStep(Space space, AABB box, Vec3 motion, double stepHeight) {
        double lift = axis(space, box, 0, stepHeight, 0);
        if (lift <= 0) {
            return null;
        }

        AABB lifted = box.move(0, lift, 0);
        double movedX = axis(space, lifted, motion.x, 0, 0);
        lifted = lifted.move(movedX, 0, 0);
        double movedZ = axis(space, lifted, 0, 0, motion.z);
        lifted = lifted.move(0, 0, movedZ);

        if (movedX == 0 && movedZ == 0) {
            return null;
        }

        double drop = axis(space, lifted, 0, -lift, 0);
        return new Vec3(movedX, lift + drop, movedZ);
    }

    private static double axis(Space space, AABB box, double x, double y, double z) {
        double amount = x != 0 ? x : (y != 0 ? y : z);
        if (amount == 0) {
            return 0;
        }
        if (space.free(box.move(x, y, z))) {
            return amount;
        }

        double fits = 0;
        double blocked = 1;
        for (int step = 0; step < CONTACT_STEPS; step++) {
            double middle = (fits + blocked) / 2;
            if (space.free(box.move(x * middle, y * middle, z * middle))) {
                fits = middle;
            } else {
                blocked = middle;
            }
        }
        return amount * fits;
    }

    private static double horizontalLength(Vec3 vector) {
        return Math.sqrt(vector.x * vector.x + vector.z * vector.z);
    }

    /** True when the box is inside something solid rather than merely touching it. */
    public static boolean wedged(Space space, AABB box) {
        return !space.free(box.deflate(0.02));
    }

    /** True when the box is resting on something solid. */
    public static boolean onGround(Space space, AABB box) {
        return !space.free(box.move(0, -0.02, 0));
    }

    /** Finds a spot next to the owner the pet can stand in, or null. */
    public static Vec3 findTeleportSpot(Space space, Vec3 owner, float width, float height, float yaw) {
        double radians = Math.toRadians(yaw);
        double backX = Math.sin(radians);
        double backZ = -Math.cos(radians);

        Vec3[] candidates = {
                owner.add(backX * 1.5, 0, backZ * 1.5),
                owner.add(-backZ * 1.5, 0, backX * 1.5),
                owner.add(backZ * 1.5, 0, -backX * 1.5),
                owner
        };

        for (Vec3 candidate : candidates) {
            Vec3 standing = findStandingSpot(space, candidate, width, height);
            if (standing != null) {
                return standing;
            }
        }

        for (Vec3 candidate : candidates) {
            for (int dy = 0; dy <= 2; dy++) {
                Vec3 spot = candidate.add(0, dy, 0);
                if (space.free(boxAt(spot, width, height))) {
                    return spot;
                }
            }
        }
        return null;
    }

    /**
     * Looks for a place near {@code near} where the pet both fits and has
     * something under its feet, searching a little up and rather further down.
     * Returns null when there is nothing to stand on, which is how wandering
     * avoids walking off ledges and into walls.
     */
    public static Vec3 findStandingSpot(Space space, Vec3 near, float width, float height) {
        for (int dy : new int[]{0, -1, 1, -2, 2, -3, -4}) {
            Vec3 candidate = new Vec3(near.x, near.y + dy, near.z);
            AABB box = boxAt(candidate, width, height);
            if (space.free(box) && !space.free(box.move(0, -0.1, 0))) {
                return candidate;
            }
        }
        return null;
    }

    public static AABB boxAt(Vec3 position, float width, float height) {
        double half = width / 2.0;
        return new AABB(
                position.x - half, position.y, position.z - half,
                position.x + half, position.y + height, position.z + half);
    }
}
