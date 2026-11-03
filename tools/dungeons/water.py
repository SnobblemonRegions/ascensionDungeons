"""The Drowned Reservoir: the Water dungeon (id `water`).

Rooms (catalogue): Reservoir Grotto -> Aqueduct Gallery -> Canal Crossing -> Tidal Court -> Sunken Archive; arena Drowned Crown Basin.
Hazard   : the canal floods (3 deep, surface level with the court floor) and drains on a timer; an undertow pushes swimmers back.
           Dry niches (reached by full-block ramps; no waterloggable blocks inside the tide box, so draining leaves nothing wet)
           let you wait out the high tide. Both ends of the canal climb OVER the water line, so water never meets open air.
Puzzle   : three sluice levers on an ascending gantry (lowest first) unseal the flooded tunnel to the Sunken Archive; the Archive
           holds the Tide Key (in the lighthouse lantern room) that opens the locked door to the Arena.
Optional : the Pearl Trial (waves) yields a Tide Pearl; offering it at the basin opens the Lost Chart Alcove. A floodgate lever
           stops the tide for good.
Boss     : the Tide Warden (drowned) behind three sea-lantern wards.
"""
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit
from kit import b, stair, slab, lever, water

W, H, L = 160, 40, 144
SB, PB, DP, PR, SL = b('stone_bricks'), b('prismarine_bricks'), b('dark_prismarine'), b('prismarine'), b('sea_lantern')
MSB, CSB, DEEP, COP = b('mossy_stone_bricks'), b('cracked_stone_bricks'), b('deepslate_tiles'), b('waxed_oxidized_cut_copper')
WATER, AIR = water(), kit.AIR
FLOOR = 7            # floor block y; standing feet y = 8
CORAL = ['tube_coral_block', 'brain_coral_block', 'bubble_coral_block', 'fire_coral_block', 'horn_coral_block']
TERRA = ['cyan_terracotta', 'light_blue_terracotta', 'pink_terracotta', 'orange_terracotta', 'yellow_terracotta']
CC = (84, 102)       # Tidal Court centre
AC = (134, 102)      # Arena centre
CX0, CX1, CZ0, CZ1 = 81, 87, 37, 80     # canal channel
PEARL = (13, 28)     # Pearl Trial pedestal
TUNNEL_Z = (101, 103)


def build():
    d = kit.Dungeon('water', 'The Drowned Reservoir', W, H, L, biome='minecraft:lukewarm_ocean')
    d.loot_table = 'ascensiondungeons:chests/flooded_ruins'
    # Cobblemon section: land-capable water-types (the creatures walk on dry cells), AscensionLib tier 3, a CobbleRaids guardian in the arena.
    def pool(names, lo, hi): return [{'species': n, 'weight': 15 if i < 4 else 8, 'min': lo, 'max': hi} for i, n in enumerate(names)]
    d.pokemon = {'tier': 3, 'scaling': {'perPlayer': 2}, 'pools': {
        'wild_low': pool(['psyduck', 'wooper', 'poliwag', 'lotad', 'tympole', 'buizel', 'slowpoke'], 12, 20),
        'wild_mid': pool(['golduck', 'quagsire', 'poliwhirl', 'lombre', 'floatzel', 'palpitoad', 'slowbro'], 22, 30),
        'boss': [{'species': n, 'weight': 10, 'min': 55, 'max': 60} for n in ['swampert', 'empoleon', 'gyarados', 'kingdra']],
        'trial': pool(['wartortle', 'croconaw', 'marshtomp', 'prinplup', 'dewott', 'azumarill', 'seaking'], 26, 34)}}
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, SB)
    xs, zs = np.arange(W)[None, :], np.arange(L)[:, None]

    def ell(cx, cz, rx, rz): return ((xs - cx) / float(rx)) ** 2 + ((zs - cz) / float(rz)) ** 2 <= 1.0

    def layer(y1, y2, mask, s):
        pid = d.pid(s)
        for y in range(y1, y2 + 1): d.a[y][mask] = pid

    def ring_pts(cx, cz, r, n, off=0.0):
        return [(int(round(cx + r * math.cos(off + k * 2 * math.pi / n))), int(round(cz + r * math.sin(off + k * 2 * math.pi / n)))) for k in range(n)]

    lantern = b('lantern', hanging=False, waterlogged=False)
    r1(d, layer, ell, ring_pts)
    r2(d, lantern)
    r3(d, lantern)
    r4(d, layer, ell, ring_pts, lantern)
    r5(d, layer)
    r6(d, layer, ring_pts)
    declare(d)
    weather(d)
    # what must be locked behind what: (state after this mechanic, room, reachable?)
    d.order_rules = [('start', 'R5', False), ('start', 'R6', False), ('sluice', 'R5', True), ('sluice', 'R6', False),
                     ('arena_door', 'R6', True)]
    return d


