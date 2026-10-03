package dev.emerald.core;

/** Identity and persistence constants shared by every Emerald module. */
public final class EmeraldConstants {
    public static final String MOD_ID = "emerald";
    public static final String MOD_NAME = "Emerald";

    /**
     * Version written to every persisted root (village, citizen list, ledger).
     * Bump it and add a {@link dev.emerald.core.data.Migration} whenever a saved shape changes.
     */
    public static final int SCHEMA_VERSION = 1;

    private EmeraldConstants() {
    }
}
