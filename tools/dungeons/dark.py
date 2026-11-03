"""The Eclipse Throne: the Dark dungeon (id `dark`).

Rooms: Twilight Habitat -> Shadow Gallery -> Blackwater Crossing -> Eclipse Court -> Contraband Vault; arena Throne of the Eclipse.
Layout : the Shadow Gallery is an alley MAZE (a fixed random depth-first maze of 3-wide passages); the deepest dead end hides the Hidden Route Lever
         that opens the exit; another dead end hides a False-Wall Strongbox behind a wall that looks like any other.
Hazard : Blackwater Crossing - a causeway of rotting planks over a black pit, watched from a searchlight tower: shadow bolts (blindness) land where you
         stand and the dark presses in. Throwing the three Shadow Alignment levers in the habitat (matching the lit/unlit lantern clues) kills the dark.
Puzzle : the Watchpost Trial yields a Silver Mask, offered at the Eclipse Court to open the Contraband Vault; its Eclipse Key opens the arena.
Extra  : the contraband chest in the Silent Habitat Alcove is the token for the Smuggler's Exchange, which opens the Concealed Ledger Niche.
Boss   : the Eclipse (enderman) behind three gold wards; shadow bolts rain on the throne room while it is awake.
"""
import math, os, sys
from collections import deque
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever
import numpy as np

W, H, L = 300, 46, 140
FLOOR = 7
SB, PB, BAS, OBS = b('smooth_basalt'), b('polished_basalt', axis='y'), b('basalt', axis='y'), b('obsidian')
BCON, PTER, GOLD, DOAK = b('black_concrete'), b('purple_terracotta'), b('gold_block'), b('dark_oak_planks')
DLOG, SOUL, PLANT = b('dark_oak_log', axis='y'), b('soul_lantern', hanging=False, waterlogged=False), b('crying_obsidian')
GLOW = b('shroomlight')
COURT, ARENA = (216, 32), (274, 32)


def build():
    d = kit.Dungeon('dark', 'The Eclipse Throne', W, H, L, biome='minecraft:dark_forest')
    d.loot_table = 'ascensiondungeons:chests/crimson_cathedral'
    d.shell = ('smooth_basalt', 'polished_basalt', 'basalt', 'obsidian', 'purple_terracotta', 'black_concrete')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, BAS)
    C.key(d, 'silver_mask', 'minecraft:white_dye', 'Silver Mask', 'Expressionless. The Mask Offering in the Eclipse Court wants it.')
    C.key(d, 'contraband', 'minecraft:emerald', "Smuggler's Token", "Stamped with a hand that is not a customs hand. The Smuggler's Exchange takes it.")
    C.key(d, 'eclipse_key', 'minecraft:tripwire_hook', 'Eclipse Key', 'Opens the black door to the Throne of the Eclipse.')
    habitat(d); maze(d); crossing(d); court(d); vault(d); throne(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R5', False), ('route', 'R3', True), ('route', 'R5', False), ('offering', 'R5', True),
                     ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, BAS, [(PTER, 0.10), (SB, 0.20), (b('polished_basalt', axis='y'), 0.12), (OBS, 0.03)])
    return d


