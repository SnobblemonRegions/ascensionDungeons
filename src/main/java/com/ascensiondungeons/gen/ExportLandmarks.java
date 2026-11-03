package com.ascensiondungeons.gen;

import com.ascensiondungeons.gen.landmark.Composer;
import com.ascensiondungeons.gen.landmark.Landmark;
import com.ascensiondungeons.gen.layout.Layout;
import com.ascensiondungeons.gen.layout.Layouts;
import com.ascensiondungeons.gen.landmark.Voxels;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Writes the landmark modules and the composed district as .schem files for inspection with WorldEdit.
 * Usage: {@code gradlew exportLandmarks -PexportDir=<folder>}. Nothing here runs in the game.
 */
public final class ExportLandmarks {
    private ExportLandmarks() {}

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "outputs/landmarks");
        Files.createDirectories(dir);
        List<String> only = args.length > 1 && !args[1].isEmpty() ? Arrays.asList(args[1].toLowerCase().split(",")) : List.of();
        for (BiomeKind biome : Layouts.available()) {
            if (!only.isEmpty() && !only.contains(biome.name().toLowerCase())) continue;
            Layout layout = Layouts.of(biome);
            for (Landmark lm : layout.landmarks()) {
                write(dir.resolve(lm.id() + "_module.schem"), moduleSchematic(lm.voxels()), lm.id() + " (module)");
            }
            Composer.Result composed = Composer.apply(layout.base(), layout.landmarks());
            write(dir.resolve(biome.name().toLowerCase() + "_district.schem"), composed.schematic(),
                    biome.displayName() + " district with landmarks");
            Files.writeString(dir.resolve(biome.name().toLowerCase() + "_district.markers.json"), markers(layout));
        }
        System.out.println("Wrote landmark schematics to " + dir.toAbsolutePath());
    }

    /** Named spots and landmark boxes in district coordinates, for tools/preview.py to draw on its images. */
    static String markers(Layout layout) {
        List<String> spots = new ArrayList<>();
        new java.util.TreeMap<>(layout.spots()).forEach((name, p) -> spots.add("\"" + name + "\": [" + p[0] + ", " + p[1] + "]"));
        List<String> boxes = new ArrayList<>();
        for (Landmark lm : layout.landmarks()) {
            Voxels v = lm.voxels();
            boxes.add("{\"id\": \"" + lm.id() + "\", \"x\": " + lm.originX() + ", \"z\": " + lm.originZ()
                    + ", \"w\": " + v.sizeX() + ", \"l\": " + v.sizeZ() + "}");
        }
        return "{\"spots\": {" + String.join(", ", spots) + "}, \"landmarks\": [" + String.join(", ", boxes) + "]}";
    }

    /** A module on its own: untouched cells become air so the file pastes cleanly into an empty area. */
    private static SpongeSchematic moduleSchematic(Voxels v) {
        List<String> palette = new ArrayList<>(List.of(Voxels.AIR));
        short[] blocks = new short[v.sizeX() * v.sizeY() * v.sizeZ()];
        for (int y = 0; y < v.sizeY(); y++) {
            for (int z = 0; z < v.sizeZ(); z++) {
                for (int x = 0; x < v.sizeX(); x++) {
                    String s = v.get(x, y, z);
                    if (s == null) continue;
                    int idx = palette.indexOf(s);
                    if (idx < 0) {
                        palette.add(s);
                        idx = palette.size() - 1;
                    }
                    blocks[x + z * v.sizeX() + y * v.sizeX() * v.sizeZ()] = (short) idx;
                }
            }
        }
        return new SpongeSchematic(v.sizeX(), v.sizeY(), v.sizeZ(), palette.toArray(new String[0]), blocks);
    }

    private static void write(Path file, SpongeSchematic s, String name) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            SpongeSchematicWriter.write(s, name, "Ascension Dungeons landmark builder", out);
        }
    }
}
