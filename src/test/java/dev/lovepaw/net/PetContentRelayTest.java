package dev.lovepaw.net;

import dev.lovepaw.pet.PetContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handing a pet from the player who has it to the player who needs it, which
 * the server does without ever looking inside it.
 */
class PetContentRelayTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("owner".getBytes(StandardCharsets.UTF_8));
    private static final UUID ASKER = UUID.nameUUIDFromBytes("asker".getBytes(StandardCharsets.UTF_8));
    private static final UUID SOMEBODY_ELSE = UUID.nameUUIDFromBytes("else".getBytes(StandardCharsets.UTF_8));

    /** A server that writes down what it was told to send instead of sending it. */
    private static final class Fake implements PetContentRelay.Courier {
        private final Map<String, List<UUID>> wearers = new HashMap<>();
        private final List<String> asks = new ArrayList<>();
        private final List<String> refusals = new ArrayList<>();
        private final Map<UUID, List<LovePawPayloads.PetChunk>> given = new HashMap<>();

        @Override
        public void ask(UUID owner, String contentHash) {
            asks.add(owner + " " + contentHash);
        }

        @Override
        public void give(UUID player, LovePawPayloads.PetChunk chunk) {
            given.computeIfAbsent(player, key -> new ArrayList<>()).add(chunk);
        }

        @Override
        public void refuse(UUID player, String contentHash) {
            refusals.add(player + " " + contentHash);
        }

        @Override
        public Collection<UUID> wearing(String contentHash) {
            return wearers.getOrDefault(contentHash, List.of());
        }

        /** The pet a player received, put back together the way a client would. */
        PetContent received(UUID player) throws Exception {
            List<LovePawPayloads.PetChunk> chunks = given.getOrDefault(player, List.of());
            assertFalse(chunks.isEmpty(), "nothing was sent to " + player);
            PetTransfer transfer = PetTransfer.awaiting(chunks.get(0).contentHash(), chunks.get(0).count());
            for (LovePawPayloads.PetChunk chunk : chunks) {
                transfer.accept(chunk);
            }
            return transfer.finish();
        }
    }

    private static PetContent cat() {
        byte[] texture = new byte[40 * 1024];
        for (int i = 0; i < texture.length; i++) {
            texture[i] = (byte) (i * 7);
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("pet.json", "{\"texture\": \"cat.png\"}".getBytes(StandardCharsets.UTF_8));
        files.put("cat.png", texture);
        return PetContent.of(files);
    }

    private Fake courier;
    private PetContentRelay relay;
    private PetContent pet;

    @BeforeEach
    void setUp() {
        courier = new Fake();
        relay = new PetContentRelay(courier);
        pet = cat();
        courier.wearers.put(pet.hash(), List.of(OWNER));
    }

    /** Plays the owner's part: sends the pet the server just asked it for. */
    private void ownerSends(PetContent content) {
        for (LovePawPayloads.PetChunk chunk : PetTransfer.split(content)) {
            relay.onChunk(OWNER, chunk);
        }
    }

    @Test
    void aPetNobodyIsWearingIsNotComing() {
        relay.onRequest(ASKER, cat().hash().replace('a', 'b'));

        assertTrue(courier.asks.isEmpty(), "there is nobody to ask");
        assertEquals(1, courier.refusals.size(), "and the asker should be told so");
    }

    @Test
    void aPetIsFetchedFromWhoeverIsWearingIt() throws Exception {
        relay.onRequest(ASKER, pet.hash());

        assertEquals(List.of(OWNER + " " + pet.hash()), courier.asks);
        assertTrue(courier.given.isEmpty(), "nothing can be handed on before it arrives");

        ownerSends(pet);

        assertEquals(pet.hash(), courier.received(ASKER).hash());
        assertEquals(pet.files().keySet(), courier.received(ASKER).files().keySet());
    }

    @Test
    void theSecondPlayerToAskCostsNobodyAnything() throws Exception {
        relay.onRequest(ASKER, pet.hash());
        ownerSends(pet);

        relay.onRequest(SOMEBODY_ELSE, pet.hash());

        assertEquals(1, courier.asks.size(), "the owner should not be asked for it twice");
        assertEquals(pet.hash(), courier.received(SOMEBODY_ELSE).hash());
    }

    @Test
    void twoPlayersWaitingBothGetIt() throws Exception {
        relay.onRequest(ASKER, pet.hash());
        relay.onRequest(SOMEBODY_ELSE, pet.hash());

        assertEquals(1, courier.asks.size(), "one pet, one ask, however many are waiting");

        ownerSends(pet);

        assertEquals(pet.hash(), courier.received(ASKER).hash());
        assertEquals(pet.hash(), courier.received(SOMEBODY_ELSE).hash());
    }

    @Test
    void bytesFromSomebodyWhoWasNotAskedAreDropped() {
        relay.onRequest(ASKER, pet.hash());

        for (LovePawPayloads.PetChunk chunk : PetTransfer.split(pet)) {
            relay.onChunk(SOMEBODY_ELSE, chunk);
        }

        assertEquals(0, relay.cachedPets(), "a pet nobody was asked for is not kept");
        assertTrue(courier.given.isEmpty());
    }

    @Test
    void aPetThatIsNotWhatWasAskedForIsNotPassedOn() {
        relay.onRequest(ASKER, pet.hash());

        // The owner answers with the right hash on the packets and the wrong
        // bytes inside them, which is the only lie worth guarding against.
        for (LovePawPayloads.PetChunk chunk : PetTransfer.split(pet)) {
            byte[] wrong = chunk.data().clone();
            wrong[0] ^= 0x01;
            relay.onChunk(OWNER, new LovePawPayloads.PetChunk(
                    chunk.contentHash(), chunk.index(), chunk.count(), wrong));
        }

        assertEquals(0, relay.cachedPets());
        assertTrue(courier.given.isEmpty(), "whatever that was, it was not the pet that was asked for");
        assertEquals(1, courier.refusals.size(), "and there is nobody else to ask");
    }

    @Test
    void thePlayerAskingIsNeverTheOneAsked() {
        courier.wearers.put(pet.hash(), List.of(ASKER));

        relay.onRequest(ASKER, pet.hash());

        assertTrue(courier.asks.isEmpty(), "asking a player for the pet they are asking for helps nobody");
        assertEquals(1, courier.refusals.size());
    }

    @Test
    void anotherPlayerWearingItCanAnswerInstead() {
        courier.wearers.put(pet.hash(), List.of(OWNER, SOMEBODY_ELSE));
        relay.onRequest(ASKER, pet.hash());
        assertEquals(1, courier.asks.size());

        relay.onLeave(OWNER);

        assertEquals(2, courier.asks.size(), "the pet is still here, on somebody else");
        assertTrue(courier.asks.get(1).startsWith(SOMEBODY_ELSE.toString()));
        assertTrue(courier.refusals.isEmpty(), "nobody should be told it is gone while it is not");
    }

    @Test
    void whenTheLastPlayerWearingItLeavesTheWaitingAreTold() {
        relay.onRequest(ASKER, pet.hash());
        courier.wearers.clear();

        relay.onLeave(OWNER);

        assertEquals(1, courier.refusals.size(), "waiting for a pet that left with its owner is waiting forever");
    }

    @Test
    void aPlayerWhoLeavesIsNoLongerWaiting() throws Exception {
        relay.onRequest(ASKER, pet.hash());

        relay.onLeave(ASKER);
        ownerSends(pet);

        assertTrue(courier.given.isEmpty(), "they are not here to receive it");
        assertEquals(1, relay.cachedPets(), "but it is worth keeping for whoever asks next");
    }
}
