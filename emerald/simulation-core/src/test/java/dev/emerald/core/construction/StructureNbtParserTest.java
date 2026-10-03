package dev.emerald.core.construction;

import dev.emerald.core.data.DataException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructureNbtParserTest {
    static Map<String, Object> block(int x, int y, int z, int state) {
        return Map.of("pos", List.of(x, y, z), "state", state);
    }

    @Test
    void parsesVanillaStructureLayoutSkippingAir() {
        Map<String, Object> root = Map.of(
                "size", List.of(2, 2, 1),
                "palette", List.of(
                        Map.of("Name", "minecraft:air"),
                        Map.of("Name", "minecraft:oak_planks"),
                        Map.of("Name", "minecraft:oak_door", "Properties", Map.of("half", "lower", "facing", "south")),
                        Map.of("Name", "minecraft:oak_door", "Properties", Map.of("half", "upper", "facing", "south"))),
                "blocks", List.of(block(0, 0, 0, 1), block(1, 0, 0, 2), block(1, 1, 0, 3), block(0, 1, 0, 0)));

        Blueprint bp = StructureNbtParser.parse("emerald:test", root);

        assertEquals(2, bp.blocks().size());
        assertEquals(Map.of("minecraft:oak_planks", 1, "minecraft:oak_door", 1), bp.materials());
        BlueprintBlock door = bp.blocks().stream().filter(b -> b.blockId().endsWith("door")).findFirst().orElseThrow();
        assertEquals("south", door.properties().get("facing"));
    }

    @Test
    void rejectsEmptyOrMalformedStructures() {
        assertThrows(DataException.class, () -> StructureNbtParser.parse("x", Map.of("blocks", List.of())));
        Map<String, Object> bad = Map.of("palette", List.of(Map.of("Name", "minecraft:stone")),
                "blocks", List.of(block(0, 0, 0, 5)));
        assertThrows(DataException.class, () -> StructureNbtParser.parse("x", bad));
    }

    @Test
    void fallbackHutIsFiveByFive() {
        Blueprint hut = Blueprints.fallbackHut();
        assertEquals(55, hut.materials().get("minecraft:oak_planks"));
        assertTrue(hut.blocks().stream().allMatch(b -> b.rel().x() >= 0 && b.rel().x() < 5 && b.rel().z() >= 0 && b.rel().z() < 5));
        assertEquals(0, hut.blocks().get(0).rel().y(), "built bottom-up");
    }
}
