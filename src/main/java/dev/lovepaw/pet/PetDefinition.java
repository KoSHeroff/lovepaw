package dev.lovepaw.pet;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Everything a pet is, as read from its {@code pet.json}. This is plain data:
 * it holds where the model, texture and animations live, not the loaded model
 * itself, so a dedicated server can carry the list of pets without ever
 * touching a client-only class.
 *
 * @param id            namespaced id, taken from the file's own location
 * @param displayName   shown in the picker; a translation key when it starts
 *                      with a letter and contains a dot, otherwise literal text
 * @param author        credited in the picker
 * @param version       the pack author's own version string
 * @param model         .geo.json location
 * @param texture       texture location
 * @param animationFile .animation.json location, may be null
 * @param animations    state (idle, walk, ...) to animation name
 * @param render        drawing options
 * @param behaviour     movement options
 * @param source        where this definition was found
 * @param contentHash   names the files this pet is made of, so a pet can be
 *                      told apart from a different one wearing the same id
 */
public record PetDefinition(
        ResourceLocation id,
        String displayName,
        String author,
        String version,
        ResourceLocation model,
        ResourceLocation texture,
        ResourceLocation animationFile,
        Map<PetAnimationState, String> animations,
        PetRenderSettings render,
        PetBehaviourSettings behaviour,
        PetSourceKind source,
        String contentHash
) {
    /** Animation name for a state, or null when the pack does not define one. */
    public String animationFor(PetAnimationState state) {
        return animations.get(state);
    }
}
