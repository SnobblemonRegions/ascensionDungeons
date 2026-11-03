"""Cinderforge Bastion Rooms 3-8 (blueprint v2 + audit decisions) and the combined 8-room dungeon.

Usage: python -I build_rooms38.py <out_dir>
Per room: standalone schematic (own 2-thick shell, connectors capped) + checks. Combined: all 8 rooms placed by the
handoff mappings, checked end to end. Block states are written for 1.21.1; WorldEdit/Sponge v3 (unnamed root).
Grid: unset = air/void; 'minecraft:air' = explicitly carved (so a later room's solid fill never refills it).
"""
import json, math, os, random, sys
from collections import deque
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_rooms12 as B
from build_rooms12 import b, blk, stair, slab, wall, CHPB, CPBB, RNB, NR, MAGMA, SHROOM, BARS, CHAIN, LAVA, \
    lantern, campfire, softcamp, ladder, chest, base

rng = random.Random(77)
AIR = 'minecraft:air'
B.VAR['RNB'] = ('red_nether_bricks', 'red_nether_brick_stairs', 'red_nether_brick_slab', 'red_nether_brick_wall')
B.VAR['WARP'] = ('warped_planks', 'warped_stairs', 'warped_slab', 'warped_planks')
B.PLANTS.update({'warped_roots', 'warped_fungus', 'twisting_vines', 'twisting_vines_plant'})
B.PASS.update(B.PLANTS); B.PASS.update({'lever'})
B.FULL.update({'warped_stem', 'warped_wart_block', 'nether_wart_block', 'obsidian', 'crying_obsidian', 'warped_nylium',
               'crimson_nylium', 'sea_lantern', 'red_nether_bricks', 'warped_planks'})
PBB, BS, NB, RN = 'PBB', 'BS', 'NB', 'RNB'
H4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def face(dx, dz):
    if abs(dx) >= abs(dz): return 'east' if dx > 0 else 'west'
    return 'south' if dz > 0 else 'north'


class Rm:
    def __init__(s, g, off): s.g, s.o = g, off
    def pos(s, x, y, z): return (x + s.o[0], y + s.o[1], z + s.o[2])
    def set(s, x, y, z, v): s.g.c[s.pos(x, y, z)] = AIR if v is None else v
    def get(s, x, y, z): return s.g.c.get(s.pos(x, y, z))
    def fill(s, x1, y1, z1, x2, y2, z2, v):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1): s.set(x, y, z, v)
    def fillu(s, x1, y1, z1, x2, y2, z2, v):
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    p = s.pos(x, y, z)
                    if p not in s.g.c: s.g.c[p] = v
    def air(s, x1, y1, z1, x2, y2, z2): s.fill(x1, y1, z1, x2, y2, z2, None)


def isfree(s): return s is None or s == AIR or base(s) in B.PASS
def isair(s): return s is None or s == AIR
def islava(s): return s is not None and base(s) == 'lava'
def solid(s): return not isfree(s) and not islava(s)
def dist(x, z, cx, cz): return math.hypot(x - cx, z - cz)


def mixed(kind_a, kind_b=None, p=0.3):
    return blk(kind_a) if (kind_b is None or rng.random() > p) else (CPBB if kind_b == 'c' else blk(kind_b))


# ============================================================ ROOM 3
def r3_fill(r): r.fillu(0, -1, 0, 32, 17, 34, blk(NB))


