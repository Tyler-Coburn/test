package dev.emerald.core.citizen;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Creates, indexes and looks up persistent citizens. Source of truth for who exists. */
public final class CitizenRegistry {
    private final Map<UUID, CitizenRecord> citizens = new LinkedHashMap<>();

    public CitizenRecord add(CitizenRecord record) {
        citizens.put(record.id(), record);
        return record;
    }

    public Optional<CitizenRecord> get(UUID id) {
        return Optional.ofNullable(citizens.get(id));
    }

    public Collection<CitizenRecord> all() {
        return Collections.unmodifiableCollection(citizens.values());
    }

    public List<CitizenRecord> alive() {
        return citizens.values().stream().filter(CitizenRecord::alive).toList();
    }

    public int size() {
        return citizens.size();
    }

    /** First living citizen with the role, in creation order. */
    public Optional<CitizenRecord> firstAlive(Role role) {
        return citizens.values().stream().filter(c -> c.alive() && c.role() == role).findFirst();
    }

    public Optional<CitizenRecord> byBody(UUID bodyUuid) {
        if (bodyUuid == null) {
            return Optional.empty();
        }
        return citizens.values().stream().filter(c -> bodyUuid.equals(c.bodyUuid())).findFirst();
    }

    /** Matches a full UUID, a UUID prefix, or a case-insensitive name. */
    public Optional<CitizenRecord> find(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        List<CitizenRecord> hits = new ArrayList<>();
        for (CitizenRecord c : citizens.values()) {
            if (c.id().toString().startsWith(q) || c.name().toLowerCase(Locale.ROOT).equals(q)) {
                hits.add(c);
            }
        }
        return hits.size() == 1 ? Optional.of(hits.get(0)) : Optional.empty();
    }

    public List<Object> toList() {
        return citizens.values().stream().<Object>map(CitizenRecord::toMap).toList();
    }

    public void loadFrom(List<Map<String, Object>> list) {
        citizens.clear();
        for (Map<String, Object> m : list) {
            add(CitizenRecord.fromMap(m));
        }
    }

}
