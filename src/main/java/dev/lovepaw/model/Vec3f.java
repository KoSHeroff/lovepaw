package dev.lovepaw.model;

/** Minimal float triple so the model layer stays free of Minecraft classes. */
public record Vec3f(float x, float y, float z) {
    public static final Vec3f ZERO = new Vec3f(0, 0, 0);
    public static final Vec3f ONE = new Vec3f(1, 1, 1);

    public boolean isZero() {
        return x == 0 && y == 0 && z == 0;
    }

    public boolean isOne() {
        return x == 1 && y == 1 && z == 1;
    }
}
