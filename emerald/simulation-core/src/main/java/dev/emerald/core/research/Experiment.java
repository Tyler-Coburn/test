package dev.emerald.core.research;

import dev.emerald.core.data.Data;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A physical test of one hypothesis. SETUP until the apparatus is placed, then RUNNING until the
 * expected observation arrives (PASSED) or the window closes (FAILED if the test had an opportunity,
 * ABORTED if nothing happened that could have tested it).
 */
public final class Experiment {
    public enum Phase { SETUP, RUNNING, PASSED, FAILED, ABORTED }

    private final UUID id;
    private final Hypothesis hypothesis;
    private final UUID researcher;
    private final Pos apparatus;
    private final String apparatusItem;
    private final Box region;
    private final long createdAt;
    private Phase phase = Phase.SETUP;
    private long startedAt = -1;
    private long timeoutAt = -1;
    private long endedAt = -1;
    private UUID evidence;
    private final List<UUID> opportunities = new ArrayList<>();
    private int collected;
    private String note = "";

    public Experiment(UUID id, Hypothesis hypothesis, UUID researcher, Pos apparatus, String apparatusItem,
                      Box region, long createdAt) {
        this.id = id;
        this.hypothesis = hypothesis;
        this.researcher = researcher;
        this.apparatus = apparatus;
        this.apparatusItem = apparatusItem;
        this.region = region;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public Hypothesis hypothesis() { return hypothesis; }
    public UUID researcher() { return researcher; }
    public Pos apparatus() { return apparatus; }
    public String apparatusItem() { return apparatusItem; }
    public Box region() { return region; }
    public long createdAt() { return createdAt; }
    public Phase phase() { return phase; }
    public long startedAt() { return startedAt; }
    public long timeoutAt() { return timeoutAt; }
    public long endedAt() { return endedAt; }
    public UUID evidence() { return evidence; }
    public List<UUID> opportunities() { return Collections.unmodifiableList(opportunities); }
    public int collected() { return collected; }
    public String note() { return note; }

    public boolean isActive() {
        return phase == Phase.SETUP || phase == Phase.RUNNING;
    }

    void begin(long now, long timeoutTicks) {
        phase = Phase.RUNNING;
        startedAt = now;
        timeoutAt = now + timeoutTicks;
    }

    void addOpportunity(UUID observationId) {
        opportunities.add(observationId);
    }

    void noteCollected() {
        collected++;
    }

    void end(Phase result, long now, UUID evidence, String note) {
        this.phase = result;
        this.endedAt = now;
        this.evidence = evidence;
        this.note = note == null ? "" : note;
    }

    public String summary() {
        return id.toString().substring(0, 8) + " " + phase + " target=" + hypothesis.target()
                + " expect=" + hypothesis.expected() + " apparatus=" + apparatusItem + "@" + apparatus
                + " eggs=" + opportunities.size() + " collected=" + collected
                + (note.isEmpty() ? "" : " (" + note + ")");
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        Data.putUuid(m, "id", id);
        m.put("hypothesis", hypothesis.toMap());
        Data.putUuid(m, "researcher", researcher);
        m.put("apparatus", apparatus.toMap());
        m.put("apparatusItem", apparatusItem);
        m.put("region", region.toMap());
        m.put("created", createdAt);
        m.put("phase", phase.name());
        m.put("started", startedAt);
        m.put("timeout", timeoutAt);
        m.put("ended", endedAt);
        Data.putUuid(m, "evidence", evidence);
        m.put("opportunities", opportunities.stream().<Object>map(UUID::toString).toList());
        m.put("collected", collected);
        m.put("note", note);
        return m;
    }

    static Experiment fromMap(Map<String, Object> m) {
        Experiment e = new Experiment(Data.uuid(m, "id"), Hypothesis.fromMap(Data.sub(m, "hypothesis")),
                Data.uuidOrNull(m, "researcher"), Pos.fromMap(Data.sub(m, "apparatus")),
                Data.str(m, "apparatusItem"), Box.fromMap(Data.sub(m, "region")), Data.l(m, "created"));
        e.phase = Data.enumOf(m, "phase", Phase.class);
        e.startedAt = Data.lOr(m, "started", -1);
        e.timeoutAt = Data.lOr(m, "timeout", -1);
        e.endedAt = Data.lOr(m, "ended", -1);
        e.evidence = Data.uuidOrNull(m, "evidence");
        for (String s : Data.strings(m, "opportunities")) {
            e.opportunities.add(UUID.fromString(s));
        }
        e.collected = Data.iOr(m, "collected", 0);
        e.note = Data.strOr(m, "note", "");
        return e;
    }
}
