package dev.emerald.core.item;

import java.util.Map;

/**
 * Any inventory the simulation can count, take from or put into: a warehouse chest, a citizen's
 * carried items, or (in tests) an in-memory fake. Implementations in the NeoForge layer wrap real
 * containers, so every transfer here is a real item transfer.
 */
public interface ItemStore {
    int count(String itemId);

    /** Removes up to {@code amount}; returns how many were actually removed. */
    int extract(String itemId, int amount);

    /** Adds up to {@code amount}; returns how many fit. */
    int insert(String itemId, int amount);

    /** Snapshot of every non-zero stack, keyed by item id. */
    Map<String, Integer> contents();
}
