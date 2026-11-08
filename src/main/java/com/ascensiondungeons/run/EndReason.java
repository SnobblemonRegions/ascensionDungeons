package com.ascensiondungeons.run;

/** Why a run ended. */
public enum EndReason {
    /** The party won (boss defeated, dungeon cleared). */
    COMPLETED,
    /** The clock ran out. */
    TIMEOUT,
    /** Everyone left, or the encounter was lost or refused. */
    ABANDONED,
    /** An operator ended it with {@code /dungeon end}. */
    ADMIN,
    /** The level could not be opened. */
    FAILED,
    /** The server stopped. */
    SHUTDOWN
}
