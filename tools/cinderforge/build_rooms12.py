"""Cinderforge Bastion test slice: Rooms 1 + 2 from docs/cinderforge-bastion-blueprint.md (v2).

Writes a Sponge v3 schematic (WorldEdit layout), a marker sidecar and runs geometry checks.
Usage: python -I build_rooms12.py <out_dir>
Combined frame: Room 1 local (x,y,z) -> (x+8, y+9, z); Room 2 local -> (x, y+9, z+21).
"""
import gzip, io, json, os, random, struct, sys
from collections import deque

rng = random.Random(1021)

# ---------------------------------------------------------------- blocks
VAR = {  # kind -> (block, stairs, slab, wall)
    'BS': ('blackstone', 'blackstone_stairs', 'blackstone_slab', 'blackstone_wall'),
    'PBB': ('polished_blackstone_bricks', 'polished_blackstone_brick_stairs', 'polished_blackstone_brick_slab', 'polished_blackstone_brick_wall'),
    'NB': ('nether_bricks', 'nether_brick_stairs', 'nether_brick_slab', 'nether_brick_wall'),
}
AIR = None


def b(name, **p):
    s = 'minecraft:' + name
    if p:
        s += '[' + ','.join('%s=%s' % (k, str(v).lower()) for k, v in sorted(p.items())) + ']'
    return s


def blk(kind): return b(VAR[kind][0])
def stair(kind, facing, top=False): return b(VAR[kind][1], facing=facing, half='top' if top else 'bottom', shape='straight', waterlogged=False)
def slab(kind, top=False): return b(VAR[kind][2], type='top' if top else 'bottom', waterlogged=False)
def wall(kind): return b(VAR[kind][3])

LAVA = b('lava', level=0)
CHPB = b('chiseled_polished_blackstone')
CPBB = b('cracked_polished_blackstone_bricks')
RNB = b('red_nether_bricks')
NR = b('netherrack')
MAGMA = b('magma_block')
SHROOM = b('shroomlight')
BARS = b('iron_bars')
CHAIN = b('chain', axis='y', waterlogged=False)
def lantern(hanging=False): return b('soul_lantern', hanging=hanging, waterlogged=False)
def campfire(): return b('campfire', facing='north', lit=True, signal_fire=False, waterlogged=False)
def softcamp(): return b('soul_campfire', facing='north', lit=True, signal_fire=False, waterlogged=False)
def ladder(facing): return b('ladder', facing=facing, waterlogged=False)
def chest(facing): return b('chest', facing=facing, type='single', waterlogged=False)

PLANTS = {'crimson_roots', 'nether_sprouts', 'crimson_fungus', 'weeping_vines', 'weeping_vines_plant'}
PASS = PLANTS | {'soul_lantern', 'chain', 'ladder'}


def base(s): return s[10:].split('[')[0]


class Grid:
    def __init__(self): self.c = {}
    def get(self, p): return self.c.get(p)
    def put(self, p, s):
        if s is None: self.c.pop(p, None)
        else: self.c[p] = s


class Room:
    def __init__(self, g, dx, dy, dz): self.g, self.d = g, (dx, dy, dz)
    def pos(self, x, y, z): return (x + self.d[0], y + self.d[1], z + self.d[2])
    def set(self, x, y, z, s): self.g.put(self.pos(x, y, z), s)
    def get(self, x, y, z): return self.g.get(self.pos(x, y, z))
    def fill(self, x1, y1, z1, x2, y2, z2, s):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, s)
    def air(self, x1, y1, z1, x2, y2, z2): self.fill(x1, y1, z1, x2, y2, z2, None)


g = Grid()
R1 = Room(g, 8, 9, 0)
R2 = Room(g, 0, 9, 21)
markers = {}


def mark(room, name, x, y, z, note=''):
    c = room.pos(x, y, z)
    markers[name] = {'combined': list(c), 'note': note}


