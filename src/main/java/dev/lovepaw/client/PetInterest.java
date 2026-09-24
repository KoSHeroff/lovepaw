package dev.lovepaw.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * Finds something nearby worth going to have a look at.
 *
 * <p>A pet that only ever wanders to a random patch of ground is a screensaver.
 * What makes one read as alive is noticing things: the bed it has not seen
 * before, a sign, a painting, somebody else's chicken. This picks one of those,
 * and the behaviour decides whether to go.
 *
 * <p>Only things a player would notice count. Stone and dirt are everywhere and
 * looking at them says nothing; a jukebox in a cave says somebody lives here.
 */
public final class PetInterest {
    /** Blocks sampled per search. A full scan of the radius would cost far more. */
    private static final int SAMPLES = 160;
    /** How much of the time a creature wins over a block, when both are about. */
    private static final float PREFER_CREATURES = 0.55f;

    private static final Set<net.minecraft.world.level.block.Block> CURIOSITIES = Set.of(
            Blocks.JUKEBOX, Blocks.NOTE_BLOCK, Blocks.BELL, Blocks.BEACON, Blocks.CONDUIT,
            Blocks.ENCHANTING_TABLE, Blocks.BREWING_STAND, Blocks.CAKE, Blocks.LODESTONE,
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.BARREL,
            Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.SCULK_SENSOR, Blocks.SCULK_CATALYST,
            Blocks.AMETHYST_CLUSTER, Blocks.BUDDING_AMETHYST, Blocks.CAULDRON,
            Blocks.COMPOSTER, Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE, Blocks.GRINDSTONE,
            Blocks.SMITHING_TABLE, Blocks.BOOKSHELF, Blocks.CHISELED_BOOKSHELF,
            Blocks.DECORATED_POT, Blocks.FLOWER_POT, Blocks.TARGET, Blocks.HONEY_BLOCK);

    private PetInterest() {
    }

    /**
     * A point near {@code centre} the pet might go and study, or null when
     * nothing around is out of the ordinary.
     */
    public static Vec3 find(Level level, Vec3 centre, double radius, RandomSource random) {
        if (radius <= 0) {
            return null;
        }

        Vec3 creature = nearbyCreature(level, centre, radius, random);
        if (creature != null && random.nextFloat() < PREFER_CREATURES) {
            return creature;
        }

        Vec3 block = curiousBlock(level, centre, radius, random);
        return block != null ? block : creature;
    }

    private static Vec3 nearbyCreature(Level level, Vec3 centre, double radius, RandomSource random) {
        AABB around = new AABB(centre, centre).inflate(radius, Math.min(radius, 5), radius);
        List<Entity> found = level.getEntities((Entity) null, around, PetInterest::worthWatching);
        if (found.isEmpty()) {
            return null;
        }
        Entity entity = found.get(random.nextInt(found.size()));
        return entity.position().add(0, entity.getBbHeight() * 0.5, 0);
    }

    /**
     * Paintings and item frames hang about being interesting; so does anything
     * alive. A pet is not an entity, so no pet can ever be on this list, which
     * is just as well — two of them staring at each other would never end.
     */
    private static boolean worthWatching(Entity entity) {
        return entity instanceof HangingEntity
                || (entity instanceof LivingEntity living && living.isAlive());
    }

    private static Vec3 curiousBlock(Level level, Vec3 centre, double radius, RandomSource random) {
        int reach = (int) Math.ceil(radius);
        int height = Math.min(reach, 4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < SAMPLES; attempt++) {
            pos.set(
                    Math.floor(centre.x) + random.nextInt(reach * 2 + 1) - reach,
                    Math.floor(centre.y) + random.nextInt(height * 2 + 1) - height,
                    Math.floor(centre.z) + random.nextInt(reach * 2 + 1) - reach);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (isCurious(level.getBlockState(pos))) {
                return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            }
        }
        return null;
    }

    private static boolean isCurious(BlockState state) {
        return CURIOSITIES.contains(state.getBlock())
                || state.is(BlockTags.BEDS)
                || state.is(BlockTags.ALL_SIGNS)
                || state.is(BlockTags.BANNERS)
                || state.is(BlockTags.CANDLES)
                || state.is(BlockTags.CAMPFIRES)
                || state.is(BlockTags.ANVIL)
                || state.is(BlockTags.SHULKER_BOXES);
    }
}
