package dev.lovepaw.model.geo;

public enum GeoFace {
    NORTH("north"),
    SOUTH("south"),
    EAST("east"),
    WEST("west"),
    UP("up"),
    DOWN("down");

    private final String key;

    GeoFace(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static GeoFace byKey(String key) {
        for (GeoFace face : values()) {
            if (face.key.equals(key)) {
                return face;
            }
        }
        return null;
    }
}
