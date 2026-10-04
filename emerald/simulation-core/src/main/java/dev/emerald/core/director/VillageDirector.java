package dev.emerald.core.director;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.construction.Blueprints;
import dev.emerald.core.construction.Building;
import dev.emerald.core.construction.ConstructionProject;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.job.WorldPort;
import dev.emerald.core.request.ResourceRequest;
import dev.emerald.core.research.EggWasteDetector;
import dev.emerald.core.research.Experiment;
import dev.emerald.core.research.ExperimentEngine;
import dev.emerald.core.research.Hypothesis;
import dev.emerald.core.research.HypothesisSource;
import dev.emerald.core.research.ProblemType;
import dev.emerald.core.research.ResearchProblem;
import dev.emerald.core.technology.DesignRegistry;
import dev.emerald.core.technology.DesignRevision;
import dev.emerald.core.utility.NeedsModel;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Slow strategic evaluator. Turns persistent conditions into problems and projects; assigns
 * labour by role; never micromanages bodies.
 * <ul>
 *   <li>Problems: EGGS_WASTED (from the bus), FOOD_LOW, HOMELESS.</li>
 *   <li>FOOD_LOW: a general without deliveries is reassigned to farming until stock recovers.</li>
 *   <li>HOMELESS: starts a hut on a free site found in the world (when food allows).</li>
 *   <li>An adopted design with no installation: the builder installs it (proof 11).</li>
 *   <li>EGGS_WASTED: hands the problem to an idle researcher with a hypothesis.</li>
 *   <li>Growth: a newcomer arrives when beds and food are spare.</li>
 * </ul>
 */
public final class VillageDirector {
    public static final int FOOD_RESERVE_PER_CITIZEN = 4;
    public static final int BEDS_PER_HUT = Blueprints.HUT_BEDS;
    private static final String[] NEWCOMER_NAMES = {
            "Ansel", "Brio", "Calla", "Dace", "Enna", "Faro", "Greer", "Holt", "Isla", "Jory",
            "Kael", "Lark", "Moss", "Nell", "Odo", "Pim", "Quin", "Rook", "Sable", "Tove", "Ulla", "Vale", "Wren", "Yara"
    };

    private VillageDirector() {
    }

    public static void evaluate(VillageState v, long now, WorldPort world, SimulationConfig cfg, HypothesisSource hypotheses) {
        ItemStore warehouse = world.warehouse();
        EggWasteDetector.evaluate(v, now, cfg.eggWasteWindowTicks());
        evaluateFood(v, now, warehouse);
        evaluateHousing(v, now);
        assignLabour(v, now);
        planHousing(v, now, world);
        evaluatePrototypes(v, now, cfg);
        deployDesigns(v, now);
        improveDesigns(v, now);
        startResearch(v, now, hypotheses);
        considerImmigration(v, now, warehouse, cfg);
    }

    public static int storedFood(ItemStore warehouse) {
        int food = 0;
        for (String item : NeedsModel.FOOD_VALUES.keySet()) {
            food += warehouse.count(item);
        }
        return food;
    }

    public static int foodTarget(VillageState v) {
        return v.citizens().alive().size() * FOOD_RESERVE_PER_CITIZEN;
    }

    public static void evaluateFood(VillageState v, long now, ItemStore warehouse) {
        if (warehouse == null) {
            return;
        }
        int food = storedFood(warehouse);
        int target = foodTarget(v);
        Optional<ResearchProblem> active = v.problems().active(ProblemType.FOOD_LOW);
        if (food < target && active.isEmpty()) {
            ResearchProblem p = v.problems().open(ProblemType.FOOD_LOW, null, now);
            v.log("PROBLEM_OPENED", now, v.warehouse(), null, p.id(), null, p.id(), Provenance.VILLAGE_DIRECTOR,
                    "problem", "FOOD_LOW", "food", String.valueOf(food), "target", String.valueOf(target));
        } else if (food >= target && active.isPresent()) {
            v.problems().setStatus(active.get().id(), ResearchProblem.Status.RESOLVED, now, "stock " + food);
            v.log("PROBLEM_RESOLVED", now, v.warehouse(), null, active.get().id(), null, active.get().id(),
                    Provenance.VILLAGE_DIRECTOR, "problem", "FOOD_LOW", "food", String.valueOf(food));
        }
    }

