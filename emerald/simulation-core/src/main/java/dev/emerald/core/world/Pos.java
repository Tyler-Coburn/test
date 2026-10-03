package dev.emerald.core.world;

import dev.emerald.core.data.Data;

import java.util.Map;

/** Block position in adapter-safe form; the NeoForge layer converts to and from BlockPos. */
public record Pos(int x, int y, int z) {
    public Pos offset(int dx, int dy, int dz) {
        return new Pos(x + dx, y + dy, z + dz);
    }

    public Pos offset(Pos d) {
        return offset(d.x, d.y, d.z);
    }

    public Pos below() {
        return offset(0, -1, 0);
    }

    public Pos above() {
        return offset(0, 1, 0);
    }

    public long distSq(Pos o) {
        long dx = x - o.x;
        long dy = y - o.y;
        long dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        return m;
    }

    public static Pos fromMap(Map<String, Object> m) {
        return new Pos(Data.i(m, "x"), Data.i(m, "y"), Data.i(m, "z"));
    }

    public static Pos fromMapOrNull(Map<String, Object> m) {
        return m == null ? null : fromMap(m);
    }

    @Override
    public String toString() {
        return x + " " + y + " " + z;
    }
}
