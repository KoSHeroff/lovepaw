package dev.lovepaw.behaviour;

import dev.lovepaw.pet.PetKind;
import net.minecraft.world.phys.Vec3;

import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
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
    /** How long one pet chases before they swap over. */
    private static final long SWAP_TICKS = 60;
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
        /** Chasing another player's pet about, or being chased by it. */
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
    private UUID playmate;
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
     * Looks for another player's pet to play with. Both pets work this out
     * separately, on their own clients, and reach the same answer: the dice are
     * seeded from the pair of owners and the world clock, so neither has to
     * tell the other anything. Whichever is the chaser is decided the same way.
     */
    private boolean startPlaying(PetActor actor, PetKind kind) {
        if (kind.playfulness() <= 0) {
            return false;
        }
        for (PetActor.Nearby other : actor.petsNearby(kind.interestRadius())) {
            RandomSource dice = gameDice(actor, other.owner());
            // The shyer of the two sets the odds, so a pet whose pack says it
            // never plays is never dragged into a game.
            if (dice.nextFloat() >= Math.min(kind.playfulness(), other.playfulness())) {
                continue;
            }
            playmate = other.owner();
            decideAt = gameStart(actor) + GAME_TICKS;
            mode = Mode.PLAY;
            actor.setSitting(false);
            return true;
        }
        return false;
    }

    private void play(PetActor actor, PetKind kind) {
        Vec3 them = null;
        for (PetActor.Nearby other : actor.petsNearby(kind.interestRadius() * 1.5)) {
            if (other.owner().equals(playmate)) {
                them = other.position();
            }
        }
        if (them == null || due(actor, decideAt)) {
            playmate = null;
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
            return;
        }

        if (chasing(actor)) {
            actor.walkTowards(them, kind.runSpeed());
            actor.faceMotion();
            if (horizontalDistance(actor.position(), them) < TAGGED) {
                actor.face(them);
            }
            return;
        }

        // Being chased: run away, but not out of the patch — the game is not
        // worth losing its owner over. At the edge it runs round the far side
        // rather than back into the chaser, which is what bolting for home
        // used to amount to.
        Vec3 away = actor.position().subtract(them);
        double length = Math.sqrt(away.x * away.x + away.z * away.z);
        Vec3 target = length < 0.01
                ? anchor
                : actor.position().add(away.x / length * RUN_TO, 0, away.z / length * RUN_TO);

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

    /** Who is chasing right now: they swap over, and both clients agree when. */
    private boolean chasing(PetActor actor) {
        long from = gameStart(actor);
        RandomSource dice = pairDiceAt(actor, playmate, from);
        dice.nextFloat();                                   // the roll that started it
        boolean firstChasesFirst = dice.nextBoolean();
        boolean iAmFirst = actor.ownerId().compareTo(playmate) <= 0;
        boolean swapped = ((actor.worldTime() - from) / SWAP_TICKS) % 2 == 1;
        return (iAmFirst == firstChasesFirst) != swapped;
    }

    /** The stretch of world time this game belongs to. */
    private static long gameStart(PetActor actor) {
        return (actor.worldTime() / GAME_TICKS) * GAME_TICKS;
    }

    private static RandomSource gameDice(PetActor actor, UUID other) {
        return pairDiceAt(actor, other, gameStart(actor));
    }

    /**
     * Dice both pets in a pair can roll and get the same number from, because
     * the seed is the two owners and a moment in world time — nothing either
     * client made up for itself.
     */
    private static RandomSource pairDiceAt(PetActor actor, UUID other, long at) {
        UUID mine = actor.ownerId();
        UUID first = mine.compareTo(other) <= 0 ? mine : other;
        UUID second = first.equals(mine) ? other : mine;
        long seed = first.getMostSignificantBits() * 31 + first.getLeastSignificantBits();
        seed = seed * 31 + second.getMostSignificantBits();
        seed = seed * 31 + second.getLeastSignificantBits();
        return RandomSource.create(seed ^ (at * 0x9E3779B97F4A7C15L));
    }

    private static float randomBetween(RandomSource dice, float min, float max) {
        return min + dice.nextFloat() * (max - min);
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
