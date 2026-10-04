package dev.emerald.core.citizen;

/** Personal proficiencies. Experience is earned by finishing real work; levels change speed and quality. */
public enum SkillType {
    FARMING,
    BUILDING,
    RESEARCH,
    TEACHING,
    COMBAT,
    LOGISTICS;

    /** Level 0..10 from experience: 10 xp for level 1, 40 for 2, 90 for 3 ... */
    public static int levelFor(int xp) {
        return Math.min(10, (int) Math.floor(Math.sqrt(Math.max(0, xp) / 10.0)));
    }
}
