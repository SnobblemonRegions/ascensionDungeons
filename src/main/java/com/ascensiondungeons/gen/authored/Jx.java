package com.ascensiondungeons.gen.authored;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Small, forgiving readers for the mechanics JSON, so every dungeon file can omit anything that has a sensible default. */
public final class Jx {
    private Jx() {}

    public static String s(JsonObject o, String k, String def) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def; }
    public static int i(JsonObject o, String k, int def) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def; }
    public static double d(JsonObject o, String k, double def) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def; }
    public static boolean b(JsonObject o, String k, boolean def) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def; }
    public static JsonObject o(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null; }
    public static JsonArray a(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }

    public static int[] ints(JsonElement e) {
        if (e == null || !e.isJsonArray()) return null;
        JsonArray a = e.getAsJsonArray();
        int[] out = new int[a.size()];
        for (int n = 0; n < out.length; n++) out[n] = a.get(n).getAsInt();
        return out;
    }

    public static double[] doubles(JsonElement e) {
        if (e == null || !e.isJsonArray()) return null;
        JsonArray a = e.getAsJsonArray();
        double[] out = new double[a.size()];
        for (int n = 0; n < out.length; n++) out[n] = a.get(n).getAsDouble();
        return out;
    }

    public static int[] ints(JsonObject o, String k) { return o == null ? null : ints(o.get(k)); }
    public static double[] doubles(JsonObject o, String k) { return o == null ? null : doubles(o.get(k)); }
}
