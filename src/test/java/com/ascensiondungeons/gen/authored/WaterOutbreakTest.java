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

/** The real water dungeon data: its Outbreak settings load, and its features land on sound ground for many seeds. */
class WaterOutbreakTest {
    private final AuthoredLayout water = AuthoredLayout.get("water");

    @Test
    void waterIsAnOutbreakDungeonAndTheOthersAreNot() {
        assertNotNull(water.outbreak());
        assertNull(AuthoredLayout.get("grass").outbreak());
        assertTrue(AuthoredLayout.get("grass").features(1).isEmpty());
    }

    @Test
    void theSettingsBuildValidClockAndSpells() {
        JsonObject c = Jx.o(water.outbreak(), "clock");
        assertDoesNotThrow(() -> new OutbreakClock.Params(Jx.i(c, "secondsPerStage", 0) * 20, Jx.i(c, "maxStage", -1), Jx.d(c, "spawnStep", -1),
                Jx.d(c, "eliteStep", -1), Jx.d(c, "eliteCap", -1), Jx.i(c, "stagesPerTierStep", 0)));
        Set<String> ids = new HashSet<>();
        for (JsonElement e : Jx.a(water.outbreak(), "spells")) {
            JsonObject s = e.getAsJsonObject();
            Spell spell = new Spell(Jx.s(s, "id", null), Jx.s(s, "name", null), (int) (Jx.d(s, "cooldownSeconds", 0) * 20), (int) (Jx.d(s, "warnSeconds", 0) * 20),
                    Jx.d(s, "range", 0), Jx.d(s, "radius", 0), Jx.d(s, "damage", 0), Spell.Effect.valueOf(Jx.s(s, "effect", "none").toUpperCase()), Jx.d(s, "strength", 0));
            assertTrue(ids.add(spell.id()));
        }
        // every spell a spawn or the elite list names exists
        for (JsonElement e : Jx.a(Jx.o(water.outbreak(), "waves"), "spawns")) {
            String spell = Jx.s(e.getAsJsonObject(), "spell", null);
            if (spell != null) assertTrue(ids.contains(spell), spell);
        }
        for (JsonElement e : Jx.a(Jx.o(water.outbreak(), "elite"), "spells")) assertTrue(ids.contains(e.getAsString()), e.getAsString());
    }

    @Test
    void everyCheckpointAndWavePoolExistsInTheLayout() {
        Set<String> mechanics = new HashSet<>();
        for (JsonElement e : water.data().mechanics) mechanics.add(Jx.s(e.getAsJsonObject(), "id", ""));
        for (JsonElement e : Jx.a(water.outbreak(), "checkpoints")) assertTrue(mechanics.contains(e.getAsString()), e.getAsString());
        JsonObject pools = Jx.o(water.data().pokemon, "pools");
        for (JsonElement e : Jx.a(Jx.o(water.outbreak(), "waves"), "spawns")) assertTrue(pools.has(Jx.s(e.getAsJsonObject(), "pokemon", "?")));
        assertTrue(pools.has(Jx.s(Jx.o(water.outbreak(), "guardians"), "pokemon", "?")));
    }

    @Test
    void featuresArePlacedInFullOnOpenGroundForManySeeds() {
        JsonObject f = Jx.o(water.outbreak(), "features");
        int wanted = 1 + Jx.i(f, "chests", 0) + Jx.i(f, "outbreakChests", 0) + Jx.i(f, "timerChests", 0) + Jx.i(f, "strongboxes", 0) + Jx.i(f, "altars", 0);
        for (long seed = 1; seed <= 200; seed++) {
            List<FeaturePlanner.Placement> plan = water.features(seed);
            assertEquals(wanted, plan.size(), "seed " + seed);
            for (FeaturePlanner.Placement p : plan) {
                int[] c = p.cell();
                assertEquals("minecraft:air", water.stateAt(c[0], c[1], c[2]), "seed " + seed + " " + p.kind() + " feet");
                assertNotEquals("minecraft:air", water.stateAt(c[0], c[1] - 1, c[2]), "seed " + seed + " " + p.kind() + " floor");
                assertNotEquals("R6", p.room(), "nothing in the boss's room");
                AuthoredLayout.Room room = water.roomAt(c[0], c[1], c[2]);
                assertNotNull(room);
            }
        }
    }

    @Test
    void theSamePlanComesBackForTheSameSeed() {
        assertSame(water.features(77), water.features(77));
    }
}
