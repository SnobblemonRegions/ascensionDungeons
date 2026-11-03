package com.ascensiondungeons.mc;

import com.ascensiondungeons.AscensionDungeonsLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Removes run level folders left behind by a crash. The spike showed Fantasy does not clean them on the next boot, and
 * other mods may use Fantasy too, so only folders carrying our marker file are touched.
 */
public final class OrphanSweeper {
    private OrphanSweeper() {}

    public static int sweep(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve("fantasy");
        if (!Files.isDirectory(root)) return 0;
        int removed = 0;
        try (Stream<Path> children = Files.list(root)) {
            for (Path dir : (Iterable<Path>) children::iterator) {
                if (!Files.isRegularFile(dir.resolve(FantasyRunHost.MARKER))) continue;
                if (deleteTree(dir)) removed++;
            }
        } catch (IOException e) {
            AscensionDungeonsLog.LOG.warn("Orphan sweep could not list {}", root, e);
        }
        if (removed > 0) AscensionDungeonsLog.LOG.info("Removed {} orphaned run folder(s) from a previous session", removed);
        return removed;
    }

    private static boolean deleteTree(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
            return true;
        } catch (IOException e) {
            AscensionDungeonsLog.LOG.warn("Could not remove orphaned run folder {}", dir, e);
            return false;
        }
    }
}
