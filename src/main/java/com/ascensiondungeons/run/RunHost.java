package com.ascensiondungeons.run;

import java.util.UUID;

/** Everything the run state machine needs from the world, so it can be tested without Minecraft. All calls are on the server thread. */
public interface RunHost {
    /** Creates the run's level. Chunk generation is requested afterwards, a few per tick. */
    void openLevel(Run run) throws Exception;
    /** Asks for up to {@code max} more chunks of the level to be generated; returns how many were requested. */
    int requestChunks(Run run, int max);
    boolean chunksReady(Run run);
    boolean isOnline(UUID player);
    /** Whether the player is currently in the run's level. */
    boolean isInside(Run run, UUID player);
    /** Moves the player into the level. False if it could not be done this tick. */
    boolean enter(Run run, UUID player);
    /** Sends the player back to where they came from. True when they are no longer in the level. */
    boolean exit(Run run, UUID player);
    /** Stops anything holding the level open (forced chunks, players) so it can be deleted. */
    void releaseLevel(Run run);
    void deleteLevel(Run run);
    /** True once the level is fully unloaded and gone. */
    boolean isLevelGone(Run run);
    void message(UUID player, String text);
    /** Called when the clock runs out. Return true if a final encounter took over (the run continues in its boss phase). */
    default boolean beginBoss(Run run) { return false; }
}
