package dev.lovepaw.model.geo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lovepaw.model.geo.baked.BakedBone;
import dev.lovepaw.model.geo.baked.BakedCube;
import dev.lovepaw.model.geo.baked.BakedGeoModel;
import dev.lovepaw.model.geo.baked.BakedQuad;
import dev.lovepaw.model.geo.baked.BakedVertex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoBakerTest {
    private static final float EPSILON = 1.0E-5f;

    private static final String ONE_BLOCK_CUBE = """
            {
              "format_version": "1.12.0",
              "minecraft:geometry": [
                {
                  "description": {
                    "identifier": "geometry.test",
                    "texture_width": 64,
                    "texture_height": 64
                  },
                  "bones": [
                    {
                      "name": "body",
                      "pivot": [1, 2, 3],
                      "rotation": [30, 45, 60],
                      "cubes": [
                        {
                          "origin": [-8, 0, -8],
                          "size": [16, 16, 16],
                          "uv": [0, 0]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """;

    private static BakedGeoModel bake(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return GeoBaker.bake(GeoParser.parse(root));
    }

    @Test
    void cubeCornersLandWhereBlockbenchShowsThem() {
        BakedGeoModel model = bake(ONE_BLOCK_CUBE);
        BakedCube cube = model.roots().get(0).cubes().get(0);

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;

        for (BakedQuad quad : cube.quads()) {
            for (BakedVertex vertex : quad.vertices()) {
                minX = Math.min(minX, vertex.x());
                maxX = Math.max(maxX, vertex.x());
                minY = Math.min(minY, vertex.y());
                maxY = Math.max(maxY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxZ = Math.max(maxZ, vertex.z());
            }
        }

        assertEquals(-0.5f, minX, EPSILON);
        assertEquals(0.5f, maxX, EPSILON);
        assertEquals(0f, minY, EPSILON);
        assertEquals(1f, maxY, EPSILON);
        assertEquals(-0.5f, minZ, EPSILON);
        assertEquals(0.5f, maxZ, EPSILON);
    }

    @Test
    void pivotIsMirroredOnXAndScaledToBlocks() {
        BakedBone bone = bake(ONE_BLOCK_CUBE).roots().get(0);

        assertEquals(-1f / 16f, bone.pivot().x(), EPSILON);
        assertEquals(2f / 16f, bone.pivot().y(), EPSILON);
        assertEquals(3f / 16f, bone.pivot().z(), EPSILON);
    }

    @Test
    void rotationNegatesXAndYAndConvertsToRadians() {
        BakedBone bone = bake(ONE_BLOCK_CUBE).roots().get(0);

        assertEquals((float) Math.toRadians(-30), bone.rotation().x(), EPSILON);
        assertEquals((float) Math.toRadians(-45), bone.rotation().y(), EPSILON);
        assertEquals((float) Math.toRadians(60), bone.rotation().z(), EPSILON);
    }

    @Test
    void boxUvUnwrapsTheWayBlockbenchDraws() {
        BakedCube cube = bake(ONE_BLOCK_CUBE).roots().get(0).cubes().get(0);
        BakedQuad north = cube.quads().get(0);

        assertEquals(0f, north.normalX(), EPSILON);
        assertEquals(0f, north.normalY(), EPSILON);
        assertEquals(-1f, north.normalZ(), EPSILON);

        assertEquals(0.5f, north.vertices()[0].u(), EPSILON);
        assertEquals(0.25f, north.vertices()[0].v(), EPSILON);
        assertEquals(0.25f, north.vertices()[1].u(), EPSILON);
        assertEquals(0.5f, north.vertices()[2].v(), EPSILON);
    }

    @Test
    void childBonesAreNestedUnderTheirParent() {
        BakedGeoModel model = bake("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [
                    {
                      "description": {"identifier": "geometry.test", "texture_width": 16, "texture_height": 16},
                      "bones": [
                        {"name": "body", "pivot": [0, 0, 0]},
                        {"name": "head", "parent": "body", "pivot": [0, 8, 0]}
                      ]
                    }
                  ]
                }
                """);

        assertEquals(1, model.roots().size());
        assertEquals("body", model.roots().get(0).name());
        assertEquals(1, model.roots().get(0).children().size());
        assertEquals("head", model.roots().get(0).children().get(0).name());
        assertSame(model.roots().get(0).children().get(0), model.bonesByName().get("head"));
    }

    @Test
    void flatCubesKeepOnlyTheFacesThatExist() {
        BakedCube cube = bake("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [
                    {
                      "description": {"identifier": "geometry.test", "texture_width": 16, "texture_height": 16},
                      "bones": [
                        {
                          "name": "flat",
                          "pivot": [0, 0, 0],
                          "cubes": [{"origin": [0, 0, 0], "size": [8, 8, 0], "uv": [0, 0]}]
                        }
                      ]
                    }
                  ]
                }
                """).roots().get(0).cubes().get(0);

        assertEquals(2, cube.quads().size());
    }

    @Test
    void brokenGeometryFailsLoudly() {
        assertThrows(GeoParseException.class, () -> bake("""
                {"format_version": "1.12.0", "minecraft:geometry": []}
                """));

        assertThrows(GeoParseException.class, () -> bake("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [
                    {
                      "description": {"identifier": "geometry.test", "texture_width": 16, "texture_height": 16},
                      "bones": [{"name": "arm", "parent": "nobody", "pivot": [0, 0, 0]}]
                    }
                  ]
                }
                """));
    }

    @Test
    void legacyNineteenEightyFormatStillLoads() {
        BakedGeoModel model = bake("""
                {
                  "format_version": "1.8.0",
                  "geometry.old": {
                    "texturewidth": 16,
                    "textureheight": 16,
                    "texture_width": 16,
                    "texture_height": 16,
                    "bones": [{"name": "body", "pivot": [0, 0, 0]}]
                  }
                }
                """);

        assertTrue(model.bonesByName().containsKey("body"));
        assertEquals("geometry.old", model.identifier());
    }
}
