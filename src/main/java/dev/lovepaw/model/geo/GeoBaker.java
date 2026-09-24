package dev.lovepaw.model.geo;

import dev.lovepaw.model.Vec3f;
import dev.lovepaw.model.geo.baked.BakedBone;
import dev.lovepaw.model.geo.baked.BakedCube;
import dev.lovepaw.model.geo.baked.BakedGeoModel;
import dev.lovepaw.model.geo.baked.BakedQuad;
import dev.lovepaw.model.geo.baked.BakedVertex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns parsed Bedrock geometry into draw-ready quads, once, at load time.
 *
 * <p>Blockbench and Minecraft do not share a coordinate space. Blockbench works
 * in 16 units per block with the X axis pointing the other way, and writes bone
 * rotations in degrees whose X and Y run opposite to Minecraft's. The whole
 * conversion is confined to this class, so everything downstream — animation,
 * physics, rendering — works in plain Minecraft coordinates:
 *
 * <ul>
 *   <li>position: {@code x -> -(x + width) / 16}, {@code y -> y / 16}, {@code z -> z / 16}</li>
 *   <li>pivot: {@code (-x, y, z) / 16}</li>
 *   <li>rotation: {@code (-x, -y, z)} in radians, applied Z, then Y, then X</li>
 * </ul>
 *
 * <p>The same sign rules apply to animation keyframes, see
 * {@code dev.lovepaw.model.anim.AnimationParser}.
 */
public final class GeoBaker {
    private static final float SCALE = 1f / 16f;
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private GeoBaker() {
    }

    public static BakedGeoModel bake(GeoModel model) {
        Map<String, List<GeoBone>> childrenByParent = new HashMap<>();
        List<GeoBone> roots = new ArrayList<>();

        for (GeoBone bone : model.bones()) {
            if (bone.parent() == null) {
                roots.add(bone);
            } else {
                childrenByParent.computeIfAbsent(bone.parent(), key -> new ArrayList<>()).add(bone);
            }
        }

        Map<String, BakedBone> byName = new LinkedHashMap<>();
        List<BakedBone> bakedRoots = new ArrayList<>(roots.size());
        for (GeoBone root : roots) {
            bakedRoots.add(bakeBone(root, childrenByParent, byName, model));
        }

        return new BakedGeoModel(
                model.identifier(),
                List.copyOf(bakedRoots),
                Map.copyOf(byName),
                model.textureWidth(),
                model.textureHeight());
    }

    private static BakedBone bakeBone(GeoBone bone,
                                      Map<String, List<GeoBone>> childrenByParent,
                                      Map<String, BakedBone> byName,
                                      GeoModel model) {
        List<BakedCube> cubes = new ArrayList<>(bone.cubes().size());
        for (GeoCube cube : bone.cubes()) {
            cubes.add(bakeCube(cube, model.textureWidth(), model.textureHeight()));
        }

        List<BakedBone> children = new ArrayList<>();
        for (GeoBone child : childrenByParent.getOrDefault(bone.name(), List.of())) {
            children.add(bakeBone(child, childrenByParent, byName, model));
        }

        BakedBone baked = new BakedBone(
                bone.name(),
                new Vec3f(-bone.pivot().x() * SCALE, bone.pivot().y() * SCALE, bone.pivot().z() * SCALE),
                new Vec3f(-bone.rotation().x() * DEG_TO_RAD, -bone.rotation().y() * DEG_TO_RAD, bone.rotation().z() * DEG_TO_RAD),
                List.copyOf(cubes),
                List.copyOf(children));

        byName.put(baked.name(), baked);
        return baked;
    }

    private static BakedCube bakeCube(GeoCube cube, float textureWidth, float textureHeight) {
        Vec3f size = cube.size();
        Vec3f origin = new Vec3f(
                -(cube.origin().x() + size.x()) * SCALE,
                cube.origin().y() * SCALE,
                cube.origin().z() * SCALE);
        Vec3f vertexSize = new Vec3f(size.x() * SCALE, size.y() * SCALE, size.z() * SCALE);
        float inflate = cube.inflate() * SCALE;

        Corners corners = new Corners(origin, vertexSize, inflate);
        List<BakedQuad> quads = new ArrayList<>(6);
        boolean boxUv = !cube.hasPerFaceUv();

        for (GeoFace face : GeoFace.values()) {
            if (isZeroSizeFace(size, face)) {
                continue;
            }
            GeoUv uv = boxUv
                    ? boxUvFor(face, cube.boxUv().x(), cube.boxUv().y(), size)
                    : cube.perFaceUv().get(face);
            if (uv == null) {
                continue;
            }
            quads.add(buildQuad(corners, face, uv, cube.mirror(), boxUv, textureWidth, textureHeight));
        }

        return new BakedCube(
                List.copyOf(quads),
                new Vec3f(-cube.pivot().x() * SCALE, cube.pivot().y() * SCALE, cube.pivot().z() * SCALE),
                new Vec3f(-cube.rotation().x() * DEG_TO_RAD, -cube.rotation().y() * DEG_TO_RAD, cube.rotation().z() * DEG_TO_RAD));
    }

