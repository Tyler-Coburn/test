package dev.emerald.core.education;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.ExperimentOutcome;
import dev.emerald.core.knowledge.KnowState;
import dev.emerald.core.knowledge.KnowledgeSource;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.testing.Fixtures;
import dev.emerald.core.testing.TestData;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LibraryTest {
    WorldObservation pull(VillageState v) {
        return v.observations().record(ObservationType.HOPPER_PULLED, "minecraft:overworld", new Pos(1, 63, 1),
                ItemIds.EGG, null, 10, null);
    }

    @Test
    void onlyTestedKnowledgeCanBeWrittenAndReadersGainTheDocumentedState() {
        VillageState v = Fixtures.village();
        CitizenRecord researcher = Fixtures.role(v, Role.RESEARCHER);
        CitizenRecord child = Fixtures.role(v, Role.CHILD);
        WorldObservation p = pull(v);
        researcher.knowledge().witness(v.observations(), p);
        assertTrue(v.library().write(researcher, ConceptId.HOPPER_PULLS_ITEM, 20).isEmpty(),
                "merely observed knowledge is not authoritative");

        researcher.knowledge().applyOutcome(ExperimentOutcome.passed(v.observations(), UUID.randomUUID(),
                ConceptId.HOPPER_PULLS_ITEM, p));
        Writing w = v.library().write(researcher, ConceptId.HOPPER_PULLS_ITEM, 30).orElseThrow();
        assertEquals(p.id(), w.sourceObservation());
        assertTrue(v.library().write(researcher, ConceptId.HOPPER_PULLS_ITEM, 31).isEmpty(), "no duplicate books");

        assertTrue(child.knowledge().readWriting(w, 40));
        var e = child.knowledge().entry(ConceptId.HOPPER_PULLS_ITEM).orElseThrow();
        assertEquals(KnowState.TESTED_TRUE, e.state());
        assertEquals(KnowledgeSource.DOCUMENT, e.source());
        assertEquals(p.id(), e.sourceObservation());
    }

    @Test
    void bookOutlivesItsAuthorAndKeepsKnowledgeInTheVillage() {
        VillageState v = Fixtures.village();
        CitizenRecord researcher = Fixtures.role(v, Role.RESEARCHER);
        CitizenRecord general = Fixtures.role(v, Role.GENERAL);
        WorldObservation p = pull(v);
        researcher.knowledge().applyOutcome(ExperimentOutcome.passed(v.observations(), UUID.randomUUID(),
                ConceptId.HOPPER_PULLS_ITEM, p));
        v.library().write(researcher, ConceptId.HOPPER_PULLS_ITEM, 30);
        v.recordDeath(researcher, "test", 40);

        VillageState back = VillageState.fromMap(TestData.reserialize(v.toMap()), dev.emerald.core.SimulationConfig.DEFAULT);
        Writing w = back.library().find(ConceptId.HOPPER_PULLS_ITEM).orElseThrow();
        CitizenRecord reader = back.citizens().get(general.id()).orElseThrow();
        assertTrue(reader.knowledge().readWriting(w, 50));
        assertEquals(KnowState.TESTED_TRUE, reader.knowledge().state(ConceptId.HOPPER_PULLS_ITEM));
    }

    @Test
    void negativeBookNeverDowngradesAConfirmedTruth() {
        VillageState v = Fixtures.village();
        CitizenRecord reader = Fixtures.role(v, Role.BUILDER);
        reader.knowledge().applyOutcome(ExperimentOutcome.passed(v.observations(), UUID.randomUUID(),
                ConceptId.HOPPER_PULLS_ITEM, pull(v)));
        Writing negative = new Writing(UUID.randomUUID(), ConceptId.HOPPER_PULLS_ITEM, KnowState.TESTED_FALSE,
                UUID.randomUUID(), null, null, 5);
        assertFalse(reader.knowledge().readWriting(negative, 6));
        assertEquals(KnowState.TESTED_TRUE, reader.knowledge().state(ConceptId.HOPPER_PULLS_ITEM));
    }
}
