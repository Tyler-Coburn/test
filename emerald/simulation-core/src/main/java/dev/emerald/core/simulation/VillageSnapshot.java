package dev.emerald.core.simulation;

import dev.emerald.core.data.Data;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * What the village last saw of its physical world while loaded. Offline simulation reasons from
 * this, never from invented facts: crop plots, chickens and warehouse stock it actually counted,
 * and the blocks each active project still needed.
 */
public final class VillageSnapshot {
    private Map<String, Integer> warehouse = new TreeMap<>();
    private int cropPlots;
    private int chickens;
    private long takenAt = -1;
    private final Map<UUID, List<Pos>> remainingByProject = new LinkedHashMap<>();

    public void refresh(Map<String, Integer> warehouseContents, int cropPlots, int chickens, long now) {
        this.warehouse = new TreeMap<>(warehouseContents);
        this.cropPlots = cropPlots;
        this.chickens = chickens;
        this.takenAt = now;
    }

    public void setRemaining(UUID projectId, List<Pos> remainingAbsolute) {
        remainingByProject.put(projectId, List.copyOf(remainingAbsolute));
    }

    public void clearRemaining(UUID projectId) {
        remainingByProject.remove(projectId);
    }

    public List<Pos> remaining(UUID projectId) {
        return remainingByProject.getOrDefault(projectId, List.of());
    }

    public int warehouseCount(String item) {
        return warehouse.getOrDefault(item, 0);
    }

    public Map<String, Integer> warehouse() {
        return Map.copyOf(warehouse);
    }

    public int cropPlots() { return cropPlots; }
    public int chickens() { return chickens; }
    public long takenAt() { return takenAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        m.put("warehouse", new LinkedHashMap<String, Object>(warehouse));
        m.put("cropPlots", cropPlots);
        m.put("chickens", chickens);
        m.put("taken", takenAt);
        List<Object> projects = new ArrayList<>();
        remainingByProject.forEach((id, list) -> {
            Map<String, Object> p = Data.map();
            Data.putUuid(p, "project", id);
            p.put("remaining", list.stream().<Object>map(Pos::toMap).toList());
            projects.add(p);
        });
        m.put("projects", projects);
        return m;
    }

    public void loadFrom(Map<String, Object> m) {
        warehouse = new TreeMap<>();
        remainingByProject.clear();
        if (m == null) return;
        Map<String, Object> wh = Data.subOrNull(m, "warehouse");
        if (wh != null) wh.forEach((k, v) -> warehouse.put(k, ((Number) v).intValue()));
        cropPlots = Data.iOr(m, "cropPlots", 0);
        chickens = Data.iOr(m, "chickens", 0);
        takenAt = Data.lOr(m, "taken", -1);
        for (Map<String, Object> p : Data.maps(m, "projects")) {
            remainingByProject.put(Data.uuid(p, "project"), Data.maps(p, "remaining").stream().map(Pos::fromMap).toList());
        }
    }
}
