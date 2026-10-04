package dev.emerald.core.sandbox;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The vision spec's proofs 1-11 plus M8-M10, end to end, for several seeds. */
class FirstSliceProofsTest {
    @ParameterizedTest
    @ValueSource(longs = {7, 11, 23, 99, 2026})
    void everyProofPasses(long seed) {
        FirstSliceScenario.Result r = new FirstSliceScenario(seed).run();
        StringBuilder failures = new StringBuilder();
        r.checks().stream().filter(c -> !c.passed())
                .forEach(c -> failures.append(c.id()).append(' ').append(c.description()).append(": ").append(c.detail()).append('\n'));
        assertTrue(r.allPassed(), "seed " + seed + " failed:\n" + failures);
    }
}
