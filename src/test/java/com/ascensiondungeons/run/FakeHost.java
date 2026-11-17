package com.ascensiondungeons.run;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A scriptable RunHost: nothing touches Minecraft. */
final class FakeHost implements RunHost {
    final Set<UUID> online = new HashSet<>();
    final Set<UUID> inside = new HashSet<>();
    int chunksRequested;
    int chunksNeeded = 0;
    boolean openFails;
    boolean levelGone = true;
    boolean bossTakesOver;
    int opened, released, deleted;

    @Override public void openLevel(Run run) throws Exception { if (openFails) throw new Exception("boom"); opened++; }
    @Override public int requestChunks(Run run, int max) { int n = Math.min(max, Math.max(0, chunksNeeded - chunksRequested)); chunksRequested += n; return n; }
    @Override public boolean chunksReady(Run run) { return chunksRequested >= chunksNeeded; }
    @Override public boolean isOnline(UUID p) { return online.contains(p); }
    @Override public boolean isInside(Run run, UUID p) { return inside.contains(p); }
    @Override public boolean enter(Run run, UUID p) { inside.add(p); return true; }
    @Override public boolean exit(Run run, UUID p) { inside.remove(p); return true; }
    @Override public void releaseLevel(Run run) { released++; }
    @Override public void deleteLevel(Run run) { deleted++; }
    @Override public boolean isLevelGone(Run run) { return levelGone; }
    @Override public void message(UUID p, String t) { }
    @Override public boolean beginBoss(Run run) { return bossTakesOver; }
}
