package dev.emerald.core.job;

import dev.emerald.core.construction.Blueprint;
import dev.emerald.core.construction.BlueprintBlock;
import dev.emerald.core.construction.Building;
import dev.emerald.core.construction.ConstructionProject;
import dev.emerald.core.director.VillageDirector;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.request.ResourceRequest;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a blueprint progressively from real items. Each step re-diffs the blueprint against the
 * world, requests missing materials through the RequestBoard (one active chunk per item), and places
 * the next block the builder actually carries.
 */
public final class BuilderRoutine extends AbstractRoutine {
    private final UUID projectId;

    public BuilderRoutine(UUID projectId) {
        this.projectId = projectId;
    }

    @Override
    public dev.emerald.core.citizen.SkillType skill() {
        return dev.emerald.core.citizen.SkillType.BUILDING;
    }

    @Override
    public TaskType task() {
        return TaskType.BUILD;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        var v = ctx.village();
        var me = ctx.citizen();
        long now = ctx.now();
        Optional<ConstructionProject> found = v.construction().get(projectId);
        if (found.isEmpty() || found.get().state() != ConstructionProject.State.ACTIVE) {
            return RoutineStatus.NOTHING;
        }
        ConstructionProject project = found.get();
        Optional<Blueprint> blueprint = ctx.blueprints().get(project.blueprintId());
        if (blueprint.isEmpty()) {
            return fail("unknown blueprint " + project.blueprintId());
        }
        List<BlueprintBlock> remaining = remaining(ctx, blueprint.get(), project.origin());
        if (remaining == null) {
            detail = "site not loaded";
            return RoutineStatus.RUNNING;
        }
        if (remaining.isEmpty()) {
            project.complete(now);
            int beds = project.blueprintId().equals(dev.emerald.core.construction.Blueprints.HUT) ? VillageDirector.BEDS_PER_HUT : 0;
            Building b = v.construction().register(new Building(UUID.randomUUID(), project.blueprintId(),
                    project.origin(), now, project.id(), beds));
            v.snapshot().clearRemaining(project.id());
            if (project.purpose() == ConstructionProject.Purpose.DESIGN) {
                v.log("DESIGN_INSTALLED", now, project.origin(), me.id(), b.id(), null, project.id(), Provenance.JOB_SYSTEM,
                        "design", String.valueOf(project.designId()), "revision", String.valueOf(project.designRevision()));
            }
            v.log("BUILDING_COMPLETED", now, project.origin(), me.id(), b.id(), null, project.id(),
                    Provenance.JOB_SYSTEM, "blueprint", project.blueprintId(), "placed", String.valueOf(project.placed()));
            detail = "completed " + project.blueprintId();
            return RoutineStatus.DONE;
        }

        requestShortfall(ctx, project, Blueprint.materialsOf(remaining));

        BlueprintBlock next = null;
        for (BlueprintBlock b : remaining) {
            if (me.carried().count(b.itemId()) > 0) {
                next = b;
                break;
            }
        }
        if (next == null) {
            detail = "waiting for materials (" + remaining.size() + " blocks left)";
            return RoutineStatus.RUNNING;
        }
        Pos target = project.origin().offset(next.rel());
        detail = "placing " + next.blockId() + " at " + target + " (" + remaining.size() + " left)";
        if (!walk(ctx, target, 3.5)) {
            return walkTimedOut() ? fail("cannot reach " + target) : RoutineStatus.RUNNING;
        }
        if (!ctx.world().place(target, next, me.carried())) {
            return fail("placement refused at " + target);
        }
        project.notePlaced();
        v.economy().consumed(next.itemId(), 1, now);
        me.addXp(dev.emerald.core.citizen.SkillType.BUILDING, 1);
        v.observations().record(ObservationType.BLOCK_PLACED, ctx.world().dimension(), target, next.itemId(),
                me.id(), now, null);
        return RoutineStatus.RUNNING;
    }

    /** Blocks still to place, or null if part of the site is not loaded. */
    private static List<BlueprintBlock> remaining(RoutineContext ctx, Blueprint bp, Pos origin) {
        List<BlueprintBlock> out = new ArrayList<>();
        for (BlueprintBlock b : bp.blocks()) {
            Pos p = origin.offset(b.rel());
            if (!ctx.world().isLoaded(p)) {
                return null;
            }
            if (!ctx.world().matches(p, b) && !ctx.village().pending().isQueued(p)) {
                out.add(b);
            }
        }
        return out;
    }

    private static void requestShortfall(RoutineContext ctx, ConstructionProject project, Map<String, Integer> needed) {
        var v = ctx.village();
        var me = ctx.citizen();
        int chunk = Math.max(1, ctx.config().plankRequestCount());
        for (Map.Entry<String, Integer> e : needed.entrySet()) {
            String item = e.getKey();
            int shortfall = e.getValue() - me.carried().count(item);
            if (shortfall <= 0) {
                continue;
            }
            List<ResourceRequest> active = v.requests().activeFor(me.id(), item);
            if (!active.isEmpty()) {
                continue;
            }
            int amount = Math.min(shortfall, chunk);
            ResourceRequest r = v.requests().open(me.id(), item, amount, project.origin(), project.id(), ctx.now());
            v.log("REQUEST_OPENED", ctx.now(), project.origin(), me.id(), null, null, r.id(), Provenance.JOB_SYSTEM,
                    "item", item, "count", String.valueOf(amount), "project", project.id().toString());
        }
    }
}
