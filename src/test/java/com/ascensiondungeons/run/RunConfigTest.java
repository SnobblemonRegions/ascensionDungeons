package com.ascensiondungeons.run;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensiondungeons.gen.BiomeKind;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class RunConfigTest {
    @Test
    void defaultsValidate() {
        assertDoesNotThrow(() -> new RunConfig().validate());
    }

    @Test
    void theFileRoundTripsAndAMissingKeyKeepsItsDefault() {
        Gson gson = new Gson();
        RunConfig partial = gson.fromJson("{\"durationTicks\": 1234}", RunConfig.class);
        assertEquals(1234, partial.durationTicks);
        assertEquals(new RunConfig().cinderforgeDurationTicks, partial.cinderforgeDurationTicks);
        assertNotNull(partial.spawns.get("ashen_caldera"));
        RunConfig again = gson.fromJson(gson.toJson(new RunConfig()), RunConfig.class);
        assertEquals(new RunConfig().spawns.get("verdant_canopy").size(), again.spawns.get("verdant_canopy").size());
        assertDoesNotThrow(again::validate);
    }

    @Test
    void everyBiomeHasASpawnTableAndABossPool() {
        RunConfig cfg = new RunConfig();
        for (BiomeKind b : BiomeKind.values()) {
            String key = b.name().toLowerCase(java.util.Locale.ROOT);
            assertFalse(cfg.spawns.getOrDefault(key, java.util.List.of()).isEmpty(), "spawns for " + key);
            assertFalse(cfg.raidBosses.getOrDefault(key, java.util.List.of()).isEmpty(), "raid bosses for " + key);
        }
    }

    @Test
    void badValuesAreRejected() {
        RunConfig c = new RunConfig();
        c.maxConcurrentRuns = 0;
        assertThrows(IllegalStateException.class, c::validate);
        c = new RunConfig();
        c.spawnMaxDistance = c.spawnMinDistance - 1;
        assertThrows(IllegalStateException.class, c::validate);
        c = new RunConfig();
        c.raidBossLevel = 101;
        assertThrows(IllegalStateException.class, c::validate);
    }

    @Test
    void aNullSpawnMapFromTheFileIsRepaired() {
        RunConfig c = new Gson().fromJson("{\"spawns\": null, \"raidBosses\": null}", RunConfig.class);
        c.validate();
        assertNotNull(c.spawns);
        assertNotNull(c.raidBosses);
    }
}