def r3(r, mk):
    ceil = {x: 11 for x in range(6, 27)}
    for i, x in enumerate(range(11, 22)): ceil[x] = [11, 12, 13, 14, 15, 15, 15, 14, 13, 12, 11][i]
    r.air(14, 1, 0, 18, 6, 3); r.air(15, 7, 0, 17, 7, 3); r.air(16, 8, 0, 16, 8, 3)
    for z in range(0, 4): r.set(16, 9, z, CHPB)
    for x in range(6, 27):
        for z in range(4, 28): r.air(x, 1, z, x, ceil[x] - 1, z)
    # floor
    for x in range(14, 19):
        for z in range(0, 4): r.set(x, 0, z, blk(NB))
    for x in range(6, 27):
        for z in range(4, 28):
            if 13 <= x <= 19: s = RNB
            elif x in (12, 20): s = blk(NB)
            else: s = b('cracked_nether_bricks') if (x + z) % 2 else blk(NB)
            r.set(x, 0, z, s)
    for xs in ((7, 8), (24, 25)):
        for zs in ((12, 13, 14), (20, 21, 22)):
            for x in xs:
                for z in zs:
                    r.set(x, 0, z, b('crimson_nylium'))
                    if rng.random() < .4: r.set(x, 1, z, b('crimson_roots'))
    r.fill(14, 0, 15, 18, 0, 19, CHPB)
    for (x, z) in ((14, 15), (18, 15), (14, 19), (18, 19)):      # braziers on 2-high posts: a cauldron at floor level is a climb-on / fall-in fire trap
        r.set(x, 1, z, wall(PBB)); r.set(x, 2, z, wall(PBB)); r.set(x, 3, z, b('lava_cauldron'))
    # sloped-ceiling stairs + ribs
    for x in range(11, 22):
        c = ceil[x]
        for z in range(4, 28):
            if x in (12, 13, 14): r.set(x, c, z, stair(NB, 'west', True))
            elif x in (18, 19, 20): r.set(x, c, z, stair(NB, 'east', True))
        for z in (7, 11, 15, 19, 23): r.set(x, c, z, RNB)
    # pillars
    for cx in (10, 22):
        for cz in (7, 11, 15, 19, 23):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if dx or dz: r.set(cx + dx, 1, cz + dz, slab(NB))
                    if dx or dz: r.set(cx + dx, 10, cz + dz, slab(NB, True))
            r.set(cx, 1, cz, RNB); r.set(cx, 10, cz, RNB)
            for y in range(2, 10):
                r.set(cx, y, cz, RNB)
                for (dx, dz) in H4:
                    if y <= 8: r.set(cx + dx, y, cz + dz, wall(RN))
                    else: r.set(cx + dx, y, cz + dz, stair(NB, face(-dx, -dz), True))
    # canopy
    for (cx, cz) in ((13, 13), (19, 13), (13, 21), (19, 21)):
        r.fill(cx - 1, 10, cz - 1, cx + 1, 11, cz + 1, b('nether_wart_block'))
        r.set(cx, 10, cz, SHROOM); r.set(cx, 12, cz, CHAIN)
    # bunk alcoves
    for z0 in (8, 12, 20):
        zc = z0 + 1
        for side in ('w', 'e'):
            xs = (4, 5) if side == 'w' else (27, 28)
            r.air(xs[0], 2, z0, xs[1], 4, z0 + 2)
            hx, fx, f = (4, 5, 'west') if side == 'w' else (28, 27, 'east')
            r.set(hx, 2, zc, b('red_bed', facing=f, occupied=False, part='head'))
            r.set(fx, 2, zc, b('red_bed', facing=f, occupied=False, part='foot'))
            r.set(fx, 4, zc, lantern(True))
            for x in xs:
                r.set(x, 4, z0, stair(NB, 'east' if side == 'w' else 'west', True))
                r.set(x, 4, z0 + 2, stair(NB, 'east' if side == 'w' else 'west', True))
    # north exit
    r.air(14, 1, 28, 18, 6, 34); r.air(15, 7, 28, 17, 7, 29); r.air(16, 8, 28, 16, 8, 29)
    for z in (28, 29): r.set(16, 9, z, CHPB)
    # east locked door + corridor, west lift cell + sealed doorway
    r.air(28, 1, 16, 32, 4, 18); r.fill(27, 1, 16, 27, 4, 18, blk(BS))
    r.air(1, 1, 16, 3, 5, 18); r.fill(1, 0, 16, 3, 0, 18, CHPB); r.fill(4, 1, 16, 5, 4, 18, blk(BS))
    mk['r3_spawn'] = (16, 1, 1); mk['r3_east_plug'] = ((27, 1, 16), (27, 4, 18)); mk['r3_west_plug'] = ((4, 1, 16), (5, 4, 18))
    mk['r3_lift_arrival'] = (2, 1, 17)


# ============================================================ ROOM 4
def r4_fill(r): r.fillu(0, -15, 0, 48, 20, 48, blk(BS))


def r4(r, mk):
    C = 24
    surf_of = lambda dd: None if dd < 7.5 else -6 if dd < 10.5 else -3 if dd < 13.5 else 0
    ring_in = {0: 13.5, -3: 10.5, -6: 7.5}
    nxt = {0: -3, -3: -6, -6: -14}
    for x in range(0, 49):
        for z in range(0, 49):
            dd = dist(x, z, C, C)
            if dd >= 16.5: continue
            sf = surf_of(dd)
            if sf is None:
                r.air(x, -6, z, x, 13, z); r.fill(x, -13, z, x, -7, z, LAVA)
            else:
                r.air(x, sf + 1, z, x, 13, z)
                r.set(x, sf, z, mixed(PBB, 'c', .25))
                if dd < ring_in[sf] + 1.0:
                    for y in range(nxt[sf] + 1, sf + 1): r.set(x, y, z, mixed(PBB, 'c', .25))
    # stairs at the four cardinals (3 wide)
    for (ax, az) in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        for l in (-1, 0, 1):
            cells = []
            for k in range(8, 20):
                x = C + ax * k + (l if az else 0); z = C + az * k + (l if ax else 0)
                cells.append((x, z, dist(x, z, C, C)))
            outward = face(ax, az)
            rim = sorted([c for c in cells if 13.5 <= c[2] < 15.5], key=lambda c: -c[2])
            t2 = sorted([c for c in cells if 10.5 <= c[2] < 12.5], key=lambda c: -c[2])
            for i, (x, z, _) in enumerate(rim):
                y = -1 - i; r.air(x, y + 1, z, x, 13, z); r.set(x, y, z, stair(PBB, outward))
            for i, (x, z, _) in enumerate(t2):
                y = -4 - i; r.air(x, y + 1, z, x, 13, z); r.set(x, y, z, stair(PBB, outward))
    # inner rail on tier 3
    for x in range(0, 49):
        for z in range(0, 49):
            dd = dist(x, z, C, C)
            if 7.5 <= dd < 8.5 and r.get(x, -6, z) not in (None, AIR): r.set(x, -5, z, wall(BS))
    # descent stairs inside the reservoir (solid, so lava may touch them); top gated by the rail cells above
    for z in range(25, 32):
        y = -7 - (31 - z)
        for x in (23, 24, 25):
            r.fill(x, -13, z, x, y - 1, z, blk(BS)); r.set(x, y, z, stair(PBB, 'south'))
    mk['r4_descent_gate'] = ((23, -5, 32), (25, -5, 32))
    # magma veins in the rim
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        for x in range(0, 49):
            for z in range(0, 49):
                dd = dist(x, z, C, C)
                if 13.5 <= dd < 16.5 and r.get(x, 0, z) is not None and r.get(x, 0, z) != AIR and 'stairs' not in r.get(x, 0, z):
                    perp = abs((x - C) * math.sin(a) - (z - C) * math.cos(a)); along = (x - C) * math.cos(a) + (z - C) * math.sin(a)
                    if perp < 0.5 and along > 0: r.set(x, 0, z, MAGMA)
    # basalt spires
    for k in range(6):
        a = math.radians(10 + 60 * k); x = round(C + 14.5 * math.cos(a)); z = round(C + 14.5 * math.sin(a))
        if r.get(x, 0, z) == MAGMA or r.get(x, 1, z) not in (None, AIR): continue
        for y in range(1, 2 + rng.randint(1, 3)): r.set(x, y, z, b('basalt', axis='y'))
    # ceiling ribs + chimney
    for x in range(0, 49):
        for z in range(0, 49):
            dd = dist(x, z, C, C)
            if dd < 16.5 and any(abs(dd - rr) < 0.5 for rr in (8, 13)): r.set(x, 13, z, stair(BS, 'north', True))
            if dd < 4: r.air(x, 14, z, x, 18, z)
            if 3 <= dd < 4: r.set(x, 18, z, SHROOM)
    # entry corridor
    r.air(22, 1, 0, 26, 6, 7); r.fill(22, 0, 0, 26, 0, 7, blk(PBB))
    # drain niches: (side, z0, z1, role)
    for side, z0, z1, role in (('E', 16, 19, 'lever1'), ('W', 16, 19, 'lever2'), ('W', 29, 32, 'lever3'), ('E', 29, 32, 'chest')):
        back = {}
        for z in range(z0, z1 + 1):
            xs = [x for x in range(0, 49) if 16.5 <= dist(x, z, C, C) < 19.5 and ((x > C) == (side == 'E'))]
            for x in xs:
                r.air(x, 1, z, x, 4, z); r.set(x, 5, z, stair(PBB, 'east' if side == 'E' else 'west', True))
            back[z] = max(xs) if side == 'E' else min(xs)
        zm = (z0 + z1) // 2; bx = back[zm]
        if role == 'chest': r.set(bx, 1, zm, chest('west' if side == 'E' else 'east'))
        else:
            r.set(bx, 2, zm, b('lever', face='wall', facing='west' if side == 'E' else 'east', powered=False))
            mk['r4_' + role] = (bx, 2, zm)
    # tunnel + plug
    r.air(6, -13, 23, 15, -11, 25); r.fill(16, -13, 23, 16, -11, 25, blk(BS))
    mk['r4_plug'] = ((16, -13, 23), (16, -11, 25)); mk['r4_spawn'] = (24, 1, 1)
    mk['r4_reservoir'] = ((17, -13, 17), (31, -7, 31))


