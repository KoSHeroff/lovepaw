package dev.lovepaw.client;

import dev.lovepaw.LovePaw;
import dev.lovepaw.pet.PetContent;
import dev.lovepaw.pet.PetDefinition;
import dev.lovepaw.pet.PetSourceKind;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads pets from a plain folder in the game directory, next to the resource
 * packs but without being one.
 *
 * <p>Making a pet otherwise means building a resource pack around it: a
 * {@code pack.mcmeta}, the right nesting under a namespace, enabling it in the
 * options. Every one of those steps fails the same silent way — the pet is
 * simply missing from the picker. A folder the player drops files into removes
 * all of them, and it is where a downloaded pet will land when a catalogue
 * exists.
 *
 * <p>Reading files is all this does; the texture is handed on as bytes, because
 * turning it into something the renderer can bind has to happen on the render
 * thread and this runs off it.
 */
public final class PetLocalLoader {
    /** Namespace for folder pets, so they can never collide with a pack's. */
    public static final String NAMESPACE = "local";

    /**
     * A pet found on disk, with its texture not yet uploaded and the folder it
     * came from, which is where it is read from again if another player needs
     * it.
     */
    public record LocalPet(PetDefinition definition, PetAssets assets, byte[] texture,
                           ResourceLocation folder, Path directory) {
    }

    private PetLocalLoader() {
    }

    /** The folder pets are read from, given the game directory. */
    public static Path folderIn(Path gameDirectory) {
        return gameDirectory.resolve("lovepaw").resolve("pets");
    }

    /**
     * Reads every pet in {@code folder}, skipping and logging the ones that do
     * not load. Creates the folder when it is missing, so that a player who
     * goes looking for it finds it already there.
     */
    public static List<LocalPet> scan(Path folder) {
        List<LocalPet> pets = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                LovePaw.LOGGER.warn("Could not create the pet folder {}: {}", folder, e.toString());
            }
            return pets;
        }

        List<Path> directories = new ArrayList<>();
        try (Stream<Path> children = Files.list(folder)) {
            children.filter(Files::isDirectory).sorted().forEach(directories::add);
        } catch (IOException e) {
            LovePaw.LOGGER.error("Could not read the pet folder {}: {}", folder, e.toString());
            return pets;
        }

        for (Path directory : directories) {
            if (!Files.isRegularFile(directory.resolve(PetContent.DEFINITION))) {
                continue;
            }
            try {
                pets.add(load(directory));
            } catch (Exception e) {
                LovePaw.LOGGER.error("Skipping pet {}: {}", directory, e.getMessage());
                LovePaw.LOGGER.debug("Local pet load failure", e);
            }
        }
        return pets;
    }

    private static LocalPet load(Path directory) throws IOException {
        String name = directory.getFileName().toString();
        ResourceLocation id = ResourceLocation.tryBuild(NAMESPACE, name);
        if (id == null) {
            throw new IOException("folder name '" + name
                    + "' cannot be a pet id; use lowercase letters, digits, _ - and .");
        }
        // Assets keep their own ids under this folder, which is what the
        // texture is registered as later: unique per pet, and never a path a
        // resource pack could also claim.
        ResourceLocation assetFolder = ResourceLocation.fromNamespaceAndPath(
                LovePaw.MOD_ID, NAMESPACE + "/" + name);

        PetBundle.Loaded loaded =
                PetBundle.read(id, assetFolder, PetSourceKind.LOCAL, PetBundle.filesIn(directory));
        return new LocalPet(loaded.definition(), loaded.assets(), loaded.texture(), assetFolder, directory);
    }
}
