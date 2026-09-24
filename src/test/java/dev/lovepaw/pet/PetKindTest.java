package dev.lovepaw.pet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which animal a pet.json says its pet is.
 *
 * <p>This is all a pack gets to say about behaviour now, so the one thing that
 * must not go wrong is a pet written before that came out being read as the
 * wrong animal — somebody's bee walking along the ground.
 */
class PetKindTest {
    private static final float EPSILON = 1.0E-5f;
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("lovepaw", "pet");
    private static final ResourceLocation FOLDER =
            ResourceLocation.fromNamespaceAndPath("lovepaw", "lovepaw/pets/pet");

    private static PetDefinition parse(String behaviour) {
        String json = """
                {
                  "model": "pet.geo.json",
                  "texture": "pet.png",
                  "behaviour": %s
                }
                """.formatted(behaviour);
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        return PetDefinitionParser.parse(ID, FOLDER, object, PetSourceKind.LOCAL, "hash");
    }

    @Test
    void aPetSaysWhichAnimalItIs() {
        assertEquals(PetKind.GROUND, parse("{ \"type\": \"ground\" }").kind());
        assertEquals(PetKind.FLYING, parse("{ \"type\": \"flying\" }").kind());
    }

    @Test
    void sayingNothingMeansItWalks() {
        assertEquals(PetKind.GROUND, parse("{ }").kind());
    }

    @Test
    void aPetWrittenBeforeKindsExistedStillReadsRight() {
        assertEquals(PetKind.GROUND, parse("{ \"type\": \"follow\" }").kind(),
                "every pet used to say 'follow', and every one of them walked");
        assertEquals(PetKind.FLYING, parse("{ \"type\": \"follow\", \"hover\": true }").kind(),
                "and a flyer said so with hover, which is still how it reads");
    }

    @Test
    void aPetAskingForSomethingElseWalksRatherThanVanishing() {
        assertEquals(PetKind.GROUND, parse("{ \"type\": \"swimming\" }").kind(),
                "a pet nobody here understands is better as a cat than as nothing");
    }

    @Test
    void theNumbersAPackUsedToChooseAreNoLongerItsToChoose() {
        PetDefinition tuned = parse("""
                {
                  "type": "ground",
                  "anchor_radius": 40.0,
                  "teleport_distance": 64.0,
                  "walk_speed": 0.9,
                  "sit_chance": 1.0,
                  "wander": false
                }
                """);

        assertEquals(PetKind.GROUND.followRadius(), tuned.kind().followRadius(), EPSILON);
        assertEquals(PetKind.GROUND.teleportDistance(), tuned.kind().teleportDistance(), EPSILON);
        assertEquals(PetKind.GROUND.walkSpeed(), tuned.kind().walkSpeed(), EPSILON);
    }

    @Test
    void aPetKeepsToTheDistanceItCanWalkBack() {
        for (PetKind kind : PetKind.values()) {
            // Wandering has to fit inside the distance at which a pet gives up
            // and teleports, or one minding its own business blinks about.
            assertTrue(kind.followRadius() + kind.strollRadius() < kind.teleportDistance(),
                    kind + " can wander further than it is willing to walk back from");
        }
    }

    @Test
    void howBigItIsDrawnIsHowBigItIs() {
        assertEquals(PetKind.GROUND.width(1f) * 2, PetKind.GROUND.width(2f), EPSILON,
                "a pet drawn at twice the size needs a box to match");
        assertTrue(PetKind.FLYING.width(1f) < PetKind.GROUND.width(1f),
                "an allay is a slighter thing than a cat");
    }

    @Test
    void aFlyerHoldsItsOwnHeightAndACatDoesNot() {
        assertTrue(PetKind.FLYING.hover());
        assertTrue(PetKind.FLYING.hoverHeight() > 0);
        assertEquals(0f, PetKind.FLYING.jumpPower(), EPSILON, "nothing that flies needs to jump");

        assertTrue(!PetKind.GROUND.hover());
        assertTrue(PetKind.GROUND.jumpPower() > 0, "a cat hops over what it cannot step onto");
    }
}
