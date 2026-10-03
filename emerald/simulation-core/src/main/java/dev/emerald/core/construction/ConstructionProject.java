package dev.emerald.core.construction;

import dev.emerald.core.data.Data;
import dev.emerald.core.world.Pos;

import java.util.Map;
import java.util.UUID;

/** A blueprint being built at a site by one builder. Progress is re-derived from the world each step. */
public final class ConstructionProject {
    public enum State { ACTIVE, COMPLETE, ABANDONED }

    private final UUID id;
    private final String blueprintId;
    private final Pos origin;
    private UUID builder;
    private final long createdAt;
    private State state = State.ACTIVE;
    private int placed;
    private long completedAt = -1;

    public ConstructionProject(UUID id, String blueprintId, Pos origin, UUID builder, long createdAt) {
        this.id = id;
        this.blueprintId = blueprintId;
        this.origin = origin;
        this.builder = builder;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public String blueprintId() { return blueprintId; }
    public Pos origin() { return origin; }
    public UUID builder() { return builder; }
    public long createdAt() { return createdAt; }
    public State state() { return state; }
    public int placed() { return placed; }
    public long completedAt() { return completedAt; }

    public void setBuilder(UUID builder) { this.builder = builder; }
    public void notePlaced() { placed++; }

    public void complete(long now) {
        state = State.COMPLETE;
        completedAt = now;
    }

    public void abandon() {
        state = State.ABANDONED;
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        Data.putUuid(m, "id", id);
        m.put("blueprint", blueprintId);
        m.put("origin", origin.toMap());
        Data.putUuid(m, "builder", builder);
        m.put("created", createdAt);
        m.put("state", state.name());
        m.put("placed", placed);
        m.put("completed", completedAt);
        return m;
    }

    static ConstructionProject fromMap(Map<String, Object> m) {
        ConstructionProject p = new ConstructionProject(Data.uuid(m, "id"), Data.str(m, "blueprint"),
                Pos.fromMap(Data.sub(m, "origin")), Data.uuidOrNull(m, "builder"), Data.l(m, "created"));
        p.state = Data.enumOf(m, "state", State.class);
        p.placed = Data.iOr(m, "placed", 0);
        p.completedAt = Data.lOr(m, "completed", -1);
        return p;
    }
}
