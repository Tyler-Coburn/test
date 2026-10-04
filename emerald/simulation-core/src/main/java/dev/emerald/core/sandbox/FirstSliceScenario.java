package dev.emerald.core.sandbox;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.construction.Blueprints;
import dev.emerald.core.construction.Building;
import dev.emerald.core.data.Json;
import dev.emerald.core.event.LedgerEvent;
import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.KnowState;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.request.ResourceRequest;
import dev.emerald.core.research.Experiment;
import dev.emerald.core.research.Hypothesis;
import dev.emerald.core.research.ProblemType;
import dev.emerald.core.technology.DesignRegistry;
import dev.emerald.core.technology.DesignRevision;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The first playable slice, end to end, with no model and no Minecraft: six citizens found a village,
 * farm, share a chest through a request, build housing, notice wasted eggs, run hopper experiments
 * (a failure first, then a pass), keep the design, write it down, teach it, install it, survive an
 * unloaded period and a night attack, reload from disk, and keep the design after the inventor dies.
 *
 * <p>Every check reads simulation state; nothing is asserted from the script itself.
 */
public final class FirstSliceScenario {
    public static final int DAY = 24000;

    public record Check(String id, String description, boolean passed, String detail) {
    }

    public record Result(List<Check> checks, List<String> narrative, VillageState village, SandboxWorld world) {
        public boolean allPassed() {
            return checks.stream().allMatch(Check::passed);
        }
    }

    private final long seed;
    private final List<String> narrative = new ArrayList<>();
    private final List<Check> checks = new ArrayList<>();
    private SandboxWorld w;
    private VillageState v;
    private VillageWorld world;

    private java.util.function.Consumer<SandboxWorld> recorder;

    public FirstSliceScenario(long seed) {
        this.seed = seed;
    }

    /** Observe every sandbox step (e.g. a {@link ReplayRecorder}). */
    public FirstSliceScenario withRecorder(java.util.function.Consumer<SandboxWorld> recorder) {
        this.recorder = recorder;
        return this;
    }

