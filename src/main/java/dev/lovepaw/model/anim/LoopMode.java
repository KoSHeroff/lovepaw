package dev.lovepaw.model.anim;

public enum LoopMode {
    /** Plays once and snaps back to the rest pose. */
    ONCE,
    /** Restarts from the beginning, the usual choice for idle and walk. */
    LOOP,
    /** Plays once and stays on the final frame. */
    HOLD_ON_LAST_FRAME;

    public static LoopMode fromJson(String value, boolean fallbackToLoop) {
        if (value == null) {
            return fallbackToLoop ? LOOP : ONCE;
        }
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "true", "loop" -> LOOP;
            case "hold_on_last_frame" -> HOLD_ON_LAST_FRAME;
            default -> ONCE;
        };
    }
}
