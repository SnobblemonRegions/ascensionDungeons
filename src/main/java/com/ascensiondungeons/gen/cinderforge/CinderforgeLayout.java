package com.ascensiondungeons.gen.cinderforge;

import com.ascensiondungeons.gen.SpongeSchematic;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * The hand-built Cinderforge Bastion: one 129x85x176 schematic plus a runtime layout (markers, mechanics, spawn cells,
 * light spots) exported by tools/cinderforge from the same grid, so the two cannot disagree. All coordinates here are
 * schematic coordinates; a run level places the schematic at {@link #ORIGIN_X}/{@link #ORIGIN_Y}/{@link #ORIGIN_Z}.
 * Contains no Minecraft classes.
 */
public final class CinderforgeLayout {
    public static final int ORIGIN_X = 0, ORIGIN_Y = 20, ORIGIN_Z = 0;
    private static final String SCHEM = "/data/ascensiondungeons/cinderforge/dungeon.schem";
    private static final String JSON = "/data/ascensiondungeons/cinderforge/layout.json";

    public static final class Box {
        public int[] min, max;
        public boolean contains(int x, int y, int z) {
            return x >= min[0] && x <= max[0] && y >= min[1] && y <= max[1] && z >= min[2] && z <= max[2];
        }
        public int volume() { return (max[0] - min[0] + 1) * (max[1] - min[1] + 1) * (max[2] - min[2] + 1); }
        public int[] centre() { return new int[] {(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2}; }
    }
    public static final class Room {
        public String id, name;
        public int[] min, max;
        public int[][] spawnCells;
        public int standable, lights;
        public boolean contains(int x, int y, int z) {
            return x >= min[0] && x <= max[0] && y >= min[1] && y <= max[1] && z >= min[2] && z <= max[2];
        }
    }
    public static final class Lever { public int[] pos; public int stage; }
    public static final class Chest { public int[] pos; public String kind, state; }
    public static final class Furnace { public int[] mouth, top, dir; }
    public static final class R7 { public int[] centre, entry; public double radius; public int floorY, topY; }
    public static final class Arena { public int[] centre; public int radius; }
    public static final class Data {
        public int version;
        public Room[] rooms;
        public int[][] lights;
        public int[] entry, overseerSpawn, liftArrival, bossSpawn;
        public Lever[] levers;
        public Box eastPlug, westPlug, reservoir, tunnelPlug, descentGate, trench, portcullis, overseerGate, liftPad, crucibleLava, crucibleRim;
        public Box[] trenchPlugs, hiddenDoors, heatVents;
        public int[][] geysers;
        public Chest[] chests;
        public Furnace[] furnaces;
        public R7 r7;
        public Arena arena;
        public String keyId;
    }

    private static CinderforgeLayout instance;

    private final SpongeSchematic schem;
    private final Data data;
    private final Set<Integer> lights = new HashSet<>();
    private final Set<Integer> gateCells = new HashSet<>();

    private CinderforgeLayout(SpongeSchematic schem, Data data) {
        this.schem = schem;
        this.data = data;
        for (int[] l : data.lights) lights.add(key(l[0], l[1], l[2]));
        Box g = data.overseerGate;
        for (int x = g.min[0]; x <= g.max[0]; x++)
            for (int y = g.min[1]; y <= g.max[1]; y++)
                for (int z = g.min[2]; z <= g.max[2]; z++) gateCells.add(key(x, y, z));
    }

    /** Loaded once and shared by every run; immutable afterwards. */
    public static synchronized CinderforgeLayout get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static CinderforgeLayout load() {
        try (InputStream s = CinderforgeLayout.class.getResourceAsStream(SCHEM);
             InputStream j = CinderforgeLayout.class.getResourceAsStream(JSON)) {
            if (s == null || j == null) throw new IllegalStateException("Missing Cinderforge resources");
            SpongeSchematic schem = SpongeSchematic.read(s);
            Data data = new Gson().fromJson(new InputStreamReader(j, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.version != 1) throw new IllegalStateException("Unsupported Cinderforge layout version");
            return new CinderforgeLayout(schem, data);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load the Cinderforge layout", e);
        }
    }

    private int key(int x, int y, int z) { return x + 256 * z + 65536 * y; }

    public Data data() { return data; }
    public int width() { return schem.width(); }
    public int height() { return schem.height(); }
    public int length() { return schem.length(); }
    public boolean inside(int x, int y, int z) { return x >= 0 && y >= 0 && z >= 0 && x < width() && y < height() && z < length(); }
    public String[] palette() { return schem.palette(); }
    public int paletteIndex(int x, int y, int z) { return schem.at(x, y, z); }
    public String stateAt(int x, int y, int z) { return schem.palette()[schem.at(x, y, z)]; }
    public boolean isLight(int x, int y, int z) { return lights.contains(key(x, y, z)); }
    public boolean isGate(int x, int y, int z) { return gateCells.contains(key(x, y, z)); }

    public Room room(String id) {
        for (Room r : data.rooms) if (r.id.equals(id)) return r;
        throw new IllegalArgumentException("No such room " + id);
    }

    /** Which room (if any) contains this schematic cell; R1/R2 and R3-R8 boxes only meet on handoff planes. */
    public Room roomAt(int x, int y, int z) {
        for (Room r : data.rooms) if (r.contains(x, y, z)) return r;
        return null;
    }
}
