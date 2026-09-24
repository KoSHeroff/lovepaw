package dev.lovepaw.behaviour;

import dev.lovepaw.pet.PetBehaviourSettings;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowBehaviourTest {
    private static final float TICK = 1f / 20f;

    private static void simulate(FollowBehaviour behaviour, TestActor actor, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            behaviour.tick(actor);
            if (actor.motion.length() > 1.0E-6) {
                actor.position = actor.position.add(actor.motion);
            }
        }
    }

    @Test
    void ignoresAnOwnerWhoStaysInThePatch() {
        TestActor actor = new TestActor();
        actor.position = new Vec3(2, 0, 0);
        FollowBehaviour behaviour = new FollowBehaviour();

        behaviour.tick(actor);
        actor.ownerPosition = new Vec3(4, 0, 3);

        simulate(behaviour, actor, 400);

        double fromAnchor = actor.position.distanceTo(Vec3.ZERO);
        assertTrue(fromAnchor < PetBehaviourSettings.DEFAULT.wanderRadius() + 2,
                "should potter about its own patch, not follow the owner around; was " + fromAnchor);
        assertFalse(actor.teleported);
    }

    @Test
    void wandersAndSitsWhenLeftToItself() {
        TestActor actor = new TestActor();
        actor.position = new Vec3(0.5, 0, 0);
        FollowBehaviour behaviour = new FollowBehaviour();

        boolean moved = false;
        boolean sat = false;
        for (int tick = 0; tick < 600; tick++) {
            behaviour.tick(actor);
            if (actor.motion.length() > 1.0E-6) {
                moved = true;
                actor.position = actor.position.add(actor.motion);
            }
            sat |= actor.sitting;
        }

        assertTrue(moved, "a pet with nothing to do should wander about on its own");
        assertTrue(sat, "and should sit down now and then");
    }

    @Test
    void movesHouseOnceTheOwnerLeavesTheRadius() {
        TestActor actor = new TestActor();
        actor.position = new Vec3(1, 0, 0);
        FollowBehaviour behaviour = new FollowBehaviour();

        behaviour.tick(actor);
        actor.ownerPosition = new Vec3(12, 0, 0);
        behaviour.tick(actor);

        assertTrue(actor.motion.x > 0, "should head after an owner who has left the area");
        assertTrue(actor.lookAlongMotion, "and face the way it is going");
    }

    @Test
    void aimsWhereTheOwnerIsGoingRatherThanWhereTheyAre() {
        TestActor actor = new TestActor();
        actor.position = new Vec3(0, 0, 0);
        FollowBehaviour behaviour = new FollowBehaviour();

        behaviour.tick(actor);
        actor.ownerPosition = new Vec3(12, 0, 0);
        actor.ownerVelocity = new Vec3(0.25, 0, 0);

        simulate(behaviour, actor, 300);

        assertTrue(actor.position.x > actor.ownerPosition.x + 1,
                "should end up ahead of a running owner, not behind them; was " + actor.position.x);
    }

    @Test
    void givesUpAndTeleportsWhenHopelesslyFarAway() {
        TestActor actor = new TestActor();
        actor.position = new Vec3(100, 0, 0);

        new FollowBehaviour().tick(actor);

        assertTrue(actor.teleported);
    }

    @Test
    void teleportsRatherThanGrindingAgainstAWall() {
        TestActor actor = new TestActor();
        actor.position = new Vec3(1, 0, 0);
        FollowBehaviour behaviour = new FollowBehaviour();

        behaviour.tick(actor);
        actor.ownerPosition = new Vec3(12, 0, 0);
        actor.stuck = true;
        behaviour.tick(actor);

        assertTrue(actor.teleported);
    }

    @Test
    void staysPutWhenWanderingIsTurnedOff() {
        TestActor actor = new TestActor();
        actor.position = new Vec3(0.5, 0, 0);
        actor.settings = withWander(false);

        simulate(new FollowBehaviour(), actor, 600);

        assertTrue(actor.position.distanceTo(Vec3.ZERO) < 1.5, "should not have gone anywhere");
    }

    @Test
    void watchesTheOwnerInGlancesRatherThanTrackingThem() {
        TestActor actor = new TestActor();
        actor.position = new Vec3(1, 0, 0);
        actor.settings = withWander(false);
        FollowBehaviour behaviour = new FollowBehaviour();

        simulate(behaviour, actor, 5);
        int looksBefore = actor.looks;
        Vec3 held = actor.lookTarget;

        for (int tick = 0; tick < 20; tick++) {
            double angle = tick * 0.3;
            actor.ownerPosition = new Vec3(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
            behaviour.tick(actor);
        }

        assertEquals(looksBefore, actor.looks,
                "a resting pet should hold one look, not re-aim at the owner every tick");
        assertEquals(held, actor.lookTarget, "and should keep looking where it was looking");
    }

    @Test
    void glancesPastTheOwnerRatherThanStraightAtThem() {
        TestActor actor = new TestActor();
        actor.position = new Vec3(1, 0, 0);
        actor.ownerPosition = new Vec3(0, 0, 0);
        actor.settings = withWander(false);
        FollowBehaviour behaviour = new FollowBehaviour();

        boolean lookedSomewhereElse = false;
        for (int tick = 0; tick < 600 && !lookedSomewhereElse; tick++) {
            behaviour.tick(actor);
            lookedSomewhereElse = actor.lookTarget != null
                    && actor.lookTarget.distanceTo(actor.ownerPosition) > 1.0E-6;
        }

        assertTrue(lookedSomewhereElse, "should look off to one side, not dead at the owner");
    }

    @Test
    void doesNotWaitOutASitItCannotDo() {
        TestActor canSit = new TestActor();
        canSit.settings = withWander(false);
        simulate(new FollowBehaviour(), canSit, 600);

        TestActor cannotSit = new TestActor();
        cannotSit.settings = withWander(false);
        cannotSit.canSit = false;
        simulate(new FollowBehaviour(), cannotSit, 600);

        assertTrue(canSit.sitAttempts <= 6,
                "a sitting pet settles in for a while; tried " + canSit.sitAttempts + " times");
        assertTrue(cannotSit.sitAttempts >= 7,
                "a pet that cannot sit should not be paying for one; tried "
                        + cannotSit.sitAttempts + " times");
    }

    private static PetBehaviourSettings withWander(boolean wander) {
        PetBehaviourSettings base = PetBehaviourSettings.DEFAULT;
        return new PetBehaviourSettings(
                base.type(), base.anchorRadius(), base.stopDistance(), base.teleportDistance(),
                base.walkSpeed(), base.runSpeed(), base.runDistance(), base.gravity(), base.stepHeight(),
                base.jumpPower(),
                base.width(), base.height(), base.canSwim(), base.hover(), base.hoverHeight(),
                wander, base.wanderRadius(), base.wanderSpeed(), base.sitChance(), base.predictionSeconds());
    }

    private static final class TestActor implements PetActor {
        private final RandomSource random = RandomSource.create(1234L);

        PetBehaviourSettings settings = PetBehaviourSettings.DEFAULT;
        Vec3 position = Vec3.ZERO;
        Vec3 ownerPosition = Vec3.ZERO;
        Vec3 ownerVelocity = Vec3.ZERO;
        Vec3 motion = Vec3.ZERO;
        boolean lookAlongMotion;
        Vec3 lookTarget;
        int looks;
        boolean teleported;
        boolean sitting;
        boolean canSit = true;
        int sitAttempts;
        boolean stuck;

        @Override
        public Vec3 position() {
            return position;
        }

        @Override
        public Vec3 ownerPosition() {
            return ownerPosition;
        }

        @Override
        public Vec3 ownerVelocity() {
            return ownerVelocity;
        }

        @Override
        public float ownerYaw() {
            return 0;
        }

        @Override
        public double distanceToOwner() {
            return position.distanceTo(ownerPosition);
        }

        @Override
        public boolean onGround() {
            return true;
        }

        @Override
        public boolean inWater() {
            return false;
        }

        @Override
        public Level level() {
            return null;
        }

        @Override
        public RandomSource random() {
            return random;
        }

        @Override
        public PetBehaviourSettings settings() {
            return settings;
        }

        @Override
        public float deltaSeconds() {
            return TICK;
        }

        @Override
        public boolean isStuck() {
            return stuck;
        }

        @Override
        public void walkTowards(Vec3 target, float speed) {
            Vec3 delta = new Vec3(target.x - position.x, 0, target.z - position.z);
            double length = delta.length();
            motion = length < 1.0E-6 ? Vec3.ZERO : delta.scale(Math.min(speed, length) / length);
        }

        @Override
        public void stand() {
            motion = Vec3.ZERO;
        }

        @Override
        public void face(Vec3 point) {
            lookAlongMotion = false;
            lookTarget = point;
            looks++;
        }

        @Override
        public void faceMotion() {
            lookAlongMotion = true;
        }

        @Override
        public void teleportToOwner() {
            teleported = true;
            position = ownerPosition;
        }

        @Override
        public void setSitting(boolean value) {
            if (value) {
                sitAttempts++;
            }
            sitting = value && canSit;
        }

        @Override
        public boolean isSitting() {
            return sitting;
        }

        @Override
        public Vec3 findStandingSpot(Vec3 near) {
            return new Vec3(near.x, 0, near.z);
        }
    }
}