# ================================================================= ROOM 1
def build_room1():
    r = R1
    r.fill(0, -1, 0, 24, -1, 20, blk('BS'))
    rows = {
        20: 'nnnXXXXXbbbbbbbbbXXXXXnnn', 19: 'nnnXXXXXXXbbbbbXXXXXXXnnn',
        18: 'nnnXXXXXXbbbbbbbXXXXXXnnn', 17: 'nnnXXXXXbbbbbbbbbXXXXXnnn',
        11: 'nnnnnnnnnnbRRRbnnnnnnnnnn', 10: 'nnnnnnnnnnbRRRbnnnnnnnnnn',
        9: 'nnnnnnnnnnbRRRbnnnnnnnnnn', 8: 'nnnnnnnnnnbRRRbnnnnnnnnnn',
        7: '.......LLLXRRRXLLL.......',
        6: '.' * 7 + 'L' + 'B' * 9 + 'L' + '.' * 7, 5: '.' * 7 + 'L' + 'B' * 9 + 'L' + '.' * 7,
        4: '.' * 7 + 'L' + 'BBBCCCBBB' + 'L' + '.' * 7, 3: '.' * 7 + 'L' + 'BBBCCCBBB' + 'L' + '.' * 7,
        2: '.' * 7 + 'L' + 'B' * 9 + 'L' + '.' * 7, 1: '.' * 7 + 'L' + 'B' * 9 + 'L' + '.' * 7,
        0: '.' * 8 + 'X' * 9 + '.' * 8,
    }
    for z in range(12, 17): rows[z] = 'n' * 8 + 'LXbRRRbXL' + 'n' * 8
    for z, row in rows.items():
        assert len(row) == 25, z
        for x, ch in enumerate(row):
            if ch == 'n': s = NR
            elif ch == '.':
                v = rng.random()
                s = NR if v < .55 else b('soul_sand') if v < .80 else blk('BS') if v < .92 else b('gravel')
            elif ch == 'X': s = blk('BS')
            elif ch == 'b': s = CPBB if rng.random() < .3 else blk('PBB')
            elif ch == 'R': s = RNB
            elif ch == 'B': s = CPBB if (x in (8, 16) or z in (1, 6)) else blk('PBB')
            elif ch == 'C': s = CHPB
            elif ch == 'L': s = LAVA
            r.set(x, 0, z, s)

    # --- Y1 walls: every lava cell walled off from open floor
    W_PBB, W_BS = wall('PBB'), wall('BS')
    for z in range(1, 7): r.set(8, 1, z, W_PBB); r.set(16, 1, z, W_PBB)
    for x in range(8, 17): r.set(x, 1, 0, W_PBB)
    for x in (8, 9, 10, 14, 15, 16): r.set(x, 1, 6, W_PBB)
    for (x, z) in ((8, 1), (16, 1), (8, 6), (16, 6)): r.set(x, 2, z, lantern())
    r.set(10, 1, 7, W_BS); r.set(14, 1, 7, W_BS)
    for z in range(1, 8): r.set(6, 1, z, W_BS); r.set(18, 1, z, W_BS)
    r.set(7, 1, 0, W_BS); r.set(17, 1, 0, W_BS)
    for x in (7, 8, 9, 15, 16, 17): r.set(x, 1, 8, W_BS)
    for z in range(12, 17):
        for x in (7, 9, 15, 17): r.set(x, 1, z, W_BS)
    for z in (12, 14, 16):
        r.set(9, 2, z, lantern()); r.set(15, 2, z, lantern())
    r.set(8, 1, 11, W_BS); r.set(16, 1, 11, W_BS)

    # --- gate mass Z17-19
    plane = {17: 'BS', 18: 'PBB', 19: 'NB'}
    def xr(y): return (3, 21) if y <= 8 else (5, 19) if y <= 11 else (7, 17)
    clear = {  # y -> {z: (x1,x2) or None}
        8: {17: (9, 15), 18: (10, 14), 19: (11, 13)}, 9: {17: (10, 14), 18: (11, 13), 19: (12, 12)},
        10: {17: (11, 13), 18: (12, 12), 19: None}, 11: {17: (12, 12), 18: None, 19: None}}
    for z, k in plane.items():
        for y in range(1, 15):
            x1, x2 = xr(y)
            r.fill(x1, y, z, x2, y, z, blk(k))
            if y <= 7: o = {17: (8, 16), 18: (9, 15), 19: (10, 14)}[z]
            else: o = clear.get(y, {}).get(z) if y in clear else None
            if o: r.air(o[0], y, z, o[1], y, z)
    # corbels (inverted stairs facing away from the opening)
    cor = {17: [(8, 8), (9, 9), (10, 10), (11, 11)], 18: [(9, 8), (10, 9), (11, 10)], 19: [(10, 8), (11, 9)]}
    for z, lst in cor.items():
        k = plane[z]
        for (x, y) in lst:
            r.set(x, y, z, stair(k, 'west', True)); r.set(24 - x, y, z, stair(k, 'east', True))
    r.set(12, 12, 17, CHPB); r.set(12, 11, 18, CHPB); r.set(12, 10, 19, CHPB)
    r.set(8, 1, 17, W_BS); r.set(16, 1, 17, W_BS)   # gate-end walls (after the opening is carved)
    for x in (8, 16):  # fangs
        r.set(x, 7, 17, W_BS); r.set(x, 6, 17, W_BS); r.set(x, 5, 17, stair('BS', 'north', True))
    for y in (9, 10, 11): r.set(12, y, 17, CHAIN)
    r.set(12, 8, 17, lantern(True))
    # alcoves
    for xs in ((5, 6), (18, 19)):
        for x in xs:
            r.air(x, 1, 17, x, 4, 18)
            for z in (17, 18):
                r.set(x, 1, z, CHPB); r.set(x, 4, z, stair('PBB', 'north', True))
            r.set(x, 2, 17, lantern())
    # crown + horns
    for x in range(7, 18):
        for z in (17, 19): r.set(x, 15, z, slab('BS'))
    for z in (17, 18, 19):
        pass
    r.set(7, 15, 18, slab('BS')); r.set(17, 15, 18, slab('BS'))
    r.set(8, 15, 18, softcamp()); r.set(16, 15, 18, softcamp())
    for hx, f in ((6, 'west'), (18, 'east')):
        r.set(hx, 12, 18, stair('BS', f));
        for y in (13, 14, 15): r.set(hx, y, 18, W_BS)
        r.set(hx, 16, 18, slab('BS'))
    # wings
    for x1, x2 in ((0, 2), (22, 24)):
        r.fill(x1, 1, 17, x2, 5, 19, blk('BS'))
        r.fill(x1, 6, 17, x2, 6, 19, stair('BS', 'north'))
    r.air(2, 1, 17, 2, 6, 17); r.air(22, 1, 17, 22, 6, 17)
    # cliff banks
    for z in range(8, 17):
        for x in (0, 1, 2, 22, 23, 24):
            h = 6 + (x * 7 + z * 13) % 5
            for y in range(1, h + 1):
                v = rng.random()
                r.set(x, y, z, blk('BS') if v < .2 else MAGMA if v < .23 else NR)
    # Z20 sealed plane with portcullis frame
    r.fill(0, 1, 20, 24, 18, 20, blk('BS'))
    r.air(10, 1, 20, 14, 7, 20)
    for y in range(1, 9): r.set(9, y, 20, blk('PBB')); r.set(15, y, 20, blk('PBB'))
    for y in range(1, 8): r.set(8, y, 20, wall('PBB')); r.set(16, y, 20, wall('PBB'))
    r.set(8, 8, 20, stair('PBB', 'west', True)); r.set(16, 8, 20, stair('PBB', 'east', True))
    r.fill(10, 8, 20, 14, 8, 20, blk('PBB')); r.set(12, 8, 20, CHPB)
    r.fill(8, 9, 20, 16, 9, 20, blk('PBB'))   # full cap: a bottom slab cannot hold the lanterns above it
    r.set(9, 10, 20, lantern()); r.set(15, 10, 20, lantern())
    r.fill(10, 5, 20, 14, 7, 20, BARS)
    # ceiling
    for x in range(0, 25):
        for z in range(0, 21):
            for y in (19, 20): r.set(x, y, z, blk('BS') if rng.random() < .3 else NR)
    # flora (decor)
    for x in range(0, 25):
        for z in range(0, 17):
            if r.get(x, 1, z) is None and r.get(x, 0, z) == NR and rng.random() < .09:
                r.set(x, 0, z, b('crimson_nylium'))   # roots/sprouts/fungus do not survive on plain netherrack
                r.set(x, 1, z, b('crimson_roots') if rng.random() < .55 else b('nether_sprouts') if rng.random() < .8 else b('crimson_fungus'))
            elif r.get(x, 1, z) is None and r.get(x, 0, z) == b('soul_sand') and rng.random() < .15:
                r.set(x, 1, z, b('gray_concrete_powder'))
    mark(R1, 'spawn', 12, 1, 3, 'facing +Z')
    mark(R1, 'portcullis_fill_min', 10, 1, 20, 'mod fills X10-14,Y1-4,Z20 with iron_bars to close')


