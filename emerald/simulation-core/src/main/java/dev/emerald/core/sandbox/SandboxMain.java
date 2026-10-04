package dev.emerald.core.sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the first-slice scenario headless and writes a Markdown report.
 * {@code ./gradlew -Pemerald.coreOnly=true :simulation-core:sandbox [-Pseed=N]}
 */
public final class SandboxMain {
    private SandboxMain() {
    }

    public static void main(String[] args) throws IOException {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 7L;
        Path out = Path.of(args.length > 1 ? args[1] : "build/sandbox-report.md");
        long start = System.nanoTime();
        FirstSliceScenario.Result r = new FirstSliceScenario(seed).run();
        long ms = (System.nanoTime() - start) / 1_000_000;
        String report = FirstSliceScenario.report(r, seed) + "\nSimulated to day " + r.world().time / 24000
                + " in " + ms + " ms.\n";
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, report);
        r.checks().forEach(c -> System.out.printf("%-5s %-4s %s -- %s%n", c.id(), c.passed() ? "PASS" : "FAIL",
                c.description(), c.detail()));
        System.out.println("Report: " + out.toAbsolutePath() + " (" + ms + " ms)");
        if (!r.allPassed()) {
            System.exit(1);
        }
    }
}