# ================================================================ R1 Twilight Habitat (cave around 24,30)
def habitat(d):
    d.ellipsoid(24, 15, 30, 22, 14, 19, kit.AIR)
    d.fill(2, 0, 10, 48, FLOOR, 52, SB)
    for x in range(3, 48):
        for z in range(11, 52):
            if d.get(x, 8, z) == kit.AIR: d.set(x, FLOOR, z, d.rng.choice([SB, PB, PTER, SB]))
    d.entry = (7, 8, 30)
    # Twilight Canopy (focal): a flat crown of dark oak leaves and purple glass held up on trunks
    cx, cz = 26, 26
    for (x, z) in ((cx - 7, cz - 7), (cx + 7, cz - 7), (cx - 7, cz + 7), (cx + 7, cz + 7)): d.fill(x, 8, z, x + 1, 17, z + 1, DLOG)
    d.fill(cx - 8, 18, cz - 8, cx + 9, 18, cz + 9, DOAK)
    d.ellipsoid(cx, 19, cz, 10, 3, 10, b('dark_oak_leaves', distance=7, persistent=True, waterlogged=False), only_air=True)
    for (x, z) in S.pts_ring(cx, cz, 6, 8): d.fill(x, 14, z, x, 17, z, b('chain', axis='y', waterlogged=False)); d.set(x, 13, z, b('soul_lantern', hanging=True, waterlogged=False))
    d.landmark('twilight_canopy', 'Twilight Canopy', 'focal', (cx - 9, 8, cz - 9), (cx + 10, 22, cz + 10))
    # Masked Statue (focal): a robed figure with a white mask
    mx, mz = 40, 40
    S.statue(d, mx, mz, 8, OBS, SB, eye=None, face='w')
    d.fill(mx - 2, 18, mz - 1, mx - 2, 19, mz + 1, b('white_concrete')); d.set(mx - 2, 18, mz - 1, BCON); d.set(mx - 2, 18, mz + 1, BCON)
    d.landmark('masked_statue', 'Masked Statue', 'focal', (mx - 3, 8, mz - 3), (mx + 3, 20, mz + 3))
    # Silent Habitat Alcove (discovery): a hush of moss in the south wall, holding the Smuggler's Token
    ax = 14
    S.nook(d, ax, 8, 43, 's', depth=15, w=5, h=4)
    d.fill(ax - 2, 7, 43, ax + 2, 7, 57, b('moss_block'))
    d.set(ax - 1, 8, 55, b('lantern', hanging=False, waterlogged=False)) if False else d.set(ax - 1, 8, 55, SOUL)
    d.add_chest((ax, 8, 57), 'north', 'key', key='contraband')
    d.set(ax + 1, 8, 55, b('brown_mushroom')) if False else d.set(ax + 1, 8, 55, b('candle', candles=2, lit=True, waterlogged=False))
    d.landmark('silent_habitat_alcove', 'Silent Habitat Alcove', 'discovery', (ax - 2, 7, 52), (ax + 2, 11, 57))
    # Shadow Alignment (interactive): three levers on the north wall under lit (soul lantern) or unlit (blackstone) lanterns
    pats = [True, False, True]
    lv = []
    for k, x in enumerate((20, 26, 32)):
        z = 12
        d.fill(x - 1, 8, z - 1, x + 1, 11, z - 1, SB)
        C.wall_lever(d, x, 8, z, 'n')
        d.set(x, 10, z, SOUL if pats[k] else b('blackstone'))
        lv.append(((x, 8, z), pats[k]))
    d.landmark('shadow_alignment', 'Shadow Alignment', 'interactive', (19, 8, 11), (33, 11, 13))
    C.toggle(d, 'shadow_alignment', lv, [C.sound((26, 9, 12), 'minecraft:block.beacon.activate', 1.5, 0.7), C.say('The lanterns steady and the dark thins. The causeway will be easier to read now.', 'dark_purple')],
             'The lanterns burn true.')
    d.air(46, 8, 29, 62, 11, 31)
    d.room('R1', 'Twilight Habitat', (2, 8, 10), (50, 28, 58))


