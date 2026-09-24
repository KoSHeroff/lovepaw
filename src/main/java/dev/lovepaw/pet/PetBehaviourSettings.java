package dev.lovepaw.pet;

/**
 * The numbers the follow behaviour runs on. All distances are in blocks and all
 * speeds in blocks per tick.
 *
 * @param type             behaviour id; only "follow" exists so far, but the
 *                         field is read and kept so a pack written today still
 *                         parses once more behaviours exist
 * @param anchorRadius     how far the owner may stray from the pet's patch of
 *                         ground before the pet moves its patch
 * @param stopDistance     counts as arrived once closer than this
 * @param teleportDistance jumps to the owner beyond this
 * @param walkSpeed        speed while catching up normally
 * @param runSpeed         speed while far behind
 * @param runDistance      switches to run speed beyond this
 * @param gravity          downward acceleration per tick
 * @param stepHeight       height of a ledge the pet walks up instead of into
 * @param jumpPower        upward speed of a hop over something too tall to step
 *                         onto; 0 for a pet that never jumps
 * @param width            collision box width
 * @param height           collision box height
 * @param canSwim          float on water instead of sinking
 * @param hover            ignore gravity and hold a height above ground
 * @param hoverHeight      height a hovering pet holds above the ground under it
 * @param hoverDrift       how far above and below that it wanders of its own accord
 * @param wander           potter about on its own once it has caught up
 * @param wanderRadius     size of the patch it pothers about in
 * @param wanderSpeed      speed while wandering, usually slower than walking
 * @param sitChance        odds of sitting down instead of wandering, 0 to 1
 * @param curiosity        odds of going to look at something nearby rather than
 *                         wandering to a spot it picked itself, 0 to 1
 * @param interestRadius   how far it notices things worth a look
 * @param playfulness      odds of starting a game of chase with another pet
 *                         that comes near, 0 to 1
 * @param predictionSeconds how far ahead of a moving owner it aims
 */
public record PetBehaviourSettings(
        String type,
        float anchorRadius,
        float stopDistance,
        float teleportDistance,
        float walkSpeed,
        float runSpeed,
        float runDistance,
        float gravity,
        float stepHeight,
        float jumpPower,
        float width,
        float height,
        boolean canSwim,
        boolean hover,
        float hoverHeight,
        float hoverDrift,
        boolean wander,
        float wanderRadius,
        float wanderSpeed,
        float sitChance,
        float curiosity,
        float interestRadius,
        float playfulness,
        float predictionSeconds
) {
    public static final PetBehaviourSettings DEFAULT = new PetBehaviourSettings(
            "follow",
            8f,
            1.4f,
            16f,
            0.16f,
            0.30f,
            5f,
            0.08f,
            1.0f,
            0.42f,
            0.6f,
            0.6f,
            true,
            false,
            0f,
            1.0f,
            true,
            4.5f,
            0.10f,
            0.35f,
            0.4f,
            10f,
            0.5f,
            1.2f);
}
