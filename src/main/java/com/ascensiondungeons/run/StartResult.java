package com.ascensiondungeons.run;

/** The answer to a run request: either the queued run, or why it was refused. */
public record StartResult(Run run, String error) {
    public static StartResult ok(Run run) { return new StartResult(run, null); }
    public static StartResult refused(String error) { return new StartResult(null, error); }
    public boolean ok() { return run != null; }
}
