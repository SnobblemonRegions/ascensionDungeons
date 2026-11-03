package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import com.ascensiondungeons.gen.BiomeKind;
import com.ascensiondungeons.gen.Template;
import com.ascensiondungeons.gen.landmark.Composer;
import com.ascensiondungeons.gen.layout.Layout;
import com.ascensiondungeons.gen.layout.Layouts;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The district templates of each biome: an authored layout (see {@code Layouts}) composed with its landmark modules. A
 * template is built and analysed once (about a second) and shared by every run; it is immutable afterwards.
 */
public final class DungeonTemplates {
    private static final Map<BiomeKind, Template> LOADED = new EnumMap<>(BiomeKind.class);

    private DungeonTemplates() {}

    /** Biomes that have a district template. */
    public static List<BiomeKind> available() { return Layouts.available(); }

    public static synchronized Template get(BiomeKind biome) {
        Template t = LOADED.get(biome);
        if (t == null) {
            long start = System.nanoTime();
            Layout layout = Layouts.of(biome);
            t = new Template(Composer.apply(layout.base(), layout.landmarks()).schematic());
            LOADED.put(biome, t);
            AscensionDungeonsLog.LOG.info("Loaded {} district template in {} ms ({} outside cells)", biome.displayName(),
                    (System.nanoTime() - start) / 1_000_000, t.exteriorCount());
        }
        return t;
    }

    public static void preload() {
        for (BiomeKind b : available()) get(b);
    }
}
