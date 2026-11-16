package com.ascensiondungeons.gen.authored;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensiondungeons.outbreak.FeaturePlanner;
import com.ascensiondungeons.outbreak.OutbreakClock;
import com.ascensiondungeons.outbreak.Spell;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The real dungeon data: every type dungeon is an Outbreak dive whose settings load and whose features land on sound ground. */
class WaterOutbreakTest {
    static final List<String> TYPES = List.of("normal", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost",
            "dragon", "dark", "steel", "fairy");

    @Test
    void everyTypeDungeonIsADiveAndNothingElseIs() {
        for (String id : AuthoredDungeons.ids()) {
            boolean type = TYPES.contains(id);
            assertEquals(type, AuthoredLayout.get(id).outbreak() != null, id);
            if (!type) assertTrue(AuthoredLayout.get(id).features(1).isEmpty(), id);
        }
        for (String id : TYPES) assertTrue(AuthoredDungeons.ids().contains(id), id + " is registered");
    }

    @Test
    void theSettingsBuildValidClocksAndSpells() {
        for (String id : TYPES) {
            JsonObject o = AuthoredLayout.get(id).outbreak();
            JsonObject c = Jx.o(o, "clock");
            assertDoesNotThrow(() -> new OutbreakClock.Params(Jx.i(c, "secondsPerStage", 0) * 20, Jx.i(c, "maxStage", -1), Jx.d(c, "spawnStep", -1),
                    Jx.d(c, "eliteStep", -1), Jx.d(c, "eliteCap", -1), Jx.i(c, "stagesPerTierStep", 0)), id);
            Set<String> ids = new HashSet<>();
            for (JsonElement e : Jx.a(o, "spells")) {
                JsonObject s = e.getAsJsonObject();
                Spell spell = new Spell(Jx.s(s, "id", null), Jx.s(s, "name", null), (int) (Jx.d(s, "cooldownSeconds", 0) * 20), (int) (Jx.d(s, "warnSeconds", 0) * 20),
                        Jx.d(s, "range", 0), Jx.d(s, "radius", 0), Jx.d(s, "damage", 0), Spell.Effect.valueOf(Jx.s(s, "effect", "none").toUpperCase()), Jx.d(s, "strength", 0));
                assertTrue(ids.add(spell.id()), id + " " + spell.id());
            }
            assertEquals(3, ids.size(), id + " has three spells");
            // every spell a spawn, the elite list or the gatekeeper names exists
            for (JsonElement e : Jx.a(Jx.o(o, "waves"), "spawns")) {
                String spell = Jx.s(e.getAsJsonObject(), "spell", null);
                if (spell != null) assertTrue(ids.contains(spell), id + " " + spell);
            }
            for (JsonElement e : Jx.a(Jx.o(o, "elite"), "spells")) assertTrue(ids.contains(e.getAsString()), id + " " + e.getAsString());
            for (JsonElement e : Jx.a(Jx.o(o, "gatekeeper"), "spells")) assertTrue(ids.contains(e.getAsString()), id + " " + e.getAsString());
            int toll = Jx.i(Jx.o(o, "portal"), "tollPercent", -1);
            assertEquals(35, toll, id + " toll");
            assertTrue(Jx.i(o, "durationMinutes", 0) >= 30, id + " is a long run");
        }
    }

    @Test
    void everyCheckpointAndPoolExistsInTheLayout() {
        for (String id : TYPES) {
            AuthoredLayout layout = AuthoredLayout.get(id);
            JsonObject o = layout.outbreak();
            Set<String> mechanics = new HashSet<>();
            for (JsonElement e : layout.data().mechanics) mechanics.add(Jx.s(e.getAsJsonObject(), "id", ""));
            assertTrue(Jx.a(o, "checkpoints").size() >= 2, id + " has checkpoints");
            for (JsonElement e : Jx.a(o, "checkpoints")) assertTrue(mechanics.contains(e.getAsString()), id + " " + e.getAsString());
            JsonObject pools = Jx.o(layout.data().pokemon, "pools");
            for (JsonElement e : Jx.a(Jx.o(o, "waves"), "spawns")) assertTrue(pools.has(Jx.s(e.getAsJsonObject(), "pokemon", "?")), id);
            assertTrue(pools.has(Jx.s(Jx.o(o, "guardians"), "pokemon", "?")), id);
            assertTrue(pools.has(Jx.s(Jx.o(o, "gatekeeper"), "pokemon", "?")), id);
        }
    }

    @Test
    void featuresArePlacedInFullOnOpenGroundForManySeeds() {
        for (String id : TYPES) {
            AuthoredLayout layout = AuthoredLayout.get(id);
            JsonObject f = Jx.o(layout.outbreak(), "features");
            int wanted = 1 + Jx.i(f, "chests", 0) + Jx.i(f, "outbreakChests", 0) + Jx.i(f, "timerChests", 0) + Jx.i(f, "strongboxes", 0) + Jx.i(f, "altars", 0);
            Set<String> bossRooms = new HashSet<>();
            for (JsonElement b : layout.data().bosses) bossRooms.add(Jx.s(b.getAsJsonObject(), "room", ""));
            for (long seed = 1; seed <= 60; seed++) {
                List<FeaturePlanner.Placement> plan = layout.features(seed);
                assertEquals(wanted, plan.size(), id + " seed " + seed);
                for (FeaturePlanner.Placement p : plan) {
                    int[] c = p.cell();
                    assertEquals("minecraft:air", layout.stateAt(c[0], c[1], c[2]), id + " seed " + seed + " " + p.kind() + " feet");
                    assertNotEquals("minecraft:air", layout.stateAt(c[0], c[1] - 1, c[2]), id + " seed " + seed + " " + p.kind() + " floor");
                    assertFalse(bossRooms.contains(p.room()), id + ": nothing in the boss's room");
                }
            }
        }
    }

    @Test
    void theSamePlanComesBackForTheSameSeed() {
        AuthoredLayout water = AuthoredLayout.get("water");
        assertSame(water.features(77), water.features(77));
    }
}
