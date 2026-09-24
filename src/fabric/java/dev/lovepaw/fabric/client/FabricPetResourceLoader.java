package dev.lovepaw.fabric.client;

import dev.lovepaw.LovePaw;
import dev.lovepaw.client.PetResourceLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

/** The shared pet loader, with the id Fabric's reload system wants. */
public final class FabricPetResourceLoader extends PetResourceLoader implements IdentifiableResourceReloadListener {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(LovePaw.MOD_ID, "pets");

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