# ============================================================ ROOM 5
def r5_fill(r): r.fillu(0, -2, 0, 32, 17, 24, NR)


def r5(r, mk):
    C = 12
    for x in range(0, 25):
        for z in range(0, 25):
            dd = dist(x, z, C, C)
            if dd < 8.5:
                r.air(x, 1, z, x, 7, z)
                s = b('warped_nylium') if rng.random() < .25 else blk(PBB)
                r.set(x, 0, z, s)
                if s == b('warped_nylium') and rng.random() < .5:
                    r.set(x, 1, z, rng.choice([b('warped_roots'), b('warped_fungus'), b('nether_sprouts')]))
            elif dd < 10.5:
                for y in range(1, 8):
                    v = rng.random()
                    r.set(x, y, z, b('warped_stem', axis='y') if v < .7 else b('warped_wart_block') if v < .9 else blk(PBB))
    for Y in range(8, 16):
        rr = 16 - Y
        for x in range(0, 25):
            for z in range(0, 25):
                dd = dist(x, z, C, C)
                if dd < rr: r.air(x, Y, z, x, Y, z)
                elif dd < rr + 1.5: r.set(x, Y, z, stair('WARP', face(C - x, C - z) if False else face(x - C, z - C), True))
    r.set(12, 16, 12, SHROOM)
    for (x, z) in ((18, 12), (9, 17), (9, 7)): r.set(x, 11, z, SHROOM)
    for (x, z) in ((12, 17), (17, 12), (12, 7), (7, 12)): r.set(x, 0, z, b('sea_lantern'))
    r.air(21, 1, 11, 31, 3, 13)
    for x in range(21, 32):
        for z in range(11, 14): r.set(x, 0, z, blk(BS))
    r.air(10, 0, 21, 14, 4, 23); r.fill(10, -1, 21, 14, -1, 23, blk(PBB))
    r.set(12, 0, 22, CHPB); r.set(12, 1, 22, chest('south'))
    for a in (45, 135, 180, 225, 270, 315):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        nx, nz = round(C + 9.5 * ca), round(C + 9.5 * sa); sx, sz = round(C + 8 * ca), round(C + 8 * sa)
        r.air(nx, 1, nz, nx, 3, nz)
        r.set(sx, 1, sz, wall(PBB)); r.set(sx, 2, sz, wall(PBB)); r.set(sx, 3, sz, CHPB)
    for (x, z) in ((11, 12), (13, 12), (12, 11), (12, 13)): pass
    mk['r5_chest'] = (12, 1, 22); mk['r5_fight_center'] = (12, 1, 12); mk['r5_tunnel_entry'] = (30, 1, 12)


# ============================================================ ROOM 6
def r6_fill(r): r.fillu(0, -1, 0, 48, 17, 24, blk(BS))


