package dev.emerald.core.construction;

import dev.emerald.core.data.Data;
import dev.emerald.core.world.Pos;

import java.util.Map;
import java.util.UUID;

/** A finished, registered building. {@code beds} is housing capacity used by the HOMELESS predicate. */
public record Building(UUID id, String type, Pos origin, long completedAt, UUID projectId, int beds) {
    Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        Data.putUuid(m, "id", id);
        m.put("type", type);
        m.put("origin", origin.toMap());
        m.put("completed", completedAt);
        Data.putUuid(m, "project", projectId);
        m.put("beds", beds);
        return m;
    }

    static Building fromMap(Map<String, Object> m) {
        return new Building(Data.uuid(m, "id"), Data.str(m, "type"), Pos.fromMap(Data.sub(m, "origin")),
                Data.l(m, "completed"), Data.uuidOrNull(m, "project"), Data.iOr(m, "beds", 0));
    }
}
