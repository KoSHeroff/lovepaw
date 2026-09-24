package dev.lovepaw.client;

import dev.lovepaw.behaviour.PetActor;
import dev.lovepaw.behaviour.PetBehaviour;
import dev.lovepaw.behaviour.PetBehaviours;
import dev.lovepaw.config.ClientConfig;
import dev.lovepaw.config.PetOverrides;
import dev.lovepaw.model.anim.AnimationPlayer;
import dev.lovepaw.model.anim.BonePose;
import dev.lovepaw.model.anim.molang.MolangContext;
import dev.lovepaw.pet.PetAnimationState;
import dev.lovepaw.pet.PetBehaviourSettings;
import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetRenderSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One pet walking beside one player, simulated entirely on this client.
 *
 * <p>Nothing here exists on the server: every client runs the same behaviour
 * for every pet it can see, which is why a pet works on a vanilla server and
 * why a server that does have the mod only has to say <em>who owns which
 * pet</em> rather than stream positions.
 *
 * <p>This class owns movement, collision and animation. What the pet wants to
 * do is decided by its {@link PetBehaviour}, which talks to it through
 * {@link PetActor}.
 */
public final class PetInstance implements PetActor {
    private static final float TICK_SECONDS = 1f / 20f;
    private static final double TERMINAL_VELOCITY = -1.2;
    private static final float TRANSITION_SECONDS = 0.2f;
    private static final double MOVING_THRESHOLD = 0.01;
    private static final int STUCK_TICKS = 20;
    private static final int JUMP_AFTER_TICKS = 2;
    private static final int PATH_AFTER_TICKS = 4;
    /** Ticks a pet's dice are shared for; decisions land on this grid. */
    private static final long DECISION_SLOT = 10;

    private final UUID ownerId;
    private final PetDefinition definition;
    private final PetAssets assets;
    private final PetBehaviour behaviour;
    private final AnimationPlayer animation;
    private final PetNavigation navigation = new PetNavigation();
    private final PetHover hover = new PetHover();
    private final MolangContext molang = new MolangContext();

    private final boolean local;
    private PetBehaviourSettings effectiveBehaviour;
    private PetRenderSettings effectiveRender;
    private int settingsRevision = -1;

    private Vec3 position = Vec3.ZERO;
    private Vec3 previousPosition = Vec3.ZERO;
    private float yaw;
    private float previousYaw;
    private double verticalMotion;
    private boolean onGround;
    private boolean inWater;
    private boolean placed;

    private double lastTickSpeed;
    private float lifeSeconds;
    private PetAnimationState state = PetAnimationState.IDLE;

    private Vec3 ownerVelocity = Vec3.ZERO;
    private Vec3 previousOwnerPosition;

    private Vec3 desiredMotion = Vec3.ZERO;
    private Vec3 lookTarget;
    private boolean lookAlongMotion;
    private boolean teleportRequested;
    private boolean sitting;
    private int stuckTicks;
    private long decisionSlot = Long.MIN_VALUE;
    private RandomSource decisions;

    private Player owner;

    public PetInstance(UUID ownerId, PetDefinition definition, PetAssets assets, boolean local) {
        this.ownerId = ownerId;
        this.definition = definition;
        this.assets = assets;
        this.local = local;
        this.behaviour = PetBehaviours.create(definition.behaviour().type());
        this.animation = new AnimationPlayer(assets.animations());
        this.effectiveBehaviour = definition.behaviour();
        this.effectiveRender = definition.render();
        refreshSettings();
    }

    private void refreshSettings() {
        if (!local) {
            return;
        }
        int revision = ClientConfig.revision();
        if (revision == settingsRevision) {
            return;
        }
        settingsRevision = revision;
        PetOverrides overrides = ClientConfig.overrides();
        effectiveBehaviour = overrides.applyTo(definition.behaviour());
        effectiveRender = overrides.applyTo(definition.render());
    }

