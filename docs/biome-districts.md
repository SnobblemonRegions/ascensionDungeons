> **SUPERSEDED IN PART (2026-10-11).** The project context is now `L:\Codex\CobbleRaids GUIs\docs\claude-terminal-handoff.md`.
> It describes the accepted v3 base as a roofed 256 x 96 x 256 *district module* (main habitat, loot room, raid arena,
> spring room, entry room, 9-wide paths, entrances to adjacent districts) with 8 biomes x 4 landmarks. The "single dome
> with three biomes and a ruin ring" in sections 1-3 below was my coarse reading of the schematic and is wrong. The
> `gen/` and `DungeonChunkGenerator` code built from it is a stopgap test terrain, not the target design.

# Biome districts - design spec (prep for procedural generation)

Source: `enclosed_biome_district_v3_underground.schem` (Sponge v3, DataVersion 3955 = 1.21.1, 256 x 96 x 256,
37 palette entries, single-byte block data, author "Dungeon Design Mockup"). v1/v2 are earlier drafts.
Everything under "Measured" was read from the file. Everything under "Proposed" is a design choice for you to
approve or change; nothing here is built yet.

## 1. Measured: what the mockup actually is

One sealed dome cavern, not a set of rooms.

| Property | Value |
|---|---|
| Footprint | circle, centre (127.5, 127.5), cavern radius about 110 blocks (rock wall from about r=110 to r=118) |
| Floor | flat at y=5 (floor blocks at y=4); about 40,000 columns |
| Ceiling | smooth dome: y about 70 at centre, 64 at r=40, 53 at r=80, 27 at the rim; solid stone above y=85 |
| Block mix | 4.33M air, 1.43M stone; the rest is decoration (about 10% of non-air) |
| Lighting | 1,446 `light` blocks in a grid at y=7 (a build aid, remove in generation); 289 `sea_lantern` from y=4 to y=73 |
| Loot | exactly one chest, at (48, 6, 87) |
| Raid space | none marked |
| Water | three small ponds, about 16 x 16, at (88-103, 128-143), (136-151, 136-151), (184-199, 168-183) |
| Lava | 81 blocks at x 236-255, y=4, z 118-138, plus 2 magma blocks |

Dominant block per region (8 x 8 columns, full height). `V` verdant, `F` volcanic, `R` stone ruin:

- Outer ring (r about 85-110): stone bricks, mossy cobblestone, mossy stone bricks, deepslate and tuff bricks. A ruined wall belt with 1 to 5-cell-thick wall segments up to y=55.
- Centre and most of the interior: verdant. Grass floor, moss block, podzol, oak trees (canopy clusters at roughly (64-119, 120-159), (136-183, 136-159), (96-119, 88-119)), flowering azalea, azure bluet, allium, cornflower, fern, short grass.
- North-east and east: volcanic. Netherrack, blackstone, basalt, polished blackstone bricks (chiseled and cracked variants), deepslate bricks. Main mass at x 168-223, z 48-103 and up to y=23; a second pocket at x 232-255, z 104-151 (the lava pool).
- Mossy cobblestone clusters in the ring at (24-47, 184-207), (48-79, 16-55), (232-247, 72-119), (136-167, 232-247): these look like collapsed towers.

So the file really contains three biomes plus ponds: **Verdant Canopy**, **Volcanic Threshold**, and a **Ruin Ring**
that wraps both. Names come from the schematic's metadata ("Verdant Canopy / Volcanic Threshold").
It does not contain separate fungal, frozen, flooded or other biomes; I will not invent them in the data model
unless you ask for more.

## 2. Proposed: generation model

The dome is one district. A run is one district. The seed decides the district's shape and contents, not the biome list.

```
seed -> DistrictPlan (pure Java, no Minecraft)
        - radius R in [90, 120]               (cavern, not the 1024 border)
        - ceiling profile: height(r) = H0 - k * (r/R)^p
        - biome regions: 3 sectors + a ring, from a seeded warped-Voronoi over the disc
        - landmark sites (deterministic, minimum spacing)
        - loot sites, raid sites
-> DungeonChunkGenerator (Minecraft) asks the plan for the biome, floor/ceiling height and
   features at each (x, z); everything is a pure function of (seed, x, z).
```

Rules that keep it testable and cheap:
- Every query is a pure function of `(seed, x, z)`, so any chunk can be generated in any order, and the same seed always gives the same dungeon.
- No decoration is stored. Landmarks, trees and loot are re-derived per chunk from a hash of `(seed, chunkX, chunkZ, featureId)`.
- All sites (loot, raid, landmark) come from one placement pass with a minimum-distance rule and a reachability check, so no raid arena sits in a wall and no chest is unreachable.

## 3. Proposed: per-biome spec

Block lists are taken from the mockup palette, so the first generator version matches what you drew.

