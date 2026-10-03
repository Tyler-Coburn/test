package dev.emerald.minecraft.world;

import dev.emerald.core.world.Pos;
import net.minecraft.core.BlockPos;

public final class Positions {
    private Positions() {
    }

    public static Pos toPos(BlockPos p) {
        return new Pos(p.getX(), p.getY(), p.getZ());
    }

    public static BlockPos toBlockPos(Pos p) {
        return new BlockPos(p.x(), p.y(), p.z());
    }
}
