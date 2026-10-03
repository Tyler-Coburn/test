package dev.emerald.core.construction;

import dev.emerald.core.world.Pos;

import java.util.Map;

/**
 * One block of a blueprint.
 *
 * @param rel        offset from the blueprint origin
 * @param blockId    block registry id to place
 * @param properties block-state properties (facing, half...) to apply when placing
 * @param itemId     item consumed to place it
 */
public record BlueprintBlock(Pos rel, String blockId, Map<String, String> properties, String itemId) {
    public BlueprintBlock {
        properties = Map.copyOf(properties);
    }

    public static BlueprintBlock simple(Pos rel, String blockId) {
        return new BlueprintBlock(rel, blockId, Map.of(), blockId);
    }
}
