"""The Elder Wyrm Roost: the Dragon dungeon (id `dragon`).

Rooms: Ancient Roost -> Scale Gallery -> Spinebridge Chasm -> Wyrm Court -> Hoard Vault; arena Elder Wyrm Crucible.
Hazard : Spinebridge Chasm is a round pit crossed by two spans of dragon vertebrae that crumble under you; fire breath erupts from wall vents
         along the spans after a telegraph (fire damage, you burn). Falls land on the pit floor; ladders lead back up.
Puzzle : Scale Alignment - four levers must match the gold/black scale clues above them to open the way to the chasm; the Wyrm Trial yields a Hoard
         Coin, offered at the court to open the Hoard Vault; its Wyrm Key opens the arena.
Extra  : Roost Beacon (one lever) opens the Hatchling Habitat Niche; Ancient Seal Control (two levers, in order) opens the Sealed Chronicle Vault.
Boss   : the Elder Wyrm (wither skeleton) behind three gold wards; fire breath rains on the crucible while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever

W, H, L = 300, 46, 140
FLOOR = 7
BS, PBB, GBS, OBS = b('blackstone'), b('polished_blackstone_bricks'), b('gilded_blackstone'), b('obsidian')
BONE, GOLD, CRY = b('bone_block', axis='y'), b('gold_block'), b('crying_obsidian')
DNT, CBB, REDB = b('deepslate_tiles'), b('cracked_polished_blackstone_bricks'), b('red_nether_bricks')
GLOW = b('glowstone')
PIT, COURT, ARENA = (150, 32), (204, 32), (262, 32)


def bone(axis): return b('bone_block', axis=axis)


def build():
    d = kit.Dungeon('dragon', 'The Elder Wyrm Roost', W, H, L, biome='minecraft:badlands')
    d.loot_table = 'ascensiondungeons:chests/ancient_foundry'
    d.shell = ('blackstone', 'polished_blackstone_bricks', 'gilded_blackstone', 'obsidian', 'cracked_polished_blackstone_bricks', 'deepslate_tiles')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, BS)
    C.key(d, 'hoard_coin', 'minecraft:gold_nugget', 'Hoard Coin', 'Heavy for its size. The Hoard Offering in the Wyrm Court wants it.')
    C.key(d, 'wyrm_key', 'minecraft:tripwire_hook', 'Wyrm Key', 'Opens the scorched door to the Elder Wyrm Crucible.')
    roost(d); scales(d); pit(d); court(d); hoard(d); crucible(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R5', False), ('scale_alignment', 'R3', True), ('scale_alignment', 'R5', False), ('offering', 'R5', True),
                     ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, BS, [(PBB, 0.22), (CBB, 0.1), (GBS, 0.025), (OBS, 0.05)])
    return d


# ================================================================ R1 Ancient Roost (cave around 26,30)
def roost(d):
    d.ellipsoid(26, 18, 30, 23, 18, 21, kit.AIR)
    d.fill(2, 0, 8, 52, FLOOR, 52, BS)
    for x in range(3, 52):
        for z in range(9, 52):
            if d.get(x, 8, z) == kit.AIR: d.set(x, FLOOR, z, d.rng.choice([BS, PBB, DNT, BS]))
    d.entry = (7, 8, 30)
    # Ancient Roost Crown (focal): a pillar topped with a nest ring and a crown of bone spikes
    cx, cz = 30, 30
    d.fill(cx - 1, 8, cz - 1, cx + 1, 20, cz + 1, OBS)
    d.disc(cx, cz, 5, 21, 21, b('hay_block')); d.disc(cx, cz, 2, 22, 22, GOLD)
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 5, 10)):
        d.fill(x, 22, z, x, 25 + k % 2 * 2, z, bone('y'))
    d.disc(cx, cz, 4, 20, 20, PBB, rim=1)
    for yy in range(9, 20, 3):
        for (x, z) in S.pts_ring(cx, cz, 2, 4, 0.78): d.set(x, yy, z, b('bone_block', axis='y'))
    d.landmark('ancient_roost_crown', 'Ancient Roost Crown', 'focal', (cx - 6, 8, cz - 6), (cx + 6, 27, cz + 6))
    # Lost Rider Camp (discovery)
    gx, gz = 12, 14
    S.pyramid(d, gx, gz, 8, 3, [b('red_wool'), b('black_wool')]); d.air(gx - 1, 8, gz - 1, gx + 1, 9, gz + 1)
    d.fill(gx + 5, 8, gz, gx + 5, 10, gz, b('oak_fence')); d.set(gx + 5, 11, gz, b('red_banner', rotation=0))
    d.set(gx + 3, 8, gz + 3, b('campfire', facing='north', lit=False, signal_fire=False, waterlogged=False))
    d.add_chest((gx, 8, gz), 'east', 'loot'); d.set(gx - 2, 8, gz + 2, b('barrel', facing='up', open=False))
    d.landmark('lost_rider_camp', 'Lost Rider Camp', 'discovery', (gx - 3, 8, gz - 3), (gx + 6, 12, gz + 4))
    # Roost Beacon (interactive): a lever that lights three beacon braziers and opens the Hatchling Habitat Niche
    bx, bz = 44, 44
    d.fill(bx, 8, bz, bx, 10, bz, OBS); d.set(bx - 1, 9, bz, lever('wall', 'west'))
    for (x, z) in ((bx - 3, bz + 3), (bx + 3, bz + 3), (bx, bz + 5)): d.fill(x, 8, z, x, 10, z, PBB); d.set(x, 11, z, b('soul_campfire', facing='north', lit=False, signal_fire=False, waterlogged=False))
    d.landmark('roost_beacon', 'Roost Beacon', 'interactive', (bx - 4, 8, bz - 1), (bx + 4, 12, bz + 6))
    # Hatchling Habitat Niche (discovery): a nest behind the east wall
    hx, hz = 49, 22
    d.air(hx, 8, hz, hx + 6, 11, hz + 6)
    d.fill(hx + 1, 8, hz + 1, hx + 5, 8, hz + 5, b('hay_block')); d.set(hx + 3, 9, hz + 3, b('turtle_egg', eggs=2, hatch=0, waterlogged=False)) if False else d.set(hx + 3, 9, hz + 3, b('white_wool'))
    d.add_chest((hx + 3, 8, hz + 6), 'north', 'loot'); d.set(hx + 1, 8, hz + 1, b('candle', candles=3, lit=True, waterlogged=False))
    d.fill(hx, 7, hz, hx + 6, 7, hz + 6, DNT)
    d.seal('hatchling_wall', 'minecraft:polished_blackstone_bricks', (hx - 1, 8, hz + 2), (hx - 1, 11, hz + 4))
    d.air(hx - 2, 8, hz + 2, hx - 1, 11, hz + 4)
    d.landmark('hatchling_habitat_niche', 'Hatchling Habitat Niche', 'discovery', (hx - 1, 7, hz), (hx + 6, 11, hz + 6))
    C.lever_seq(d, 'beacon', [(bx - 1, 9, bz)], [C.sound((bx, 9, bz + 4), 'minecraft:item.firecharge.use', 2, 0.6), C.say('The braziers catch. A hollow grinds open in the east wall of the roost.', 'gold'),
                                               C.clear(C.bx((hx - 1, 8, hz + 2), (hx - 1, 11, hz + 4)))])
    d.air(50, 8, 29, 62, 11, 31)
    d.room('R1', 'Ancient Roost', (2, 8, 8), (54, 28, 52))


# ================================================================ R2 Scale Gallery (x62..120, z16..46)
def scales(d):
    d.air(62, 8, 16, 120, 20, 46)
    S.floor_pattern(d, 62, 16, 120, 46, FLOOR, [DNT, PBB, REDB, PBB], 2)
    for x in range(66, 120, 6):
        d.fill(x, 8, 16, x, 19, 16, b('polished_blackstone_bricks')); d.fill(x, 8, 46, x, 19, 46, PBB)
        d.set(x, 17, 17, b('lantern', hanging=True, waterlogged=False)) if False else None
    for x in range(68, 118, 10): d.set(x, 19, 31, b('lantern', hanging=True, waterlogged=False)) if d.get(x, 20, 31) != kit.AIR else d.set(x, 19, 31, GLOW)
    # Dragonbone Arch (focal): a pair of giant ribs meeting overhead
    ax = 84
    for side in (22, 40):
        for t in range(0, 13):
            y = 8 + t
            z = side + (t // 3 if side == 22 else -(t // 3))
            d.fill(ax, y, z, ax + 1, y, z, bone('y'))
    for z in range(26, 37): d.fill(ax, 19, z, ax + 1, 19, z, bone('z'))
    for z in (26, 36): d.fill(ax, 18, z, ax + 1, 18, z, bone('z'))
    for z in (21, 41): d.fill(ax - 1, 8, z, ax + 2, 8, z, GBS); d.set(ax, 9, z, GLOW)
    d.landmark('dragonbone_arch', 'Dragonbone Arch', 'focal', (ax - 1, 8, 21), (ax + 2, 20, 41))
    # Scale Obelisk (focal): a layered spire of scales
    ox, oz = 100, 31
    for k in range(14):
        r = 3 - k // 5
        d.disc(ox, oz, r, 8 + k, 8 + k, [REDB, PBB, GBS][k % 3])
    d.set(ox, 22, oz, GLOW) if False else d.set(ox, 22, oz, b('end_rod', facing='up'))
    d.landmark('scale_obelisk', 'Scale Obelisk', 'focal', (ox - 4, 8, oz - 4), (ox + 4, 23, oz + 4))
    # Hoard Fragment Cache (discovery): a shallow gold nook under the north wall
    hx = 72
    S.nook(d, hx, 8, 16, 'n', depth=4, w=5, h=4)
    d.fill(hx - 2, 8, 12, hx + 2, 8, 13, GOLD) if False else d.fill(hx - 1, 8, 12, hx + 1, 8, 13, GOLD)
    d.add_chest((hx, 9, 12), 'south', 'loot'); d.set(hx - 2, 8, 13, b('candle', candles=3, lit=True, waterlogged=False))
    d.landmark('hoard_fragment_cache', 'Hoard Fragment Cache', 'discovery', (hx - 2, 8, 12), (hx + 2, 11, 16))
    # Wyrm Trial (interactive): a plate in a ring of scale pillars
    px, pz = 108, 38
    d.disc(px, pz, 4, FLOOR, FLOOR, GBS); d.disc(px, pz, 1, FLOOR, FLOOR, GOLD)
    d.set(px, 8, pz, b('heavy_weighted_pressure_plate', power=0))
    for (x, z) in S.pts_ring(px, pz, 4, 6): d.fill(x, 8, z, x, 11, z, PBB); d.set(x, 12, z, GLOW)
    d.landmark('wyrm_trial', 'Wyrm Trial', 'interactive', (px - 5, 7, pz - 5), (px + 5, 12, pz + 5))
    C.trial(d, 'wyrm_trial', (px, 8, pz), 'R2',
            [{'say': 'The scales rattle. Something old stirs in the roost.', 'spawns': [C.mob('minecraft:zombie', 3, 'Scale Thrall'), C.mob('minecraft:skeleton', 2, 'Wyrmling Archer')]},
             {'say': 'Smoke rolls over the circle.', 'spawns': [C.mob('minecraft:zombie', 4, 'Scale Thrall'), C.mob('minecraft:blaze', 1, 'Ember Wyrmling')]}],
            [C.say('The roar dies. A Hoard Coin rolls to a stop on the plate.', 'gold'), C.give_key('hoard_coin', (px, 8, pz))])
    # Scale Alignment (interactive): four levers under gold (on) or black (off) scale clues against the south wall
    pats = [False, True, True, False]
    lv = []
    for k, x in enumerate((74, 79, 92, 97)):
        d.set(x, 8, 46, lever('wall', 'north'))
        d.fill(x, 9, 46, x, 10, 46, kit.AIR)
        d.set(x, 11, 46, GOLD if pats[k] else BS)
        lv.append(((x, 8, 46), pats[k]))
    d.landmark('scale_alignment', 'Scale Alignment', 'interactive', (72, 7, 43), (99, 12, 46))
    d.air(120, 8, 31, 132, 11, 33)
    d.seal('scale_gate', 'minecraft:polished_blackstone_bricks', (122, 8, 31), (123, 11, 33))
    C.toggle(d, 'scale_alignment', lv, [C.clear(C.bx((122, 8, 31), (123, 11, 33))), C.sound((122, 9, 32), 'minecraft:block.stone.break', 2, 0.5),
                                        C.say('The scales ring into place. The way to the chasm grinds open.', 'gold')],
             'The scales ring into place.')
    C.encounter(d, 'R2', 'Scales shift underfoot. The gallery remembers something that breathed fire.',
                [C.mob('minecraft:zombie', 3, 'Scale Thrall'), C.mob('minecraft:skeleton', 3, 'Wyrmling Archer'), C.mob('minecraft:blaze', 1, 'Ember Wyrmling')])
    d.room('R2', 'Scale Gallery', (62, 8, 16), (121, 20, 46))


# ================================================================ R3 Spinebridge Chasm (round pit centre 150,32 r26)
def pit(d):
    cx, cz = PIT
    d.disc(cx, cz, 26, 2, 34, kit.AIR)
    d.disc(cx, cz, 27, 1, 1, OBS)
    d.air(120, 8, 31, 126, 11, 33); d.air(120, 8, 28, 124, 11, 36)
    d.air(174, 8, 28, 180, 11, 36)
    # the central spire island (top feet y8) and two spans of vertebrae at z31..33
    d.disc(cx, cz, 5, 1, 7, PBB); d.disc(cx, cz, 5, 7, 7, DNT)
    for k in range(6):
        d.fill(cx, 8, cz, cx, 8 + 2 * k, cz, bone('y')) if False else None
    d.fill(cx - 1, 8, cz - 1, cx + 1, 15, cz + 1, bone('y')); d.fill(cx, 16, cz, cx, 20, cz, bone('y'))
    for (dx, dz) in ((2, 0), (-2, 0), (0, 2), (0, -2)): d.fill(cx + dx, 8, cz + dz, cx + dx, 10, cz + dz, bone('y'))
    d.set(cx, 21, cz, GLOW)
    for (x0, x1) in ((124, 144), (156, 176)):
        for x in range(x0, x1 + 1):
            vert = (x - x0) % 4 in (0, 1)
            d.fill(x, 7, 31, x, 7, 33, bone('x') if vert else PBB)
            if vert:
                d.fill(x, 8, 30, x, 9, 30, bone('y')); d.fill(x, 8, 34, x, 9, 34, bone('y'))
                d.set(x, 10, 31, bone('y')) if False else None
    # ladders on the pit wall beside each end
    for y in range(2, 8): d.set(125, y, 29, kit.ladder('east')); d.set(175, y, 35, kit.ladder('west'))
    # Shattered Hoard Gate (focal) at the far (east) rim
    gx = 178
    d.fill(gx, 8, 27, gx, 17, 28, GBS); d.fill(gx, 8, 36, gx, 17, 37, GBS)
    d.fill(gx, 18, 27, gx, 19, 37, GBS); d.air(gx, 14, 30, gx, 19, 34)
    d.set(gx, 12, 28, GOLD); d.set(gx, 15, 37, GOLD); d.fill(gx, 8, 26, gx, 10, 26, GOLD)
    S.rubble(d, gx - 1, 24, gx + 3, 40, 8, [GBS, PBB, GOLD, CBB], 14)
    d.landmark('shattered_hoard_gate', 'Shattered Hoard Gate', 'focal', (gx - 1, 8, 26), (gx + 1, 19, 38))
    # Hidden Spine Tunnel (discovery): a crawl-way off the pit floor to the south, lined with vertebrae
    d.air(145, 2, 58, 155, 5, 62)
    d.air(148, 2, 58, 152, 5, 59)
    d.add_chest((150, 2, 62), 'north', 'loot')
    for x in range(146, 155, 2): d.fill(x, 2, 60, x, 4, 60, bone('y'))
    d.set(150, 2, 60, b('candle', candles=2, lit=True, waterlogged=False))
    d.landmark('hidden_spine_tunnel', 'Hidden Spine Tunnel', 'discovery', (145, 2, 56), (155, 5, 62))
    for (x, z) in S.pts_ring(cx, cz, 25, 12, 0.3): d.set(x, 18, z, GLOW)
    # fire breath vents along both spans
    cells = [[132, 8, 28], [138, 8, 36], [164, 8, 28], [170, 8, 36]]
    for c in cells: d.set(c[0], c[1] - 1, c[2], b('magma_block')) if False else None
    C.crumble(d, 'spine_crumble', C.bx((124, 7, 31), (176, 7, 33)), only='bone_block', delay=26, restore=170)
    C.pulse(d, 'fire_breath', cells=cells, period=96, warn=34, radius=1.8, damage=2.5, kind='fire', fire=60, height=3.0, stagger=24,
            warnParticle='minecraft:smoke', particle='minecraft:flame', count=22, sound='minecraft:entity.blaze.shoot', vol=1.0, pitch=0.6)
    d.room('R3', 'Spinebridge Chasm', (124, 2, 14), (180, 34, 52))


# ================================================================ R4 Wyrm Court (centre 204,32 r22)
def court(d):
    cx, cz = COURT
    d.air(176, 8, 29, 184, 11, 35)
    d.disc(cx, cz, 22, 8, 28, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, DNT); d.disc(cx, cz, 18, FLOOR, FLOOR, REDB, rim=15); d.disc(cx, cz, 12, FLOOR, FLOOR, PBB, rim=10)
    for (x, z) in S.pts_ring(cx, cz, 21, 14, 0.2): d.fill(x, 8, z, x, 22, z, PBB); d.set(x, 23, z, GLOW)
    # Wyrm Statue (focal): a coiled serpent in dark scales with a head rearing at the centre
    for t in range(0, 110):
        a_ = t * 0.115
        r = 9 - t * 0.055
        x, z = int(round(cx + r * math.cos(a_))), int(round(cz + r * math.sin(a_)))
        y = 8 + int(t * 0.04)
        d.fill(x - 1, y, z - 1, x + 1, y + 1, z + 1, [PBB, REDB, GBS][(t // 6) % 3])
    d.fill(cx - 1, 11, cz - 1, cx + 1, 18, cz + 1, PBB)
    d.fill(cx - 2, 18, cz - 1, cx + 2, 20, cz + 1, GBS)
    d.set(cx - 2, 19, cz - 2, GLOW); d.set(cx + 2, 19, cz - 2, GLOW)
    d.fill(cx - 1, 21, cz - 1, cx + 1, 21, cz - 1, bone('x'))
    d.landmark('wyrm_statue', 'Wyrm Statue', 'focal', (cx - 10, 8, cz - 10), (cx + 10, 22, cz + 10))
    # Ancient Seal Control (interactive): two levers on a pillar; Sealed Chronicle Vault (discovery) behind the north wall
    lx, lz = cx - 16, cz - 12
    d.fill(lx, 8, lz, lx, 11, lz, OBS); d.fill(lx + 3, 8, lz, lx + 3, 11, lz, OBS)
    d.set(lx - 1, 9, lz, lever('wall', 'west')) if False else d.set(lx + 1, 9, lz, lever('wall', 'east')) if False else None
    d.fill(lx - 1, 8, lz - 1, lx + 4, 11, lz - 1, PBB)
    d.set(lx, 9, lz, lever('wall', 'south')); d.set(lx + 3, 9, lz, lever('wall', 'south'))
    d.fill(lx - 1, 8, lz, lx - 1, 11, lz, PBB) if False else None
    d.landmark('ancient_seal_control', 'Ancient Seal Control', 'interactive', (lx - 1, 8, lz - 1), (lx + 4, 12, lz + 1))
    sx, sz = cx - 4, 8
    d.air(sx, 8, 3, sx + 8, 12, 8)
    d.fill(sx, 7, 3, sx + 8, 7, 8, DNT)
    d.add_chest((sx + 4, 8, 4), 'south', 'loot'); d.set(sx + 1, 8, 4, b('lectern', facing='east', powered=False)); d.set(sx + 7, 8, 4, b('lectern', facing='west', powered=False))
    d.fill(sx + 2, 8, 7, sx + 6, 8, 7, b('bookshelf')); d.fill(sx + 2, 9, 7, sx + 6, 10, 7, b('bookshelf'))
    d.seal('chronicle_wall', 'minecraft:polished_blackstone_bricks', (sx + 3, 8, 9), (sx + 5, 11, 10))
    d.landmark('sealed_chronicle_vault', 'Sealed Chronicle Vault', 'discovery', (sx, 7, 3), (sx + 8, 12, 10))
    C.lever_seq(d, 'seal_control', [(lx, 9, lz), (lx + 3, 9, lz)], [C.clear(C.bx((sx + 3, 8, 9), (sx + 5, 11, 10))), C.sound((sx + 4, 9, 9), 'minecraft:block.stone.break', 2, 0.5),
                                                                   C.say('The seals turn. A chronicle vault opens in the north wall.', 'gold')],
                on_step=[[C.sound((lx, 9, lz), 'minecraft:block.piston.extend', 1.5, 0.7), C.say('One seal turns. One more.', 'gold')]], jam='The seals are locked. The west lever first.')
    # Hoard Offering (interactive): a bowl in the south; the Hoard Vault gate behind it
    ox, oz = cx, cz + 17
    d.fill(ox, 8, oz, ox, 9, oz, PBB); d.set(ox, 10, oz, GOLD)
    for x in (ox - 2, ox + 2): d.fill(x, 8, oz, x, 11, oz, GBS); d.set(x, 12, oz, GLOW)
    d.landmark('hoard_offering', 'Hoard Offering', 'interactive', (ox - 2, 8, oz - 1), (ox + 2, 12, oz + 1))
    d.air(cx - 1, 8, 54, cx + 1, 11, 66)
    d.seal('hoard_gate', 'minecraft:polished_blackstone_bricks', (cx - 1, 8, 56), (cx + 1, 11, 57))
    C.lock(d, 'offering', C.bx((ox, 10, oz)), 'hoard_coin', [C.clear(C.bx((cx - 1, 8, 56), (cx + 1, 11, 57))), C.sound((cx, 9, 56), 'minecraft:block.stone.break', 2, 0.5),
                                                          C.say('The coin rings in the gold. The vault gate grinds aside.', 'gold')],
           'A bed of gold with a hollow the size of a coin.', 'drops the Hoard Coin among the gold and the vault gate grinds aside.')
    d.air(cx + 20, 8, 31, cx + 38, 11, 33)
    d.seal('arena_door', 'minecraft:polished_blackstone_bricks', (cx + 23, 8, 31), (cx + 24, 11, 33))
    C.lock(d, 'arena_door', C.bx((cx + 23, 8, 31), (cx + 24, 11, 33)), 'wyrm_key', [C.clear(C.bx((cx + 23, 8, 31), (cx + 24, 11, 33))),
                                                                                    C.sound((cx + 23, 9, 32), 'minecraft:entity.ender_dragon.growl', 1.5, 0.8), C.say('The scorched door to the Elder Wyrm Crucible opens.', 'gold')],
           'A scorched door carved with a coiled wyrm, a claw-shaped keyhole.', 'turns the Wyrm Key and the scorched door swings open.')
    C.encounter(d, 'R4', 'The coiled statue seems to watch. Seal levers stand in the north-west; a coin bowl waits in the south.',
                [C.mob('minecraft:zombie', 3, 'Scale Thrall'), C.mob('minecraft:skeleton', 3, 'Wyrmling Archer'), C.mob('minecraft:blaze', 1, 'Ember Wyrmling')])
    d.room('R4', 'Wyrm Court', (182, 8, 3), (226, 28, 54))


# ================================================================ R5 Hoard Vault (x188..226, z67..100)
def hoard(d):
    cx = COURT[0]
    d.air(188, 8, 67, 224, 20, 100)
    d.air(cx - 1, 8, 58, cx + 1, 11, 66)
    S.floor_pattern(d, 188, 67, 224, 100, FLOOR, [DNT, GBS], 2)
    for k in range(18):
        x, z = d.rng.randint(190, 222), d.rng.randint(70, 96)
        r = d.rng.randint(2, 4)
        d.disc(x, z, r, 8, 8 + r // 2, GOLD if k % 3 else b('gold_block'))
    for x in range(192, 222, 8):
        d.fill(x, 8, 68, x + 1, 14, 69, PBB); d.set(x, 15, 68, GLOW)
    d.air(cx - 3, 8, 94, cx + 3, 12, 99)
    d.add_chest((cx, 8, 97), 'north', 'key', key='wyrm_key')
    d.fill(cx - 2, 8, 98, cx + 2, 10, 99, GOLD); d.fill(cx - 1, 8, 98, cx + 1, 9, 99, kit.AIR)
    d.room('R5', 'Hoard Vault', (188, 8, 58), (224, 20, 100))


# ================================================================ R6 Elder Wyrm Crucible (centre 262,32 r22)
def crucible(d):
    cx, cz = ARENA
    d.air(226, 8, 31, 242, 11, 33)
    d.disc(cx, cz, 22, 8, 32, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, DNT); d.disc(cx, cz, 18, FLOOR, FLOOR, REDB, rim=14); d.disc(cx, cz, 7, FLOOR, FLOOR, GBS, rim=4)
    d.disc(cx, cz, 4, 8, 8, OBS); d.fill(cx - 1, 9, cz - 1, cx + 1, 10, cz + 1, GOLD)
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 14, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 24, z + 1, PBB)
        for yy in range(25, 28): d.set(x, yy, z, bone('y')) if (yy - 25) % 2 == 0 else d.set(x + 1, yy, z + 1, bone('y'))
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, GOLD); targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): d.fill(x, 8, z, x, 20, z, OBS); d.set(x, 21, z, CRY)
    C.boss(d, 'elder_wyrm', 'R6', 'minecraft:wither_skeleton', 'The Elder Wyrm', (cx, 9, cz), targets=targets,
           on_block='minecraft:gold_block', off_block='minecraft:blackstone', health=5.0, scale=2.4, color='RED',
           equipment={'mainhand': 'minecraft:stone_sword', 'head': 'minecraft:netherite_helmet', 'chest': 'minecraft:netherite_chestplate'},
           intro='The Elder Wyrm uncoils. Break the three gold-wards on the pillars to crack its scales.',
           death='The Elder Wyrm collapses into ash and bone.', break_text='The wards break! The Elder Wyrm is bare!',
           adds=[C.mob('minecraft:skeleton', 2, 'Wyrmling Archer') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('The Wyrm roars. Smoke fills the crucible.', 'red'), C.spawn('minecraft:skeleton', 2, 'R6', 'Wyrmling Archer')]},
                   {'at': 0.33, 'effects': [C.say('The Wyrm calls the roost.', 'red'), C.spawn('minecraft:zombie', 3, 'R6', 'Scale Thrall'), C.spawn('minecraft:blaze', 1, 'R6', 'Ember Wyrmling')]}])
    C.pulse(d, 'wyrm_breath', box=C.bx((244, 8, 10), (284, 32, 54)), period=62, warn=28, count=2, radius=2.0, damage=3.0, kind='fire', fire=80,
            warnParticle='minecraft:smoke', particle='minecraft:flame', count_=0, sound='minecraft:entity.blaze.shoot', vol=1.0, pitch=0.6,
            after='boss_awake:elder_wyrm', until='boss:elder_wyrm', scatter=True, **{'target': 'players'})
    C.finish(d, 'elder_wyrm', 'The Elder Wyrm Roost. Align the scales, cross the spine, and bring a coin to the court.', 'The roost cools. The crucible closes shortly.')
    d.room('R6', 'Elder Wyrm Crucible', (244, 8, 10), (284, 33, 54))