# ================================================================= ROOM 2
def build_room2():
    r = R2
    r.fill(0, -9, 0, 40, -9, 69, blk('BS'))
    r.fill(3, -8, 1, 37, -4, 68, LAVA)
    # cliffs
    for z in range(0, 70):
        for x in (0, 1, 2, 38, 39, 40):
            h = 8 + (x * 7 + z * 13) % 5
            if (x, z) in ((2, 20), (38, 48)) or (x == 1 and z == 20) or (x == 39 and z == 48): h = 12
            for y in range(-9, h + 1):
                r.set(x, y, z, blk('BS') if rng.random() < .25 else NR)
    for (fx, fz) in ((2, 20), (38, 48)):  # faux lavafalls
        for y in range(-3, 12): r.set(fx, y, fz, SHROOM if y % 4 == 0 else MAGMA)
    # Z0 / Z69 walls outside landing & exit foundations
    for x in list(range(3, 16)) + list(range(25, 38)):
        r.fill(x, -9, 0, x, 12, 0, blk('BS')); r.fill(x, -9, 69, x, 12, 69, blk('BS'))
    # ceiling
    for x in range(0, 41):
        for z in range(0, 70):
            for y in (15, 16): r.set(x, y, z, SHROOM if rng.random() < .03 else blk('BS') if rng.random() < .3 else NR)
    # landing + exit foundations
    r.fill(16, -9, 0, 24, -1, 5, blk('BS')); r.fill(16, 0, 0, 24, 0, 5, blk('PBB'))
    r.set(20, 0, 2, CHPB); r.set(20, 0, 3, CHPB); r.set(19, 0, 2, CHPB); r.set(21, 0, 3, CHPB)
    r.fill(16, -9, 64, 24, -1, 69, blk('BS'))
    r.fill(16, 0, 64, 24, 0, 69, blk('BS')); r.fill(18, 0, 68, 22, 0, 69, blk('PBB'))
    for z in range(0, 6): r.set(16, 1, z, wall('BS')); r.set(24, 1, z, wall('BS'))
    for (x, z) in ((16, 0), (24, 0), (16, 5), (24, 5)): r.set(x, 2, z, lantern())
    # deck
    pz = [(18, 23), (40, 45), (62, 67)]
    inpylon = lambda z: any(a <= z <= b2 for a, b2 in pz)
    isl_gap = {17: [(29, 31), (51, 53)], 23: [(32, 34), (54, 56)]}   # rail gaps
    def in_gap(x, z): return any(a <= z <= b2 for a, b2 in isl_gap.get(x, []))
    lip_gap = {16: isl_gap[17], 24: isl_gap[23]}
    for z in range(6, 68):
        band = (z - 6) % 6 < 4
        for x in range(17, 24):
            r.set(x, 0, z, RNB if 19 <= x <= 21 else (blk('PBB') if band else CPBB))
        for x in range(16, 25): r.set(x, -1, z, blk('BS'))
        for x in (16, 24):
            if not any(a <= z <= b2 for a, b2 in lip_gap[x]):
                r.set(x, 0, z, slab('PBB')); r.set(x, -1, z, stair('PBB', 'east' if x == 16 else 'west', True))
        if not inpylon(z):
            for x in (17, 23):
                if not in_gap(x, z): r.set(x, 1, z, wall('BS'))
    for z in (9, 15, 27, 33, 49, 55):
        for x in (17, 23):
            for y in (1, 2, 3): r.set(x, y, z, wall('BS'))
            r.set(x, 4, z, blk('PBB')); r.set(x, 5, z, campfire()); r.set(x, 6, z, slab('PBB'))
            for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)): r.set(x + dx, 5, z + dz, BARS)
    for z in (28, 34, 52, 58):
        for x in (18, 22): r.set(x, 0, z, MAGMA)
    # piers
    for z0 in (9, 15, 27, 33, 49, 55):
        for z in (z0, z0 + 1):
            r.fill(15, -9, z, 25, -7, z, blk('BS')); r.fill(16, -6, z, 24, -4, z, blk('BS'))
            r.fill(17, -3, z, 23, -1, z, blk('PBB'))
            r.air(18, -3, z, 22, -3, z); r.air(19, -2, z, 21, -2, z); r.air(20, -1, z, 20, -1, z)
            r.set(18, -2, z, stair('PBB', 'west', True)); r.set(22, -2, z, stair('PBB', 'east', True))
            r.set(19, -1, z, stair('PBB', 'west', True)); r.set(21, -1, z, stair('PBB', 'east', True))
            if z0 in (27, 33): r.air(19, -6, z, 21, -4, z)
    # pylons
    for (p0, p1) in pz:
        r.fill(12, -9, p0, 28, -5, p1, blk('BS'))
        r.fill(13, -4, p0, 27, -1, p1, blk('PBB'))
        for x1, x2 in ((13, 16), (24, 27)):
            r.fill(x1, 0, p0, x2, 0, p1, blk('PBB'))
            r.fill(x1, 1, p0, x2, 10, p1, blk('PBB'))
            for x in (x1, x2):
                for z in (p0, p1): r.fill(x, 1, z, x, 10, z, blk('BS'))
        r.fill(17, 7, p0, 23, 10, p1, blk('PBB'))
        for z in range(p0, p1 + 1):
            r.air(17, 1, z, 23, 6, z); r.air(18, 7, z, 22, 7, z); r.air(19, 8, z, 21, 8, z); r.air(20, 9, z, 20, 9, z)
            r.set(17, 7, z, stair('PBB', 'west', True)); r.set(23, 7, z, stair('PBB', 'east', True))
            r.set(18, 8, z, stair('PBB', 'west', True)); r.set(22, 8, z, stair('PBB', 'east', True))
            r.set(19, 9, z, stair('PBB', 'west', True)); r.set(21, 9, z, stair('PBB', 'east', True))
            r.set(20, 10, z, CHPB)
            r.set(12, 7, z, stair('BS', 'east', True)); r.set(28, 7, z, stair('BS', 'west', True))
        for x1, x2, ox in ((13, 16, 13), (24, 27, 27)):
            for z in range(p0, p1 + 1):
                if z % 2 == 0:
                    for x in (x1, x2): r.set(x, 11, z, blk('PBB'))
            for z in (p0 + 1, p1 - 1):  # arrow slits
                r.air(ox, 4, z, ox, 6, z)
                bx = 14 if ox == 13 else 26
                r.fill(bx, 4, z, bx, 6, z, BARS)
            for z in (p0, p1):  # niches
                if x1 == 13: nx = (14, 15)
                else: nx = (25, 26)
                for x in nx:
                    r.air(x, 2, z, x, 4, z)
                r.set(nx[0], 2, z, lantern())
    # trench
    r.fill(19, -8, 24, 21, -8, 39, blk('BS'))
    r.fill(19, -7, 24, 21, -7, 39, blk('PBB'))
    r.fill(18, -7, 24, 18, -4, 39, blk('BS')); r.fill(22, -7, 24, 22, -4, 39, blk('BS'))
    r.fill(19, -6, 24, 21, -4, 39, LAVA)
    # ladder shafts + corridors + plugs. (chamber z, corridor row, plug z): the corridor row is one row inside the
    # pylon so it never touches the lava sea; the plug cell sits between the corridor and the trench end.
    for cz, row, plug in ((20, 22, 23), (43, 41, 40)):
        r.air(25, -6, cz, 25, 2, cz)
        for z in range(min(cz, row), max(cz, row) + 1): r.air(25, -6, z, 25, -5, z)
        r.air(21, -6, row, 24, -5, row)
        for y in range(-6, 3): r.set(25, y, cz, ladder('west'))   # after the carve so the ladder reaches the floor
        r.fill(21, -6, plug, 21, -5, plug, blk('BS'))   # trench plug (mod removes)
        mark(R2, 'door_%d' % cz, 24, 1, cz, 'hidden door cells (24,1-2,z): solid PBB; mod clears')
        mark(R2, 'trench_plug_z%d' % plug, 21, -6, plug, 'BS plug cells y-6..-5; mod clears with drain')
    # islands
    for (cx, cz, west) in ((11, 30, True), (29, 33, False), (11, 52, True), (29, 55, False)):
        r.fill(cx - 3, -8, cz - 3, cx + 3, -4, cz + 3, blk('BS'))
        for x in range(cx - 3, cx + 4):
            for z in range(cz - 3, cz + 4):
                corner = abs(x - cx) == 3 and abs(z - cz) == 3
                if corner: r.set(x, -4, z, blk('BS')); r.set(x, -3, z, LAVA if False else None); continue
                edge = abs(x - cx) == 3 or abs(z - cz) == 3
                r.set(x, -3, z, MAGMA if edge and rng.random() < .25 and not (x == (cx + 3 if west else cx - 3) and abs(z - cz) <= 1)
                      else b('smooth_basalt') if rng.random() < .35 else b('basalt', axis='y'))
        for (dx, dz) in ((-1, 1), (1, -2), (0, 2)):
            for y in (-2, -1): r.set(cx + dx, y, cz + dz, b('basalt', axis='y'))
        r.set(cx, -2, cz, chest('south'))
        mark(R2, 'island_chest_%d_%d' % (cx, cz), cx, -2, cz, 'chest slot')
        for rz in (cz - 1, cz, cz + 1):
            if west:
                r.fill(16, -4, rz, 16, -2, rz, blk('BS')); r.fill(15, -4, rz, 15, -3, rz, blk('BS'))
                r.set(16, -1, rz, stair('BS', 'east')); r.set(15, -2, rz, stair('BS', 'east'))
            else:
                r.fill(24, -4, rz, 24, -2, rz, blk('BS')); r.fill(25, -4, rz, 25, -3, rz, blk('BS'))
                r.set(24, -1, rz, stair('BS', 'west')); r.set(25, -2, rz, stair('BS', 'west'))
    mark(R2, 'landing', 20, 1, 2, '')
    mark(R2, 'exit_threshold', 20, 1, 68, 'handoff to Room 3 (slice is capped by shell here)')


