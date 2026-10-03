package dev.emerald.minecraft.saved;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts simulation-core's neutral map trees to and from NBT. The only place the two formats meet. */
public final class NbtBridge {
    private NbtBridge() {
    }

    public static CompoundTag toTag(Map<String, Object> map) {
        CompoundTag tag = new CompoundTag();
        map.forEach((key, value) -> {
            Tag t = toTagValue(value);
            if (t != null) {
                tag.put(key, t);
            }
        });
        return tag;
    }

    @SuppressWarnings("unchecked")
    private static Tag toTagValue(Object v) {
        if (v == null) return null;
        if (v instanceof String s) return StringTag.valueOf(s);
        if (v instanceof Integer i) return IntTag.valueOf(i);
        if (v instanceof Long l) return LongTag.valueOf(l);
        if (v instanceof Boolean b) return ByteTag.valueOf(b);
        if (v instanceof Byte b) return ByteTag.valueOf(b);
        if (v instanceof Short s) return ShortTag.valueOf(s);
        if (v instanceof Double d) return DoubleTag.valueOf(d);
        if (v instanceof Float f) return FloatTag.valueOf(f);
        if (v instanceof Map<?, ?> m) return toTag((Map<String, Object>) m);
        if (v instanceof List<?> list) {
            ListTag out = new ListTag();
            for (Object o : list) {
                Tag t = toTagValue(o);
                if (t != null) {
                    out.add(t);
                }
            }
            return out;
        }
        throw new IllegalArgumentException("Cannot persist " + v.getClass().getName());
    }

    public static Map<String, Object> toMap(CompoundTag tag) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            Object v = fromTag(tag.get(key));
            if (v != null) {
                out.put(key, v);
            }
        }
        return out;
    }

    private static Object fromTag(Tag t) {
        if (t instanceof CompoundTag c) return toMap(c);
        if (t instanceof ListTag l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Tag e : l) {
                out.add(fromTag(e));
            }
            return out;
        }
        if (t instanceof StringTag s) return s.getAsString();
        if (t instanceof IntArrayTag a) return box(a.getAsIntArray());
        if (t instanceof LongArrayTag a) {
            List<Object> out = new ArrayList<>();
            for (long x : a.getAsLongArray()) out.add(x);
            return out;
        }
        if (t instanceof ByteArrayTag a) {
            List<Object> out = new ArrayList<>();
            for (byte x : a.getAsByteArray()) out.add(x);
            return out;
        }
        if (t instanceof LongTag n) return n.getAsLong();
        if (t instanceof ByteTag n) return n.getAsByte();
        if (t instanceof DoubleTag n) return n.getAsDouble();
        if (t instanceof FloatTag n) return n.getAsFloat();
        if (t instanceof NumericTag n) return n.getAsInt();
        return null;
    }

    private static List<Object> box(int[] values) {
        List<Object> out = new ArrayList<>(values.length);
        for (int x : values) out.add(x);
        return out;
    }
}
