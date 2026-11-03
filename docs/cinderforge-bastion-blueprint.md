# Cinderforge Bastion: Blueprint v2 (Rooms 1-8)

Fire / Nether dungeon, 8 rooms, vanilla blocks only (no client install needed). v2 = v1 plus the Room 1-2 verification fixes and the Rooms 3-8 audit fixes. Gameplay behaviour is owned by the mod; this document only reserves blocks and regions ("mod markers").

## Conventions
- Each room has a local frame (X, Y, Z). **Y0 = floor surface layer; players stand at Y1.** Y-1 and below is foundation.
- Z increases toward the room exit unless stated. Handoffs list the offset to the previous room.
- "Inv." = upside-down stair, open side facing the opening. "wall" = wall block. "slab (top/bottom)" = slab half.
- Distances *d* in circular rooms are measured block-centre to the room centre.
- Palette: `BS` blackstone, `PBB` polished blackstone bricks (`cPBB` cracked, `chPB` chiseled polished blackstone), `NB` nether bricks (`cNB` cracked), `RNB` red nether bricks, `NR` netherrack, `SS` soul sand, `L` lava source, `SL` soul lantern, `SC` soul campfire. Each room also uses stairs, slabs and walls of its main materials.
- **Enclosure rule (decision, was open item 11):** every room is a closed volume. Any footprint boundary that is not a declared connector gets a 2-thick `NR`/`BS` shell, and each room has a physical ceiling (heights below). The instance world supplies nothing.
- **Lava rule:** lava is source blocks only, every one with solid blocks on all four sides and below, and no iron bars touching liquid. No flowing lava anywhere (Room 2 lavafalls are faux).
- Mod markers are listed at the end.

---

## Room 1: Ashen Gatehouse
**Frame:** X0-24, Z0-20, Y-1 to Y20. Spawn (12,1,3) facing +Z. Ceiling: `NR`/`BS` mix at Y19-20 over the whole footprint. Perimeter shell 2 thick outside the footprint on W, E, S.

**Y-1:** `BS` over X0-24, Z0-20.

**Y0 floor** (Z20 at top; `.` = ground mix 55% `NR`, 25% `SS`, 20% `BS` gravel):
```
        X→ 0         1         2
           0123456789012345678901234
Z20        nnnXXXXXbbbbbbbbbXXXXXnnn
Z19        nnnXXXXXXXbbbbbXXXXXXXnnn
Z18        nnnXXXXXXbbbbbbbXXXXXXnnn
Z17        nnnXXXXXbbbbbbbbbXXXXXnnn
Z16..12    nnnnnnnnLXbRRRbXLnnnnnnnn   (5 rows)
Z11..8     nnnnnnnnnnbRRRbnnnnnnnnnn   (4 rows)
Z7         .......LLLXRRRXLLL.......
Z6,5,2,1   .......LBBBBBBBBBL.......
Z4,3       .......LBBBCCCBBBL.......
Z0         ........XXXXXXXXX........
```
`n` `NR`; `X` `BS`; `b` `PBB` (30% `cPBB`); `R` `RNB`; `B` `PBB` (outer pad ring `cPBB`); `C` `chPB`; `L` lava.
Verified: all rows 25 wide; every lava cell has solid Y0 neighbours.

**Y1 walls (every lava cell is walled off from open floor):**
- Pad edge: `PBB` wall at X8 and X16 for Z1-6; Z0 for X8-16; Z6 for X8-10 and X14-16 (exit gap X11-13). `SL` on the 4 pad corner posts (Y2).
- Bridge rails (Z7): `BS` wall at X10 and X14.
- Moat outer side: `BS` wall at X6 for Z1-7, X18 for Z1-7, (7,0), (17,0); far side Z8 at X7-9 and X15-17.
- Channels (Z12-16): `BS` wall at X9 and X15 (inner) and X7 and X17 (outer); channel ends (8,11), (16,11); gate ends (8,17), (16,17). `SL` on top of the X9/X15 walls at Z12, Z14, Z16.
- Flora: crimson roots, nether sprouts, a few crimson fungus on `NR`; sparse gray concrete powder on `SS` as ash. Rubble hills (decor only) on the outer ground.

