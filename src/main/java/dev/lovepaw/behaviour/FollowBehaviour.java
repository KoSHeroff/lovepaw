package dev.lovepaw.behaviour;

import dev.lovepaw.pet.PetKind;
import net.minecraft.world.phys.Vec3;


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * The default behaviour: a pet lives in a patch of ground, not on a leash.
 *
 * <p>The patch has an anchor — normally wherever the owner last settled. While
 * the owner stays near it the pet ignores them entirely and gets on with its
 * own life: wandering to spots it picks itself, looking around, sitting down.
 * That is a tamed cat's day, and how far it strays is the cat's business, not
 * the pack author's — the numbers all come from {@link PetKind}.
 *
 * <p>When the owner leaves that radius the pet moves house rather than giving
 * chase. It works out where the owner is heading from how they are moving,
 * anchors its patch there, and runs to a spot in it — so it arrives alongside
 * them instead of trailing behind. While the owner keeps moving the prediction
 * keeps updating; the moment they stop, the anchor settles on where they
 * stopped and the pet goes back to its own business.
 */
public final class FollowBehaviour implements PetBehaviour {
    private static final double ARRIVED = 0.7;
    private static final float WANDER_TIMEOUT_SECONDS = 6f;
    private static final float REST_MIN_SECONDS = 1.5f;
    private static final float REST_MAX_SECONDS = 5f;
    private static final float SIT_MIN_SECONDS = 4f;
    private static final float SIT_MAX_SECONDS = 12f;
    private static final float GLANCE_MIN_SECONDS = 2f;
    private static final float GLANCE_MAX_SECONDS = 4.5f;
    private static final double GLANCE_SPREAD = 1.5;
    private static final int TARGET_ATTEMPTS = 8;
    private static final double MAX_LEAD = 6;
    private static final double RETARGET_DISTANCE = 2;
    private static final float STUDY_MIN_SECONDS = 2.5f;
    private static final float STUDY_MAX_SECONDS = 7f;
    private static final float REACH_IT_SECONDS = 9f;
    private static final double CLOSE_ENOUGH_TO_LOOK = 2.0;
    private static final int THINGS_REMEMBERED = 5;
    private static final double SAME_THING = 2.5;
    /**
     * Decisions are taken on this grid of world ticks, never between. Every
     * client simulating the same pet reaches a boundary at the same moment and
     * asks the same seeded random the same question, so they all decide alike.
     */
    private static final long SLOT = 10;
    /**
     * A game belongs to a stretch of world time rather than to whoever started
     * it. Both pets work out the same answers from the clock, so it does not
     * matter that one of them joined a moment later than the other.
     */
    private static final long GAME_TICKS = 300;
    /** How long one shape of a game lasts before the roles turn over. */
    private static final long SWAP_TICKS = 60;
    /** Keeps the two questions asked of one pet from sharing an answer. */
    private static final long MOOD = 0x9E3779B97F4A7C15L;
    private static final long ROLE = 0xC2B2AE3D27D4EB4FL;
    /** Close enough to have caught the other one. */
    private static final double TAGGED = 1.2;
    /** How far the one being chased tries to get away. */
    private static final double RUN_TO = 5;
    private static final float SIT_AND_STARE = 0.4f;

    private enum Mode {
        /** Moving the patch to where the owner is going. */
        RELOCATE,
        /** Ambling to a spot inside the patch. */
        WANDER,
        /** Standing around in the patch. */
        REST,
        /** On its way to something it noticed. */
        INSPECT,
        /** Stood in front of that something, looking at it. */
        STUDY,
        /** In a game of chase with the pets around it, on one side or the other. */
        PLAY
    }

    private Mode mode = Mode.REST;
    private Vec3 anchor;
    private Vec3 travelTarget;
    private Vec3 travelAnchor;
    private Vec3 wanderTarget;
    private long decideAt;
    private long glanceAt;
    private Vec3 glanceTarget;
    private Vec3 interest;
    private final Deque<Vec3> alreadySeen = new ArrayDeque<>();

