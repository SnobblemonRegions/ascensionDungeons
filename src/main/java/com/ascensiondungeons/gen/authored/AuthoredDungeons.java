package com.ascensiondungeons.gen.authored;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Which authored dungeons ship with the mod: the ids in {@code dungeons/index.json}. */
public final class AuthoredDungeons {
    private static final List<String> IDS = load();

    private AuthoredDungeons() {}

    public static List<String> ids() { return IDS; }
    public static boolean isAuthored(String type) { return type != null && IDS.contains(type); }

    private static List<String> load() {
        try (InputStream s = AuthoredDungeons.class.getResourceAsStream("/data/ascensiondungeons/dungeons/index.json")) {
            if (s == null) return List.of();
            String[] ids = new Gson().fromJson(new InputStreamReader(s, StandardCharsets.UTF_8), String[].class);
            return ids == null ? List.of() : List.of(ids);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read the authored dungeon index", e);
        }
    }
}
