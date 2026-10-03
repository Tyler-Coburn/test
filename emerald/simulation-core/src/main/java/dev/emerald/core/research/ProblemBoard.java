package dev.emerald.core.research;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Open, researching and resolved problems. At most one active problem per type. */
public final class ProblemBoard {
    private final Map<UUID, ResearchProblem> problems = new LinkedHashMap<>();

    /** Opens a problem of {@code type} unless one is already active; returns the active one. */
    public ResearchProblem open(ProblemType type, UUID evidence, long now) {
        return active(type).orElseGet(() -> {
            ResearchProblem p = new ResearchProblem(UUID.randomUUID(), type, now, evidence);
            problems.put(p.id(), p);
            return p;
        });
    }

    public Optional<ResearchProblem> active(ProblemType type) {
        return problems.values().stream().filter(p -> p.type() == type && p.isActive()).findFirst();
    }

    public Optional<ResearchProblem> get(UUID id) {
        return Optional.ofNullable(problems.get(id));
    }

    public List<ResearchProblem> all() {
        return List.copyOf(problems.values());
    }

    /** Latest resolution time for the type, or -1. */
    public long lastResolved(ProblemType type) {
        return problems.values().stream()
                .filter(p -> p.type() == type && p.status() == ResearchProblem.Status.RESOLVED)
                .mapToLong(ResearchProblem::resolvedAt).max().orElse(-1);
    }

    public void setStatus(UUID id, ResearchProblem.Status status, long now, String note) {
        ResearchProblem p = problems.get(id);
        if (p != null) {
            p.setStatus(status, now, note);
        }
    }

    public List<Object> toList() {
        return problems.values().stream().<Object>map(ResearchProblem::toMap).toList();
    }

    public void loadFrom(List<Map<String, Object>> list) {
        problems.clear();
        for (Map<String, Object> m : list) {
            ResearchProblem p = ResearchProblem.fromMap(m);
            problems.put(p.id(), p);
        }
    }
}
