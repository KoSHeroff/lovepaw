package dev.lovepaw.model.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lovepaw.model.Vec3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads Blockbench's "Bedrock Model" export (.geo.json). Both the modern 1.12.0
 * layout ("minecraft:geometry": [...]) and the legacy 1.8.0 layout (top-level
 * "geometry.name" objects) are accepted, because Blockbench still writes the old
 * one for projects created in it.
 */
public final class GeoParser {
    private GeoParser() {
    }

    public static GeoModel parse(JsonObject root) {
        return parse(root, GeoLimits.DEFAULT);
    }

    public static GeoModel parse(JsonObject root, GeoLimits limits) {
        if (root.has("minecraft:geometry")) {
            JsonArray array = root.getAsJsonArray("minecraft:geometry");
            if (array.isEmpty()) {
                throw new GeoParseException("'minecraft:geometry' is empty");
            }
            JsonObject geometry = array.get(0).getAsJsonObject();
            JsonObject description = geometry.has("description")
                    ? geometry.getAsJsonObject("description")
                    : new JsonObject();
            String identifier = string(description, "identifier", "geometry.unknown");
            return read(geometry, description, identifier, limits);
        }

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getKey().startsWith("geometry.") && entry.getValue().isJsonObject()) {
                JsonObject geometry = entry.getValue().getAsJsonObject();
                return read(geometry, geometry, entry.getKey(), limits);
            }
        }

        throw new GeoParseException("no geometry found: expected 'minecraft:geometry' or a 'geometry.*' key");
    }

    private static GeoModel read(JsonObject geometry, JsonObject description, String identifier, GeoLimits limits) {
        float textureWidth = number(description, "texture_width", 64);
        float textureHeight = number(description, "texture_height", 64);
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new GeoParseException("texture_width and texture_height must be positive");
        }

        List<GeoBone> bones = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int totalCubes = 0;

        if (geometry.has("bones")) {
            JsonArray boneArray = geometry.getAsJsonArray("bones");
            if (boneArray.size() > limits.maxBones()) {
                throw new GeoParseException("too many bones: " + boneArray.size() + " > " + limits.maxBones());
            }
            for (JsonElement element : boneArray) {
                GeoBone bone = readBone(element.getAsJsonObject(), limits);
                if (!names.add(bone.name())) {
                    throw new GeoParseException("duplicate bone name: " + bone.name());
                }
                totalCubes += bone.cubes().size();
                if (totalCubes > limits.maxTotalCubes()) {
                    throw new GeoParseException("too many cubes: more than " + limits.maxTotalCubes());
                }
                bones.add(bone);
            }
        }

        for (GeoBone bone : bones) {
            if (bone.parent() != null && !names.contains(bone.parent())) {
                throw new GeoParseException("bone " + bone.name() + " has unknown parent " + bone.parent());
            }
        }

        return new GeoModel(identifier, textureWidth, textureHeight, List.copyOf(bones));
    }

    private static GeoBone readBone(JsonObject json, GeoLimits limits) {
        String name = string(json, "name", null);
        if (name == null || name.isBlank()) {
            throw new GeoParseException("bone without a name");
        }
        String parent = string(json, "parent", null);
        Vec3f pivot = vec3(json, "pivot", Vec3f.ZERO);
        Vec3f rotation = vec3(json, "rotation", Vec3f.ZERO);
        boolean mirror = bool(json, "mirror", false);
        float inflate = number(json, "inflate", 0);

        List<GeoCube> cubes = new ArrayList<>();
        if (json.has("cubes")) {
            JsonArray cubeArray = json.getAsJsonArray("cubes");
            if (cubeArray.size() > limits.maxCubesPerBone()) {
                throw new GeoParseException("bone " + name + " has too many cubes: " + cubeArray.size());
            }
            for (JsonElement element : cubeArray) {
                cubes.add(readCube(element.getAsJsonObject(), pivot, mirror, inflate));
            }
        }

        return new GeoBone(name, parent, pivot, rotation, mirror, inflate, List.copyOf(cubes));
    }

    private static GeoCube readCube(JsonObject json, Vec3f bonePivot, boolean boneMirror, float boneInflate) {
        Vec3f origin = vec3(json, "origin", Vec3f.ZERO);
        Vec3f size = vec3(json, "size", Vec3f.ZERO);
        Vec3f pivot = vec3(json, "pivot", bonePivot);
        Vec3f rotation = vec3(json, "rotation", Vec3f.ZERO);
        float inflate = number(json, "inflate", boneInflate);
        boolean mirror = bool(json, "mirror", boneMirror);

        Vec3f boxUv = Vec3f.ZERO;
        Map<GeoFace, GeoUv> perFaceUv = null;

        JsonElement uv = json.get("uv");
        if (uv != null && uv.isJsonArray()) {
            JsonArray array = uv.getAsJsonArray();
            boxUv = new Vec3f(
                    array.size() > 0 ? array.get(0).getAsFloat() : 0,
                    array.size() > 1 ? array.get(1).getAsFloat() : 0,
                    0);
        } else if (uv != null && uv.isJsonObject()) {
            perFaceUv = new EnumMap<>(GeoFace.class);
            for (Map.Entry<String, JsonElement> entry : uv.getAsJsonObject().entrySet()) {
                GeoFace face = GeoFace.byKey(entry.getKey());
                if (face == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject faceJson = entry.getValue().getAsJsonObject();
                Vec3f uvOrigin = vec2(faceJson, "uv");
                Vec3f uvSize = vec2(faceJson, "uv_size");
                perFaceUv.put(face, new GeoUv(uvOrigin.x(), uvOrigin.y(), uvSize.x(), uvSize.y()));
            }
        }

        return new GeoCube(origin, size, pivot, rotation, inflate, mirror, boxUv, perFaceUv);
    }

    private static Vec3f vec3(JsonObject json, String key, Vec3f fallback) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) {
            return fallback;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() < 3) {
            throw new GeoParseException(key + " must hold 3 numbers");
        }
        return new Vec3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    private static Vec3f vec2(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) {
            return Vec3f.ZERO;
        }
        JsonArray array = element.getAsJsonArray();
        return new Vec3f(
                array.size() > 0 ? array.get(0).getAsFloat() : 0,
                array.size() > 1 ? array.get(1).getAsFloat() : 0,
                0);
    }

    private static float number(JsonObject json, String key, float fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
    }

    private static String string(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }
}