# ================================================================= shell + connections
def shell():
    boxes = [((8, 8, 0), (32, 29, 20)), ((0, 0, 21), (40, 25, 90))]
    xs = [c[0] for c in g.c]; ys = [c[1] for c in g.c]; zs = [c[2] for c in g.c]
    lo = (min(xs + [0]) - 2, min(ys + [0]) - 2, min(zs + [0]) - 2)
    hi = (max(xs + [40]) + 2, max(ys + [29]) + 2, max(zs + [90]) + 2)
    def inside(x, y, z):
        return any(a[0] <= x <= c2[0] and a[1] <= y <= c2[1] and a[2] <= z <= c2[2] for a, c2 in boxes)
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                if (x, y, z) not in g.c and not inside(x, y, z): g.c[(x, y, z)] = blk('BS')
    return lo, hi


FULL = {'blackstone', 'polished_blackstone_bricks', 'cracked_polished_blackstone_bricks', 'chiseled_polished_blackstone',
        'nether_bricks', 'red_nether_bricks', 'netherrack', 'soul_sand', 'gravel', 'magma_block', 'shroomlight', 'basalt',
        'smooth_basalt', 'gray_concrete_powder'}


def connect():
    D = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
    for p, s in list(g.c.items()):
        n = base(s)
        if n.endswith('_wall') or n == 'iron_bars':
            conn = {}
            for d, (dx, dz) in D.items():
                q = g.get((p[0] + dx, p[1], p[2] + dz))
                conn[d] = bool(q) and (base(q).endswith('_wall') or base(q) == 'iron_bars' or base(q) in FULL)
            if n == 'iron_bars':
                g.c[p] = b('iron_bars', waterlogged=False, **conn)
            else:
                up = g.get((p[0], p[1] + 1, p[2]))
                tall = bool(up) and (base(up).endswith('_wall') or base(up) in FULL)
                sides = {d: ('tall' if tall else 'low') if c else 'none' for d, c in conn.items()}
                straight = (conn['north'] and conn['south'] and not conn['east'] and not conn['west']) or \
                           (conn['east'] and conn['west'] and not conn['north'] and not conn['south'])
                g.c[p] = 'minecraft:' + n + '[east=%s,north=%s,south=%s,up=%s,waterlogged=false,west=%s]' % (
                    sides['east'], sides['north'], sides['south'], 'false' if (straight and not tall) else 'true', sides['west'])


