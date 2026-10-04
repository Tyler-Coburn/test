package dev.emerald.core.sandbox;

import dev.emerald.core.data.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Records a scenario run for the replay viewer: {@code :simulation-core:replay -Pseed=N}. */
public final class ReplayMain {
    private ReplayMain() {
    }

    public static void main(String[] args) throws IOException {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 7L;
        Path out = Path.of(args.length > 1 ? args[1] : "build/replay.json");
        ReplayRecorder rec = new ReplayRecorder(400);
        FirstSliceScenario.Result r = new FirstSliceScenario(seed).withRecorder(rec).run();
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, Json.write(rec.toMap(r, seed)).replaceAll("\\n\\s*", ""));
        System.out.println("Replay: " + out.toAbsolutePath() + " (" + Files.size(out) / 1024 + " KiB)");
    }
}
