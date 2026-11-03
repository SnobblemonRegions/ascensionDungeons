"""Reusable building shapes for dungeon scripts. All take the Dungeon `d`, absolute schematic coordinates, and block strings.

`y` is always the first layer ABOVE the ground a structure stands on (standing feet level). Shapes never place waterloggable
blocks unless asked. They only add geometry; every dungeon decides where, with what materials, and what mechanic it serves.
"""
import math, zlib
import numpy as np
import kit
from kit import b, AIR


def pts_ring(cx, cz, r, n, off=0.0):
    return [(int(round(cx + r * math.cos(off + k * 2 * math.pi / n))), int(round(cz + r * math.sin(off + k * 2 * math.pi / n)))) for k in range(n)]


def column(d, x, z, y, h, s, w=1, cap=None, base=None):
    d.fill(x, y, z, x + w - 1, y + h - 1, z + w - 1, s)
    if cap: d.fill(x - 1, y + h, z - 1, x + w, y + h, z + w, cap)
    if base: d.fill(x - 1, y, z - 1, x + w, y, z + w, base)


def tower(d, cx, cz, r, y, h, wall, floor=None, door=None, glow=None, roof=None, levels=0):
    """Round tower. door = 'n','s','e','w' opens a 3-high doorway. glow = block set into the wall every few layers."""
    d.disc(cx, cz, r, y, y + h - 1, wall)
    d.disc(cx, cz, r - 1, y, y + h - 1, AIR)
    if floor: d.disc(cx, cz, r - 1, y - 1, y - 1, floor)
    if roof: d.disc(cx, cz, r, y + h, y + h, roof)
    if door:
        dx, dz = {'n': (0, -1), 's': (0, 1), 'e': (1, 0), 'w': (-1, 0)}[door]
        for k in range(r - 1, r + 1):
            for w_ in (-1, 0, 1):
                for yy in range(y, y + 3):
                    d.set(cx + dx * k + (w_ if dz else 0), yy, cz + dz * k + (w_ if dx else 0), AIR)
    if glow:
        for yy in range(y + 3, y + h - 1, 4):
            for (x, z) in pts_ring(cx, cz, r, 4, math.pi / 4): d.set(x, yy, z, glow)
    for lv in range(levels):
        yy = y + 4 * (lv + 1)
        d.disc(cx, cz, r - 1, yy, yy, floor or wall, rim=None)
        d.fill(cx - 1, yy, cz - 1, cx, yy, cz, AIR)


