package dev.emerald.core.research;

import dev.emerald.core.data.Data;

import java.util.Map;
import java.util.UUID;

/** An explicit open problem, citing the observation (if any) that proved it exists. */
public final class ResearchProblem {
    public enum Status { OPEN, RESEARCHING, RESOLVED }

    private final UUID id;
    private final ProblemType type;
    private final long openedAt;
    private final UUID evidence;
    private Status status = Status.OPEN;
    private long resolvedAt = -1;
    private String note = "";

    public ResearchProblem(UUID id, ProblemType type, long openedAt, UUID evidence) {
        this.id = id;
        this.type = type;
        this.openedAt = openedAt;
        this.evidence = evidence;
    }

    public UUID id() { return id; }
    public ProblemType type() { return type; }
    public long openedAt() { return openedAt; }
    public UUID evidence() { return evidence; }
    public Status status() { return status; }
    public long resolvedAt() { return resolvedAt; }
    public String note() { return note; }

    public boolean isActive() {
        return status != Status.RESOLVED;
    }

    void setStatus(Status status, long now, String note) {
        this.status = status;
        this.note = note == null ? "" : note;
        if (status == Status.RESOLVED) {
            this.resolvedAt = now;
        }
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        Data.putUuid(m, "id", id);
        m.put("type", type.name());
        m.put("opened", openedAt);
        Data.putUuid(m, "evidence", evidence);
        m.put("status", status.name());
        m.put("resolved", resolvedAt);
        m.put("note", note);
        return m;
    }

    static ResearchProblem fromMap(Map<String, Object> m) {
        ResearchProblem p = new ResearchProblem(Data.uuid(m, "id"), Data.enumOf(m, "type", ProblemType.class),
                Data.l(m, "opened"), Data.uuidOrNull(m, "evidence"));
        p.status = Data.enumOf(m, "status", Status.class);
        p.resolvedAt = Data.lOr(m, "resolved", -1);
        p.note = Data.strOr(m, "note", "");
        return p;
    }
}