# ================================================================ R2 Shadow Gallery: the maze (x60..121, z14..50)
def maze(d):
    COLS, ROWS, X0, Z0 = 15, 9, 61, 15
    def cell_xz(i, j): return X0 + 4 * i, Z0 + 4 * j
    seen = {(0, 4)}
    open_edges = set()
    stack = [(0, 4)]
    while stack:
        i, j = stack[-1]
        nbrs = [(i + di, j + dj) for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= i + di < COLS and 0 <= j + dj < ROWS and (i + di, j + dj) not in seen]
        if not nbrs: stack.pop(); continue
        n = d.rng.choice(nbrs)
        seen.add(n); open_edges.add(frozenset(((i, j), n))); stack.append(n)
    for i in range(COLS):
        for j in range(ROWS):
            x, z = cell_xz(i, j)
            d.air(x, 8, z, x + 2, 11, z + 2)
            d.fill(x, 7, z, x + 2, 7, z + 2, PB if (i + j) % 2 else SB)
    for e in open_edges:
        (i1, j1), (i2, j2) = sorted(e)
        x1, z1 = cell_xz(i1, j1)
        if j1 == j2: d.air(x1 + 3, 8, z1, x1 + 3, 11, z1 + 2); d.fill(x1 + 3, 7, z1, x1 + 3, 7, z1 + 2, SB)
        else: d.air(x1, 8, z1 + 3, x1 + 2, 11, z1 + 3); d.fill(x1, 7, z1 + 3, x1 + 2, 7, z1 + 3, SB)
    deg = {}
    for e in open_edges:
        for c in e: deg[c] = deg.get(c, 0) + 1
    # BFS depth from the entry cell to find the deepest dead end
    adj = {}
    for e in open_edges:
        a, b_ = tuple(e); adj.setdefault(a, []).append(b_); adj.setdefault(b_, []).append(a)
    dist = {(0, 4): 0}; q = deque([(0, 4)])
    while q:
        c = q.popleft()
        for n in adj.get(c, []):
            if n not in dist: dist[n] = dist[c] + 1; q.append(n)
    dead = [c for c in dist if deg.get(c, 0) == 1 and c != (0, 4) and c != (COLS - 1, 4)]
    dead.sort(key=lambda c: -dist[c])
    lever_cell, chest_cell = dead[0], None
    for c in dead[1:]:
        if c[1] == 0 and abs(c[0] - lever_cell[0]) > 3: chest_cell = c; break
    if chest_cell is None: chest_cell = next(c for c in dead[1:] if c[1] in (0, ROWS - 1))
    # Hidden Route Lever: on a wall of the deepest dead end
    lx, lz = cell_xz(*lever_cell)
    for (dx, dz, wall) in ((0, -1, 'n'), (0, 1, 's'), (-1, 0, 'w'), (1, 0, 'e')):
        px, pz = lx + 1 + dx * 2, lz + 1 + dz * 2
        if d.get(px, 9, pz) not in (kit.AIR,): break
    else: dx, dz, wall = 0, -1, 'n'
    ex, ez = lx + 1 + dx, lz + 1 + dz
    C.wall_lever(d, ex, 9, ez, wall)
    px_, pz_ = (1, 0) if dz else (0, 1)
    for k in (-1, 0, 1):
        for y in (8, 9, 10, 11):
            if (k, y) == (0, 9): continue
            d.set(ex + px_ * k, y, ez + pz_ * k, b('polished_blackstone_bricks') if y != 10 else b('chiseled_polished_blackstone'))
    d.landmark('hidden_route_lever', 'Hidden Route Lever', 'interactive', (lx, 8, lz), (lx + 2, 11, lz + 2))
    # False-Wall Strongbox: a chamber behind the north wall of a dead end on the top row
    sx, sz = cell_xz(*chest_cell)
    d.air(sx, 8, sz - 6, sx + 2, 11, sz - 2)
    d.fill(sx, 7, sz - 6, sx + 2, 7, sz - 2, SB)
    d.add_chest((sx + 1, 8, sz - 6), 'south', 'loot'); d.set(sx, 8, sz - 5, SOUL); d.set(sx + 2, 8, sz - 5, b('candle', candles=2, lit=True, waterlogged=False))
    d.seal('false_wall', 'minecraft:smooth_basalt', (sx, 8, sz - 2), (sx + 2, 11, sz - 2)) if False else None
    d.seal('false_wall', 'minecraft:smooth_basalt', (sx, 8, sz - 1), (sx + 2, 11, sz - 1))
    d.air(sx, 8, sz - 1, sx + 2, 11, sz - 1)
    d.landmark('false_wall_strongbox', 'False-Wall Strongbox', 'discovery', (sx, 7, sz - 6), (sx + 2, 11, sz - 1))
    # Masked decor: lanterns at junctions, so it is a city and not just a maze
    for (i, j) in list(dist)[::6]:
        x, z = cell_xz(i, j)
        d.set(x + 1, 8, z + 1, SOUL)
    # entry and exit
    d.air(44, 8, 31, 61, 11, 33)
    ex_x = X0 + 4 * (COLS - 1) + 3
    d.air(ex_x, 8, 31, 138, 11, 33)
    d.seal('maze_exit', 'minecraft:smooth_basalt', (ex_x + 1, 8, 31), (ex_x + 2, 11, 33))
    C.lever_seq(d, 'route', [(ex, 9, ez)], [C.clear(C.bx((ex_x + 1, 8, 31), (ex_x + 2, 11, 33))), C.clear(C.bx((sx, 8, sz - 1), (sx + 2, 11, sz - 1))),
                                              C.sound((ex_x + 1, 9, 32), 'minecraft:block.stone.break', 2, 0.5),
                                              C.say('Somewhere a wall slides. The way out of the alleys opens, and so does a wall in the north.', 'dark_purple')])
    C.encounter(d, 'R2', 'The alleys are narrow and the lamps are few. Something follows, one turning behind.',
                [C.mob('minecraft:zombie', 3, 'Cutpurse'), C.mob('minecraft:skeleton', 3, 'Rooftop Archer'), C.mob('minecraft:enderman', 1, 'Shade')])
    d.room('R2', 'Shadow Gallery', (60, 8, 4), (ex_x, 11, 52))
    d.maze_info = {'lever': lever_cell, 'chest': chest_cell}


