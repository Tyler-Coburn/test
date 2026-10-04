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
    /** chicken_collector Mk I: a hopper in the pen floor feeding a chest directly below it. */
    public static final String COLLECTOR = "emerald:chicken_collector";
    public static final int HUT_BEDS = 2;
    /** chicken_collector Mk II family: a hopper under every pen cell, chained into one chest. Id suffix "WxD". */
    public static final String FULL_COLLECTOR_PREFIX = "emerald:chicken_collector_full_";

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

    public static String fullCollectorId(int width, int depth) {
        return FULL_COLLECTOR_PREFIX + width + "x" + depth;
    }

    public static boolean isCollector(String blueprintId) {
        return blueprintId.equals(COLLECTOR) || blueprintId.startsWith(FULL_COLLECTOR_PREFIX);
    }

    /**
     * Hoppers under every cell of a {@code width x depth} pen floor (origin = the floor's min corner).
     * Cells feed toward the centre column, the centre column toward the centre cell, and the centre
     * hopper feeds down into one chest. Plain vanilla hopper chaining.
     */
    public static Blueprint fullCollector(int width, int depth) {
        int cx = (width - 1) / 2;
        int cz = (depth - 1) / 2;
        List<BlueprintBlock> blocks = new ArrayList<>();
        blocks.add(BlueprintBlock.simple(new Pos(cx, -1, cz), ItemIds.CHEST));
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                String facing = x < cx ? "east" : x > cx ? "west" : z < cz ? "south" : z > cz ? "north" : "down";
                blocks.add(new BlueprintBlock(new Pos(x, 0, z), ItemIds.HOPPER, java.util.Map.of("facing", facing), ItemIds.HOPPER));
            }
        }
        return new Blueprint(fullCollectorId(width, depth), blocks);
    }

    /** Parses "..._WxD"; null if the id is not a full-collector id. */
    public static Blueprint fromId(String id) {
        if (!id.startsWith(FULL_COLLECTOR_PREFIX)) return null;
        String[] wd = id.substring(FULL_COLLECTOR_PREFIX.length()).split("x");
        try {
            int w = Integer.parseInt(wd[0]);
            int d = Integer.parseInt(wd[1]);
            return w > 0 && d > 0 && w <= 9 && d <= 9 ? fullCollector(w, d) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Where a collector building's output chest is. */
    public static Pos collectorChest(Building b) {
        if (b.type().equals(COLLECTOR)) {
            return b.origin().below();
        }
        Blueprint bp = fromId(b.type());
        if (bp == null) return null;
        Pos rel = bp.blocks().get(0).rel();
        return b.origin().offset(rel);
    }

    /** Origin is the hopper position (pen floor). The chest goes underneath; built bottom-up. */
    public static Blueprint collector() {
        return new Blueprint(COLLECTOR, List.of(
                BlueprintBlock.simple(new Pos(0, -1, 0), ItemIds.CHEST),
                new BlueprintBlock(new Pos(0, 0, 0), ItemIds.HOPPER, java.util.Map.of("facing", "down"), ItemIds.HOPPER)));
    }
}
