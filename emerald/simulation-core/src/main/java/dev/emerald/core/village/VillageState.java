package dev.emerald.core.village;

import dev.emerald.core.EmeraldConstants;
import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.CitizenRegistry;
import dev.emerald.core.construction.ConstructionRegistry;
import dev.emerald.core.data.Data;
import dev.emerald.core.data.MigrationRegistry;
import dev.emerald.core.event.EventLedger;
import dev.emerald.core.event.LedgerEvent;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.observe.ObservationBus;
import dev.emerald.core.request.RequestBoard;
import dev.emerald.core.research.ExperimentLog;
import dev.emerald.core.research.ProblemBoard;
import dev.emerald.core.technology.DesignRegistry;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.Pos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The settlement's operational source of truth: who lives here, what it stores, what it is
 * building, what problems it has, what it has tested and which designs it owns.
 */
public final class VillageState {
    private final UUID id;
    private String name;
    private final String dimension;
    private Pos center;
    private Pos warehouse;
    private Box pen;
    private final long foundedAt;
    private long lastSimulationTime;

    private final CitizenRegistry citizens = new CitizenRegistry();
    private final RequestBoard requests = new RequestBoard();
    private final ConstructionRegistry construction = new ConstructionRegistry();
    private final ProblemBoard problems = new ProblemBoard();
    private final ExperimentLog experiments = new ExperimentLog();
    private final DesignRegistry designs = new DesignRegistry();
    private final ObservationBus observations;
    private final EventLedger ledger;

    public VillageState(UUID id, String name, String dimension, Pos center, long foundedAt, SimulationConfig config) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.center = center;
        this.foundedAt = foundedAt;
        this.lastSimulationTime = foundedAt;
        this.observations = new ObservationBus(config.observationCap());
        this.ledger = new EventLedger(config.ledgerCap());
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String dimension() { return dimension; }
    public Pos center() { return center; }
    public Pos warehouse() { return warehouse; }
    public Box pen() { return pen; }
    public long foundedAt() { return foundedAt; }
    public long lastSimulationTime() { return lastSimulationTime; }
    public CitizenRegistry citizens() { return citizens; }
    public RequestBoard requests() { return requests; }
    public ConstructionRegistry construction() { return construction; }
    public ProblemBoard problems() { return problems; }
    public ExperimentLog experiments() { return experiments; }
    public DesignRegistry designs() { return designs; }
    public ObservationBus observations() { return observations; }
    public EventLedger ledger() { return ledger; }

    public void setName(String name) { this.name = name; }
    public void setCenter(Pos center) { this.center = center; }
    public void setWarehouse(Pos warehouse) { this.warehouse = warehouse; }
    public void setPen(Box pen) { this.pen = pen; }
    public void setLastSimulationTime(long t) { this.lastSimulationTime = t; }

    /** Appends a ledger event with an inline payload of alternating key/value strings. */
    public LedgerEvent log(String type, long now, Pos pos, UUID actor, UUID subject, UUID cause, UUID correlation,
                           Provenance provenance, String... keyValues) {
        Map<String, String> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            payload.put(keyValues[i], keyValues[i + 1]);
        }
        return ledger.append(type, now, pos, actor, subject, cause, correlation, provenance, payload);
    }

    /** Validated death: the record stays (alive=false); village-owned designs are untouched. */
    public void recordDeath(CitizenRecord citizen, String cause, long now) {
        if (!citizen.alive()) {
            return;
        }
        citizen.die(cause, now);
        log("CITIZEN_DIED", now, null, citizen.id(), null, null, null, Provenance.SYSTEM_OBSERVED,
                "name", citizen.name(), "role", citizen.role().name(), "cause", cause);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        m.put(MigrationRegistry.SCHEMA_KEY, EmeraldConstants.SCHEMA_VERSION);
        Data.putUuid(m, "id", id);
        m.put("name", name);
        m.put("dim", dimension);
        m.put("center", center.toMap());
        if (warehouse != null) m.put("warehouse", warehouse.toMap());
        if (pen != null) m.put("pen", pen.toMap());
        m.put("founded", foundedAt);
        m.put("lastSim", lastSimulationTime);
        m.put("citizens", citizens.toList());
        m.put("requests", requests.toList());
        m.put("projects", construction.projectsToList());
        m.put("buildings", construction.buildingsToList());
        m.put("problems", problems.toList());
        m.put("experiments", experiments.toList());
        m.put("designs", designs.toList());
        m.put("observations", observations.toList());
        m.put("observationsTotal", observations.totalRecorded());
        m.put("ledger", ledger.toList());
        m.put("ledgerTotal", ledger.totalAppended());
        return m;
    }

    public static VillageState fromMap(Map<String, Object> m, SimulationConfig config) {
        MigrationRegistry.standard().upgrade(m);
        VillageState v = new VillageState(Data.uuid(m, "id"), Data.strOr(m, "name", "Village"),
                Data.strOr(m, "dim", "minecraft:overworld"), Pos.fromMap(Data.sub(m, "center")),
                Data.lOr(m, "founded", 0), config);
        v.warehouse = Pos.fromMapOrNull(Data.subOrNull(m, "warehouse"));
        Map<String, Object> pen = Data.subOrNull(m, "pen");
        v.pen = pen == null ? null : Box.fromMap(pen);
        v.lastSimulationTime = Data.lOr(m, "lastSim", v.foundedAt);
        v.citizens.loadFrom(Data.maps(m, "citizens"));
        v.requests.loadFrom(Data.maps(m, "requests"));
        v.construction.loadFrom(Data.maps(m, "projects"), Data.maps(m, "buildings"));
        v.problems.loadFrom(Data.maps(m, "problems"));
        v.experiments.loadFrom(Data.maps(m, "experiments"));
        v.designs.loadFrom(Data.maps(m, "designs"));
        v.observations.loadFrom(Data.maps(m, "observations"), Data.lOr(m, "observationsTotal", 0));
        v.ledger.loadFrom(Data.maps(m, "ledger"), Data.lOr(m, "ledgerTotal", 0));
        return v;
    }
}