    /** How this pet is drawn, with the player's tweaks applied. */
    public PetRenderSettings renderSettings() {
        return effectiveRender;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public PetDefinition definition() {
        return definition;
    }

    public PetAssets assets() {
        return assets;
    }

    public PetAnimationState state() {
        return state;
    }

    /** Physics and behaviour, once per client tick. */
    public void tick(Player petOwner) {
        this.owner = petOwner;
        refreshSettings();
        PetBehaviourSettings config = effectiveBehaviour;
        Level level = petOwner.level();

        previousPosition = position;
        previousYaw = yaw;
        lifeSeconds += TICK_SECONDS;
        updateOwnerVelocity(petOwner);

        if (!placed) {
            teleportToOwner();
            placed = true;
        }
        if (position.y < level.getMinBuildHeight() - 8 || wedged(level, config)) {
            teleportToOwner();
        }

        inWater = inWater(level, config);

        desiredMotion = Vec3.ZERO;
        lookTarget = null;
        lookAlongMotion = false;
        behaviour.tick(this);

        if (teleportRequested) {
            teleportRequested = false;
            applyTeleport(config);
            desiredMotion = Vec3.ZERO;
            inWater = inWater(level, config);
        }

        Vec3 wanted = desiredMotion;
        if (inWater && config.canSwim()) {
            verticalMotion = Math.min(0.08, verticalMotion + config.gravity() * 1.6);
            wanted = wanted.scale(0.6);
        } else if (config.hover()) {
            verticalMotion = hover.climb(
                    PetPhysics.Space.of(level),
                    position,
                    config.width(),
                    config.height(),
                    config.hoverHeight(),
                    config.hoverDrift(),
                    ownerPosition().y,
                    stuckTicks >= JUMP_AFTER_TICKS,
                    level.random);
        } else {
            verticalMotion = Math.max(TERMINAL_VELOCITY, verticalMotion - config.gravity());
        }

        AABB box = PetPhysics.boxAt(position, config.width(), config.height());
        Vec3 moved = PetPhysics.move(
                PetPhysics.Space.of(level),
                box,
                new Vec3(wanted.x, verticalMotion, wanted.z),
                config.hover() ? 0 : config.stepHeight());

        position = position.add(moved);
        boolean landed = verticalMotion < 0 && moved.y > verticalMotion + 1.0E-4;
        onGround = !config.hover()
                && (landed || PetPhysics.onGround(PetPhysics.Space.of(level),
                        PetPhysics.boxAt(position, config.width(), config.height())));
        if (onGround && verticalMotion < 0) {
            verticalMotion = 0;
        }

        lastTickSpeed = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
        updateStuck(wanted);
        maybeJump(config);
        updateYaw(moved);
        updateState(config);

        this.owner = null;
    }

    private void updateOwnerVelocity(Player petOwner) {
        Vec3 now = petOwner.position();
        if (previousOwnerPosition != null) {
            Vec3 delta = now.subtract(previousOwnerPosition);
            ownerVelocity = ownerVelocity.scale(0.6).add(delta.scale(0.4));
        }
        previousOwnerPosition = now;
    }

    private void updateStuck(Vec3 wanted) {
        double wantedSpeed = Math.sqrt(wanted.x * wanted.x + wanted.z * wanted.z);
        if (wantedSpeed > MOVING_THRESHOLD && lastTickSpeed < wantedSpeed * 0.25) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
    }

    private void maybeJump(PetBehaviourSettings config) {
        if (!wantsJump(config, onGround, inWater, stuckTicks)) {
            return;
        }
        verticalMotion = config.jumpPower();
        onGround = false;
    }

    static boolean wantsJump(PetBehaviourSettings config, boolean onGround, boolean inWater, int blockedTicks) {
        if (config.jumpPower() <= 0 || config.hover() || !onGround || inWater) {
            return false;
        }
        return blockedTicks >= JUMP_AFTER_TICKS;
    }

    private boolean wedged(Level level, PetBehaviourSettings config) {
        if (config.hover()) {
            return false;
        }
        return PetPhysics.wedged(PetPhysics.Space.of(level),
                PetPhysics.boxAt(position, config.width(), config.height()));
    }

    private boolean inWater(Level level, PetBehaviourSettings config) {
        BlockPos pos = BlockPos.containing(position.x, position.y + config.height() * 0.5, position.z);
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    private void updateYaw(Vec3 moved) {
        double dx;
        double dz;
        if (lookAlongMotion && lastTickSpeed > MOVING_THRESHOLD) {
            dx = moved.x;
            dz = moved.z;
        } else if (lookTarget != null) {
            dx = lookTarget.x - position.x;
            dz = lookTarget.z - position.z;
        } else {
            return;
        }
        if (Math.abs(dx) < 1.0E-5 && Math.abs(dz) < 1.0E-5) {
            return;
        }

        float target = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        yaw = yaw + Mth.wrapDegrees(target - yaw) * 0.25f;
    }

    private void updateState(PetBehaviourSettings config) {
        state = stateFor(config, inWater, onGround, verticalMotion, lastTickSpeed, sitting);
        animation.play(resolveAnimation(state), TRANSITION_SECONDS);
    }

    static PetAnimationState stateFor(PetBehaviourSettings config,
                                      boolean inWater,
                                      boolean onGround,
                                      double verticalMotion,
                                      double speed,
                                      boolean sitting) {
        boolean airborne = !onGround && !config.hover();
        if (inWater && config.canSwim()) {
            return PetAnimationState.SWIM;
        }
        if (airborne && verticalMotion > 0) {
            return PetAnimationState.JUMP;
        }
        if (airborne && verticalMotion < -0.15) {
            return PetAnimationState.FALL;
        }
        if (speed > config.walkSpeed() * 0.9) {
            return PetAnimationState.RUN;
        }
        if (speed > MOVING_THRESHOLD) {
            return PetAnimationState.WALK;
        }
        if (sitting) {
            return PetAnimationState.SIT;
        }
        return PetAnimationState.IDLE;
    }

    private boolean hasAnimation(PetAnimationState candidate) {
        return assets.hasAnimation(definition.animationFor(candidate));
    }

    private String resolveAnimation(PetAnimationState wanted) {
        String name = definition.animationFor(wanted);
        if (assets.hasAnimation(name)) {
            return name;
        }
        if (wanted == PetAnimationState.RUN || wanted == PetAnimationState.SWIM) {
            String walk = definition.animationFor(PetAnimationState.WALK);
            if (assets.hasAnimation(walk)) {
                return walk;
            }
        }
        if (wanted == PetAnimationState.JUMP) {
            String fall = definition.animationFor(PetAnimationState.FALL);
            if (assets.hasAnimation(fall)) {
                return fall;
            }
        }
        String idle = definition.animationFor(PetAnimationState.IDLE);
        return assets.hasAnimation(idle) ? idle : null;
    }

    private void applyTeleport(PetBehaviourSettings config) {
        Vec3 spot = PetPhysics.findTeleportSpot(PetPhysics.Space.of(owner.level()),
                owner.position(), config.width(), config.height(), owner.getYRot());
        position = spot != null ? spot : owner.position();
        previousPosition = position;
        verticalMotion = 0;
        yaw = owner.getYRot();
        previousYaw = yaw;
        lastTickSpeed = 0;
        stuckTicks = 0;
        sitting = false;
        navigation.forget();
        hover.forget();
    }

    /**
     * Advances the animation clock by real elapsed time, so animation stays
     * smooth above 20 fps instead of stepping once per tick.
     */
    public void updateAnimation(float deltaSeconds) {
        molang.setQuery("life_time", lifeSeconds);
        molang.setQuery("ground_speed", (float) (lastTickSpeed * 20));
        molang.setQuery("is_on_ground", onGround ? 1 : 0);
        animation.tick(deltaSeconds);
    }

    public Map<String, BonePose> pose() {
        return animation.pose(molang);
    }

    public Vec3 renderPosition(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, previousPosition.x, position.x),
                Mth.lerp(partialTick, previousPosition.y, position.y),
                Mth.lerp(partialTick, previousPosition.z, position.z));
    }