    public static void evaluateHousing(VillageState v, long now) {
        long adults = adults(v);
        int beds = v.construction().totalBeds();
        Optional<ResearchProblem> active = v.problems().active(ProblemType.HOMELESS);
        if (adults > beds && active.isEmpty()) {
            ResearchProblem p = v.problems().open(ProblemType.HOMELESS, null, now);
            v.log("PROBLEM_OPENED", now, v.center(), null, p.id(), null, p.id(), Provenance.VILLAGE_DIRECTOR,
                    "problem", "HOMELESS", "adults", String.valueOf(adults), "beds", String.valueOf(beds));
        } else if (adults <= beds && active.isPresent()) {
            v.problems().setStatus(active.get().id(), ResearchProblem.Status.RESOLVED, now, "beds " + beds);
            v.log("PROBLEM_RESOLVED", now, v.center(), null, active.get().id(), null, active.get().id(),
                    Provenance.VILLAGE_DIRECTOR, "problem", "HOMELESS", "beds", String.valueOf(beds));
        }
    }

    static long adults(VillageState v) {
        return v.citizens().alive().stream().filter(c -> c.role().isAdult()).count();
    }

    /** FOOD_LOW pulls one free general onto the farm; recovery sends them back. */
    static void assignLabour(VillageState v, long now) {
        boolean foodLow = v.problems().active(ProblemType.FOOD_LOW).isPresent();
        for (CitizenRecord c : v.citizens().alive()) {
            if (!foodLow && c.jobOverride() != null) {
                c.setJobOverride(null);
                v.log("JOB_ASSIGNED", now, null, c.id(), null, null, null, Provenance.VILLAGE_DIRECTOR,
                        "job", c.role().name(), "reason", "food recovered");
            }
        }
        if (!foodLow || v.snapshot().cropPlots() == 0) {
            return;   // nothing to farm: moving a courier would only stop deliveries
        }
        boolean helperAssigned = v.citizens().alive().stream().anyMatch(c -> c.jobOverride() == Role.FARMER);
        if (helperAssigned) {
            return;
        }
        for (CitizenRecord c : v.citizens().alive()) {
            boolean carrying = v.requests().active().stream()
                    .anyMatch(r -> r.state() == ResourceRequest.State.CLAIMED && c.id().equals(r.carrier()));
            if (c.role() == Role.GENERAL && !carrying) {
                c.setJobOverride(Role.FARMER);
                v.log("JOB_ASSIGNED", now, null, c.id(), null, null, null, Provenance.VILLAGE_DIRECTOR,
                        "job", Role.FARMER.name(), "reason", "FOOD_LOW");
                return;
            }
        }
    }

    /** HOMELESS -> start a hut on a free site, unless food is short or a hut is already under way. */
    static void planHousing(VillageState v, long now, WorldPort world) {
        if (v.problems().active(ProblemType.HOMELESS).isEmpty()
                || v.problems().active(ProblemType.FOOD_LOW).isPresent()
                || v.construction().hasActive(ConstructionProject.Purpose.HOUSING, null)) {
            return;
        }
        Optional<CitizenRecord> builder = v.citizens().firstAlive(Role.BUILDER);
        if (builder.isEmpty() || v.construction().activeFor(builder.get().id()).isPresent()) {
            return;
        }
        Optional<Pos> site = world.findBuildSite(v.center(), 5, 5, 6, 40, occupied(v));
        if (site.isEmpty()) {
            boolean recentlyLogged = v.ledger().ofType("SITE_NOT_FOUND").stream()
                    .anyMatch(e -> now - e.gameTime() < 24000);
            if (recentlyLogged) {
                return;
            }
            v.log("SITE_NOT_FOUND", now, v.center(), null, null, null, null, Provenance.VILLAGE_DIRECTOR,
                    "blueprint", Blueprints.HUT);
            return;
        }
        ConstructionProject p = v.construction().start(Blueprints.HUT, site.get(), builder.get().id(), now)
                .withPurpose(ConstructionProject.Purpose.HOUSING, null, 0);
        v.log("PROJECT_STARTED", now, site.get(), builder.get().id(), p.id(), null, p.id(), Provenance.VILLAGE_DIRECTOR,
                "blueprint", Blueprints.HUT, "purpose", "HOUSING");
    }

