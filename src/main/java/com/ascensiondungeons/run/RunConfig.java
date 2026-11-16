package com.ascensiondungeons.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/ascensiondungeons.json}. Public mutable fields so Gson maps the file directly; a missing key keeps its default here.
 * Every value is provisional.
 */
public final class RunConfig {
    // ---- run clock and capacity ----
    /** Exploration clock for district and type-pilot runs (default 20 minutes). */
    public int durationTicks = 20 * 60 * 20;
    /** Clock for Cinderforge runs (default 25 minutes). */
    public int cinderforgeDurationTicks = 25 * 60 * 20;
    /** Clock for authored type dungeons (default 25 minutes). */
    public int authoredDurationTicks = 25 * 60 * 20;
    /** Clock for Outbreak dives when the dungeon's outbreak.json does not set durationMinutes (default 45 minutes). */
    public int diveDurationTicks = 45 * 60 * 20;
    /** How long the final raid may take once the exploration clock ended. */
    public int bossPhaseTicks = 15 * 60 * 20;
    public int maxPartySize = 4;
    /** Runs that may hold a level at once; further requests wait in the queue. */
    public int maxConcurrentRuns = 2;
    public int maxQueuedRuns = 8;
    /** Chunks requested per tick while a level warms up. */
    public int chunksPerTick = 4;
    /** Ticks a level may take to delete before the run is flagged CLEANUP_FAILED. */
    public int cleanupTimeoutTicks = 20 * 30;

    // ---- world ----
    public int districtRadius = 3;
    public int borderRadius = 256;
    public int prewarmRadiusChunks = 6;

    // ---- wild Pokemon (Cobblemon) ----
    public int wildPerRun = 48;
    public int wildPerDistrict = 6;
    public int spawnIntervalTicks = 40;
    public int spawnMinDistance = 16;
    public int spawnMaxDistance = 48;
    /** Spawn entries per biome (lower-case {@code BiomeKind} name). */
    public Map<String, List<SpawnTable.Entry>> spawns = defaultSpawns();

    // ---- raids (CobbleRaids) ----
    public boolean raidRewards = true;
    public boolean raidCatchable = false;
    public int raidBossLevel = 70;
    public int raidActivationSeconds = 5;
    public int raidRetryTicks = 20 * 30;
    /** CobbleRaids raid-definition ids per biome. */
    public Map<String, List<String>> raidBosses = defaultRaidBosses();

    // ---- authored dungeons ----
    public boolean authoredPokemon = true;
    public boolean authoredRaids = true;
    public boolean authoredCombat = false;
    /** Whether any player may open the dungeon picker with /dive. Off, the command needs operator rights (for servers that open it from an NPC). */
    public boolean playerDives = true;
    /** How long a party invitation stands. */
    public int partyInviteSeconds = 60;
    public boolean authoredRealtimeBoss = false;
    public int combatFaintSeconds = 10;
    public double combatDamageMultiplier = 1.0;
    public double moveCooldownBaseSeconds = 2.0;
    public double moveCooldownSecondsPerPower = 0.02;

    /** Throws if a value would break the run state machine; fills in gaps a hand-edited file may have left. */
    public void validate() {
        if (durationTicks < 20) throw new IllegalStateException("durationTicks must be at least 20");
        if (cinderforgeDurationTicks < 20) throw new IllegalStateException("cinderforgeDurationTicks must be at least 20");
        if (authoredDurationTicks < 20) throw new IllegalStateException("authoredDurationTicks must be at least 20");
        if (diveDurationTicks < 20) throw new IllegalStateException("diveDurationTicks must be at least 20");
        if (bossPhaseTicks < 20) throw new IllegalStateException("bossPhaseTicks must be at least 20");
        if (maxPartySize < 1) throw new IllegalStateException("maxPartySize must be at least 1");
        if (maxConcurrentRuns < 1) throw new IllegalStateException("maxConcurrentRuns must be at least 1");
        if (maxQueuedRuns < 0) throw new IllegalStateException("maxQueuedRuns must not be negative");
        if (chunksPerTick < 1) throw new IllegalStateException("chunksPerTick must be at least 1");
        if (cleanupTimeoutTicks < 20) throw new IllegalStateException("cleanupTimeoutTicks must be at least 20");
        if (districtRadius < 1) throw new IllegalStateException("districtRadius must be at least 1");
        if (borderRadius < 16) throw new IllegalStateException("borderRadius must be at least 16");
        if (prewarmRadiusChunks < 0) throw new IllegalStateException("prewarmRadiusChunks must not be negative");
        if (spawnIntervalTicks < 1) throw new IllegalStateException("spawnIntervalTicks must be at least 1");
        if (spawnMinDistance < 0 || spawnMaxDistance < spawnMinDistance) throw new IllegalStateException("spawn distances must satisfy 0 <= min <= max");
        if (raidBossLevel < 1 || raidBossLevel > 100) throw new IllegalStateException("raidBossLevel must be 1-100");
        if (raidActivationSeconds < 1) throw new IllegalStateException("raidActivationSeconds must be at least 1");
        if (raidRetryTicks < 1) throw new IllegalStateException("raidRetryTicks must be at least 1");
        if (combatFaintSeconds < 0 || combatDamageMultiplier < 0 || moveCooldownBaseSeconds < 0 || moveCooldownSecondsPerPower < 0)
            throw new IllegalStateException("combat values must not be negative");
        if (spawns == null) spawns = defaultSpawns();
        if (raidBosses == null) raidBosses = defaultRaidBosses();
    }

