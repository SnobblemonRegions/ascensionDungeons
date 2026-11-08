package com.ascensiondungeons.run;

/** Lifecycle of a run. QUEUED -> PREPARING -> ACTIVE -> ENDING -> CLOSED, or ENDING -> CLEANUP_FAILED when the level will not delete. */
public enum RunState { QUEUED, PREPARING, ACTIVE, ENDING, CLOSED, CLEANUP_FAILED }
