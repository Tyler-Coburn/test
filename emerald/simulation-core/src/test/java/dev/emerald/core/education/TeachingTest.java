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
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeachingTest {
    /** teaching_copies_observed_not_tested_true */
    @Test
    void studentReceivesObservedNeverTestedTrue() {
        VillageState v = Fixtures.village();
        CitizenRecord teacher = Fixtures.role(v, Role.RESEARCHER);
        CitizenRecord student = Fixtures.role(v, Role.CHILD);
        WorldObservation pull = v.observations().record(ObservationType.HOPPER_PULLED, "minecraft:overworld",
                new Pos(1, 63, 1), ItemIds.EGG, teacher.id(), 10, null);
        teacher.knowledge().applyOutcome(ExperimentOutcome.passed(v.observations(), UUID.randomUUID(),
                ConceptId.HOPPER_PULLS_ITEM, pull));
        assertEquals(KnowState.TESTED_TRUE, teacher.knowledge().state(ConceptId.HOPPER_PULLS_ITEM));

        assertEquals(Teaching.Result.TAUGHT, Teaching.teach(v, teacher, student, ConceptId.HOPPER_PULLS_ITEM, 20));

        var learned = student.knowledge().entry(ConceptId.HOPPER_PULLS_ITEM).orElseThrow();
        assertEquals(KnowState.OBSERVED, learned.state());
        assertEquals(KnowledgeSource.TAUGHT, learned.source());
        assertEquals(pull.id(), learned.sourceObservation(), "the lesson cites the original evidence");
        assertTrue(learned.confidence() < 90);
        assertEquals(1, v.ledger().ofType("KNOWLEDGE_TAUGHT").size());
    }

    @Test
    void cannotTeachWhatYouDoNotKnowOrToTheDead() {
        VillageState v = Fixtures.village();
        CitizenRecord teacher = Fixtures.role(v, Role.RESEARCHER);
        CitizenRecord student = Fixtures.role(v, Role.GENERAL);
        assertEquals(Teaching.Result.TEACHER_IGNORANT, Teaching.teach(v, teacher, student, ConceptId.CROP_GROWTH, 1));
        v.recordDeath(student, "test", 2);
        assertEquals(Teaching.Result.NOT_ALIVE, Teaching.teach(v, teacher, student, ConceptId.CROP_GROWTH, 3));
    }

    @Test
    void knowledgeDiesWithItsOnlyHolderWhenNotTaught() {
        VillageState v = Fixtures.village();
        CitizenRecord teacher = Fixtures.role(v, Role.RESEARCHER);
        WorldObservation pull = v.observations().record(ObservationType.HOPPER_PULLED, "minecraft:overworld",
                new Pos(1, 63, 1), ItemIds.EGG, teacher.id(), 10, null);
        teacher.knowledge().witness(v.observations(), pull);
        v.recordDeath(teacher, "test", 20);
        long holders = v.citizens().alive().stream()
                .filter(c -> c.knowledge().state(ConceptId.HOPPER_PULLS_ITEM) != KnowState.UNKNOWN).count();
        assertEquals(0, holders);
    }
}