    @Override
    public void tick(PetActor actor) {
        PetKind kind = actor.kind();

        if (anchor == null) {
            anchor = actor.ownerPosition();
        }

        if (actor.distanceToOwner() > kind.teleportDistance()) {
            actor.teleportToOwner();
            anchor = actor.ownerPosition();
            restFor(actor, REST_MIN_SECONDS);
            return;
        }

        boolean ownerLeftThePatch = horizontalDistance(actor.ownerPosition(), anchor) > kind.followRadius();
        if (ownerLeftThePatch || mode == Mode.RELOCATE) {
            aimAtOwner(actor, kind);
        }

        switch (mode) {
            case RELOCATE -> relocate(actor, kind);
            case WANDER -> wander(actor, kind);
            case INSPECT -> inspect(actor, kind);
            case STUDY -> study(actor);
            case PLAY -> play(actor, kind);
            case REST -> rest(actor, kind);
        }
    }

    private void aimAtOwner(PetActor actor, PetKind kind) {
        actor.setSitting(false);
        wanderTarget = null;
        anchor = predictOwner(actor, kind);

        boolean needsTarget = mode != Mode.RELOCATE
                || travelTarget == null
                || travelAnchor == null
                || horizontalDistance(travelAnchor, anchor) > RETARGET_DISTANCE;

        if (needsTarget) {
            Vec3 target = pickSpotAround(actor, anchor, kind.strollRadius() * 0.5);
            travelTarget = target != null ? target : anchor;
            travelAnchor = anchor;
        }

        mode = Mode.RELOCATE;
    }

    private Vec3 predictOwner(PetActor actor, PetKind kind) {
        Vec3 owner = actor.ownerPosition();
        Vec3 velocity = actor.ownerVelocity();

        double lead = kind.predictionSeconds() * 20;
        Vec3 ahead = new Vec3(velocity.x * lead, 0, velocity.z * lead);
        double length = ahead.length();
        if (length > MAX_LEAD) {
            ahead = ahead.scale(MAX_LEAD / length);
        }

        Vec3 predicted = owner.add(ahead);
        Vec3 standing = actor.findStandingSpot(predicted);
        return standing != null ? standing : owner;
    }

    private void relocate(PetActor actor, PetKind kind) {
        double distance = horizontalDistance(actor.position(), travelTarget);
        if (distance <= Math.max(ARRIVED, kind.stopDistance())) {
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
            return;
        }

        float speed = distance > kind.runDistance() ? kind.runSpeed() : kind.walkSpeed();
        actor.walkTowards(travelTarget, speed);
        actor.faceMotion();

        if (actor.isStuck()) {
            actor.teleportToOwner();
            anchor = actor.ownerPosition();
            restFor(actor, REST_MIN_SECONDS);
        }
    }

