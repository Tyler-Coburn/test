package dev.emerald.core.construction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A known structure: ordered block list (bottom-up) plus derived material totals. */
public final class Blueprint {
    private final String id;
    private final List<BlueprintBlock> blocks;

    public Blueprint(String id, List<BlueprintBlock> blocks) {
        this.id = id;
        List<BlueprintBlock> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.<BlueprintBlock>comparingInt(b -> b.rel().y())
                .thenComparingInt(b -> b.rel().x())
                .thenComparingInt(b -> b.rel().z()));
        this.blocks = List.copyOf(sorted);
    }

    public String id() {
        return id;
    }

    public List<BlueprintBlock> blocks() {
        return blocks;
    }

    public Map<String, Integer> materials() {
        return materialsOf(blocks);
    }

    public static Map<String, Integer> materialsOf(List<BlueprintBlock> blocks) {
        Map<String, Integer> out = new TreeMap<>();
        for (BlueprintBlock b : blocks) {
            out.merge(b.itemId(), 1, Integer::sum);
        }
        return out;
    }
}
