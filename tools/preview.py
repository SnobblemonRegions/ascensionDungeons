"""Renders review images of a Sponge .schem district: top-down, height map, floor-level plan and
cross-sections, each with a 32-block coordinate grid.

Markers: if <name>.markers.json sits next to the .schem (written by exportLandmarks) the named spots (entry, nav, loot,
explore, arena) and landmark boxes are drawn on the plan images; chests are found in the schematic itself.
Usage: python -I tools/preview.py <file.schem|dir> [--out outputs/previews] [--ymax 40]
                                   [--xcuts 128] [--zcuts 128]
Output: <out>/<name>/{topdown,height,floor,section_x<N>,section_z<N>}.png and <out>/<name>_sheet.png
"""
import argparse
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

SCALE = 3
GRID = 32

RULES = [  # first match wins (substring tests on the block id)
    (('red_mushroom_block',), (190, 40, 40)), (('brown_mushroom_block',), (150, 105, 70)),
    (('mushroom_stem',), (225, 215, 190)), (('mycelium',), (120, 100, 125)), (('mud',), (125, 90, 70)),
    (('shroomlight',), (240, 160, 70)), (('tuff',), (95, 100, 92)), (('podzol', 'coarse_dirt'), (100, 70, 40)),
    (('lava',), (255, 110, 20)), (('water',), (50, 110, 230)),
    (('chest',), (255, 220, 0)), (('amethyst',), (170, 90, 230)),
    (('obsidian',), (60, 20, 90)),
    (('froglight', 'sea_lantern', 'glowstone', 'lantern', 'shroomlight'), (255, 200, 120)),
    (('snow', 'calcite', 'quartz', 'diorite', 'powder'), (225, 235, 245)), (('ice',), (140, 190, 235)),
    (('blackstone', 'basalt', 'magma', 'netherrack'), (50, 48, 56)), (('deepslate',), (80, 80, 90)),
    (('leaves', 'azalea'), (50, 120, 45)), (('log', 'wood', 'stem'), (110, 80, 45)),
    (('grass', 'moss', 'fern', 'vine', 'bush'), (80, 150, 60)),
    (('stone_brick', 'tuff', 'cobble'), (140, 140, 130)),
    (('planks', 'fence'), (160, 125, 75)), (('sand',), (220, 205, 150)),
    (('dirt', 'mud', 'podzol', 'rooted'), (110, 80, 55)),
    (('glass',), (170, 215, 225)), (('iron', 'copper'), (190, 120, 90)),
]


