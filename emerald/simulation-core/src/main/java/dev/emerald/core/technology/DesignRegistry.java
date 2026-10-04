package dev.emerald.core.technology;

import dev.emerald.core.knowledge.ConceptId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Village-level designs. Owned by VillageState so they survive the death of their inventor. */
public final class DesignRegistry {
    public static final String CHICKEN_COLLECTOR = "chicken_collector";

    private final List<DesignRevision> revisions = new ArrayList<>();

    public DesignRevision record(String designId, Map<String, Integer> cost, int measuredOutput, int failures,
                                 boolean adopted, UUID inventor, UUID experimentId, List<ConceptId> requires,
                                 String blueprintId, long now) {
        int next = (int) revisions.stream().filter(r -> r.designId().equals(designId)).count() + 1;
        DesignRevision rev = new DesignRevision(designId, next, cost, measuredOutput, failures, adopted, inventor,
                experimentId, requires, blueprintId, now);
        revisions.add(rev);
        return rev;
    }

    public List<DesignRevision> all() {
        return Collections.unmodifiableList(revisions);
    }

    public List<DesignRevision> of(String designId) {
        return revisions.stream().filter(r -> r.designId().equals(designId)).toList();
    }

    public Optional<DesignRevision> latestAdopted(String designId) {
        DesignRevision best = null;
        for (DesignRevision r : revisions) {
            if (r.designId().equals(designId) && r.adopted() && (best == null || r.revision() > best.revision())) {
                best = r;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Marks a stored revision adopted. Returns false if it does not exist or never passed a test. */
    public boolean adopt(String designId, int revision) {
        for (int i = 0; i < revisions.size(); i++) {
            DesignRevision r = revisions.get(i);
            if (r.designId().equals(designId) && r.revision() == revision) {
                if (r.failures() > 0 && r.measuredOutput() == 0) {
                    return false;
                }
                revisions.set(i, r.withAdopted(true));
                return true;
            }
        }
        return false;
    }

    /** Field-trial outcome for a stored revision. */
    public boolean conclude(String designId, int revision, int output, boolean success) {
        for (int i = 0; i < revisions.size(); i++) {
            DesignRevision r = revisions.get(i);
            if (r.designId().equals(designId) && r.revision() == revision) {
                revisions.set(i, r.withResult(output, success ? 0 : r.failures() + 1, success));
                return true;
            }
        }
        return false;
    }

    public java.util.Optional<DesignRevision> get(String designId, int revision) {
        return revisions.stream().filter(r -> r.designId().equals(designId) && r.revision() == revision).findFirst();
    }

    public java.util.Optional<DesignRevision> inTrial(String designId) {
        return revisions.stream().filter(r -> r.designId().equals(designId) && r.inTrial()).findFirst();
    }

    public List<Object> toList() {
        return revisions.stream().<Object>map(DesignRevision::toMap).toList();
    }

    public void loadFrom(List<Map<String, Object>> list) {
        revisions.clear();
        for (Map<String, Object> m : list) {
            revisions.add(DesignRevision.fromMap(m));
        }
    }
}
