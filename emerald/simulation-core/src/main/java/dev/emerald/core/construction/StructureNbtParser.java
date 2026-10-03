package dev.emerald.core.construction;

import dev.emerald.core.data.Data;
import dev.emerald.core.data.DataException;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a vanilla structure-block file (already converted from NBT to the neutral map form)
 * into a {@link Blueprint}. Uses only the stable vanilla layout: {@code palette} entries with
 * {@code Name}/{@code Properties}, and {@code blocks} entries with {@code pos} and {@code state}.
 * Air, structure voids and jigsaws are skipped; block entities' contents are ignored.
 */
public final class StructureNbtParser {
    private static final Set<String> SKIPPED = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
            "minecraft:structure_void", "minecraft:jigsaw", "minecraft:structure_block");

    /** Blocks whose item has a different id from the block. */
    private static final Map<String, String> BLOCK_TO_ITEM = Map.of(
            "minecraft:wall_torch", "minecraft:torch",
            "minecraft:redstone_wire", "minecraft:redstone",
            "minecraft:wheat", "minecraft:wheat_seeds",
            "minecraft:farmland", "minecraft:dirt");

    private StructureNbtParser() {
    }

    public static Blueprint parse(String id, Map<String, Object> root) {
        List<Map<String, Object>> palette = Data.maps(root, "palette");
        if (palette.isEmpty()) {
            List<Map<String, Object>> palettes = Data.maps(root, "palettes");
            if (!palettes.isEmpty()) {
                throw new DataException("Multi-palette structures are not supported: " + id);
            }
            throw new DataException("Structure " + id + " has no palette");
        }
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (Map<String, Object> b : Data.maps(root, "blocks")) {
            List<Integer> pos = Data.ints(b, "pos");
            int stateIndex = Data.i(b, "state");
            if (pos.size() != 3 || stateIndex < 0 || stateIndex >= palette.size()) {
                throw new DataException("Malformed block entry in " + id);
            }
            Map<String, Object> state = palette.get(stateIndex);
            String name = Data.str(state, "Name");
            if (SKIPPED.contains(name)) {
                continue;
            }
            Map<String, String> props = new LinkedHashMap<>();
            Map<String, Object> rawProps = Data.subOrNull(state, "Properties");
            if (rawProps != null) {
                rawProps.forEach((k, v) -> props.put(k, String.valueOf(v)));
            }
            // Only the lower half of two-block structures consumes an item.
            if ("upper".equals(props.get("half")) && name.endsWith("_door")) {
                continue;
            }
            blocks.add(new BlueprintBlock(new Pos(pos.get(0), pos.get(1), pos.get(2)), name, props,
                    BLOCK_TO_ITEM.getOrDefault(name, name)));
        }
        if (blocks.isEmpty()) {
            throw new DataException("Structure " + id + " contains no placeable blocks");
        }
        return new Blueprint(id, blocks);
    }
}
