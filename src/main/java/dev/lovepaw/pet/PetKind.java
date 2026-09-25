package dev.lovepaw.pet;

import java.util.Locale;

/**
 * What kind of animal a pet is, and with it everything about how it moves.
 *
 * <p>The numbers are the game's own. A pet that walks behaves like a tamed cat
 * or wolf: it gets on with its own business, comes when you get too far ahead,
 * and appears beside you when following stops being possible. A pet that flies
 * behaves like an allay carrying something you gave it: it keeps a looser
 * distance, holds its own height and goes over what a walking pet goes around.
 *
 * <p>Those are animals players have watched for years, so a pet that moves like
 * them needs no explaining. None of it is a knob: a pack author draws an animal
 * and says which of the two it is, and that is the whole of it. Numbers in a
 * pet.json only ever produced pets that moved wrongly in ways nobody could
 * name.
 */
public enum PetKind {
    /**
     * A tamed cat or wolf.
     *
     * <p>Vanilla's {@code FollowOwnerGoal} starts following at 10 blocks and
     * teleports past 12. The patch it keeps to is smaller than that on purpose:
     * wandering has to fit inside the distance at which it gives up and
     * teleports, or a pet minding its own business would blink about instead of
     * walking back.
     */
    GROUND("ground",
            new Body(0.6f, 0.7f, 0.08f, 1.0f, 0.42f, true, false, 0f, 0f),
            new Following(7f, 2f, 5f, 12f, 1.2f),
            new Speeds(0.16f, 0.30f, 0.10f),
            new Pastimes(4f, 0.35f, 0.4f, 10f, 0.7f)),

    /**
     * An allay holding something you handed it.
     *
     * <p>An allay stays within 16 blocks of the player it likes and counts 4 as
     * close enough — further out and less fussy than anything on the ground,
     * which is what flying next to somebody looks like. It never teleports
     * because it can fly over what stops a cat; this one still does, past 16,
     * since nothing here can path through a wall the way an allay can.
     */
    FLYING("flying",
            new Body(0.35f, 0.6f, 0.08f, 0f, 0f, false, true, 1.8f, 1.2f),
            new Following(10f, 4f, 6f, 16f, 1.2f),
            new Speeds(0.18f, 0.32f, 0.12f),
            new Pastimes(5f, 0f, 0.4f, 12f, 0.7f));

    /**
     * How big it is and what the world does to it.
     *
     * @param width       collision box width at scale 1
     * @param height      collision box height at scale 1
     * @param gravity     downward acceleration per tick
     * @param stepHeight  height of a ledge it walks up instead of into
     * @param jumpPower   upward speed of a hop over something too tall to step
     *                    onto; 0 for an animal that never jumps
     * @param canSwim     float on water instead of sinking
     * @param hover       hold a height above the ground instead of falling
     * @param hoverHeight how far above the ground under it a flyer sits
     * @param hoverDrift  how far above and below that it wanders of its own accord
     */
    public record Body(float width, float height, float gravity, float stepHeight, float jumpPower,
                       boolean canSwim, boolean hover, float hoverHeight, float hoverDrift) {
    }

    /**
     * How it keeps up with its owner.
     *
     * @param radius            how far the owner may stray from the pet's patch
     *                          of ground before the pet moves its patch
     * @param stopDistance      counts as arrived once closer than this
     * @param runDistance       switches to running beyond this
     * @param teleportDistance  gives up and appears beside the owner beyond this
     * @param predictionSeconds how far ahead of a moving owner it aims
     */
    public record Following(float radius, float stopDistance, float runDistance,
                            float teleportDistance, float predictionSeconds) {
    }

    /** Blocks per tick: a walking player is about 0.21, a sprinting one 0.28. */
    public record Speeds(float walk, float run, float stroll) {
    }

    /**
     * What it does when it has nothing to do.
     *
     * @param strollRadius   size of the patch it potters about in
     * @param sitChance      odds of sitting down instead of strolling, 0 to 1
     * @param curiosity      odds of going to look at something nearby instead
     * @param interestRadius how far it notices things worth a look
     * @param playfulness    odds of being up for a game of chase in any given
     *                       stretch of world time
     */
    public record Pastimes(float strollRadius, float sitChance, float curiosity,
                           float interestRadius, float playfulness) {
    }

    /** What a pet that behaved like this was called before kinds existed. */
    private static final String LEGACY_TYPE = "follow";

    private final String key;
    private final Body body;
    private final Following following;
    private final Speeds speeds;
    private final Pastimes pastimes;

    PetKind(String key, Body body, Following following, Speeds speeds, Pastimes pastimes) {
        this.key = key;
        this.body = body;
        this.following = following;
        this.speeds = speeds;
        this.pastimes = pastimes;
    }

    /** The kind a {@code pet.json} named, or null if it named something else. */
    public static PetKind byName(String type) {
        if (type == null) {
            return null;
        }
        String cleaned = type.trim().toLowerCase(Locale.ROOT);
        for (PetKind kind : values()) {
            if (kind.key.equals(cleaned)) {
                return kind;
            }
        }
        return null;
    }

    /** True for the name every pet used when there was only one behaviour. */
    public static boolean isLegacyType(String type) {
        return type == null || type.isBlank() || LEGACY_TYPE.equals(type.trim().toLowerCase(Locale.ROOT));
    }

    public String key() {
        return key;
    }

    /** The collision box this pet has, once its drawn size is taken into account. */
    public float width(float scale) {
        return body.width() * scale;
    }

    public float height(float scale) {
        return body.height() * scale;
    }

    public float gravity() {
        return body.gravity();
    }

    public float stepHeight() {
        return body.stepHeight();
    }

    public float jumpPower() {
        return body.jumpPower();
    }

    public boolean canSwim() {
        return body.canSwim();
    }

    public boolean hover() {
        return body.hover();
    }

    public float hoverHeight() {
        return body.hoverHeight();
    }

    public float hoverDrift() {
        return body.hoverDrift();
    }

    public float followRadius() {
        return following.radius();
    }

    public float stopDistance() {
        return following.stopDistance();
    }

    public float runDistance() {
        return following.runDistance();
    }

    public float teleportDistance() {
        return following.teleportDistance();
    }

    public float predictionSeconds() {
        return following.predictionSeconds();
    }

    public float walkSpeed() {
        return speeds.walk();
    }

    public float runSpeed() {
        return speeds.run();
    }

    public float strollSpeed() {
        return speeds.stroll();
    }

    public float strollRadius() {
        return pastimes.strollRadius();
    }

    public float sitChance() {
        return pastimes.sitChance();
    }

    public float curiosity() {
        return pastimes.curiosity();
    }

    public float interestRadius() {
        return pastimes.interestRadius();
    }

    public float playfulness() {
        return pastimes.playfulness();
    }
}