# Exact ids that substring rules would get wrong (sand vs soul_sand and so on).
EXACT = {
    'sand': (224, 210, 160), 'red_sand': (190, 102, 48), 'sandstone': (218, 202, 150), 'smooth_sandstone': (226, 212, 160),
    'cut_sandstone': (222, 206, 154), 'chiseled_sandstone': (214, 196, 142), 'red_sandstone': (178, 92, 40),
    'smooth_red_sandstone': (182, 96, 44), 'cut_red_sandstone': (176, 90, 40), 'chiseled_red_sandstone': (172, 86, 38),
    'gold_block': (246, 208, 61), 'terracotta': (152, 94, 67), 'orange_terracotta': (160, 82, 45), 'red_terracotta': (143, 61, 46),
    'yellow_terracotta': (186, 133, 36), 'white_terracotta': (209, 178, 161), 'brown_terracotta': (77, 51, 36),
    'light_gray_terracotta': (135, 107, 98), 'pink_terracotta': (161, 78, 78), 'cactus': (48, 112, 40), 'glowstone': (255, 214, 130),
    'cherry_planks': (226, 178, 170), 'stripped_cherry_wood': (214, 150, 150), 'stripped_cherry_log': (214, 150, 150),
    'cherry_log': (86, 60, 66), 'cherry_leaves': (240, 160, 196), 'pink_petals': (246, 180, 210), 'pink_terracotta': (161, 78, 78),
    'red_concrete': (170, 40, 36), 'blackstone': (40, 36, 44), 'bamboo': (120, 160, 50), 'lantern': (255, 190, 90),
    'grass_block': (110, 160, 70), 'clay': (150, 156, 172), 'stone_bricks': (124, 124, 124), 'stone_brick_wall': (124, 124, 124),
    'redstone_lamp': (230, 160, 90),
    'lapis_block': (31, 67, 160), 'deepslate_tiles': (62, 62, 70), 'polished_deepslate': (76, 76, 84), 'blue_terracotta': (84, 72, 130),
    'blue_concrete': (45, 47, 170), 'blue_glazed_terracotta': (47, 64, 170), 'cyan_terracotta': (86, 110, 112),
    'chiseled_deepslate': (52, 52, 58), 'bookshelf': (140, 104, 60), 'enchanting_table': (150, 30, 70), 'deepslate_tile_slab': (62, 62, 70),
    'red_nether_bricks': (96, 20, 24), 'nether_bricks': (60, 28, 32), 'nether_wart_block': (130, 20, 22), 'crimson_planks': (110, 44, 62),
    'crimson_stem': (120, 40, 70), 'polished_blackstone_bricks': (44, 40, 48), 'polished_blackstone': (50, 46, 54),
    'gilded_blackstone': (70, 56, 40), 'shroomlight': (242, 166, 76), 'crimson_nylium': (150, 30, 40), 'chiseled_nether_bricks': (80, 22, 26),
    'red_stained_glass': (200, 40, 40), 'orange_stained_glass': (230, 130, 40), 'yellow_stained_glass': (240, 220, 60),
    'weeping_vines': (150, 30, 40), 'weeping_vines_plant': (150, 30, 40), 'crying_obsidian': (66, 22, 118), 'red_candle': (200, 60, 60),
    'crimson_stairs': (110, 44, 62),
    'dark_oak_planks': (66, 43, 20), 'spruce_planks': (114, 84, 48), 'stripped_dark_oak_log': (82, 60, 36), 'dark_oak_log': (60, 40, 18),
    'stripped_spruce_log': (140, 108, 64), 'red_wool': (160, 39, 34), 'brown_terracotta': (77, 51, 36), 'red_terracotta': (143, 61, 46),
    'dark_oak_slab': (66, 43, 20),
    'end_stone': (226, 226, 160), 'end_stone_bricks': (216, 220, 164), 'purpur_block': (170, 126, 170), 'purpur_pillar': (176, 130, 176),
    'obsidian': (22, 18, 34), 'chorus_plant': (110, 70, 120), 'chorus_flower': (160, 120, 160), 'magenta_stained_glass': (190, 70, 190),
    'magenta_glazed_terracotta': (208, 100, 191), 'purpur_slab': (170, 126, 170),
    'warped_nylium': (43, 140, 128), 'warped_wart_block': (20, 120, 110), 'warped_planks': (43, 104, 99), 'warped_stem': (58, 142, 140),
    'stripped_warped_stem': (80, 160, 150), 'soul_soil': (80, 62, 50), 'soul_lantern': (90, 220, 230), 'twisting_vines': (30, 180, 160),
    'twisting_vines_plant': (30, 180, 160), 'warped_roots': (30, 180, 160), 'nether_sprouts': (30, 180, 160), 'warped_fungus': (40, 200, 170), 'candle': (240, 220, 190), 'enchanting_table': (150, 30, 70), 'chain': (60, 64, 70),
}
def colour(b):
    if b in EXACT:
        return EXACT[b]


    for keys, c in RULES:
        if any(k in b for k in keys):
            return c
    return (115, 110, 105)


class Schem:
    def __init__(self, path):
        _, r = nbt.load(path)
        s = r.get('Schematic', r)
        self.w, self.h, self.l = s['Width'], s['Height'], s['Length']
        pal = {v: k.replace('minecraft:', '').split('[')[0] for k, v in s['Blocks']['Palette'].items()}  # id only: properties like waterlogged= must not match rules
        ids = np.array(nbt.varints(s['Blocks']['Data']), dtype=np.int32).reshape(self.h, self.l, self.w)
        self.ids = ids  # [y, z, x]
        n = max(pal) + 1
        self.names = [pal.get(i, 'air') for i in range(n)]
        self.air = np.array([x == 'air' or x.endswith('_air') or x.startswith('light') for x in self.names])
        self.water = np.array([x.startswith('water') for x in self.names])
        self.lava = np.array([x.startswith('lava') for x in self.names])
        self.chest = np.array(['chest' in x for x in self.names])
        self.col = np.array([colour(x) for x in self.names], dtype=np.float32)


