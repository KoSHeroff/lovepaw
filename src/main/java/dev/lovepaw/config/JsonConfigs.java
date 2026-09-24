package dev.lovepaw.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lovepaw.LovePaw;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads and writes the mod's small JSON config files. */
public final class JsonConfigs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonConfigs() {
    }

    /** Returns the file's contents, or an empty object if it is missing or broken. */
    public static JsonObject read(Path file) {
        if (!Files.isRegularFile(file)) {
            return new JsonObject();
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            LovePaw.LOGGER.warn("Could not read {}, using defaults: {}", file, e.toString());
            return new JsonObject();
        }
    }

    public static void write(Path file, JsonObject json) {
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            LovePaw.LOGGER.error("Could not write {}: {}", file, e.toString());
        }
    }
}