# ================================================================ R3 Blackwater Crossing (pit x140..190, z10..54; causeway at z31..33)
def crossing(d):
    d.air(140, 2, 10, 190, 34, 54)
    d.fill(139, 1, 10, 191, 1, 54, BCON)
    d.air(134, 8, 31, 142, 11, 33); d.air(138, 8, 28, 140, 11, 36)
    d.air(191, 8, 28, 200, 11, 36)
    # causeway: rotting planks west rim -> tower island (x159..171) -> east rim, with rails of fence posts
    d.fill(140, 7, 31, 158, 7, 33, DOAK); d.fill(172, 7, 31, 190, 7, 33, DOAK)
    for x in range(141, 190, 3):
        if 159 <= x <= 171: continue
        d.fill(x, 8, 31, x, 8, 31, b('dark_oak_fence')); d.fill(x, 8, 33, x, 8, 33, b('dark_oak_fence'))
    d.fill(159, 1, 26, 171, 7, 38, SB); d.fill(159, 7, 26, 171, 7, 38, PB)
    # Blackwater Watchtower (focal) on the island, with a searchlight lamp on the roof
    tx, tz = 165, 32
    S.tower(d, tx, tz, 4, 8, 16, b('polished_blackstone_bricks'), floor=SB, door='w', glow=None, roof=OBS)
    d.fill(tx - 1, 25, tz - 1, tx + 1, 25, tz + 1, b('sea_lantern')); d.fill(tx - 2, 24, tz - 2, tx + 2, 24, tz + 2, OBS) if False else None
    d.fill(tx - 4, 24, tz - 4, tx + 4, 24, tz + 4, b('polished_blackstone_bricks')) if False else None
    for (x, z) in S.pts_ring(tx, tz, 4, 4, 0.78): d.fill(x, 24, z, x, 25, z, b('polished_blackstone_bricks'))
    d.set(tx, 24, tz, b('sea_lantern'))
    for y in range(9, 24): d.set(tx + 3, y, tz, kit.ladder('west'))
    d.air(tx + 3, 24, tz, tx + 3, 25, tz)
    d.air(tx - 4, 24, tz, tx - 1, 24, tz) if False else None
    d.landmark('blackwater_watchtower', 'Blackwater Watchtower', 'focal', (tx - 5, 8, tz - 5), (tx + 5, 26, tz + 5))
    # Watchpost Trial (interactive): a plate at the foot of the tower
    px, pz = 165, 36
    d.disc(px, pz, 2, FLOOR, FLOOR, GOLD if False else b('polished_blackstone_bricks'))
    d.set(px, 8, pz, b('polished_blackstone_pressure_plate', powered=False))
    for (x, z) in S.pts_ring(px, pz, 2, 4, 0.78): d.fill(x, 8, z, x, 9, z, OBS); d.set(x, 10, z, SOUL)
    d.landmark('watchpost_trial', 'Watchpost Trial', 'interactive', (px - 3, 7, pz - 3), (px + 3, 10, pz + 3))
    C.trial(d, 'watchpost_trial', (px, 8, pz), 'R3',
            [{'say': 'The searchlight swings down. The watch has noticed you.', 'spawns': [C.mob('minecraft:zombie', 3, 'Watchman'), C.mob('minecraft:skeleton', 2, 'Rooftop Archer')]},
             {'say': 'Reinforcements come over the water.', 'spawns': [C.mob('minecraft:zombie', 3, 'Watchman'), C.mob('minecraft:skeleton', 3, 'Rooftop Archer'), C.mob('minecraft:enderman', 1, 'Shade')]}],
            [C.say('The lamp goes out. A Silver Mask lies on the plate, staring at nothing.', 'gold'), C.give_key('silver_mask', (px, 8, pz))])
    # Abandoned Scout Camp (discovery) on the west rim
    gx, gz = 136, 40
    d.air(130, 8, 36, 142, 11, 44)
    d.fill(130, 7, 36, 142, 7, 44, SB)
    S.pyramid(d, 136, 41, 8, 3, [b('black_wool'), b('gray_wool')]); d.air(135, 8, 40, 137, 9, 42)
    d.add_chest((136, 8, 41), 'east', 'loot'); d.set(139, 8, 41, b('campfire', facing='north', lit=False, signal_fire=False, waterlogged=False)); d.set(133, 8, 38, b('barrel', facing='up', open=False))
    d.landmark('abandoned_scout_camp', 'Abandoned Scout Camp', 'discovery', (133, 8, 38), (140, 11, 44))
    # Broken Shadow Gate (focal) at the east rim: a shattered black arch
    gx = 192
    d.fill(gx, 8, 27, gx + 1, 17, 28, OBS); d.fill(gx, 8, 36, gx + 1, 17, 37, OBS)
    d.fill(gx, 18, 27, gx + 1, 19, 31, OBS); d.air(gx, 14, 29, gx + 1, 19, 34)
    d.set(gx, 12, 28, PLANT); d.set(gx, 15, 37, PLANT)
    S.rubble(d, gx - 1, 24, gx + 6, 40, 8, [OBS, SB, PB, b('polished_blackstone_bricks')], 14)
    d.landmark('broken_shadow_gate', 'Broken Shadow Gate', 'focal', (gx - 1, 8, 26), (gx + 2, 19, 38))
    for y in range(2, 8): d.set(140, y, 29, kit.ladder('east')); d.set(190, y, 29, kit.ladder('west'))
    d.fill(158, 2, 10, 158, 6, 54, b('iron_bars')); d.fill(172, 2, 10, 172, 6, 54, b('iron_bars'))
    for (x, z) in S.pts_ring(165, 32, 24, 12, 0.3): d.set(x, 20, z, b('lantern', hanging=False, waterlogged=False)) if False else None
    C.crumble(d, 'rotting_planks', C.bx((140, 7, 31), (190, 7, 33)), only='dark_oak_planks', delay=24, restore=170, sound='minecraft:block.wood.break')
    C.zone(d, 'blackwater_dark', C.bx((140, 2, 10), (190, 34, 54)), 'minecraft:darkness', 0, 120, 60, until='shadow_alignment')
    C.pulse(d, 'shadow_bolts', box=C.bx((140, 2, 10), (190, 34, 54)), period=78, warn=34, count=1, radius=1.8, damage=3.0, kind='magic',
            warnParticle='minecraft:smoke', particle='minecraft:squid_ink', effect='minecraft:blindness', duration=60, amp=0,
            sound='minecraft:entity.enderman.stare', vol=0.7, pitch=1.2, until='shadow_alignment', scatter=False, **{'target': 'players'})
    d.room('R3', 'Blackwater Crossing', (130, 2, 10), (200, 34, 54))


