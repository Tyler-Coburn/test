package dev.emerald.core.education;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.KnowState;
import dev.emerald.core.knowledge.KnowledgeEntry;
import dev.emerald.core.knowledge.KnowledgeSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Institutional memory. Writings can only be produced from a citizen's own tested knowledge
 * (experiment or document), so a book cannot be forged from hearsay or model output.
 */
public final class Library {
    private final List<Writing> writings = new ArrayList<>();

    /** Writes the author's tested understanding of {@code concept}. Empty if they have nothing writable. */
    public Optional<Writing> write(CitizenRecord author, ConceptId concept, long now) {
        Optional<KnowledgeEntry> e = author.knowledge().entry(concept);
        if (e.isEmpty() || !isWritable(e.get())) {
            return Optional.empty();
        }
        KnowState state = e.get().state() == KnowState.ADOPTED ? KnowState.TESTED_TRUE : e.get().state();
        if (find(concept).filter(w -> w.state() == state).isPresent()) {
            return Optional.empty();
        }
        Writing w = new Writing(UUID.randomUUID(), concept, state, e.get().sourceObservation(),
                e.get().experimentId(), author.id(), now);
        writings.add(w);
        return Optional.of(w);
    }

    public static boolean isWritable(KnowledgeEntry e) {
        return e.state().isTested() && e.sourceObservation() != null
                && (e.source() == KnowledgeSource.EXPERIMENT || e.source() == KnowledgeSource.DOCUMENT
                || e.source() == KnowledgeSource.VILLAGE_ADOPTION);
    }

    /** Best writing on a concept: a TESTED_TRUE one if any, else the latest. */
    public Optional<Writing> find(ConceptId concept) {
        Writing best = null;
        for (Writing w : writings) {
            if (w.concept() != concept) continue;
            if (best == null || w.state() == KnowState.TESTED_TRUE || best.state() != KnowState.TESTED_TRUE) {
                best = w;
            }
        }
        return Optional.ofNullable(best);
    }

    public List<Writing> all() {
        return Collections.unmodifiableList(writings);
    }

    public List<Object> toList() {
        return writings.stream().<Object>map(Writing::toMap).toList();
    }

    public void loadFrom(List<Map<String, Object>> list) {
        writings.clear();
        for (Map<String, Object> m : list) {
            writings.add(Writing.fromMap(m));
        }
    }
}
