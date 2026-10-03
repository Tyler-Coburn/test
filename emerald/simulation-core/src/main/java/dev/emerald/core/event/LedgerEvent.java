package dev.emerald.core.event;

import dev.emerald.core.EmeraldConstants;
import dev.emerald.core.data.Data;
import dev.emerald.core.world.Pos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One important, append-only history event with causation and provenance.
 *
 * @param causationId   the event or observation that directly caused this one
 * @param correlationId groups events of one story (e.g. one experiment)
 * @param payload       small string key/values; never large blobs
 */
public record LedgerEvent(
        UUID eventId,
        String type,
        long gameTime,
        String dimension,
        Pos pos,
        UUID actor,
        UUID subject,
        UUID causationId,
        UUID correlationId,
        Provenance provenance,
        int schemaVersion,
        Map<String, String> payload
) {
    public LedgerEvent {
        payload = Map.copyOf(payload);
    }

    public String summary() {
        StringBuilder sb = new StringBuilder(type).append(" t=").append(gameTime).append(" [").append(provenance).append(']');
        payload.forEach((k, v) -> sb.append(' ').append(k).append('=').append(v));
        return sb.toString();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        Data.putUuid(m, "id", eventId);
        m.put("type", type);
        m.put("time", gameTime);
        if (dimension != null) {
            m.put("dim", dimension);
        }
        if (pos != null) {
            m.put("pos", pos.toMap());
        }
        Data.putUuid(m, "actor", actor);
        Data.putUuid(m, "subject", subject);
        Data.putUuid(m, "cause", causationId);
        Data.putUuid(m, "corr", correlationId);
        m.put("prov", provenance.name());
        m.put("schema", schemaVersion);
        Map<String, Object> p = new LinkedHashMap<>(payload);
        m.put("payload", p);
        return m;
    }

    static LedgerEvent fromMap(Map<String, Object> m) {
        Map<String, String> payload = new LinkedHashMap<>();
        Map<String, Object> p = Data.subOrNull(m, "payload");
        if (p != null) {
            p.forEach((k, v) -> payload.put(k, String.valueOf(v)));
        }
        return new LedgerEvent(
                Data.uuid(m, "id"),
                Data.str(m, "type"),
                Data.l(m, "time"),
                Data.strOr(m, "dim", null),
                Pos.fromMapOrNull(Data.subOrNull(m, "pos")),
                Data.uuidOrNull(m, "actor"),
                Data.uuidOrNull(m, "subject"),
                Data.uuidOrNull(m, "cause"),
                Data.uuidOrNull(m, "corr"),
                Data.enumOr(m, "prov", Provenance.class, Provenance.SYSTEM_OBSERVED),
                Data.iOr(m, "schema", EmeraldConstants.SCHEMA_VERSION),
                payload);
    }
}
