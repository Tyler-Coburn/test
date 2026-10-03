package dev.emerald.core.event;

import dev.emerald.core.EmeraldConstants;
import dev.emerald.core.world.Pos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-only history of important events. Entries are never edited; the oldest age out when the
 * persisted tail exceeds its cap. VillageState stays the operational truth; the ledger is evidence
 * and history.
 */
public final class EventLedger {
    private final Deque<LedgerEvent> events = new ArrayDeque<>();
    private int cap;
    private long totalAppended;

    public EventLedger(int cap) {
        this.cap = Math.max(16, cap);
    }

    public void setCap(int cap) {
        this.cap = Math.max(16, cap);
        trim();
    }

    public LedgerEvent append(String type, long gameTime, Pos pos, UUID actor, UUID subject,
                              UUID causationId, UUID correlationId, Provenance provenance,
                              Map<String, String> payload) {
        LedgerEvent e = new LedgerEvent(UUID.randomUUID(), type, gameTime, null, pos, actor, subject,
                causationId, correlationId, provenance, EmeraldConstants.SCHEMA_VERSION, payload);
        events.addLast(e);
        totalAppended++;
        trim();
        return e;
    }

    public Optional<LedgerEvent> get(UUID id) {
        for (LedgerEvent e : events) {
            if (e.eventId().equals(id)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    public List<LedgerEvent> recent(int n) {
        List<LedgerEvent> all = new ArrayList<>(events);
        return List.copyOf(all.subList(Math.max(0, all.size() - n), all.size()));
    }

    public List<LedgerEvent> ofType(String type) {
        return events.stream().filter(e -> e.type().equals(type)).toList();
    }

    public List<LedgerEvent> byCorrelation(UUID correlationId) {
        return events.stream().filter(e -> correlationId.equals(e.correlationId())).toList();
    }

    public int size() {
        return events.size();
    }

    public long totalAppended() {
        return totalAppended;
    }

    private void trim() {
        while (events.size() > cap) {
            events.removeFirst();
        }
    }

    public List<Object> toList() {
        return events.stream().<Object>map(LedgerEvent::toMap).toList();
    }

    public void loadFrom(List<Map<String, Object>> entries, long total) {
        events.clear();
        for (Map<String, Object> m : entries) {
            events.addLast(LedgerEvent.fromMap(m));
        }
        totalAppended = Math.max(total, events.size());
        trim();
    }
}