# ================================================================ R4 Eclipse Court (centre 216,32 r22)
def court(d):
    cx, cz = COURT
    d.air(192, 8, 28, 197, 11, 36)
    d.disc(cx, cz, 22, 8, 28, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, SB); d.disc(cx, cz, 18, FLOOR, FLOOR, PB, rim=15); d.disc(cx, cz, 12, FLOOR, FLOOR, PTER, rim=10)
    for (x, z) in S.pts_ring(cx, cz, 21, 16, 0.2): d.fill(x, 8, z, x, 22, z, OBS); d.set(x, 18, z, SOUL)
    # Eclipse Monument (focal): a black disc rising before a ring of gold
    mx, mz = cx, cz - 12
    for ang in range(0, 360, 3):
        a_ = math.radians(ang)
        d.set(int(round(mx + 9 * math.cos(a_))), 18 + int(round(9 * math.sin(a_))) - 4 + 4 - 4 + 0, mz, GOLD) if False else None
        x, y = int(round(mx + 8 * math.cos(a_))), 17 + int(round(8 * math.sin(a_)))
        d.set(x, y, mz, GOLD); d.set(x, y, mz + 1, GOLD)
    for x in range(mx - 7, mx + 8):
        for y in range(10, 25):
            if (x - mx) ** 2 + (y - 17) ** 2 <= 36: d.set(x, y, mz, BCON); d.set(x, y, mz + 1, BCON)
    d.fill(mx - 2, 8, mz, mx + 2, 9, mz + 1, OBS)
    d.landmark('eclipse_monument', 'Eclipse Monument', 'focal', (mx - 9, 8, mz - 1), (mx + 9, 26, mz + 2))
    # Smuggler's Exchange (interactive): a back-alley counter on the west side; its token opens the Concealed Ledger Niche
    ex_, ez_ = cx - 17, cz + 4
    d.fill(ex_ - 2, 8, ez_ - 1, ex_ + 2, 8, ez_ + 1, b('dark_oak_planks')); d.set(ex_, 9, ez_, b('barrel', facing='up', open=False)); d.set(ex_ - 2, 9, ez_, b('lantern', hanging=False, waterlogged=False)); d.set(ex_ + 2, 9, ez_, b('candle', candles=3, lit=True, waterlogged=False))
    d.fill(ex_ - 2, 8, ez_ + 2, ex_ + 2, 11, ez_ + 2, SB)
    d.landmark('smuggler_s_exchange', "Smuggler's Exchange", 'interactive', (ex_ - 2, 8, ez_ - 1), (ex_ + 2, 11, ez_ + 2))
    # Concealed Ledger Niche (discovery): behind the north wall, a short passage and a sealed panel
    nx = cx - 3
    d.air(nx, 8, 3, nx + 6, 11, 9)
    d.air(cx - 1, 8, 10, cx + 1, 11, 12)
    d.fill(nx, 7, 3, nx + 6, 7, 9, SB); d.fill(cx - 1, 7, 10, cx + 1, 7, 12, SB)
    d.add_chest((nx + 3, 8, 4), 'south', 'loot'); d.set(nx + 1, 8, 4, b('lectern', facing='east', powered=False)); d.set(nx + 5, 8, 4, b('lectern', facing='west', powered=False))
    d.set(nx + 3, 8, 7, SOUL)
    d.seal('ledger_wall', 'minecraft:smooth_basalt', (cx - 1, 8, 10), (cx + 1, 11, 11))
    d.landmark('concealed_ledger_niche', 'Concealed Ledger Niche', 'discovery', (nx, 7, 3), (nx + 6, 11, 12))
    C.lock(d, 'exchange', C.bx((ex_, 9, ez_)), 'contraband', [C.clear(C.bx((cx - 1, 8, 10), (cx + 1, 11, 11))), C.sound((cx, 9, 10), 'minecraft:block.stone.break', 2, 0.5),
                                                            C.say('The token is stamped and shoved under the counter. Somewhere a wall opens in the north-west.', 'dark_purple')],
           'A back-alley counter with a slot for a token.', 'slips the Smuggler\'s Token across the counter.')
    # Mask Offering (interactive) in the south; the Contraband Vault gate behind
    ox, oz = cx, cz + 17
    d.fill(ox, 8, oz, ox, 9, oz, OBS); d.set(ox, 10, oz, BCON)
    for x in (ox - 2, ox + 2): d.fill(x, 8, oz, x, 11, oz, SB); d.set(x, 12, oz, SOUL)
    d.landmark('mask_offering', 'Mask Offering', 'interactive', (ox - 2, 8, oz - 1), (ox + 2, 12, oz + 1))
    d.air(cx - 1, 8, 54, cx + 1, 11, 66)
    d.seal('vault_wall', 'minecraft:smooth_basalt', (cx - 1, 8, 56), (cx + 1, 11, 57))
    C.lock(d, 'offering', C.bx((ox, 10, oz)), 'silver_mask', [C.clear(C.bx((cx - 1, 8, 56), (cx + 1, 11, 57))), C.sound((cx, 9, 56), 'minecraft:block.stone.break', 2, 0.5),
                                                            C.say('The mask slots onto the blank stone. A false wall in the south sags open.', 'dark_purple')],
           'A blank stone face with nothing to look at.', 'hangs the Silver Mask on the blank face and a wall sags open.')
    d.air(cx + 20, 8, 31, cx + 38, 11, 33)
    d.seal('arena_door', 'minecraft:smooth_basalt', (cx + 23, 8, 31), (cx + 24, 11, 33))
    C.lock(d, 'arena_door', C.bx((cx + 23, 8, 31), (cx + 24, 11, 33)), 'eclipse_key', [C.clear(C.bx((cx + 23, 8, 31), (cx + 24, 11, 33))),
                                                                                        C.sound((cx + 23, 9, 32), 'minecraft:entity.enderman.teleport', 1.5, 0.6), C.say('The black door to the Throne of the Eclipse opens.', 'gold')],
           'A black door with a keyhole that swallows light.', 'turns the Eclipse Key and the black door opens.')
    C.encounter(d, 'R4', 'An eclipse hangs over the court. A counter stands in a back alley; a blank face watches the south.',
                [C.mob('minecraft:zombie', 3, 'Watchman'), C.mob('minecraft:skeleton', 3, 'Rooftop Archer'), C.mob('minecraft:enderman', 1, 'Shade')])
    d.room('R4', 'Eclipse Court', (194, 8, 3), (238, 28, 54))


