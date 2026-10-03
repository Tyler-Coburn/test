package dev.emerald.core.job;

import dev.emerald.core.construction.BlueprintBlock;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.world.Pos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative world actions available to job routines. Every method performs or checks a
 * real world change; routines record observations only after these confirm success.
 */
public interface WorldPort {
    long gameTime();

    String dimension();

    /** The registered warehouse inventory, or null if unset or not loaded. */
    ItemStore warehouse();

    Optional<Pos> findMatureCrop(Pos center, int radius);

    /** Breaks a mature crop and moves its real drops into {@code into}. Empty if nothing was harvested. */
    Map<String, Integer> harvestCrop(Pos pos, ItemStore into);

    /** Replants the crop at {@code pos}, consuming one seed from {@code from}. */
    boolean replant(Pos pos, ItemStore from);

    /** True if the world already holds {@code block} at {@code pos}. */
    boolean matches(Pos pos, BlueprintBlock block);

    /** True if the position is loaded and can be read or edited this tick. */
    boolean isLoaded(Pos pos);

    /** Places {@code block}, consuming one {@code block.itemId()} from {@code from}. */
    boolean place(Pos pos, BlueprintBlock block, ItemStore from);

    /** Current position of a citizen's loaded body, if any. */
    Optional<Pos> bodyPosition(UUID citizenId);
}
