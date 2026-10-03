package dev.emerald.minecraft.entity;

import dev.emerald.core.job.TaskType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import net.tslat.smartbrainlib.api.core.BrainActivityGroup;
import net.tslat.smartbrainlib.api.core.SmartBrainProvider;
import net.tslat.smartbrainlib.api.core.behaviour.FirstApplicableBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.OneRandomBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.custom.look.LookAtTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.misc.Idle;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.MoveToWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.path.SetRandomWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetPlayerLookTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetRandomLookTarget;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.HurtBySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyLivingEntitySensor;

import java.util.List;
import java.util.UUID;

/**
 * The physical body of a citizen. It owns no civilisation state: the CitizenRecord (in
 * VillageSavedData) is the person, and this entity only stores which record it embodies.
 *
 * <p>SmartBrainLib executes movement and looking. The job layer above writes a walk goal and a
 * flee flag; behaviours read them. No research, economy or strategy lives here.
 */
public class CivVillager extends PathfinderMob implements SmartBrainOwner<CivVillager> {
    private static final String CITIZEN_TAG = "EmeraldCitizen";

    private UUID citizenId;
    private BlockPos walkGoal;
    private int walkReach = 1;
    private boolean fleeing;
    private Vec3 threatPosition;
    private TaskType task = TaskType.IDLE;

    public CivVillager(EntityType<? extends CivVillager> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
        }
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    // --- link to the persistent record -------------------------------------------------------

    public UUID citizenId() {
        return citizenId;
    }

    public void setCitizenId(UUID citizenId) {
        this.citizenId = citizenId;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (citizenId != null) {
            tag.putUUID(CITIZEN_TAG, citizenId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(CITIZEN_TAG)) {
            citizenId = tag.getUUID(CITIZEN_TAG);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // --- instructions from the job layer -------------------------------------------------------

    public BlockPos walkGoal() {
        return walkGoal;
    }

    public int walkReach() {
        return walkReach;
    }

    public void setWalkGoal(BlockPos goal, int reach) {
        this.walkGoal = goal;
        this.walkReach = Math.max(0, reach);
    }

    public void clearWalkGoal() {
        this.walkGoal = null;
    }

    public boolean isFleeing() {
        return fleeing;
    }

    public void setFleeing(boolean fleeing) {
        this.fleeing = fleeing;
        if (fleeing) {
            this.walkGoal = null;
        }
    }

    public Vec3 threatPosition() {
        return threatPosition;
    }

    public TaskType task() {
        return task;
    }

    public void setTask(TaskType task) {
        this.task = task;
    }

    /** True if wandering is allowed (no job is steering the body). */
    public boolean mayWander() {
        return walkGoal == null && !fleeing && (task == TaskType.IDLE || task == TaskType.PATROL);
    }

    /** Server-observed threat check: a monster within 8 blocks, or hurt by a mob in the last 5 seconds. */
    public boolean senseThreat() {
        threatPosition = null;
        if (getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < 100) {
            threatPosition = getLastHurtByMob().position();
            return true;
        }
        List<Monster> monsters = level().getEntitiesOfClass(Monster.class, getBoundingBox().inflate(8.0));
        if (!monsters.isEmpty()) {
            threatPosition = monsters.get(0).position();
            return true;
        }
        return false;
    }

    // --- SmartBrainLib -------------------------------------------------------------------------

    @Override
    protected Brain.Provider<?> brainProvider() {
        return new SmartBrainProvider<>(this);
    }

    @Override
    protected void customServerAiStep() {
        tickBrain(this);
    }

    @Override
    public List<? extends ExtendedSensor<? extends CivVillager>> getSensors() {
        return List.of(new NearbyLivingEntitySensor<CivVillager>(), new HurtBySensor<CivVillager>());
    }

    @Override
    public BrainActivityGroup<? extends CivVillager> getCoreTasks() {
        return BrainActivityGroup.coreTasks(
                new LookAtTarget<CivVillager>(),
                new MoveToWalkTarget<CivVillager>());
    }

    @Override
    public BrainActivityGroup<? extends CivVillager> getIdleTasks() {
        return BrainActivityGroup.idleTasks(
                new FirstApplicableBehaviour<CivVillager>(
                        new FleeThreat(),
                        new WalkToTaskTarget(),
                        new OneRandomBehaviour<CivVillager>(
                                new SetRandomWalkTarget<CivVillager>().startCondition(CivVillager::mayWander),
                                new Idle<CivVillager>().runFor(e -> e.getRandom().nextInt(30, 60)))),
                new OneRandomBehaviour<CivVillager>(
                        new SetPlayerLookTarget<CivVillager>(),
                        new SetRandomLookTarget<CivVillager>()));
    }
}
