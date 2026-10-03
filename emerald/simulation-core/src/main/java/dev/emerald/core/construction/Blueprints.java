package dev.emerald.core.construction;

import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in blueprints. The preferred source for the hut is {@code data/emerald/structure/hut.nbt}
 * (authored in game with a structure block, see docs/MANUAL_STEPS.md). Until that file exists the
 * builder uses this original, code-defined 5x5 hut so the construction loop still runs.
 */
public final class Blueprints {
    public static final String HUT = "emerald:hut";

    private Blueprints() {
    }

    /**
     * 5x5 footprint, origin at the north-west floor corner (y=0 is the first wall layer, built on
     * existing ground). Two-high oak plank walls with a 1x2 doorway on the south side, flat plank roof.
     * 55 oak planks total.
     */
    public static Blueprint fallbackHut() {
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
                    boolean doorway = x == 2 && z == 4;
                    if (edge && !doorway) {
                        blocks.add(BlueprintBlock.simple(new Pos(x, y, z), ItemIds.OAK_PLANKS));
                    }
                }
            }
        }
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(BlueprintBlock.simple(new Pos(x, 2, z), ItemIds.OAK_PLANKS));
            }
        }
        return new Blueprint(HUT, blocks);
    }
}