def r6(r, mk):
    r.air(4, 1, 2, 43, 15, 22)
    for x in range(4, 44):
        for z in range(2, 23): r.set(x, 0, z, mixed(BS, 'c', .2) if rng.random() < .8 else CPBB)
    r.air(0, 1, 11, 3, 4, 13); r.air(44, 1, 11, 47, 4, 13)
    # trench
    for x in range(8, 42):
        r.fill(x, 0, 11, x, 1, 11, blk(BS)); r.fill(x, 0, 13, x, 1, 13, blk(BS)); r.set(x, 0, 12, LAVA)
    for z in (11, 12, 13): r.fill(7, 0, z, 7, 1, z, blk(BS)); r.fill(42, 0, z, 42, 1, z, blk(BS))
    for (a, c) in ((17, 19), (27, 29), (37, 39)):
        for x in range(a, c + 1):
            for z in (11, 12, 13): r.set(x, 0, z, blk(PBB)); r.air(x, 1, z, x, 1, z)
        r.set(a, 1, 12, wall(BS)); r.set(c, 1, 12, wall(BS))
    # furnaces
    for c in (13, 23, 33):
        r.fill(c - 3, 1, 2, c + 3, 5, 6, blk(NB)); r.fill(c - 3, 1, 2, c + 3, 1, 6, blk(BS))
        r.air(c - 1, 1, 3, c + 1, 2, 5)
        for x in range(c - 1, c + 2):
            for z in range(3, 6): r.set(x, 0, z, LAVA)
        r.fill(c - 1, 1, 6, c + 1, 2, 6, BARS)
        for x in range(c - 3, c + 4):
            for z in range(2, 7):
                edge = x in (c - 3, c + 3) or z in (2, 6)
                r.set(x, 6, z, stair(NB, face(x - c, z - 4), True) if edge else blk(NB))
                r.set(x, 7, z, slab(NB, True))
        r.set(c, 11, 12, b('lava_cauldron'))
        for y in range(12, 16): r.set(c, y, 12, CHAIN)
    # gantry
    r.fill(5, 6, 16, 41, 6, 19, blk(PBB)); r.fill(5, 1, 16, 9, 5, 19, blk(BS))
    for x in range(5, 42):
        r.set(x, 7, 16, wall(BS)); r.set(x, 7, 19, wall(BS))
    for px in (10, 20, 30, 40): r.fill(px, 1, 16, px + 1, 5, 19, blk(PBB))
    for bx in (12, 22, 32):
        r.fill(bx, 4, 16, bx, 5, 19, blk(PBB)); r.fill(bx + 7, 4, 16, bx + 7, 5, 19, blk(PBB))
        r.fill(bx + 1, 5, 16, bx + 1, 5, 19, blk(PBB)); r.fill(bx + 6, 5, 16, bx + 6, 5, 19, blk(PBB))
    for y in range(1, 8): r.set(4, y, 17, ladder('east'))
    for x in range(6, 42):
        r.set(x, 9 if False else 9, 2, b('hopper', facing='east', enabled=True))
    for x in (8, 18, 28, 38): r.set(x, 3, 1, b('dropper', facing='south', triggered=False))
    for (x0) in (5, 40):
        for x in range(x0, x0 + 3):
            for z in range(4, 7): r.set(x, 0, z, b('soul_sand')); r.set(x, 1, z, b('soul_fire'))
    for x in (8, 18, 28, 38): r.fill(x, 15, 2, x, 15, 22, blk(PBB))
    mk['r6_entry'] = (1, 1, 12); mk['r6_exit'] = (47, 1, 12); mk['r6_overseer'] = (23, 1, 8)


# ============================================================ ROOM 7
RISE = [0, 2, 5, 7, 10, 12, 15, 17]


def ramp_cell(x, z):
    dx, dz = x - 12, z - 12; dd = math.hypot(dx, dz)
    th = math.degrees(math.atan2(dz, dx)); t = ((th - 180) % 360) / 360.0
    k = min(7, int(t * 8)); f = t * 8 - k
    s = RISE[k]; nx = RISE[k + 1] if k < 7 else 20; rise = nx - s
    arc = 2 * math.pi * dd / 8; pos = f * arc; st = arc - rise
    h = s if pos < st else s + min(rise, int(pos - st) + 1)
    tang = (-math.sin(math.radians(th)), math.cos(math.radians(th)))
    return h, h > s and h < nx or (h == nx and pos >= st), tang, t


def r7_fill(r): r.fillu(0, -1, 0, 24, 48, 24, blk(BS)) if False else r.fillu(0, -1, 0, 24, 48, 24, blk(BS))


def r7(r, mk):
    for x in range(0, 25):
        for z in range(0, 25):
            dd = dist(x, z, 12, 12)
            if dd >= 9.5:
                if rng.random() < .04 and 9.5 <= dd < 12:
                    for y in range(0, 46):
                        if rng.random() < .15: r.set(x, y, z, b('crying_obsidian'))
                continue
            r.air(x, 0, z, x, 45, z)
            if dd < 5.5: continue
            r.set(x, 0, z, blk(PBB))
            h0, stepf, tang, t = ramp_cell(x, z)
            for lap in (0, 1):
                h = h0 + 20 * lap
                if h > 0:
                    r.set(x, h, z, stair(PBB, face(*tang)) if stepf else blk(PBB))
                if dd < 6.5: r.set(x, h + 1, z, wall(BS))
    r.air(0, 1, 11, 3, 4, 13); r.air(0, 41, 11, 3, 44, 13)
    r.fill(0, 40, 11, 4, 40, 13, blk(PBB)); r.air(3, 41, 11, 4, 44, 13)
    for dx in (-1, 0, 1):
        pass
    # windows (N, E, S, W; both laps; W lap 0 is the entry)
    wins = [((0, -1), 5), ((1, 0), 12 - 12 + 10), ((0, 1), 15), ((-1, 0), 20)]
    def lvl(axis):
        zlev = {(0, -1): 5, (1, 0): 10, (0, 1): 15, (-1, 0): 20}
        return zlev[axis]
    for axis, _ in wins:
        base_y = lvl(axis)
        for lap in (0, 1):
            y0 = base_y + 20 * lap if axis != (-1, 0) else 20
            if axis == (-1, 0) and lap == 1: continue
            for off in (-1, 0):
                for step in range(8, 12):
                    cx = 12 + axis[0] * step + (off if axis[1] else 0); cz = 12 + axis[1] * step + (off if axis[0] else 0)
                    if not (0 <= cx <= 24 and 0 <= cz <= 24): continue
                    if dist(cx, cz, 12, 12) < 9.5: continue
                    deep = dist(cx, cz, 12, 12)
                    if deep < 11:
                        r.air(cx, y0 + 1, cz, cx, y0 + 3, cz)
                    elif deep < 12:
                        for y in range(y0 + 1, y0 + 4): r.set(cx, y, cz, SHROOM if y == y0 + 2 else MAGMA)
    mk['r7_entry'] = (1, 1, 12); mk['r7_exit'] = (1, 41, 12)


