package dev.lovepaw.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Works out a way round things, block by block.
 *
 * <p>Walking straight at a target is enough until something is in the way, and
 * then it is hopeless: a pet meeting the corner of a house grinds along it
 * until the stuck rescue teleports it, which looks like a broken toy rather
 * than an animal. This is an A* over the blocks a pet could stand on, the same
 * shape of search a villager does, cut down to what a cosmetic pet needs.
 *
 * <p>It asks the world only whether a box fits, through {@link PetPhysics.Space},
 * which is what keeps it testable against a handful of blocks instead of a
 * running game — and what keeps it honest about the pet's real size.
 */
public final class PetPathfinder {
    /** Nodes expanded before the search gives up, a few milliseconds' work. */
    private static final int MAX_NODES = 320;
    /** How far from the start a path may wander, in blocks. */
    private static final int MAX_RANGE = 24;
    /** How far a pet will drop on its way somewhere. */
    private static final int MAX_DROP = 3;
    private static final double DIAGONAL = Math.sqrt(2);
    /** Climbing and dropping are worth avoiding when flat ground would do. */
    private static final double CLIMB_COST = 0.6;
    private static final double DROP_COST = 0.4;

    private static final int[][] STEPS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
    };

    private PetPathfinder() {
    }

    /** What the pet can fit through and how far it can climb. */
    public record Shape(float width, float height, double stepHeight) {
    }

    /**
     * A route from {@code from} to the block holding {@code to}, as the centres
     * of the blocks to walk over, or an empty list when there is no way there
     * within the search budget. The starting block is not included.
     */
    public static List<Vec3> find(PetPhysics.Space space, Vec3 from, Vec3 to, Shape shape) {
        BlockPos start = standingBlock(space, BlockPos.containing(from), shape);
        BlockPos goal = standingBlock(space, BlockPos.containing(to), shape);
        if (start == null || goal == null || start.equals(goal)) {
            return List.of();
        }

        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double> cost = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::estimate));

        cost.put(start, 0.0);
        open.add(new Node(start, heuristic(start, goal)));

        int expanded = 0;
        while (!open.isEmpty() && expanded < MAX_NODES) {
            Node node = open.poll();
            BlockPos at = node.pos();
            if (at.equals(goal)) {
                return walkBack(cameFrom, start, goal);
            }
            expanded++;

            for (int[] step : STEPS) {
                BlockPos next = stepTo(space, at, step[0], step[1], shape);
                if (next == null || start.distSqr(next) > MAX_RANGE * MAX_RANGE) {
                    continue;
                }
                double climb = next.getY() - at.getY();
                double price = (step[0] != 0 && step[1] != 0 ? DIAGONAL : 1)
                        + (climb > 0 ? CLIMB_COST : 0)
                        + (climb < 0 ? DROP_COST * -climb : 0);
                double through = cost.get(at) + price;
                if (through < cost.getOrDefault(next, Double.MAX_VALUE)) {
                    cost.put(next, through);
                    cameFrom.put(next, at);
                    open.add(new Node(next, through + heuristic(next, goal)));
                }
            }
        }
        return List.of();
    }

    /**
     * Where the pet ends up stepping one block sideways: the same height, one
     * up if it can climb that, or down onto whatever it lands on. Null when
     * that way is blocked.
     */
    private static BlockPos stepTo(PetPhysics.Space space, BlockPos from, int dx, int dz, Shape shape) {
        BlockPos level = from.offset(dx, 0, dz);
        if (dx != 0 && dz != 0) {
            // A diagonal has to be walkable both ways round the corner, or the
            // pet clips through the join between two blocks.
            if (!fits(space, from.offset(dx, 0, 0), shape) || !fits(space, from.offset(0, 0, dz), shape)) {
                return null;
            }
        }
        if (fits(space, level, shape)) {
            return standingBlock(space, level, shape);
        }
        if (shape.stepHeight() >= 1) {
            BlockPos up = level.above();
            if (fits(space, up, shape) && supported(space, up, shape)) {
                return up;
            }
        }
        return null;
    }

    /** The block the pet actually stands in when aiming at this one. */
    private static BlockPos standingBlock(PetPhysics.Space space, BlockPos pos, Shape shape) {
        if (!fits(space, pos, shape)) {
            BlockPos up = pos.above();
            return fits(space, up, shape) && supported(space, up, shape) ? up : null;
        }
        if (supported(space, pos, shape)) {
            return pos;
        }
        for (int drop = 1; drop <= MAX_DROP; drop++) {
            BlockPos below = pos.below(drop);
            if (!fits(space, below, shape)) {
                return null;
            }
            if (supported(space, below, shape)) {
                return below;
            }
        }
        return null;
    }

    private static boolean fits(PetPhysics.Space space, BlockPos pos, Shape shape) {
        return space.free(boxIn(pos, shape));
    }

    private static boolean supported(PetPhysics.Space space, BlockPos pos, Shape shape) {
        return !space.free(boxIn(pos, shape).move(0, -0.1, 0));
    }

    private static AABB boxIn(BlockPos pos, Shape shape) {
        return PetPhysics.boxAt(
                new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                shape.width(), shape.height());
    }

    private static double heuristic(BlockPos from, BlockPos to) {
        double dx = Math.abs(from.getX() - to.getX());
        double dz = Math.abs(from.getZ() - to.getZ());
        double dy = Math.abs(from.getY() - to.getY()) * CLIMB_COST;
        return Math.max(dx, dz) + (DIAGONAL - 1) * Math.min(dx, dz) + dy;
    }

    private static List<Vec3> walkBack(Map<BlockPos, BlockPos> cameFrom, BlockPos start, BlockPos goal) {
        Deque<Vec3> route = new ArrayDeque<>();
        BlockPos at = goal;
        while (!at.equals(start)) {
            route.addFirst(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
            at = cameFrom.get(at);
            if (at == null) {
                return List.of();
            }
        }
        return new ArrayList<>(route);
    }

    private record Node(BlockPos pos, double estimate) {
    }
}
