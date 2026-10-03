package dev.emerald.core.job;

import dev.emerald.core.utility.NeedsModel;
import dev.emerald.core.world.Pos;

import java.util.Map;

/** Eat carried food; otherwise walk to the warehouse, take one real food item and eat it. */
public final class EatRoutine extends AbstractRoutine {
    @Override
    public TaskType task() {
        return TaskType.EAT;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        var c = ctx.citizen();
        String eaten = NeedsModel.eatFromCarried(c);
        if (eaten != null) {
            detail = "ate " + eaten;
            return RoutineStatus.DONE;
        }
        Pos wh = ctx.village().warehouse();
        if (wh == null) {
            return fail("hungry, no food carried and no warehouse");
        }
        detail = "going to warehouse for food";
        if (!walk(ctx, wh, 2.5)) {
            return walkTimedOut() ? fail("warehouse unreachable") : RoutineStatus.RUNNING;
        }
        var store = ctx.world().warehouse();
        if (store != null) {
            String best = null;
            int bestValue = 0;
            for (Map.Entry<String, Integer> e : NeedsModel.FOOD_VALUES.entrySet()) {
                if (store.count(e.getKey()) > 0 && e.getValue() > bestValue) {
                    best = e.getKey();
                    bestValue = e.getValue();
                }
            }
            if (best != null && store.extract(best, 1) == 1) {
                c.carried().insert(best, 1);
                detail = "ate " + NeedsModel.eatFromCarried(c) + " from warehouse";
                return RoutineStatus.DONE;
            }
        }
        return fail("no food in warehouse");
    }
}
