package dev.emerald.core.job;

import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Find mature wheat -> walk -> harvest -> replant -> repeat -> walk to warehouse -> deposit. No GOAP, no LLM. */
public final class FarmerRoutine extends AbstractRoutine {
    /** Carried wheat that triggers a trip to the warehouse. */
    public static final int CARRY_LIMIT = 32;
    /** Seeds a farmer keeps for replanting; the rest are deposited. */
    public static final int SEED_RESERVE = 8;

    private enum Step { FIND, TO_CROP, TO_WAREHOUSE }

    private Step step = Step.FIND;
    private Pos crop;
    private UUID lastHarvest;

    @Override
    public TaskType task() {
        return TaskType.HARVEST_CROPS;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        var c = ctx.citizen();
        switch (step) {
            case FIND -> {
                if (c.carried().count(ItemIds.WHEAT) >= CARRY_LIMIT) {
                    step = Step.TO_WAREHOUSE;
                    return RoutineStatus.RUNNING;
                }
                Pos center = c.work() != null ? c.work() : ctx.village().center();
                Optional<Pos> found = ctx.world().findMatureCrop(center, ctx.config().farmRadius());
                if (found.isPresent()) {
                    crop = found.get();
                    step = Step.TO_CROP;
                    detail = "walking to wheat at " + crop;
                } else if (c.carried().count(ItemIds.WHEAT) > 0) {
                    step = Step.TO_WAREHOUSE;
                } else {
                    detail = "no mature wheat";
                    return RoutineStatus.DONE;
                }
                return RoutineStatus.RUNNING;
            }
            case TO_CROP -> {
                if (!walk(ctx, crop, 2.5)) {
                    return walkTimedOut() ? fail("crop at " + crop + " unreachable") : RoutineStatus.RUNNING;
                }
                Map<String, Integer> drops = ctx.world().harvestCrop(crop, c.carried());
                if (!drops.isEmpty()) {
                    WorldObservation obs = ctx.village().observations().record(ObservationType.CROP_HARVESTED,
                            ctx.world().dimension(), crop, ItemIds.WHEAT, c.id(), ctx.now(), null);
                    lastHarvest = obs.id();
                    ctx.world().replant(crop, c.carried());
                    detail = "harvested " + drops;
                }
                step = Step.FIND;
                return RoutineStatus.RUNNING;
            }
            case TO_WAREHOUSE -> {
                Pos wh = ctx.village().warehouse();
                if (wh == null) {
                    return fail("no warehouse registered");
                }
                detail = "carrying " + c.carried().count(ItemIds.WHEAT) + " wheat to warehouse";
                if (!walk(ctx, wh, 2.5)) {
                    return walkTimedOut() ? fail("warehouse unreachable") : RoutineStatus.RUNNING;
                }
                var store = ctx.world().warehouse();
                if (store == null) {
                    return fail("warehouse is not a loaded container");
                }
                int wheat = c.carried().count(ItemIds.WHEAT);
                int stored = store.insert(ItemIds.WHEAT, wheat);
                c.carried().extract(ItemIds.WHEAT, stored);
                int spareSeeds = Math.max(0, c.carried().count(ItemIds.WHEAT_SEEDS) - SEED_RESERVE);
                c.carried().extract(ItemIds.WHEAT_SEEDS, store.insert(ItemIds.WHEAT_SEEDS, spareSeeds));
                if (stored > 0) {
                    ctx.village().observations().record(ObservationType.ITEM_STORED,
                            ctx.world().dimension(), wh, ItemIds.WHEAT, c.id(), ctx.now(), lastHarvest);
                }
                if (stored < wheat) {
                    return fail("warehouse full (" + stored + "/" + wheat + " stored)");
                }
                detail = "deposited " + stored + " wheat";
                return RoutineStatus.DONE;
            }
        }
        return RoutineStatus.RUNNING;
    }
}
