"""Eye-level voxel renders of a district .schem: a stand-in for walking the world in game.

Usage: python tools/render3d.py <file.schem> --shots tools/shots/<biome>.json [--out outputs/views] [--width 800]

A shots file is a JSON list of {"name", "pos": [x, y, z], "yaw", "pitch"}; yaw 0 faces north (-z), 90 east (+x), pitch is
up-positive; pos is the eye in block coordinates (standing on the floor row y=4 means y about 6.6).

Approximations: every block is a full cube except air, light blocks, plants, carpets, chains, fans and glass, which are
not drawn; water tints what lies beneath it; light level is ignored (a fixed ambient plus fog) and emissive blocks are
drawn unshaded. It shows shapes, scale, materials and sight lines, not exact in-game lighting.
"""
import argparse
import json
import math
import os
import sys
from multiprocessing import Pool

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview

FOG = np.array([8, 10, 14], dtype=np.float32)
WATER_TINT = np.array([22, 78, 120], dtype=np.float32)
PASS_KEYS = ('carpet', 'chain', 'coral_fan', 'glass', 'kelp', 'vine', 'lichen', 'pane')
# Thin plants are not drawn; exact ids, so that leaves and grass blocks are not caught by a substring.
PASS_EXACT = {'light', 'air', 'cave_air', 'void_air', 'seagrass', 'tall_seagrass', 'lily_pad', 'short_grass', 'tall_grass', 'fern',
              'large_fern', 'azalea', 'flowering_azalea', 'allium', 'azure_bluet', 'cornflower', 'red_mushroom',
              'brown_mushroom', 'hanging_roots', 'sweet_berry_bush', 'dandelion', 'poppy', 'sea_pickle', 'dead_bush', 'torch'}
EMISSIVE = ('end_rod', 'candle', 'campfire', 'lava', 'shroomlight', 'sea_lantern', 'froglight', 'glowstone', 'lantern', 'shroomlight', 'magma')

# Specific colours first, then the plan-view rules as a fallback.
COLOURS = [
    ('mangrove_roots', (104, 76, 46)), ('rooted_dirt', (118, 88, 62)),
    ('end_rod', (240, 240, 255)), ('crying_obsidian', (66, 22, 118)), ('candle', (255, 236, 190)), ('spore_blossom', (236, 140, 190)),
    ('soul_sand', (90, 70, 58)),
    ('campfire', (238, 128, 44)), ('hay_block', (206, 172, 52)),
    ('pearlescent_froglight', (246, 228, 246)), ('blue_ice', (92, 140, 220)), ('packed_ice', (140, 170, 230)),
    ('polished_andesite', (150, 152, 150)), ('andesite', (140, 140, 138)), ('chiseled_copper', (196, 120, 88)),
    ('cut_copper', (192, 112, 82)), ('iron_block', (214, 216, 218)), ('iron_bars', (120, 124, 130)), ('magma_block', (214, 84, 24)),
    ('ochre_froglight', (252, 206, 120)), ('polished_deepslate', (70, 70, 78)),
    ('red_mushroom_block', (196, 44, 44)), ('brown_mushroom_block', (150, 106, 72)), ('mushroom_stem', (228, 218, 192)),
    ('mycelium', (118, 98, 124)), ('mud_brick', (140, 100, 80)), ('packed_mud', (128, 94, 70)), ('shroomlight', (242, 166, 76)),
    ('podzol', (96, 66, 36)), ('coarse_dirt', (112, 80, 56)), ('tuff', (96, 101, 92)), ('dark_oak', (62, 42, 22)),
    ('sea_lantern', (205, 236, 226)), ('lantern', (255, 190, 90)),
    ('dark_prismarine', (42, 92, 80)), ('prismarine', (84, 158, 142)), ('oxidized_cut_copper', (72, 160, 138)),
    ('oxidized', (72, 160, 138)), ('mossy_stone_brick', (104, 124, 92)), ('mossy_cobble', (100, 120, 90)),
    ('cracked_stone_brick', (112, 112, 112)), ('chiseled_stone', (120, 120, 120)), ('stone_brick', (124, 124, 124)),
    ('clay', (150, 156, 172)), ('tube_coral', (50, 90, 220)), ('brain_coral', (220, 90, 160)),
    ('bubble_coral', (170, 60, 190)), ('fire_coral', (200, 50, 50)), ('horn_coral', (210, 190, 60)),
    ('sea_pickle', (110, 140, 50)), ('moss_block', (92, 120, 40)), ('stone', (108, 108, 108)),
    ('deepslate', (74, 74, 82)),
]


def block_colour(name):
    if name in preview.EXACT:
        return preview.EXACT[name]
    for key, c in COLOURS:
        if key in name:
            return c
    return preview.colour(name)


