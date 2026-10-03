package dev.emerald.minecraft.entity;

import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour;
import net.tslat.smartbrainlib.util.BrainUtils;

import java.util.List;

/** Executes the walk goal the job layer chose. Reads a target; decides nothing. */
public final class WalkToTaskTarget extends ExtendedBehaviour<CivVillager> {
    private static final float SPEED = 0.6f;

    @Override
    protected List<Pair<MemoryModuleType<?>, MemoryStatus>> getMemoryRequirements() {
        return List.of();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, CivVillager entity) {
        return entity.walkGoal() != null && !entity.isFleeing();
    }

    @Override
    protected void start(CivVillager entity) {
        push(entity);
    }

    @Override
    protected void tick(CivVillager entity) {
        push(entity);
    }

    @Override
    protected boolean shouldKeepRunning(CivVillager entity) {
        return entity.walkGoal() != null && !entity.isFleeing();
    }

    private static void push(CivVillager entity) {
        if (entity.walkGoal() != null) {
            BrainUtils.setMemory(entity, MemoryModuleType.WALK_TARGET,
                    new WalkTarget(entity.walkGoal(), SPEED, entity.walkReach()));
        }
    }
}
