package dev.lovepaw.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lovepaw.LovePaw;
import dev.lovepaw.config.ClientConfig;
import dev.lovepaw.net.LovePawPayloads;
import dev.lovepaw.net.PetTransfer;
import dev.lovepaw.pet.PetContent;
import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetSourceKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pets belonging to other players, fetched when they are needed.
 *
 * <p>A player can make a pet nobody else has. Rather than making everybody
 * install the same files by hand, a client that meets a pet it does not have
 * asks the server for it and the server asks whoever is wearing it. The files
 * arrive as bytes and are built into a pet here, so nothing is written into a
 * resource pack and no resource reload happens: a reload mid-fight, to show
 * somebody's cat, would be a poor trade.
 *
 * <p>Downloaded pets are kept apart from the player's own. They are never put
 * in the registry and so never appear in the picker — they belong to whoever
 * made them, and are only ever drawn on that player.
 */
public final class PetDownloads {
    /** How many downloaded pets are kept on disk between sessions. */
    private static final int MAX_CACHED_PETS = 64;

    private static final PetDownloads INSTANCE = new PetDownloads();

    /** A downloaded pet, ready to draw. */
    public record Installed(PetDefinition definition, PetAssets assets) {
    }

    private final Map<String, Installed> installed = new HashMap<>();
    private final Map<String, PetTransfer> arriving = new HashMap<>();
    private final Set<String> asked = new HashSet<>();

    /** Pets the server has said are not coming, so we stop asking for them. */
    private final Set<String> refused = new HashSet<>();

    private Path cacheFolder;

    private PetDownloads() {
    }

    public static PetDownloads get() {
        return INSTANCE;
    }

    /** Where downloaded pets are kept, given the game directory. */
    public static Path folderIn(Path gameDirectory) {
        return gameDirectory.resolve("lovepaw").resolve("cache");
    }

    public void setGameDirectory(Path gameDirectory) {
        this.cacheFolder = gameDirectory == null ? null : folderIn(gameDirectory);
    }

    /** A downloaded pet with this hash, or null if we do not have one. */
    public Installed find(String contentHash) {
        return installed.get(contentHash);
    }

    /**
     * Somebody nearby is wearing a pet we do not have. Asks for it once, and
     * says nothing more about it until the answer arrives.
     */
    public void want(String contentHash) {
        if (contentHash == null || contentHash.isEmpty()
                || installed.containsKey(contentHash)
                || arriving.containsKey(contentHash)
                || asked.contains(contentHash)
                || refused.contains(contentHash)
                || !ClientConfig.downloadPlayerPets()
                || !PetManager.get().serverHasMod()) {
            // Without a server that speaks LovePaw there is nobody to ask, and
            // remembering that we asked would keep us from asking later.
            return;
        }

        if (fromCache(contentHash)) {
            return;
        }

        asked.add(contentHash);
        PetManager.get().send(new LovePawPayloads.PetRequestPayload(contentHash));
    }

    /** The server is asking for a pet this client has. */
    public void onRequested(String contentHash) {
        PetContent content = PetOrigins.contentOf(contentHash);
        if (content == null) {
            return;
        }
        if (!content.isShareable()) {
            LovePaw.LOGGER.info("Pet {} is too big to send to other players ({} bytes)",
                    contentHash, content.size());
            return;
        }
        for (LovePawPayloads.PetChunk chunk : PetTransfer.split(content)) {
            PetManager.get().send(new LovePawPayloads.PetUploadPayload(chunk));
        }
    }

    /** One slice of a pet we asked for. */
    public void onChunk(LovePawPayloads.PetChunk chunk) {
        String contentHash = chunk.contentHash();
        if (installed.containsKey(contentHash)) {
            return;
        }
        try {
            PetTransfer transfer = arriving.get(contentHash);
            if (transfer == null) {
                transfer = PetTransfer.awaiting(contentHash, chunk.count());
                arriving.put(contentHash, transfer);
            }
            if (!transfer.accept(chunk)) {
                return;
            }
            arriving.remove(contentHash);
            asked.remove(contentHash);
            install(transfer.finish(), true);
        } catch (IOException e) {
            LovePaw.LOGGER.warn("Pet {} did not arrive in one piece: {}", contentHash, e.getMessage());
            arriving.remove(contentHash);
            asked.remove(contentHash);
            refused.add(contentHash);
        }
    }

    /** Nobody here can give us that pet, so stop waiting for it. */
    public void onMissing(String contentHash) {
        arriving.remove(contentHash);
        asked.remove(contentHash);
        refused.add(contentHash);
    }

