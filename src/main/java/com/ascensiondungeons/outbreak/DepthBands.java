package com.ascensiondungeons.outbreak;

import java.util.Map;

/**
 * How deep a room is, in the terms a player feels: the Depths are cut into three bands by how far a room is from the entrance,
 * and the deeper the band the better the loot and the fiercer the enemies. The hand-built dungeon above is band 0 too.
 * Pure: no Minecraft.
 */
public final class DepthBands {
    private DepthBands() {}

    /** Bands: 0 the outer rooms, 1 the middle, 2 the deep. */
    public static final int OUTER = 0, MIDDLE = 1, DEEP = 2;

    /**
     * The band of a room at this depth in a maze whose farthest room is at {@code maxDepth}: the first third of the way is
     * outer, the last third deep. A maze too shallow to split (two rooms) is all outer.
     */
    public static int band(int depth, int maxDepth) {
        if (maxDepth <= 1 || depth <= 0) return OUTER;
        int b = depth * 3 / (maxDepth + 1);
        return Math.max(OUTER, Math.min(DEEP, b));
    }

    /** How much luckier chests are in this band: each step of luck shifts the rarity roll up. */
    public static int chestLuck(int band) { return band <= 0 ? 0 : band == 1 ? 1 : 3; }

    /** How many times a room of this band is offered when chests are spread around: deep rooms get the most. */
    public static int chestWeight(int band) { return band + 1; }

    /** Extra enemies in a wave that falls on a player in this band. */
    public static int extraEnemies(int band) { return Math.max(0, band); }

    /** Added to the chance that an enemy of a wave in this band is an elite. */
    public static double eliteBonus(int band) { return band <= 0 ? 0 : band == 1 ? 0.05 : 0.12; }

    /** Added to the level of enemies that spawn in this band. */
    public static int levelBonus(int band) { return band <= 0 ? 0 : band == 1 ? 3 : 7; }

    /** What each type calls its three bands. Anything else gets the plain names. */
    private static final Map<String, String[]> NAMES = Map.ofEntries(
            Map.entry("normal", new String[] {"The Cellar Rows", "The Old Warrens", "The Forgotten Hearth"}),
            Map.entry("water", new String[] {"The Sunken Halls", "The Drowned Galleries", "The Abyssal Cistern"}),
            Map.entry("grass", new String[] {"The Root Walks", "The Overgrown Vaults", "The Heartwood"}),
            Map.entry("electric", new String[] {"The Conduit Halls", "The Arc Galleries", "The Storm Core"}),
            Map.entry("ice", new String[] {"The Rime Halls", "The Frozen Galleries", "The Glacier Heart"}),
            Map.entry("fighting", new String[] {"The Training Halls", "The Proving Grounds", "The Inner Sanctum"}),
            Map.entry("poison", new String[] {"The Seeping Halls", "The Blighted Galleries", "The Venom Wells"}),
            Map.entry("ground", new String[] {"The Dug Halls", "The Fault Galleries", "The Bedrock Hollow"}),
            Map.entry("flying", new String[] {"The Windward Halls", "The Gale Galleries", "The Eye of the Storm"}),
            Map.entry("psychic", new String[] {"The Quiet Halls", "The Dreaming Galleries", "The Mind's Eye"}),
            Map.entry("bug", new String[] {"The Burrows", "The Brood Galleries", "The Queen's Chamber"}),
            Map.entry("rock", new String[] {"The Quarry Halls", "The Fossil Galleries", "The Stone Heart"}),
            Map.entry("ghost", new String[] {"The Whispering Halls", "The Ossuary", "The Veil"}),
            Map.entry("dragon", new String[] {"The Scaled Halls", "The Wyrm Galleries", "The Hoard"}),
            Map.entry("dark", new String[] {"The Dim Halls", "The Shadow Galleries", "The Lightless Deep"}),
            Map.entry("steel", new String[] {"The Forge Halls", "The Gear Galleries", "The Iron Core"}),
            Map.entry("fairy", new String[] {"The Petal Halls", "The Glimmer Galleries", "The Moonwell"}),
            Map.entry("fire", new String[] {"The Ember Halls", "The Cinder Galleries", "The Caldera"}));

    private static final String[] PLAIN = {"The Outer Depths", "The Middle Depths", "The Deep"};

    public static String name(String type, int band) {
        String[] names = NAMES.getOrDefault(type, PLAIN);
        return names[Math.max(0, Math.min(2, band))];
    }

    /** "Outer", "Middle" or "Deep", for the line under a band's name. */
    public static String label(int band) { return band <= 0 ? "Outer" : band == 1 ? "Middle" : "Deep"; }
}
