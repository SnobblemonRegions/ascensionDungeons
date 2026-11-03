# The Mourning Court — open sky, low walls (0.0.9)

The arena-wide cavern roof, high boundary shell, overhead vault ribs and outdoor suspended fixture have been removed. The graveyard is now open to the sky, with three-block perimeter masonry and half-slab coping. The side cemetery walls are also three blocks high, with occasional taller lantern posts. The mausoleum, reliquary and bell tower retain their own roofs and architectural features.

The build remains 128 blocks wide × 128 blocks long × 100 blocks high as a schematic envelope. The unoccupied upper space is air. All 54 graves, the central boss marker, dry staging route, blue/purple fixtures, crypts, pumpkins and trees are retained. The 20-block-radius combat court is open from its floor to the top boundary.

Validation passes for marker/chest reachability, the open top boundary, complete low perimeter walls, central overhead clearance, foundation continuity, solid-component support and lantern attachment. The original full-enclosure test was intentionally replaced, since enclosure is no longer the requested design. Partial-block collision/rendering still needs in-game review.

Replace the previous dungeon JAR with `AscensionDungeons-0.0.9-open-graveyard.jar`, keeping one AscensionDungeons version. Restart and create a new `/dungeon type halloween_graveyard` run. Existing generated chunks are not rewritten. For standalone use, import `MourningCourt_OpenSky_128x128x100.schem` with a normal paste including air, so the previous ceiling can be cleared.

No client code or resource pack is required. Boss assignment remains a reserved marker, not an automatically configured encounter. Sky appearance, time and weather follow the existing instance settings; the offline previews use an illustrative twilight background and do not change those settings.

See the original [arena notes](halloween-graveyard-arena.md) for coordinates, layout and materials; this revision supersedes their enclosure and high-vault descriptions.
