package dev.emerald.core.research;

import dev.emerald.core.data.Data;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.observe.ObservationType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A testable prediction. It is never a fact: only the experiment engine, reading the bus, decides.
 *
 * @param target   concept whose state the test decides
 * @param expected observation type that counts as a pass
 * @param origin   FALLBACK (hand-authored table) or AI (validated model proposal)
 */
public record Hypothesis(
        UUID id,
        UUID author,
        String statement,
        List<ConceptId> concepts,
        ConceptId target,
        ObservationType expected,
        UUID problemId,
        Origin origin,
        long createdAt
) {
    public enum Origin { FALLBACK, AI }

    public Hypothesis {
        concepts = List.copyOf(concepts);
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        Data.putUuid(m, "id", id);
        Data.putUuid(m, "author", author);
        m.put("statement", statement);
        m.put("concepts", concepts.stream().<Object>map(Enum::name).toList());
        m.put("target", target.name());
        m.put("expected", expected.name());
        Data.putUuid(m, "problem", problemId);
        m.put("origin", origin.name());
        m.put("created", createdAt);
        return m;
    }

    static Hypothesis fromMap(Map<String, Object> m) {
        return new Hypothesis(Data.uuid(m, "id"), Data.uuidOrNull(m, "author"), Data.str(m, "statement"),
                Data.strings(m, "concepts").stream().map(ConceptId::valueOf).toList(),
                Data.enumOf(m, "target", ConceptId.class), Data.enumOf(m, "expected", ObservationType.class),
                Data.uuidOrNull(m, "problem"), Data.enumOr(m, "origin", Origin.class, Origin.FALLBACK),
                Data.l(m, "created"));
    }
}
