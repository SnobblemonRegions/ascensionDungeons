package com.ascensiondungeons.spike;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.mc.FantasyRunHost;
import com.ascensiondungeons.run.Run;
import com.ascensiondungeons.run.RunConfig;
import com.ascensiondungeons.run.RunHost;
import com.ascensiondungeons.run.RunManager;
import com.ascensiondungeons.run.RunState;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Headless end-to-end check of RunManager + FantasyRunHost with a pretend participant (no client needed).
 * Env DUNGEONSPIKE_E2E=1. Logs "E2E ..." lines and halts the server when the run is closed.
 */
public final class E2E {
    private E2E() {}

    /** Real level handling, but one imaginary player who is always online and always inside. */
    private static final class PretendPlayerHost implements RunHost {
        private final FantasyRunHost real;
        PretendPlayerHost(FantasyRunHost real) { this.real = real; }
        @Override public void openLevel(Run r) throws Exception { real.openLevel(r); }
        @Override public int requestChunks(Run r, int max) { return real.requestChunks(r, max); }
        @Override public boolean chunksReady(Run r) { return real.chunksReady(r); }
        @Override public boolean isOnline(UUID p) { return true; }
        @Override public boolean isInside(Run r, UUID p) { return true; }
        @Override public boolean enter(Run r, UUID p) { return true; }
        @Override public boolean exit(Run r, UUID p) { return true; }
        @Override public void releaseLevel(Run r) { real.releaseLevel(r); }
        @Override public void deleteLevel(Run r) { real.deleteLevel(r); }
        @Override public boolean isLevelGone(Run r) { return real.isLevelGone(r); }
        @Override public void message(UUID p, String t) { }
    }

