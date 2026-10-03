package dev.emerald.core.knowledge;

import dev.emerald.core.data.Data;

import java.util.Map;
import java.util.UUID;

/**
 * A citizen's current understanding of one concept.
 *
 * @param sourceObservation bus observation that justifies the state (null only for HYPOTHESIS)
 * @param experimentId      experiment that produced a TESTED_* state, if any
 * @param confidence        0..100
 */
public record KnowledgeEntry(
        ConceptId concept,
        KnowState state,
        UUID sourceObservation,
        UUID experimentId,
        long gameTime,
        int confidence,
        KnowledgeSource source
) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        m.put("concept", concept.name());
        m.put("state", state.name());
        Data.putUuid(m, "obs", sourceObservation);
        Data.putUuid(m, "exp", experimentId);
        m.put("time", gameTime);
        m.put("confidence", confidence);
        m.put("source", source.name());
        return m;
    }

    static KnowledgeEntry fromMap(Map<String, Object> m) {
        return new KnowledgeEntry(
                Data.enumOf(m, "concept", ConceptId.class),
                Data.enumOf(m, "state", KnowState.class),
                Data.uuidOrNull(m, "obs"),
                Data.uuidOrNull(m, "exp"),
                Data.l(m, "time"),
                Data.iOr(m, "confidence", 0),
                Data.enumOr(m, "source", KnowledgeSource.class, KnowledgeSource.WITNESSED));
    }
}
