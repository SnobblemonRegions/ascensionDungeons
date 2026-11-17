package com.ascensiondungeons.outbreak;

/**
 * How many enemies an Outbreak wave adds. Pure maths.
 *
 * @param base           enemies in a stage-0 wave for one player
 * @param perStage       extra enemies per Outbreak stage
 * @param perExtraPlayer extra enemies per player beyond the first
 * @param maxAlive       the wave never pushes the number alive past this
 */
public record WavePlanner(int base, int perStage, int perExtraPlayer, int maxAlive) {
    public WavePlanner {
        if (base < 0 || perStage < 0 || perExtraPlayer < 0) throw new IllegalArgumentException("wave sizes must not be negative");
        if (maxAlive < 1) throw new IllegalArgumentException("maxAlive must be at least 1");
    }

    public int count(int stage, int players, int alive) {
        if (players < 1) return 0;
        int wanted = base + perStage * Math.max(0, stage) + perExtraPlayer * (players - 1);
        return Math.max(0, Math.min(wanted, maxAlive - Math.max(0, alive)));
    }
}
