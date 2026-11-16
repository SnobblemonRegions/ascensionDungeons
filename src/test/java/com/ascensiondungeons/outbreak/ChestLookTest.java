package com.ascensiondungeons.outbreak;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChestLookTest {
    private static Set<Integer> everyModelTheCodeCanShow() {
        Set<Integer> ids = new HashSet<>();
        for (ChestRarity r : ChestRarity.values()) {
            ids.add(ChestLook.model(FeaturePlanner.Kind.CHEST, r, false));
            ids.add(ChestLook.model(FeaturePlanner.Kind.OUTBREAK_CHEST, r, false));
        }
        ids.add(ChestLook.model(FeaturePlanner.Kind.TIMER_CHEST, ChestRarity.RARE, true));
        ids.add(ChestLook.model(FeaturePlanner.Kind.TIMER_CHEST, ChestRarity.RARE, false));
        ids.add(ChestLook.model(FeaturePlanner.Kind.STRONGBOX, ChestRarity.EPIC, false));
        return ids;
    }

    @Test
    void everyChestStateHasItsOwnModel() {
        assertEquals(11, everyModelTheCodeCanShow().size());
        assertEquals(0, ChestLook.model(FeaturePlanner.Kind.ALTAR, null, false));
        assertEquals(0, ChestLook.model(FeaturePlanner.Kind.PORTAL, null, false));
        assertNotEquals(ChestLook.model(FeaturePlanner.Kind.TIMER_CHEST, ChestRarity.RARE, true), ChestLook.model(FeaturePlanner.Kind.TIMER_CHEST, ChestRarity.RARE, false));
    }

    /** The committed resource pack maps exactly the ids the code shows, and every model and texture it names exists. */
    @Test
    void theResourcePackMatchesTheCode() throws Exception {
        Path pack = Path.of("resourcepack");
        Path overrides = pack.resolve("assets/minecraft/models/item/popped_chorus_fruit.json");
        assertTrue(Files.exists(overrides), "run: python tools/outbreak/make_chest_pack.py resourcepack");
        JsonObject root = JsonParser.parseString(Files.readString(overrides)).getAsJsonObject();
        Set<Integer> inPack = new HashSet<>();
        for (JsonElement e : root.getAsJsonArray("overrides")) {
            JsonObject o = e.getAsJsonObject();
            inPack.add(o.getAsJsonObject("predicate").get("custom_model_data").getAsInt());
            String model = o.get("model").getAsString();
            assertTrue(model.startsWith("ascensiondungeons:chest/"), model);
            String name = model.substring("ascensiondungeons:chest/".length());
            Path modelFile = pack.resolve("assets/ascensiondungeons/models/chest/" + name + ".json");
            assertTrue(Files.exists(modelFile), modelFile.toString());
            JsonObject m = JsonParser.parseString(Files.readString(modelFile)).getAsJsonObject();
            assertTrue(m.getAsJsonArray("elements").size() >= 3, name + " has geometry");
            String texture = m.getAsJsonObject("textures").get("0").getAsString();
            assertTrue(Files.exists(pack.resolve("assets/ascensiondungeons/textures/" + texture.substring("ascensiondungeons:".length()) + ".png")), texture);
        }
        assertEquals(everyModelTheCodeCanShow(), inPack);
        assertTrue(ChestLook.DISPLAY_ITEM.endsWith("popped_chorus_fruit"));
    }
}
