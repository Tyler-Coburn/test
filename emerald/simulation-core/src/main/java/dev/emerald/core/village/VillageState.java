package dev.emerald.core.village;

import dev.emerald.core.EmeraldConstants;
import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.CitizenRegistry;
import dev.emerald.core.construction.ConstructionRegistry;
import dev.emerald.core.data.Data;
import dev.emerald.core.data.MigrationRegistry;
import dev.emerald.core.economy.EconomyStats;
import dev.emerald.core.education.Library;
import dev.emerald.core.event.EventLedger;
import dev.emerald.core.event.LedgerEvent;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.observe.ObservationBus;
import dev.emerald.core.request.RequestBoard;
import dev.emerald.core.research.ExperimentLog;
import dev.emerald.core.research.ProblemBoard;
import dev.emerald.core.simulation.PendingMaterialization;
import dev.emerald.core.simulation.VillageSnapshot;
import dev.emerald.core.technology.DesignRegistry;
import dev.emerald.core.technology.SkillLibrary;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.Pos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The settlement's operational source of truth: who lives here, what it stores, what it is
 * building, what problems it has, what it has tested, written down and adopted, and what it last
 * saw of its world (for offline simulation).
 */
public final class VillageState {
    private final UUID id;
    private String name;
    private final String dimension;
    private Pos center;
    private Pos warehouse;
    private Pos libraryPos;
    private Box pen;
    private final long foundedAt;
    private VillageBias bias;
    private long lastSimulationTime;
    private long lastDirectorRun = Long.MIN_VALUE;
    private long lastImmigration = Long.MIN_VALUE;
    private boolean loaded = true;

    private final CitizenRegistry citizens = new CitizenRegistry();
    private final RequestBoard requests = new RequestBoard();
    private final ConstructionRegistry construction = new ConstructionRegistry();
    private final ProblemBoard problems = new ProblemBoard();
    private final ExperimentLog experiments = new ExperimentLog();
    private final DesignRegistry designs = new DesignRegistry();
    private final Library library = new Library();
    private final SkillLibrary skills = new SkillLibrary();
    private final EconomyStats economy = new EconomyStats();
    private final VillageSnapshot snapshot = new VillageSnapshot();
    private final PendingMaterialization pending = new PendingMaterialization();
    private final ObservationBus observations;
    private final EventLedger ledger;

    public VillageState(UUID id, String name, String dimension, Pos center, long foundedAt, SimulationConfig config) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.center = center;
        this.foundedAt = foundedAt;
        this.lastSimulationTime = foundedAt;
        this.bias = VillageBias.roll(id.getLeastSignificantBits());
        this.observations = new ObservationBus(config.observationCap());
        this.ledger = new EventLedger(config.ledgerCap());
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String dimension() { return dimension; }
    public Pos center() { return center; }
    public Pos warehouse() { return warehouse; }
    /** Where books are kept and read: the registered library, else the warehouse, else the centre. */
    public Pos libraryPos() { return libraryPos != null ? libraryPos : warehouse != null ? warehouse : center; }
    public Box pen() { return pen; }
    public long foundedAt() { return foundedAt; }
    public VillageBias bias() { return bias; }
    public long lastSimulationTime() { return lastSimulationTime; }
    public long lastDirectorRun() { return lastDirectorRun; }
    public long lastImmigration() { return lastImmigration; }
    public boolean loaded() { return loaded; }
    public CitizenRegistry citizens() { return citizens; }
    public RequestBoard requests() { return requests; }
    public ConstructionRegistry construction() { return construction; }
    public ProblemBoard problems() { return problems; }
    public ExperimentLog experiments() { return experiments; }
    public DesignRegistry designs() { return designs; }
    public Library library() { return library; }
    public SkillLibrary skills() { return skills; }
    public EconomyStats economy() { return economy; }
    public VillageSnapshot snapshot() { return snapshot; }
    public PendingMaterialization pending() { return pending; }
    public ObservationBus observations() { return observations; }
    public EventLedger ledger() { return ledger; }

