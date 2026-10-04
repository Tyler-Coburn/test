package dev.emerald.core.technology;

import dev.emerald.core.data.Data;
import dev.emerald.core.knowledge.ConceptId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One engineering revision of a design (e.g. chicken_collector Mk I). Separate from the scientific
 * concept: HOPPER_PULLS_ITEM stays TESTED_TRUE while Mk II changes cost and reliability.
 *
 * @param measuredOutput items collected during the qualifying test
 * @param blueprintId    structure a builder places for this design, if any
 */
public record DesignRevision(
        String designId,
        int revision,
        Map<String, Integer> cost,
        int measuredOutput,
        int failures,
        boolean adopted,
        UUID inventor,
        UUID experimentId,
        List<ConceptId> requires,
        String blueprintId,
        long createdAt
) {
    public DesignRevision {
        cost = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(cost));
        requires = List.copyOf(requires);
    }

    public String label() {
        return designId + " Mk " + roman(revision) + (adopted ? " [adopted]" : failures > 0 ? " [failed]" : " [prototype]");
    }

    DesignRevision withAdopted(boolean value) {
        return new DesignRevision(designId, revision, cost, measuredOutput, failures, value, inventor,
                experimentId, requires, blueprintId, createdAt);
    }

    DesignRevision withResult(int output, int failures, boolean adopted) {
        return new DesignRevision(designId, revision, cost, output, failures, adopted, inventor,
                experimentId, requires, blueprintId, createdAt);
    }

    /** A prototype awaiting its field trial: not adopted, not failed. */
    public boolean inTrial() {
        return !adopted && failures == 0 && blueprintId != null;
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n > 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        m.put("design", designId);
        m.put("revision", revision);
        m.put("cost", new LinkedHashMap<String, Object>(cost));
        m.put("output", measuredOutput);
        m.put("failures", failures);
        m.put("adopted", adopted);
        Data.putUuid(m, "inventor", inventor);
        Data.putUuid(m, "experiment", experimentId);
        m.put("requires", requires.stream().<Object>map(Enum::name).toList());
        if (blueprintId != null) m.put("blueprint", blueprintId);
        m.put("created", createdAt);
        return m;
    }

    static DesignRevision fromMap(Map<String, Object> m) {
        Map<String, Integer> cost = new LinkedHashMap<>();
        Map<String, Object> rawCost = Data.subOrNull(m, "cost");
        if (rawCost != null) {
            rawCost.forEach((k, v) -> cost.put(k, ((Number) v).intValue()));
        }
        return new DesignRevision(Data.str(m, "design"), Data.i(m, "revision"), cost, Data.iOr(m, "output", 0),
                Data.iOr(m, "failures", 0), Data.b(m, "adopted", false), Data.uuidOrNull(m, "inventor"),
                Data.uuidOrNull(m, "experiment"),
                Data.strings(m, "requires").stream().map(ConceptId::valueOf).toList(),
                Data.strOr(m, "blueprint", null), Data.l(m, "created"));
    }
}
