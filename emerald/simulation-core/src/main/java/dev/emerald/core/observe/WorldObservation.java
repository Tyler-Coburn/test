package dev.emerald.core.observe;

import dev.emerald.core.data.Data;
import dev.emerald.core.world.Pos;

import java.util.Map;
import java.util.UUID;

/**
 * One server-observed physical fact. Instances are only created by {@link ObservationBus#record},
 * which is only called from server-side world adapters.
 *
 * @param itemId   registry id of the item involved, or null
 * @param witness  citizen who saw it, or null
 * @param causedBy earlier observation this one follows from (egg spawn -> hopper pull), or null
 */
public record WorldObservation(
        UUID id,
        ObservationType type,
        String dimension,
        Pos pos,
        String itemId,
        UUID witness,
        long gameTime,
        UUID causedBy
) {
    public boolean isItem(String id) {
        return id.equals(itemId);
    }

    public String summary() {
        return type + (itemId != null ? " " + itemId : "") + " @" + pos + " t=" + gameTime;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        Data.putUuid(m, "id", id);
        m.put("type", type.name());
        m.put("dim", dimension);
        m.put("pos", pos.toMap());
        if (itemId != null) {
            m.put("item", itemId);
        }
        Data.putUuid(m, "witness", witness);
        m.put("time", gameTime);
        Data.putUuid(m, "causedBy", causedBy);
        return m;
    }

    static WorldObservation fromMap(Map<String, Object> m) {
        return new WorldObservation(
                Data.uuid(m, "id"),
                Data.enumOf(m, "type", ObservationType.class),
                Data.strOr(m, "dim", "minecraft:overworld"),
                Pos.fromMap(Data.sub(m, "pos")),
                Data.strOr(m, "item", null),
                Data.uuidOrNull(m, "witness"),
                Data.l(m, "time"),
                Data.uuidOrNull(m, "causedBy"));
    }
}