    public Result run() {
        SimulationConfig cfg = SimulationConfig.DEFAULT;
        world = new VillageWorld(cfg);
        w = new SandboxWorld(seed, 1000, 64, cfg);
        if (recorder != null) {
            w.stepListeners.add(recorder);
        }
        Pos center = new Pos(0, 64, 0);
        v = world.found("Sandbox Hollow", SandboxWorld.DIM, center, w.time, seed);
        w.bind(v);
        watch();
        note("Founded %s (bias %s) with %d citizens.", v.name(), v.bias(), v.citizens().size());

        Pos warehouse = new Pos(3, 64, 0);
        ItemCounter chest = w.chestAt(warehouse);
        chest.insert(ItemIds.OAK_PLANKS, 240);
        chest.insert(ItemIds.HOPPER, 4);
        chest.insert(ItemIds.CHEST, 2);
        chest.insert("minecraft:book", 4);
        chest.insert(ItemIds.BREAD, 60);
        chest.insert(ItemIds.WHEAT_SEEDS, 16);
        v.setWarehouse(warehouse);
        w.plantField(new Pos(6, 64, -9), 5, 5, 4);
        Box pen = Box.of(new Pos(10, 64, 10), new Pos(12, 64, 10));
        v.setPen(pen);
        for (int i = 0; i < 3; i++) {
            w.addChicken(new Pos(10 + i, 64, 10));
        }
        // Chickens favour the pen's ends at first, so the first hopper test can fail for real.
        w.layingCells = List.of(new Pos(10, 64, 10), new Pos(12, 64, 10));
        w.summonMissingBodies(center);
        List<UUID> founders = v.citizens().all().stream().map(CitizenRecord::id).toList();

        CitizenRecord builder = role(Role.BUILDER);
        ResourceRequest planks = v.requests().open(builder.id(), ItemIds.OAK_PLANKS, 16, null, null, w.time);
        note("Builder %s asks for 16 oak planks.", builder.name());

        // Day 1
        w.runTicks(DAY);
        boolean allAlive = founders.stream().allMatch(id -> v.citizens().get(id).map(CitizenRecord::alive).orElse(false));
        int maxHunger = v.citizens().alive().stream().mapToInt(CitizenRecord::hunger).max().orElse(0);
        check("P1", "Survive a day without a model", allAlive && maxHunger < 100,
                "alive=" + v.citizens().alive().size() + " maxHunger=" + maxHunger);
        check("P3", "Share one chest through a request", planks.state() == ResourceRequest.State.DELIVERED,
                "16 planks " + planks.state() + " carrier=" + name(planks.carrier()));

        // Run until a hopper experiment has failed (chickens avoid the hopper).
        runUntil(() -> v.experiments().all().stream().anyMatch(e -> e.phase() == Experiment.Phase.FAILED), 10 * DAY);
        note("Day %d: first experiment result: %s", day(), v.experiments().all().isEmpty() ? "none"
                : v.experiments().all().get(0).summary());

        // Chickens wander the whole pen from now on.
        w.layingCells = null;
        runUntil(() -> v.experiments().all().stream().anyMatch(e -> e.phase() == Experiment.Phase.PASSED), 12 * DAY);
        note("Day %d: experiments so far: %d, designs: %s", day(), v.experiments().all().size(),
                v.designs().all().stream().map(DesignRevision::label).toList());

        // Let the village install the design, write it down and teach.
        runUntil(() -> v.construction().hasBuilding(Blueprints.COLLECTOR) && collectorStored() > 0
                && !v.ledger().ofType("KNOWLEDGE_TAUGHT").isEmpty() && !v.library().all().isEmpty(), 12 * DAY);
        note("Day %d: collector built=%s, eggs stored by collector=%d, books=%d", day(),
                v.construction().hasBuilding(Blueprints.COLLECTOR), collectorStored(), v.library().all().size());

        // Housing growth needs time and materials.
        runUntil(() -> v.construction().buildings().stream().anyMatch(b -> b.type().equals(Blueprints.HUT)), 6 * DAY);

        checkScience();

        // Engineering: eggs still wasted at the pen's ends -> Mk N prototype, field trial, adoption.
        runUntil(() -> v.designs().all().stream().anyMatch(d -> d.blueprintId() != null
                && d.blueprintId().startsWith(Blueprints.FULL_COLLECTOR_PREFIX) && !d.inTrial()), 10 * DAY);
        Optional<DesignRevision> full = v.designs().all().stream()
                .filter(d -> d.blueprintId() != null && d.blueprintId().startsWith(Blueprints.FULL_COLLECTOR_PREFIX))
                .findFirst();
        check("E1", "Prototype revision is built, field-tested from bus evidence, and adopted",
                full.map(DesignRevision::adopted).orElse(false),
                full.map(d -> d.label() + " collected " + d.measuredOutput() + " in trial; blueprint " + d.blueprintId())
                        .orElse("no prototype"));

        // Save to disk format and reload into a fresh world object.
        String json = Json.write(world.toMap());
        world = VillageWorld.fromMap(Json.parseObject(json), cfg);
        VillageState reloaded = world.primary().orElseThrow();
        boolean sameIds = founders.stream().allMatch(id -> reloaded.citizens().get(id).isPresent());
        boolean sameDesign = reloaded.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).isPresent();
        v = reloaded;
        w.bind(v);
        watch();
        w.scheduler = new dev.emerald.core.job.CitizenScheduler();
        check("S1", "Save -> JSON -> reload keeps citizens, designs and knowledge", sameIds && sameDesign
                        && role(Role.RESEARCHER).knowledge().state(ConceptId.HOPPER_PULLS_ITEM).isConfirmedTrue(),
                json.length() / 1024 + " KiB save; founders present=" + sameIds + "; design present=" + sameDesign);

        // Unloaded for three days: statistical simulation, then reconciliation.
        int wheatBefore = w.containers.get(warehouse).count(ItemIds.WHEAT);
        int eggsBefore = collectorChest().map(c -> c.count(ItemIds.EGG)).orElse(0);
        w.setLoaded(false);
        w.runTicks(3L * DAY);
        List<LedgerEvent> catchups = v.ledger().ofType("OFFLINE_CATCHUP");
        w.setLoaded(true);
        w.runTicks(400);
        int wheatAfter = w.containers.get(warehouse).count(ItemIds.WHEAT);
        int eggsAfter = collectorChest().map(c -> c.count(ItemIds.EGG)).orElse(0);
        check("M9", "Village progresses while unloaded and reconciles on load",
                !catchups.isEmpty() && (wheatAfter != wheatBefore || eggsAfter != eggsBefore) && v.pending().deltas().isEmpty(),
                "catch-up events=" + catchups.size() + " wheat " + wheatBefore + "->" + wheatAfter
                        + " collector eggs " + eggsBefore + "->" + eggsAfter);

