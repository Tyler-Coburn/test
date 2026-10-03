package dev.emerald.core.request;

import dev.emerald.core.data.Data;
import dev.emerald.core.world.Pos;

import java.util.Map;
import java.util.UUID;

/**
 * One unmet material need. Lifecycle: OPEN -> CLAIMED -> DELIVERED, with BLOCKED (no stock) and
 * CANCELLED (already satisfied / abandoned) as side exits. Illegal transitions throw.
 */
public final class ResourceRequest {
    public enum State { OPEN, CLAIMED, DELIVERED, BLOCKED, CANCELLED }

    private final UUID id;
    private final UUID requester;
    private final String itemId;
    private final int count;
    private final Pos deliverTo;
    private final UUID projectId;
    private final long createdAt;
    private State state = State.OPEN;
    private UUID carrier;
    private boolean pickedUp;
    private String reason = "";
    private long updatedAt;

    public ResourceRequest(UUID id, UUID requester, String itemId, int count, Pos deliverTo, UUID projectId, long createdAt) {
        if (count <= 0) {
            throw new IllegalArgumentException("Request count must be positive");
        }
        this.id = id;
        this.requester = requester;
        this.itemId = itemId;
        this.count = count;
        this.deliverTo = deliverTo;
        this.projectId = projectId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID requester() { return requester; }
    public String itemId() { return itemId; }
    public int count() { return count; }
    public Pos deliverTo() { return deliverTo; }
    public UUID projectId() { return projectId; }
    public long createdAt() { return createdAt; }
    public State state() { return state; }
    public UUID carrier() { return carrier; }
    public boolean pickedUp() { return pickedUp; }
    public String reason() { return reason; }
    public long updatedAt() { return updatedAt; }

    public boolean isActive() {
        return state == State.OPEN || state == State.CLAIMED || state == State.BLOCKED;
    }

    void claim(UUID carrier, long now) {
        require(State.OPEN, "claim");
        this.carrier = carrier;
        this.state = State.CLAIMED;
        this.reason = "";
        this.updatedAt = now;
    }

    void markPickedUp(long now) {
        require(State.CLAIMED, "pick up");
        this.pickedUp = true;
        this.updatedAt = now;
    }

    void deliver(long now) {
        require(State.CLAIMED, "deliver");
        if (!pickedUp) {
            throw new IllegalStateException("Request " + id + " cannot be delivered before pickup");
        }
        this.state = State.DELIVERED;
        this.updatedAt = now;
    }

    void block(String why, long now) {
        if (state != State.OPEN && state != State.CLAIMED) {
            throw new IllegalStateException("Cannot block request in state " + state);
        }
        this.state = State.BLOCKED;
        this.carrier = null;
        this.pickedUp = false;
        this.reason = why;
        this.updatedAt = now;
    }

    void reopen(long now) {
        require(State.BLOCKED, "reopen");
        this.state = State.OPEN;
        this.reason = "";
        this.updatedAt = now;
    }

    void cancel(String why, long now) {
        if (!isActive()) {
            throw new IllegalStateException("Cannot cancel request in state " + state);
        }
        this.state = State.CANCELLED;
        this.reason = why;
        this.updatedAt = now;
    }

    private void require(State expected, String action) {
        if (state != expected) {
            throw new IllegalStateException("Cannot " + action + " request " + id + " in state " + state);
        }
    }

    public String summary() {
        return id.toString().substring(0, 8) + " " + count + "x " + itemId + " " + state
                + (carrier != null ? " carrier=" + carrier.toString().substring(0, 8) : "")
                + (reason.isEmpty() ? "" : " (" + reason + ")");
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        Data.putUuid(m, "id", id);
        Data.putUuid(m, "requester", requester);
        m.put("item", itemId);
        m.put("count", count);
        if (deliverTo != null) m.put("deliverTo", deliverTo.toMap());
        Data.putUuid(m, "project", projectId);
        m.put("created", createdAt);
        m.put("updated", updatedAt);
        m.put("state", state.name());
        Data.putUuid(m, "carrier", carrier);
        m.put("pickedUp", pickedUp);
        m.put("reason", reason);
        return m;
    }

    static ResourceRequest fromMap(Map<String, Object> m) {
        ResourceRequest r = new ResourceRequest(Data.uuid(m, "id"), Data.uuid(m, "requester"), Data.str(m, "item"),
                Data.i(m, "count"), Pos.fromMapOrNull(Data.subOrNull(m, "deliverTo")), Data.uuidOrNull(m, "project"),
                Data.l(m, "created"));
        r.updatedAt = Data.lOr(m, "updated", r.createdAt);
        r.state = Data.enumOf(m, "state", State.class);
        r.carrier = Data.uuidOrNull(m, "carrier");
        r.pickedUp = Data.b(m, "pickedUp", false);
        r.reason = Data.strOr(m, "reason", "");
        return r;
    }
}
