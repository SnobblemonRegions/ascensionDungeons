"""The Titan's Cairn: the Rock dungeon (id `rock`).

Rooms: Mineral Habitat -> Fossil Gallery -> Boulder Canyon -> Quarry Court -> Geode Vault; arena Titan's Cairn.
Layout : the Boulder Canyon is a winding S-curved gorge; the Quarry Court is a terraced PIT (rim, two terraces, a floor) entered by cut ramps, with an
         underpass ditch from the pit floor to the vault.
Hazard : Boulder Canyon - boulders drop onto whoever walks the gorge after a telegraph of dust (heavy damage, quick to dodge).
Puzzle : the Quarry Route Control (two levers in order) clears the rockfall that seals the canyon; the Resonance Trial yields a Resonant Ore, offered at the pit
         floor's Mineral Offering to open the Geode Vault; its Titan Key opens the arena. The Stone Alignment levers open the Hidden Scree Tunnel.
Boss   : the Titan (ravager) behind three amethyst wards; boulders fall on the cairn while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever
import numpy as np

W, H, L = 300, 46, 140
FLOOR = 7
STN, COB, AND, DIO, TUFF = b('stone'), b('cobblestone'), b('andesite'), b('diorite'), b('tuff')
GRAN, DRIP, CAL, AME, BUD = b('granite'), b('dripstone_block'), b('calcite'), b('amethyst_block'), b('budding_amethyst')
SBR, MCOB, GLOW, BONE = b('stone_bricks'), b('mossy_cobblestone'), b('glowstone'), b('bone_block', axis='y')
CRYS = b('amethyst_cluster', facing='up', waterlogged=False)
COURT, ARENA = (214, 32), (272, 32)


def build():
    d = kit.Dungeon('rock', "The Titan's Cairn", W, H, L, biome='minecraft:stony_peaks')
    d.loot_table = 'ascensiondungeons:chests/painted_badlands'
    d.shell = ('stone', 'cobblestone', 'andesite', 'diorite', 'tuff', 'granite', 'dripstone_block', 'calcite', 'stone_bricks')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, STN)
    C.key(d, 'resonant_ore', 'minecraft:raw_iron', 'Resonant Ore', 'It hums at one note. The Mineral Offering on the quarry floor wants it.')
    C.key(d, 'titan_key', 'minecraft:tripwire_hook', 'Titan Key', 'Opens the cairn-door to the Titan\'s Cairn.')
    habitat(d); fossils(d); canyon(d); quarry(d); geode(d); cairn(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R5', False), ('route', 'R3', True), ('route', 'R5', False), ('offering', 'R5', True),
                     ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, STN, [(COB, 0.14), (AND, 0.14), (TUFF, 0.08), (DIO, 0.05), (GRAN, 0.05)])
    return d


# ================================================================ R1 Mineral Habitat (cave around 24,30)
def habitat(d):
    d.ellipsoid(24, 15, 30, 22, 14, 19, kit.AIR)
    d.fill(2, 0, 10, 48, FLOOR, 52, STN)
    for x in range(3, 48):
        for z in range(11, 52):
            if d.get(x, 8, z) == kit.AIR: d.set(x, FLOOR, z, d.rng.choice([STN, COB, AND, STN, TUFF]))
    d.entry = (7, 8, 30)
    for (x, z, h) in [(10, 14, 9), (38, 14, 8), (40, 44, 7), (12, 46, 8)]: S.spire(d, x, z, 8, h, 2, DRIP)
    # Balanced Stone Cairn (focal): boulders of shrinking size stacked impossibly high
    bx, bz = 26, 30
    r = 5; y = 8
    for k, mat in enumerate((AND, GRAN, DIO, COB, TUFF, STN)):
        d.ellipsoid(bx + (k % 2), y + r - 1, bz + (k % 3) - 1, r, max(2, r - 1), r, mat, only_air=True)
        y += 2 * r - 3; r = max(2, r - 1)
    d.set(bx, y + 1, bz, GLOW)
    d.landmark('balanced_stone_cairn', 'Balanced Stone Cairn', 'focal', (bx - 6, 8, bz - 6), (bx + 7, y + 3, bz + 6))
    # Abandoned Survey Camp (discovery)
    cx, cz = 12, 14
    S.pyramid(d, cx, cz, 8, 3, [b('orange_wool'), b('white_wool')]); d.air(cx - 1, 8, cz - 1, cx + 1, 9, cz + 1)
    d.set(cx + 4, 8, cz, b('cartography_table')); d.set(cx + 4, 8, cz + 2, b('crafting_table')); d.add_chest((cx, 8, cz), 'east', 'loot')
    d.set(cx + 3, 8, cz - 2, b('campfire', facing='north', lit=False, signal_fire=False, waterlogged=False)); d.set(cx - 2, 8, cz + 2, b('barrel', facing='up', open=False))
    d.landmark('abandoned_survey_camp', 'Abandoned Survey Camp', 'discovery', (cx - 3, 8, cz - 3), (cx + 5, 11, cz + 3))
    # Geode Habitat Pocket (discovery): a small amethyst-lined pocket in the south wall
    gx, gz = 36, 46
    d.ellipsoid(gx, 9, gz + 4, 4, 3, 4, kit.AIR); d.air(gx - 1, 8, gz - 1, gx + 1, 10, gz + 2)
    d.fill(gx - 5, 5, gz - 1, gx + 5, 7, gz + 9, STN)
    for dx in range(-5, 6):
        for dz in range(-1, 10):
            for dy in range(0, 6):
                x, y2, z = gx + dx, 8 + dy, gz + dz
                if d.get(x, y2, z) == STN and any(d.get(x + a, y2 + b_, z + c) == kit.AIR for a, b_, c in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
                    d.set(x, y2, z, AME if d.rng.random() < 0.75 else BUD)
    for k in range(5): d.set(gx - 2 + k, 8, gz + 4 + (k % 2), CRYS)
    d.add_chest((gx, 8, gz + 6), 'north', 'loot')
    d.landmark('geode_habitat_pocket', 'Geode Habitat Pocket', 'discovery', (gx - 5, 7, gz - 1), (gx + 5, 12, gz + 9))
    # Resonance Trial (interactive): a plate between tuning-fork pillars
    px, pz = 40, 26
    d.disc(px, pz, 4, FLOOR, FLOOR, SBR); d.disc(px, pz, 1, FLOOR, FLOOR, b('iron_block'))
    d.set(px, 8, pz, b('stone_pressure_plate', powered=False))
    for (x, z) in S.pts_ring(px, pz, 4, 6): d.fill(x, 8, z, x, 10, z, AND); d.set(x, 11, z, b('note_block'))
    d.landmark('resonance_trial', 'Resonance Trial', 'interactive', (px - 5, 7, pz - 5), (px + 5, 11, pz + 5))
    C.trial(d, 'resonance_trial', (px, 8, pz), 'R1',
            [{'say': 'The pillars begin to ring. The cave answers.', 'spawns': [C.mob('minecraft:zombie', 3, 'Tumble Husk'), C.mob('minecraft:skeleton', 2, 'Scree Archer')]},
             {'say': 'The note climbs.', 'spawns': [C.mob('minecraft:zombie', 4, 'Tumble Husk'), C.mob('minecraft:spider', 2, 'Cave Crawler')]}],
            [C.say('The ringing stops on a clean note. A lump of Resonant Ore rests on the plate.', 'gold'), C.give_key('resonant_ore', (px, 8, pz))])
    # Excavator Station (interactive): a hut with picks and a hard hat's worth of stamina
    sx, sz = 16, 44
    S.hut(d, sx - 3, sz - 3, sx + 3, sz + 3, 8, 4, SBR, b('stone_slab', type='bottom', waterlogged=False) if False else STN, door='n', floor=COB)
    d.set(sx - 2, 8, sz + 2, b('stonecutter', facing='north')); d.set(sx + 2, 8, sz + 2, b('blast_furnace', facing='north', lit=False)); d.set(sx, 8, sz + 2, b('grindstone', face='floor', facing='north'))
    d.set(sx, 11, sz, b('lantern', hanging=True, waterlogged=False))
    d.landmark('excavator_station', 'Excavator Station', 'interactive', (sx - 4, 7, sz - 4), (sx + 4, 12, sz + 4))
    C.zone(d, 'excavator_haste', C.bx((sx - 3, 8, sz - 3), (sx + 3, 11, sz + 3)), 'minecraft:haste', 1, 400, 100)
    C.zone(d, 'excavator_rest', C.bx((sx - 3, 8, sz - 3), (sx + 3, 11, sz + 3)), 'minecraft:regeneration', 0, 80, 40)
    d.air(40, 8, 29, 62, 11, 31)
    d.room('R1', 'Mineral Habitat', (2, 8, 10), (50, 28, 52))


# ================================================================ R2 Fossil Gallery (x62..120, z18..44)
def fossils(d):
    d.air(62, 8, 18, 120, 20, 44)
    S.floor_pattern(d, 62, 18, 120, 44, FLOOR, [STN, SBR, COB], 3)
    for x in range(66, 120, 8):
        d.fill(x, 8, 18, x, 19, 18, AND); d.fill(x, 8, 44, x, 19, 44, AND)
    # fossils set into the north wall: skeleton silhouettes in bone block
    for k in range(4):
        x0 = 68 + k * 12
        d.fill(x0, 9, 17, x0 + 8, 9, 17, BONE)
        for i in range(0, 9, 2): d.fill(x0 + i, 10, 17, x0 + i, 13 - abs(i - 4) // 2, 17, BONE)
        d.fill(x0 + 8, 10, 17, x0 + 10, 12, 17, BONE) if k % 2 == 0 else None
    # Fossil Rib Arch (focal): giant ribs meeting overhead across the hall
    ax = 90
    for side in (22, 40):
        for t in range(0, 12):
            d.fill(ax, 8 + t, side + (t // 3 if side == 22 else -(t // 3)), ax + 1, 8 + t, side + (t // 3 if side == 22 else -(t // 3)), BONE)
    for z in range(26, 37): d.fill(ax, 19, z, ax + 1, 19, z, BONE)
    for z in (22, 40): d.fill(ax - 1, 8, z, ax + 2, 8, z, STN); d.set(ax, 9, z, GLOW)
    d.landmark('fossil_rib_arch', 'Fossil Rib Arch', 'focal', (ax - 1, 8, 21), (ax + 2, 20, 41))
    # Fossil Cache (discovery): a dig site in the south-west corner
    fx, fz = 70, 40
    d.fill(fx - 3, 7, fz - 2, fx + 3, 7, fz + 2, b('coarse_dirt')); d.fill(fx - 2, 7, fz - 1, fx + 2, 7, fz + 1, b('gravel'))
    d.set(fx - 1, 8, fz, BONE); d.set(fx + 1, 8, fz, BONE); d.set(fx, 8, fz - 1, b('suspicious_gravel')) if False else None
    d.add_chest((fx, 8, fz + 1), 'north', 'loot'); d.fill(fx - 3, 8, fz - 2, fx - 3, 9, fz + 2, b('oak_fence')); d.fill(fx + 3, 8, fz - 2, fx + 3, 9, fz + 2, b('oak_fence'))
    d.set(fx, 8, fz - 2, b('lantern', hanging=False, waterlogged=False))
    d.landmark('fossil_cache', 'Fossil Cache', 'discovery', (fx - 3, 7, fz - 2), (fx + 3, 9, fz + 2))
    # Quarry Route Control (interactive): two levers in order on the east end clear the rockfall
    vx = 114
    d.fill(vx, 8, 26, vx + 1, 10, 26, b('iron_block')) if False else None
    for k, z in enumerate((24, 38)):
        d.fill(vx, 8, z - 1, vx + 1, 11, z + 1, AND)
        C.wall_lever(d, vx - 1, 9, z, 'e')
    d.landmark('quarry_route_control', 'Quarry Route Control', 'interactive', (vx - 2, 8, 23), (vx + 1, 12, 39))
    d.air(120, 8, 29, 130, 11, 35)
    d.seal('rockfall', 'minecraft:cobblestone', (122, 8, 29), (124, 11, 35))
    C.lever_seq(d, 'route', [(vx - 1, 9, 24), (vx - 1, 9, 38)], [C.clear(C.bx((122, 8, 29), (124, 11, 35))), C.sound((123, 9, 32), 'minecraft:block.stone.break', 2, 0.5),
                                                              C.say('Winches drag the rockfall aside. A gorge winds on beyond.', 'gray')],
                on_step=[[C.sound((114, 9, 24), 'minecraft:block.piston.extend', 1.5, 0.7), C.say('A winch ratchets. The second lever.', 'gray')]], jam='The winch is jammed. The north lever first.')
    C.encounter(d, 'R2', 'Bones jut from the walls. Some of what is in here is not a fossil.',
                [C.mob('minecraft:zombie', 3, 'Tumble Husk'), C.mob('minecraft:skeleton', 3, 'Scree Archer'), C.mob('minecraft:spider', 2, 'Cave Crawler')])
    d.room('R2', 'Fossil Gallery', (62, 8, 18), (121, 20, 44))


# ================================================================ R3 Boulder Canyon (winding gorge x124..188)
def canyon(d):
    for x in range(122, 190):
        zc = 32 + int(round(7 * math.sin((x - 124) / 9.0)))
        d.air(x, 8, zc - 3, x, 22, zc + 3)
        d.fill(x, 7, zc - 3, x, 7, zc + 3, d.rng.choice([STN, COB]))
        if d.rng.random() < 0.3: d.set(x, 8, zc + d.rng.choice([-3, 3]), d.rng.choice([COB, AND, TUFF]))
    # boulders wedged in the walls (decor)
    for x in range(126, 186, 7):
        zc = 32 + int(round(7 * math.sin((x - 124) / 9.0)))
        for sgn in (-1, 1):
            d.ellipsoid(x, 17, zc + sgn * 4, 2, 3, 2, d.rng.choice([COB, AND, GRAN]), only_air=True) if d.rng.random() < 0.6 else None
    for x in range(130, 188, 10): d.set(x, 22, 32 + int(round(7 * math.sin((x - 124) / 9.0))), GLOW)
    # Petrified Colossus (focal): a stone giant sitting in a widened chamber at the end of the gorge
    cx, cz = 180, 32
    d.air(172, 8, 22, 188, 24, 42)
    d.fill(172, 7, 22, 188, 7, 42, STN)
    d.fill(cx - 3, 8, cz - 4, cx + 3, 8, cz + 4, SBR)
    d.fill(cx - 2, 9, cz - 3, cx + 2, 13, cz - 1, STN); d.fill(cx - 2, 9, cz + 1, cx + 2, 13, cz + 3, STN)         # legs
    d.fill(cx - 2, 14, cz - 3, cx + 2, 20, cz + 3, AND)                                                            # torso
    d.fill(cx - 4, 16, cz - 3, cx - 3, 20, cz + 3, AND); d.fill(cx + 3, 16, cz - 3, cx + 4, 20, cz + 3, AND)       # shoulders
    d.fill(cx - 5, 12, cz - 4, cx - 4, 18, cz - 3, STN); d.fill(cx - 5, 12, cz + 3, cx - 4, 18, cz + 4, STN)       # arms
    d.fill(cx - 1, 21, cz - 1, cx + 1, 23, cz + 1, GRAN)
    d.set(cx - 2, 22, cz, GLOW) if False else None
    d.landmark('petrified_colossus', 'Petrified Colossus', 'focal', (cx - 6, 8, cz - 5), (cx + 5, 24, cz + 5))
    # Miner's Strongbox (discovery): a locker niche in the gorge wall near the start
    mx, mz = 134, 25
    zc = 32 + int(round(7 * math.sin((mx - 124) / 9.0)))
    S.nook(d, mx, 8, zc - 3, 'n', depth=4, w=3, h=3)
    d.add_chest((mx, 8, zc - 6), 'south', 'loot'); d.set(mx - 1, 8, zc - 6, b('barrel', facing='south', open=False)); d.set(mx + 1, 8, zc - 5, b('lantern', hanging=False, waterlogged=False))
    d.fill(mx - 1, 8, zc - 7, mx + 1, 10, zc - 7, STN) if False else None
    d.fill(mx - 2, 7, zc - 7, mx + 2, 7, zc - 4, SBR); d.fill(mx - 2, 8, zc - 7, mx - 2, 10, zc - 7, GRAN); d.fill(mx + 2, 8, zc - 7, mx + 2, 10, zc - 7, GRAN)
    d.landmark('miner_s_strongbox', "Miner's Strongbox", 'discovery', (mx - 2, 7, zc - 7), (mx + 2, 10, zc - 3))
    d.air(188, 8, 29, 198, 11, 35)
    C.pulse(d, 'boulder_fall', box=C.bx((124, 8, 20), (170, 22, 44)), period=70, warn=34, count=1, radius=2.0, damage=4.5, kind='fall', height=4.0,
            warnParticle='minecraft:poof', particle='minecraft:explosion', sound='minecraft:block.gravel.break', vol=1.5, pitch=0.5, push=[0.0, -0.2, 0.0],
            scatter=False, **{'target': 'players'})
    d.room('R3', 'Boulder Canyon', (124, 8, 20), (190, 24, 44))


# ================================================================ R4 Quarry Court (terraced pit centre 214,32 r22)
def quarry(d):
    cx, cz = COURT
    d.air(188, 8, 29, 192, 11, 35)
    xs, zs = np.arange(W)[None, :], np.arange(L)[:, None]
    r2 = (xs - cx) ** 2 + (zs - cz) ** 2
    disc = r2 <= 22 * 22 + 0.5
    for y in range(2, 28): d.a[y][disc] = d.pid(kit.AIR)
    rings = [(18, 7), (13, 5), (8, 3), (0, 1)]                               # (inner radius, top block y): feet = top+1
    for inner, top in rings:
        m = disc & (r2 >= inner * inner) if inner else disc
        for y in range(1, top + 1): d.a[y][m & (d.a[y] == d.pid(kit.AIR))] = d.pid(STN)
    for inner, top in ((18, 7), (13, 5), (8, 3), (0, 1)):                      # recompute so each ring ends at its own height
        pass
    # re-do rings properly: carve down to each ring's height inside its radius range
    for y in range(2, 8): d.a[y][disc] = d.pid(kit.AIR)
    def ring_mask(lo, hi): return disc & (r2 >= lo * lo) & (r2 < hi * hi if hi else True)
    for lo, hi, top in ((18, 99, 7), (13, 18, 5), (8, 13, 3), (0, 8, 1)):
        m = ring_mask(lo, hi)
        for y in range(1, top + 1): d.a[y][m] = d.pid(d.rng.choice([STN, COB, AND]) if y == top else STN)
    # cut ramps (3 wide) down the north side: intermediate steps at r=17, 12, 7
    for (r, top) in ((17, 6), (12, 4), (7, 2)):
        for dx in (-1, 0, 1):
            for dz in range(0, 1):
                x, z = cx + dx, cz - r
                for y in range(1, top + 1): d.set(x, y, z, STN)
                for y in range(top + 1, 12): d.set(x, y, z, kit.AIR)
    d.fill(cx - 1, 7, cz - 22, cx + 1, 7, cz - 18, STN)
    # underpass ditch from the pit floor to the vault, roofed beyond the rim
    d.air(cx - 1, 2, cz + 7, cx + 1, 5, 66)
    d.fill(cx - 1, 1, cz + 7, cx + 1, 1, 66, STN)
    d.fill(cx - 1, 6, cz + 18, cx + 1, 7, cz + 22, STN)
    for (x, z) in S.pts_ring(cx, cz, 21, 16, 0.2): d.fill(x, 8, z, x, 20, z, AND); d.set(x, 16, z, GLOW)
    # Quarry Crane (focal): a tower on the west rim with a jib reaching over the pit
    kx, kz = cx - 21, cz
    d.fill(kx - 1, 8, kz - 1, kx + 1, 22, kz + 1, b('iron_block') if False else AND)
    d.fill(kx + 2, 21, kz, cx - 4, 21, kz, b('iron_block')); d.fill(kx + 2, 22, kz - 1, cx - 4, 22, kz + 1, AND) if False else None
    d.fill(cx - 6, 14, kz, cx - 6, 20, kz, b('chain', axis='y', waterlogged=False)); d.fill(cx - 7, 12, kz - 1, cx - 5, 13, kz + 1, b('iron_block'))
    d.fill(kx, 23, kz, kx, 24, kz, b('iron_bars')); d.set(kx, 25, kz, GLOW)
    d.landmark('quarry_crane', 'Quarry Crane', 'focal', (kx - 2, 8, kz - 2), (cx - 4, 25, kz + 2))
    # Stone Alignment Puzzle (interactive): four levers on the south rim wall under andesite (on) / diorite (off); opens the Hidden Scree Tunnel
    pats = [True, False, True, False]
    lv = []
    for k, x in enumerate((cx - 9, cx - 3, cx + 3, cx + 9)):
        z = cz + 19
        d.fill(x - 1, 8, z + 1, x + 1, 11, z + 1, AND) if False else None
        d.fill(x - 1, 8, z + 1, x + 1, 12, z + 1, STN)
        C.wall_lever(d, x, 9, z, 's')
        d.set(x, 11, z, AND if pats[k] else DIO)
        d.set(x - 1, 10, z, GRAN); d.set(x + 1, 10, z, GRAN); d.set(x, 8, z, SBR)
        lv.append(((x, 9, z), pats[k]))
    d.landmark('stone_alignment_puzzle', 'Stone Alignment Puzzle', 'interactive', (cx - 11, 8, cz + 18), (cx + 11, 12, cz + 20))
    sx, sz = cx + 14, cz - 17
    d.air(sx, 8, sz - 6, sx + 6, 11, sz - 2)
    d.air(sx + 2, 8, sz - 1, sx + 4, 11, sz + 2)
    d.fill(sx, 7, sz - 6, sx + 6, 7, sz - 2, STN); d.fill(sx + 2, 7, sz - 1, sx + 4, 7, sz + 2, STN)
    d.add_chest((sx + 3, 8, sz - 6), 'south', 'loot'); d.set(sx + 1, 8, sz - 5, b('lantern', hanging=False, waterlogged=False))
    for x in range(sx + 1, sx + 6, 2): d.set(x, 8, sz - 3, GRAN)
    d.seal('scree_wall', 'minecraft:cobblestone', (sx + 2, 8, sz - 1), (sx + 4, 11, sz))
    for x in (sx + 1, sx + 5): d.fill(x, 8, sz - 5, x, 10, sz - 5, GRAN); d.fill(x, 8, sz - 3, x, 9, sz - 3, COB)
    d.set(sx + 3, 8, sz - 4, b('gravel')); d.set(sx + 3, 7, sz - 4, b('gravel'))
    d.landmark('hidden_scree_tunnel', 'Hidden Scree Tunnel', 'discovery', (sx, 7, sz - 6), (sx + 6, 11, sz + 1))
    C.toggle(d, 'stones', lv, [C.clear(C.bx((sx + 2, 8, sz - 1), (sx + 4, 11, sz))), C.sound((sx + 3, 9, sz - 1), 'minecraft:block.gravel.break', 2, 0.6),
                               C.say('Scree pours away from a hidden tunnel in the north-east wall.', 'gray')],
             'The stones settle on the right note.')
    # Mineral Offering (interactive) on the pit floor; the Geode Vault gate in the ditch
    ox, oz = cx, cz
    d.fill(ox, 2, oz, ox, 3, oz, SBR); d.set(ox, 4, oz, b('iron_block'))
    for (dx, dz) in ((-2, 0), (2, 0)): d.fill(ox + dx, 2, oz + dz, ox + dx, 6, oz + dz, AND); d.set(ox + dx, 7, oz + dz, GLOW) if False else None
    d.landmark('mineral_offering', 'Mineral Offering', 'interactive', (ox - 2, 2, oz - 1), (ox + 2, 6, oz + 1))
    d.seal('geode_gate', 'minecraft:cobblestone', (cx - 1, 2, cz + 27), (cx + 1, 5, cz + 28))
    C.lock(d, 'offering', C.bx((ox, 4, oz)), 'resonant_ore', [C.clear(C.bx((cx - 1, 2, cz + 27), (cx + 1, 5, cz + 28))), C.sound((cx, 3, cz + 27), 'minecraft:block.stone.break', 2, 0.5),
                                                            C.say('The ore rings in its cradle. The gate in the quarry ditch grinds open.', 'gray')],
           'A block of iron with a hollow cut for a lump of ore.', 'sets the Resonant Ore in the cradle and the ditch gate grinds open.')
    d.air(cx + 20, 8, 31, cx + 38, 11, 33)
    d.seal('arena_door', 'minecraft:cobblestone', (cx + 23, 8, 31), (cx + 24, 11, 33))
    C.lock(d, 'arena_door', C.bx((cx + 23, 8, 31), (cx + 24, 11, 33)), 'titan_key', [C.clear(C.bx((cx + 23, 8, 31), (cx + 24, 11, 33))),
                                                                                       C.sound((cx + 23, 9, 32), 'minecraft:block.stone.break', 2, 0.4), C.say('The cairn-door of the Titan\'s Cairn rolls aside.', 'gold')],
           'A door of fitted boulders, a keyhole carved between two of them.', 'turns the Titan Key and the boulder door rolls aside.')
    C.encounter(d, 'R4', 'The quarry steps down into a pit. A ringing block waits on the floor; stone levers line the south wall.',
                [C.mob('minecraft:zombie', 3, 'Tumble Husk'), C.mob('minecraft:skeleton', 3, 'Scree Archer')])
    d.room('R4', 'Quarry Court', (192, 1, 3), (238, 28, 56))


# ================================================================ R5 Geode Vault (spherical geode south of the quarry)
def geode(d):
    cx = COURT[0]
    gz = 84
    d.air(cx - 1, 2, 60, cx + 1, 5, 61)
    d.ellipsoid(cx, 14, gz, 17, 13, 17, CAL); d.ellipsoid(cx, 14, gz, 16, 12, 16, AME)
    d.ellipsoid(cx, 14, gz, 15, 11, 15, kit.AIR)
    d.fill(cx - 17, 0, gz - 17, cx + 17, 7, gz + 17, STN) if False else None
    xs, zs = np.arange(W)[None, :], np.arange(L)[:, None]
    inside = ((xs - cx) / 15.0) ** 2 + ((zs - gz) / 15.0) ** 2 <= 1.0
    for y in range(1, 8): d.a[y][inside] = d.pid(AME if y == 7 and d.rng.random() < 0.5 else CAL if y == 7 else STN)
    d.air(cx - 1, 2, 60, cx + 1, 5, 61)
    d.fill(cx - 1, 1, 60, cx + 1, 1, 61, STN)
    for i in range(1, 7):                                                   # the ditch climbs to the geode floor, one block per cell
        z = 61 + i
        d.fill(cx - 1, 1, z, cx + 1, 1 + i, z, STN)
        d.air(cx - 1, 2 + i, z, cx + 1, 11, z)
    for _ in range(60):
        x, z = d.rng.randint(cx - 13, cx + 13), d.rng.randint(gz - 13, gz + 13)
        if inside[z, x] and d.get(x, 8, z) == kit.AIR and (x - cx) ** 2 + (z - gz) ** 2 > 16: S.crystal(d, x, z, 8, d.rng.randint(2, 5), AME, b('amethyst_block'))
    for k in range(10):
        a_ = k * math.pi / 5
        x, y, z = int(round(cx + 13 * math.cos(a_))), 12 + k % 4, int(round(gz + 13 * math.sin(a_)))
        d.set(x, y, z, BUD)
    d.set(cx, 25, gz, GLOW)
    d.fill(cx - 1, 1, 67, cx + 1, 7, 72, STN); d.air(cx - 1, 8, 67, cx + 1, 11, 72)
    d.add_chest((cx, 8, gz + 11), 'north', 'key', key='titan_key')
    d.fill(cx - 2, 8, gz + 12, cx + 2, 10, gz + 13, AME) if False else d.fill(cx - 2, 8, gz + 12, cx + 2, 9, gz + 13, AME)
    d.landmark('giant_geode', 'Giant Geode', 'focal', (cx - 17, 1, gz - 17), (cx + 17, 27, gz + 17))
    d.room('R5', 'Geode Vault', (cx - 17, 8, 62), (cx + 17, 27, gz + 17))


# ================================================================ R6 Titan's Cairn (centre 272,32 r22)
def cairn(d):
    cx, cz = ARENA
    d.air(236, 8, 31, 252, 11, 33)
    d.disc(cx, cz, 22, 8, 32, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, STN); d.disc(cx, cz, 18, FLOOR, FLOOR, COB, rim=14); d.disc(cx, cz, 9, FLOOR, FLOOR, AND, rim=6)
    # the Cairn: stacked discs of shrinking radius, capped with a glowing stone
    y = 8
    for k, (r, h, mat) in enumerate(((6, 3, GRAN), (5, 3, DIO), (4, 3, AND), (3, 3, COB), (2, 3, TUFF))):
        d.disc(cx, cz, r, y, y + h - 1, mat); y += h
    d.set(cx, y, cz, GLOW)
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 14, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 22, z + 1, AND)
        d.fill(x - 1, 23, z - 1, x + 2, 24, z + 2, COB)
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, AME); targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): d.fill(x, 8, z, x, 18, z, b('stone_bricks')); d.set(x, 19, z, GLOW)
    C.boss(d, 'titan', 'R6', 'minecraft:ravager', 'The Titan', (cx, 9, cz + 9), targets=targets,
           on_block='minecraft:amethyst_block', off_block='minecraft:stone', health=2.8, scale=1.6, color='WHITE',
           intro='The Titan shrugs off a cairn of boulders. Shatter the three amethyst wards on the pillars to crack its stone hide.',
           death='The Titan crumbles back into a heap of stones.', break_text='The wards shatter! The Titan is bare!',
           adds=[C.mob('minecraft:zombie', 2, 'Tumble Husk') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('Boulders drop from the roof.', 'red'), C.spawn('minecraft:skeleton', 2, 'R6', 'Scree Archer')]},
                   {'at': 0.33, 'effects': [C.say('The cairn shakes itself apart.', 'red'), C.spawn('minecraft:zombie', 3, 'R6', 'Tumble Husk')]}])
    C.pulse(d, 'cairn_boulders', box=C.bx((254, 8, 10), (294, 30, 54)), period=60, warn=32, count=2, radius=2.2, damage=4.5, kind='fall', height=4.0,
            warnParticle='minecraft:poof', particle='minecraft:explosion', sound='minecraft:block.gravel.break', pitch=0.5, push=[0.0, -0.2, 0.0],
            after='boss_awake:titan', until='boss:titan', scatter=True, **{'target': 'players'})
    C.finish(d, 'titan', "The Titan's Cairn. Clear the rockfall, brave the gorge, and ring the ore on the quarry floor.", 'The stones settle. The cairn closes shortly.')
    d.room('R6', "Titan's Cairn", (254, 8, 10), (294, 33, 54))
