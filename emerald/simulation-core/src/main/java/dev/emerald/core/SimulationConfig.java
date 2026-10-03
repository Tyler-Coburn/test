package dev.emerald.core;

/**
 * Tunable simulation rates. Pure data so tests can construct their own; the NeoForge layer
 * fills it from the server config file.
 *
 * @param utilityIntervalTicks   how often needs are re-scored (never every tick)
 * @param eggWasteWindowTicks    an egg with no collection evidence after this long opens EGGS_WASTED
 * @param experimentTimeoutTicks an experiment with no expected evidence after this long ends
 * @param observationCap         observations kept in the persisted bus tail
 * @param ledgerCap              ledger events kept in the persisted ledger tail
 * @param farmRadius             blocks around the village centre a farmer scans for crops
 * @param plankRequestCount      planks the builder asks for in the M4 delivery proof
 */
public record SimulationConfig(
        int utilityIntervalTicks,
        int eggWasteWindowTicks,
        int experimentTimeoutTicks,
        int observationCap,
        int ledgerCap,
        int farmRadius,
        int plankRequestCount
) {
    public static final SimulationConfig DEFAULT = new SimulationConfig(40, 2400, 12000, 512, 2048, 16, 16);
}
