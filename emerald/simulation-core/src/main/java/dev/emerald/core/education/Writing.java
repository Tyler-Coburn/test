package dev.emerald.core.education;

import dev.emerald.core.data.Data;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.KnowState;

import java.util.Map;
import java.util.UUID;

/**
 * A book in the village library: a tested result written down by someone who tested it (or read
 * it), citing the observation that proved it. Authoritative documentation for readers.
 */
public record Writing(UUID id, ConceptId concept, KnowState state, UUID sourceObservation, UUID experimentId,
                      UUID author, long writtenAt) {
    Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        Data.putUuid(m, "id", id);
        m.put("concept", concept.name());
        m.put("state", state.name());
        Data.putUuid(m, "obs", sourceObservation);
        Data.putUuid(m, "exp", experimentId);
        Data.putUuid(m, "author", author);
        m.put("written", writtenAt);
        return m;
    }

    static Writing fromMap(Map<String, Object> m) {
        return new Writing(Data.uuid(m, "id"), Data.enumOf(m, "concept", ConceptId.class),
                Data.enumOf(m, "state", KnowState.class), Data.uuid(m, "obs"), Data.uuidOrNull(m, "exp"),
                Data.uuidOrNull(m, "author"), Data.l(m, "written"));
    }
}
