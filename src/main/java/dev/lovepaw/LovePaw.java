package dev.lovepaw;

import dev.lovepaw.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Shared constants and entry point. Loader-specific bootstrapping lives in
 * src/fabric and src/neoforge and calls into here.
 */
public final class LovePaw {
    public static final String MOD_ID = "lovepaw";
    public static final Logger LOGGER = LoggerFactory.getLogger("LovePaw");

    /** Bumped when the network format changes in a way older clients cannot read. */
    public static final int PROTOCOL_VERSION = 1;

    private static Path configDir;

    private LovePaw() {
    }

    /** Called by both loaders, on both sides, before anything else. */
    public static void init(Path loaderConfigDir) {
        configDir = loaderConfigDir;
        LOGGER.info("LovePaw ready");
    }

    /**
     * Called when a server starts, integrated or dedicated. The server config
     * is read here rather than at init so a client that never hosts anything
     * does not grow a server config file.
     */
    public static void onServerStarting() {
        ServerConfig.load(configDir);
    }

    public static Path configDir() {
        return configDir;
    }
}