# ================================================================= checks
def blocking(s): return s is not None and base(s) not in PASS
def is_lava(s): return s is not None and base(s) == 'lava'
H4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def checks(lo, hi):
    out = []
    ok = True
    # 1 lava containment
    bad = []
    for p, s in g.c.items():
        if not is_lava(s): continue
        for dx, dz in H4:
            q = g.get((p[0] + dx, p[1], p[2] + dz))
            if q is None or base(q) in PLANTS: bad.append(('side-air', p))
        if g.get((p[0], p[1] - 1, p[2])) is None: bad.append(('below-air', p))
    out.append('lava containment violations: %d %s' % (len(bad), bad[:8])); ok &= not bad
    # 2 lava exposure to open floor (player can step into lava)
    exp = []
    for p, s in g.c.items():
        if not is_lava(s): continue
        for dx, dz in H4:
            n = (p[0] + dx, p[1], p[2] + dz); q = g.get(n)
            if q is not None and not is_lava(q) and base(q) not in PLANTS:
                up = g.get((n[0], n[1] + 1, n[2]))
                up2 = g.get((n[0], n[1] + 2, n[2]))
                if (up is None or base(up) in PLANTS) and p[1] >= 9 - 1 + 0:  # walkable levels (Y>=0 local)
                    exp.append(p + (dx, dz))
    exp_r1 = [e for e in exp if e[2] <= 20]
    out.append('lava exposed to open floor at floor level: room1=%d room2-floor=%d %s' % (
        len(exp_r1), len(exp) - len(exp_r1), exp[:6])); ok &= not exp
    # 3 enclosure
    seed = R1.pos(12, 1, 3)
    seen = {seed}; q = deque([seed]); leak = False
    while q:
        p = q.popleft()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if n in seen: continue
            if not (lo[0] <= n[0] <= hi[0] and lo[1] <= n[1] <= hi[1] and lo[2] <= n[2] <= hi[2]): leak = True; continue
            s = g.get(n)
            if s is None or base(s) in PASS or is_lava(s): seen.add(n); q.append(n)
    out.append('enclosure: %s (%d open cells reachable)' % ('SEALED' if not leak else 'LEAK', len(seen))); ok &= not leak
    # 4 walkability
    def free(p): s = g.get(p); return s is None or base(s) in PASS
    def stand(p):
        s = g.get(p)
        if s is not None and base(s) == 'ladder': return True
        if not free(p) or not free((p[0], p[1] + 1, p[2])): return False
        if is_lava(g.get(p)): return False
        gr = g.get((p[0], p[1] - 1, p[2]))
        return gr is not None and blocking(gr) and not is_lava(gr) and not base(gr).endswith('_wall')   # walls are 1.5 tall
    def reach(start, banned=lambda p: False):
        seen = {start}; q = deque([start])
        while q:
            p = q.popleft()
            nxt = []
            s = g.get(p)
            if s is not None and base(s) == 'ladder':
                for dy in (1, -1):
                    c = (p[0], p[1] + dy, p[2])
                    if free(c): nxt.append(c)
            for dx, dz in H4:
                for dy in (1, 0, -1, -2, -3):
                    c = (p[0] + dx, p[1] + dy, p[2] + dz)
                    if dy == 1 and not free((p[0], p[1] + 2, p[2])): continue
                    if dy <= -1 and not (free((c[0], p[1], c[2])) and free((c[0], p[1] + 1, c[2]))): continue
                    if stand(c) or (g.get(c) is not None and base(g.get(c)) == 'ladder'): nxt.append(c)
            for c in nxt:
                if c not in seen and not banned(c) and (stand(c) or (g.get(c) and base(g.get(c)) == 'ladder')):
                    seen.add(c); q.append(c)
        return seen
    start = R1.pos(12, 1, 3)
    exit_c = R2.pos(20, 1, 68)
    A = reach(start)
    out.append('walk A (default state): spawn -> exit threshold: %s' % (exit_c in A)); ok &= exit_c in A
    chests = [m['combined'] for k, m in markers.items() if k.startswith('island_chest')]
    for c in chests:
        # stand next to chest: any neighbour stand cell
        hit = any((c[0] + dx, c[1], c[2] + dz) in A for dx, dz in H4)
        out.append('  island chest %s reachable: %s' % (c, hit)); ok &= hit
    trench_cell = R2.pos(20, -6, 30)
    out.append('walk A: trench NOT reachable while lava-filled/closed: %s' % (trench_cell not in A))
    # scenario B: open doors, remove plugs, drain trench
    saved = {}
    def clear(box):
        for x in range(box[0], box[3] + 1):
            for y in range(box[1], box[4] + 1):
                for z in range(box[2], box[5] + 1):
                    saved[(x, y, z)] = g.get((x, y, z)); g.put((x, y, z), None)
    for cz in (20, 43):
        a = R2.pos(24, 1, cz); clear((a[0], a[1], a[2], a[0], a[1] + 1, a[2]))
    for tz in (23, 40):
        a = R2.pos(21, -6, tz); clear((a[0], a[1], a[2], a[0], a[1] + 1, a[2]))
    a = R2.pos(19, -6, 24); c2 = R2.pos(21, -4, 39); clear((a[0], a[1], a[2], c2[0], c2[1], c2[2]))
    p1 = R2.pos(20, 1, 21); p2 = R2.pos(20, 1, 42)
    spanB = lambda p: R2.pos(0, 0, 24)[2] <= p[2] <= R2.pos(0, 0, 39)[2] and p[1] >= R2.pos(0, 0, 0)[1] - 3
    B = reach(R2.pos(20, 1, 21), banned=lambda p: spanB(p) and p[1] >= R2.pos(0, 1, 0)[1] - 1 and 17 <= p[0] <= 23)
    out.append('walk B (doors open, plugs out, drained): P1 passage -> P2 passage bypassing span B deck via trench: %s' % (p2 in B))
    ok &= p2 in B
    for k, v in saved.items(): g.put(k, v)
    # 5 portcullis closed-state cells empty
    pc = [R1.get(x, y, 20) for x in range(10, 15) for y in range(1, 5)]
    out.append('portcullis fill region empty in default state: %s' % all(v is None for v in pc)); ok &= all(v is None for v in pc)
    # 6 arch funnel
    tbl = {17: {1: (8, 16), 8: (9, 15), 9: (10, 14), 10: (11, 13), 11: (12, 12)},
           18: {1: (9, 15), 8: (10, 14), 9: (11, 13), 10: (12, 12)}, 19: {1: (10, 14), 8: (11, 13), 9: (12, 12)}}
    fun = True
    for z, t in tbl.items():
        for y, (x1, x2) in t.items():
            ys = range(1, 8) if y == 1 else [y]
            for yy in ys:
                for x in range(0, 25):
                    inopen = x1 <= x <= x2
                    s = R1.get(x, yy, z)
                    if inopen and blocking(s) and not (z == 17 and x in (8, 16) and yy in (1, 5, 6, 7)) and not (z == 17 and x == 12 and yy in (8, 9, 10, 11)):
                        fun = False
    out.append('room1 gate funnel openings clear: %s' % fun); ok &= fun
    return ok, out