    /** Footprints the director must not build over: buildings, projects, pen, warehouse. */
    public static List<Box> occupied(VillageState v) {
        List<Box> out = new ArrayList<>();
        for (Building b : v.construction().buildings()) {
            out.add(Box.of(b.origin().offset(-1, -1, -1), b.origin().offset(5, 4, 5)));
        }
        for (ConstructionProject p : v.construction().active()) {
            out.add(Box.of(p.origin().offset(-1, -1, -1), p.origin().offset(5, 4, 5)));
        }
        if (v.pen() != null) {
            out.add(Box.of(v.pen().min().offset(-2, -2, -2), v.pen().max().offset(2, 2, 2)));
        }
        if (v.warehouse() != null) {
            out.add(Box.of(v.warehouse().offset(-2, -2, -2), v.warehouse().offset(2, 2, 2)));
        }
        return out;
    }

    /**
     * Proof 11: once a chicken_collector revision is adopted, the builder installs its blueprint at
     * the pen. The builder needs no understanding of hoppers: the design is the village's.
     */
    static void deployDesigns(VillageState v, long now) {
        Optional<DesignRevision> design = v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR);
        if (design.isEmpty() || v.pen() == null || design.get().blueprintId() == null) {
            return;
        }
        String bp = design.get().blueprintId();
        if (v.construction().hasBuilding(bp) || v.construction().hasActive(ConstructionProject.Purpose.DESIGN, null)) {
            return;
        }
        startDesignProject(v, now, design.get(), originFor(v.pen(), bp));
    }

    /**
     * Engineering, not science: when eggs are still wasted although an adopted collector is installed,
     * the village builds the next revision as a prototype (a hopper under every pen cell). Its field
     * trial decides adoption; HOPPER_PULLS_ITEM itself is already known.
     */
    static void improveDesigns(VillageState v, long now) {
        Optional<ResearchProblem> problem = v.problems().active(ProblemType.EGGS_WASTED)
                .filter(p -> p.status() == ResearchProblem.Status.OPEN);
        Optional<DesignRevision> adopted = v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR);
        if (problem.isEmpty() || adopted.isEmpty() || v.pen() == null
                || !v.construction().hasBuilding(adopted.get().blueprintId())
                || v.designs().inTrial(DesignRegistry.CHICKEN_COLLECTOR).isPresent()
                || v.construction().hasActive(ConstructionProject.Purpose.DESIGN, null)) {
            return;
        }
        int w = v.pen().max().x() - v.pen().min().x() + 1;
        int d = v.pen().max().z() - v.pen().min().z() + 1;
        String bp = Blueprints.fullCollectorId(w, d);
        if (bp.equals(adopted.get().blueprintId()) || w * d == 1) {
            return;   // already the best this pen allows; the problem needs a new idea
        }
        UUID inventor = v.citizens().firstAlive(Role.RESEARCHER).or(() -> v.citizens().firstAlive(Role.BUILDER))
                .map(CitizenRecord::id).orElse(null);
        DesignRevision proto = v.designs().record(DesignRegistry.CHICKEN_COLLECTOR,
                java.util.Map.of(dev.emerald.core.world.ItemIds.HOPPER, w * d, dev.emerald.core.world.ItemIds.CHEST, 1),
                0, 0, false, inventor, null, ExperimentEngine.COLLECTOR_REQUIRES, bp, now);
        v.problems().setStatus(problem.get().id(), ResearchProblem.Status.RESEARCHING, now, "prototype " + proto.label());
        v.log("PROTOTYPE_STARTED", now, v.pen().min(), inventor, problem.get().id(), problem.get().evidence(),
                problem.get().id(), Provenance.VILLAGE_DIRECTOR, "design", proto.label(), "blueprint", bp);
        startDesignProject(v, now, proto, originFor(v.pen(), bp));
    }

    /**
     * Field trial of an installed prototype, from bus evidence only: of the eggs laid in the pen since
     * installation, how many were pulled by a hopper? Adopt at 90%+, reject below 50% after enough eggs.
     */
    static void evaluatePrototypes(VillageState v, long now, SimulationConfig cfg) {
        Optional<DesignRevision> proto = v.designs().inTrial(DesignRegistry.CHICKEN_COLLECTOR);
        if (proto.isEmpty() || v.pen() == null) {
            return;
        }
        Optional<Building> installed = v.construction().buildings().stream()
                .filter(b -> b.type().equals(proto.get().blueprintId())).findFirst();
        if (installed.isEmpty()) {
            return;
        }
        long since = installed.get().completedAt();
        var eggs = v.observations().matching(o -> o.type() == dev.emerald.core.observe.ObservationType.ITEM_SPAWNED
                && o.isItem(dev.emerald.core.world.ItemIds.EGG) && o.gameTime() >= since
                && v.pen().containsWithin(o.pos(), 1));
        if (eggs.size() < 4) {
            return;
        }
        long pulled = eggs.stream().filter(e -> v.observations()
                .findCausedBy(dev.emerald.core.observe.ObservationType.HOPPER_PULLED, e.id()).isPresent()).count();
        double ratio = (double) pulled / eggs.size();
        Optional<ResearchProblem> problem = v.problems().active(ProblemType.EGGS_WASTED);
        if (ratio >= 0.9) {
            v.designs().conclude(DesignRegistry.CHICKEN_COLLECTOR, proto.get().revision(), (int) pulled, true);
            problem.ifPresent(p -> v.problems().setStatus(p.id(), ResearchProblem.Status.RESOLVED, now,
                    "solved by " + proto.get().designId() + " Mk " + proto.get().revision()));
            v.log("DESIGN_ADOPTED", now, installed.get().origin(), proto.get().inventor(), null, eggs.get(eggs.size() - 1).id(),
                    installed.get().projectId(), Provenance.SYSTEM_OBSERVED, "design", proto.get().designId(),
                    "revision", String.valueOf(proto.get().revision()), "collected", pulled + "/" + eggs.size());
        } else if (ratio < 0.5 || eggs.size() >= 12) {
            v.designs().conclude(DesignRegistry.CHICKEN_COLLECTOR, proto.get().revision(), (int) pulled, false);
            problem.ifPresent(p -> v.problems().setStatus(p.id(), ResearchProblem.Status.OPEN, now, "prototype failed"));
            v.log("PROTOTYPE_FAILED", now, installed.get().origin(), proto.get().inventor(), null, null,
                    installed.get().projectId(), Provenance.SYSTEM_OBSERVED, "design", proto.get().designId(),
                    "revision", String.valueOf(proto.get().revision()), "collected", pulled + "/" + eggs.size());
        }
    }

    static void startDesignProject(VillageState v, long now, DesignRevision design, Pos origin) {
        Optional<CitizenRecord> builder = v.citizens().firstAlive(Role.BUILDER);
        if (builder.isEmpty() || v.construction().activeFor(builder.get().id()).isPresent()) {
            return;
        }
        ConstructionProject p = v.construction().start(design.blueprintId(), origin, builder.get().id(), now)
                .withPurpose(ConstructionProject.Purpose.DESIGN, design.designId(), design.revision());
        v.log("PROJECT_STARTED", now, origin, builder.get().id(), p.id(), null, p.id(), Provenance.VILLAGE_DIRECTOR,
                "blueprint", design.blueprintId(), "purpose", "DESIGN", "design", design.label());
    }

    /** Mk I goes at the apparatus (pen floor centre); full collectors at the pen floor's min corner. */
    public static Pos originFor(Box pen, String blueprintId) {
        return blueprintId.equals(Blueprints.COLLECTOR) ? apparatusFor(pen) : pen.min().below();
    }

    static void startResearch(VillageState v, long now, HypothesisSource hypotheses) {
        if (v.pen() == null || !v.experiments().active().isEmpty()
                || v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).isPresent()) {
            return;   // the principle is known and a design adopted: improvement is engineering, not science
        }
        Optional<ResearchProblem> problem = v.problems().active(ProblemType.EGGS_WASTED)
                .filter(p -> p.status() == ResearchProblem.Status.OPEN);
        Optional<CitizenRecord> researcher = v.citizens().firstAlive(Role.RESEARCHER);
        if (problem.isEmpty() || researcher.isEmpty()) {
            return;
        }
        var grounding = problem.get().type().groundingConcept();
        if (grounding != null && researcher.get().knowledge().state(grounding) == dev.emerald.core.knowledge.KnowState.UNKNOWN) {
            return;   // the researcher investigates first (see InvestigateRoutine)
        }
        Optional<Hypothesis> h = hypotheses.next(v, problem.get(), researcher.get(), now);
        if (h.isPresent()) {
            ExperimentEngine.propose(v, h.get(), researcher.get(), apparatusFor(v.pen()), now);
        }
    }

    /**
     * Growth: a newcomer arrives when there is a spare bed, food is at least twice the reserve, the
     * village is under its cap, and nobody arrived recently. Role follows unmet demand, then bias.
     */
    public static Optional<CitizenRecord> considerImmigration(VillageState v, long now, ItemStore warehouse, SimulationConfig cfg) {
        if (warehouse == null) {
            return Optional.empty();
        }
        int alive = v.citizens().alive().size();
        if (alive >= cfg.maxPopulation()
                || v.construction().totalBeds() <= adults(v)
                || storedFood(warehouse) < 2 * foodTarget(v)
                || (v.lastImmigration() != Long.MIN_VALUE && now - v.lastImmigration() < cfg.immigrationCooldownTicks())) {
            return Optional.empty();
        }
        Random rng = new Random(v.id().getLeastSignificantBits() ^ now);
        Role role;
        long blocked = v.requests().active().stream().filter(r -> r.state() == ResourceRequest.State.OPEN).count();
        if (blocked >= 2) {
            role = Role.GENERAL;
        } else if (rng.nextInt(3) == 0) {
            role = Role.FARMER;
        } else {
            role = v.bias().favouredRole();
        }
        String name = NEWCOMER_NAMES[rng.nextInt(NEWCOMER_NAMES.length)];
        CitizenRecord c = new CitizenRecord(new UUID(rng.nextLong(), rng.nextLong()), v.id(), name, role);
        c.setAgeDays(18 + rng.nextInt(30));
        c.setHunger(20);
        c.setEnergy(80);
        c.setCuriosity(20 + rng.nextInt(70));
        c.setCaution(20 + rng.nextInt(70));
        c.setHome(v.center());
        v.citizens().add(c);
        v.setLastImmigration(now);
        v.log("CITIZEN_ARRIVED", now, v.center(), c.id(), null, null, null, Provenance.VILLAGE_DIRECTOR,
                "name", name, "role", role.name(), "bias", v.bias().name());
        return Optional.of(c);
    }

    /** The hopper goes in the pen floor: centre column, one below the pen's lowest air layer. */
    public static Pos apparatusFor(Box pen) {
        return new Pos((pen.min().x() + pen.max().x()) / 2, pen.min().y() - 1, (pen.min().z() + pen.max().z()) / 2);
    }

    /** True if any experiment is waiting for its apparatus. */
    public static boolean awaitingSetup(VillageState v) {
        return v.experiments().active().stream().anyMatch(e -> e.phase() == Experiment.Phase.SETUP);
    }
}
