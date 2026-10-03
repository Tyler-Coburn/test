package dev.emerald.core.data;

import java.util.Map;

/** Upgrades one persisted village root from {@link #fromVersion()} to {@code fromVersion() + 1}. */
public interface Migration {
    int fromVersion();

    void apply(Map<String, Object> root);
}
