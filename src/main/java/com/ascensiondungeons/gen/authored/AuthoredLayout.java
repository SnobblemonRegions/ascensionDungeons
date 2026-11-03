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

    private AuthoredLayout(SpongeSchematic schem, Data data) {
        this.schem = schem;
        this.data = data;
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
            return new AuthoredLayout(schem, data);
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

    public Room room(String id) {
        for (Room r : data.rooms) if (r.id.equals(id)) return r;
        throw new IllegalArgumentException("No such room " + id + " in dungeon " + data.id);
    }

    public Room roomAt(int x, int y, int z) {
        for (Room r : data.rooms) if (r.contains(x, y, z)) return r;
        return null;
    }
}
