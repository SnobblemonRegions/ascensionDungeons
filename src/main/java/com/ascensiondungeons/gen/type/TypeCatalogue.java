package com.ascensiondungeons.gen.type;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Server-owned authored content inventory; entries do not imply that draft builds are playable. */
public final class TypeCatalogue {
    public record Landmark(String id,String name,String role) {}
    public record Theme(String id,List<String> rooms,List<Landmark> landmarks,String arena,boolean playable) {}
    private static final List<Theme> THEMES=load();
    private TypeCatalogue() {}
    public static List<Theme> all() { return THEMES; }
    public static Theme get(String id) {
        return THEMES.stream().filter(t -> t.id().equals(id.toLowerCase(Locale.ROOT))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown dungeon type: "+id));
    }
    private static List<Theme> load() {
        var raw=TypeCatalogue.class.getResourceAsStream("/data/ascensiondungeons/type_catalogue.json");
        if(raw==null) throw new IllegalStateException("Missing type catalogue");
        try(var reader=new InputStreamReader(raw,StandardCharsets.UTF_8)) {
            Theme[] entries=new Gson().fromJson(reader,Theme[].class);
            if(entries==null||entries.length!=18) throw new IllegalStateException("Type catalogue requires 18 themes");
            java.util.Set<String> ids=new java.util.HashSet<>();
            for(Theme t:entries) {
                if(!ids.add(t.id())||t.rooms().size()!=5||t.landmarks().size()!=15||t.arena().isBlank()) throw new IllegalStateException("Invalid catalogue: "+t.id());
                if(t.landmarks().stream().map(Landmark::id).distinct().count()!=15) throw new IllegalStateException("Duplicate landmarks: "+t.id());
            }
            return List.of(entries);
        } catch(java.io.IOException e) { throw new IllegalStateException("Cannot load type catalogue",e); }
    }
}
