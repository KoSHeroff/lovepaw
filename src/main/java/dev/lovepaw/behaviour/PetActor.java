package dev.lovepaw.behaviour;

import dev.lovepaw.pet.PetBehaviourSettings;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What a behaviour may know and do. The pet itself handles gravity, collision
 * and animation; a behaviour only decides where it wants to be and where it
 * wants to look.
 *
 * <p>This is the seam the add-on API will be built on: a third-party behaviour
 * gets this view and nothing else, so it cannot reach into rendering or
 * networking.
 */
public interface PetActor {
    Vec3 position();

    Vec3 ownerPosition();

    /**
     * How fast the owner is moving, in blocks per tick, smoothed over the last
     * few ticks. This is what lets a pet aim where its owner is going instead
     * of where they have been.
     */
    Vec3 ownerVelocity();

    /** Which way the owner is facing, in degrees. */
    float ownerYaw();

    double distanceToOwner();

    boolean onGround();

    boolean inWater();

    Level level();

    /**
     * Randomness every client simulating this pet agrees on: it is seeded from
     * who owns the pet and where the world clock has got to, so two players
     * watching the same pet see it make the same choice rather than one
     * watching it sit on a chest while the other watches it sniff a flower.
     */
    RandomSource random();

    /** The world clock, which every client has the same reading of. */
    long worldTime();

    PetBehaviourSettings settings();

    /** Length of one tick in seconds, for timers. */
    float deltaSeconds();

    /**
     * True when the pet has been trying to move and getting nowhere, which
     * means it is wedged against geometry and should give up.
     */
    boolean isStuck();

    /** Heads towards a point this tick. Vertical movement is ignored. */
    void walkTowards(Vec3 target, float speed);

    /** Stays put this tick. */
    void stand();

    /** Turns to face a point. */
    void face(Vec3 point);

    /** Turns to face whichever way it is moving. */
    void faceMotion();

    void teleportToOwner();

    /** Sitting pets play the sit animation and do not wander. */
    void setSitting(boolean sitting);

    /**
     * Whether the pet actually is sitting. Asking rather than remembering: a
     * pet may refuse to sit because its pack ships no sit animation, and it
     * stands back up on its own when it is teleported.
     */
    boolean isSitting();

    /**
     * A spot near {@code near} the pet could actually stand on, or null if
     * there is none — used to avoid wandering into walls or off ledges.
     */
    Vec3 findStandingSpot(Vec3 near);

    /**
     * Something within {@code radius} of {@code near} worth going to look at —
     * a bed, a sign, a painting, somebody else's chicken — or null when
     * everything around is ordinary.
     */
    Vec3 findSomethingInteresting(Vec3 near, double radius);
}
