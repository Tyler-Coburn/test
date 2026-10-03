package dev.emerald.core.data;

import dev.emerald.core.EmeraldConstants;

import java.util.Map;
import java.util.TreeMap;

/**
 * Brings an old save up to {@link EmeraldConstants#SCHEMA_VERSION}, one step at a time.
 * Schema 1 is the first shape, so no migrations exist yet; add {@code Migration_1_to_2} here
 * the first time a persisted field changes.
 */
public final class MigrationRegistry {
    public static final String SCHEMA_KEY = "schemaVersion";

    private final Map<Integer, Migration> steps = new TreeMap<>();

    public static MigrationRegistry standard() {
        return new MigrationRegistry();
    }

    public MigrationRegistry register(Migration migration) {
        steps.put(migration.fromVersion(), migration);
        return this;
    }

    /** Upgrades {@code root} in place and returns the version it started at. */
    public int upgrade(Map<String, Object> root) {
        int start = Data.iOr(root, SCHEMA_KEY, 1);
        if (start > EmeraldConstants.SCHEMA_VERSION) {
            throw new DataException("Save schema " + start + " is newer than this build ("
                    + EmeraldConstants.SCHEMA_VERSION + "); refusing to load it");
        }
        int version = start;
        while (version < EmeraldConstants.SCHEMA_VERSION) {
            Migration step = steps.get(version);
            if (step == null) {
                throw new DataException("No migration registered from schema " + version);
            }
            step.apply(root);
            version++;
            root.put(SCHEMA_KEY, version);
        }
        return start;
    }
}