def arch(d, x, z, y, w, h, s, axis='x', depth=1, accent=None):
    """Free-standing arch opening w wide (along axis) and h high, 2-thick jambs, lintel across."""
    if axis == 'x':
        d.fill(x - 1, y, z, x - 1, y + h, z + depth - 1, s); d.fill(x + w, y, z, x + w, y + h, z + depth - 1, s)
        d.fill(x - 1, y + h, z, x + w, y + h + 1, z + depth - 1, s)
        if accent: d.set(x + w // 2, y + h + 1, z, accent)
    else:
        d.fill(x, y, z - 1, x + depth - 1, y + h, z - 1, s); d.fill(x, y, z + w, x + depth - 1, y + h, z + w, s)
        d.fill(x, y + h, z - 1, x + depth - 1, y + h + 1, z + w, s)
        if accent: d.set(x, y + h + 1, z + w // 2, accent)


def pyramid(d, cx, cz, y, r, mats):
    for i in range(r):
        d.fill(cx - r + i, y + i, cz - r + i, cx + r - i, y + i, cz + r - i, mats[i % len(mats)])


def dome(d, cx, cz, y, r, s, thick=1):
    d.ellipsoid(cx, y, cz, r, r, r, s)
    d.ellipsoid(cx, y, cz, r - thick, r - thick, r - thick, AIR)
    d.fill(cx - r, y - r, cz - r, cx + r, y - 1, cz + r, AIR) if False else None


def spire(d, cx, cz, y, h, r, s):
    for i in range(h):
        rr = max(0, int(round(r * (1 - i / float(h)))))
        d.disc(cx, cz, rr, y + i, y + i, s)


def tree(d, cx, cz, y, trunk_h, trunk_r, log, leaf, leaf_r, leaf_h=None):
    leaf_h = leaf_h or leaf_r
    d.disc(cx, cz, trunk_r, y, y + trunk_h - 1, log)
    d.ellipsoid(cx, y + trunk_h + leaf_h // 2, cz, leaf_r, leaf_h, leaf_r, leaf, only_air=True)
    # roots
    for (x, z) in pts_ring(cx, cz, trunk_r + 2, 6):
        d.fill(x, y, z, x, y + 1, z, log)


def mushroom(d, cx, cz, y, h, cap_r, stem, cap):
    d.fill(cx, y, cz, cx, y + h - 1, cz, stem)
    d.ellipsoid(cx, y + h, cz, cap_r, max(2, cap_r // 2), cap_r, cap, only_air=True)
    d.fill(cx, y + h - 1, cz, cx, y + h - 1, cz, stem)


def statue(d, cx, cz, y, s, s2, eye=None, face='n', h=14):
    """A robed humanoid, arms down; `face` is the side the face is on."""
    d.fill(cx - 2, y, cz - 2, cx + 2, y, cz + 2, s2)                                     # plinth
    d.fill(cx - 1, y + 1, cz, cx - 1, y + 4, cz, s); d.fill(cx + 1, y + 1, cz, cx + 1, y + 4, cz, s)
    d.fill(cx - 1, y + 5, cz - 1, cx + 1, y + 8, cz + 1, s)
    d.fill(cx - 2, y + 6, cz, cx - 2, y + 8, cz, s); d.fill(cx + 2, y + 6, cz, cx + 2, y + 8, cz, s)
    d.fill(cx - 1, y + 9, cz - 1, cx + 1, y + 11, cz + 1, s)
    if eye:
        dx, dz = {'n': (0, -2), 's': (0, 2), 'e': (2, 0), 'w': (-2, 0)}[face]
        px, pz = (1, 0) if dz else (0, 1)
        d.set(cx + dx - px, y + 10, cz + dz - pz, eye); d.set(cx + dx + px, y + 10, cz + dz + pz, eye)


def wheel(d, cx, cy, cz, r, axis, rim, spoke, hub, thick=2):
    """Spoked wheel in the plane perpendicular to `axis` ('x' or 'z')."""
    def put(a, v, s):
        for t in range(thick):
            if axis == 'z': d.set(a, v, cz + t, s)
            else: d.set(cx + t, v, a, s)
    for ang in range(0, 360, 3):
        a_ = math.radians(ang)
        put(int(round((cx if axis == 'z' else cz) + r * math.cos(a_))), int(round(cy + r * math.sin(a_))), rim)
    for sp in range(8):
        a_ = sp * math.pi / 4
        for rr in range(0, r):
            put(int(round((cx if axis == 'z' else cz) + rr * math.cos(a_))), int(round(cy + rr * math.sin(a_))), spoke)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            put((cx if axis == 'z' else cz) + dx, cy + dy, hub)


def steps(d, x, z, y, n, direction, width, s, base=None):
    """n full-block steps rising one per cell in `direction` ('n','s','e','w'); y = floor feet level of the first step's neighbour.
    Step i (1..n) has top at y+i (so feet at y+i)."""
    dx, dz = {'n': (0, -1), 's': (0, 1), 'e': (1, 0), 'w': (-1, 0)}[direction]
    px, pz = (1, 0) if dz else (0, 1)
    for i in range(1, n + 1):
        for w_ in range(width):
            xx, zz = x + dx * (i - 1) + px * w_, z + dz * (i - 1) + pz * w_
            d.fill(xx, (base if base is not None else y - 1), zz, xx, y + i - 1, zz, s)


def platform(d, x1, z1, x2, z2, y, s, base=None):
    """A solid slab/column block with top at feet level y (blocks up to y-1, from base)."""
    d.fill(x1, base if base is not None else y - 1, z1, x2, y - 1, z2, s)


def bridge(d, x1, z1, x2, z2, y, w, s, rail=None):
    """Straight axis-aligned bridge deck at block layer y-1 (feet y), w wide; optional rail block 1 high on both sides."""
    if x1 == x2:
        lo, hi = sorted((z1, z2)); half = w // 2
        d.fill(x1 - half, y - 1, lo, x1 - half + w - 1, y - 1, hi, s)
        if rail:
            d.fill(x1 - half, y, lo, x1 - half, y, hi, rail); d.fill(x1 - half + w - 1, y, lo, x1 - half + w - 1, y, hi, rail)
    else:
        lo, hi = sorted((x1, x2)); half = w // 2
        d.fill(lo, y - 1, z1 - half, hi, y - 1, z1 - half + w - 1, s)
        if rail:
            d.fill(lo, y, z1 - half, hi, y, z1 - half, rail); d.fill(lo, y, z1 - half + w - 1, hi, y, z1 - half + w - 1, rail)


def hut(d, x1, z1, x2, z2, y, h, wall, roof, door='s', floor=None, glow=None):
    """Small walled building with a flat roof and a doorway on `door` side (3 wide? 1 wide x 2 high at centre)."""
    d.fill(x1, y, z1, x2, y + h - 1, z2, wall)
    d.fill(x1 + 1, y, z1 + 1, x2 - 1, y + h - 1, z2 - 1, AIR)
    d.fill(x1 - 1, y + h, z1 - 1, x2 + 1, y + h, z2 + 1, roof)
    if floor: d.fill(x1 + 1, y - 1, z1 + 1, x2 - 1, y - 1, z2 - 1, floor)
    cx, cz = (x1 + x2) // 2, (z1 + z2) // 2
    if door == 's': d.fill(cx, y, z2, cx, y + 1, z2, AIR)
    elif door == 'n': d.fill(cx, y, z1, cx, y + 1, z1, AIR)
    elif door == 'e': d.fill(x2, y, cz, x2, y + 1, cz, AIR)
    elif door == 'w': d.fill(x1, y, cz, x1, y + 1, cz, AIR)
    if glow: d.set(cx, y + h - 1, cz, glow)


def crystal(d, cx, cz, y, h, s, s2=None):
    """A leaning crystal cluster: one tall shard and several short ones."""
    d.fill(cx, y, cz, cx, y + h - 1, cz, s)
    d.fill(cx, y + h, cz, cx, y + h, cz, s2 or s)
    for k, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        hh = max(2, h // 2 - k % 2)
        d.fill(cx + dx, y, cz + dz, cx + dx, y + hh - 1, cz + dz, s2 or s)


def rubble(d, x1, z1, x2, z2, y, mats, n, rng=None):
    rng = rng or d.rng
    for _ in range(n):
        x, z = rng.randint(x1, x2), rng.randint(z1, z2)
        if d.get(x, y, z) == AIR:
            d.set(x, y, z, rng.choice(mats))
            if rng.random() < 0.25 and d.get(x, y + 1, z) == AIR: d.set(x, y + 1, z, rng.choice(mats))


def ceiling_lights(d, x1, z1, x2, z2, y, step, s):
    for x in range(x1, x2 + 1, step):
        for z in range(z1, z2 + 1, step): d.set(x, y, z, s)


def floor_pattern(d, x1, z1, x2, z2, y, mats, cell=4):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            d.set(x, y, z, mats[((x // cell) + (z // cell)) % len(mats)])


def weather_pass(d, base_block, mix):
    """Replace the plain shell block with a weighted mix [(block, p), ...] so walls read as material, not a flat colour."""
    pid = d.pid(base_block)
    mask = d.a == pid
    d.mass = mask.copy()
    rnd = np.random.RandomState(zlib.crc32(d.id.encode())).rand(*d.a.shape)
    lo = 0.0
    for blk, p in mix:
        d.a[mask & (rnd >= lo) & (rnd < lo + p)] = d.pid(blk)
        lo += p


def nook(d, x, y, z, facing, depth=2, w=3, h=3):
    """Carve a small alcove into a wall: opening at (x,z) going `facing` ('n','s','e','w') for `depth`, w wide, h high."""
    dx, dz = {'n': (0, -1), 's': (0, 1), 'e': (1, 0), 'w': (-1, 0)}[facing]
    px, pz = (1, 0) if dz else (0, 1)
    half = w // 2
    for k in range(depth):
        for w_ in range(-half, half + 1):
            for yy in range(y, y + h):
                d.set(x + dx * k + px * w_, yy, z + dz * k + pz * w_, AIR)
