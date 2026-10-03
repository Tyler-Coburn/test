package dev.emerald.core.knowledge;

import dev.emerald.core.observe.ObservationBus;
import dev.emerald.core.observe.WorldObservation;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One holder's (citizen's) knowledge, with the transition rules enforced in one place:
 * <ul>
 *   <li>OBSERVED requires an authentic bus observation, or a teaching transfer that cites one.</li>
 *   <li>TESTED_TRUE / TESTED_FALSE require an {@link ExperimentOutcome}.</li>
 *   <li>ADOPTED requires TESTED_TRUE first.</li>
 *   <li>Negative results are retained; a later failure never erases a confirmed truth.</li>
 * </ul>
 */
public final class KnowledgeBook {
    private final Map<ConceptId, KnowledgeEntry> entries = new EnumMap<>(ConceptId.class);

    public KnowState state(ConceptId concept) {
        KnowledgeEntry e = entries.get(concept);
        return e == null ? KnowState.UNKNOWN : e.state();
    }

    public Optional<KnowledgeEntry> entry(ConceptId concept) {
        return Optional.ofNullable(entries.get(concept));
    }

    public Collection<KnowledgeEntry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** Concepts at OBSERVED or beyond (including TESTED_FALSE: negative knowledge is knowledge). */
    public Set<ConceptId> known() {
        Set<ConceptId> out = EnumSet.noneOf(ConceptId.class);
        entries.forEach((c, e) -> {
            if (e.state() != KnowState.UNKNOWN) {
                out.add(c);
            }
        });
        return out;
    }

    /** Witnessing: learns every concept the observation evidences. Returns concepts newly learned. */
    public Set<ConceptId> witness(ObservationBus bus, WorldObservation obs) {
        if (!bus.isAuthentic(obs)) {
            throw new IllegalArgumentException("Only ObservationBus observations can be witnessed");
        }
        Set<ConceptId> learned = EnumSet.noneOf(ConceptId.class);
        for (ConceptId c : ConceptCatalog.witnessedBy(obs)) {
            if (state(c) == KnowState.UNKNOWN) {
                entries.put(c, new KnowledgeEntry(c, KnowState.OBSERVED, obs.id(), null, obs.gameTime(), 60,
                        KnowledgeSource.WITNESSED));
                learned.add(c);
            }
        }
        return learned;
    }

    /**
     * Receives a taught concept. The student only ever reaches OBSERVED this way, and only if the
     * teacher's entry cites a source observation. Returns true if the student's state changed.
     */
    public boolean receiveTeaching(KnowledgeEntry teacherEntry, int confidence, long gameTime) {
        if (teacherEntry.sourceObservation() == null || teacherEntry.state() == KnowState.UNKNOWN) {
            return false;
        }
        if (state(teacherEntry.concept()) != KnowState.UNKNOWN) {
            return false;
        }
        entries.put(teacherEntry.concept(), new KnowledgeEntry(teacherEntry.concept(), KnowState.OBSERVED,
                teacherEntry.sourceObservation(), null, gameTime, clamp(confidence), KnowledgeSource.TAUGHT));
        return true;
    }

    /** Marks a concept as under hypothesis. Tested knowledge is never downgraded. */
    public void hypothesize(ConceptId concept, long gameTime) {
        KnowState s = state(concept);
        if (s == KnowState.UNKNOWN || s == KnowState.OBSERVED) {
            KnowledgeEntry prev = entries.get(concept);
            entries.put(concept, new KnowledgeEntry(concept, KnowState.HYPOTHESIS,
                    prev == null ? null : prev.sourceObservation(), null, gameTime,
                    prev == null ? 30 : prev.confidence(), KnowledgeSource.HYPOTHESIZED));
        }
    }

    /** Applies an experiment result. Returns the new state. */
    public KnowState applyOutcome(ExperimentOutcome outcome) {
        ConceptId c = outcome.concept();
        KnowState current = state(c);
        if (outcome.passed()) {
            if (current != KnowState.ADOPTED) {
                entries.put(c, new KnowledgeEntry(c, KnowState.TESTED_TRUE, outcome.evidence().id(),
                        outcome.experimentId(), outcome.gameTime(), 90, KnowledgeSource.EXPERIMENT));
            }
        } else if (!current.isConfirmedTrue()) {
            entries.put(c, new KnowledgeEntry(c, KnowState.TESTED_FALSE, outcome.evidence().id(),
                    outcome.experimentId(), outcome.gameTime(), 70, KnowledgeSource.EXPERIMENT));
        }
        return state(c);
    }

    /** TESTED_TRUE -> ADOPTED. Returns false if the concept was not tested true. */
    public boolean adopt(ConceptId concept, long gameTime) {
        KnowledgeEntry e = entries.get(concept);
        if (e == null || e.state() != KnowState.TESTED_TRUE) {
            return false;
        }
        entries.put(concept, new KnowledgeEntry(concept, KnowState.ADOPTED, e.sourceObservation(),
                e.experimentId(), gameTime, e.confidence(), KnowledgeSource.VILLAGE_ADOPTION));
        return true;
    }

    public List<Object> toList() {
        return entries.values().stream().<Object>map(KnowledgeEntry::toMap).toList();
    }

    public void loadFrom(List<Map<String, Object>> list) {
        entries.clear();
        for (Map<String, Object> m : list) {
            KnowledgeEntry e = KnowledgeEntry.fromMap(m);
            entries.put(e.concept(), e);
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    /** Lookup helper used by inspection commands. */
    public static String describe(KnowledgeEntry e) {
        UUID src = e.sourceObservation();
        return e.concept() + "=" + e.state() + " (" + e.source() + ", conf " + e.confidence()
                + (src != null ? ", evidence " + src.toString().substring(0, 8) : "") + ")";
    }
}
