package com.ascensiondungeons.api;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Optional server-thread presentation hook. Register one Fabric entrypoint named ascensiondungeons:raid_entrance. */
public interface RaidEntrance {
    /** Return null to skip decoration. Implementations must clean up partial creation if this throws. */
    Animation begin(Context context);

    record Context(UUID runId, ServerLevel level, Vec3 anchor, String biome, List<UUID> viewers) {
        public Context { viewers = List.copyOf(viewers); }
    }

    interface Animation {
        /** Called once per server tick, with elapsed ticks 1..79. Must not start an encounter. */
        void tick(int elapsed);
        /** Successful encounter start; called immediately before close. */
        void reveal();
        /** Idempotent. Called on completion, refusal, cancellation, failure and run release. */
        void close();
    }
}
