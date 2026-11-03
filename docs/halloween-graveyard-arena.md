# The Mourning Court — Halloween graveyard boss arena

Designed against the current AscensionDungeons Python authoring kit and authored-resource loader. No separate building-prompt document was found; the user approved using the code as the reference.

## Deliverable

An enclosed **128-block X × 128-block Z × 100-block Y** arena. This is a footprint and height, not a literal 128-block radius. The schematic reports Width=128, Length=128, Height=100, using the correct Sponge v3 unnamed outer compound and Schematic child.

The build includes 54 individual grave plots, disturbed-earth beds, varied stone/cross headstones, carved pumpkins, candles, skull decorations, two dead lantern trees, a west reliquary, a monumental skull-front mausoleum, Gothic stained-glass bays, a rose window, a bell tower, pointed entry arches, high supported vault ribs and suspended lantern fixtures.

The architecture uses vanilla deepslate, blackstone, mossy masonry, dark oak, bone blocks, iron bars, chains, amethyst and crying obsidian. Blue/purple accents use soul lanterns, blue/purple candles and stained glass, pearlescent froglights, crying obsidian and end rods. **Vanilla block light is white**; these materials supply colored surfaces and flames. No colored-light renderer, shader, client code or required resource pack is added.

## Gameplay space and markers

All coordinates below are schematic-local. The authored resource currently uses origin [0,0,0], so they are also the initial run-world coordinates.

| Marker | X / Y / Z | Purpose |
|---|---|---|
| Entry / exit | 64 / 13 / 119 | Inside the south gateway |
| Boss anchor | 64 / 13 / 64 | Center of the combat court |
| Party staging | 64 / 13 / 103 | South processional approach |
| West crypt | 24 / 13 / 29 | Reliquary access |
| Chapel altar | 64 / 16 / 24 | Raised interior altar |
| Bell tower | 104 / 13 / 27 | Ground-level tower entrance |

The main floor surface is Y=12. The central combat area has a **20-block clear radius and eight blocks of clear height**, measured in the final composed build. Graveyard props, fences, lantern columns and vault structures remain outside that volume. Bosses larger than this clearance need a separate placement review.

Five chest markers are provided, using the existing crimson-cathedral loot table when loaded through the authored engine. Lootr conversion follows the mod's existing integration. Standalone WorldEdit pastes contain vanilla chest placeholders and do not automatically configure the mod's loot or raid ownership.

**This is an arena build, not a configured boss encounter.** No boss species, AI, raid definition, battle duration, catchability, victory reward or automatic fight activation is assigned. The boss/staging markers reserve integration points. The authored entry can be used to inspect the arena; normal run timeout/leave behavior still belongs to the existing mod.

## Installation / review

The server JAR is `AscensionDungeons-0.0.8-halloween-court.jar`. Replace the previous AscensionDungeons JAR, keep one version, restart and use:

```text
/dungeon type halloween_graveyard
```

The arena is appended to the authored dungeon index. Existing authored dungeons and gameplay code are retained. No server was started or live server mods directory changed during this task.

Alternatively, import `MourningCourt_128x128x100.schem` with WorldEdit. The origin is the lowest northwest corner; use a normal paste **including air**, because its interior must be cleared. The enclosed build contains a solid foundation, physical roof and perimeter shell. Allow the full 128×128×100 volume.

## Authoring and validation

Reproducible builder: `L:/ascensionDungeons/tools/dungeons/halloween_graveyard.py`. Run it directly rather than through the generic six-room dungeon runner, because this is one boss-arena asset rather than a five-room adventure contract.

```text
python tools/dungeons/halloween_graveyard.py outputs/halloween-graveyard
```

The builder checks important marker/chest reachability, the central combat volume, solid boundary sealing, six-neighbor connectivity of masonry to the foundation (including chains as suspension supports), and lantern attachment. It exports the actual schematic, layout, palette, material quantities and validation report. The attachment check approximates partial blocks as occupied cells; it is not a full Minecraft collision/physics simulation.

Validation found and corrected a tower-door obstruction, removed combat-space intrusions and restored the tower floor before release. Final geometry reports 12,821 reachable standing cells, no detached solid components and no validation errors. The schematic was round-tripped through an independent NBT reader; dimensions, v3 wrapper and vanilla block IDs were checked against local Minecraft 1.21.1 assets. Wall and iron-bar connections are explicitly authored for the live generator.

The existing Gradle test and remap tasks completed successfully. No live Minecraft visual inspection or multiplayer load benchmark was performed.

Preview images show the exported blocks with vanilla textures in an offline raycaster. Partial models, glass transparency and lighting are approximated; they are not in-game screenshots and do not promise shader lighting. The source mod's unchanged runtime should be used for final in-game review.