    public float renderYaw(float partialTick) {
        return previousYaw + Mth.wrapDegrees(yaw - previousYaw) * partialTick;
    }

    @Override
    public Vec3 position() {
        return position;
    }

    @Override
    public Vec3 ownerPosition() {
        return owner != null ? owner.position() : position;
    }

    @Override
    public Vec3 ownerVelocity() {
        return ownerVelocity;
    }

    @Override
    public float ownerYaw() {
        return owner != null ? owner.getYRot() : yaw;
    }

    @Override
    public double distanceToOwner() {
        return owner != null ? position.distanceTo(owner.position()) : 0;
    }

    @Override
    public boolean onGround() {
        return onGround;
    }

    @Override
    public boolean inWater() {
        return inWater;
    }

    @Override
    public Level level() {
        return owner.level();
    }

    @Override
    public RandomSource random() {
        long slot = worldTime() / DECISION_SLOT;
        if (slot != decisionSlot || decisions == null) {
            decisionSlot = slot;
            decisions = RandomSource.create(seedFor(ownerId, slot));
        }
        return decisions;
    }

    @Override
    public List<PetActor.Nearby> petsNearby(double radius) {
        return PetManager.get().petsNear(ownerId, position, radius);
    }

    @Override
    public long worldTime() {
        return owner.level().getGameTime();
    }

