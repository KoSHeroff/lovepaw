package dev.lovepaw.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lovepaw.LovePaw;
import dev.lovepaw.behaviour.PetActor;
import dev.lovepaw.client.render.PetRenderer;
import dev.lovepaw.config.ClientConfig;
import dev.lovepaw.net.LovePawPayloads;
import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps track of which player shows which pet and runs the live ones.
 *
 * <p>The local player's choice comes from their own config and applies
 * immediately, so the mod behaves the same on a vanilla server. Other players'
 * choices only arrive if the server has the mod and is willing to share them.
 */
public final class PetManager {
    private static final float MAX_FRAME_SECONDS = 0.25f;
    private static final double RENDER_RANGE = 64;

    private static final PetManager INSTANCE = new PetManager();

    /** Set by the loader glue; null until then, and on a vanilla server it is never used. */
    public interface PacketSender {
        void sendToServer(CustomPacketPayload payload);
    }

    private final Map<UUID, ResourceLocation> remoteSelections = new HashMap<>();
    private final Map<UUID, PetInstance> instances = new HashMap<>();

    private PacketSender sender;
    private boolean serverHasMod;
    private long lastFrameNanos;

    private PetManager() {
    }

    public static PetManager get() {
        return INSTANCE;
    }

    public void setSender(PacketSender sender) {
        this.sender = sender;
    }

    /** True once the server has said it speaks LovePaw. */
    public boolean serverHasMod() {
        return serverHasMod;
    }

    /** The server greeted us, so tell it what we are wearing. */
    public void onServerHello() {
        serverHasMod = true;
        sendSelection();
    }

    public void onStateEntries(List<LovePawPayloads.Entry> entries) {
        for (LovePawPayloads.Entry entry : entries) {
            if (entry.petId().isEmpty()) {
                remoteSelections.remove(entry.owner());
                instances.remove(entry.owner());
            } else {
                ResourceLocation id = ResourceLocation.tryParse(entry.petId());
                if (id != null) {
                    remoteSelections.put(entry.owner(), id);
                }
            }
        }
    }

    public void onDisconnect() {
        serverHasMod = false;
        remoteSelections.clear();
        instances.clear();
    }

    public ResourceLocation localSelection() {
        return ClientConfig.selectedPet();
    }

    public void setLocalSelection(ResourceLocation petId) {
        ClientConfig.setSelectedPet(petId);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            instances.remove(minecraft.player.getUUID());
        }
        sendSelection();
    }

    private void sendSelection() {
        if (!serverHasMod || sender == null) {
            return;
        }
        ResourceLocation selected = ClientConfig.selectedPet();
        sender.sendToServer(new LovePawPayloads.SelectPayload(selected == null ? "" : selected.toString()));
    }

    /** Once per client tick: create, update and retire pets. */
    public void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }

        Set<UUID> alive = new HashSet<>();

        for (Player player : level.players()) {
            UUID owner = player.getUUID();
            ResourceLocation wanted = wantedPet(minecraft, owner);
            if (wanted == null) {
                continue;
            }

            PetInstance instance = instances.get(owner);
            if (instance == null || !instance.definition().id().equals(wanted)) {
                instance = create(owner, wanted, owner.equals(minecraft.player.getUUID()));
                if (instance == null) {
                    continue;
                }
                instances.put(owner, instance);
            }

            instance.tick(player);
            alive.add(owner);
        }

        instances.keySet().retainAll(alive);
    }

    /**
     * Other people's pets close to this one. Every pet this client knows about
     * is simulated, on screen or not, so one of them is always a real playmate
     * rather than something standing still.
     */
    public List<PetActor.Nearby> petsNear(UUID excluding, Vec3 centre, double radius) {
        List<PetActor.Nearby> found = new ArrayList<>();
        for (PetInstance pet : instances.values()) {
            if (pet.ownerId().equals(excluding)) {
                continue;
            }
            if (pet.position().distanceToSqr(centre) > radius * radius) {
                continue;
            }
            found.add(new PetActor.Nearby(pet.ownerId(), pet.position(),
                    pet.settings().playfulness()));
        }
        return found;
    }

    private ResourceLocation wantedPet(Minecraft minecraft, UUID owner) {
        if (minecraft.player != null && owner.equals(minecraft.player.getUUID())) {
            return ClientConfig.selectedPet();
        }
        if (!ClientConfig.showOtherPlayersPets()) {
            return null;
        }
        return remoteSelections.get(owner);
    }

    private PetInstance create(UUID owner, ResourceLocation petId, boolean isLocal) {
        PetDefinition definition = PetRegistry.get().get(petId).orElse(null);
        if (definition == null) {
            return null;
        }
        PetAssets assets = PetAssetCache.get().get(petId);
        if (assets == null) {
            LovePaw.LOGGER.warn("Pet {} has no loaded model", petId);
            return null;
        }
        return new PetInstance(owner, definition, assets, isLocal);
    }

    /** Once per frame, after entities have been drawn. */
    public void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera,
                       Frustum frustum, float partialTick) {
        if (instances.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        float frameSeconds = frameDelta(minecraft.isPaused());
        Vec3 cameraPosition = camera.getPosition();

        List<PetInstance> visible = inView(instances.values(), frustum, cameraPosition, frameSeconds, partialTick);
        draw(visible, level, poseStack, buffers, cameraPosition, partialTick);
    }

    /**
     * Advances every pet's animation and keeps the ones actually on screen. The
     * clock runs either way, so a pet that walks back into view is where it
     * would have been rather than where it was when it left.
     */
    private static List<PetInstance> inView(Collection<PetInstance> pets, Frustum frustum,
                                            Vec3 cameraPosition, float frameSeconds, float partialTick) {
        List<PetInstance> visible = new ArrayList<>();
        for (PetInstance pet : pets) {
            pet.updateAnimation(frameSeconds);
            Vec3 at = pet.renderPosition(partialTick);
            if (at.distanceToSqr(cameraPosition) > RENDER_RANGE * RENDER_RANGE) {
                continue;
            }
            if (frustum != null && !frustum.isVisible(
                    new AABB(at.x - 1.5, at.y - 0.5, at.z - 1.5, at.x + 1.5, at.y + 2.5, at.z + 1.5))) {
                continue;
            }
            visible.add(pet);
        }
        return visible;
    }

    /** Bodies first, then shadows: one batch each rather than two per pet. */
    private static void draw(List<PetInstance> pets, ClientLevel level, PoseStack poseStack,
                             MultiBufferSource buffers, Vec3 cameraPosition, float partialTick) {
        for (PetInstance pet : pets) {
            PetRenderer.renderBody(pet, level, poseStack, buffers, cameraPosition, partialTick);
        }
        for (PetInstance pet : pets) {
            PetRenderer.renderShadow(pet, level, poseStack, buffers, cameraPosition, partialTick);
        }
    }

    private float frameDelta(boolean paused) {
        long now = System.nanoTime();
        long previous = lastFrameNanos;
        lastFrameNanos = now;
        if (previous == 0 || paused) {
            return 0;
        }
        return Math.min(MAX_FRAME_SECONDS, (now - previous) / 1_000_000_000f);
    }
}
