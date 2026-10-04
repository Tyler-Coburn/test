package dev.emerald.minecraft.entity;

import com.mojang.datafixers.util.Pair;
import dev.emerald.core.job.TaskType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Monster;
import net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour;
import net.tslat.smartbrainlib.util.BrainUtils;

import java.util.List;

/**
 * Guard body behaviour while the job layer says DEFEND: close in on the nearest monster and strike
 * with a cooldown. Target choice is local and physical; whether to defend was decided above.
 */
public final class DefendVillage extends ExtendedBehaviour<CivVillager> {
    private static final double RANGE = 16.0;
    private static final int ATTACK_COOLDOWN = 20;
    private int cooldown;

    @Override
    protected List<Pair<MemoryModuleType<?>, MemoryStatus>> getMemoryRequirements() {
        return List.of();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, CivVillager entity) {
        return entity.task() == TaskType.DEFEND && entity.nearestMonster(RANGE) != null;
    }

    @Override
    protected void start(CivVillager entity) {
        tick(entity);
    }

    @Override
    protected void tick(CivVillager entity) {
        Monster target = entity.nearestMonster(RANGE);
        if (target == null) {
            return;
        }
        entity.getLookControl().setLookAt(target);
        if (cooldown > 0) {
            cooldown--;
        }
        if (entity.distanceToSqr(target) > 4.0) {
            BrainUtils.setMemory(entity, MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.75f, 1));
        } else if (cooldown <= 0) {
            entity.swing(InteractionHand.MAIN_HAND);
            entity.doHurtTarget(target);
            cooldown = ATTACK_COOLDOWN;
        }
    }

    @Override
    protected boolean shouldKeepRunning(CivVillager entity) {
        return entity.task() == TaskType.DEFEND && entity.nearestMonster(RANGE) != null;
    }
}
