package dev.lovepaw.model.geo;

public class GeoParseException extends RuntimeException {
    public GeoParseException(String message) {
        super(message);
    }

    public GeoParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
