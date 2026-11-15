package com.ascensiondungeons.outbreak;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Chooses where a run's Outbreak features go: rarity chests, outbreak chests, timer chests, strongboxes, curse altars and the
 * extraction portal. Pure: it works on room names and standing cells, so the same seed always gives the same dungeon and the rules
 * (spacing, nothing on the doorstep) are unit tested.
 */
public final class FeaturePlanner {
    public enum Kind { CHEST, OUTBREAK_CHEST, TIMER_CHEST, STRONGBOX, ALTAR, PORTAL }

    /** A room and the cells in it where something can stand. Cells are {x, y, z}. */
    public record Room(String id, List<int[]> cells) {}

    public record Counts(int chests, int outbreakChests, int timerChests, int strongboxes, int altars) {
        public Counts {
            if (chests < 0 || outbreakChests < 0 || timerChests < 0 || strongboxes < 0 || altars < 0) throw new IllegalArgumentException("counts must not be negative");
        }
    }

    /** One placed feature. {@code rarity} is null for altars and the portal. */
    public record Placement(Kind kind, ChestRarity rarity, String room, int[] cell) {}

    /** Features keep this far from each other and (except the portal) from the entry. */
    public static final int SPACING = 7;
    /** The portal sits this far from the entry: close, but never where a player lands. */
    public static final int PORTAL_MIN = 4;

    private FeaturePlanner() {}

    public static List<Placement> plan(List<Room> rooms, int[] entry, String entryRoom, Counts counts, long seed) {
        Random rng = new Random(seed ^ 0x4F5554425245414BL);
        List<Placement> out = new ArrayList<>();
        Room home = null;
        List<Room> usable = new ArrayList<>();
        for (Room r : rooms) {
            if (r.cells().isEmpty()) continue;
            usable.add(r);
            if (r.id().equals(entryRoom)) home = r;
        }
        if (usable.isEmpty()) return out;

        // The portal: the standing cell nearest the entry that is not on top of it.
        Room portalRoom = home != null ? home : usable.get(0);
        int[] portal = null;
        double best = Double.MAX_VALUE;
        for (int[] c : portalRoom.cells()) {
            double d = distSq(c, entry);
            if (d >= PORTAL_MIN * PORTAL_MIN && d < best) { best = d; portal = c; }
        }
        if (portal == null) portal = portalRoom.cells().get(0);
        out.add(new Placement(Kind.PORTAL, null, portalRoom.id(), portal));

        // Strongboxes and timer chests are the prizes: never in the entry room when another room exists.
        List<Room> deep = new ArrayList<>(usable);
        if (deep.size() > 1 && home != null) deep.remove(home);
        place(out, Kind.STRONGBOX, counts.strongboxes(), deep, entry, rng, 3);
        place(out, Kind.TIMER_CHEST, counts.timerChests(), deep, entry, rng, 2);
        place(out, Kind.OUTBREAK_CHEST, counts.outbreakChests(), deep, entry, rng, 0);
        place(out, Kind.ALTAR, counts.altars(), deep, entry, rng, 0);
        place(out, Kind.CHEST, counts.chests(), usable, entry, rng, 0);
        return out;
    }

    private static void place(List<Placement> out, Kind kind, int count, List<Room> rooms, int[] entry, Random rng, int luck) {
        int start = rng.nextInt(rooms.size());
        for (int i = 0; i < count; i++) {
            // Round-robin over the rooms so features spread out, with a few tries per room for a free cell.
            boolean placed = false;
            for (int attempt = 0; attempt < rooms.size() && !placed; attempt++) {
                Room room = rooms.get((start + i + attempt) % rooms.size());
                for (int tries = 0; tries < 24 && !placed; tries++) {
                    int[] c = room.cells().get(rng.nextInt(room.cells().size()));
                    if (distSq(c, entry) < SPACING * SPACING || crowded(out, c)) continue;
                    ChestRarity rarity = kind == Kind.ALTAR ? null
                            : kind == Kind.STRONGBOX ? ChestRarity.EPIC
                            : kind == Kind.TIMER_CHEST ? ChestRarity.RARE
                            : ChestRarity.roll(rng, luck);
                    out.add(new Placement(kind, rarity, room.id(), c));
                    placed = true;
                }
            }
        }
    }

    private static boolean crowded(List<Placement> out, int[] c) {
        for (Placement p : out) if (distSq(p.cell(), c) < SPACING * SPACING) return true;
        return false;
    }

    private static double distSq(int[] a, int[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }
}
