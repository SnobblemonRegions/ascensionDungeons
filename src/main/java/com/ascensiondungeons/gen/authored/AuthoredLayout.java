package com.ascensiondungeons.gen.authored;

import com.ascensiondungeons.gen.SpongeSchematic;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * One authored dungeon: a schematic plus a layout file declaring its rooms, light spots, seals, chests, mechanics, hazards,
 * encounters and bosses (see docs/type-dungeons-plan.md). All coordinates are schematic coordinates; the run level places the
 * schematic at {@link Data#origin}. Contains no Minecraft classes.
 */
public final class AuthoredLayout {
    public static final class Box {
        public int[] min, max;
        public boolean contains(int x, int y, int z) {
            return x >= min[0] && x <= max[0] && y >= min[1] && y <= max[1] && z >= min[2] && z <= max[2];
        }
        public int volume() { return (max[0] - min[0] + 1) * (max[1] - min[1] + 1) * (max[2] - min[2] + 1); }
        public int[] centre() { return new int[] {(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2}; }
        public static Box of(JsonElement e) {
            if (e == null || !e.isJsonObject()) return null;
            Box b = new Box();
            b.min = Jx.ints(e.getAsJsonObject(), "min");
            b.max = Jx.ints(e.getAsJsonObject(), "max");
            return b.min == null || b.max == null ? null : b;
        }
    }
    public static final class Room {
        public String id, name;
        public int[] min, max;
        public int[][] spawnCells = new int[0][];
        public boolean contains(int x, int y, int z) {
            return x >= min[0] && x <= max[0] && y >= min[1] && y <= max[1] && z >= min[2] && z <= max[2];
        }
    }
    public static final class Seal { public String id, block; public int[] min, max; }
    public static final class Chest { public int[] pos; public String kind, state, key, table; }
    public static final class Data {
        public int version;
        public String id, name, biome = "minecraft:the_void";
        public int[] origin = {0, 20, 0};
        public int[] size, entry;
        public int lightLevel = 10;
        public String lootTable = "ascensiondungeons:chests/flooded_ruins";
        public Room[] rooms = new Room[0];
        public int[][] lights = new int[0][];
        public Seal[] seals = new Seal[0];
        public Chest[] chests = new Chest[0];
        public JsonObject keys = new JsonObject();
        public JsonArray mechanics = new JsonArray(), hazards = new JsonArray(), encounters = new JsonArray(), bosses = new JsonArray(), pads = new JsonArray();
        public JsonObject completion = new JsonObject();
        /** Optional Cobblemon section: {tier, pools: {name: [{species, weight, min, max}]}}. */
        public JsonObject pokemon = new JsonObject();
    }

    private static final Map<String, AuthoredLayout> CACHE = new HashMap<>();

    private final SpongeSchematic schem;
    private final Data data;
    private final Set<Integer> lights = new HashSet<>();
    private final Map<Integer, String> sealBlocks = new HashMap<>();
    private final JsonObject outbreak;

    private AuthoredLayout(SpongeSchematic schem, Data data, JsonObject outbreak) {
        this.schem = schem;
        this.data = data;
        this.outbreak = outbreak;
        for (int[] l : data.lights) lights.add(key(l[0], l[1], l[2]));
        for (Seal s : data.seals)
            for (int x = s.min[0]; x <= s.max[0]; x++)
                for (int y = s.min[1]; y <= s.max[1]; y++)
                    for (int z = s.min[2]; z <= s.max[2]; z++) sealBlocks.put(key(x, y, z), s.block);
    }

    public static synchronized AuthoredLayout get(String id) {
        AuthoredLayout l = CACHE.get(id);
        if (l == null) { l = load(id); CACHE.put(id, l); }
        return l;
    }

    private static AuthoredLayout load(String id) {
        String base = "/data/ascensiondungeons/dungeons/" + id + "/";
        try (InputStream s = AuthoredLayout.class.getResourceAsStream(base + "dungeon.schem");
             InputStream j = AuthoredLayout.class.getResourceAsStream(base + "layout.json")) {
            if (s == null || j == null) throw new IllegalStateException("Missing resources for dungeon " + id);
            SpongeSchematic schem = SpongeSchematic.read(s);
            Data data = new Gson().fromJson(new InputStreamReader(j, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.version != 2 || !id.equals(data.id)) throw new IllegalStateException("Bad layout for dungeon " + id);
            JsonObject outbreak = null;
            try (InputStream o = AuthoredLayout.class.getResourceAsStream(base + "outbreak.json")) {
                if (o != null) outbreak = new Gson().fromJson(new InputStreamReader(o, StandardCharsets.UTF_8), JsonObject.class);
            }
            return new AuthoredLayout(schem, data, outbreak);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load dungeon " + id, e);
        }
    }

    private int key(int x, int y, int z) { return x + 512 * z + 512 * 512 * y; }

    public Data data() { return data; }
    public String id() { return data.id; }
    public int width() { return schem.width(); }
    public int height() { return schem.height(); }
    public int length() { return schem.length(); }
    public String[] palette() { return schem.palette(); }
    public int paletteIndex(int x, int y, int z) { return schem.at(x, y, z); }
    public String stateAt(int x, int y, int z) { return schem.palette()[schem.at(x, y, z)]; }
    public boolean isLight(int x, int y, int z) { return lights.contains(key(x, y, z)); }
    /** The sealed block at this cell (placed at generation instead of the schematic's), or null. */
    public String sealAt(int x, int y, int z) { return sealBlocks.get(key(x, y, z)); }

    /** This dungeon's Outbreak dive settings ({@code outbreak.json} beside the layout), or null when it is an ordinary dungeon. */
    public JsonObject outbreak() { return outbreak; }

    /**
     * What this dungeon asks of a party, for the picker and the check at the door: its tier, the levels of its ordinary
     * enemies (the {@code wild_low} and {@code wild_mid} pools), its gatekeepers' level (the {@code boss} pool) and its length.
     */
    public com.ascensiondungeons.outbreak.Readiness.Card card() {
        JsonObject pools = Jx.o(data.pokemon, "pools");
        int min = level(pools, "wild_low", "min", true), max = level(pools, "wild_mid", "max", false), keeper = level(pools, "boss", "min", true);
        if (max < min) max = Math.max(min, level(pools, "wild_low", "max", false));
        return new com.ascensiondungeons.outbreak.Readiness.Card(data.id, data.name == null ? data.id : data.name, Math.max(1, Jx.i(data.pokemon, "tier", 1)), min, max, keeper,
                outbreak == null ? 0 : Jx.i(outbreak, "durationMinutes", 45));
    }

    /** The lowest (or highest) value of a key over a pool's entries; 0 when the pool is missing or empty. */
    private static int level(JsonObject pools, String pool, String key, boolean lowest) {
        int found = 0;
        boolean any = false;
        for (JsonElement e : Jx.a(pools, pool)) {
            if (!e.isJsonObject() || !e.getAsJsonObject().has(key)) continue;
            int v = e.getAsJsonObject().get(key).getAsInt();
            found = !any ? v : lowest ? Math.min(found, v) : Math.max(found, v);
            any = true;
        }
        return found;
    }

    // ---- the Depths (procedural, per run) ------------------------------------------------------------------------

    private com.ascensiondungeons.gen.depths.DepthsLink link;
    private final java.util.LinkedHashMap<Long, com.ascensiondungeons.gen.depths.Depths> depthsCache = new java.util.LinkedHashMap<>();
    private final Map<Long, Room[]> depthRoomCache = new HashMap<>();
    private String[] depthsPalette;

    /** Whether this dungeon has procedural Depths ({@code depths} in its outbreak.json). */
    public boolean hasDepths() { return outbreak != null && Jx.o(outbreak, "depths") != null; }

    private com.ascensiondungeons.gen.depths.Depths.Params depthsParams() {
        JsonObject p = Jx.o(outbreak, "depths");
        return new com.ascensiondungeons.gen.depths.Depths.Params(Jx.i(p, "gridW", 7), Jx.i(p, "gridH", 7), Jx.i(p, "cell", 24), Jx.d(p, "occupancy", 0.75), Jx.d(p, "loops", 0.35),
                Jx.i(p, "passage", 5));
    }

    /**
     * How the Depths join this dungeon: a tunnel out of the entrance room and a stairway down, wherever one can be cut without
     * touching another room, a mechanism or water; otherwise detached, with a teleporting stairway. Worked out once per dungeon
     * (it does not depend on the run), so the way down is always in the same place. {@code "link": "pad"} under {@code depths}
     * in outbreak.json forces the detached form. Null when the dungeon has no Depths.
     */
    public synchronized com.ascensiondungeons.gen.depths.DepthsLink link() {
        if (!hasDepths()) return null;
        if (link != null) return link;
        var p = depthsParams();
        int sizeX = p.gridW() * p.cell(), sizeZ = p.gridH() * p.cell(), doorX = com.ascensiondungeons.gen.depths.Depths.doorX(p);
        com.ascensiondungeons.gen.depths.DepthsLink.Grid grid = new com.ascensiondungeons.gen.depths.DepthsLink.Grid() {
            @Override public int width() { return schem.width(); }
            @Override public int height() { return schem.height(); }
            @Override public int length() { return schem.length(); }
            @Override public String state(int x, int y, int z) { return stateAt(x, y, z); }
        };
        Room home = entryRoom();
        if (home == null || "pad".equals(Jx.s(Jx.o(outbreak, "depths"), "link", "tunnel"))) {
            link = com.ascensiondungeons.gen.depths.DepthsLink.detached(grid, sizeX, sizeZ, doorX);
            return link;
        }
        java.util.List<Box> boxes = new java.util.ArrayList<>();
        java.util.List<int[]> points = new java.util.ArrayList<>();
        collectReserved(boxes, points);
        java.util.List<int[]> starts = new java.util.ArrayList<>();
        starts.add(data.entry);
        starts.addAll(java.util.Arrays.asList(home.spawnCells));
        java.util.function.Predicate<int[]> keep = c -> sealBlocks.containsKey(key(c[0], c[1], c[2])) || reserved(c, boxes, points);
        java.util.function.Predicate<int[]> own = c -> home.contains(c[0], c[1], c[2]);
        // As wide and as high as this dungeon's wall can take (five by six fits every Pokémon); "passage": 3 keeps it narrow.
        link = p.passage() >= 5 ? com.ascensiondungeons.gen.depths.DepthsLink.findWidest(grid, starts, keep, own, data.entry, sizeX, sizeZ, doorX)
                : com.ascensiondungeons.gen.depths.DepthsLink.find(grid, starts, keep, own, data.entry, sizeX, sizeZ, doorX);
        return link;
    }

    private void collectReserved(java.util.List<Box> boxes, java.util.List<int[]> points) {
        collect(data.hazards, boxes, points);
        collect(data.mechanics, boxes, points);
        collect(data.pads, boxes, points);
        for (Chest c : data.chests) points.add(c.pos);
    }

    /** This run's Depths, or null when the dungeon has none. Generated once per seed and remembered for a few runs. */
    public synchronized com.ascensiondungeons.gen.depths.Depths depths(long seed) {
        if (!hasDepths()) return null;
        com.ascensiondungeons.gen.depths.Depths d = depthsCache.get(seed);
        if (d == null) {
            // A maze joined by a tunnel has a doorway in its near wall; a detached one is sealed.
            d = com.ascensiondungeons.gen.depths.Depths.generate(seed, depthsParams(), link().physical() ? link().halfWidth() : -1);
            depthsCache.put(seed, d);
            if (depthsCache.size() > 6) {                       // a handful of live runs at most
                Long oldest = depthsCache.keySet().iterator().next();
                depthsCache.remove(oldest);
                depthRoomCache.remove(oldest);
                featureCache.remove(oldest);
                pieceCache.remove(oldest);
            }
        }
        return d;
    }

    /** Every room of this run: the authored ones, then the Depths' (ids {@code D<column>_<row>}), all in schematic coordinates. */
    public synchronized Room[] rooms(long seed) {
        com.ascensiondungeons.gen.depths.Depths d = depths(seed);
        if (d == null) return data.rooms;
        Room[] cached = depthRoomCache.get(seed);
        if (cached != null) return cached;
        {
            com.ascensiondungeons.gen.depths.DepthsLink at = link();
            java.util.List<Room> out = new java.util.ArrayList<>(java.util.Arrays.asList(data.rooms));
            for (com.ascensiondungeons.gen.depths.Depths.Room r : d.rooms()) {
                Room room = new Room();
                room.id = r.id();
                room.name = "The Depths";
                // The maze may be turned to face the dungeon, so its corners can swap.
                int[] a = at.toSchem(r.min()[0], r.min()[1], r.min()[2]), b = at.toSchem(r.max()[0], r.max()[1], r.max()[2]);
                room.min = new int[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
                room.max = new int[] {Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
                room.spawnCells = new int[r.cells().size()][];
                for (int i = 0; i < room.spawnCells.length; i++) {
                    int[] c = r.cells().get(i);
                    room.spawnCells[i] = at.toSchem(c[0], c[1], c[2]);
                }
                out.add(room);
            }
            cached = out.toArray(new Room[0]);
            depthRoomCache.put(seed, cached);
            return cached;
        }
    }

    public Room roomAt(long seed, int x, int y, int z) {
        for (Room r : rooms(seed)) if (r.contains(x, y, z)) return r;
        return null;
    }

    /** True for a room of the Depths (as opposed to an authored room). */
    public static boolean inDepths(Room room) { return room != null && room.id.startsWith("D") && room.id.indexOf('_') > 0; }

    /**
     * The four block states the Depths are built from: wall, floor, accent, pillar. Taken from {@code depths.palette} in
     * outbreak.json when given, otherwise the dungeon's own most-used building blocks, so the Depths look like part of it.
     */
    public synchronized String[] depthsPalette() {
        if (depthsPalette != null) return depthsPalette;
        JsonObject given = Jx.o(Jx.o(outbreak, "depths"), "palette");
        java.util.List<String> ranked = new java.util.ArrayList<>();
        if (given == null || !given.has("wall") || !given.has("floor") || !given.has("accent") || !given.has("pillar")) {
            long[] uses = new long[schem.palette().length];
            for (int y = 0; y < schem.height(); y++) for (int z = 0; z < schem.length(); z++) for (int x = 0; x < schem.width(); x++) uses[schem.at(x, y, z)]++;
            Map<String, Long> byBlock = new HashMap<>();
            for (int i = 0; i < uses.length; i++) {
                String name = schem.palette()[i];
                int bracket = name.indexOf('[');
                if (bracket > 0) name = name.substring(0, bracket);
                if (buildingBlock(name)) byBlock.merge(name, uses[i], Long::sum);
            }
            byBlock.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).forEach(e -> ranked.add(e.getKey()));
        }
        String fallback = ranked.isEmpty() ? "minecraft:stone_bricks" : ranked.get(0);
        depthsPalette = new String[] {
                Jx.s(given, "wall", fallback),
                Jx.s(given, "floor", ranked.size() > 1 ? ranked.get(1) : fallback),
                Jx.s(given, "accent", ranked.size() > 2 ? ranked.get(2) : fallback),
                Jx.s(given, "pillar", ranked.size() > 3 ? ranked.get(3) : ranked.size() > 2 ? ranked.get(2) : fallback)};
        return depthsPalette;
    }

    /** A plain, full, inert block that is safe to build walls and floors from. Deliberately strict: when in doubt, leave it out. */
    static boolean buildingBlock(String name) {
        String n = name.substring(name.indexOf(':') + 1);
        String[] no = {"air", "water", "lava", "light", "stairs", "slab", "_wall", "fence", "leaves", "glass", "door", "torch", "lantern", "chest", "lever", "button", "plate",
                "carpet", "pane", "bars", "chain", "sign", "banner", "vine", "flower", "sapling", "fern", "mushroom", "kelp", "seagrass", "coral", "rail", "candle", "bed", "head",
                "skull", "pot", "cluster", "bud", "dripstone", "sand", "gravel", "powder", "tnt", "spawner", "magma", "cactus", "campfire", "fire", "honey", "slime", "sculk",
                "barrier", "structure", "command", "piston", "observer", "redstone", "dispenser", "dropper", "hopper", "furnace", "table", "anvil", "cauldron", "bell", "beacon",
                "portal", "farmland", "path", "ice", "snow", "web", "scaffolding", "ladder", "roots", "moss", "azalea", "bamboo", "sugar", "wheat", "crop", "stem", "petals",
                "grass", "bush", "tulip", "orchid", "allium", "dandelion", "poppy", "lily", "rose", "peony", "lilac", "sunflower", "froglight", "shroomlight", "glowstone",
                "sea_lantern", "end_rod", "lightning", "tripwire", "hook", "banner", "shulker", "barrel", "bookshelf", "lectern", "jukebox", "note_block", "target", "sponge",
                "infested", "suspicious", "egg", "cake", "frame", "stand", "grindstone", "loom", "composter", "beehive", "bee_nest", "conduit", "respawn", "lodestone", "dirt",
                "mud", "clay", "podzol", "mycelium", "nylium", "obsidian", "bedrock", "ore", "raw_", "wart", "hay", "melon", "pumpkin", "dried", "bone_block", "anchor", "vault",
                "trial", "crafter", "copper_bulb", "grate", "trapdoor", "hanging", "pink_petals", "spore", "sprouts", "pitcher", "torchflower", "cocoa", "nether_wart", "chorus",
                "dragon", "wither", "soul", "netherrack", "basalt_"};
        for (String bad : no) if (n.contains(bad)) return false;
        return true;
    }

    /**
     * Slabs the stairway's half steps may be built from, best first: {@code depths.step} in outbreak.json, the slab forms of
     * the four Depths blocks, the slabs the dungeon itself uses most, and a plain stone slab. The game uses the first that exists.
     */
    public synchronized java.util.List<String> depthsSteps() {
        java.util.List<String> out = new java.util.ArrayList<>();
        String given = Jx.s(Jx.o(outbreak, "depths"), "step", null);
        if (given != null) out.add(given);
        for (String block : depthsPalette()) { String slab = slabOf(block); if (!out.contains(slab)) out.add(slab); }
        Map<String, Integer> used = new HashMap<>();
        for (int x = 0; x < schem.width(); x++) for (int y = 0; y < schem.height(); y++) for (int z = 0; z < schem.length(); z++) {
            String state = stateAt(x, y, z);
            int cut = state.indexOf('[');
            String name = cut < 0 ? state : state.substring(0, cut);
            if (name.endsWith("_slab")) used.merge(name, 1, Integer::sum);
        }
        used.entrySet().stream().sorted((a, b) -> b.getValue() != a.getValue().intValue() ? b.getValue() - a.getValue() : a.getKey().compareTo(b.getKey()))
                .forEach(e -> { if (!out.contains(e.getKey())) out.add(e.getKey()); });
        if (!out.contains("minecraft:smooth_stone_slab")) out.add("minecraft:smooth_stone_slab");
        return out;
    }

    /** The name a block's slab would have, if it has one: {@code stone_bricks -> stone_brick_slab}, {@code oak_planks -> oak_slab}. */
    public static String slabOf(String block) {
        if (block.endsWith("_bricks") || block.endsWith("_tiles")) return block.substring(0, block.length() - 1) + "_slab";
        if (block.endsWith("_planks")) return block.substring(0, block.length() - "_planks".length()) + "_slab";
        return block + "_slab";
    }

    private boolean airAt(com.ascensiondungeons.gen.depths.Depths d, int x, int y, int z) {
        com.ascensiondungeons.gen.depths.DepthsLink at = link();
        if (at != null) {
            Byte cut = at.override(x, y, z);                       // the tunnel and the stairway
            if (cut != null) return com.ascensiondungeons.gen.depths.DepthsLink.open(cut);
        }
        if (x >= 0 && y >= 0 && z >= 0 && x < schem.width() && y < schem.height() && z < schem.length()) return air(x, y, z);
        if (d == null || at == null) return false;
        int[] l = at.toLocal(x, y, z);
        return d.open(l[0], l[1], l[2]);
    }

    /** Whether a creature can occupy this cell in this run: open air in the dungeon, the tunnel, the stairway or the Depths. */
    public boolean passable(long seed, int x, int y, int z) { return airAt(depths(seed), x, y, z); }

    /** The room the party enters in, or null. */
    public Room entryRoom() { return roomAt(data.entry[0], data.entry[1], data.entry[2]); }

    private boolean air(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= schem.width() || y >= schem.height() || z >= schem.length()) return false;
        String state = stateAt(x, y, z);
        return state.equals("minecraft:air") || state.equals("minecraft:cave_air");
    }

    /** A standing cell with open floor on all four sides, so something placed on it can always be walked around. */
    private boolean open(com.ascensiondungeons.gen.depths.Depths depths, int[] c) {
        int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            int x = c[0] + d[0], z = c[2] + d[1];
            if (!airAt(depths, x, c[1], z) || !airAt(depths, x, c[1] + 1, z) || airAt(depths, x, c[1] - 1, z)) return false;
        }
        return true;
    }

    /** Boxes and points a feature must keep clear of: hazards, trial plates, levers, locks, authored chests and the boss's room. */
    private boolean reserved(int[] c, java.util.List<Box> boxes, java.util.List<int[]> points) {
        for (Box b : boxes) if (b.contains(c[0], c[1], c[2])) return true;
        for (int[] p : points) {
            int dx = p[0] - c[0], dy = p[1] - c[1], dz = p[2] - c[2];
            if (dx * dx + dy * dy + dz * dz < 9) return true;
        }
        return false;
    }

    private static void collect(JsonElement e, java.util.List<Box> boxes, java.util.List<int[]> points) {
        if (e == null) return;
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            if (a.size() == 3 && a.get(0).isJsonPrimitive() && a.get(0).getAsJsonPrimitive().isNumber()) { points.add(Jx.ints(a)); return; }
            for (JsonElement x : a) collect(x, boxes, points);
        } else if (e.isJsonObject()) {
            Box b = Box.of(e);
            if (b != null) { boxes.add(b); return; }
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) collect(x.getValue(), boxes, points);
        }
    }

    private final Map<Long, java.util.List<com.ascensiondungeons.outbreak.FeaturePlanner.Placement>> featureCache = new HashMap<>();
    private final Map<Long, java.util.List<com.ascensiondungeons.outbreak.SetPieces.Piece>> pieceCache = new HashMap<>();

    private com.ascensiondungeons.outbreak.PokemonFinds.Lists finds;

    /** The Pokémon this dungeon's chests can hold, by tier ({@code finds.json} beside the layout); empty lists when it has none. */
    public synchronized com.ascensiondungeons.outbreak.PokemonFinds.Lists finds() {
        if (finds != null) return finds;
        java.util.Map<String, java.util.List<String>> byName = new HashMap<>();
        java.util.Map<String, Integer> levels = new HashMap<>();
        int min = 5, max = 20;
        try (var in = AuthoredLayout.class.getResourceAsStream("/data/ascensiondungeons/dungeons/" + data.id + "/finds.json")) {
            if (in != null) {
                JsonObject o = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : new String[] {"standard", "starter", "pseudo", "legendary", "mythical"}) {
                    java.util.List<String> names = new java.util.ArrayList<>();
                    for (JsonElement e : Jx.a(o, key)) names.add(e.getAsString());
                    byName.put(key, names);
                    if (o.has(key + "Level")) levels.put(key + "Level", o.get(key + "Level").getAsInt());
                }
                JsonArray range = Jx.a(o, "standardLevels");
                if (range.size() == 2) { min = range.get(0).getAsInt(); max = range.get(1).getAsInt(); }
            }
        } catch (java.io.IOException | RuntimeException e) {
            com.ascensiondungeons.AscensionDungeonsLog.LOG.warn("Dungeon {} finds.json could not be read; its chests hold no Pokémon", data.id, e);
        }
        finds = com.ascensiondungeons.outbreak.PokemonFinds.lists(byName, min, max, levels);
        return finds;
    }

    /** This run's set pieces in the Depths (nests, alphas, hazard rooms); empty when the dungeon has none. */
    public synchronized java.util.List<com.ascensiondungeons.outbreak.SetPieces.Piece> setPieces(long seed) {
        features(seed);
        return pieceCache.getOrDefault(seed, java.util.List.of());
    }

    /** The maze's own record of a Depths room, or null. */
    public com.ascensiondungeons.gen.depths.Depths.Room depthsRoom(long seed, String id) {
        com.ascensiondungeons.gen.depths.Depths d = depths(seed);
        if (d == null) return null;
        for (var r : d.rooms()) if (r.id().equals(id)) return r;
        return null;
    }

    /** A room's {@link com.ascensiondungeons.outbreak.DepthBands depth band}: the hand-built dungeon is band 0. */
    public int band(long seed, Room room) {
        if (room == null || !inDepths(room)) return 0;
        com.ascensiondungeons.gen.depths.Depths d = depths(seed);
        var r = depthsRoom(seed, room.id);
        return d == null || r == null ? 0 : com.ascensiondungeons.outbreak.DepthBands.band(r.depth(), d.maxDepth());
    }

    /**
     * Where this run's Outbreak features go, from its seed: empty when the dungeon has no {@code outbreak.json}. The chunk
     * generator places the blocks and the dive runs them, and both call this, so it must stay a pure function of the seed.
     */
    public synchronized java.util.List<com.ascensiondungeons.outbreak.FeaturePlanner.Placement> features(long seed) {
        if (outbreak == null) return java.util.List.of();
        java.util.List<com.ascensiondungeons.outbreak.FeaturePlanner.Placement> cached = featureCache.get(seed);
        if (cached != null) return cached;
        {
            java.util.List<Box> boxes = new java.util.ArrayList<>();
            java.util.List<int[]> points = new java.util.ArrayList<>();
            collectReserved(boxes, points);
            com.ascensiondungeons.gen.depths.DepthsLink joined = link();
            if (joined != null && joined.physical()) {
                // Keep the way down clear: nothing is placed at the tunnel's mouth or just inside the maze's door.
                int[] m = joined.mouth();
                int keepClear = joined.halfWidth() + 1;
                for (int a = -keepClear; a <= keepClear; a++) for (int b = -keepClear; b <= keepClear; b++) points.add(new int[] {m[0] + a, m[1], m[2] + b});
            }
            Set<String> bossRooms = new HashSet<>();
            for (JsonElement b : data.bosses) bossRooms.add(Jx.s(b.getAsJsonObject(), "room", ""));
            com.ascensiondungeons.gen.depths.Depths depths = depths(seed);
            java.util.List<com.ascensiondungeons.outbreak.FeaturePlanner.Room> rooms = new java.util.ArrayList<>();
            java.util.List<com.ascensiondungeons.outbreak.SetPieces.Room> deepRooms = new java.util.ArrayList<>();
            for (Room r : rooms(seed)) {
                java.util.List<int[]> cells = new java.util.ArrayList<>();
                if (!bossRooms.contains(r.id)) for (int[] c : r.spawnCells) if (open(depths, c) && !reserved(c, boxes, points)) cells.add(c);
                int band = band(seed, r);
                rooms.add(new com.ascensiondungeons.outbreak.FeaturePlanner.Room(r.id, cells, band));
                var shape = inDepths(r) ? depthsRoom(seed, r.id) : null;
                if (shape != null && shape.kind() != com.ascensiondungeons.gen.depths.Depths.Kind.ENTRANCE)
                    deepRooms.add(new com.ascensiondungeons.outbreak.SetPieces.Room(r.id, band, shape.kind() == com.ascensiondungeons.gen.depths.Depths.Kind.VAULT, cells));
            }
            int[] arrival = null;
            String arrivalRoom = null;
            if (depths != null && !joined.physical()) {
                // Detached Depths are reached by a teleporting stairway: a pad near the entrance and one where the party arrives.
                int[] e = depths.entrance();
                arrival = joined.toSchem(e[0], e[1], e[2]);
                Room at = roomAt(seed, arrival[0], arrival[1], arrival[2]);
                arrivalRoom = at == null ? "" : at.id;
            }
            JsonObject f = Jx.o(outbreak, "features");
            var counts = new com.ascensiondungeons.outbreak.FeaturePlanner.Counts(Jx.i(f, "chests", 4), Jx.i(f, "outbreakChests", 1),
                    Jx.i(f, "timerChests", 1), Jx.i(f, "strongboxes", 1), Jx.i(f, "altars", 1));
            Room home = entryRoom();
            java.util.List<com.ascensiondungeons.outbreak.FeaturePlanner.Placement> plan =
                    new java.util.ArrayList<>(com.ascensiondungeons.outbreak.FeaturePlanner.plan(rooms, data.entry, home == null ? "" : home.id, counts, seed, arrival, arrivalRoom));
            // The Depths' set pieces go in rooms of their own, after the ordinary features, keeping clear of them.
            JsonObject sp = Jx.o(outbreak, "setPieces");
            var pieceCounts = new com.ascensiondungeons.outbreak.SetPieces.Counts(Jx.i(sp, "nests", 2), Jx.i(sp, "alphas", 2), Jx.i(sp, "hazards", 3));
            java.util.List<int[]> taken = new java.util.ArrayList<>();
            for (var p : plan) taken.add(p.cell());
            java.util.List<com.ascensiondungeons.outbreak.SetPieces.Piece> pieces = deepRooms.isEmpty() ? java.util.List.of()
                    : java.util.List.copyOf(com.ascensiondungeons.outbreak.SetPieces.plan(deepRooms, pieceCounts, seed, taken));
            java.util.Random luck = new java.util.Random(seed ^ 0x484f415244L);
            for (var piece : pieces) {
                switch (piece.kind()) {
                    case NEST -> {
                        plan.add(new com.ascensiondungeons.outbreak.FeaturePlanner.Placement(com.ascensiondungeons.outbreak.FeaturePlanner.Kind.NEST, null, piece.room(), piece.centre()));
                        plan.add(new com.ascensiondungeons.outbreak.FeaturePlanner.Placement(com.ascensiondungeons.outbreak.FeaturePlanner.Kind.NEST_CHEST,
                                piece.band() >= 2 ? com.ascensiondungeons.outbreak.ChestRarity.EPIC : com.ascensiondungeons.outbreak.ChestRarity.RARE, piece.room(), piece.reward()));
                    }
                    case ALPHA -> plan.add(new com.ascensiondungeons.outbreak.FeaturePlanner.Placement(com.ascensiondungeons.outbreak.FeaturePlanner.Kind.HOARD,
                            com.ascensiondungeons.outbreak.ChestRarity.EPIC, piece.room(), piece.reward()));
                    case HAZARD -> plan.add(new com.ascensiondungeons.outbreak.FeaturePlanner.Placement(com.ascensiondungeons.outbreak.FeaturePlanner.Kind.CHEST,
                            com.ascensiondungeons.outbreak.ChestRarity.roll(luck, 2 + com.ascensiondungeons.outbreak.DepthBands.chestLuck(piece.band())), piece.room(), piece.reward()));
                }
            }
            pieceCache.put(seed, pieces);
            cached = java.util.List.copyOf(plan);
            featureCache.put(seed, cached);
            return cached;
        }
    }

    public Room room(String id) {
        for (Room r : data.rooms) if (r.id.equals(id)) return r;
        throw new IllegalArgumentException("No such room " + id + " in dungeon " + data.id);
    }

    public Room roomAt(int x, int y, int z) {
        for (Room r : data.rooms) if (r.contains(x, y, z)) return r;
        return null;
    }
}