**Maw Arch (Z17-19).** Mass silhouette X range: Y1-8 X3-21; Y9-11 X5-19; Y12-14 X7-17. Planes: Z17 `BS`, Z18 `PBB`, Z19 `NB`. Clear X range per plane (funnel, 3 nested pointed arches):

| Y | Z17 | Z18 | Z19 |
|---|---|---|---|
| 1-7 | 8-16 | 9-15 | 10-14 |
| 8 | 9-15 | 10-14 | 11-13 |
| 9 | 10-14 | 11-13 | 12 |
| 10 | 11-13 | 12 | closed |
| 11 | 12 | closed | closed |
| 12+ | closed | closed | closed |

- **Corbels (Inv. stair of that plane's material):** Z17 at (8,Y8), (9,Y9), (10,Y10), (11,Y11) and mirror (16,Y8), (15,Y9), (14,Y10), (13,Y11). Z18 at (9,Y8), (10,Y9), (11,Y10) and mirror. Z19 at (10,Y8), (11,Y9) and mirror.
- **Keystones (`chPB`):** (12,12,17), (12,11,18), (12,10,19).
- **Fangs:** at Z17, `BS` wall at X8 and X16, Y6-7, with an Inv. `BS` stair tip at Y5 (clearance Y1-4).
- **Tongue:** at Z17, X12: `chain` Y9-11, `SL` at Y8.
- **Alcoves:** cut X5-6 and X18-19, Z17-18, Y1-4. `chPB` floor at Y1, `SL` at Y2, Inv. `PBB` stair ceiling at Y4 (clear Y2-3: lantern niche, not walk-in).
- **Crown:** Y14 is full `BS`; Y15 bottom `BS` slabs on the perimeter cells (parapet); `SC` at (8,15,18) and (16,15,18).
- **Horns:** X6 and X18, Z18: Y12 `BS` stair (flare), Y13-15 `BS` wall, Y16 `BS` slab.
- **Wings:** `BS` bricks Z17-19, X0-2 and X22-24, Y1-5, topped with `BS` stairs at Y6; cut X2/X22 at Z17 back one block (1-deep step).
- **Cliff banks:** X0-2 and X22-24, Z8-16, `NR` (20% `BS`, a little `magma block`) rising irregularly to Y10; `weeping vines` along the top.

**Portcullis frame and back wall (Z20).** Z20 is a sealed plane X0-24, Y1-18 (`BS`) with one opening X10-14, Y1-7. Frame: inner jambs X9, X15 (Y1-8, `PBB`); outer jambs X8, X16 (`PBB` wall Y1-7, Inv. `PBB` stair at Y8); lintel Y8 X10-14 (`PBB`, `chPB` at X12); cap Y9 X8-16 (`PBB` bottom slabs); `SL` at Y10 on X9 and X15. Gate slot: iron bars X10-14, Y5-7 permanent (raised portcullis). **Closed state** = mod fills X10-14, Y1-4, Z20 with iron bars; open = air.

---

## Room 2: Blackstone Causeway
**Frame:** X0-40, Z0-69, Y-9 to Y16. Mapping from Room 1: X2 = X1 + 8, Z2 = Z1 - 21, same Y. Deck Y0, centerline X20, 7 wide (X17-23). Ceiling: `NR`/`BS` at Y15-16 (shroomlight accents). Shell on any boundary not touching a neighbouring room.

**Zones (Z):** 0-5 landing; 6-17 span A; 18-23 pylon 1; 24-39 span B; 40-45 pylon 2; 46-61 span C; 62-67 pylon 3; 68-69 exit threshold.

**Lava sea:** `L` at Y-8 to Y-4 over X3-37, Z1-68; `BS` floor at Y-9. Solid (not sea): landing foundation X16-24, Z0-5 and exit foundation X16-24, Z64-69, both `BS` Y-9 to Y-1; Z0 and Z69 rows outside those are `BS` wall Y-9 to Y12. Cliffs X0-2 and X38-40: `NR` (25% `BS`), Y-9 to Y12 irregular, `weeping vines` on top. **Faux lavafalls** (no flowing lava): 1-wide vertical strips of `magma block` with a `shroomlight` every 4th block, Y-3 to Y11, at X2/Z20 and X38/Z48 on the cliff faces.

**Landing (Z0-5):** `PBB` X16-24 with `chPB` crest at X20, Z2-3; tapers to the deck at Z6; `BS` wall edges, `SL` at corners.

**Deck (Z6-67, X17-23):** Y0: runner X19-21 `RNB`, edge strips X17-18 and X22-23 `PBB` in 4-long bands alternating with 2-long `cPBB`. Y-1: `BS` X16-24 (a pier overrides it). Lip: bottom `PBB` slab at X16 and X24 on Y0, Inv. `PBB` stair under it at Y-1 (omitted on island stair rows). Rails: `BS` wall at X17 and X23, Y1, **stopping at the pylon faces Z17/Z24 etc.**; walkable X18-22 on spans, full 7 inside pylons.

**Posts and braziers:** at the first Z of each pier pair (Z9, 15, 27, 33, 49, 55) on both rails: `BS` wall Y1-3, `PBB` Y4, `campfire` Y5 with iron bars on its 4 sides, `PBB` bottom slab Y6.

**Piers (2 thick in Z):** Z9-10, 15-16 (A); 27-28, 33-34 (B); 49-50, 55-56 (C). Footing `BS`: Y-9 to Y-7 X15-25; Y-6 to Y-4 X16-24. Upper `PBB` Y-3 to Y-1 X17-23 with arch cut: Y-3 X18-22 open, Y-2 X19-21, Y-1 X20 (Inv. `PBB` stairs at steps). **Span B piers (Z27-28, Z33-34) also get a trench pass-through: clear X19-21, Y-6 to Y-4.**

**Pylons (P1 Z18-23, P2 Z40-45, P3 Z62-67):**
- Flared base `BS` Y-9 to Y-5, X12-28 over **exactly** the pylon Z-range (a Z extension would overlap the trench ends); middle `PBB` Y-4 to Y-1, X13-27, Z of pylon.
- **Y0 under the towers (X13-16, X24-27): `PBB`.**
- Towers X13-16 and X24-27, Y1-11, `PBB` body with `BS` trim. Passage X17-23, Y1-6 clear, stepped arch: Y7 X18-22, Y8 X19-21, Y9 X20, `chPB` keystone Y10 X20 (Inv. `PBB` stairs at steps).
- Corbel course of `BS` stairs at Y7 projecting 1 block on the X12 and X28 faces. Arrow slits on outer faces (X13, X27): 1x3 recesses Y4-6, 1 deep, iron bars across, 2 per face. Battlements Y11: alternate `PBB` merlons and gaps. Front/rear niches: 2 wide, 3 tall, 1 deep at Y2-4 with `SL`.

**Loop B trench (bypasses span B):** `PBB` floor Y-7, X19-21, Z24-39, **with `BS` at Y-8 below (not lava)**; `BS` walls X18 and X22, Y-7 to Y-4; fill `L` X19-21, Y-6 to Y-4 (mod drains it to air). Access:
- **P1:** hidden door `PBB` at (24,1-2,20) (mod clears it). Chamber column X25, Z20, Y-6 to Y2 with the **ladder in the chamber cell itself (X25, Y-6 to Y2, facing west, backed by solid X26)**, so the door opens straight onto it. Roof Y3 solid. **Corridor** X25, Z20-22, Y-6 to Y-5, then west X21-24 at **Z22** (one row inside the pylon, so it never touches the lava sea). **Trench plug:** `BS` at (21, Y-6 to -5, Z23), which the mod removes with the drain; it meets the trench at (21,24).
- **P2 (mirror):** door (24,1-2,43); chamber and ladder at X25, Z43; corridor X25, Z41-43, then west X21-24 at **Z41**; trench plug `BS` at (21, Y-6 to -5, Z40), meeting the trench end at (21,39).

**Basalt islands:** I1 X8-14, Z27-33; I2 X26-32, Z30-36; I3 X8-14, Z49-55; I4 X26-32, Z52-58. Top at Y-3 (`basalt`, `smooth basalt`, `magma block` rim), 2-high basalt cover clusters at Y-2 to -1, chest at each centre. **Stairs, 3 wide:** I1 rows Z29-31, I2 rows Z32-34, I3 rows Z51-53, I4 rows Z54-56. West islands: rail gap at X17 (Y1), stairs at X16 (Y-1) and X15 (Y-2), island edge X14. East islands: gap at X23, stairs X24 (Y-1) and X25 (Y-2), edge X26. **`BS` support fill** from Y-4 up to under each stair.

**Geyser vent markers:** flush `magma block` in the deck at X18 and X22 for Z28, 34, 52, 58.

---

## Room 3: Crimson Barracks
**Frame:** X0-32, Z0-34, Y-1 to Y17. Mapping from Room 2: X3 = X2 - 4, Z3 = Z2 - 70, same Y. Everything in the footprint not listed as open is solid `NB`/`BS`.

- **Vestibule** Z0-3, X14-18: `NB` floor, clear Y1-6, stepped arch (Y7 X15-17, Y8 X16, `chPB` keystone Y9).
- **Nave** interior X6-26, Z4-27. Floor Y0: strip X12-20 `RNB` with `NB` border; aisles `NB`/`cNB` checker; four 2x3 crimson nylium patches per aisle at Z12-14 and Z20-22 (X7-8 and X24-25). Dais: 5x5 `chPB` at X14-18, Z15-19, lava cauldron at its 4 corners on Y1.
- **Pillars** (3x3) centred X10 and X22 at Z7, 11, 15, 19, 23. Y1: ring of `NB` bottom slabs, centre `RNB`. Y2-8: centre `RNB`, `RNB` wall on the 4 orthogonal sides. Y9: Inv. `NB` stairs on the orthogonal sides. Y10: `NB` top slabs on the 8 ring blocks, centre `RNB`.
- **Vault:** aisle ceiling flat at Y11. Over X11-21 the ceiling block Y is 11, 12, 13, 14, 15, 15, 15, 14, 13, 12, 11 (Inv. `NB` stairs on slopes). Ribs: ceiling blocks at the pillar Z rows (7, 11, 15, 19, 23) are `RNB`. 2-thick `NB` shell above (Y16-17).
- **Canopy:** `nether wart block` clusters 3x3x2 at Y10-11, centred (13,13), (19,13), (13,21), (19,21), `shroomlight` at each centre, hung by one `chain` at Y12; `weeping vines` below.
- **Bunk alcoves:** west cut X4-5, east cut X27-28, at Z8-10, 12-14, 20-22 (1-block wall gap to the door slot on each side). Y2-4, red bed along X, `SL` on chain at Y4, Inv. `NB` stairs at the top corners.
- **Exits:**
  1. North: opening X14-18, Y1-6, stepped arch as the vestibule; corridor X14-18, Z28-34 (hands off at Z34).
  2. **East locked door (to Room 6): Z16-18** (centred on the dais). Plug `BS` at X27, Z16-18, Y1-4. Corridor X28-32, Z16-18, Y1-4, hands off at X32.
  3. **West lift cell: Z16-18.** Cell X1-3, Z16-18, Y1-5, `chPB` floor. Doorway X4-5, Z16-18, Y1-4 sealed with `BS`. The mod teleports players here.

---

## Room 4: Magma Cistern
**Frame:** X0-48, Z0-48, Y-15 to Y20. Mapping from Room 3: X4 = X3 + 8, Z4 = Z3 - 34, same Y. Centre (24,24). **Only the basin is built: the 2-thick outer wall (d 16.5-18.5, Y-15 to Y13) is the enclosure; the square corners (d > 18.5) are not built, and the world fill outside is not part of the room.**

| Ring | d | Surface Y |
|---|---|---|
| Outer wall | 16.5-18.5 | up to Y13 (`PBB`), ceiling Y14 |
| Rim | 13.5-16.5 | Y0 |
| Tier 2 | 10.5-13.5 | Y-3 |
| Tier 3 | 7.5-10.5 | Y-6 |
| Reservoir | <7.5 | floor Y-14 (`BS`), `L` at Y-13 to Y-7 |

- **Solid mass: `BS` for d >= 7.5 from Y-15 up to each ring's surface, and `BS` for the reservoir wall (d 7.5, Y-14 to Y-6).** Risers faced `PBB`/`cPBB`.
- Entry corridor X22-26, Z0-7, Y1-6 through the wall; opens onto the rim at Z8.
- Stairs: at N, E, S, W, 3 wide (X23-25 or Z23-25). Innermost 2 blocks of the rim ring step down to tier 2; innermost 2 of tier 2 step down to tier 3.
- Tier 3 rail: `BS` wall on d 7.5-8.5 at Y-5.
- Magma veins: 8 radial 1-wide `magma block` lines in the rim at angles 22.5 deg + 45 deg x k. **Walking on them hurts (intended hazard).**
- Decor: 6 basalt spires 2-4 high on the rim near d 14.5, `BS` gravel.
- Ceiling `NB` at Y14, Inv. `BS` stair ring ribs at d = 8, 13, 18 (Y13). Chimney oculus d < 4 to Y18, capped, ring of `shroomlight` at its top.
- **Drain-gate niches (4 at 45, 135, 225, 315 deg):** 4 wide, Y1-5, stepped arch, cut 2 deep into the outer wall (no bars). A lever on the back wall at Y2. NE = stage 1, NW = stage 2, SW = stage 3, SE = decorative with lava cauldrons and a chest slot.
- **Drain stages (mod marker: reservoir d < 7.5):** (1) remove `L` at Y-8 to -7; (2) Y-10 to -9; (3) Y-13 to -11 plus the plug. Stage 3 also triggers Room 2's trench drain.
- **Hidden passage to Room 5:** tunnel X6-16, Z23-25, Y-13 to Y-11 (3x3), floor `BS` at Y-14. Plug `BS` at X16, Z23-25, Y-13 to Y-11 (removed at stage 3).

---

## Room 5: Warped Reliquary
**Frame:** chapel X0-24, Z0-24, Y-2 to Y17, plus tunnel X22-32. Mapping from Room 4: X5 = X4 + 16, Z5 = Z4 - 12, Y5 = Y4 + 14. Centre (12,12). Everything outside the carved volume is solid `NR`.

- Interior d < 8.5, floor Y0. Wall d 8.5-10.5, Y1-7: `warped stem`, `warped wart block` accents, `PBB` trim.
- **Dome:** rings of Inv. `warped stairs`, each about 1.5 thick, ring inner radius = 16 - Y for Y8-15 (8, 7, 6, 5, 4, 3, 2, 1); Y16 `shroomlight` cap. 3 `shroomlight` blocks embedded in the Y11 ring.
- Tunnel from Room 4: X22-32, Z11-13, Y1-3, floor `BS`. Doorway through the wall X21-22, Z11-13, Y1-3.
- **Altar niche (north): cut X10-14, Z21-23, floor Y-1 (sunken).** Altar `chPB` at (12,0,22), key chest at (12,1,22).
- **Statue niches:** 6, 1 wide x 3 tall x 1 deep, at angles 45, 135, 180, 225, 270, 315 deg (0 deg = entry, 90 deg = altar), at d 9.5. Each holds `PBB` wall Y1-2 plus a `chPB` head at Y3.
- Flora: `warped nylium` patches, warped fungus/roots, twisting vines, nether sprouts. Four `sea lantern` floor blocks at d = 5 (N, E, S, W), `SL` accents.

---

## Room 6: The Smelting Works
**Frame:** X0-48, Z0-24, Y-1 to Y17. **Mapping from Room 3: X6 = X3 - 32, Z6 = Z3 - 5**, same Y. Hall interior X4-43, Z2-22; north wall Z0-1. Entry corridor X0-3, Z11-13, Y1-4. Exit corridor X44-47, Z11-13, Y1-4.

- **Trough:** `BS` rim at Z11 and Z13 (Y0-1), `L` at Z12 (Y0), X8-41; end caps X7 and X42 (`BS` Y0-1, Z11-13). Crossings: replace Z11-13 with `PBB` at Y0 at X17-19, X27-29, X37-39.
- **Furnaces:** centres X13, X23, X33; housings X(c-3)-(c+3), Z2-6. Body `NB` Y1-5 on `BS` base; hood Inv. `NB` stairs at Y6; lip top `NB` slab at Y7. Cavity X(c-1)-(c+1), Z3-5, Y1-2 air with `L` at Y0 only (solid below and around). Mouth: iron bars on the Z6 plane, X(c-1)-(c+1), Y1-2 (no liquid adjacent). Ladles: `lava cauldron` at (c,11,12) hung by 4 `chain` (Y12-15).
- **South gantry:** deck Y6, X5-41, Z16-19 (`PBB`), `BS` wall rails at Z16 and Z19 on Y7. Piers 2x4 at X10-11, 20-21, 30-31, 40-41, Y1-5, `PBB`. Bays X12-19, 22-29, 32-39 closed by corbel arches (clear width 8 at Y1-3, 6 at Y4, 4 at Y5). X5-9 is solid `BS` landing mass Y1-5. **Ladder at (4, Y1-7, 17)** against the wall at X3.
- Pipes: `hopper` run at Y9, **Z2**, X6-41, with `chain` drops. **Fire-trap droppers (mod markers)** at **Z1** (in the wall), Y3, facing +Z, at X8, 18, 28, 38.
- Soul sand pits (3x3, soul fire on top) at X5-7, Z4-6 and X40-42, Z4-6. Ceiling `BS` Y16 with `PBB` ribs, 2-thick shell above.

---

## Room 7: Obsidian Spiral
**Frame:** X0-24, Z0-24, Y-1 to Y48. Mapping from Room 6: X7 = X6 - 48, Z7 = Z6, same Y. Centre (12,12). Entry passage X0-3, Z11-13, Y1-4 (west wall).

- **Shaft:** outer wall d 9.5-12 (`BS`, scattered `crying obsidian`) full height. **Ramp d 5.5-9.5. Inner rail `BS` wall on d 5.5-6.5 at ramp-top +1, so walkable d 6.5-9.5 (3 wide).** Central well d < 5.5, floor Y-1.
- **Ramp:** two clockwise laps, 16 octants (W, NW, N, NE, E, SE, S, SW, repeated). Start Y of each octant: 0, 2, 5, 7, 10, 12, 15, 17, 20, 22, 25, 27, 30, 32, 35, 37; ends Y40 at W. Each octant is a flat landing then 2-3 stair steps at its end (arc length about 6.3 at r = 8). Surface `PBB` with `BS` stairs and `PBB` slab trim. Headroom 4.
- **Windows:** 8 arched 2x3 windows through the wall at the N, E, S, W landings of each lap, backed by a `magma block` and `shroomlight` wall (no liquid, no bars).
- **Lava rise:** the central well is the lava volume (mod-driven; damage-based).
- **Exit:** doorway X0-3, Z11-13, **Y41-44**, west wall. Roof `BS` at Y46-47.

---

## Room 8: The Forge Heart
**Frame:** X0-48, Z0-48, Y-1 to Y28. Mapping from Room 7: X8 = X7 + 48, Z8 = Z7 + 12, Y8 = Y7 - 40. Sits above Room 6 (at least 22 Y separation). Centre (24,24). Ceiling `NR`/`BS`/`magma block` at Y26-27 with `shroomlight`.

| d | Feature |
|---|---|
| <5 | Crucible platform, Y2 |
| 5-6.5 | Stair ring, Y1 |
| 6.5-9 | `PBB` |
| 9-13 | `BS` with `cPBB` spokes |
| 13-19 | `PBB` outer band |
| 19-22 | Lava moat, `L` at Y0, `BS` at Y-1 |
| 22-24 | Outer wall |

- Crucible platform: `chPB`/`PBB` concentric rings, `L` source at the centre for d < 1.5 (Y2).
- Moat rim: `BS` wall at d 18-19, Y1, except at the bridge.
- **Entry:** gate in the east wall X46-48, Z23-25, Y1-4, floor `PBB` at Y0. **Bridge X43-45, Z22-26 (5 wide), `PBB` at Y0, `BS` wall rails on Z22 and Z26 (Y1).**
- **Outer wall:** d 22-24 and **solid up to the ceiling (Y1-26)**, stepping 1 block inward per 4 blocks above Y12. 12 blind arches (4 wide, 9 tall, 1 deep) at angles 15 + 30k deg, each with a `PBB` slab ledge at Y4 and a `BS` rail (decorative balcony).
- **Heat vents:** eight 2x2 flush `magma block` at Y0, each starting at (34,28), (34,20), (14,28), (14,20), (28,34), (20,34), (28,14), (20,14).
- **Chain pylons:** hanging 3x3 `obsidian` columns Y14-26 at (35,35), (13,35), (35,13), (13,13); chains below to Y10, ending in a hanging `SL` at Y9.
- **Basalt spires:** 2x2, rising Y13-22, from the wall at 6 points.
- **Lift pad:** the 3x3 centre lava at (23-25, 2, 23-25) is replaced by `chPB` plus an `SL` ring by the mod when the boss is defeated.

---

## Handoffs
| From | To | Link |
|---|---|---|
| Room 1 Z20 portcullis | Room 2 Z0 | deck/landing X8-16 -> X16-24 |
| Room 2 Z69 | Room 3 Z0 | X18-22 -> X14-18 |
| Room 3 Z34 | Room 4 Z0 | X14-18 -> X22-26 |
| Room 4 tunnel (X6-16) | Room 5 chapel | Z23-25 -> Z11-13 |
| Room 3 east plug (Z16-18) | Room 6 X0 | Z11-13 |
| Room 6 X47 | Room 7 X0 | Z11-13 |
| Room 7 Y41-44 | Room 8 east gate | Z11-13 -> Z23-25 |
| Room 8 lift pad | Room 3 lift cell | mod teleport |

## Mod markers
Room 1: portcullis fill (X10-14, Y1-4, Z20). Room 2: trench fill, trench plugs (21,-6..-5,23) and (21,-6..-5,40), hidden doors (24,1-2,20) and (24,1-2,43), island chests, geyser vents. Room 3: east plug, west plug, lift cell arrival. Room 4: three levers, drain regions, tunnel plug, SE chest. Room 5: mini-fight ring (d = 5), key chest. Room 6: Overseer spawn (23,1,8), fire-trap droppers. Room 7: central well lava volume. Room 8: boss spawn, vents, moat zone, lift pad.

## Open integration notes
- The existing district system uses 9-wide, 8-high gates (floor Y4, clear Y5-12). Cinderforge connectors are 3-7 wide and 4-6 high; they will need widening or an adapter if the two systems must share a gate contract.
- Nothing here has been built or seen in-world. Lava containment, door/plug cells and the ladder shafts are the first things to check on a test build.

---

## v2.1 build notes (generator: `tools/cinderforge/build_rooms12.py`, `build_rooms38.py`)
Decisions taken while building Rooms 3-8 and assembling the full dungeon. Where these differ from the text above, **this section wins**.
- **Gate size:** Cinderforge stays standalone with its own 3-7 wide connectors; it does not share the district 9x8 gate contract. An adapter can be added if the two ever have to join.
- **Room 4 descent stairs (new):** after the drain players need a way back out of the reservoir. A 3-wide `PBB` staircase runs down the south side of the reservoir (X23-25, Z25-31, Y-7 to Y-13), solid and submerged until the drain. Its top is closed by the tier-3 rail cells at (23-25, Y-5, Z32); the mod removes them with drain stage 3 (marker `r4_descent_gate`). While closed, lava touches only the top step, which nobody can reach.
- **Room 4 drain niches:** four axis-aligned niches in the outer wall, E and W sides at Z16-19 and Z29-32 (not diagonals). E/Z16-19 = lever 1, W/Z16-19 = lever 2, W/Z29-32 = lever 3, E/Z29-32 = chest.
- **Room 6 crossings:** each trough crossing has `BS` wall rails at Y1 on the two cells that touch lava (X edge, Z12), so each crossing is 1 wide in the middle.
- **Room 7:** 7 windows (N, E, S on both laps, W on lap 1; W lap 0 is the entry). Ring floor is solid at Y0; entry and exit passage floors are `PBB`; the exit landing (X3-4, Z11-13) is solid at Y40.
- **Room 8:** `BS` wall rim at Y3 around the 3x3 crucible lava so nobody steps in by accident. Wall arches carve Y5-12 (ledge slab at Y4). Basalt spires stand on the arena floor at d = 16.5 (not on the wall).
- **Room 4 / Room 5 overlap:** the chapel is carved into Room 4's solid south-west corner mass; this is intended.
- **Not generated:** weeping vines, rubble hills, island chest loot, all gameplay.
- **Room 3 dais braziers (changed after the bot walk test):** the four lava cauldrons now sit on 2-high `PBB` wall posts (cauldron at Y3). At floor level a player can climb onto or fall into a cauldron and catch fire (the bot took 4 hp per hit and kept burning).
- **Walk model:** walls (1.5 tall) are never walkable ground, and cauldrons/magma/campfires are avoided. The rail walls in Room 4 cannot be stepped over.
