package dev.lovepaw.behaviour;

import dev.lovepaw.pet.PetBehaviourSettings;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The default behaviour: a pet lives in a patch of ground, not on a leash.
 *
 * <p>The patch has an anchor — normally wherever the owner last settled. While
 * the owner stays within {@code anchor_radius} of it the pet ignores them
 * entirely and gets on with its own life: wandering to spots it picks itself,
 * looking around, sitting down.
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
        STUDY
    }

    private Mode mode = Mode.REST;
    private Vec3 anchor;
    private Vec3 travelTarget;
    private Vec3 travelAnchor;
    private Vec3 wanderTarget;
    private float modeSeconds;
    private float glanceSeconds;
    private Vec3 glanceTarget;
    private Vec3 interest;
    private final Deque<Vec3> alreadySeen = new ArrayDeque<>();

    @Override
    public void tick(PetActor actor) {
        PetBehaviourSettings settings = actor.settings();

        if (anchor == null) {
            anchor = actor.ownerPosition();
        }

        if (actor.distanceToOwner() > settings.teleportDistance()) {
            actor.teleportToOwner();
            anchor = actor.ownerPosition();
            restFor(actor, REST_MIN_SECONDS);
            return;
        }

        boolean ownerLeftThePatch = horizontalDistance(actor.ownerPosition(), anchor) > settings.anchorRadius();
        if (ownerLeftThePatch || mode == Mode.RELOCATE) {
            aimAtOwner(actor, settings);
        }

        switch (mode) {
            case RELOCATE -> relocate(actor, settings);
            case WANDER -> wander(actor, settings);
            case INSPECT -> inspect(actor, settings);
            case STUDY -> study(actor);
            case REST -> rest(actor, settings);
        }
    }

    private void aimAtOwner(PetActor actor, PetBehaviourSettings settings) {
        actor.setSitting(false);
        wanderTarget = null;
        anchor = predictOwner(actor, settings);

        boolean needsTarget = mode != Mode.RELOCATE
                || travelTarget == null
                || travelAnchor == null
                || horizontalDistance(travelAnchor, anchor) > RETARGET_DISTANCE;

        if (needsTarget) {
            Vec3 target = pickSpotAround(actor, anchor, settings.wanderRadius() * 0.5);
            travelTarget = target != null ? target : anchor;
            travelAnchor = anchor;
        }

        mode = Mode.RELOCATE;
    }

    private Vec3 predictOwner(PetActor actor, PetBehaviourSettings settings) {
        Vec3 owner = actor.ownerPosition();
        Vec3 velocity = actor.ownerVelocity();

        double lead = settings.predictionSeconds() * 20;
        Vec3 ahead = new Vec3(velocity.x * lead, 0, velocity.z * lead);
        double length = ahead.length();
        if (length > MAX_LEAD) {
            ahead = ahead.scale(MAX_LEAD / length);
        }

        Vec3 predicted = owner.add(ahead);
        Vec3 standing = actor.findStandingSpot(predicted);
        return standing != null ? standing : owner;
    }

    private void relocate(PetActor actor, PetBehaviourSettings settings) {
        double distance = horizontalDistance(actor.position(), travelTarget);
        if (distance <= Math.max(ARRIVED, settings.stopDistance())) {
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
            return;
        }

        float speed = distance > settings.runDistance() ? settings.runSpeed() : settings.walkSpeed();
        actor.walkTowards(travelTarget, speed);
        actor.faceMotion();

        if (actor.isStuck()) {
            actor.teleportToOwner();
            anchor = actor.ownerPosition();
            restFor(actor, REST_MIN_SECONDS);
        }
    }

    private void rest(PetActor actor, PetBehaviourSettings settings) {
        actor.stand();
        modeSeconds -= actor.deltaSeconds();

        glanceSeconds -= actor.deltaSeconds();
        if (glanceSeconds <= 0) {
            glanceSeconds = randomBetween(actor, GLANCE_MIN_SECONDS, GLANCE_MAX_SECONDS);
            glanceTarget = actor.random().nextFloat() < 0.5f ? ownerGlance(actor) : randomGlance(actor);
            actor.face(glanceTarget);
        }

        if (modeSeconds > 0) {
            return;
        }

        if (actor.isSitting()) {
            actor.setSitting(false);
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
            return;
        }

        if (settings.wander() && actor.random().nextFloat() < settings.curiosity()
                && goAndLook(actor, settings)) {
            return;
        }

        if (settings.wander() && actor.random().nextFloat() > settings.sitChance()) {
            Vec3 target = pickSpotAround(actor, anchor, settings.wanderRadius());
            if (target != null && horizontalDistance(actor.position(), target) > ARRIVED) {
                mode = Mode.WANDER;
                wanderTarget = target;
                modeSeconds = WANDER_TIMEOUT_SECONDS;
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
    private boolean goAndLook(PetActor actor, PetBehaviourSettings settings) {
        Vec3 thing = actor.findSomethingInteresting(anchor, settings.interestRadius());
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
        modeSeconds = REACH_IT_SECONDS;
        return true;
    }

    private void inspect(PetActor actor, PetBehaviourSettings settings) {
        modeSeconds -= actor.deltaSeconds();
        double distance = horizontalDistance(actor.position(), interest);

        if (distance <= CLOSE_ENOUGH_TO_LOOK) {
            remember(interest);
            mode = Mode.STUDY;
            modeSeconds = randomBetween(actor, STUDY_MIN_SECONDS, STUDY_MAX_SECONDS);
            actor.stand();
            if (actor.onGround() && !actor.inWater() && actor.random().nextFloat() < SIT_AND_STARE) {
                actor.setSitting(true);
            }
            return;
        }

        if (modeSeconds <= 0 || actor.isStuck()) {
            remember(interest);
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
            return;
        }

        actor.walkTowards(interest, settings.wanderSpeed());
        actor.faceMotion();
    }

    private void study(PetActor actor) {
        actor.stand();
        actor.face(interest);
        modeSeconds -= actor.deltaSeconds();
        if (modeSeconds <= 0) {
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

    private void wander(PetActor actor, PetBehaviourSettings settings) {
        modeSeconds -= actor.deltaSeconds();

        boolean arrived = wanderTarget == null || horizontalDistance(actor.position(), wanderTarget) < ARRIVED;
        if (arrived || modeSeconds <= 0 || actor.isStuck()) {
            wanderTarget = null;
            restFor(actor, randomBetween(actor, REST_MIN_SECONDS, REST_MAX_SECONDS));
            return;
        }

        actor.walkTowards(wanderTarget, settings.wanderSpeed());
        actor.faceMotion();
    }

    private void restFor(PetActor actor, float seconds) {
        mode = Mode.REST;
        modeSeconds = seconds;
        wanderTarget = null;
        glanceSeconds = 0;
        actor.stand();
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
