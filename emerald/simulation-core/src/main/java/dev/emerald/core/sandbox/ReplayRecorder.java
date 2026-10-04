package dev.emerald.core.sandbox;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.construction.Blueprints;
import dev.emerald.core.data.Data;
import dev.emerald.core.event.LedgerEvent;
import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Samples a sandbox run into compact frames for the replay viewer: bodies with their tasks, crop
 * ages, block changes, monsters, warehouse stock and the ledger events since the previous frame.
 */
public final class ReplayRecorder implements Consumer<SandboxWorld> {
    private final int every;
    private final List<Object> frames = new ArrayList<>();
    private final List<Object> people = new ArrayList<>();
    private final Map<UUID, Integer> personIndex = new HashMap<>();
    private Map<Pos, String> lastBlocks = new HashMap<>();
    private long lastLedgerTotal;
    private long nextFrame = Long.MIN_VALUE;

    public ReplayRecorder(int everyTicks) {
        this.every = everyTicks;
    }

    @Override
    public void accept(SandboxWorld w) {
        if (nextFrame != Long.MIN_VALUE && w.time < nextFrame) {
            return;
        }
        nextFrame = w.time + every;
        VillageState v = w.village;
        Map<String, Object> f = Data.map();
        f.put("t", w.time);
        f.put("loaded", w.isLoadedVillage());

        List<Object> bodies = new ArrayList<>();
        for (CitizenRecord c : v.citizens().all()) {
            int idx = personIndex.computeIfAbsent(c.id(), id -> {
                Map<String, Object> p = Data.map();
                p.put("name", c.name());
                p.put("role", c.role().name());
                people.add(p);
                return people.size() - 1;
            });
            var body = w.bodies.get(c.id());
            if (body != null && c.alive() && w.isLoadedVillage()) {
                bodies.add(List.of(idx, body.pos.x(), body.pos.z(), c.currentTask().name(), c.hunger(), c.energy()));
            } else if (!c.alive()) {
                bodies.add(List.of(idx, 0, 0, "DEAD", 0, 0));
            }
        }
        f.put("bodies", bodies);

        List<Object> crops = new ArrayList<>();
        w.crops.forEach((p, age) -> crops.add(List.of(p.x(), p.z(), age)));
        f.put("crops", crops);

        Map<Pos, String> now = new HashMap<>();
        w.blocks.forEach((p, b) -> {
            if (!b.equals("minecraft:wheat") && !b.equals("minecraft:farmland")) now.put(p, b);
        });
        List<Object> added = new ArrayList<>();
        List<Object> removed = new ArrayList<>();
        now.forEach((p, b) -> {
            if (!b.equals(lastBlocks.get(p))) added.add(List.of(p.x(), p.y(), p.z(), b.replace("minecraft:", "")));
        });
        lastBlocks.forEach((p, b) -> {
            if (!now.containsKey(p)) removed.add(List.of(p.x(), p.y(), p.z()));
        });
        lastBlocks = now;
        if (!added.isEmpty()) f.put("add", added);
        if (!removed.isEmpty()) f.put("del", removed);

        f.put("monsters", w.monsters.stream().<Object>map(m -> List.of(m.pos.x(), m.pos.z())).toList());

        Map<String, Object> stock = new LinkedHashMap<>();
        ItemCounter wh = v.warehouse() == null ? null : w.containers.get(v.warehouse());
        if (wh != null) {
            for (String item : List.of(ItemIds.WHEAT, ItemIds.BREAD, ItemIds.OAK_PLANKS, ItemIds.HOPPER, "minecraft:book")) {
                stock.put(item.replace("minecraft:", ""), wh.count(item));
            }
        }
        int eggs = v.construction().buildings().stream().filter(b -> Blueprints.isCollector(b.type()))
                .map(Blueprints::collectorChest).filter(p -> p != null && w.containers.containsKey(p))
                .distinct().mapToInt(p -> w.containers.get(p).count(ItemIds.EGG)).sum();
        stock.put("eggs collected", eggs);
        f.put("stock", stock);
        f.put("pop", v.citizens().alive().size());

        long total = v.ledger().totalAppended();
        int fresh = (int) Math.min(40, total - lastLedgerTotal);
        lastLedgerTotal = total;
        if (fresh > 0) {
            List<Object> events = new ArrayList<>();
            for (LedgerEvent e : v.ledger().recent(fresh)) {
                String who = e.actor() == null ? "" : v.citizens().get(e.actor()).map(CitizenRecord::name).orElse("");
                events.add(List.of(e.gameTime(), e.type(), who, describe(e)));
            }
            f.put("events", events);
        }
        frames.add(f);
    }

    private static String describe(LedgerEvent e) {
        StringBuilder sb = new StringBuilder();
        e.payload().forEach((k, val) -> {
            if (val.length() < 60 && !k.equals("cause") && !k.equals("evidence")) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(k).append(' ').append(val);
            }
        });
        return sb.toString();
    }

    public Map<String, Object> toMap(FirstSliceScenario.Result r, long seed) {
        Map<String, Object> m = Data.map();
        m.put("seed", seed);
        m.put("every", every);
        m.put("village", r.village().name());
        m.put("bias", r.village().bias().name());
        m.put("center", List.of(r.village().center().x(), r.village().center().z()));
        if (r.village().pen() != null) {
            m.put("pen", List.of(r.village().pen().min().x(), r.village().pen().min().z(),
                    r.village().pen().max().x(), r.village().pen().max().z()));
        }
        if (r.village().warehouse() != null) {
            m.put("warehouse", List.of(r.village().warehouse().x(), r.village().warehouse().z()));
        }
        m.put("people", people);
        m.put("checks", r.checks().stream().<Object>map(c -> List.of(c.id(), c.description(), c.passed(), c.detail())).toList());
        m.put("frames", frames);
        return m;
    }
}