# ================================================================ R1 Reservoir Grotto (x6..34, z6..34)
def r1(d, layer, ell, ring_pts):
    d.ellipsoid(20, 13, 20, 14, 8, 14, AIR)
    d.fill(6, 0, 6, 34, FLOOR, 34, SB)
    for (cx, cz, h) in [(10, 12, 9), (30, 11, 8), (13, 31, 7), (27, 31, 9), (8, 24, 6), (16, 9, 5)]:
        d.fill(cx, 8, cz, cx + 1, 8 + h, cz + 1, PR)
        d.fill(cx, 8 + h, cz, cx, 8 + h + 1, cz, PB)
    pool = ell(24, 22, 7, 6)
    layer(5, 7, pool, WATER)
    layer(4, 4, pool, SB)
    for x in range(14, 36):
        for z in range(13, 32):
            if pool[z, x]:
                if d.rng.random() < 0.15: d.set(x, 5, z, b('seagrass'))
            elif pool[z - 1:z + 2, x - 1:x + 2].any() and d.get(x, FLOOR, z) == SB:
                d.set(x, FLOOR, z, d.rng.choice([DP, PB, MSB]))
    # Drowned Statue (focal): a robed figure rising from the pool, trident raised, eyes lit
    sx, sz = 24, 22
    d.fill(sx - 1, 5, sz - 1, sx + 1, 7, sz + 1, PB)
    d.fill(sx - 1, 8, sz - 1, sx + 1, 8, sz + 1, DP)
    d.fill(sx - 1, 9, sz, sx - 1, 12, sz, PB); d.fill(sx + 1, 9, sz, sx + 1, 12, sz, PB)
    d.fill(sx - 1, 13, sz - 1, sx + 1, 15, sz + 1, PB)
    d.fill(sx - 2, 14, sz, sx - 2, 14, sz, PB); d.fill(sx + 2, 14, sz, sx + 2, 14, sz, PB)
    d.fill(sx - 3, 15, sz, sx - 3, 17, sz, PB)
    d.fill(sx + 2, 15, sz, sx + 3, 15, sz, PB)
    d.fill(sx - 1, 16, sz - 1, sx + 1, 18, sz + 1, PB)
    d.set(sx - 1, 17, sz - 2, SL); d.set(sx + 1, 17, sz - 2, SL)
    d.fill(sx - 3, 18, sz, sx - 3, 19, sz, PR)
    d.set(sx - 4, 19, sz, PR); d.set(sx - 2, 19, sz, PR)
    d.fill(sx - 2, 13, sz - 2, sx + 2, 13, sz - 2, DP)
    d.landmark('drowned_statue', 'Drowned Statue', 'focal', (sx - 4, 5, sz - 2), (sx + 3, 19, sz + 2))
    # Submerged Habitat Niche (discovery): a flooded alcove in the pool's east wall
    d.fill(32, 5, 21, 34, 6, 23, WATER)
    for (x, z, c) in [(32, 20, 0), (33, 20, 1), (34, 20, 2), (32, 24, 3), (33, 24, 4), (34, 24, 0)]:
        for y in (5, 6): d.set(x, y, z, b(CORAL[c]))
    for (x, z, c) in [(32, 21, 1), (32, 23, 2), (34, 21, 3), (34, 23, 4)]: d.set(x, 7, z, b(CORAL[c]))
    d.set(33, 5, 21, b('sea_pickle', pickles=3, waterlogged=True)); d.set(33, 5, 23, b('sea_pickle', pickles=2, waterlogged=True))
    d.set(32, 5, 22, b('tube_coral_fan', waterlogged=True)); d.set(33, 5, 22, b('brain_coral_fan', waterlogged=True))
    d.add_chest((34, 5, 22), 'west', 'loot', water_=True)
    d.landmark('submerged_habitat_niche', 'Submerged Habitat Niche', 'discovery', (32, 4, 20), (34, 7, 24))
    # Pearl Trial Pool (interactive): ring of water around a pedestal with a pressure plate
    px, pz = PEARL
    d.disc(px, pz, 4, FLOOR, FLOOR, PB)
    d.disc(px, pz, 3, 5, 7, WATER, rim=2)
    d.disc(px, pz, 3, 4, 4, SB, rim=2)
    d.disc(px, pz, 2, 5, 7, PB)
    d.set(px, 8, pz, b('stone_pressure_plate', powered=False))
    for (x, z) in ring_pts(px, pz, 5, 4, 0.7): d.set(x, 8, z, SL)
    d.landmark('pearl_trial_pool', 'Pearl Trial Pool', 'interactive', (px - 5, 4, pz - 5), (px + 5, 9, pz + 5))
    # corridor to the gallery
    d.air(33, 8, 18, 50, 11, 20)
    for x in range(36, 50, 4): d.set(x, 11, 19, SL)
    d.room('R1', 'Reservoir Grotto', (6, 8, 6), (36, 22, 36))
    d.entry = (12, 8, 20)
    d.mark('entry', d.entry)


