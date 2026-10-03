package dev.emerald.core.construction;

import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Active construction projects plus the register of completed buildings. */
public final class ConstructionRegistry {
    private final Map<UUID, ConstructionProject> projects = new LinkedHashMap<>();
    private final List<Building> buildings = new ArrayList<>();

    public ConstructionProject start(String blueprintId, Pos origin, UUID builder, long now) {
        ConstructionProject p = new ConstructionProject(UUID.randomUUID(), blueprintId, origin, builder, now);
        projects.put(p.id(), p);
        return p;
    }

    public Optional<ConstructionProject> get(UUID id) {
        return Optional.ofNullable(projects.get(id));
    }

    public Optional<ConstructionProject> activeFor(UUID builder) {
        return projects.values().stream()
                .filter(p -> p.state() == ConstructionProject.State.ACTIVE && builder.equals(p.builder()))
                .findFirst();
    }

    public List<ConstructionProject> projects() {
        return List.copyOf(projects.values());
    }

    public Building register(Building building) {
        buildings.add(building);
        return building;
    }

    public List<Building> buildings() {
        return Collections.unmodifiableList(buildings);
    }

    public int totalBeds() {
        return buildings.stream().mapToInt(Building::beds).sum();
    }

    public List<Object> projectsToList() {
        return projects.values().stream().<Object>map(ConstructionProject::toMap).toList();
    }

    public List<Object> buildingsToList() {
        return buildings.stream().<Object>map(Building::toMap).toList();
    }

    public void loadFrom(List<Map<String, Object>> projectList, List<Map<String, Object>> buildingList) {
        projects.clear();
        buildings.clear();
        for (Map<String, Object> m : projectList) {
            ConstructionProject p = ConstructionProject.fromMap(m);
            projects.put(p.id(), p);
        }
        for (Map<String, Object> m : buildingList) {
            buildings.add(Building.fromMap(m));
        }
    }
}