        // A night attack: guards defend, others flee.
        w.time = (w.time / DAY + 1) * DAY + 14000 - 10;
        SandboxWorld.Monster zombie = w.spawnMonster(v.center().offset(4, 0, 4));
        runUntil(() -> !w.monsters.contains(zombie), DAY / 2);
        int guardXp = v.citizens().alive().stream().filter(c -> c.role() == Role.GUARD)
                .mapToInt(c -> c.xp(dev.emerald.core.citizen.SkillType.COMBAT)).sum();
        boolean nobodyHurt = v.citizens().alive().size() >= founders.size() - 1;
        check("G1", "Guards defend the village at night", !w.monsters.contains(zombie) && guardXp > 0 && nobodyHurt,
                "monster removed=" + !w.monsters.contains(zombie) + " guards' combat xp=" + guardXp);

        // The inventor dies; the village keeps the design and the collector keeps working.
        CitizenRecord researcher = role(Role.RESEARCHER);
        w.kill(researcher, "old age");
        long deathTime = w.time;
        int storedBefore = collectorStored();
        runUntil(() -> collectorStored() > storedBefore, 4 * DAY);
        check("P11b", "Design survives the inventor's death and keeps collecting",
                v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).isPresent() && collectorStored() > storedBefore,
                "inventor dead since t=" + deathTime + ", collector stores " + storedBefore + "->" + collectorStored());

        // Growth: newcomers once beds and food allow.
        runUntil(() -> v.citizens().size() > founders.size(), 10 * DAY);
        check("M10", "Director grows the village: auto housing, labour, newcomers",
                !v.ledger().ofType("PROJECT_STARTED").isEmpty(),
                "projects started by director=" + v.ledger().ofType("PROJECT_STARTED").size()
                        + " huts=" + count(Blueprints.HUT) + " beds=" + v.construction().totalBeds()
                        + " citizens=" + v.citizens().size() + " arrivals=" + v.ledger().ofType("CITIZEN_ARRIVED").size());

        note("Finished on day %d. Economy produced %s, consumed %s.", day(), v.economy().producedTotals(),
                v.economy().consumedTotals());
        return new Result(List.copyOf(checks), List.copyOf(narrative), v, w);
    }

    private void checkScience() {
        long farmWheat = v.economy().produced(ItemIds.WHEAT);
        check("P2", "Farm and store food", farmWheat > 0,
                "wheat deposited=" + farmWheat + " warehouse wheat=" + w.containers.get(v.warehouse()).count(ItemIds.WHEAT));
        long huts = count(Blueprints.HUT);
        check("P4", "Build one hut from real materials", huts > 0,
                "huts=" + huts + " (director-started housing projects=" + v.ledger().ofType("PROJECT_STARTED").stream()
                        .filter(e -> "HOUSING".equals(e.payload().get("purpose"))).count() + ")");
        var problem = v.problems().all().stream().filter(p -> p.type() == ProblemType.EGGS_WASTED).findFirst();
        check("P5", "Open one problem from the bus", problem.isPresent() && problem.get().evidence() != null,
                problem.map(p -> "EGGS_WASTED evidence " + p.evidence().toString().substring(0, 8)).orElse("none"));
        long ended = v.experiments().all().stream().filter(e -> !e.isActive()).count();
        check("P6", "Run one experiment", ended > 0, v.experiments().all().size() + " experiments, " + ended + " concluded");
        CitizenRecord researcher = role(Role.RESEARCHER);
        var entry = researcher.knowledge().entry(ConceptId.HOPPER_PULLS_ITEM);
        boolean evidenced = entry.map(e -> e.state().isConfirmedTrue()
                && v.ledger().ofType("EXPERIMENT_PASSED").stream().anyMatch(ev -> e.sourceObservation().equals(ev.causationId())))
                .orElse(false);
        check("P7", "Write one TESTED_TRUE principle from evidence", evidenced,
                "HOPPER_PULLS_ITEM=" + entry.map(e -> e.state().name()).orElse("UNKNOWN")
                        + " books=" + v.library().all().size());
        check("P8", "Store one prototype revision", !v.designs().all().isEmpty(),
                v.designs().all().stream().map(DesignRevision::label).toList().toString());
        boolean mk2OrFailed = v.designs().all().stream().anyMatch(d -> d.revision() >= 2 || d.failures() > 0);
        check("P9", "Store Mk II or a failed revision", mk2OrFailed,
                v.designs().all().stream().map(d -> d.label() + " output=" + d.measuredOutput()).toList().toString());
        Optional<LedgerEvent> taught = v.ledger().ofType("KNOWLEDGE_TAUGHT").stream()
                .filter(e -> v.citizens().get(e.subject()).map(c -> c.role() == Role.CHILD || c.role() == Role.GENERAL
                        || c.role() == Role.BUILDER).orElse(false))
                .findFirst();
        check("P10", "Teach OBSERVED (never TESTED_TRUE) to another citizen",
                taught.isPresent() && "OBSERVED".equals(taught.get().payload().get("studentState")),
                taught.map(e -> name(e.subject()) + " learned " + e.payload().get("concept") + " as "
                        + e.payload().get("studentState")).orElse("no lesson recorded"));
        Optional<Building> collector = v.construction().buildings().stream()
                .filter(b -> b.type().equals(Blueprints.COLLECTOR)).findFirst();
        check("P11", "Builder installs the adopted design and the hopper actually collects",
                collector.isPresent() && collectorStored() > 0,
                collector.map(b -> "installed at " + b.origin() + "; eggs stored via hopper->chest=" + collectorStored())
                        .orElse("not installed"));
        boolean modelFree = v.experiments().all().stream().allMatch(e -> e.hypothesis().origin() == Hypothesis.Origin.FALLBACK);
        check("M8", "Loop runs with the model off (deterministic hypothesis table)", modelFree && ended > 0,
                "hypothesis origins=" + v.experiments().all().stream().map(e -> e.hypothesis().origin().name()).distinct().toList());
    }

    /** Eggs the installed collector stored, counted from ITEM_STORED observations as they happen. */
    private int collectorStored() {
        return collectorStored;
    }

    private int collectorStored;

    /** Subscribes the report's counter to the bus (again after every rebind, which resets listeners). */
    private void watch() {
        v.observations().subscribe(o -> {
            if (o.type() == ObservationType.ITEM_STORED && o.isItem(ItemIds.EGG)
                    && v.construction().buildings().stream().anyMatch(b -> Blueprints.isCollector(b.type())
                    && o.pos().equals(Blueprints.collectorChest(b)))) {
                collectorStored++;
            }
        });
    }

    private Optional<ItemCounter> collectorChest() {
        return v.construction().buildings().stream().filter(b -> Blueprints.isCollector(b.type())).findFirst()
                .map(b -> w.containers.get(Blueprints.collectorChest(b)));
    }

    private long count(String type) {
        return v.construction().buildings().stream().filter(b -> b.type().equals(type)).count();
    }

    private void runUntil(BooleanSupplier done, long maxTicks) {
        long end = w.time + maxTicks;
        while (!done.getAsBoolean() && w.time < end) {
            w.runTicks(200);
        }
    }

    private CitizenRecord role(Role role) {
        return v.citizens().all().stream().filter(c -> c.role() == role).findFirst().orElseThrow();
    }

    private String name(UUID id) {
        return id == null ? "-" : v.citizens().get(id).map(CitizenRecord::name).orElse(id.toString().substring(0, 8));
    }

    private long day() {
        return w.time / DAY;
    }

    private void check(String id, String description, boolean passed, String detail) {
        checks.add(new Check(id, description, passed, detail));
        note("[%s] %s %s: %s", passed ? "PASS" : "FAIL", id, description, detail);
    }

    private void note(String fmt, Object... args) {
        narrative.add(String.format(fmt, args));
    }

    /** Markdown report of a run. */
    public static String report(Result r, long seed) {
        StringBuilder sb = new StringBuilder("# Emerald sandbox run (seed ").append(seed).append(")\n\n");
        sb.append("| Check | Description | Result | Detail |\n|---|---|---|---|\n");
        for (Check c : r.checks()) {
            sb.append("| ").append(c.id()).append(" | ").append(c.description()).append(" | ")
                    .append(c.passed() ? "PASS" : "**FAIL**").append(" | ").append(c.detail().replace("|", "/")).append(" |\n");
        }
        sb.append("\n## Narrative\n\n");
        r.narrative().forEach(n -> sb.append("- ").append(n).append('\n'));
        VillageState v = r.village();
        sb.append("\n## Final state\n\n");
        for (CitizenRecord c : v.citizens().all()) {
            sb.append("- ").append(c.name()).append(" (").append(c.role()).append(c.alive() ? "" : ", dead")
                    .append(") hunger=").append(c.hunger()).append(" energy=").append(c.energy())
                    .append(" skills=").append(c.skills()).append(" knows=").append(c.knowledge().known()).append('\n');
        }
        sb.append("\nBuildings: ").append(v.construction().buildings().stream().map(Building::type).toList()).append('\n');
        sb.append("\nDesigns: ").append(v.designs().all().stream().map(DesignRevision::label).toList()).append('\n');
        sb.append("\nProcedures: ").append(v.skills().all()).append('\n');
        sb.append("\nLast ledger events:\n\n");
        v.ledger().recent(25).forEach(e -> sb.append("- ").append(e.summary()).append('\n'));
        return sb.toString();
    }
}