    private void rest(PetActor actor, PetKind kind) {
        actor.stand();

        if (due(actor, glanceAt)) {
            glanceAt = deadline(actor, randomBetween(actor, GLANCE_MIN_SECONDS, GLANCE_MAX_SECONDS));
            glanceTarget = actor.random().nextFloat() < 0.5f ? ownerGlance(actor) : randomGlance(actor);
            actor.face(glanceTarget);
        }

        if (!due(actor, decideAt)) {
            return;
        }

        if (actor.isSitting()) {
            actor.setSitting(false);
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
            return;
        }

        if (startPlaying(actor, kind)) {
            return;
        }

        if (actor.random().nextFloat() < kind.curiosity()
                && goAndLook(actor, kind)) {
            return;
        }

        if (actor.random().nextFloat() > kind.sitChance()) {
            Vec3 target = pickSpotAround(actor, anchor, kind.strollRadius());
            if (target != null && horizontalDistance(actor.position(), target) > ARRIVED) {
                mode = Mode.WANDER;
                wanderTarget = target;
                decideAt = deadline(actor, WANDER_TIMEOUT_SECONDS);
                return;
            }
        }

        if (actor.onGround() && !actor.inWater()) {
            actor.setSitting(true);
        }
        restFor(actor, actor.isSitting()
                ? randomBetween(actor, SIT_MIN_SECONDS, SIT_MAX_SECONDS)
                : randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
    }

    /**
     * Picks something nearby worth a look and sets off towards it. A pet that
     * only ever ambles to a random patch of ground is a screensaver; noticing
     * the bed, the sign, somebody's chicken is what reads as alive.
     */
    private boolean goAndLook(PetActor actor, PetKind kind) {
        Vec3 thing = actor.findSomethingInteresting(anchor, kind.interestRadius());
        if (thing == null || seenLately(thing)) {
            return false;
        }
        Vec3 spot = actor.findStandingSpot(thing);
        if (spot == null || horizontalDistance(actor.position(), thing) < CLOSE_ENOUGH_TO_LOOK) {
            // Right next to it already, or nowhere to stand: remember it either
            // way, or the pet keeps picking the same unreachable thing.
            remember(thing);
            return false;
        }
        interest = thing;
        mode = Mode.INSPECT;
        decideAt = deadline(actor, REACH_IT_SECONDS);
        return true;
    }

    private void inspect(PetActor actor, PetKind kind) {
        double distance = horizontalDistance(actor.position(), interest);

        if (distance <= CLOSE_ENOUGH_TO_LOOK) {
            remember(interest);
            mode = Mode.STUDY;
            decideAt = deadline(actor, randomBetween(actor, STUDY_MIN_SECONDS, STUDY_MAX_SECONDS));
            actor.stand();
            if (actor.onGround() && !actor.inWater() && actor.random().nextFloat() < SIT_AND_STARE) {
                actor.setSitting(true);
            }
            return;
        }

        if (due(actor, decideAt) || actor.isStuck()) {
            remember(interest);
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
            return;
        }

        actor.walkTowards(interest, kind.strollSpeed());
        actor.faceMotion();
    }

    private void study(PetActor actor) {
        actor.stand();
        actor.face(interest);
        if (due(actor, decideAt)) {
            actor.setSitting(false);
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
        }
    }

    /** Things just looked at, so the pet does not shuttle between the same two. */
    private void remember(Vec3 thing) {
        alreadySeen.addLast(thing);
        while (alreadySeen.size() > THINGS_REMEMBERED) {
            alreadySeen.removeFirst();
        }
    }

    private boolean seenLately(Vec3 thing) {
        return alreadySeen.stream().anyMatch(old -> old.distanceToSqr(thing) < SAME_THING * SAME_THING);
    }

    /**
     * Joins a game, if there is one to join.
     *
     * <p>Nobody is invited and nobody agrees to anything. Every pet works out
     * from the world clock and its own owner whether it is in the mood and
     * whether it is the one doing the chasing — and it can work the same out
     * for every pet it can see, because those answers need nothing from that
     * pet but its owner and how playful its kind is. So all of them reach the
     * same picture of who is after whom without a word passing between them, on
     * every client at once.
     *
     * <p>That picture is a group, not a pair. Three pets in the mood come out
     * two against one; a moment later the roles turn over and it is one against
     * two. Nothing had to be arranged for that: it falls out of each pet
     * answering the same question about itself.
     */
    private boolean startPlaying(PetActor actor, PetKind kind) {
        long era = gameStart(actor);
        if (!inTheMood(actor.ownerId(), kind.playfulness(), era)) {
            return false;
        }
        boolean chasing = chasesAt(actor.ownerId(), era, actor.worldTime());
        if (others(actor, kind.interestRadius(), era, !chasing).isEmpty()) {
            return false;
        }

        decideAt = era + GAME_TICKS;
        mode = Mode.PLAY;
        actor.setSitting(false);
        return true;
    }

    private void play(PetActor actor, PetKind kind) {
        long era = gameStart(actor);
        if (due(actor, decideAt) || !inTheMood(actor.ownerId(), kind.playfulness(), era)) {
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
            return;
        }

        boolean chasing = chasesAt(actor.ownerId(), era, actor.worldTime());
        List<PetActor.Nearby> them = others(actor, kind.interestRadius() * 1.5, era, !chasing);
        if (them.isEmpty()) {
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
            return;
        }

        if (chasing) {
            // With two to choose from it goes for whichever is closer, and
            // changes its mind as they move: that is what chasing two pets
            // about looks like from the outside.
            Vec3 nearest = nearest(actor.position(), them);
            actor.walkTowards(nearest, kind.runSpeed());
            actor.faceMotion();
            if (horizontalDistance(actor.position(), nearest) < TAGGED) {
                actor.face(nearest);
            }
            return;
        }

        // Being chased, by one pet or by several. Running from the nearest
        // alone would send it straight into the others, so it runs the way that
        // puts distance between it and all of them at once.
        Vec3 target = actor.position().add(escape(actor.position(), them).scale(RUN_TO));

        // But not out of its patch: the game is not worth losing its owner
        // over. At the edge it runs round the far side rather than back into
        // whoever is after it, which is what bolting for home used to amount to.
        double limit = kind.strollRadius() * 1.5;
        Vec3 fromAnchor = target.subtract(anchor);
        double out = Math.sqrt(fromAnchor.x * fromAnchor.x + fromAnchor.z * fromAnchor.z);
        if (out > limit) {
            target = anchor.add(fromAnchor.x / out * limit, 0, fromAnchor.z / out * limit);
        }

        Vec3 spot = actor.findStandingSpot(target);
        actor.walkTowards(spot != null ? spot : target, kind.runSpeed());
        actor.faceMotion();
    }

    /** The pets in this game that are on the other side of it. */
    private static List<PetActor.Nearby> others(PetActor actor, double radius, long era, boolean chasing) {
        List<PetActor.Nearby> found = new ArrayList<>();
        for (PetActor.Nearby other : actor.petsNearby(radius)) {
            if (inTheMood(other.owner(), other.playfulness(), era)
                    && chasesAt(other.owner(), era, actor.worldTime()) == chasing) {
                found.add(other);
            }
        }
        return found;
    }

    /**
     * Whichever of them is closest, and on a tie whichever owner sorts first —
     * so two clients watching the same pet never pick differently.
     */
    private static Vec3 nearest(Vec3 from, List<PetActor.Nearby> them) {
        PetActor.Nearby closest = them.get(0);
        double best = horizontalDistance(from, closest.position());
        for (PetActor.Nearby one : them) {
            double distance = horizontalDistance(from, one.position());
            if (distance < best - 1.0E-9
                    || (distance < best + 1.0E-9 && one.owner().compareTo(closest.owner()) < 0)) {
                closest = one;
                best = distance;
            }
        }
        return closest.position();
    }

    /**
     * The way out: each pursuer pushes equally, so one being chased by two runs
     * between and past them rather than into whichever it was not looking at.
     */
    static Vec3 escape(Vec3 from, List<PetActor.Nearby> chasers) {
        double x = 0;
        double z = 0;
        for (PetActor.Nearby chaser : chasers) {
            double dx = from.x - chaser.position().x;
            double dz = from.z - chaser.position().z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 0.01) {
                continue;
            }
            x += dx / length;
            z += dz / length;
        }

        double length = Math.sqrt(x * x + z * z);
        if (length >= 0.01) {
            return new Vec3(x / length, 0, z / length);
        }

        // Caught between them with nowhere straight to go: it breaks sideways
        // past the nearest one rather than standing there being surrounded.
        Vec3 nearest = nearest(from, chasers);
        double dx = from.x - nearest.x;
        double dz = from.z - nearest.z;
        double away = Math.sqrt(dx * dx + dz * dz);
        return away < 0.01 ? new Vec3(1, 0, 0) : new Vec3(-dz / away, 0, dx / away);
    }

