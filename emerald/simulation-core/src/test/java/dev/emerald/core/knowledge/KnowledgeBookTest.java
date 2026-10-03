package dev.emerald.core.knowledge;

import dev.emerald.core.observe.ObservationBus;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBookTest {
    final ObservationBus bus = new ObservationBus(64);

    WorldObservation obs(ObservationType type) {
        return bus.record(type, "minecraft:overworld", new Pos(0, 0, 0), ItemIds.EGG, null, 1, null);
    }

    @Test
    void witnessingRequiresAnAuthenticObservation() {
        KnowledgeBook book = new KnowledgeBook();
        WorldObservation fake = new WorldObservation(UUID.randomUUID(), ObservationType.ITEM_SPAWNED,
                "minecraft:overworld", new Pos(0, 0, 0), ItemIds.EGG, null, 1, null);
        assertThrows(IllegalArgumentException.class, () -> book.witness(bus, fake));
        book.witness(bus, obs(ObservationType.ITEM_SPAWNED));
        assertEquals(KnowState.OBSERVED, book.state(ConceptId.CHICKEN_LAYING));
        assertEquals(KnowState.OBSERVED, book.state(ConceptId.ITEM_ENTITY_SPAWN));
    }

    @Test
    void adoptionRequiresTestedTrue() {
        KnowledgeBook book = new KnowledgeBook();
        book.hypothesize(ConceptId.HOPPER_PULLS_ITEM, 1);
        assertFalse(book.adopt(ConceptId.HOPPER_PULLS_ITEM, 2));
        book.applyOutcome(ExperimentOutcome.passed(bus, UUID.randomUUID(), ConceptId.HOPPER_PULLS_ITEM,
                obs(ObservationType.HOPPER_PULLED)));
        assertTrue(book.adopt(ConceptId.HOPPER_PULLS_ITEM, 3));
        assertEquals(KnowState.ADOPTED, book.state(ConceptId.HOPPER_PULLS_ITEM));
    }

    @Test
    void laterFailureDoesNotEraseConfirmedTruthButFalseIsRetained() {
        KnowledgeBook book = new KnowledgeBook();
        WorldObservation egg = obs(ObservationType.ITEM_SPAWNED);
        book.applyOutcome(ExperimentOutcome.failed(bus, UUID.randomUUID(), ConceptId.HOPPER_PULLS_ITEM, egg, 5));
        assertEquals(KnowState.TESTED_FALSE, book.state(ConceptId.HOPPER_PULLS_ITEM));
        book.hypothesize(ConceptId.HOPPER_PULLS_ITEM, 6);
        assertEquals(KnowState.TESTED_FALSE, book.state(ConceptId.HOPPER_PULLS_ITEM), "negative knowledge is kept");
        book.applyOutcome(ExperimentOutcome.passed(bus, UUID.randomUUID(), ConceptId.HOPPER_PULLS_ITEM,
                obs(ObservationType.HOPPER_PULLED)));
        assertEquals(KnowState.TESTED_TRUE, book.state(ConceptId.HOPPER_PULLS_ITEM));
        book.applyOutcome(ExperimentOutcome.failed(bus, UUID.randomUUID(), ConceptId.HOPPER_PULLS_ITEM, egg, 9));
        assertEquals(KnowState.TESTED_TRUE, book.state(ConceptId.HOPPER_PULLS_ITEM));
    }

    @Test
    void busTailIsBounded() {
        ObservationBus small = new ObservationBus(16);
        for (int i = 0; i < 40; i++) {
            small.record(ObservationType.CROP_GREW, "minecraft:overworld", new Pos(i, 0, 0), null, null, i, null);
        }
        assertEquals(16, small.size());
        assertEquals(40, small.totalRecorded());
    }

    @Test
    void waterPushIsNotYetObservable() {
        assertNull(ConceptCatalog.evidenceFor(ConceptId.WATER_PUSHES_ITEM));
    }
}
