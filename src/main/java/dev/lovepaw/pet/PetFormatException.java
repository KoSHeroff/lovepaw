package dev.lovepaw.pet;

public class PetFormatException extends RuntimeException {
    public PetFormatException(String message) {
        super(message);
    }

    public PetFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
