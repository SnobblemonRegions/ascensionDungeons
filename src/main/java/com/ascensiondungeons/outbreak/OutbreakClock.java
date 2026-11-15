package com.ascensiondungeons.outbreak;

/**
 * The Outbreak: how worked-up a dungeon has become the longer a party stays. Pure maths, no Minecraft types.
 *
 * <p>The clock counts ticks since the run started plus any {@linkplain #addPenalty penalty} a curse or event added, and turns
 * that into a stage {@code 0..maxStage}. The stage drives spawn pressure, elite chance and the zone tier. It is separate from
 * the run's hard deadline and from the Threat band chosen at the door.
 */
public final class OutbreakClock {
    /**
     * Tuning for one dungeon. Every number is provisional.
     *
     * @param ticksPerStage     ticks of effective time per stage
     * @param maxStage          highest stage reached
     * @param spawnStep         extra spawn pressure per stage (0.25 means x1.25 at stage 1)
     * @param eliteStep         elite chance added per stage
     * @param eliteCap          ceiling on elite chance, 0..1
     * @param stagesPerTierStep stages needed to raise the zone tier by one
     */
    public record Params(int ticksPerStage, int maxStage, double spawnStep, double eliteStep, double eliteCap, int stagesPerTierStep) {
        public Params {
            if (ticksPerStage < 1) throw new IllegalArgumentException("ticksPerStage must be at least 1");
            if (maxStage < 0) throw new IllegalArgumentException("maxStage must not be negative");
            if (spawnStep < 0 || eliteStep < 0) throw new IllegalArgumentException("steps must not be negative");
            if (eliteCap < 0 || eliteCap > 1) throw new IllegalArgumentException("eliteCap must be 0..1");
            if (stagesPerTierStep < 1) throw new IllegalArgumentException("stagesPerTierStep must be at least 1");
        }

        /** Spawn-pressure multiplier at a stage: 1 at stage 0. */
        public double spawnScale(int stage) { return 1.0 + spawnStep * clamp(stage); }

        /** Chance that a spawn is an elite at a stage, never above the cap. */
        public double eliteChance(int stage) { return Math.min(eliteCap, eliteStep * clamp(stage)); }

        /** The profile-zone tier for a dungeon whose base tier is {@code baseTier}, kept within the library's 1..10. */
        public int zoneTier(int baseTier, int stage) {
            return Math.max(1, Math.min(10, baseTier + clamp(stage) / stagesPerTierStep));
        }

        private int clamp(int stage) { return Math.max(0, Math.min(maxStage, stage)); }
    }

    private final Params params;
    private final long startTick;
    private long penaltyTicks;
    private int lastPolled;

    public OutbreakClock(Params params, long startTick) {
        this.params = params;
        this.startTick = startTick;
    }

    public Params params() { return params; }

    /** Adds effective time, as a curse or event would. Zero or negative amounts are ignored: nothing ever calms the dungeon. */
    public void addPenalty(long ticks) { if (ticks > 0) penaltyTicks += ticks; }

    public long penaltyTicks() { return penaltyTicks; }

    /** The current stage. A tick before the start counts as the start. */
    public int stage(long now) {
        long effective = Math.max(0, now - startTick) + penaltyTicks;
        long stage = effective / params.ticksPerStage();
        return (int) Math.min(params.maxStage(), stage);
    }

    /** How far through the current stage the clock is, 0..1; 1 once the last stage is reached. */
    public double progress(long now) {
        if (stage(now) >= params.maxStage()) return 1.0;
        long effective = Math.max(0, now - startTick) + penaltyTicks;
        return (effective % params.ticksPerStage()) / (double) params.ticksPerStage();
    }

    /** The new stage if it rose since the last call, otherwise -1. Skipped stages are reported as the latest one. */
    public int pollNewStage(long now) {
        int stage = stage(now);
        if (stage > lastPolled) {
            lastPolled = stage;
            return stage;
        }
        return -1;
    }
}