def grid(img, w, h, flip_y=False):
    """Grid lines every GRID blocks with block-coordinate labels; the image is SCALE px per block."""
    d = ImageDraw.Draw(img)
    for gx in range(0, w + 1, GRID):
        px = gx * SCALE
        d.line([(px, 0), (px, h * SCALE)], fill=(90, 90, 100), width=1)
        d.text((px + 2, 2), str(gx), fill=(255, 255, 0))
    for gy in range(0, h + 1, GRID):
        py = gy * SCALE
        d.line([(0, py), (w * SCALE, py)], fill=(90, 90, 100), width=1)
        d.text((2, py + 2), str(h - gy if flip_y else gy), fill=(255, 255, 0))
    return img


def to_img(arr, w, h):
    return Image.fromarray(arr.clip(0, 255).astype(np.uint8)).resize((w * SCALE, h * SCALE), Image.NEAREST)


def topdown(s, ymax):
    top = min(ymax, s.h - 1)
    sub = s.ids[:top + 1]
    solid = ~s.air[sub]  # [y, z, x]
    has = solid.any(axis=0)
    ytop = top - np.argmax(solid[::-1], axis=0)  # highest solid y per (z, x)
    ids = np.take_along_axis(sub, ytop[None], axis=0)[0]
    shade = 0.55 + 0.45 * np.clip((ytop - 4) / 24.0, 0, 1)
    rgb = s.col[ids] * shade[..., None]
    rgb[~has] = (15, 15, 20)
    return rgb, np.where(has, ytop, -1)


def heightmap(ytop):
    """Surface height ramp (blue low, green mid, white high) with a contour line every 4 blocks."""
    h = np.where(ytop < 0, 0, ytop).astype(np.float32)
    t = np.clip((np.minimum(h, 28.0) - 4) / 24.0, 0, 1)  # walls above y28 saturate so floor relief stays visible
    rgb = np.stack([255 * np.clip(2 * t - 0.5, 0, 1),
                    255 * (0.25 + 0.75 * np.sin(np.pi * t * 0.9)),
                    255 * np.clip(1 - 2 * t, 0.15, 1)], -1)
    edge = np.zeros(h.shape, dtype=bool)
    q = h // 4
    edge[:, 1:] |= q[:, 1:] != q[:, :-1]
    edge[1:, :] |= q[1:, :] != q[:-1, :]
    rgb[edge] *= 0.55
    rgb[ytop < 0] = (15, 15, 20)
    return rgb


def floorplan(s, f=4):
    """What a player at floor level meets: walkable, blocked at head height, water, lava, chest."""
    out = np.full((s.l, s.w, 3), (15, 15, 20), dtype=np.float32)
    fl, h1, h2 = s.ids[f], s.ids[f + 1], s.ids[f + 2]
    floor = ~s.air[fl]
    free1 = s.air[h1] | s.water[h1]
    free2 = s.air[h2] | s.water[h2]
    out[floor] = (60, 60, 70)
    out[floor & free1 & free2 & ~s.lava[fl]] = (110, 170, 110)
    out[~free1 & ~s.lava[h1]] = (150, 80, 70)
    out[s.water[fl] | s.water[h1]] = (50, 110, 230)
    out[s.lava[fl] | s.lava[h1]] = (255, 110, 20)
    out[s.chest[h1] | s.chest[fl]] = (255, 220, 0)
    return out


def spawnable(s, f=4, clear=3):
    """Cells where a ground mob could stand: solid dry floor with `clear` open blocks above (roof/light rules aside)."""
    ok = ~s.air[s.ids[f]] & ~s.lava[s.ids[f]] & ~s.water[s.ids[f]]
    for dy in range(1, clear + 1):
        ok &= s.air[s.ids[f + dy]]
    out = np.full((s.l, s.w, 3), (15, 15, 20), dtype=np.float32)
    out[~s.air[s.ids[f]]] = (45, 45, 55)
    out[ok] = (120, 200, 120)
    return out, ok