    /**
     * The same pet, the same moment, the same number on every client. Without
     * this each client rolled its own dice and the same pet led a different
     * life on each screen.
     */
    private static long seedFor(UUID owner, long slot) {
        long mixed = owner.getMostSignificantBits() * 31 + owner.getLeastSignificantBits();
        return mixed ^ (slot * 0x9E3779B97F4A7C15L);
    }

    @Override
    public PetBehaviourSettings settings() {
        return effectiveBehaviour;
    }

    @Override
    public float deltaSeconds() {
        return TICK_SECONDS;
    }

    @Override
    public boolean isStuck() {
        return stuckTicks > STUCK_TICKS;
    }

    @Override
    public void walkTowards(Vec3 target, float speed) {
        Vec3 heading = steer(target);
        double dx = heading.x - position.x;
        double dz = heading.z - position.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4) {
            desiredMotion = Vec3.ZERO;
            return;
        }

        double step = Math.min(speed, horizontal);
        desiredMotion = new Vec3(dx / horizontal * step, 0, dz / horizontal * step);
    }

    /**
     * Where to actually put the next step. A pet flies or walks straight at
     * what it wants until that plainly is not working, and only then is a route
     * worth the search.
     */
    private Vec3 steer(Vec3 target) {
        PetBehaviourSettings config = effectiveBehaviour;
        if (config.hover()) {
            return target;
        }
        return navigation.steer(
                PetPhysics.Space.of(owner.level()),
                position,
                target,
                new PetPathfinder.Shape(config.width(), config.height(), config.stepHeight()),
                stuckTicks >= PATH_AFTER_TICKS);
    }

    @Override
    public void stand() {
        desiredMotion = Vec3.ZERO;
    }

    @Override
    public void face(Vec3 point) {
        lookTarget = point;
        lookAlongMotion = false;
    }

    @Override
    public void faceMotion() {
        lookAlongMotion = true;
    }

    @Override
    public void teleportToOwner() {
        teleportRequested = true;
    }

    @Override
    public void setSitting(boolean value) {
        this.sitting = value && hasAnimation(PetAnimationState.SIT);
    }

    @Override
    public boolean isSitting() {
        return sitting;
    }

    @Override
    public Vec3 findSomethingInteresting(Vec3 near, double radius) {
        return PetInterest.find(owner.level(), near, radius, random());
    }

    @Override
    public Vec3 findStandingSpot(Vec3 near) {
        PetBehaviourSettings config = effectiveBehaviour;
        return PetPhysics.findStandingSpot(PetPhysics.Space.of(owner.level()),
                near, config.width(), config.height());
    }
}
