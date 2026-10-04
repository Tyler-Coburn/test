package dev.emerald.core.economy;

import dev.emerald.core.data.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregate production and consumption, by item and by day. An analytic view over real transfers
 * (deposits, deliveries, meals, collector output); it never creates items.
 */
public final class EconomyStats {
    public static final int DAY = 24000;
    public static final int DAYS_KEPT = 7;

    private final Map<String, Long> producedTotal = new TreeMap<>();
    private final Map<String, Long> consumedTotal = new TreeMap<>();
    /** day index -> item -> net change (produced - consumed). */
    private final TreeMap<Long, Map<String, Integer>> daily = new TreeMap<>();
    private final Map<String, Double> measuredProduction = new TreeMap<>();
    private final Map<String, Long> sampledTotals = new TreeMap<>();
    private long lastSample = Long.MIN_VALUE;
    public static final int SAMPLE_INTERVAL = 2400;

    /** Turns production since the last sample into measured per-day rates. Call only while loaded. */
    public void sample(long now) {
        if (lastSample == Long.MIN_VALUE) {
            lastSample = now;
            sampledTotals.putAll(producedTotal);
            return;
        }
        if (now - lastSample < SAMPLE_INTERVAL) return;
        for (String item : producedTotal.keySet()) {
            long delta = producedTotal.get(item) - sampledTotals.getOrDefault(item, 0L);
            sampleProduction(item, (int) delta, now - lastSample);
        }
        sampledTotals.clear();
        sampledTotals.putAll(producedTotal);
        lastSample = now;
    }

    /** Forget the sampling baseline (e.g. after an unloaded period, so offline output is not "measured"). */
    public void resetSampling() {
        lastSample = Long.MIN_VALUE;
        sampledTotals.clear();
    }

    public void produced(String item, int count, long now) {
        if (count <= 0) return;
        producedTotal.merge(item, (long) count, Long::sum);
        bucket(now).merge(item, count, Integer::sum);
    }

    public void consumed(String item, int count, long now) {
        if (count <= 0) return;
        consumedTotal.merge(item, (long) count, Long::sum);
        bucket(now).merge(item, -count, Integer::sum);
    }

    private Map<String, Integer> bucket(long now) {
        Map<String, Integer> b = daily.computeIfAbsent(now / DAY, d -> new TreeMap<>());
        while (daily.size() > DAYS_KEPT) {
            daily.pollFirstEntry();
        }
        return b;
    }

    public long produced(String item) {
        return producedTotal.getOrDefault(item, 0L);
    }

    public long consumed(String item) {
        return consumedTotal.getOrDefault(item, 0L);
    }

    /** Average net change per day over the kept window (excluding the current, partial day when possible). */
    public double netPerDay(String item, long now) {
        long today = now / DAY;
        int days = 0;
        long sum = 0;
        for (Map.Entry<Long, Map<String, Integer>> e : daily.entrySet()) {
            if (e.getKey() < today || daily.size() == 1) {
                sum += e.getValue().getOrDefault(item, 0);
                days++;
            }
        }
        return days == 0 ? 0 : (double) sum / days;
    }

    /** Gross production per day of {@code item}, measured while the village was loaded (for offline rates). */
    public double producedPerDay(String item) {
        return measuredProduction.getOrDefault(item, 0.0);
    }

    /** Records a measured production sample: {@code count} produced over {@code ticks}. Exponential average. */
    public void sampleProduction(String item, int count, long ticks) {
        if (ticks <= 0) return;
        double perDay = count * (double) DAY / ticks;
        measuredProduction.merge(item, perDay, (old, now) -> old * 0.7 + now * 0.3);
    }

    public Map<String, Long> producedTotals() {
        return Map.copyOf(producedTotal);
    }

    public Map<String, Long> consumedTotals() {
        return Map.copyOf(consumedTotal);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        m.put("produced", new LinkedHashMap<String, Object>(producedTotal));
        m.put("consumed", new LinkedHashMap<String, Object>(consumedTotal));
        List<Object> days = daily.entrySet().stream().<Object>map(e -> {
            Map<String, Object> d = Data.map();
            d.put("day", e.getKey());
            d.put("net", new LinkedHashMap<String, Object>(e.getValue()));
            return d;
        }).toList();
        m.put("daily", days);
        Map<String, Object> rates = Data.map();
        measuredProduction.forEach((k, v) -> rates.put(k, (long) Math.round(v * 1000)));
        m.put("ratesMilli", rates);
        return m;
    }

    public void loadFrom(Map<String, Object> m) {
        producedTotal.clear();
        consumedTotal.clear();
        daily.clear();
        measuredProduction.clear();
        if (m == null) return;
        Map<String, Object> rates = Data.subOrNull(m, "ratesMilli");
        if (rates != null) rates.forEach((k, v) -> measuredProduction.put(k, ((Number) v).longValue() / 1000.0));
        copyLongs(Data.subOrNull(m, "produced"), producedTotal);
        copyLongs(Data.subOrNull(m, "consumed"), consumedTotal);
        for (Map<String, Object> d : Data.maps(m, "daily")) {
            Map<String, Integer> net = new TreeMap<>();
            Map<String, Object> raw = Data.subOrNull(d, "net");
            if (raw != null) {
                raw.forEach((k, v) -> net.put(k, ((Number) v).intValue()));
            }
            daily.put(Data.l(d, "day"), net);
        }
    }

    private static void copyLongs(Map<String, Object> src, Map<String, Long> dst) {
        if (src != null) {
            src.forEach((k, v) -> dst.put(k, ((Number) v).longValue()));
        }
    }
}
