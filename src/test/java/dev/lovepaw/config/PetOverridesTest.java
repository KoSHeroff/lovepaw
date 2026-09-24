package dev.lovepaw.config;

import com.google.gson.JsonObject;
import dev.lovepaw.pet.PetBehaviourSettings;
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
        PetBehaviourSettings behaviour = PetBehaviourSettings.DEFAULT;
        assertSame(behaviour, PetOverrides.NONE.applyTo(behaviour));
        assertSame(PetRenderSettings.DEFAULT, PetOverrides.NONE.applyTo(PetRenderSettings.DEFAULT));
        assertTrue(PetOverrides.NONE.isEmpty());
    }

    @Test
    void onlyTheTweakedValueIsReplaced() {
        PetBehaviourSettings base = PetBehaviourSettings.DEFAULT;
        PetBehaviourSettings tweaked = PetOverrides.NONE.withAnchorRadius(3f).applyTo(base);

        assertEquals(3f, tweaked.anchorRadius(), EPSILON);
        assertEquals(base.predictionSeconds(), tweaked.predictionSeconds(), EPSILON);
        assertEquals(base.wanderRadius(), tweaked.wanderRadius(), EPSILON);
        assertEquals(base.walkSpeed(), tweaked.walkSpeed(), EPSILON);
        assertEquals(base.teleportDistance(), tweaked.teleportDistance(), EPSILON);
        assertEquals(base.hover(), tweaked.hover());
    }

    @Test
    void clearingATweakGivesThePetItsOwnValueBack() {
        PetOverrides overrides = PetOverrides.NONE.withAnchorRadius(3f).withAnchorRadius(null);

        assertTrue(overrides.isEmpty());
        assertEquals(PetBehaviourSettings.DEFAULT.anchorRadius(),
                overrides.applyTo(PetBehaviourSettings.DEFAULT).anchorRadius(), EPSILON);
    }

    @Test
    void scaleAppliesToRenderingOnly() {
        PetRenderSettings tweaked = PetOverrides.NONE.withScale(2f).applyTo(PetRenderSettings.DEFAULT);

        assertEquals(2f, tweaked.scale(), EPSILON);
        assertEquals(PetRenderSettings.DEFAULT.shadowRadius(), tweaked.shadowRadius(), EPSILON);
    }

    @Test
    void survivesBeingWrittenAndReadBack() {
        PetOverrides overrides = PetOverrides.NONE
                .withAnchorRadius(5.5f)
                .withPredictionSeconds(0f)
                .withSitChance(0.25f)
                .withWander(false);

        JsonObject json = overrides.toJson();
        PetOverrides loaded = PetOverrides.fromJson(json);

        assertEquals(5.5f, loaded.anchorRadius(), EPSILON);
        assertEquals(0f, loaded.predictionSeconds(), EPSILON);
        assertEquals(0.25f, loaded.sitChance(), EPSILON);
        assertEquals(Boolean.FALSE, loaded.wander());
        assertNull(loaded.wanderRadius());
        assertNull(loaded.scale());
    }

    @Test
    void aMissingSectionMeansNoTweaks() {
        assertTrue(PetOverrides.fromJson(null).isEmpty());
        assertTrue(PetOverrides.fromJson(new JsonObject()).isEmpty());
    }
}