# ================================================================ R5 Contraband Vault (x196..236, z68..100)
def vault(d):
    cx = COURT[0]
    d.air(196, 8, 68, 234, 18, 100)
    d.air(cx - 1, 8, 58, cx + 1, 11, 68)
    S.floor_pattern(d, 196, 68, 234, 100, FLOOR, [SB, PB], 2)
    for x in range(200, 232, 6):
        for z in (72, 90):
            d.fill(x, 8, z, x + 2, 9, z + 1, b('dark_oak_planks')); d.set(x + 1, 10, z, b('barrel', facing='up', open=False)); d.set(x, 10, z, b('barrel', facing='up', open=False))
            d.set(x + 3, 8, z, b('candle', candles=2, lit=True, waterlogged=False))
    d.add_chest((cx, 8, 97), 'north', 'key', key='eclipse_key')
    d.fill(cx - 2, 8, 98, cx + 2, 10, 99, OBS); d.set(cx - 2, 8, 97, SOUL); d.set(cx + 2, 8, 97, SOUL)
    d.landmark('contraband_cache', 'Contraband Cache', 'discovery', (cx - 2, 8, 96), (cx + 2, 10, 99))
    d.room('R5', 'Contraband Vault', (196, 8, 58), (234, 18, 100))


# ================================================================ R6 Throne of the Eclipse (centre 274,32 r22)
def throne(d):
    cx, cz = ARENA
    d.air(238, 8, 31, 252, 11, 33)
    d.disc(cx, cz, 22, 8, 30, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, SB); d.disc(cx, cz, 18, FLOOR, FLOOR, BCON, rim=14); d.disc(cx, cz, 8, FLOOR, FLOOR, PTER, rim=3)
    d.disc(cx, cz, 4, 8, 8, OBS); d.fill(cx - 1, 9, cz - 1, cx + 1, 9, cz + 1, GOLD)
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 14, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 22, z + 1, OBS)
        d.set(x, 23, z, SOUL) if False else d.set(x, 23, z, b('soul_lantern', hanging=False, waterlogged=False))
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, GOLD); targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): d.fill(x, 8, z, x, 16, z, SB); d.set(x, 17, z, SOUL)
    C.boss(d, 'eclipse', 'R6', 'minecraft:enderman', 'The Eclipse', (cx, 9, cz + 8), targets=targets,
           on_block='minecraft:gold_block', off_block='minecraft:black_concrete', health=4.0, scale=1.5, color='PURPLE',
           intro='The Eclipse takes the throne. Black out the three gold crowns on the pillars to unseat it.',
           death='The Eclipse stands up out of the throne and is not there.', break_text='The crowns go black! The Eclipse is exposed!',
           adds=[C.mob('minecraft:zombie', 2, 'Watchman') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('The light goes out of the room.', 'red'), C.spawn('minecraft:skeleton', 2, 'R6', 'Rooftop Archer')]},
                   {'at': 0.33, 'effects': [C.say('Shapes peel from the walls.', 'red'), C.spawn('minecraft:zombie', 3, 'R6', 'Watchman'), C.spawn('minecraft:enderman', 1, 'R6', 'Shade')]}])
    C.pulse(d, 'eclipse_bolts', box=C.bx((256, 8, 10), (296, 30, 54)), period=60, warn=28, count=2, radius=2.0, damage=3.5, kind='magic',
            warnParticle='minecraft:smoke', particle='minecraft:squid_ink', effect='minecraft:blindness', duration=60, amp=0,
            sound='minecraft:entity.enderman.stare', after='boss_awake:eclipse', until='boss:eclipse', scatter=True, **{'target': 'players'})
    C.finish(d, 'eclipse', 'The Eclipse Throne. Find the hidden lever in the alleys and cross the blackwater.', 'The shadows draw back. The throne room closes shortly.')
    d.room('R6', 'Throne of the Eclipse', (256, 8, 10), (296, 31, 54))
