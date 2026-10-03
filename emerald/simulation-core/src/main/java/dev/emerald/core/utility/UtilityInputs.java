package dev.emerald.core.utility;

import dev.emerald.core.citizen.Role;

/** Everything utility scoring may look at. Built from the record plus a few world facts. */
public record UtilityInputs(int hunger, int energy, boolean threatened, boolean night, Role role, int caution) {
}