    /** The stretch of world time this game belongs to. */
    private static long gameStart(PetActor actor) {
        return (actor.worldTime() / GAME_TICKS) * GAME_TICKS;
    }

    /**
     * Whether a pet is up for a game at all in this stretch of time. Anybody
     * can work this out about anybody, which is the point: how playful a pet is
     * comes from its kind, and that travels with it.
     */
    static boolean inTheMood(UUID owner, float playfulness, long era) {
        return playfulness > 0 && unitFloat(roll(owner, era, MOOD)) < playfulness;
    }

    /**
     * Whether a pet is chasing rather than being chased, right now. The sides
     * turn over partway through, so a game that started as two against one
     * finishes as one against two.
     */
    static boolean chasesAt(UUID owner, long era, long worldTime) {
        boolean chases = (roll(owner, era, ROLE) & 1L) != 0;
        boolean turned = ((worldTime - era) / SWAP_TICKS) % 2 == 1;
        return chases != turned;
    }

    /**
     * One number for one pet in one stretch of world time.
     *
     * <p>Worked out with arithmetic rather than a random source, for two
     * reasons. A pet in a crowd asks this of every pet it can see, every tick,
     * and handing out generators for that would cost more than the game is
     * worth. And the answer is this mod's own arithmetic rather than somebody
     * else's generator, so it cannot drift between clients or between versions
     * of the game underneath us.
     */
    private static long roll(UUID owner, long era, long salt) {
        long seed = owner.getMostSignificantBits() * 31 + owner.getLeastSignificantBits();
        return mix((seed * salt) ^ (era * 0x9E3779B97F4A7C15L));
    }

