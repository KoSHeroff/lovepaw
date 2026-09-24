package dev.lovepaw.client;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * How high a flying pet decides to be.
 *
 * <p>Holding a fixed height above the owner's feet is what a balloon on a
 * string does. A bee picks its own height over whatever it happens to be above,
 * drifts up and down while it thinks about it, and climbs when something is in
 * the way. The owner only comes into it through where the pet is going, not how
 * far off the ground it is.
 */
public final class PetHover {
    /** How much of the gap to the wanted height is closed per tick. */
    private static final double EASE = 0.12;
    /** Fastest climb or dive, in blocks per tick. */
    private static final double TOP_SPEED = 0.22;
    /** How far down it looks for something to fly above. */
    private static final int GROUND_REACH = 12;
    /** Ticks between the ground checks; between them the last answer stands. */
    private static final int GROUND_EVERY = 4;
    /** Ticks a chosen drift is held for. */
    private static final int DRIFT_MIN = 30;
    private static final int DRIFT_MAX = 110;
    /** Extra height wanted while something is in the way. */
    private static final double CLEAR_IT_BY = 1.5;
    /** How far the owner may get above the pet before it comes up after them. */
    private static final double LET_THEM_CLIMB = 3;

    private double drift;
    private int driftFor;
    private double ground = Double.NaN;
    private int groundIn;

    /**
     * Vertical speed for this tick.
     *
     * @param height how high above the ground this pet likes to fly
     * @param wander how far it drifts above and below that of its own accord
     * @param ownerY how high the owner is, which the pet ignores until they are
     *               well above it — a flyer picks its own height, but it is not
     *               going to stay by the ground while its owner climbs a tower
     * @param blocked it is trying to move and getting nowhere, so climbing may help
     */
    public double climb(PetPhysics.Space space, Vec3 position, float width, float petHeight,
                        double height, double wander, double ownerY, boolean blocked,
                        RandomSource random) {
        if (--driftFor <= 0) {
            driftFor = DRIFT_MIN + random.nextInt(DRIFT_MAX - DRIFT_MIN);
            drift = (random.nextDouble() * 2 - 1) * wander;
        }
        if (--groundIn <= 0) {
            groundIn = GROUND_EVERY;
            ground = groundUnder(space, position, width, petHeight);
        }

        double wanted = Double.isNaN(ground)
                ? position.y + drift * 0.25          // nothing below: just bob
                : ground + height + drift;
        if (blocked) {
            wanted = Math.max(wanted, position.y + CLEAR_IT_BY);
        }
        wanted = Math.max(wanted, ownerY - LET_THEM_CLIMB);
        wanted = Math.min(wanted, headroom(space, position, width, petHeight));

        return Math.max(-TOP_SPEED, Math.min(TOP_SPEED, (wanted - position.y) * EASE));
    }

    public void forget() {
        ground = Double.NaN;
        groundIn = 0;
    }

    /** The top of whatever is under the pet, or NaN over a drop. */
    private static double groundUnder(PetPhysics.Space space, Vec3 position, float width, float height) {
        double clear = position.y;
        for (int step = 1; step <= GROUND_REACH * 2; step++) {
            double y = position.y - step * 0.5;
            if (!isClear(space, position, y, width)) {
                // Somewhere between the last clear height and this one is the
                // surface; a slab or a stair is not on a whole block boundary,
                // so it is worth finding properly rather than rounding.
                double solid = y;
                for (int refine = 0; refine < 5; refine++) {
                    double middle = (clear + solid) / 2;
                    if (isClear(space, position, middle, width)) {
                        clear = middle;
                    } else {
                        solid = middle;
                    }
                }
                return clear;
            }
            clear = y;
        }
        return Double.NaN;
    }

    private static boolean isClear(PetPhysics.Space space, Vec3 position, double y, float width) {
        return space.free(PetPhysics.boxAt(new Vec3(position.x, y - 0.05, position.z), width, 0.05f));
    }

    /** How high it may go before it would bump its head. */
    private static double headroom(PetPhysics.Space space, Vec3 position, float width, float height) {
        for (int step = 1; step <= 8; step++) {
            double y = position.y + step * 0.5;
            if (!space.free(PetPhysics.boxAt(new Vec3(position.x, y, position.z), width, height))) {
                return y - 0.5;
            }
        }
        return Double.MAX_VALUE;
    }
}
