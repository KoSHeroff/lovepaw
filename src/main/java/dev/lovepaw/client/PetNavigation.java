package dev.lovepaw.client;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Decides where a pet should put its next step when the place it wants to be
 * is not in a straight line from where it is.
 *
 * <p>Walking straight is right nearly all the time and costs nothing, so that
 * is what happens until the pet is demonstrably getting nowhere. Only then does
 * it work out a route, and it keeps that route until the destination moves,
 * rather than thinking again every tick: a pet is cosmetic and there may be one
 * per player on screen.
 */
public final class PetNavigation {
    /** How close to a waypoint counts as having reached it. */
    private static final double REACHED = 0.6;
    /** How far the destination may drift before the route is worth redoing. */
    private static final double GOAL_MOVED = 1.5;
    /** Ticks between searches while the pet keeps being blocked. */
    private static final int SEARCH_COOLDOWN = 10;
    /** Ticks to wait after a search that found nothing, so it is not retried flat out. */
    private static final int FAILURE_COOLDOWN = 40;
    /** How finely a straight line is checked for obstructions. */
    private static final double LINE_STEP = 0.4;

    private List<Vec3> route = List.of();
    private int waypoint;
    private Vec3 goal;
    private int clock;
    private int nextSearch;

    /**
     * The point to head for this tick: a waypoint when following a route, and
     * otherwise the destination itself.
     *
     * @param blocked the pet has been trying to move and getting nowhere
     */
    public Vec3 steer(PetPhysics.Space space, Vec3 position, Vec3 destination,
                      PetPathfinder.Shape shape, boolean blocked) {
        clock++;

        if (goal == null || goal.distanceToSqr(destination) > GOAL_MOVED * GOAL_MOVED) {
            goal = destination;
            forget();
        }

        Vec3 next = follow(space, position, shape);
        if (next != null) {
            return next;
        }

        if (blocked && clock >= nextSearch) {
            route = PetPathfinder.find(space, position, destination, shape);
            waypoint = 0;
            nextSearch = clock + (route.isEmpty() ? FAILURE_COOLDOWN : SEARCH_COOLDOWN);
            next = follow(space, position, shape);
            if (next != null) {
                return next;
            }
        }

        return destination;
    }

    /** True while a route is being followed, rather than a straight line. */
    public boolean hasRoute() {
        return waypoint < route.size();
    }

    public void forget() {
        route = List.of();
        waypoint = 0;
    }

    private Vec3 follow(PetPhysics.Space space, Vec3 position, PetPathfinder.Shape shape) {
        while (waypoint < route.size()) {
            Vec3 point = route.get(waypoint);
            boolean reached = horizontalDistance(position, point) < REACHED;
            // Cutting to the waypoint after next whenever it is in plain sight
            // keeps a pet off the exact centre of every block it crosses, which
            // is what makes a path look walked rather than marched.
            boolean skippable = waypoint + 1 < route.size()
                    && lineIsClear(space, position, route.get(waypoint + 1), shape);
            if (reached || skippable) {
                waypoint++;
                continue;
            }
            return point;
        }
        return null;
    }

    /** Whether the pet could walk from one point to the other without turning. */
    static boolean lineIsClear(PetPhysics.Space space, Vec3 from, Vec3 to, PetPathfinder.Shape shape) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4) {
            return true;
        }
        int steps = (int) Math.ceil(length / LINE_STEP);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 at = new Vec3(from.x + dx * t, from.y, from.z + dz * t);
            if (!space.free(PetPhysics.boxAt(at, shape.width(), shape.height()))) {
                return false;
            }
        }
        return true;
    }

    private static double horizontalDistance(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
