package dev.emerald.minecraft.entity;

import dev.emerald.core.job.BodyPort;
import dev.emerald.core.world.Pos;
import dev.emerald.minecraft.world.Positions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Adapts a loaded CivVillager to the job layer's BodyPort. */
public record CivBodyPort(CivVillager entity) implements BodyPort {
    @Override
    public Pos position() {
        return Positions.toPos(entity.blockPosition());
    }

    @Override
    public boolean moveTo(Pos target, double reach) {
        BlockPos goal = Positions.toBlockPos(target);
        if (entity.position().distanceToSqr(Vec3.atCenterOf(goal)) <= reach * reach) {
            entity.clearWalkGoal();
            return true;
        }
        entity.setWalkGoal(goal, Math.max(1, (int) reach - 1));
        return false;
    }
}