# ============================================================ ROOM 8
VENTS = [(34, 28), (34, 20), (14, 28), (14, 20), (28, 34), (20, 34), (28, 14), (20, 14)]


def r8_fill(r): r.fillu(0, -1, 0, 48, 28, 48, blk(BS))


def r8(r, mk):
    C = 24
    for x in range(0, 49):
        for z in range(0, 49):
            dd = dist(x, z, C, C)
            if dd >= 22: continue
            for Y in range(1, 27):
                R = 22 if Y <= 12 else 22 - (Y - 9) // 4
                if dd < R: r.set(x, Y, z, None)
            if dd < 5:
                r.fill(x, 0, z, x, 1, z, blk(BS)); r.set(x, 2, z, CHPB if dd < 2.5 else blk(PBB))
                if dd < 1.5: r.set(x, 2, z, LAVA)
                elif dd < 2.5: r.set(x, 3, z, wall(BS))   # rim so nobody steps into the crucible by accident
            elif dd < 6.5:
                r.set(x, 0, z, blk(BS)); r.set(x, 1, z, stair(PBB, face(C - x, C - z)))
            elif dd < 9: r.set(x, 0, z, blk(PBB))
            elif dd < 13:
                ang = math.degrees(math.atan2(z - C, x - C)) % 45
                r.set(x, 0, z, CPBB if min(ang, 45 - ang) < 3 else blk(BS))
            elif dd < 19: r.set(x, 0, z, blk(PBB))
            else: r.set(x, 0, z, LAVA)
    for (vx, vz) in VENTS:
        for x in (vx, vx + 1):
            for z in (vz, vz + 1): r.set(x, 0, z, MAGMA)
    for x in range(0, 49):
        for z in range(0, 49):
            dd = dist(x, z, C, C)
            if 18 <= dd < 19 and not (x > C and 22 <= z <= 26): r.set(x, 1, z, wall(BS))
    for x in range(43, 46):
        for z in range(22, 27): r.set(x, 0, z, blk(PBB))
        r.set(x, 1, 22, wall(BS)); r.set(x, 1, 26, wall(BS))
    r.air(46, 1, 23, 48, 4, 25); r.fill(46, 0, 23, 48, 0, 25, blk(PBB))
    # blind arches with ledges
    for k in range(12):
        a = math.radians(15 + 30 * k); ca, sa = math.cos(a), math.sin(a)
        for x in range(0, 49):
            for z in range(0, 49):
                dd = dist(x, z, C, C)
                if 22 <= dd < 23:
                    perp = abs((x - C) * sa - (z - C) * ca)
                    for Y, wv in ((5, 2), (6, 2), (7, 2), (8, 2), (9, 2), (10, 2), (11, 1), (12, .6)):
                        if perp < wv: r.set(x, Y, z, None)
                    if perp < 2: r.set(x, 4, z, slab(PBB))
    # pylons + spires
    for (px, pz) in ((35, 35), (13, 35), (35, 13), (13, 13)):
        r.fill(px - 1, 14, pz - 1, px + 1, 26, pz + 1, b('obsidian'))
        for y in range(10, 14): r.set(px, y, pz, CHAIN)
        r.set(px, 9, pz, lantern(True))
    for k in range(6):
        a = math.radians(30 + 60 * k); sx = round(C + 16.5 * math.cos(a)); sz = round(C + 16.5 * math.sin(a))
        for x in (sx, sx + 1):
            for z in (sz, sz + 1):
                for y in range(1, 7 + rng.randint(0, 4)):
                    if r.get(x, y, z) in (None, AIR): r.set(x, y, z, b('basalt', axis='y'))
    for x in range(0, 49):
        for z in range(0, 49):
            if dist(x, z, C, C) < 22 and rng.random() < .02: r.set(x, 26, z, SHROOM)
    mk['r8_entry'] = (47, 1, 24); mk['r8_boss'] = (24, 3, 24); mk['r8_lift_pad'] = ((23, 2, 23), (25, 2, 25))
    mk['r8_vents'] = [(vx, 0, vz) for (vx, vz) in VENTS]


# ============================================================ assembly
ROOMS = {
    3: dict(fill=r3_fill, build=r3, box=((0, -1, 0), (32, 17, 34)), origin=(4, 9, 91)),
    4: dict(fill=r4_fill, build=r4, box=((0, -15, 0), (48, 20, 48)), origin=(-4, 9, 125)),
    5: dict(fill=r5_fill, build=r5, box=((0, -2, 0), (32, 17, 24)), origin=(-20, -5, 137)),
    6: dict(fill=r6_fill, build=r6, box=((0, -1, 0), (48, 17, 24)), origin=(36, 9, 96)),
    7: dict(fill=r7_fill, build=r7, box=((0, -1, 0), (24, 48, 24)), origin=(84, 9, 96)),
    8: dict(fill=r8_fill, build=r8, box=((0, -1, 0), (48, 28, 48)), origin=(36, 49, 84)),
}


