package dev.emerald.core.job;

import dev.emerald.core.world.Pos;

/** A loaded citizen body as the job layer sees it. Implemented by the NeoForge entity adapter. */
public interface BodyPort {
    Pos position();

    /**
     * Steers the body toward {@code target} (the body layer handles pathing).
     * Returns true once the body is within {@code reach} blocks.
     */
    boolean moveTo(Pos target, double reach);
}