def marks(img, s, markers, draw_landmarks=True):
    """Overlays chests, landmark boxes and named spots (district block coordinates) on a plan image."""
    d = ImageDraw.Draw(img)
    for y, z, x in np.argwhere(s.chest[s.ids]):
        cx, cy = x * SCALE + SCALE // 2, z * SCALE + SCALE // 2
        d.rectangle([cx - 4, cy - 4, cx + 4, cy + 4], outline=(255, 230, 0), width=2)
    if draw_landmarks:
        for lm in markers.get('landmarks', []):
            x0, z0 = lm['x'] * SCALE, lm['z'] * SCALE
            d.rectangle([x0, z0, x0 + lm['w'] * SCALE, z0 + lm['l'] * SCALE], outline=(255, 60, 220), width=2)
            d.text((x0 + 3, z0 + 3), lm['id'], fill=(255, 150, 240))
    for name, (x, z) in markers.get('spots', {}).items():
        cx, cy = x * SCALE + SCALE // 2, z * SCALE + SCALE // 2
        col = (0, 255, 255) if name == 'entry' else (255, 255, 255)
        d.polygon([(cx, cy - 8), (cx + 8, cy), (cx, cy + 8), (cx - 8, cy)], outline=col, fill=(0, 0, 0))
        d.text((cx + 10, cy - 5), name, fill=col)
    return img


def section(s, axis, at):
    """axis 'z' cuts at a fixed z (x across, y up); 'x' cuts at a fixed x (z across, y up)."""
    sl = s.ids[:, at, :] if axis == 'z' else s.ids[:, :, at]
    rgb = s.col[sl].copy()
    rgb[s.air[sl]] = (15, 15, 20)
    return rgb[::-1]  # y up


def render(path, out, ymax, xcuts, zcuts):
    name = os.path.splitext(os.path.basename(path))[0]
    d = os.path.join(out, name)
    os.makedirs(d, exist_ok=True)
    s = Schem(path)
    mpath = path[:-len('.schem')] + '.markers.json' if path.endswith('.schem') else ''
    markers = json.load(open(mpath, encoding='utf-8')) if os.path.exists(mpath) else {}
    imgs = {}
    rgb, ytop = topdown(s, ymax)
    imgs['topdown'] = grid(to_img(rgb, s.w, s.l), s.w, s.l)
    imgs['height'] = grid(to_img(heightmap(ytop), s.w, s.l), s.w, s.l)
    imgs['floor'] = grid(to_img(floorplan(s), s.w, s.l), s.w, s.l)
    sp, ok = spawnable(s)
    imgs['spawn'] = grid(to_img(sp, s.w, s.l), s.w, s.l)
    for k in ('topdown', 'height', 'floor', 'spawn'):
        marks(imgs[k], s, markers)
    print('%s: %d spawnable floor cells (%.0f%% of the room), %d chests' % (
        name, ok.sum(), 100.0 * ok.sum() / max(1, (~s.air[s.ids[4]]).sum()), s.chest[s.ids].sum()))
    for x in xcuts:
        if 0 <= x < s.w:
            imgs['section_x%d' % x] = grid(to_img(section(s, 'x', x), s.l, s.h), s.l, s.h, True)
    for z in zcuts:
        if 0 <= z < s.l:
            imgs['section_z%d' % z] = grid(to_img(section(s, 'z', z), s.w, s.h), s.w, s.h, True)
    for k, im in imgs.items():
        im.save(os.path.join(d, k + '.png'))
    plans = [imgs[k] for k in ('topdown', 'height', 'floor')]
    secs = [im for k, im in imgs.items() if k.startswith('section')][:2]
    width = sum(i.width for i in plans) + 16
    height = plans[0].height + 8 + max([i.height for i in secs] or [0])
    sheet = Image.new('RGB', (width, height), (30, 30, 36))
    x = 0
    for im in plans:
        sheet.paste(im, (x, 0))
        x += im.width + 8
    x = 0
    for im in secs:
        sheet.paste(im, (x, plans[0].height + 8))
        x += im.width + 8
    sheet.save(os.path.join(out, name + '_sheet.png'))
    print('wrote', d, sorted(imgs))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('src')
    ap.add_argument('--out', default='outputs/previews')
    ap.add_argument('--ymax', type=int, default=40)
    ap.add_argument('--xcuts', default='128')
    ap.add_argument('--zcuts', default='128')
    a = ap.parse_args()
    ints = lambda t: [int(v) for v in t.split(',') if v]
    if os.path.isdir(a.src):
        files = [os.path.join(a.src, f) for f in sorted(os.listdir(a.src)) if f.endswith('_district.schem')]
    else:
        files = [a.src]
    for f in files:
        render(f, a.out, a.ymax, ints(a.xcuts), ints(a.zcuts))


if __name__ == '__main__':
    main()