def shell_for(g, boxes):
    lo = [min(a[i] for a, _ in boxes) - 2 for i in range(3)]; hi = [max(c[i] for _, c in boxes) + 2 for i in range(3)]
    def inside(p): return any(a[0] <= p[0] <= c[0] and a[1] <= p[1] <= c[1] and a[2] <= p[2] <= c[2] for a, c in boxes)
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                p = (x, y, z)
                if p not in g.c and not inside(p): g.c[p] = blk(BS)
    return lo, hi


def box_world(spec, off):
    (a, c) = spec['box']
    return ((a[0] + off[0], a[1] + off[1], a[2] + off[2]), (c[0] + off[0], c[1] + off[1], c[2] + off[2]))


def connect(g):
    D = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
    for p, s in list(g.c.items()):
        n = base(s)
        if n.endswith('_wall') or n == 'iron_bars':
            conn = {}
            for d, (dx, dz) in D.items():
                q = g.c.get((p[0] + dx, p[1], p[2] + dz))
                conn[d] = bool(q) and (base(q).endswith('_wall') or base(q) == 'iron_bars' or base(q) in B.FULL)
            if n == 'iron_bars': g.c[p] = b('iron_bars', waterlogged=False, **conn)
            else:
                up = g.c.get((p[0], p[1] + 1, p[2]))
                tall = bool(up) and (base(up).endswith('_wall') or base(up) in B.FULL)
                sd = {d: ('tall' if tall else 'low') if c else 'none' for d, c in conn.items()}
                straight = (conn['north'] and conn['south'] and not conn['east'] and not conn['west']) or \
                           (conn['east'] and conn['west'] and not conn['north'] and not conn['south'])
                g.c[p] = 'minecraft:' + n + '[east=%s,north=%s,south=%s,up=%s,waterlogged=false,west=%s]' % (
                    sd['east'], sd['north'], sd['south'], 'false' if (straight and not tall) else 'true', sd['west'])


# ============================================================ analysis
class An:
    def __init__(s, g, boxes, lo, hi): s.g, s.boxes, s.lo, s.hi = g, boxes, lo, hi
    def get(s, p): return s.g.c.get(p)
    def free(s, p): return isfree(s.get(p))
    def stand(s, p):
        c = s.get(p)
        if c is not None and base(c) == 'ladder': return True
        if not s.free(p) or not s.free((p[0], p[1] + 1, p[2])) or islava(c): return False
        gr = s.get((p[0], p[1] - 1, p[2]))
        return gr is not None and solid(gr) and not base(gr).endswith('_wall')   # walls are 1.5 tall
    def lava_checks(s):
        cont, exp = [], []
        for p, v in s.g.c.items():
            if not islava(v): continue
            if s.get((p[0], p[1] - 1, p[2])) in (None, AIR): cont.append(('below', p))
            for dx, dz in H4:
                n = (p[0] + dx, p[1], p[2] + dz); q = s.get(n)
                if q is None or q == AIR or base(q) in B.PLANTS: cont.append(('side', p))
                elif not islava(q) and base(q) not in B.PASS:
                    up = s.get((n[0], n[1] + 1, n[2]))
                    if isair(up) or (up is not None and base(up) in B.PLANTS): exp.append(p + (dx, dz))
        return cont, exp
    def enclosure(s, seed):
        seen = {seed}; q = deque([seed]); leak = None
        while q:
            p = q.popleft()
            for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
                if n in seen: continue
                v = s.get(n)
                if not (isfree(v) or islava(v)): continue
                if not any(a[0] <= n[0] <= c[0] and a[1] <= n[1] <= c[1] and a[2] <= n[2] <= c[2] for a, c in s.boxes):
                    leak = leak or n; continue
                seen.add(n); q.append(n)
        return leak, len(seen)
    def reach(s, start, banned=lambda p: False):
        seen = {start}; q = deque([start])
        while q:
            p = q.popleft(); cur = s.get(p); nxt = []
            if cur is not None and base(cur) == 'ladder':
                for dy in (1, -1):
                    c = (p[0], p[1] + dy, p[2])
                    if s.free(c): nxt.append(c)
            for dx, dz in H4:
                for dy in (1, 0, -1, -2, -3):
                    c = (p[0] + dx, p[1] + dy, p[2] + dz)
                    if dy == 1 and not s.free((p[0], p[1] + 2, p[2])): continue
                    if dy <= -1 and not (s.free((c[0], p[1], c[2])) and s.free((c[0], p[1] + 1, c[2]))): continue
                    nxt.append(c)
            for c in nxt:
                if c in seen or banned(c): continue
                if s.stand(c): seen.add(c); q.append(c)
        return seen


def clear_cells(g, cells):
    saved = {}
    for c in cells: saved[c] = g.c.get(c); g.c[c] = AIR
    return saved


def restore(g, saved):
    for c, v in saved.items():
        if v is None: g.c.pop(c, None)
        else: g.c[c] = v


def cube(a, c): return [(x, y, z) for x in range(min(a[0], c[0]), max(a[0], c[0]) + 1) for y in range(min(a[1], c[1]), max(a[1], c[1]) + 1) for z in range(min(a[2], c[2]), max(a[2], c[2]) + 1)]


