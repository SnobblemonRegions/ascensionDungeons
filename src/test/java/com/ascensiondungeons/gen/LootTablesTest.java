package com.ascensiondungeons.gen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The placeholder loot structure must stay wired: every biome has its tables and every internal reference resolves. */
class LootTablesTest {
    private static final Path ROOT = Path.of("src/main/resources/data/ascensiondungeons/loot_table");

    @Test
    void everyBiomeHasChestRaidAndFlavourTables() {
        for (BiomeKind b : BiomeKind.values()) {
            String id = b.name().toLowerCase();
            for (String dir : List.of("chests", "raid", "biome")) {
                assertTrue(Files.exists(ROOT.resolve(dir + "/" + id + ".json")), dir + "/" + id + " missing");
            }
        }
    }

    @Test
    void everyInternalReferenceResolvesToAFile() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ROOT)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                collect(JsonParser.parseString(Files.readString(f)), missing);
            }
        }
        assertTrue(missing.isEmpty(), "dangling loot table references: " + missing);
    }

    private static void collect(JsonElement e, List<String> missing) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("type") && o.get("type").getAsString().equals("minecraft:loot_table")) {
                String v = o.get("value").getAsString();
                if (v.startsWith("ascensiondungeons:") && !Files.exists(ROOT.resolve(v.substring("ascensiondungeons:".length()) + ".json"))) {
                    missing.add(v);
                }
            }
            o.entrySet().forEach(en -> collect(en.getValue(), missing));
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(x -> collect(x, missing));
        }
    }
}