class World:
    def __init__(self, path):
        s = preview.Schem(path)
        self.s = s
        names = s.names
        cls = np.zeros(len(names), dtype=np.int8)           # 0 pass, 1 solid, 2 water
        for i, n in enumerate(names):
            if n.startswith('water') or n.startswith('bubble_column'):
                cls[i] = 2
            elif 'iron_bars' in n:
                cls[i] = 3
            elif s.air[i] or n in PASS_EXACT or any(k in n for k in PASS_KEYS):
                cls[i] = 0
            else:
                cls[i] = 1
        self.cls = cls[s.ids]                                # [y, z, x]
        # Smoke: a lit campfire sends a tall plume up; drawn as class 4 (translucent grey) so its placement can be checked.
        camp = np.array([n == 'campfire' for n in names])
        if camp.any():
            for y, z, x in zip(*np.nonzero(camp[s.ids])):
                for dy in range(1, 15):
                    if y + dy < s.ids.shape[0] and self.cls[y + dy, z, x] == 0:
                        self.cls[y + dy, z, x] = 4
        self.col = np.array([block_colour(n) for n in names], dtype=np.float32)
        self.emit = np.array([any(k in n for k in EMISSIVE) for n in names])
        self.bloom = np.array(['flowering_azalea_leaves' in n for n in names])
        self.ids = s.ids
        self.h, self.l, self.w = s.ids.shape


_world = None


def _init(path):
    global _world
    _world = World(path)


def hash3(a, b, c, d, e):
    h = (a.astype(np.int64) * 73856093) ^ (b.astype(np.int64) * 19349663) ^ (c.astype(np.int64) * 83492791) \
        ^ (d.astype(np.int64) * 374761393) ^ (e.astype(np.int64) * 668265263)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 1023) / 1023.0


def render_rows(args):
    shot, width, height, y0, y1 = args
    w = _world
    fov = math.radians(70.0)
    px = (np.arange(width) + 0.5) / width * 2 - 1
    py = 1 - (np.arange(y0, y1) + 0.5) / height * 2
    f = math.tan(fov / 2)
    aspect = width / height
    xs, ys = np.meshgrid(px * f * aspect, py * f)
    yaw, pitch = math.radians(shot['yaw']), math.radians(shot['pitch'])
    fwd = np.array([math.sin(yaw) * math.cos(pitch), math.sin(pitch), -math.cos(yaw) * math.cos(pitch)])
    right = np.array([math.cos(yaw), 0.0, math.sin(yaw)])
    up = np.cross(right, fwd)
    d = fwd[None, None, :] + xs[..., None] * right[None, None, :] + ys[..., None] * up[None, None, :]
    d = d.reshape(-1, 3)
    d /= np.linalg.norm(d, axis=1, keepdims=True)
    n = d.shape[0]
    o = np.array(shot['pos'], dtype=np.float64)
    safe = np.where(np.abs(d) < 1e-9, 1e-9, d)
    step = np.where(d >= 0, 1, -1).astype(np.int64)
    tdelta = np.abs(1.0 / safe)
    v = np.tile(np.floor(o).astype(np.int64), (n, 1))
    tmax = np.where(d >= 0, (v + 1 - o) / safe, (v - o) / safe)

    out = np.tile(FOG, (n, 1))
    dither = np.random.default_rng(7 + y0).random(n)
    idx = np.arange(n)
    wlen = np.zeros(n)
    prev_water = np.zeros(n, dtype=bool)
    slen = np.zeros(n)
    prev_smoke = np.zeros(n, dtype=bool)
    tprev = np.zeros(n)
    hit_v = np.zeros((n, 3), dtype=np.int64)
    hit_axis = np.zeros(n, dtype=np.int64)
    hit_t = np.zeros(n)
    hit = np.zeros(n, dtype=bool)
    for _ in range(520):
        if idx.size == 0:
            break
        tm = tmax[idx]
        ax = np.argmin(tm, axis=1)
        t = tm[np.arange(idx.size), ax]
        vv = v[idx]
        vv[np.arange(idx.size), ax] += step[idx, ax]
        v[idx] = vv
        tmax[idx, ax] += tdelta[idx, ax]
        inb = (vv[:, 0] >= 0) & (vv[:, 0] < w.w) & (vv[:, 1] >= 0) & (vv[:, 1] < w.h) & (vv[:, 2] >= 0) & (vv[:, 2] < w.l)
        c = np.zeros(idx.size, dtype=np.int8)
        c[inb] = w.cls[vv[inb, 1], vv[inb, 2], vv[inb, 0]]
        # Water length accumulates over the segment just crossed when the ray was inside water.
        wlen[idx] += np.where(prev_water[idx], t - tprev[idx], 0.0)
        prev_water[idx] = c == 2
        slen[idx] += np.where(prev_smoke[idx], t - tprev[idx], 0.0)
        prev_smoke[idx] = c == 4
        tprev[idx] = t
        solid = (c == 1) | ((c == 3) & (dither[idx] > 0.55))
        gone = ~inb
        if solid.any():
            k = idx[solid]
            hit[k] = True
            hit_v[k] = vv[solid]
            hit_axis[k] = ax[solid]
            hit_t[k] = t[solid]
        idx = idx[~(solid | gone)]

    k = np.nonzero(hit)[0]
    if k.size:
        hv = hit_v[k]
        bid = w.ids[hv[:, 1], hv[:, 2], hv[:, 0]]
        base = w.col[bid].copy()
        ha = hit_axis[k]
        t = hit_t[k]
        p = o[None, :] + d[k] * t[:, None]
        frac = p - hv
        fr = np.clip(frac, 0, 0.9999)
        # Texel on the two axes that are not the face normal.
        ua = np.where(ha == 0, 2, 0)
        va = np.where(ha == 1, 2, 1)
        tu = np.floor(fr[np.arange(k.size), ua] * 16).astype(np.int64)
        tv = np.floor(fr[np.arange(k.size), va] * 16).astype(np.int64)
        jitter = 0.90 + 0.18 * hash3(hv[:, 0], hv[:, 1], hv[:, 2], tu, tv)
        edge = (tu == 0) | (tv == 0)
        jitter = np.where(edge, jitter * 0.93, jitter)
        sgn = step[k, ha]
        shade = np.where(ha == 1, np.where(sgn < 0, 1.0, 0.5), np.where(ha == 0, 0.80, 0.68))
        emit = w.emit[bid]
        bloom = w.bloom[bid] & (hash3(hv[:, 0], hv[:, 1], hv[:, 2], tu + 7, tv + 3) < 0.28)
        base = np.where(bloom[:, None], np.array([232, 128, 176], dtype=np.float32), base)
        colour = base * jitter[:, None] * np.where(emit, 1.15, shade)[:, None]
        fog = 1 - np.exp(-t / 110.0)
        fog = np.where(emit, fog * 0.4, fog)
        colour = colour * (1 - fog[:, None]) + FOG * fog[:, None]
        a = 1 - np.exp(-wlen[k] / 5.0)
        colour = colour * (1 - a[:, None]) + WATER_TINT * a[:, None] * np.where(emit, 1.0, 0.55)[:, None] \
            + np.where(emit, 1.0, 0.0)[:, None] * (1 - a[:, None]) * 0
        sa = 1 - np.exp(-slen[k] / 1.6)
        colour = colour * (1 - sa[:, None]) + np.array([175, 175, 182], dtype=np.float32) * sa[:, None]
        out[k] = colour
    return np.clip(out, 0, 255).astype(np.uint8).reshape(y1 - y0, width, 3)


