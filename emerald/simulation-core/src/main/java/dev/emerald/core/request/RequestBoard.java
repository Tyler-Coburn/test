package dev.emerald.core.request;

import dev.emerald.core.item.ItemStore;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Village-wide list of material requests and the v1 resolver chain:
 * <ol>
 *   <li>requester already holds it -> CANCELLED</li>
 *   <li>warehouse has unreserved stock -> stays OPEN for a carrier to CLAIM (claim reserves stock)</li>
 *   <li>otherwise -> BLOCKED, visible to the village director</li>
 * </ol>
 * No recursive crafting resolver in v1.
 */
public final class RequestBoard {
    private final Map<UUID, ResourceRequest> requests = new LinkedHashMap<>();

    public ResourceRequest open(UUID requester, String itemId, int count, Pos deliverTo, UUID projectId, long now) {
        ResourceRequest r = new ResourceRequest(UUID.randomUUID(), requester, itemId, count, deliverTo, projectId, now);
        requests.put(r.id(), r);
        return r;
    }

    public Optional<ResourceRequest> get(UUID id) {
        return Optional.ofNullable(requests.get(id));
    }

    public Collection<ResourceRequest> all() {
        return Collections.unmodifiableCollection(requests.values());
    }

    public List<ResourceRequest> active() {
        return requests.values().stream().filter(ResourceRequest::isActive).toList();
    }

    public List<ResourceRequest> activeFor(UUID requester, String itemId) {
        return requests.values().stream()
                .filter(r -> r.isActive() && r.requester().equals(requester) && r.itemId().equals(itemId))
                .toList();
    }

    /** Stock promised to claimed-but-not-yet-picked-up requests. */
    public int reserved(String itemId) {
        int sum = 0;
        for (ResourceRequest r : requests.values()) {
            if (r.state() == ResourceRequest.State.CLAIMED && !r.pickedUp() && r.itemId().equals(itemId)) {
                sum += r.count();
            }
        }
        return sum;
    }

    public int available(ItemStore warehouse, String itemId) {
        return warehouse == null ? 0 : Math.max(0, warehouse.count(itemId) - reserved(itemId));
    }

    /** Runs the v1 resolver chain over every OPEN and BLOCKED request. */
    public void resolve(ItemStore warehouse, Function<UUID, ItemStore> heldBy, long now) {
        for (ResourceRequest r : new ArrayList<>(requests.values())) {
            if (r.state() != ResourceRequest.State.OPEN && r.state() != ResourceRequest.State.BLOCKED) {
                continue;
            }
            ItemStore held = heldBy.apply(r.requester());
            if (held != null && held.count(r.itemId()) >= r.count()) {
                r.cancel("already held by requester", now);
                continue;
            }
            int avail = available(warehouse, r.itemId());
            if (avail >= r.count()) {
                if (r.state() == ResourceRequest.State.BLOCKED) {
                    r.reopen(now);
                }
            } else if (r.state() == ResourceRequest.State.OPEN) {
                r.block(warehouse == null ? "no warehouse registered"
                        : "warehouse has " + avail + "/" + r.count() + " " + r.itemId(), now);
            }
        }
    }

    /** First OPEN request whose stock is available right now. */
    public Optional<ResourceRequest> nextClaimable(ItemStore warehouse) {
        for (ResourceRequest r : requests.values()) {
            if (r.state() == ResourceRequest.State.OPEN && available(warehouse, r.itemId()) >= r.count()) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public void claim(UUID requestId, UUID carrier, long now) {
        find(requestId).claim(carrier, now);
    }

    public void markPickedUp(UUID requestId, long now) {
        find(requestId).markPickedUp(now);
    }

    public void deliver(UUID requestId, long now) {
        find(requestId).deliver(now);
    }

    public void block(UUID requestId, String reason, long now) {
        find(requestId).block(reason, now);
    }

    public void cancel(UUID requestId, String reason, long now) {
        find(requestId).cancel(reason, now);
    }

    private ResourceRequest find(UUID id) {
        ResourceRequest r = requests.get(id);
        if (r == null) {
            throw new IllegalArgumentException("Unknown request " + id);
        }
        return r;
    }

    public List<Object> toList() {
        return requests.values().stream().<Object>map(ResourceRequest::toMap).toList();
    }

    public void loadFrom(List<Map<String, Object>> list) {
        requests.clear();
        for (Map<String, Object> m : list) {
            ResourceRequest r = ResourceRequest.fromMap(m);
            requests.put(r.id(), r);
        }
    }
}
