package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AscensionLib by reflection (it stays optional, like CobbleRaids), following its DUNGEONS-CONTRACT.md: a profile zone with a tier
 * while the run level exists, and one {@code settleDungeonRun} call when a run is won. Players whose status is DISABLED or whose
 * call threw are kept in a pending list and retried (a settlement is exactly-once per run and player, so retrying is safe).
 */
public final class AscensionBridge implements AuthoredBridges.Ascension {
    private static final long RETRY_EVERY = 20L * 30;
    private static final int MAX_ATTEMPTS = 40;

    private static final class Pending {
        final UUID run; final String outcome; final int tier, arenaWins; final boolean boss; final Set<UUID> players;
        int attempts; long next;
        Pending(UUID run, String outcome, int tier, int arenaWins, boolean boss, Collection<UUID> players, long next) {
            this.run = run; this.outcome = outcome; this.tier = tier; this.arenaWins = arenaWins; this.boss = boss;
            this.players = new HashSet<>(players); this.next = next;
        }
    }

    private final Method open, close, settle;
    private final List<Pending> pending = new ArrayList<>();
    private long now;

    private AscensionBridge(Method open, Method close, Method settle) { this.open = open; this.close = close; this.settle = settle; }

    /** Null when AscensionLib is absent or its contract is not version 1. */
    public static AscensionBridge tryCreate() {
        try {
            Class<?> zones = Class.forName("com.ascensionlib.AscensionZones");
            int version = (int) zones.getMethod("contractVersion").invoke(null);
            if (version != 1) {
                AscensionDungeonsLog.LOG.warn("AscensionLib contract version {} is not supported (need 1); rewards disabled", version);
                return null;
            }
            Class<?> rewards = Class.forName("com.ascensionlib.AscensionRewards");
            return new AscensionBridge(zones.getMethod("openZone", String.class, int.class), zones.getMethod("closeZone", String.class),
                    rewards.getMethod("settleDungeonRun", UUID.class, String.class, int.class, int.class, boolean.class, Collection.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    @Override
    public void openZone(String dimensionId, int tier) {
        try {
            Object ok = open.invoke(null, dimensionId, tier);
            AscensionDungeonsLog.LOG.info("AscensionLib zone {} tier {}: {}", dimensionId, tier, ok);
        } catch (ReflectiveOperationException | LinkageError e) {
            AscensionDungeonsLog.LOG.warn("AscensionLib openZone failed for {}", dimensionId, e);
        }
    }

    @Override
    public void closeZone(String dimensionId) {
        try { close.invoke(null, dimensionId); }
        catch (ReflectiveOperationException | LinkageError e) { AscensionDungeonsLog.LOG.warn("AscensionLib closeZone failed for {}", dimensionId, e); }
    }

    @Override
    public void settle(UUID runId, String outcome, int tier, int arenaWins, boolean bossDefeated, Collection<UUID> players) {
        Pending p = new Pending(runId, outcome, tier, Math.max(0, Math.min(16, arenaWins)), bossDefeated, players, now);
        attempt(p);
        if (!p.players.isEmpty()) pending.add(p);
    }

    @Override
    public void tick(long tickNow) {
        now = tickNow;
        if (pending.isEmpty()) return;
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (now < p.next) continue;
            attempt(p);
            if (p.players.isEmpty() || p.attempts >= MAX_ATTEMPTS) it.remove();
        }
    }

    /** One call; removes the players that no longer need retrying. */
    private void attempt(Pending p) {
        p.attempts++;
        p.next = now + RETRY_EVERY;
        try {
            Object result = settle.invoke(null, p.run, p.outcome, p.tier, p.arenaWins, p.boss, new ArrayList<>(p.players));
            if (result instanceof Map<?, ?> statuses) {
                for (Map.Entry<?, ?> e : statuses.entrySet()) {
                    String status = String.valueOf(e.getValue());
                    AscensionDungeonsLog.LOG.info("AscensionLib settleDungeonRun {} {}: {}", p.run, e.getKey(), status);
                    if (!"DISABLED".equals(status) && e.getKey() instanceof UUID id) p.players.remove(id);
                }
            } else p.players.clear();
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException e) {
            AscensionDungeonsLog.LOG.warn("AscensionLib settleDungeonRun failed (attempt {})", p.attempts, e);
        }
    }
}
