package dev.lovepaw.config;

import com.google.gson.JsonObject;
import dev.lovepaw.pet.PetRenderSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetOverridesTest {
    private static final float EPSILON = 1.0E-5f;

    @Test
    void anUntouchedOverrideChangesNothing() {
        assertSame(PetRenderSettings.DEFAULT, PetOverrides.NONE.applyTo(PetRenderSettings.DEFAULT));
        assertTrue(PetOverrides.NONE.isEmpty());
    }

    @Test
    void onlyTheSizeIsReplaced() {
        PetRenderSettings tweaked = PetOverrides.NONE.withScale(2f).applyTo(PetRenderSettings.DEFAULT);

        assertEquals(2f, tweaked.scale(), EPSILON);
        assertEquals(PetRenderSettings.DEFAULT.shadowRadius(), tweaked.shadowRadius(), EPSILON);
        assertEquals(PetRenderSettings.DEFAULT.yOffset(), tweaked.yOffset(), EPSILON);
    }

    @Test
    void clearingItGivesThePetItsOwnSizeBack() {
        PetOverrides overrides = PetOverrides.NONE.withScale(3f).withScale(null);

        assertTrue(overrides.isEmpty());
        assertEquals(PetRenderSettings.DEFAULT.scale(),
                overrides.applyTo(PetRenderSettings.DEFAULT).scale(), EPSILON);
    }

    @Test
    void survivesBeingWrittenAndReadBack() {
        JsonObject json = PetOverrides.NONE.withScale(1.75f).toJson();

        assertEquals(1.75f, PetOverrides.fromJson(json).scale(), EPSILON);
    }

    @Test
    void aMissingSectionMeansNoTweaks() {
        assertTrue(PetOverrides.fromJson(null).isEmpty());
        assertTrue(PetOverrides.fromJson(new JsonObject()).isEmpty());
        assertNull(PetOverrides.fromJson(new JsonObject()).scale());
    }

    @Test
    void theOldBehaviourSlidersAreSimplyForgotten() {
        JsonObject saved = new JsonObject();
        saved.addProperty("anchor_radius", 5.5f);
        saved.addProperty("sit_chance", 0.25f);
        saved.addProperty("wander", false);

        // Somebody upgrading has these sitting in their config. How a pet moves
        // is the game's business now, so they read as what they are: nothing.
        assertTrue(PetOverrides.fromJson(saved).isEmpty());
    }
}
