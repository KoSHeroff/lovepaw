package dev.lovepaw.net;

import dev.lovepaw.pet.PetContent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A pet cut into packets and put back together. */
class PetTransferTest {
    private static PetContent pet(int textureBytes) {
        byte[] texture = new byte[textureBytes];
        for (int i = 0; i < texture.length; i++) {
            texture[i] = (byte) i;
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("pet.json", "{\"texture\": \"cat.png\"}".getBytes(StandardCharsets.UTF_8));
        files.put("cat.png", texture);
        return PetContent.of(files);
    }

    private static PetContent roundTrip(PetContent content) throws IOException {
        List<LovePawPayloads.PetChunk> chunks = PetTransfer.split(content);
        PetTransfer transfer = PetTransfer.awaiting(content.hash(), chunks.size());
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertTrue(!transfer.accept(chunks.get(i)), "it is not finished until the last piece");
        }
        assertTrue(transfer.accept(chunks.get(chunks.size() - 1)));
        return transfer.finish();
    }

    @Test
    void aPetThatFitsInOnePacketStillTravels() throws IOException {
        PetContent content = pet(16);

        assertEquals(1, PetTransfer.split(content).size());
        assertEquals(content.hash(), roundTrip(content).hash());
    }

    @Test
    void aPetTooBigForOnePacketArrivesInOnePiece() throws IOException {
        PetContent content = pet(40 * 1024);

        assertTrue(PetTransfer.split(content).size() > 1, "this one has to be cut up");
        PetContent arrived = roundTrip(content);

        assertEquals(content.hash(), arrived.hash());
        assertEquals(content.files().keySet(), arrived.files().keySet());
        assertEquals(content.size(), arrived.size());
    }

    @Test
    void piecesOfAnotherPetAreRefused() throws IOException {
        PetContent mine = pet(16);
        PetContent theirs = pet(32);
        PetTransfer transfer = PetTransfer.awaiting(mine.hash(), 1);

        assertThrows(IOException.class, () -> transfer.accept(PetTransfer.split(theirs).get(0)));
    }

    @Test
    void theSamePieceTwiceIsRefused() throws IOException {
        PetContent content = pet(40 * 1024);
        List<LovePawPayloads.PetChunk> chunks = PetTransfer.split(content);
        PetTransfer transfer = PetTransfer.awaiting(content.hash(), chunks.size());
        transfer.accept(chunks.get(0));

        assertThrows(IOException.class, () -> transfer.accept(chunks.get(0)),
                "counting it twice would call an unfinished pet finished");
    }

    @Test
    void aPetIsOnlyTheOneThatWasAskedFor() throws IOException {
        PetContent content = pet(16);
        LovePawPayloads.PetChunk only = PetTransfer.split(content).get(0);
        byte[] tampered = only.data().clone();
        tampered[tampered.length - 1] ^= 0x01;

        PetTransfer transfer = PetTransfer.awaiting(content.hash(), 1);
        transfer.accept(new LovePawPayloads.PetChunk(content.hash(), 0, 1, tampered));

        assertThrows(IOException.class, transfer::finish,
                "bytes that do not hash to what was asked for are not that pet");
    }

    @Test
    void aPetPromisedInMorePiecesThanAnyPetHasIsRefused() {
        assertThrows(IOException.class, () -> PetTransfer.awaiting("abc", PetTransfer.MAX_CHUNKS + 1));
        assertThrows(IOException.class, () -> PetTransfer.awaiting("abc", 0));
    }
}
