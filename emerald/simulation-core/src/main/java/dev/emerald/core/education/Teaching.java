package dev.emerald.core.education;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.KnowledgeEntry;
import dev.emerald.core.village.VillageState;

import java.util.Optional;

/**
 * Lossy knowledge transfer. A student learns at most OBSERVED, citing the teacher's source
 * observation; TESTED_TRUE requires the student's own evidence or an authoritative document.
 */
public final class Teaching {
    public enum Result { TAUGHT, ALREADY_KNOWN, TEACHER_IGNORANT, NO_EVIDENCE, NOT_ALIVE, SAME_CITIZEN }

    private Teaching() {
    }

    public static Result teach(VillageState v, CitizenRecord teacher, CitizenRecord student, ConceptId concept, long now) {
        if (teacher.id().equals(student.id())) {
            return Result.SAME_CITIZEN;
        }
        if (!teacher.alive() || !student.alive()) {
            return Result.NOT_ALIVE;
        }
        Optional<KnowledgeEntry> entry = teacher.knowledge().entry(concept);
        if (entry.isEmpty()) {
            return Result.TEACHER_IGNORANT;
        }
        if (entry.get().sourceObservation() == null) {
            return Result.NO_EVIDENCE;
        }
        int confidence = entry.get().confidence() * (50 + teacher.curiosity() / 2) / 100;
        if (!student.knowledge().receiveTeaching(entry.get(), confidence, now)) {
            return Result.ALREADY_KNOWN;
        }
        v.log("KNOWLEDGE_TAUGHT", now, null, teacher.id(), student.id(), entry.get().sourceObservation(), null,
                Provenance.TEACHING, "concept", concept.name(), "teacherState", entry.get().state().name(),
                "studentState", student.knowledge().state(concept).name());
        return Result.TAUGHT;
    }
}