# ================================================================= export
def write_schem(path, lo, hi):
    W, Hh, L = hi[0] - lo[0] + 1, hi[1] - lo[1] + 1, hi[2] - lo[2] + 1
    pal = {b('air'): 0}; data = bytearray()
    def vint(v):
        while v & ~0x7f: data.append((v & 0x7f) | 0x80); v >>= 7
        data.append(v)
    for y in range(lo[1], hi[1] + 1):
        for z in range(lo[2], hi[2] + 1):
            for x in range(lo[0], hi[0] + 1):
                s = g.get((x, y, z)) or b('air')
                if s not in pal: pal[s] = len(pal)
                vint(pal[s])
    o = io.BytesIO()
    def tag(t, n): o.write(struct.pack('>B', t)); st(n)
    def st(s): bs = s.encode('utf8'); o.write(struct.pack('>H', len(bs))); o.write(bs)
    o.write(b'\x0a'); st('')
    tag(10, 'Schematic')
    tag(3, 'Version'); o.write(struct.pack('>i', 3))
    tag(3, 'DataVersion'); o.write(struct.pack('>i', 3955))
    tag(2, 'Width'); o.write(struct.pack('>h', W)); tag(2, 'Height'); o.write(struct.pack('>h', Hh)); tag(2, 'Length'); o.write(struct.pack('>h', L))
    tag(11, 'Offset'); o.write(struct.pack('>i', 3)); o.write(struct.pack('>3i', 0, 0, 0))
    tag(10, 'Metadata'); tag(8, 'Name'); st('cinderforge_rooms12_slice'); tag(8, 'Author'); st('claude'); o.write(b'\x00')
    tag(10, 'Blocks')
    tag(10, 'Palette')
    for s, i in pal.items(): tag(3, s); o.write(struct.pack('>i', i))
    o.write(b'\x00')
    tag(7, 'Data'); o.write(struct.pack('>i', len(data))); o.write(data)
    tag(9, 'BlockEntities'); o.write(b'\x0a'); o.write(struct.pack('>i', 0))
    o.write(b'\x00'); o.write(b'\x00'); o.write(b'\x00')
    with gzip.open(path, 'wb') as f: f.write(o.getvalue())
    return W, Hh, L, len(pal)


if __name__ == '__main__':
    outdir = sys.argv[1]
    os.makedirs(outdir, exist_ok=True)
    build_room1(); build_room2()
    lo, hi = shell()
    connect()
    ok, rep = checks(lo, hi)
    for line in rep: print(line)
    print('ALL CHECKS PASS' if ok else 'CHECKS FAILED')
    W, H, L, np_ = write_schem(os.path.join(outdir, 'cinderforge_rooms12_slice.schem'), lo, hi)
    for m in markers.values(): m['schematic'] = [m['combined'][i] - lo[i] for i in range(3)]
    json.dump({'size': [W, H, L], 'origin_combined': list(lo), 'palette_size': np_, 'markers': markers,
               'checks': rep}, open(os.path.join(outdir, 'cinderforge_rooms12_slice.json'), 'w', encoding='utf-8'), indent=1)
    print('wrote', W, H, L, 'palette', np_)