# ================================================================ R2 Aqueduct Gallery (x50..109, z9..29)
def r2(d, lantern):
    d.air(50, 8, 9, 109, 21, 29)
    for x in range(51, 109):
        for z in range(10, 29):
            if (x // 4 + z // 4) % 2 == 0: d.set(x, FLOOR, z, d.rng.choice([MSB, CSB]))
    for px in range(54, 103, 8):
        d.fill(px, 8, 10, px + 1, 13, 12, PB)
    for k in range(6):
        x0 = 56 + 8 * k
        d.fill(x0, 13, 10, x0 + 5, 13, 12, PB)
        for z in (10, 11, 12):
            d.set(x0, 12, z, stair('prismarine_brick', 'east', top=True)); d.set(x0 + 5, 12, z, stair('prismarine_brick', 'west', top=True))
    d.fill(51, 14, 9, 108, 17, 13, PB)
    d.fill(52, 15, 10, 107, 17, 12, AIR)
    d.fill(52, 15, 10, 75, 16, 12, WATER)
    d.fill(89, 15, 10, 107, 16, 12, WATER)
    d.air(76, 14, 9, 88, 17, 13)                              # the collapse: the channel simply stops
    d.fill(76, 14, 9, 77, 17, 13, PB); d.fill(88, 14, 9, 88, 17, 13, PB)
    d.air(78, 11, 10, 87, 13, 12)                              # piers and lintels broken away
    d.air(78, 14, 9, 87, 17, 13)
    for _ in range(70):
        x, z = d.rng.randint(76, 90), d.rng.randint(10, 22)
        if d.get(x, 8, z) == AIR:
            d.set(x, 8, z, d.rng.choice([PB, CSB, MSB, DP]))
            if d.rng.random() < 0.3 and d.get(x, 9, z) == AIR: d.set(x, 9, z, d.rng.choice([PB, CSB]))
    d.landmark('broken_aqueduct', 'Broken Aqueduct', 'focal', (51, 8, 9), (108, 17, 13))
    for cx in range(56, 106, 10):
        d.fill(cx, 8, 25, cx + 1, 20, 26, PB)
        d.fill(cx - 1, 21, 24, cx + 2, 21, 27, DP)
        d.fill(cx - 1, 8, 24, cx + 2, 8, 24, DP); d.fill(cx - 1, 8, 27, cx + 2, 8, 27, DP)
        d.set(cx + 2, 20, 25, b('lantern', hanging=True, waterlogged=False))
    for x in range(54, 106, 8):
        for z in (15, 21): d.set(x, 21, z, SL)
    for (cx, cz) in [(62, 18), (74, 21), (96, 19), (104, 17)]:
        for dx, dz in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
            d.set(cx + dx, 6, cz + dz, SB); d.set(cx + dx, FLOOR, cz + dz, WATER)
    # Reservoir Wheel (focal): a paddle wheel in an alcove of the south wall, its foot in a catch basin
    wx, wy, wz = 66, 14, 31
    d.air(57, 8, 30, 75, 21, 33)
    d.fill(59, 6, 30, 73, 7, 33, WATER); d.fill(59, 5, 30, 73, 5, 33, SB)
    rim, ring, spoke = b('dark_oak_planks'), b('stripped_dark_oak_log', axis='z'), b('dark_oak_log', axis='z')
    for ang in range(0, 360, 3):
        a = math.radians(ang)
        for r, blk in ((6, rim), (5, ring)):
            x, y = int(round(wx + r * math.cos(a))), int(round(wy + r * math.sin(a)))
            d.set(x, y, wz, blk); d.set(x, y, wz + 1, blk)
    for sp in range(8):
        a = sp * math.pi / 4
        for r in range(0, 6):
            x, y = int(round(wx + r * math.cos(a))), int(round(wy + r * math.sin(a)))
            d.set(x, y, wz, spoke); d.set(x, y, wz + 1, spoke)
    for k in range(16):
        a = k * math.pi / 8 + 0.2
        x, y = int(round(wx + 7 * math.cos(a))), int(round(wy + 7 * math.sin(a)))
        if y >= 8 and d.get(x, y, wz) == AIR:
            d.set(x, y, wz, b('dark_oak_trapdoor', facing='south', half='bottom', open=True, powered=False, waterlogged=False))
    d.fill(wx - 1, wy - 1, wz, wx + 1, wy + 1, wz + 1, COP)
    d.landmark('reservoir_wheel', 'Reservoir Wheel', 'focal', (58, 5, 30), (74, 21, 33))
    # Diver's Cache (discovery): a stone nook in the north-east corner
    d.fill(105, 8, 9, 109, 13, 13, PB)
    d.air(106, 8, 10, 108, 10, 12); d.air(106, 8, 13, 108, 10, 13)
    d.set(107, 11, 11, lantern) if False else None
    d.add_chest((108, 8, 10), 'south', 'loot')
    d.set(106, 8, 10, lantern)
    d.landmark('diver_s_cache', "Diver's Cache", 'discovery', (105, 8, 9), (109, 13, 13))
    d.room('R2', 'Aqueduct Gallery', (50, 8, 9), (109, 22, 36))
    # exit south: a flat corridor into the canal's dry north landing
    d.air(82, 8, 30, 86, 12, 36)


# ================================================================ R3 Canal Crossing (x81..87, z37..80)
def r3(d, lantern):
    d.air(CX0, 8, CZ0, CX1, 13, CZ1)                          # landing air
    d.air(CX0, 5, CZ0, CX1, 7, CZ1)
    # north dam + steps down (tops 8,7,6,5), south steps up (tops 6,7,8): the water line is always below the dry floor
    d.fill(CX0 - 1, 4, CZ0, CX1 + 1, 4, CZ1, DEEP)
    d.fill(CX0, 4, CZ0, CX1, 7, CZ0 + 2, SB)                   # z37..39 flush with the corridor floor (top 8.0)
    d.fill(CX0, 4, CZ0 + 3, CX1, 6, CZ0 + 3, SB)               # z40 top 7.0
    d.fill(CX0, 4, CZ0 + 4, CX1, 5, CZ0 + 4, SB)               # z41 top 6.0
    d.fill(CX0, 4, CZ1 - 5, CX1, 5, CZ1 - 5, SB)               # z75 top 6.0
    d.fill(CX0, 4, CZ1 - 4, CX1, 6, CZ1 - 4, SB)               # z76 top 7.0
    d.fill(CX0, 4, CZ1 - 3, CX1, 7, CZ1, SB)                   # z77..80 top 8.0 (the landing into the court)
    # dry niches reached by 4-step full-block ramps (the last step top 9.0 = niche floor top)
    for zc, side in [(46, 'w'), (58, 'e'), (70, 'w')]:
        if side == 'w':
            d.air(CX0 - 3, 9, zc - 1, CX0 - 1, 11, zc + 1)
            for n in range(1, 5): d.fill(CX0, 5, zc - 5 + n, CX0, 4 + n, zc - 5 + n, SB)
            d.set(CX0 - 2, 9, zc, lantern); d.set(CX0 - 3, 11, zc - 1, SL)
        else:
            d.air(CX1 + 1, 9, zc - 1, CX1 + 3, 11, zc + 1)
            for n in range(1, 5): d.fill(CX1, 5, zc + 5 - n, CX1, 4 + n, zc + 5 - n, SB)
            d.set(CX1 + 2, 9, zc, lantern); d.set(CX1 + 3, 11, zc + 1, SL)
    for z in range(CZ0 + 3, CZ1 - 3, 5):
        d.set(CX0 - 1, 11, z, SL); d.set(CX1 + 1, 11, z, SL)
    for z in range(CZ0, CZ1 + 1, 6): d.fill(CX0, 13, z, CX1, 13, z, PB)
    for x in range(82, 87):
        d.set(x, 12, 33, lantern) if False else None
    # Coral Tunnel (discovery): off the first west niche, a hidden gallery grown over with coral-coloured terracotta
    zt = 46
    d.air(70, 9, zt - 1, CX0 - 4, 11, zt + 1)
    d.air(63, 9, zt - 3, 69, 13, zt + 3)
    for x in range(62, 71):
        for z in range(zt - 4, zt + 5):
            for y in range(9, 15):
                if d.get(x, y, z) != AIR and d.rng.random() < 0.55: d.set(x, y, z, b(d.rng.choice(TERRA)))
    for (x, z) in [(64, zt - 2), (64, zt + 2), (68, zt - 2), (68, zt + 2)]:
        d.set(x, 9, z, b('candle', candles=3, lit=True, waterlogged=False))
    d.set(66, 13, zt, SL)
    d.add_chest((63, 9, zt), 'east', 'loot')
    d.landmark('coral_tunnel', 'Coral Tunnel', 'discovery', (62, 8, zt - 4), (77, 14, zt + 4))
    d.hazards.append({'id': 'canal_tide', 'type': 'tide', 'box': {'min': [CX0, 5, CZ0], 'max': [CX1, 13, CZ1]},
                      'water': {'min': [CX0, 5, CZ0 + 3], 'max': [CX1, 7, CZ1 - 4]}, 'period': 700, 'high': 260, 'warn': 100,
                      'push': [0, -1], 'force': 0.05, 'until': 'floodgate', 'warning': 'The reservoir sluices groan: the tide is turning!'})
    d.room('R3', 'Canal Crossing', (62, 5, CZ0), (CX1 + 3, 14, CZ1))


# ================================================================ R4 Tidal Court (centre 84,102 r22)
def r4(d, layer, ell, ring_pts, lantern):
    cx, cz = CC
    d.disc(cx, cz, 22, 8, 22, AIR)
    d.fill(cx - 22, 23, cz - 22, cx + 22, 23, cz + 22, SB) if False else None
    # inlay of the floor
    d.disc(cx, cz, 9, FLOOR, FLOOR, PB)
    d.disc(cx, cz, 7, FLOOR, FLOOR, DP)
    d.disc(cx, cz, 16, FLOOR, FLOOR, MSB, rim=14)
    for k in range(16):
        a = k * math.pi / 8
        x, z = int(round(cx + 21 * math.cos(a))), int(round(cz + 21 * math.sin(a)))
        if k in (0, 12): continue                               # keep the east door and the canal mouth clear
        d.fill(x, 8, z, x, 22, z, PR)
        d.set(x, 13, z, SL); d.set(x, 20, z, SL)
    # the well: shaft and tunnel to the Sunken Archive
    d.disc(cx, cz, 5, 2, 7, WATER)
    d.disc(cx, cz, 5, 1, 1, DEEP)
    d.fill(50, 2, TUNNEL_Z[0], 79, 4, TUNNEL_Z[1], WATER)
    d.fill(50, 1, TUNNEL_Z[0], 79, 1, TUNNEL_Z[1], DEEP)
    d.seal('tunnel_gate', 'minecraft:dark_prismarine', (66, 2, TUNNEL_Z[0]), (67, 4, TUNNEL_Z[1]))
    # --- Sluice Control: three levers on an ascending gantry on the west side (lowest first)
    def block_cols(x0, x1, z0, z1, top, s):
        d.fill(x0, FLOOR, z0, x1, top - 1, z1, s)
    block_cols(67, 69, 110, 112, 8, PB)
    for n, z in enumerate((109, 108, 107, 106), start=1): block_cols(67, 69, z, z, 8 + n, DP if n % 2 else PB)
    block_cols(67, 69, 103, 105, 12, PB)
    for n, z in enumerate((102, 101, 100, 99), start=1): block_cols(67, 69, z, z, 12 + n, DP if n % 2 else PB)
    block_cols(67, 69, 96, 98, 16, PB)
    levers = []
    for (lz, top) in ((111, 8), (104, 12), (97, 16)):
        d.fill(66, FLOOR, lz, 66, top + 2, lz, PB)
        d.set(67, top + 1, lz, lever('wall', 'east'))
        levers.append([67, top + 1, lz])
        d.set(66, top + 3, lz, SL)
    for (lz, top) in ((111, 8), (104, 12), (97, 16)):
        d.fill(68, top + 2, lz - 1, 69, top + 2, lz + 1, PB) if False else None
    d.landmark('sluice_control', 'Sluice Control', 'interactive', (66, 7, 96), (69, 19, 112))
    d.mechanics.append({'id': 'sluice', 'type': 'lever_sequence', 'levers': levers,
                        'jam': 'The mechanism is jammed. The sluices open from the lowest lever upward.',
                        'onStep': [[{'do': 'sound', 'at': [84, 6, 102], 'id': 'minecraft:block.piston.extend', 'vol': 1.5, 'pitch': 0.6},
                                    {'do': 'say', 'text': 'Somewhere below, a heavy chain begins to turn.', 'color': 'aqua'}],
                                   [{'do': 'sound', 'at': [84, 6, 102], 'id': 'minecraft:block.piston.extend', 'vol': 1.5, 'pitch': 0.5},
                                    {'do': 'particles', 'at': [84, 8, 102], 'type': 'minecraft:bubble', 'count': 60, 'spread': [4, 0.5, 4]},
                                    {'do': 'say', 'text': 'The pool shudders. One lever remains.', 'color': 'aqua'}]],
                        'onDone': [{'do': 'fill', 'block': 'minecraft:water', 'box': {'min': [66, 2, TUNNEL_Z[0]], 'max': [67, 4, TUNNEL_Z[1]]}},
                                   {'do': 'sound', 'at': [84, 6, 102], 'id': 'minecraft:entity.generic.splash', 'vol': 2, 'pitch': 0.6},
                                   {'do': 'say', 'text': 'The sluice gate unseals: a passage opens at the bottom of the well, leading to the Sunken Archive.', 'color': 'gold'}]})
    # --- Floodgate Switch (interactive): north-east of the canal mouth; stops the tide for good
    d.fill(90, 8, 84, 90, 10, 84, PB)
    d.set(89, 9, 84, lever('wall', 'west'))
    d.set(90, 11, 84, SL)
    d.fill(89, 8, 83, 89, 8, 85, DP) if False else None
    d.fill(88, 7, 82, 91, 7, 86, DP); d.fill(88, 8, 82, 88, 10, 82, PR); d.fill(91, 8, 82, 91, 10, 82, PR); d.fill(89, 8, 85, 89, 8, 85, PB); d.fill(90, 8, 85, 90, 9, 85, PB)
    d.landmark('floodgate_switch', 'Floodgate Switch', 'interactive', (88, 7, 82), (91, 11, 86))
    d.mechanics.append({'id': 'floodgate', 'type': 'lever_sequence', 'levers': [[89, 9, 84]],
                        'onDone': [{'do': 'clear', 'only': 'water', 'box': {'min': [CX0, 5, CZ0 + 3], 'max': [CX1, 7, CZ1 - 4]}},
                                   {'do': 'sound', 'at': [84, 6, 70], 'id': 'minecraft:block.piston.contract', 'vol': 2, 'pitch': 0.5},
                                   {'do': 'say', 'text': 'The floodgates drop. The canal drains and the tide will not return.', 'color': 'aqua'}]})
    # --- Conduit Shrine (interactive): a real conduit in a prismarine frame; the shrine also mends
    sx, sz = 95, 114
    d.fill(sx - 2, 3, sz - 2, sx + 2, 8, sz + 2, PB)
    d.fill(sx - 1, 5, sz - 1, sx + 1, 7, sz + 1, WATER)
    d.set(sx, 6, sz, b('conduit', waterlogged=True))
    d.fill(sx - 1, 8, sz - 1, sx + 1, 8, sz + 1, b('glass'))
    d.fill(sx - 2, 9, sz - 2, sx + 2, 9, sz + 2, DP); d.fill(sx - 1, 9, sz - 1, sx + 1, 9, sz + 1, AIR)
    d.fill(sx - 2, 9, sz - 2, sx + 2, 9, sz - 2, DP)
    d.set(sx, 9, sz, SL)
    d.fill(sx - 3, 8, sz - 3, sx + 3, 8, sz - 3, PR) if False else None
    d.landmark('conduit_shrine', 'Conduit Shrine', 'interactive', (sx - 2, 3, sz - 2), (sx + 2, 9, sz + 2))
    d.hazards.append({'id': 'shrine_mending', 'type': 'zone', 'box': {'min': [sx - 6, 8, sz - 6], 'max': [sx + 6, 12, sz + 4]},
                      'effect': 'minecraft:regeneration', 'amp': 0, 'duration': 80, 'every': 40})
    # --- Tide Offering Basin (interactive) + Lost Chart Alcove (discovery) behind it
    bx, bz = 84, 120
    d.fill(bx, 8, bz, bx, 9, bz, PB)
    d.set(bx, 10, bz, b('water_cauldron', level=3))
    for (x, z) in [(bx - 2, bz), (bx + 2, bz)]: d.fill(x, 8, z, x, 11, z, PR); d.set(x, 12, z, SL)
    d.landmark('tide_offering_basin', 'Tide Offering Basin', 'interactive', (bx - 2, 8, bz - 1), (bx + 2, 12, bz + 1))
    d.air(83, 8, 122, 85, 10, 126)
    d.air(80, 8, 127, 88, 12, 133)
    d.fill(80, 7, 127, 88, 7, 133, DP)
    d.add_chest((84, 8, 132), 'north', 'loot')
    d.set(81, 8, 128, lantern); d.set(87, 8, 128, lantern); d.set(84, 12, 130, SL)
    for x in (82, 86):
        for z in (129, 131): d.fill(x, 8, z, x, 10, z, PR)
    d.seal('chart_door', 'minecraft:prismarine_bricks', (83, 8, 122), (85, 10, 123))
    d.landmark('lost_chart_alcove', 'Lost Chart Alcove', 'discovery', (80, 7, 122), (88, 12, 133))
    d.mechanics.append({'id': 'offering', 'type': 'lock', 'at': {'min': [bx, 10, bz], 'max': [bx, 10, bz]}, 'key': 'tide_pearl',
                        'hint': 'A basin of still water. It wants a pearl of the deep.', 'verb': 'sets a pearl in the basin and the wall behind it slides away.',
                        'onOpen': [{'do': 'clear', 'box': {'min': [83, 8, 122], 'max': [85, 10, 123]}},
                                   {'do': 'sound', 'at': [84, 9, 123], 'id': 'minecraft:block.piston.extend', 'vol': 2, 'pitch': 0.7}]})
    # --- East door to the arena (locked, Tide Key)
    d.air(105, 8, 101, 112, 11, 103)
    d.fill(105, 12, 101, 112, 12, 103, PB) if False else None
    d.seal('arena_door', 'minecraft:prismarine_bricks', (107, 8, 101), (108, 11, 103))
    d.mechanics.append({'id': 'arena_door', 'type': 'lock', 'at': {'min': [107, 8, 101], 'max': [108, 11, 103]}, 'key': 'tide_key',
                        'hint': 'A sealed door of prismarine, a heart-shaped hollow in its centre.', 'verb': 'sets the Tide Key in the door and it grinds open.',
                        'onOpen': [{'do': 'clear', 'box': {'min': [107, 8, 101], 'max': [108, 11, 103]}},
                                   {'do': 'sound', 'at': [107, 9, 102], 'id': 'minecraft:block.anvil.destroy', 'vol': 2, 'pitch': 0.5},
                                   {'do': 'say', 'text': 'The way to the Drowned Crown Basin is open.', 'color': 'gold'}]})
    # --- Coral Cathedral (focal) is in the Archive; Barnacled Strongbox too. The court's own landmarks are above.
    d.encounters.append({'room': 'R4', 'say': 'Three sluice levers climb the western gantry. Open them from the lowest, and the well will open.',
                         'spawns': [{'entity': 'minecraft:drowned', 'count': 3, 'name': 'Court Drowned', 'health': 1.0, 'equipment': {'mainhand': 'minecraft:trident'}, 'pokemon': 'wild_mid'},
                                    {'entity': 'minecraft:skeleton', 'count': 2, 'name': 'Drowned Archer', 'pokemon': 'wild_mid'}]})
    d.room('R4', 'Tidal Court', (62, 5, 78), (108, 24, 134))
    d.entry_rooms = None


# ================================================================ R5 Sunken Archive (x8..49, z88..124, flooded)
def r5(d, layer):
    d.fill(8, 2, 88, 49, 14, 124, WATER)
    d.fill(7, 1, 87, 50, 1, 125, DEEP)
    # bookshelf aisles in the south half
    for x in range(12, 48, 6):
        if abs(x - 36) <= 4: continue
        d.fill(x, 2, 106, x + 1, 9, 122, b('bookshelf'))
        for z in range(106, 123, 4): d.fill(x, 10, z, x + 1, 11, z, b('chiseled_bookshelf', facing='north')) if False else None
        d.set(x, 10, 114, SL)
    # --- Sunken Lighthouse (focal): hollow tower, lantern room holds the Tide Key
    lx, lz = 36, 112
    d.fill(lx - 3, 2, lz - 3, lx + 3, 14, lz + 3, PB)
    d.fill(lx - 2, 2, lz - 2, lx + 2, 13, lz + 2, WATER)
    d.fill(lx - 2, 9, lz - 2, lx + 2, 9, lz + 2, DP)
    d.fill(lx - 2, 9, lz - 2, lx - 1, 9, lz - 1, WATER)         # a hole through the lantern-room floor
    d.fill(lx - 1, 2, lz - 3, lx + 1, 4, lz - 3, WATER)         # door on the north face
    for y in (11, 12):
        for (x, z) in [(lx - 3, lz), (lx + 3, lz), (lx, lz - 3), (lx, lz + 3)]: d.set(x, y, z, SL)
    d.fill(lx - 1, 10, lz + 2, lx + 1, 10, lz + 2, PB) if False else None
    d.fill(lx - 1, 13, lz - 1, lx + 1, 13, lz + 1, SL)
    d.add_chest((lx + 2, 10, lz + 2), 'west', 'key', key='tide_key', water_=True)
    d.landmark('sunken_lighthouse', 'Sunken Lighthouse', 'focal', (lx - 3, 2, lz - 3), (lx + 3, 14, lz + 3))
    # --- Coral Cathedral (focal): a vaulted nave with live coral windows, an altar at the west end
    z0, z1 = 91, 100
    d.fill(12, 2, z0, 47, 2, z1, b('smooth_quartz') if False else PB)
    for x in range(14, 46, 4):
        for z in (z0, z1):
            d.fill(x, 3, z, x, 12, z, PB)
            d.set(x, 13, z, b(CORAL[(x // 4) % 5]))
        for z in range(z0 + 1, z1): d.set(x, 13, z, DP)
        for yy, zz in ((12, z0 + 1), (12, z1 - 1)): d.set(x, yy, zz, DP)
    for x in range(13, 47):
        for z in (z0 - 1, z1 + 1):
            for y in range(3, 12):
                if (x % 4) in (1, 2, 3) and 5 <= y <= 10:
                    d.set(x, y, z, b(CORAL[(x // 4 + y) % 5]))
                elif d.get(x, y, z) in (WATER, 'minecraft:water[level=0]'): d.set(x, y, z, PB)
    d.fill(12, 2, z0 + 3, 16, 3, z1 - 3, PB)                   # altar steps
    d.fill(12, 4, z0 + 4, 13, 4, z1 - 4, DP)
    d.set(12, 5, (z0 + z1) // 2, SL)
    d.add_chest((13, 5, (z0 + z1) // 2 + 1), 'east', 'loot', water_=True)
    for x in range(20, 46, 5):
        for z in (z0 + 2, z1 - 2):
            d.set(x, 3, z, b('sea_pickle', pickles=3, waterlogged=True))
            d.set(x + 1, 3, z, b(['tube', 'brain', 'bubble', 'fire', 'horn'][(x // 5) % 5] + '_coral_fan', waterlogged=True))
    d.landmark('coral_cathedral', 'Coral Cathedral', 'focal', (12, 2, z0 - 1), (47, 13, z1 + 1))
    d.landmark('barnacled_strongbox', 'Barnacled Strongbox', 'discovery', (12, 2, z0 + 3), (16, 5, z1 - 3))
    d.hazards.append({'id': 'archive_breath', 'type': 'zone', 'box': {'min': [8, 1, 86], 'max': [80, 15, 126]},
                      'effect': 'minecraft:water_breathing', 'amp': 0, 'duration': 120, 'every': 20})
    d.hazards.append({'id': 'archive_sight', 'type': 'zone', 'box': {'min': [8, 1, 86], 'max': [80, 15, 126]},
                      'effect': 'minecraft:night_vision', 'amp': 0, 'duration': 260, 'every': 100})
    d.room('R5', 'Sunken Archive', (8, 1, 86), (49, 15, 126))
    d.keys['tide_key'] = {'item': 'minecraft:heart_of_the_sea', 'name': 'Tide Key', 'lore': 'Opens the sealed door to the Drowned Crown Basin.'}
    d.keys['tide_pearl'] = {'item': 'minecraft:nautilus_shell', 'name': 'Tide Pearl', 'lore': 'An offering for the basin in the Tidal Court.'}


# ================================================================ R6 Drowned Crown Basin (centre 134,102 r22)
def r6(d, layer, ring_pts):
    cx, cz = AC
    d.disc(cx, cz, 22, 8, 25, AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, DEEP, rim=20)
    d.disc(cx, cz, 18, 5, 7, WATER, rim=15)                    # the moat, flush with the floor
    d.disc(cx, cz, 18, 4, 4, SB, rim=15)
    for ddx, ddz in ((-1, 0), (1, 0), (0, -1), (0, 1)):         # four bridges across the moat
        for r in range(14, 20):
            for w in (-1, 0, 1):
                x, z = cx + ddx * r + (w if ddz else 0), cz + ddz * r + (w if ddx else 0)
                for y in (5, 6, 7): d.set(x, y, z, PB)
    d.disc(cx, cz, 8, FLOOR + 1, FLOOR + 1, PB)               # the dais
    d.disc(cx, cz, 5, FLOOR + 1, FLOOR + 1, DP)
    targets = []
    for k, (x, z) in enumerate(ring_pts(cx, cz, 12, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 18, z + 1, PB)
        for yy in range(19, 22): d.set(x, yy, z, PR) if (yy - 19) % 2 == 0 else d.set(x + 1, yy, z + 1, PR)
        d.set(x, 22, z, SL)
        if k % 3 == 0:
            # a ward: a sea lantern bolted to the pillar's inner face at chest height
            tx = x - 1 if x > cx else x + 2
            tz = z
            d.set(tx, 9, tz, SL)
            targets.append({'min': [tx, 9, tz], 'max': [tx, 9, tz]})
    for x, z in ring_pts(cx, cz, 20, 12): d.set(x, 12, z, SL)
    for x, z in ring_pts(cx, cz, 14, 10, 0.3): d.set(x, 25, z, SL)
    d.air(105, 8, 101, 112, 11, 103)
    d.room('R6', 'Drowned Crown Basin', (112, 8, 80), (156, 26, 124))
    d.bosses.append({'id': 'tide_warden', 'room': 'R6', 'entity': 'minecraft:drowned', 'name': 'The Tide Warden',
                     'spawn': [cx, 9, cz], 'health': 9.0, 'pokemon': 'boss', 'pokemonHealth': 300, 'damageMultiplier': 1.8, 'scale': 1.7, 'attack': 6, 'knockbackResistance': 0.6, 'color': 'BLUE',
                     'equipment': {'mainhand': 'minecraft:trident', 'head': 'minecraft:turtle_helmet'},
                     'intro': 'The basin stirs. Break the three wards on the crown pillars to wake what sleeps below.',
                     'deathText': 'The Tide Warden sinks. The basin goes still.',
                     'shield': {'targets': targets, 'needed': 3, 'onBlock': 'minecraft:sea_lantern', 'offBlock': 'minecraft:prismarine',
                                'window': 360, 'breakText': 'The wards shatter! Strike the Warden now!',
                                'relapseText': 'The wards reform and the tide calls its servants.',
                                'relapseAdds': [{'entity': 'minecraft:drowned', 'count': 2, 'room': 'R6', 'name': 'Tide Thrall', 'pokemon': 'trial'}]},
                     'raid': {'pool': ['cobbleraids:swampert', 'cobbleraids:kyogre', 'cobbleraids:suicune', 'cobbleraids:empoleon'], 'level': 55},
                     'phases': [{'at': 0.66, 'effects': [{'do': 'say', 'text': 'The Warden stamps and the moat boils.', 'color': 'red'},
                                                          {'do': 'spawn', 'entity': 'minecraft:drowned', 'count': 2, 'room': 'R6', 'name': 'Tide Thrall', 'pokemon': 'trial'}]},
                                {'at': 0.33, 'effects': [{'do': 'say', 'text': 'The Warden calls the deep.', 'color': 'red'},
                                                          {'do': 'spawn', 'entity': 'minecraft:drowned', 'count': 3, 'room': 'R6', 'name': 'Tide Thrall', 'pokemon': 'trial'}]}]})
    d.completion = {'boss': 'tide_warden', 'delaySeconds': 45, 'intro': 'The Drowned Reservoir. Cross the canal, wake the sluices, and take the Tide Key.',
                    'message': 'The reservoir drains. The dungeon closes shortly.'}


# ================================================================ declarations common to the whole dungeon
def declare(d):
    d.encounters.append({'room': 'R2', 'say': 'Water seeps from the broken aqueduct, and things stir in the puddles.',
                         'spawns': [{'entity': 'minecraft:drowned', 'count': 4, 'name': 'Aqueduct Drowned', 'health': 1.0, 'pokemon': 'wild_low'},
                                    {'entity': 'minecraft:zombie', 'count': 2, 'name': 'Waterlogged Wretch', 'pokemon': 'wild_low'}]})
    d.mechanics.append({'id': 'pearl_trial', 'type': 'trial', 'plate': {'min': [PEARL[0], 8, PEARL[1]], 'max': [PEARL[0], 8, PEARL[1]]},
                        'hold': 40, 'room': 'R1',
                        'waves': [{'say': 'The pool ripples. Something answers the pearl.',
                                   'spawns': [{'entity': 'minecraft:drowned', 'count': 3, 'room': 'R1', 'name': 'Pearl Guardian', 'pokemon': 'trial'}]},
                                  {'say': 'A second tide rises.',
                                   'spawns': [{'entity': 'minecraft:drowned', 'count': 4, 'room': 'R1', 'name': 'Pearl Guardian', 'pokemon': 'trial'}]}],
                        'onDone': [{'do': 'say', 'text': 'The trial is passed. A pearl glimmers on the pedestal.', 'color': 'gold'},
                                   {'do': 'give', 'key': 'tide_pearl', 'at': [PEARL[0], 8, PEARL[1]]}]})


def weather(d):
    """Mix the plain stone-brick shell so the walls read as old masonry rather than a flat wall."""
    pid = d.pid(SB)
    mask = d.a == pid
    rnd = np.random.RandomState(20261017).rand(*d.a.shape)
    d.a[mask & (rnd < 0.22)] = d.pid(MSB)
    d.a[mask & (rnd >= 0.22) & (rnd < 0.34)] = d.pid(CSB)
    d.a[mask & (rnd >= 0.34) & (rnd < 0.40)] = d.pid(PB)