def room_checks(n, g, rm, mk, boxes, lo, hi):
    out, ok = [], True
    an = An(g, boxes, lo, hi)
    cont, exp = an.lava_checks()
    out.append('R%d lava containment violations: %d %s' % (n, len(cont), cont[:4])); ok &= not cont
    if n == 4:
        tops = {rm.pos(x, -7, 31) for x in (23, 24, 25)}
        gated = [e for e in exp if (e[0] + e[3], e[1], e[2] + e[4]) in tops]
        exp = [e for e in exp if e not in gated]
        out.append('R4 lava at the gated stair top (safe while the descent gate stays closed): %d' % len(gated))
    out.append('R%d lava exposed to open floor: %d %s' % (n, len(exp), exp[:4])); ok &= not exp
    P = rm.pos
    start = {3: mk.get('r3_spawn'), 4: mk.get('r4_spawn'), 5: mk.get('r5_tunnel_entry'), 6: mk.get('r6_entry'), 7: mk.get('r7_entry'),
             8: mk.get('r8_entry')}[n]
    leak, cells = an.enclosure(P(*start))
    out.append('R%d enclosure: %s (%d open cells)' % (n, 'SEALED' if not leak else 'LEAK at %s' % (leak,), cells)); ok &= not leak
    A = an.reach(P(*start))
    def need(label, loc, reach_set, expect=True):
        p = P(*loc); hit = p in reach_set
        out.append('  R%d %s %s: %s' % (n, label, loc, hit)); return hit == expect
    if n == 3:
        ok &= need('north exit end', (16, 1, 34), A)
        ok &= need('east plug front', (26, 1, 17), A)
        ok &= need('east corridor end (plug closed -> unreachable)', (32, 1, 17), A, False)
        sv = clear_cells(g, [P(*c) for c in cube(*mk['r3_east_plug']) + cube(*mk['r3_west_plug'])])
        B2 = an.reach(P(*start)); ok &= need('east corridor end (open)', (32, 1, 17), B2); ok &= need('lift cell (open)', (2, 1, 17), B2)
        restore(g, sv)
    if n == 4:
        ok &= need('tier 3 south', (24, -5, 34), A)
        ok &= need('reservoir floor (gated)', (20, -13, 24), A, False)
        res = [P(*c) for c in cube(*mk['r4_reservoir'])]
        gate = [P(*c) for c in cube(*mk['r4_descent_gate'])]
        plug = [P(*c) for c in cube(*mk['r4_plug'])]
        sv = {c: g.c.get(c) for c in res + gate + plug}
        for c in res:
            if islava(g.c.get(c)): g.c[c] = AIR
        for c in gate + plug: g.c[c] = AIR
        B2 = an.reach(P(*start))
        ok &= need('reservoir floor (drained)', (20, -13, 24), B2); ok &= need('tunnel far end (drained)', (7, -13, 24), B2)
        for c, v in sv.items():
            if v is None: g.c.pop(c, None)
            else: g.c[c] = v
    if n == 5:
        ok &= need('altar front', (12, 0, 21), A); ok &= need('chest neighbour', (12, 0, 21), A)
    if n == 6:
        ok &= need('exit corridor end', (47, 1, 12), A); ok &= need('gantry deck', (20, 7, 17), A)
    if n == 7:
        ok &= need('top of ramp', (3, 41, 13), A); ok &= need('exit passage', (1, 41, 12), A)
    if n == 8:
        ok &= need('platform', (27, 3, 24), A)
        for (vx, vz) in VENTS: ok &= need('vent', (vx, 1, vz), A)
    return ok, out


def build_standalone(n, outdir):
    spec = ROOMS[n]; g = B.Grid(); (a, c) = spec['box']
    off = (-a[0] + 3, -a[1] + 3, -a[2] + 3)
    rm = Rm(g, off); mk = {}
    spec['fill'](rm); spec['build'](rm, mk)
    boxes = [box_world(spec, off)]
    lo, hi = shell_for(g, boxes); connect(g)
    ok, rep = room_checks(n, g, rm, mk, boxes, lo, hi)
    path = os.path.join(outdir, 'cinderforge_room%d.schem' % n)
    W, H, L, np_ = write(g, lo, hi, path, 'cinderforge_room%d' % n)
    markers = {k: (v if isinstance(v[0], int) else v) for k, v in mk.items() if not isinstance(v, list)}
    return ok, rep, (W, H, L), {k: _m(rm, lo, v) for k, v in mk.items()}


def _m(rm, lo, v):
    def conv(p): q = rm.pos(*p); return [q[0] - lo[0], q[1] - lo[1], q[2] - lo[2]]
    if isinstance(v, list): return [conv(p) for p in v]
    if isinstance(v[0], int): return conv(v)
    return [conv(p) for p in v]


def write(g, lo, hi, path, name):
    old = B.g; B.g = g
    try:
        res = B.write_schem(path, tuple(lo), tuple(hi))
    finally: B.g = old
    return res