def render_shot(pool, shot, width, height):
    bands = 40
    ys = np.linspace(0, height, bands + 1).astype(int)
    jobs = [(shot, width, height, int(ys[i]), int(ys[i + 1])) for i in range(bands)]
    parts = pool.map(render_rows, jobs)
    return Image.fromarray(np.concatenate(parts, axis=0))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('schem')
    ap.add_argument('--shots', required=True)
    ap.add_argument('--out', default='outputs/views')
    ap.add_argument('--width', type=int, default=800)
    ap.add_argument('--only', default='')
    a = ap.parse_args()
    shots = json.load(open(a.shots, encoding='utf-8'))
    if a.only:
        keep = set(a.only.split(','))
        shots = [s for s in shots if s['name'] in keep]
    name = os.path.splitext(os.path.basename(a.schem))[0]
    outdir = os.path.join(a.out, name)
    os.makedirs(outdir, exist_ok=True)
    width, height = a.width, a.width * 9 // 16
    imgs = []
    with Pool(min(16, os.cpu_count() or 4), initializer=_init, initargs=(a.schem,)) as pool:
        for s in shots:
            im = render_shot(pool, s, width, height)
            d = ImageDraw.Draw(im)
            d.rectangle([0, 0, 8 + 7 * len(s['name']), 16], fill=(0, 0, 0))
            d.text((4, 2), s['name'], fill=(255, 255, 0))
            im.save(os.path.join(outdir, s['name'] + '.png'))
            imgs.append(im)
            print('rendered', s['name'], flush=True)
    cols = 2
    rows = (len(imgs) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * width + (cols - 1) * 6, rows * height + (rows - 1) * 6), (30, 30, 36))
    for i, im in enumerate(imgs):
        sheet.paste(im, ((i % cols) * (width + 6), (i // cols) * (height + 6)))
    sheet.save(os.path.join(a.out, name + '_views.png'))


if __name__ == '__main__':
    main()