    public static void install() {
        RunManager[] mgr = {null};
        Run[] run = {null};
        RunState[] last = {null};
        long[] startTick = {0};
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            long t = server.getTickCount();
            if (mgr[0] == null && t > 200) start(server, mgr, run, startTick, t);
            if (mgr[0] == null) return;
            mgr[0].tick(t);
            Run r = run[0];
            if (r.state() != last[0]) {
                last[0] = r.state();
                if (r.state() == RunState.ACTIVE) verifyWorld(server);
                AscensionDungeonsLog.LOG.info("E2E t+{} {} reason={}", t - startTick[0], r.state(), r.endReason());
            }
            if (r.state() == RunState.CLOSED || r.state() == RunState.CLEANUP_FAILED || t - startTick[0] > 3000) {
                AscensionDungeonsLog.LOG.info("E2E finished state={} resourceCount={} levelsLoaded={}", r.state(),
                        mgr[0].resourceCount(), java.util.stream.StreamSupport.stream(server.getAllLevels().spliterator(), false).count());
                server.halt(false);
            }
        });
    }

    /** Generates the interesting chunks of the run's world and checks entry, chests, corridors and plugs. */
    private static void verifyWorld(MinecraftServer server) {
        net.minecraft.server.level.ServerLevel level = null;
        for (net.minecraft.server.level.ServerLevel l : server.getAllLevels()) {
            if (l.getChunkSource().getGenerator() instanceof com.ascensiondungeons.mc.DungeonChunkGenerator) level = l;
        }
        if (level == null) {
            AscensionDungeonsLog.LOG.error("E2E CHECK FAIL no dungeon level found");
            return;
        }
        var gen = (com.ascensiondungeons.mc.DungeonChunkGenerator) level.getChunkSource().getGenerator();
        var grid = gen.grid();
        int fails = 0;
        // Every block state of every biome's template must parse, whether or not this run's grid happens to use that biome.
        int states = 0;
        for (var biome : com.ascensiondungeons.mc.DungeonTemplates.available()) {
            for (String name : com.ascensiondungeons.mc.DungeonTemplates.get(biome).schematic().palette()) {
                states++;
                try {
                    net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), name, false);
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
                    fails += check("block state of " + biome + " parses: " + name, false);
                }
            }
        }
        AscensionDungeonsLog.LOG.info("E2E checked {} palette entries across {} biomes", states, com.ascensiondungeons.mc.DungeonTemplates.available().size());
        int[] e = gen.entry();
        fails += check("entry floor solid", !level.getBlockState(new net.minecraft.core.BlockPos(e[0], e[1] - 1, e[2])).isAir());
        fails += check("entry feet clear", level.getBlockState(new net.minecraft.core.BlockPos(e[0], e[1], e[2])).isAir());
        fails += check("entry head clear", level.getBlockState(new net.minecraft.core.BlockPos(e[0], e[1] + 1, e[2])).isAir());
        int chests = 0, corridors = 0, plugs = 0;
        for (var cell : grid.cells()) {
            var template = com.ascensiondungeons.mc.DungeonTemplates.get(cell.biome());
            var o = cell.orientation();
            for (int[] m : template.chestMarkers()) {
                int[] d = o.toWorld(m[0] - 88, m[2] - 88);
                var pos = new net.minecraft.core.BlockPos(cell.cx() * 176 + d[0], m[1], cell.cz() * 176 + d[1]);
                level.getChunk(pos);
                var st = level.getBlockState(pos);
                boolean ok = st.is(net.minecraft.world.level.block.Blocks.CHEST)
                        || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString().equals("lootr:lootr_chest");
                fails += check("chest at " + pos.toShortString() + " is " + st.getBlock(), ok);
                chests++;
            }
            for (com.ascensiondungeons.gen.Side tside : new com.ascensiondungeons.gen.Side[] {
                    com.ascensiondungeons.gen.Side.N, com.ascensiondungeons.gen.Side.S, com.ascensiondungeons.gen.Side.E}) {
                int tx = tside == com.ascensiondungeons.gen.Side.E ? 170 : 88;
                int tz = tside == com.ascensiondungeons.gen.Side.E ? 88 : tside == com.ascensiondungeons.gen.Side.N ? 5 : 170;
                int[] d = o.toWorld(tx - 88, tz - 88);
                int x = cell.cx() * 176 + d[0], z = cell.cz() * 176 + d[1];
                level.getChunk(new net.minecraft.core.BlockPos(x, 8, z));
                boolean open = cell.open().contains(o.toWorld(tside));
                boolean air = level.getBlockState(new net.minecraft.core.BlockPos(x, 8, z)).isAir();
                fails += check("corridor " + tside + " of cell " + cell.cx() + "," + cell.cz() + " " + cell.biome() + (open ? " open" : " sealed"), open == air);
                if (open) corridors++;
                if (!open) {
                    // Plug sits behind the plane: N z=16, S z=163, E x=163 in template coordinates.
                    int px = tside == com.ascensiondungeons.gen.Side.E ? 163 : 88;
                    int pz = tside == com.ascensiondungeons.gen.Side.E ? 88 : tside == com.ascensiondungeons.gen.Side.N ? 16 : 163;
                    int[] pd = o.toWorld(px - 88, pz - 88);
                    var pp = new net.minecraft.core.BlockPos(cell.cx() * 176 + pd[0], 8, cell.cz() * 176 + pd[1]);
                    level.getChunk(pp);
                    fails += check("plug " + tside + " of cell " + cell.cx() + "," + cell.cz() + " is solid", !level.getBlockState(pp).isAir());
                    plugs++;
                }
            }
        }
        // Continuity across every shared edge: the centre line must be open for a few blocks on both sides.
        int joins = 0;
        for (var cell : grid.cells()) {
            for (var side : cell.open()) {
                if (side != com.ascensiondungeons.gen.Side.E && side != com.ascensiondungeons.gen.Side.S) continue;
                for (int k = -8; k <= 8; k++) {
                    int x = side == com.ascensiondungeons.gen.Side.E ? cell.cx() * 176 + 87 + k : cell.cx() * 176;
                    int z = side == com.ascensiondungeons.gen.Side.S ? cell.cz() * 176 + 87 + k : cell.cz() * 176;
                    var pos = new net.minecraft.core.BlockPos(x, 8, z);
                    level.getChunk(pos);
                    fails += check("join " + side + " of " + cell.cx() + "," + cell.cz() + " offset " + k, level.getBlockState(pos).isAir());
                }
                joins++;
            }
        }
        // Every biome's chest table must load and, rolled many times, give items; log what the first biome yields.
        int tablesChecked = 0;
        for (var biome : com.ascensiondungeons.mc.DungeonTemplates.available()) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ascensiondungeons",
                            "chests/" + biome.name().toLowerCase(java.util.Locale.ROOT)));
            var table = server.reloadableRegistries().getLootTable(key);
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                            net.minecraft.world.phys.Vec3.ZERO)
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            int items = 0;
            java.util.Set<String> seen = new java.util.TreeSet<>();
            for (int i = 0; i < 200; i++) {
                for (var stack : table.getRandomItems(params)) {
                    items += stack.getCount();
                    seen.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
            }
            fails += check("loot table chests/" + biome + " yields items", items > 0 && table != net.minecraft.world.level.storage.loot.LootTable.EMPTY);
            fails += check("loot table chests/" + biome + " includes its custom token", seen.contains("minecraft:paper"));
            tablesChecked++;
            if (tablesChecked == 1) AscensionDungeonsLog.LOG.info("E2E loot sample {}: {}", biome, seen);
        }
        AscensionDungeonsLog.LOG.info("E2E WORLD CHECK done: {} failures, {} chests, {} open corridors, {} plugs, {} joins",
                fails, chests, corridors, plugs, joins);
    }

    private static int check(String what, boolean ok) {
        if (!ok) AscensionDungeonsLog.LOG.error("E2E CHECK FAIL {}", what);
        return ok ? 0 : 1;
    }

    private static void start(MinecraftServer server, RunManager[] mgr, Run[] run, long[] startTick, long t) {
        RunConfig cfg = new RunConfig();
        cfg.durationTicks = 200;
        cfg.prewarmRadiusChunks = 3;
        cfg.borderRadius = 256;
        mgr[0] = new RunManager(new PretendPlayerHost(new FantasyRunHost(server, cfg)), cfg, () -> 1234L);
        run[0] = mgr[0].request(Set.of(UUID.randomUUID())).run();
        startTick[0] = t;
        AscensionDungeonsLog.LOG.info("E2E requested run {}", run[0].id());
    }
}