# ============================================================ full dungeon
def build_full(outdir):
    B.g.c.clear(); B.markers.clear()   # rooms 1-2 are bound to this grid object, so reuse it
    B.build_room1(); B.build_room2(); B.shell(); B.connect()
    G = B.Grid(); G.c.update(B.g.c)
    r12 = dict(B.markers)
    rms = {n: Rm(G, ROOMS[n]['origin']) for n in ROOMS}; mks = {n: {} for n in ROOMS}
    for n in ROOMS: ROOMS[n]['fill'](rms[n])
    for n in ROOMS: ROOMS[n]['build'](rms[n], mks[n])
    connect(G)
    boxes = [((8, 8, 0), (32, 29, 20)), ((0, 0, 21), (40, 25, 90))] + [box_world(ROOMS[n], ROOMS[n]['origin']) for n in ROOMS]
    xs = [c[0] for c in G.c]; ys = [c[1] for c in G.c]; zs = [c[2] for c in G.c]
    lo = (min(xs), min(ys), min(zs)); hi = (max(xs), max(ys), max(zs))
    an = An(G, boxes, lo, hi); P = lambda n, *c: rms[n].pos(*c)
    out, ok = [], True
    cont, exp = an.lava_checks()
    out.append('FULL lava containment violations: %d %s' % (len(cont), cont[:4])); ok &= not cont
    tops = {P(4, x, -7, 31) for x in (23, 24, 25)}
    crossing = [e for e in exp if (e[0] + e[3], e[1], e[2] + e[4]) in tops]
    sea_y = B.R2.pos(0, -4, 0)[1]
    sea = [e for e in exp if e not in crossing and e[1] == sea_y]
    rest = [e for e in exp if e not in crossing and e[1] != sea_y]
    out.append('FULL lava exposed to open floor (excluding the R4 gated stair top and the R2 sea surface, %d island-shore cells): %d %s' % (len(sea), len(rest), rest[:4])); ok &= not rest
    spawn = B.R1.pos(12, 1, 3)
    leak, cells = an.enclosure(spawn)
    out.append('FULL enclosure: %s (%d open cells)' % ('SEALED' if not leak else 'LEAK at %s' % (leak,), cells)); ok &= not leak
    A = an.reach(spawn)
    def need(label, p, S, expect=True):
        hit = p in S; out.append('  %s: %s' % (label, hit)); return hit == expect
    ok &= need('spawn -> Room 3 vestibule end', P(3, 16, 1, 1), A)
    ok &= need('spawn -> Room 3 nave centre', P(3, 16, 1, 12), A)
    ok &= need('spawn -> Room 4 tier 3 (default)', P(4, 24, -5, 34), A)
    ok &= need('Room 6 unreachable while the locked door is closed', P(6, 23, 1, 8), A, False)
    ok &= need('Room 5 unreachable before the drain', P(5, 12, 0, 21), A, False)
    # scenario: all plugs open, cistern drained, descent gate open
    cells_ = [P(3, *c) for c in cube(*mks[3]['r3_east_plug']) + cube(*mks[3]['r3_west_plug'])]
    cells_ += [P(4, *c) for c in cube(*mks[4]['r4_plug']) + cube(*mks[4]['r4_descent_gate'])]
    lavac = [P(4, *c) for c in cube(*mks[4]['r4_reservoir']) if islava(G.c.get(P(4, *c)))]
    sv = {c: G.c.get(c) for c in cells_ + lavac}
    for c in cells_ + lavac: G.c[c] = AIR
    S = an.reach(spawn)
    ok &= need('open: Room 6 hall reachable', P(6, 23, 1, 8), S)
    ok &= need('open: Room 6 gantry', P(6, 20, 7, 17), S)
    ok &= need('open: Room 7 top of ramp', P(7, 3, 41, 13), S)
    ok &= need('open: Room 8 platform', P(8, 27, 3, 24), S)
    ok &= need('open: Room 5 altar front (via drained cistern)', P(5, 12, 0, 21), S)
    ok &= need('open: Room 3 lift cell', P(3, 2, 1, 17), S)
    for c, v in sv.items():
        if v is None: G.c.pop(c, None)
        else: G.c[c] = v
    # overlap sanity: no room box intersects another except on the shared handoff planes
    names = ['R1', 'R2'] + ['R%d' % n for n in ROOMS]
    for i in range(len(boxes)):
        for j in range(i + 1, len(boxes)):
            a, c = boxes[i], boxes[j]
            ov = [min(a[1][k], c[1][k]) - max(a[0][k], c[0][k]) + 1 for k in range(3)]
            if all(v > 0 for v in ov) and not (min(ov) == 1) and {names[i], names[j]} != {'R4', 'R5'}:
                out.append('  overlap %s/%s by %s' % (names[i], names[j], ov)); ok = False
    import export_runtime as ER
    stats = ER.export(G, an, rms, mks, r12, boxes, lo, os.path.join(outdir, 'cinderforge_runtime.json'), B, sys.modules[__name__], cube)
    out.append('  runtime layout: %s' % (stats,))
    path = os.path.join(outdir, 'cinderforge_full_dungeon.schem')
    W, H, L, np_ = write(G, lo, hi, path, 'cinderforge_full_dungeon')
    marks = {'R1/R2': {k: [v['combined'][i] - lo[i] for i in range(3)] for k, v in r12.items()}}
    for n in ROOMS: marks['R%d' % n] = {k: _m(rms[n], lo, v) for k, v in mks[n].items()}
    json.dump({'size': [W, H, L], 'origin_combined': list(lo), 'room_origins': {'R%d' % n: ROOMS[n]['origin'] for n in ROOMS},
               'markers': marks, 'checks': out}, open(os.path.join(outdir, 'cinderforge_full_dungeon.json'), 'w', encoding='utf-8'), indent=1)
    return ok, out, (W, H, L)


if __name__ == '__main__':
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    summary = {}; allok = True
    which = [int(a) for a in sys.argv[2:]] or sorted(ROOMS)
    for n in which:
        ok, rep, size, mk = build_standalone(n, out)
        print('--- Room %d: %s %s' % (n, 'PASS' if ok else 'FAIL', size))
        for line in rep: print(line)
        summary[n] = {'ok': ok, 'size': size, 'checks': rep, 'markers': mk}; allok &= ok
    json.dump(summary, open(os.path.join(out, 'cinderforge_rooms38_checks.json'), 'w', encoding='utf-8'), indent=1)
    print('ALL ROOMS PASS' if allok else 'SOME ROOMS FAILED')
    if not sys.argv[2:]:
        fok, frep, fsize = build_full(out)
        print('--- FULL DUNGEON: %s %s' % ('PASS' if fok else 'FAIL', fsize))
        for line in frep: print(line)