    /**
     * Leaving a server drops what is in flight, but not what has arrived: the
     * pets themselves are worth keeping, and the next server may well have the
     * same players on it.
     */
    public void onDisconnect() {
        arriving.clear();
        asked.clear();
        refused.clear();
    }

    /** Reads a pet we downloaded before, so meeting it again costs nothing. */
    private boolean fromCache(String contentHash) {
        Path directory = directoryFor(contentHash);
        if (directory == null || !Files.isRegularFile(directory.resolve(PetContent.DEFINITION))) {
            return false;
        }
        try {
            PetContent content = PetBundle.contentOf(
                    idFor(contentHash), folderFor(contentHash), PetBundle.filesIn(directory));
            if (!content.is(contentHash)) {
                // Not what it is filed under, so it is not this pet, whatever
                // it is. Asking the server for it again is the honest answer.
                return false;
            }
            install(content, false);
            return true;
        } catch (Exception e) {
            LovePaw.LOGGER.debug("Cached pet {} would not load: {}", contentHash, e.toString());
            return false;
        }
    }

    private void install(PetContent content, boolean writeToCache) {
        String contentHash = content.hash();
        ResourceLocation id = idFor(contentHash);
        ResourceLocation folder = folderFor(contentHash);
        try {
            PetBundle.Loaded loaded = PetBundle.read(
                    id, folder, PetSourceKind.REMOTE, PetBundle.of(content.files()));

            // The texture never becomes a resource: it is handed straight to
            // the texture manager, which is why no reload is needed to see it
            // and why a later reload does not take it away again.
            try (InputStream in = new ByteArrayInputStream(loaded.texture())) {
                Minecraft.getInstance().getTextureManager().register(
                        loaded.definition().texture(), new DynamicTexture(NativeImage.read(in)));
            }

            installed.put(contentHash, new Installed(loaded.definition(), loaded.assets()));
            if (writeToCache) {
                writeCache(contentHash, content);
            }
            PetOrigins.remember(loaded.definition(), folder, directoryFor(contentHash));
            LovePaw.LOGGER.info("Pet {} arrived from another player", loaded.definition().displayName());
        } catch (Exception e) {
            LovePaw.LOGGER.warn("Pet {} would not load: {}", contentHash, e.toString());
            refused.add(contentHash);
        }
    }

    private void writeCache(String contentHash, PetContent content) {
        Path directory = directoryFor(contentHash);
        if (directory == null) {
            return;
        }
        try {
            Files.createDirectories(directory);
            for (Map.Entry<String, byte[]> file : content.files().entrySet()) {
                Files.write(directory.resolve(file.getKey()), file.getValue());
            }
            prune();
        } catch (IOException e) {
            LovePaw.LOGGER.debug("Could not keep pet {}: {}", contentHash, e.toString());
        }
    }

    /** Keeps the cache from growing without end, oldest first. */
    private void prune() throws IOException {
        List<Path> directories;
        try (Stream<Path> children = Files.list(cacheFolder)) {
            directories = new ArrayList<>(children.filter(Files::isDirectory).toList());
        }
        if (directories.size() <= MAX_CACHED_PETS) {
            return;
        }
        directories.sort(Comparator.comparing(PetDownloads::modifiedAt));
        for (Path directory : directories.subList(0, directories.size() - MAX_CACHED_PETS)) {
            if (installed.containsKey(directory.getFileName().toString())) {
                continue;
            }
            delete(directory);
        }
    }

    private static long modifiedAt(Path directory) {
        try {
            return Files.getLastModifiedTime(directory).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static void delete(Path directory) {
        try {
            List<Path> files = new ArrayList<>();
            try (Stream<Path> children = Files.list(directory)) {
                children.forEach(files::add);
            }
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(directory);
        } catch (IOException e) {
            LovePaw.LOGGER.debug("Could not clear {}: {}", directory, e.toString());
        }
    }

    private Path directoryFor(String contentHash) {
        return cacheFolder == null ? null : cacheFolder.resolve(contentHash);
    }

    /**
     * A downloaded pet is named after its own files. It is never the id the
     * player who made it uses, because that id means something else here.
     */
    private static ResourceLocation idFor(String contentHash) {
        return ResourceLocation.fromNamespaceAndPath("remote", contentHash.substring(0, 16));
    }

    private static ResourceLocation folderFor(String contentHash) {
        return ResourceLocation.fromNamespaceAndPath(LovePaw.MOD_ID, "remote/" + contentHash);
    }
}
