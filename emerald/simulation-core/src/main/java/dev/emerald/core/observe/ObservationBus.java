package dev.emerald.core.observe;

import dev.emerald.core.world.Pos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The only entry point for facts. World adapters call {@link #record}; research, knowledge and
 * problem detection read from here. Model output and dialogue never reach this class.
 *
 * <p>The persisted tail is bounded ({@code cap}); older observations age out unless something
 * (an experiment, a knowledge entry) already copied their id as evidence.
 */
public final class ObservationBus {
    private final Deque<WorldObservation> log = new ArrayDeque<>();
    private final List<Consumer<WorldObservation>> listeners = new CopyOnWriteArrayList<>();
    private int cap;
    private long totalRecorded;

    public ObservationBus(int cap) {
        this.cap = Math.max(16, cap);
    }

    public void setCap(int cap) {
        this.cap = Math.max(16, cap);
        trim();
    }

    public WorldObservation record(ObservationType type, String dimension, Pos pos, String itemId,
                                   UUID witness, long gameTime, UUID causedBy) {
        WorldObservation obs = new WorldObservation(UUID.randomUUID(), type, dimension, pos, itemId,
                witness, gameTime, causedBy);
        log.addLast(obs);
        totalRecorded++;
        trim();
        for (Consumer<WorldObservation> listener : listeners) {
            listener.accept(obs);
        }
        return obs;
    }

    public void subscribe(Consumer<WorldObservation> listener) {
        listeners.add(listener);
    }

    public void clearListeners() {
        listeners.clear();
    }

    /** True if {@code obs} is a genuine entry of this bus (same id and content). */
    public boolean isAuthentic(WorldObservation obs) {
        return obs != null && get(obs.id()).filter(obs::equals).isPresent();
    }

    public Optional<WorldObservation> get(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        for (WorldObservation o : log) {
            if (o.id().equals(id)) {
                return Optional.of(o);
            }
        }
        return Optional.empty();
    }

    /** Most recent observation of {@code type} whose causedBy is {@code cause}. */
    public Optional<WorldObservation> findCausedBy(ObservationType type, UUID cause) {
        Iterator<WorldObservation> it = log.descendingIterator();
        while (it.hasNext()) {
            WorldObservation o = it.next();
            if (o.type() == type && cause.equals(o.causedBy())) {
                return Optional.of(o);
            }
        }
        return Optional.empty();
    }

    public List<WorldObservation> matching(Predicate<WorldObservation> filter) {
        List<WorldObservation> out = new ArrayList<>();
        for (WorldObservation o : log) {
            if (filter.test(o)) {
                out.add(o);
            }
        }
        return out;
    }

    /** Last {@code n} observations, oldest first. */
    public List<WorldObservation> recent(int n) {
        List<WorldObservation> all = new ArrayList<>(log);
        return List.copyOf(all.subList(Math.max(0, all.size() - n), all.size()));
    }

    public int size() {
        return log.size();
    }

    public long totalRecorded() {
        return totalRecorded;
    }

    private void trim() {
        while (log.size() > cap) {
            log.removeFirst();
        }
    }

    public List<Object> toList() {
        return log.stream().<Object>map(WorldObservation::toMap).toList();
    }

    public void loadFrom(List<Map<String, Object>> entries, long total) {
        log.clear();
        for (Map<String, Object> m : entries) {
            log.addLast(WorldObservation.fromMap(m));
        }
        totalRecorded = Math.max(total, log.size());
        trim();
    }
}