    /** The usual bit-mixing finaliser: every input bit reaches every output bit. */
    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        value *= 0xC4CEB9FE1A85EC53L;
        return value ^ (value >>> 33);
    }

    /** The top bits of that, as a number from 0 up to but not including 1. */
    private static float unitFloat(long bits) {
        return (bits >>> 40) / (float) (1 << 24);
    }

    private void wander(PetActor actor, PetKind kind) {
        boolean arrived = wanderTarget == null || horizontalDistance(actor.position(), wanderTarget) < ARRIVED;
        if (arrived || due(actor, decideAt) || actor.isStuck()) {
            wanderTarget = null;
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
            return;
        }

        actor.walkTowards(wanderTarget, kind.strollSpeed());
        actor.faceMotion();
    }

    private void restFor(PetActor actor, float seconds) {
        mode = Mode.REST;
        decideAt = deadline(actor, seconds);
        wanderTarget = null;
        glanceAt = 0;
        actor.stand();
    }

    /**
     * A moment in world time, rounded onto the decision grid. Using the world
     * clock rather than counting down from whenever this client happened to
     * start the pet is what keeps two players' copies in step.
     */
    private static long deadline(PetActor actor, float seconds) {
        long ticks = Math.max(SLOT, Math.round(seconds * 20.0 / SLOT) * SLOT);
        long boundary = (actor.worldTime() / SLOT) * SLOT;
        return boundary + ticks;
    }

    private static boolean due(PetActor actor, long when) {
        return actor.worldTime() >= when;
    }

    private Vec3 pickSpotAround(PetActor actor, Vec3 centre, double radius) {
        double reach = Math.max(1, radius);
        for (int attempt = 0; attempt < TARGET_ATTEMPTS; attempt++) {
            double angle = actor.random().nextDouble() * Math.PI * 2;
            double length = actor.random().nextDouble() * reach;
            Vec3 candidate = centre.add(Math.cos(angle) * length, 0, Math.sin(angle) * length);

            Vec3 standing = actor.findStandingSpot(candidate);
            if (standing != null) {
                return standing;
            }
        }
        return null;
    }

    private static Vec3 ownerGlance(PetActor actor) {
        Vec3 owner = actor.ownerPosition();
        double sideways = (actor.random().nextDouble() * 2 - 1) * GLANCE_SPREAD;
        double forwards = (actor.random().nextDouble() * 2 - 1) * GLANCE_SPREAD;
        return owner.add(sideways, 0, forwards);
    }

    private static Vec3 randomGlance(PetActor actor) {
        double angle = actor.random().nextDouble() * Math.PI * 2;
        return actor.position().add(Math.cos(angle) * 6, actor.random().nextDouble() * 2 - 0.5, Math.sin(angle) * 6);
    }

    private static float randomBetween(PetActor actor, float min, float max) {
        return min + actor.random().nextFloat() * (max - min);
    }

    private static double horizontalDistance(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
