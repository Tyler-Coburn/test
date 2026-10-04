package dev.emerald.core.technology;

import dev.emerald.core.data.Data;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Village-level procedures with success metrics: deterministic steps the village has run before,
 * not generated code. A procedure counts as "reliable" after repeated successes.
 */
public final class SkillLibrary {
    public record Stat(String id, int attempts, int successes, long totalTicks) {
        public double successRate() {
            return attempts == 0 ? 0 : (double) successes / attempts;
        }

        public long averageTicks() {
            return successes == 0 ? 0 : totalTicks / successes;
        }

        public boolean reliable() {
            return successes >= 3 && successRate() >= 0.75;
        }
    }

    private final Map<String, Stat> stats = new TreeMap<>();

    public void record(String skillId, boolean success, long ticks) {
        Stat s = stats.getOrDefault(skillId, new Stat(skillId, 0, 0, 0));
        stats.put(skillId, new Stat(skillId, s.attempts() + 1, s.successes() + (success ? 1 : 0),
                s.totalTicks() + (success ? Math.max(0, ticks) : 0)));
    }

    public Stat get(String skillId) {
        return stats.getOrDefault(skillId, new Stat(skillId, 0, 0, 0));
    }

    public List<Stat> all() {
        return List.copyOf(stats.values());
    }

    public List<Object> toList() {
        return stats.values().stream().<Object>map(s -> {
            Map<String, Object> m = Data.map();
            m.put("id", s.id());
            m.put("attempts", s.attempts());
            m.put("successes", s.successes());
            m.put("ticks", s.totalTicks());
            return m;
        }).toList();
    }

    public void loadFrom(List<Map<String, Object>> list) {
        stats.clear();
        for (Map<String, Object> m : list) {
            String id = Data.str(m, "id");
            stats.put(id, new Stat(id, Data.i(m, "attempts"), Data.i(m, "successes"), Data.lOr(m, "ticks", 0)));
        }
    }
}