    public void setName(String name) { this.name = name; }
    public void setCenter(Pos center) { this.center = center; }
    public void setWarehouse(Pos warehouse) { this.warehouse = warehouse; }
    public void setLibraryPos(Pos pos) { this.libraryPos = pos; }
    public void setPen(Box pen) { this.pen = pen; }
    public void setBias(VillageBias bias) { this.bias = bias; }
    public void setLastSimulationTime(long t) { this.lastSimulationTime = t; }
    public void setLastDirectorRun(long t) { this.lastDirectorRun = t; }
    public void setLastImmigration(long t) { this.lastImmigration = t; }
    public void setLoaded(boolean loaded) { this.loaded = loaded; }

    /** Appends a ledger event with an inline payload of alternating key/value strings. */
    public LedgerEvent log(String type, long now, Pos pos, UUID actor, UUID subject, UUID cause, UUID correlation,
                           Provenance provenance, String... keyValues) {
        Map<String, String> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            payload.put(keyValues[i], keyValues[i + 1]);
        }
        return ledger.append(type, now, pos, actor, subject, cause, correlation, provenance, payload);
    }

    /** Validated death: the record stays (alive=false); village-owned designs and books are untouched. */
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
        if (libraryPos != null) m.put("library", libraryPos.toMap());
        if (pen != null) m.put("pen", pen.toMap());
        m.put("founded", foundedAt);
        m.put("bias", bias.name());
        m.put("lastSim", lastSimulationTime);
        m.put("lastDirector", lastDirectorRun);
        m.put("lastImmigration", lastImmigration);
        m.put("loaded", loaded);
        m.put("citizens", citizens.toList());
        m.put("requests", requests.toList());
        m.put("projects", construction.projectsToList());
        m.put("buildings", construction.buildingsToList());
        m.put("problems", problems.toList());
        m.put("experiments", experiments.toList());
        m.put("designs", designs.toList());
        m.put("writings", library.toList());
        m.put("skillStats", skills.toList());
        m.put("economy", economy.toMap());
        m.put("snapshot", snapshot.toMap());
        m.put("pending", pending.toMap());
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
        v.libraryPos = Pos.fromMapOrNull(Data.subOrNull(m, "library"));
        Map<String, Object> pen = Data.subOrNull(m, "pen");
        v.pen = pen == null ? null : Box.fromMap(pen);
        v.bias = Data.enumOr(m, "bias", VillageBias.class, v.bias);
        v.lastSimulationTime = Data.lOr(m, "lastSim", v.foundedAt);
        v.lastDirectorRun = Data.lOr(m, "lastDirector", Long.MIN_VALUE);
        v.lastImmigration = Data.lOr(m, "lastImmigration", Long.MIN_VALUE);
        v.loaded = Data.b(m, "loaded", true);
        v.citizens.loadFrom(Data.maps(m, "citizens"));
        v.requests.loadFrom(Data.maps(m, "requests"));
        v.construction.loadFrom(Data.maps(m, "projects"), Data.maps(m, "buildings"));
        v.problems.loadFrom(Data.maps(m, "problems"));
        v.experiments.loadFrom(Data.maps(m, "experiments"));
        v.designs.loadFrom(Data.maps(m, "designs"));
        v.library.loadFrom(Data.maps(m, "writings"));
        v.skills.loadFrom(Data.maps(m, "skillStats"));
        v.economy.loadFrom(Data.subOrNull(m, "economy"));
        v.snapshot.loadFrom(Data.subOrNull(m, "snapshot"));
        v.pending.loadFrom(Data.subOrNull(m, "pending"));
        v.observations.loadFrom(Data.maps(m, "observations"), Data.lOr(m, "observationsTotal", 0));
        v.ledger.loadFrom(Data.maps(m, "ledger"), Data.lOr(m, "ledgerTotal", 0));
        return v;
    }
}
