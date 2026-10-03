package dev.emerald.core.world;

import dev.emerald.core.data.Data;

import java.util.Map;

/** Inclusive axis-aligned block region, e.g. a chicken pen. */
public record Box(Pos min, Pos max) {
    public static Box of(Pos a, Pos b) {
        return new Box(
                new Pos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new Pos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())));
    }

    public boolean contains(Pos p) {
        return p.x() >= min.x() && p.x() <= max.x()
                && p.y() >= min.y() && p.y() <= max.y()
                && p.z() >= min.z() && p.z() <= max.z();
    }

    /** True if {@code p} is inside the box grown by {@code margin} blocks on every side. */
    public boolean containsWithin(Pos p, int margin) {
        return new Box(min.offset(-margin, -margin, -margin), max.offset(margin, margin, margin)).contains(p);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        m.put("min", min.toMap());
        m.put("max", max.toMap());
        return m;
    }

    public static Box fromMap(Map<String, Object> m) {
        return new Box(Pos.fromMap(Data.sub(m, "min")), Pos.fromMap(Data.sub(m, "max")));
    }
}
