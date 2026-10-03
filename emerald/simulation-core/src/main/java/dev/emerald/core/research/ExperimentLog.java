package dev.emerald.core.research;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every experiment the village has run, passed and failed alike. */
public final class ExperimentLog {
    private final Map<UUID, Experiment> experiments = new LinkedHashMap<>();

    void add(Experiment e) {
        experiments.put(e.id(), e);
    }

    public Optional<Experiment> get(UUID id) {
        return Optional.ofNullable(experiments.get(id));
    }

    public List<Experiment> all() {
        return List.copyOf(experiments.values());
    }

    public List<Experiment> active() {
        return experiments.values().stream().filter(Experiment::isActive).toList();
    }

    public List<Object> toList() {
        return experiments.values().stream().<Object>map(Experiment::toMap).toList();
    }

    public void loadFrom(List<Map<String, Object>> list) {
        experiments.clear();
        for (Map<String, Object> m : list) {
            add(Experiment.fromMap(m));
        }
    }
}
