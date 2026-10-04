package dev.emerald.core.job;

import dev.emerald.core.event.Provenance;
import dev.emerald.core.request.ResourceRequest;
import dev.emerald.core.world.Pos;

import java.util.Optional;
import java.util.UUID;

/** Claim -> walk to warehouse -> extract real items -> walk to requester -> hand over -> DELIVERED. */
public final class CourierRoutine extends AbstractRoutine {
    private enum Step { CLAIM, TO_WAREHOUSE, TO_REQUESTER }

    private final UUID requestId;
    private Step step = Step.CLAIM;

    public CourierRoutine(UUID requestId) {
        this.requestId = requestId;
    }

    @Override
    public dev.emerald.core.citizen.SkillType skill() {
        return dev.emerald.core.citizen.SkillType.LOGISTICS;
    }

    @Override
    public TaskType task() {
        return TaskType.DELIVER_REQUEST;
    }

    @Override
    public RoutineStatus step(RoutineContext ctx) {
        var v = ctx.village();
        var me = ctx.citizen();
        Optional<ResourceRequest> found = v.requests().get(requestId);
        if (found.isEmpty()) {
            return fail("request vanished");
        }
        ResourceRequest r = found.get();
        long now = ctx.now();
        switch (step) {
            case CLAIM -> {
                if (r.state() == ResourceRequest.State.CLAIMED && me.id().equals(r.carrier())) {
                    step = r.pickedUp() ? Step.TO_REQUESTER : Step.TO_WAREHOUSE;
                } else if (r.state() == ResourceRequest.State.OPEN
                        && v.requests().available(ctx.world().warehouse(), r.itemId()) >= r.count()) {
                    v.requests().claim(r.id(), me.id(), now);
                    v.log("REQUEST_CLAIMED", now, null, me.id(), r.requester(), null, r.id(), Provenance.JOB_SYSTEM,
                            "item", r.itemId(), "count", String.valueOf(r.count()));
                    step = Step.TO_WAREHOUSE;
                } else {
                    detail = "request no longer claimable (" + r.state() + ")";
                    return RoutineStatus.NOTHING;
                }
                return RoutineStatus.RUNNING;
            }
            case TO_WAREHOUSE -> {
                Pos wh = v.warehouse();
                if (wh == null) {
                    v.requests().block(r.id(), "no warehouse registered", now);
                    return fail("no warehouse registered");
                }
                detail = "fetching " + r.count() + "x " + r.itemId();
                if (!walk(ctx, wh, 2.5)) {
                    if (walkTimedOut()) {
                        v.requests().block(r.id(), "courier could not reach warehouse", now);
                        return fail("warehouse unreachable");
                    }
                    return RoutineStatus.RUNNING;
                }
                var store = ctx.world().warehouse();
                int got = store == null ? 0 : store.extract(r.itemId(), r.count());
                int carried = me.carried().insert(r.itemId(), got);
                if (carried < got) {
                    store.insert(r.itemId(), got - carried);
                }
                if (carried < r.count()) {
                    if (carried > 0 && store != null) {
                        me.carried().extract(r.itemId(), store.insert(r.itemId(), carried));
                    }
                    v.requests().block(r.id(), "warehouse had " + got + "/" + r.count() + " at pickup", now);
                    return fail("stock missing at pickup");
                }
                v.requests().markPickedUp(r.id(), now);
                step = Step.TO_REQUESTER;
                return RoutineStatus.RUNNING;
            }
            case TO_REQUESTER -> {
                Optional<Pos> target = ctx.world().bodyPosition(r.requester());
                Pos dest = target.orElse(r.deliverTo());
                if (dest == null) {
                    detail = "waiting for requester body to load";
                    return RoutineStatus.RUNNING;
                }
                detail = "delivering " + r.count() + "x " + r.itemId() + " to " + dest;
                if (!walk(ctx, dest, 2.5)) {
                    return walkTimedOut() ? fail("requester unreachable") : RoutineStatus.RUNNING;
                }
                var requester = v.citizens().get(r.requester());
                if (requester.isEmpty() || !requester.get().alive()) {
                    v.requests().cancel(r.id(), "requester gone", now);
                    return fail("requester gone; items still carried");
                }
                int moved = me.carried().extract(r.itemId(), r.count());
                int accepted = requester.get().carried().insert(r.itemId(), moved);
                if (accepted < moved) {
                    me.carried().insert(r.itemId(), moved - accepted);
                    return fail("requester inventory full");
                }
                v.requests().deliver(r.id(), now);
                v.log("REQUEST_DELIVERED", now, dest, me.id(), r.requester(), null, r.id(), Provenance.JOB_SYSTEM,
                        "item", r.itemId(), "count", String.valueOf(r.count()));
                detail = "delivered " + r.count() + "x " + r.itemId();
                return RoutineStatus.DONE;
            }
        }
        return RoutineStatus.RUNNING;
    }
}
