package dev.emerald.core;

/**
 * Tunable simulation rates. Pure data so tests can construct their own; the NeoForge layer
 * fills it from the server config file.
 *
 * @param utilityIntervalTicks     how often needs are re-scored (never every tick)
 * @param eggWasteWindowTicks      an egg with no collection evidence after this long opens EGGS_WASTED
 * @param experimentTimeoutTicks   an experiment with no expected evidence after this long ends
 * @param observationCap           observations kept in the persisted bus tail
 * @param ledgerCap                ledger events kept in the persisted ledger tail
 * @param farmRadius               blocks around the village centre a farmer scans for crops
 * @param plankRequestCount        largest single material request (the M4 proof asks for 16 planks)
 * @param directorIntervalTicks    how often the village director re-evaluates (slow, strategic)
 * @param maxPopulation            immigration stops at this many living citizens
 * @param immigrationCooldownTicks minimum time between arrivals
 * @param offlineStepTicks         size of one statistical step while a village is unloaded
 * @param offlineMaxTicks          longest unloaded period simulated in detail; beyond it time is skipped
 * @param patrolRadius             distance from the centre guards patrol
 */
public record SimulationConfig(
        int utilityIntervalTicks,
        int eggWasteWindowTicks,
        int experimentTimeoutTicks,
        int observationCap,
        int ledgerCap,
        int farmRadius,
        int plankRequestCount,
        int directorIntervalTicks,
        int maxPopulation,
        int immigrationCooldownTicks,
        int offlineStepTicks,
        long offlineMaxTicks,
        int patrolRadius
) {
    public static final SimulationConfig DEFAULT = new SimulationConfig(
            40, 2400, 12000, 512, 2048, 16, 16,
            100, 16, 24000, 1200, 30L * 24000, 12);

    public SimulationConfig withEggWindow(int ticks) {
        return new SimulationConfig(utilityIntervalTicks, ticks, experimentTimeoutTicks, observationCap, ledgerCap,
                farmRadius, plankRequestCount, directorIntervalTicks, maxPopulation, immigrationCooldownTicks,
                offlineStepTicks, offlineMaxTicks, patrolRadius);
    }

    public SimulationConfig withExperimentTimeout(int ticks) {
        return new SimulationConfig(utilityIntervalTicks, eggWasteWindowTicks, ticks, observationCap, ledgerCap,
                farmRadius, plankRequestCount, directorIntervalTicks, maxPopulation, immigrationCooldownTicks,
                offlineStepTicks, offlineMaxTicks, patrolRadius);
    }
}
