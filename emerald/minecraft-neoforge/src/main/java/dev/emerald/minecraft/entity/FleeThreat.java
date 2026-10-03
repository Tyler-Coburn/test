package dev.emerald.minecraft.entity;

import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour;
import net.tslat.smartbrainlib.util.BrainUtils;

import java.util.List;

/** Runs away from the sensed threat while the utility layer has chosen FLEE. */
public final class FleeThreat extends ExtendedBehaviour<CivVillager> {
    private static final float SPEED = 0.8f;

    @Override
    protected List<Pair<MemoryModuleType<?>, MemoryStatus>> getMemoryRequirements() {
        return List.of();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, CivVillager entity) {
        return entity.isFleeing() && entity.threatPosition() != null;
    }

    @Override
    protected void start(CivVillager entity) {
        retarget(entity);
    }

    @Override
    protected void tick(CivVillager entity) {
        if (!BrainUtils.hasMemory(entity, MemoryModuleType.WALK_TARGET)) {
            retarget(entity);
        }
    }

    @Override
    protected boolean shouldKeepRunning(CivVillager entity) {
        return entity.isFleeing();
    }

    private static void retarget(CivVillager entity) {
        Vec3 from = entity.threatPosition();
        if (from == null) {
            return;
        }
        Vec3 away = DefaultRandomPos.getPosAway(entity, 16, 7, from);
        if (away != null) {
            BrainUtils.setMemory(entity, MemoryModuleType.WALK_TARGET, new WalkTarget(away, SPEED, 0));
        }
    }
}
