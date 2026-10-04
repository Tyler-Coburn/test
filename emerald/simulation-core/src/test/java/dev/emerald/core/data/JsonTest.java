package dev.emerald.core.data;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.core.world.Pos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {
    @Test
    void roundTripsTheNeutralTree() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("s", "quote \" and \\ and \n newline");
        m.put("i", 5);
        m.put("l", 9_000_000_000L);
        m.put("b", true);
        m.put("list", List.of(1, 2, Map.of("k", "v")));
        m.put("empty", Map.of());
        assertEquals(m, Json.parseObject(Json.write(m)));
    }

    @Test
    void villageWorldSurvivesJson() {
        VillageWorld w = new VillageWorld(SimulationConfig.DEFAULT);
        w.found("J", "minecraft:overworld", new Pos(1, 2, 3), 77, 5L);
        VillageWorld back = VillageWorld.fromMap(Json.parseObject(Json.write(w.toMap())), SimulationConfig.DEFAULT);
        assertEquals(w.primary().orElseThrow().id(), back.primary().orElseThrow().id());
        assertEquals(6, back.primary().orElseThrow().citizens().size());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(DataException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(DataException.class, () -> Json.parse("[1,2] x"));
    }
}
