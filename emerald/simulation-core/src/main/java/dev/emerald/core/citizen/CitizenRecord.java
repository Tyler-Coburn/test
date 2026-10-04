package dev.emerald.core.citizen;

import dev.emerald.core.data.Data;
import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.job.TaskType;
import dev.emerald.core.knowledge.KnowledgeBook;
import dev.emerald.core.utility.Need;
import dev.emerald.core.world.Pos;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * The persistent person. Exists whether or not a body is loaded, survives body unload, and keeps
 * existing (with {@code alive=false}) after death so history and designs can cite it.
 */
public final class CitizenRecord {
    private final UUID id;
    private final UUID villageId;
    private String name;
    private Role role;
    private int ageDays;
    /** 0 = full, 100 = starving. */
    private int hunger;
    /** 0 = exhausted, 100 = rested. */
    private int energy;
    private int curiosity;
    private int caution;
    private boolean alive = true;
    private UUID bodyUuid;
    private Pos home;
    private Pos work;
    private Need currentNeed = Need.WORK;
    private TaskType currentTask = TaskType.IDLE;
    private String taskDetail = "";
    private String deathCause;
    private long diedAt = -1;
    private Role jobOverride;
    private final Map<SkillType, Integer> skillXp = new EnumMap<>(SkillType.class);
    private final ItemCounter carried = new ItemCounter(64 * 9);
    private final KnowledgeBook knowledge = new KnowledgeBook();

    public CitizenRecord(UUID id, UUID villageId, String name, Role role) {
        this.id = id;
        this.villageId = villageId;
        this.name = name;
        this.role = role;
    }

    public UUID id() { return id; }
    public UUID villageId() { return villageId; }
    public String name() { return name; }
    public Role role() { return role; }
    public int ageDays() { return ageDays; }
    public int hunger() { return hunger; }
    public int energy() { return energy; }
    public int curiosity() { return curiosity; }
    public int caution() { return caution; }
    public boolean alive() { return alive; }
    public UUID bodyUuid() { return bodyUuid; }
    public boolean hasBody() { return bodyUuid != null; }
    public Pos home() { return home; }
    public Pos work() { return work; }
    public Need currentNeed() { return currentNeed; }
    public TaskType currentTask() { return currentTask; }
    public String taskDetail() { return taskDetail; }
    public String deathCause() { return deathCause; }
    public long diedAt() { return diedAt; }
    public ItemCounter carried() { return carried; }
    public KnowledgeBook knowledge() { return knowledge; }
    /** Temporary job assigned by the director (e.g. a general farming during FOOD_LOW), or null. */
    public Role jobOverride() { return jobOverride; }
    public void setJobOverride(Role job) { this.jobOverride = job; }
    /** The job actually worked: the director's override, else the citizen's role. */
    public Role effectiveRole() { return jobOverride != null ? jobOverride : role; }
    public int xp(SkillType skill) { return skillXp.getOrDefault(skill, 0); }
    public int skillLevel(SkillType skill) { return SkillType.levelFor(xp(skill)); }
    public void addXp(SkillType skill, int amount) {
        if (amount > 0) skillXp.merge(skill, amount, Integer::sum);
    }
    public Map<SkillType, Integer> skills() { return Map.copyOf(skillXp); }

    public void setName(String name) { this.name = name; }
    public void setRole(Role role) { this.role = role; }
    public void setAgeDays(int ageDays) { this.ageDays = Math.max(0, ageDays); }
    public void setHunger(int hunger) { this.hunger = clamp(hunger); }
    public void setEnergy(int energy) { this.energy = clamp(energy); }
    public void setCuriosity(int curiosity) { this.curiosity = clamp(curiosity); }
    public void setCaution(int caution) { this.caution = clamp(caution); }
    public void setHome(Pos home) { this.home = home; }
    public void setWork(Pos work) { this.work = work; }
    public void setNeed(Need need) { this.currentNeed = need; }

    public void setTask(TaskType task, String detail) {
        this.currentTask = task;
        this.taskDetail = detail == null ? "" : detail;
    }

    public void bindBody(UUID bodyUuid) { this.bodyUuid = bodyUuid; }
    public void unbindBody() { this.bodyUuid = null; }

    /** Marks the person dead. The record is kept; only a validated world event should call this. */
    public void die(String cause, long gameTime) {
        this.alive = false;
        this.deathCause = cause;
        this.diedAt = gameTime;
        this.bodyUuid = null;
        this.currentTask = TaskType.IDLE;
        this.taskDetail = "";
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        Data.putUuid(m, "id", id);
        Data.putUuid(m, "village", villageId);
        m.put("name", name);
        m.put("role", role.name());
        m.put("ageDays", ageDays);
        m.put("hunger", hunger);
        m.put("energy", energy);
        m.put("curiosity", curiosity);
        m.put("caution", caution);
        m.put("alive", alive);
        Data.putUuid(m, "body", bodyUuid);
        if (home != null) m.put("home", home.toMap());
        if (work != null) m.put("work", work.toMap());
        m.put("need", currentNeed.name());
        m.put("task", currentTask.name());
        m.put("taskDetail", taskDetail);
        if (deathCause != null) m.put("deathCause", deathCause);
        m.put("diedAt", diedAt);
        if (jobOverride != null) m.put("jobOverride", jobOverride.name());
        Map<String, Object> xp = Data.map();
        skillXp.forEach((k, v) -> xp.put(k.name(), v));
        m.put("skills", xp);
        m.put("carried", carried.toList());
        m.put("knowledge", knowledge.toList());
        return m;
    }

    public static CitizenRecord fromMap(Map<String, Object> m) {
        CitizenRecord r = new CitizenRecord(Data.uuid(m, "id"), Data.uuid(m, "village"), Data.str(m, "name"),
                Data.enumOf(m, "role", Role.class));
        r.ageDays = Data.iOr(m, "ageDays", 0);
        r.hunger = clamp(Data.iOr(m, "hunger", 0));
        r.energy = clamp(Data.iOr(m, "energy", 100));
        r.curiosity = clamp(Data.iOr(m, "curiosity", 50));
        r.caution = clamp(Data.iOr(m, "caution", 50));
        r.alive = Data.b(m, "alive", true);
        r.bodyUuid = Data.uuidOrNull(m, "body");
        r.home = Pos.fromMapOrNull(Data.subOrNull(m, "home"));
        r.work = Pos.fromMapOrNull(Data.subOrNull(m, "work"));
        r.currentNeed = Data.enumOr(m, "need", Need.class, Need.WORK);
        r.currentTask = Data.enumOr(m, "task", TaskType.class, TaskType.IDLE);
        r.taskDetail = Data.strOr(m, "taskDetail", "");
        r.deathCause = Data.strOr(m, "deathCause", null);
        r.diedAt = Data.lOr(m, "diedAt", -1);
        r.jobOverride = Data.enumOr(m, "jobOverride", Role.class, null);
        Map<String, Object> xp = Data.subOrNull(m, "skills");
        if (xp != null) {
            xp.forEach((k, v) -> {
                try {
                    r.skillXp.put(SkillType.valueOf(k), ((Number) v).intValue());
                } catch (IllegalArgumentException ignored) {
                    // skill removed in a later version
                }
            });
        }
        r.carried.loadFrom(Data.maps(m, "carried"));
        r.knowledge.loadFrom(Data.maps(m, "knowledge"));
        return r;
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }
}