    private static List<SpawnTable.Entry> table(Object... triples) {
        List<SpawnTable.Entry> out = new ArrayList<>();
        for (int i = 0; i < triples.length; i += 4)
            out.add(new SpawnTable.Entry((String) triples[i], (Integer) triples[i + 1], (Integer) triples[i + 2], (Integer) triples[i + 3]));
        return out;
    }

    /** Placeholder tables: a few common species per biome, levels 10-25. Edit freely; unknown species are dropped with a log line. */
    static Map<String, List<SpawnTable.Entry>> defaultSpawns() {
        Map<String, List<SpawnTable.Entry>> m = new LinkedHashMap<>();
        m.put("verdant_canopy", table("oddish", 10, 10, 20, "bulbasaur", 6, 12, 22, "caterpie", 10, 8, 16, "sunkern", 8, 10, 18));
        m.put("ashen_caldera", table("growlithe", 10, 12, 22, "slugma", 10, 12, 22, "numel", 8, 12, 22, "charmander", 6, 12, 22));
        m.put("glacial_vault", table("swinub", 10, 12, 22, "snorunt", 10, 12, 22, "snover", 8, 14, 24, "delibird", 5, 14, 24));
        m.put("crystal_cavern", table("geodude", 10, 10, 20, "nosepass", 8, 12, 22, "roggenrola", 10, 10, 20, "carbink", 4, 16, 26));
        m.put("flooded_ruins", table("psyduck", 10, 10, 20, "wooper", 10, 10, 20, "tentacool", 8, 12, 22, "lotad", 8, 10, 20));
        m.put("fungal_grotto", table("paras", 10, 10, 20, "shroomish", 10, 10, 20, "foongus", 8, 12, 22, "morelull", 6, 12, 22));
        m.put("sunken_garden", table("lotad", 10, 10, 20, "bellsprout", 10, 10, 20, "surskit", 8, 10, 20, "azurill", 6, 8, 16));
        m.put("ancient_foundry", table("magnemite", 10, 12, 22, "klink", 10, 12, 22, "ferroseed", 8, 12, 22, "beldum", 4, 14, 24));
        m.put("sunscorched_necropolis", table("sandshrew", 10, 12, 22, "trapinch", 10, 12, 22, "cubone", 8, 12, 22, "sandile", 8, 12, 22));
        m.put("painted_badlands", table("diglett", 10, 10, 20, "rhyhorn", 8, 14, 24, "hippopotas", 8, 12, 22, "baltoy", 8, 12, 22));
        m.put("cherry_grove", table("hoppip", 10, 10, 20, "cherubi", 10, 10, 20, "azurill", 8, 8, 16, "snubbull", 6, 12, 22));
        m.put("lapis_observatory", table("abra", 10, 10, 20, "natu", 8, 12, 22, "solosis", 8, 12, 22, "elgyem", 6, 14, 24));
        m.put("crimson_cathedral", table("gastly", 10, 12, 22, "misdreavus", 8, 14, 24, "litwick", 8, 12, 22, "shuppet", 8, 12, 22));
        m.put("gilded_library", table("rattata", 10, 8, 16, "meowth", 10, 10, 20, "abra", 6, 10, 20, "munna", 6, 12, 22));
        m.put("warped_hollow", table("zubat", 10, 10, 20, "drowzee", 8, 12, 22, "gothita", 8, 12, 22, "ralts", 6, 10, 20));
        m.put("end_spire", table("duskull", 10, 14, 24, "sableye", 8, 16, 26, "deino", 6, 16, 26, "dreepy", 6, 14, 24));
        return m;
    }

    /** Placeholder boss pools: ids that exist as CobbleRaids raid definitions. */
    static Map<String, List<String>> defaultRaidBosses() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("ashen_caldera", List.of("cobbleraids:charizard", "cobbleraids:blaziken"));
        for (String biome : new String[] {"verdant_canopy", "glacial_vault", "crystal_cavern", "flooded_ruins", "fungal_grotto", "sunken_garden",
                "ancient_foundry", "sunscorched_necropolis", "painted_badlands", "cherry_grove", "lapis_observatory", "crimson_cathedral",
                "gilded_library", "warped_hollow", "end_spire"})
            m.put(biome, List.of("cobbleraids:blastoise", "cobbleraids:chandelure"));
        return m;
    }
}
