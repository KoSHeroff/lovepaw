package dev.lovepaw.model.anim.molang;

public class MolangException extends RuntimeException {
    public MolangException(String message) {
        super(message);
    }

    public MolangException(String message, Throwable cause) {
        super(message, cause);
    }
}