    private static boolean isZeroSizeFace(Vec3f size, GeoFace face) {
        if (size.x() == 0) {
            return face != GeoFace.WEST && face != GeoFace.EAST;
        }
        if (size.y() == 0) {
            return face != GeoFace.UP && face != GeoFace.DOWN;
        }
        if (size.z() == 0) {
            return face != GeoFace.NORTH && face != GeoFace.SOUTH;
        }
        return false;
    }

    private static GeoUv boxUvFor(GeoFace face, float u, float v, Vec3f size) {
        float w = (float) Math.floor(size.x());
        float h = (float) Math.floor(size.y());
        float d = (float) Math.floor(size.z());

        return switch (face) {
            case WEST -> new GeoUv(u + d + w, v + d, d, h);
            case EAST -> new GeoUv(u, v + d, d, h);
            case NORTH -> new GeoUv(u + d, v + d, w, h);
            case SOUTH -> new GeoUv(u + d + w + d, v + d, w, h);
            case UP -> new GeoUv(u + d, v, w, d);
            case DOWN -> new GeoUv(u + d + w, v + d, w, -d);
        };
    }

    private static BakedQuad buildQuad(Corners corners,
                                       GeoFace face,
                                       GeoUv uv,
                                       boolean mirror,
                                       boolean boxUv,
                                       float textureWidth,
                                       float textureHeight) {
        BakedVertex[] vertices = corners.forFace(face, boxUv, mirror);

        float u = uv.u();
        float v = uv.v();
        float uMax = (u + uv.uSize()) / textureWidth;
        float vMax = (v + uv.vSize()) / textureHeight;
        u /= textureWidth;
        v /= textureHeight;

        float normalX = normalX(face);
        if (!mirror) {
            float swap = uMax;
            uMax = u;
            u = swap;
        } else {
            normalX = -normalX;
        }

        BakedVertex[] uvVertices = new BakedVertex[]{
                vertices[0].withUv(u, v),
                vertices[1].withUv(uMax, v),
                vertices[2].withUv(uMax, vMax),
                vertices[3].withUv(u, vMax)
        };

        return new BakedQuad(uvVertices, normalX, normalY(face), normalZ(face));
    }

    private static float normalX(GeoFace face) {
        return switch (face) {
            case WEST -> -1;
            case EAST -> 1;
            default -> 0;
        };
    }

    private static float normalY(GeoFace face) {
        return switch (face) {
            case DOWN -> -1;
            case UP -> 1;
            default -> 0;
        };
    }

    private static float normalZ(GeoFace face) {
        return switch (face) {
            case NORTH -> -1;
            case SOUTH -> 1;
            default -> 0;
        };
    }

    private record Corners(
            BakedVertex bottomLeftBack,
            BakedVertex bottomRightBack,
            BakedVertex topLeftBack,
            BakedVertex topRightBack,
            BakedVertex topLeftFront,
            BakedVertex topRightFront,
            BakedVertex bottomLeftFront,
            BakedVertex bottomRightFront
    ) {
        Corners(Vec3f origin, Vec3f size, float inflate) {
            this(
                    vertex(origin.x() - inflate, origin.y() - inflate, origin.z() - inflate),
                    vertex(origin.x() - inflate, origin.y() - inflate, origin.z() + size.z() + inflate),
                    vertex(origin.x() - inflate, origin.y() + size.y() + inflate, origin.z() - inflate),
                    vertex(origin.x() - inflate, origin.y() + size.y() + inflate, origin.z() + size.z() + inflate),
                    vertex(origin.x() + size.x() + inflate, origin.y() + size.y() + inflate, origin.z() - inflate),
                    vertex(origin.x() + size.x() + inflate, origin.y() + size.y() + inflate, origin.z() + size.z() + inflate),
                    vertex(origin.x() + size.x() + inflate, origin.y() - inflate, origin.z() - inflate),
                    vertex(origin.x() + size.x() + inflate, origin.y() - inflate, origin.z() + size.z() + inflate));
        }

        private static BakedVertex vertex(float x, float y, float z) {
            return new BakedVertex(x, y, z, 0, 0);
        }

        BakedVertex[] forFace(GeoFace face, boolean boxUv, boolean mirror) {
            return switch (face) {
                case WEST -> mirror ? east() : west();
                case EAST -> mirror ? west() : east();
                case NORTH -> north();
                case SOUTH -> south();
                case UP -> mirror && !boxUv ? down() : up();
                case DOWN -> mirror && !boxUv ? up() : down();
            };
        }

        private BakedVertex[] west() {
            return new BakedVertex[]{topRightBack, topLeftBack, bottomLeftBack, bottomRightBack};
        }

        private BakedVertex[] east() {
            return new BakedVertex[]{topLeftFront, topRightFront, bottomRightFront, bottomLeftFront};
        }

        private BakedVertex[] north() {
            return new BakedVertex[]{topLeftBack, topLeftFront, bottomLeftFront, bottomLeftBack};
        }

        private BakedVertex[] south() {
            return new BakedVertex[]{topRightFront, topRightBack, bottomRightBack, bottomRightFront};
        }

        private BakedVertex[] up() {
            return new BakedVertex[]{topRightBack, topRightFront, topLeftFront, topLeftBack};
        }

        private BakedVertex[] down() {
            return new BakedVertex[]{bottomLeftBack, bottomLeftFront, bottomRightFront, bottomRightBack};
        }
    }
}