### Verdant Canopy (about 55% of the disc, centre and west/south)
- **Terrain:** flat to gently rolling floor at y=5..8 (the mockup is flat; roll is optional, 0 to 3 blocks). Grass block floor, coarse dirt and podzol patches, moss block under tree clusters, small clay/gravel beds at ponds.
- **Vegetation:** oak trees in clusters (trunk 6-10, leaves persistent). Understory: short grass, fern, flowering azalea, azure bluet, allium, cornflower. Density about 1 tree per 60 blocks inside clusters, none outside.
- **Landmarks:** ponds (16 x 16, 1-3 blocks deep, clay rim); grown-over stone-brick ruin pieces (single walls, arches); one canopy "great tree" per run.
- **Loot sites:** 4-6 Lootr chests: inside ruin pieces and under the great tree. Never in open grass.
- **Raid space:** 1 arena, open clearing at least 24 x 24, flat, no trees, near but not at the centre.

### Volcanic Threshold (about 20%, north-east and east)
- **Terrain:** raised blackstone and basalt shelf (floor y=5 up to about y=28 in the mockup), netherrack slopes, a lava pool at the far end (81 lava blocks in the mockup; keep it off every walking route and fence it with magma).
- **Vegetation:** none. Lighting comes from the lava and from sea lanterns embedded in the ceiling (the mockup already does this).
- **Landmarks:** polished blackstone brick structure (chiseled and cracked pieces) on the shelf, deepslate-brick walls; one "threshold gate" between Verdant and Volcanic.
- **Loot sites:** 2-3 chests inside the blackstone structure; higher-tier loot table than Verdant.
- **Raid space:** 1 arena on the blackstone shelf, at least 24 x 24, with an exit toward Verdant. This is the natural boss encounter.

### Ruin Ring (outer annulus, about 25%, r about 85-110)
- **Terrain:** the dome wall: stone-brick, mossy stone-brick, mossy cobblestone, tuff-brick and deepslate-brick belts; ceiling lowers to meet the floor.
- **Vegetation:** moss and the occasional vine/azalea on bricks.
- **Landmarks:** collapsed towers (the mossy cobblestone clusters, 4 per run), wall breaks that connect the ring to the interior (this is the usual way in).
- **Loot sites:** 2-4 chests in the towers.
- **Raid space:** none by default (cramped). Optional: one tower base.

### Ponds
Three per run, placed in Verdant only, never within 20 blocks of a raid arena or chest.

## 4. Proposed: data the generator needs (all server-side config, one file)

```
district:  radius(min,max), floorY=5, ceilingCentreY, ceilingExponent
biome:     id, weight, floorBlocks[], accentBlocks[], treeDensity, flora[], lightBlock
sites:     loot { biome, minSpacing, count(min,max), lootTable }
           raid { biome, clearing(w,l), count }
           landmark { biome, structureId, count }
raid slot: position (x,y,z), facing, clearing size
```

The raid slots are what CobbleRaids needs: an explicit `ServerLevel` and `Vec3`, up to four players per boss encounter.

## 5. Gaps and open questions (need your call)

1. **Raid arena size.** CobbleRaids does not document a footprint; I assumed 24 x 24 clear floor. What does it actually need?
2. **Loot tables.** Which vanilla or custom tables for Verdant and Volcanic? I used `simple_dungeon` for the test chest only.
3. **Schematic structures.** Should landmarks be hand-built schematics pasted by the generator (best looks), or generated by code from the palette (best variety)? The mockup suggests hand-built, and the generator would then pick, rotate and place pieces.
4. **Scale.** The mockup cavern is about 220 blocks across. That is about 16 x 16 chunks of floor but 4-5 chunks of height, so it is cheap in chunk count, though the volume is big. Is this the size you want, or larger?
5. **Mobs.** Do biomes have their own Cobblemon spawn tables (for example fire types in Volcanic)? The spawn scheduler is not written yet.
6. **More biomes.** The file has only two named biomes plus the ring. If you want more (water, frost, fungal), they should be added to the mockup first.

## 6. Proposed build order

1. `DistrictPlan` + `BiomeSpec` in pure Java with unit tests (determinism per seed, spacing, reachability, no site in rock).
2. `DungeonChunkGenerator`: carves the dome and fills floor/biome blocks from the plan, no features yet. Check in game with WorldEdit.
3. Features: trees, flora, ponds, lava, landmarks.
4. Sites: Lootr chests and raid-slot markers, then wire to CobbleRaids.
5. Performance check with the benchmark harness: cost per chunk and per level, then re-measure the run cap.

## 7. Status: layout code built (2026-10-10)

`com.ascensiondungeons.gen` is pure Java with no Minecraft types: `DistrictPlan.create(seed, DistrictParams)` gives the
radius, `biomeAt(x, z)`, `ceilingY(x, z)`, the entry point and the list of `Site`s. 28 tests include 40,000-seed sweeps
(every site placed, inside its biome, no overlaps, ponds away from chests/arenas, arrival area clear, 24 x 24 arenas).

Changes from the proposal, forced by fitting everything into the smallest cavern (radius 90):
- Ponds 12 x 12 (not 16 x 16); pond-to-chest/arena gap 14.
- Towers 12 x 12 footprint; the ring starts at about 78% of the radius.
- The Volcanic threshold gate only needs its centre in Volcanic (it sits on the border).
- A seed whose layout cannot fit every site falls back to a derived layout (still deterministic per seed).
- Loot sites are placed independently for now, not inside landmarks yet. That needs the hand-built schematic pieces.

Decisions recorded from the user: arenas are 24 x 24; loot tables come later; landmarks are hand-built schematics;
cavern size as in the mockup; biomes get their own Cobblemon spawn tables.
