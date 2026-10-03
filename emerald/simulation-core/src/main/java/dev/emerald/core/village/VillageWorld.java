package dev.emerald.core.village;

import dev.emerald.core.EmeraldConstants;
import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.FounderFactory;
import dev.emerald.core.data.Data;
import dev.emerald.core.data.MigrationRegistry;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.world.Pos;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every village in one save. The NeoForge SavedData persists exactly this object. */
public final class VillageWorld {
    private final Map<UUID, VillageState> villages = new LinkedHashMap<>();
    private final SimulationConfig config;

    public VillageWorld(SimulationConfig config) {
        this.config = config;
    }

    public SimulationConfig config() {
        return config;
    }

    public Collection<VillageState> villages() {
        return Collections.unmodifiableCollection(villages.values());
    }

    /** v1 runs one settlement; this is it, if founded. */
    public Optional<VillageState> primary() {
        return villages.values().stream().findFirst();
    }

    public Optional<VillageState> get(UUID id) {
        return Optional.ofNullable(villages.get(id));
    }

    public Optional<CitizenRecord> citizen(UUID citizenId) {
        for (VillageState v : villages.values()) {
            Optional<CitizenRecord> c = v.citizens().get(citizenId);
            if (c.isPresent()) {
                return c;
            }
        }
        return Optional.empty();
    }

    public Optional<VillageState> villageOf(UUID citizenId) {
        return villages.values().stream().filter(v -> v.citizens().get(citizenId).isPresent()).findFirst();
    }

    /** Founds a village with six citizen records (FARMER..CHILD). No bodies are created here. */
    public VillageState found(String name, String dimension, Pos center, long now, long seed) {
        VillageState v = new VillageState(UUID.randomUUID(), name, dimension, center, now, config);
        List<CitizenRecord> founders = FounderFactory.founders(v.id(), seed ^ now);
        for (CitizenRecord c : founders) {
            c.setHome(center);
            v.citizens().add(c);
            v.log("CITIZEN_CREATED", now, center, c.id(), null, null, null, Provenance.PLAYER_COMMAND,
                    "name", c.name(), "role", c.role().name());
        }
        v.log("VILLAGE_FOUNDED", now, center, null, v.id(), null, null, Provenance.PLAYER_COMMAND, "name", name);
        villages.put(v.id(), v);
        return v;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        m.put(MigrationRegistry.SCHEMA_KEY, EmeraldConstants.SCHEMA_VERSION);
        m.put("villages", villages.values().stream().<Object>map(VillageState::toMap).toList());
        return m;
    }

    public static VillageWorld fromMap(Map<String, Object> m, SimulationConfig config) {
        MigrationRegistry.standard().upgrade(m);
        VillageWorld w = new VillageWorld(config);
        for (Map<String, Object> vm : Data.maps(m, "villages")) {
            VillageState v = VillageState.fromMap(vm, config);
            w.villages.put(v.id(), v);
        }
        return w;
    }
}
