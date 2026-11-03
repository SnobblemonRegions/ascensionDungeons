package com.ascensiondungeons.mc;

import java.util.UUID;

/** What the mod core needs from the raid-arena feature, so it never touches CobbleRaids classes directly. */
public interface ArenaDirector {
    /** Called every server tick with the server tick count. */
    void tick(long now);

    /** The run's level is being released: end any raid still going in it. */
    void runReleased(UUID runId);
}
