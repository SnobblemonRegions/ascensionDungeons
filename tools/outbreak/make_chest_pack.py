"""Builds the Outbreak chest resource pack: one original model per chest kind, drawn procedurally (no vanilla or third-party art).

Usage: python tools/outbreak/make_chest_pack.py <pack_dir> [preview.png]

Each chest is a list of boxes in block-model units (a block is 16). The model is a little larger than a vanilla chest so the
item display that shows it hides the real Lootr chest underneath. The front of a chest faces north (z = 0).
The server shows a model with an item_display holding minecraft:popped_chorus_fruit with custom_model_data = the ids below
(ChestRarity.modelId in the mod; keep the two in step).
"""
import json, os, random, sys
from PIL import Image, ImageDraw

TILE = 16
ATLAS = 64  # 4 x 4 tiles

def clamp(v): return max(0, min(255, int(v)))
def shade(c, f): return tuple(clamp(x * f) for x in c)
def mix(a, b, t): return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))
def hexc(h): return ((h >> 16) & 255, (h >> 8) & 255, h & 255)

def planks(c, rng):
    im = Image.new('RGB', (TILE, TILE))
    for y in range(TILE):
        for x in range(TILE):
            f = 0.86 + 0.14 * rng.random()
            if y % 4 == 3: f *= 0.62                      # the gap between planks
            if (x + (y // 4) * 5) % 9 == 0: f *= 0.8      # a nail / board end
            im.putpixel((x, y), shade(c, f))
    return im

def metal(c, rng, rivets=True):
    im = Image.new('RGB', (TILE, TILE))
    for y in range(TILE):
        for x in range(TILE):
            f = 0.82 + 0.12 * rng.random() + 0.10 * (1 - y / TILE)
            if x in (0, TILE - 1) or y in (0, TILE - 1): f *= 0.7
            im.putpixel((x, y), shade(c, f))
    if rivets:
        for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
            im.putpixel((x, y), shade(c, 1.45)); im.putpixel((x + 1, y + 1) if x < 14 and y < 14 else (x, y), shade(c, 0.5))
    return im

def band(c, rng):
    im = Image.new('RGB', (TILE, TILE))
    for y in range(TILE):
        for x in range(TILE):
            f = 0.9 + 0.1 * rng.random()
            if x % 4 == 0: f *= 1.25
            if x % 4 == 3: f *= 0.7
            im.putpixel((x, y), shade(c, f))
    return im

def gem(c, rng, dark):
    im = Image.new('RGB', (TILE, TILE), dark)
    for y in range(TILE):
        for x in range(TILE):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 7.5:
                f = 1.5 - d / 6.5 + (0.25 if x < 7 and y < 7 else 0)
                im.putpixel((x, y), shade(c, max(0.45, f)))
    return im

def stone(c, rng):
    im = Image.new('RGB', (TILE, TILE))
    for y in range(TILE):
        for x in range(TILE):
            f = 0.75 + 0.3 * rng.random()
            if (x * 7 + y * 3) % 11 == 0: f *= 0.7
            im.putpixel((x, y), shade(c, f))
    return im

def clock(face, hand, rim, rng):
    im = Image.new('RGB', (TILE, TILE), rim)
    d = ImageDraw.Draw(im)
    d.ellipse((1, 1, 14, 14), fill=face, outline=shade(rim, 0.6))
    for (x, y) in ((7, 2), (8, 2), (13, 7), (13, 8), (7, 13), (8, 13), (2, 7), (2, 8)): im.putpixel((x, y), shade(rim, 0.5))
    d.line((8, 8, 8, 4), fill=hand); d.line((8, 8, 11, 9), fill=hand)
    return im

def eye(c, dark, rng):
    im = metal(dark, rng, rivets=False)
    d = ImageDraw.Draw(im)
    d.polygon([(2, 8), (8, 3), (13, 8), (8, 12)], fill=shade(c, 0.55))
    d.ellipse((5, 5, 10, 10), fill=c)
    d.rectangle((7, 6, 8, 9), fill=(20, 0, 0))
    im.putpixel((6, 6), (255, 220, 220))
    return im

def atlas(tiles):
    im = Image.new('RGBA', (ATLAS, ATLAS), (0, 0, 0, 0))
    for i, t in enumerate(tiles): im.paste(t, ((i % 4) * TILE, (i // 4) * TILE))
    return im

# ---- geometry ----------------------------------------------------------------------------------------------------
def box(x1, y1, z1, x2, y2, z2, tile): return (x1, y1, z1, x2, y2, z2, tile)

def chest_body(body=0, lid=1, lid_h=5.0, stepped=False):
    b = [box(0.5, 0, 0.5, 15.5, 9.5, 15.5, body), box(0.5, 9.5, 0.5, 15.5, 9.5 + lid_h - (2.5 if stepped else 0), 15.5, lid)]
    if stepped: b.append(box(2.5, 9.5 + lid_h - 2.5, 2.5, 13.5, 9.5 + lid_h, 13.5, lid))
    return b

def straps(tile, top=14.8):
    return [box(3, 0, 0.2, 5, top, 15.8, tile), box(11, 0, 0.2, 13, top, 15.8, tile)]

def corners(tile, size=3, top=14.5):
    out = []
    for x in (0.1, 15.9 - size):
        for z in (0.1, 15.9 - size):
            out.append(box(x, 0, z, x + size, size, z + size, tile)); out.append(box(x, top - size + 0.3, z, x + size, top + 0.3, z + size, tile))
    return out

def latch(tile, w=2, h=4, y=7): return [box(8 - w / 2, y, -0.4, 8 + w / 2, y + h, 0.5, tile)]

def growth(tile, count, seed):
    """Crystal growths: `count` of them, cycling front, top, west, back, east so every side shows some."""
    rng = random.Random(seed)
    out = []
    for i in range(count):
        s = rng.choice((2.5, 3, 3.5))
        side = i % 5
        a, bb = rng.uniform(1.5, 14 - s), rng.uniform(1, 9 - s) if side != 1 else 0
        deep = rng.uniform(1.0, 1.8)
        if side == 0: out.append(box(a, bb, 0.5 - deep, a + s, bb + s, 0.6, tile))                 # front
        elif side == 1:                                                                                # top: a spike
            c = rng.uniform(1.5, 14 - s)
            out.append(box(a, 14.4, c, a + s * 0.8, 14.5 + rng.uniform(1.5, 4.0), c + s * 0.8, tile))
        elif side == 2: out.append(box(0.5 - deep, bb, a, 0.6, bb + s, a + s, tile))                 # west
        elif side == 3: out.append(box(a, bb, 15.4, a + s, bb + s, 15.5 + deep, tile))               # back
        else: out.append(box(15.4, bb, a, 15.5 + deep, bb + s, a + s, tile))                         # east
    return out

WOOD, DARKWOOD, IRON, GOLD = (139, 98, 55), (84, 58, 36), (150, 154, 162), (232, 190, 70)
RARITY = {'common': 0xC1C5CF, 'uncommon': 0x1BD7E8, 'rare': 0x38CCEF, 'epic': 0xAF69FF}

def designs():
    out = {}
    r = random.Random(7)
    # Common: a plain plank chest with iron straps.
    out['common'] = (9101, atlas([planks(WOOD, r), planks(shade(WOOD, 0.9), r), band(IRON, r), metal(shade(IRON, 0.8), r)]),
                     chest_body() + straps(2) + latch(3))
    # Uncommon: the same chest, capped in cyan metal with a gem latch.
    cy = hexc(RARITY['uncommon'])
    out['uncommon'] = (9102, atlas([planks(DARKWOOD, r), planks(shade(DARKWOOD, 1.1), r), band(shade(cy, 0.75), r), gem(cy, r, shade(cy, 0.35)), metal(shade(cy, 0.7), r)]),
                       chest_body() + straps(2) + corners(4) + latch(3, 3, 4, 6.5))
    # Rare: blue steel, a stepped lid, a gold seam and lock plate.
    bl = hexc(0x2F6FD0)
    out['rare'] = (9103, atlas([metal(bl, r), metal(shade(bl, 1.15), r), band(GOLD, r), gem(hexc(RARITY['rare']), r, shade(GOLD, 0.6)), metal(GOLD, r, rivets=False)]),
                   chest_body(0, 1, 6.0, stepped=True) + [box(0.2, 8.7, 0.2, 15.8, 10.3, 15.8, 2)] + corners(4, 2.5, 9) + [box(5.5, 5.5, -0.5, 10.5, 11.5, 0.5, 4)] + latch(3, 3, 3, 7))
    # Epic: violet and gold, corner pillars, crystals growing from the lid.
    vi = hexc(0x6E3BC4); cr = hexc(RARITY['epic'])
    crystals = [box(4, 15.5, 4, 6, 19.5, 6, 3), box(9.5, 15.5, 5, 11.5, 21, 7, 3), box(6.5, 15.5, 9, 9, 18.5, 11.5, 3), box(11, 15.5, 10.5, 12.5, 18, 12, 3), box(3.5, 15.5, 10.5, 5, 17.5, 12, 3)]
    pillars = [box(x, 0, z, x + 2.2, 15.2, z + 2.2, 4) for x in (-0.1, 13.9) for z in (-0.1, 13.9)]
    out['epic'] = (9104, atlas([metal(vi, r), metal(shade(vi, 1.2), r), band(GOLD, r), gem(shade(cr, 1.15), r, shade(cr, 0.5)), metal(GOLD, r, rivets=False)]),
                   chest_body(0, 1, 6.0, stepped=True) + pillars + [box(0.2, 8.7, 0.2, 15.8, 10.3, 15.8, 2)] + crystals + [box(5, 4.5, -0.6, 11, 11.5, 0.5, 4)] + latch(3, 4, 4, 6))
    # Timer chest: gold, a clock dial on the front; chained while sealed.
    gold_tiles = lambda face, hand: atlas([metal(GOLD, r), metal(shade(GOLD, 1.1), r), band(shade(IRON, 0.45), r), clock(face, hand, shade(GOLD, 0.7), r), metal(shade(GOLD, 0.8), r, rivets=False)])
    dial = [box(3.5, 1.5, -0.6, 12.5, 10.5, 0.5, 3)]
    chains = [box(7, 0, 0.1, 9, 15.0, 15.9, 2), box(0.1, 5.5, 7, 15.9, 7.5, 9, 2), box(0.1, 11.5, 7, 15.9, 13.0, 9, 2)]
    out['timer_sealed'] = (9105, gold_tiles((60, 50, 30), (255, 80, 60)), chest_body() + corners(4, 2.5) + chains + dial)
    out['timer_open'] = (9106, gold_tiles((235, 255, 235), (40, 160, 60)), chest_body() + corners(4, 2.5) + dial)
    # Strongbox: a squat riveted safe with a red eye.
    nt = (58, 54, 62)
    out['strongbox'] = (9107, atlas([metal(nt, r), metal(shade(nt, 1.25), r), band(shade(nt, 0.6), r), eye((230, 40, 50), shade(nt, 0.7), r), metal((120, 30, 36), r, rivets=False)]),
                        [box(0, 0, 0, 16, 12.5, 16, 0), box(1, 12.5, 1, 15, 14.5, 15, 1), box(4.5, 14.5, 4.5, 11.5, 15.5, 11.5, 4)]
                        + [box(x, 0, z, x + 3, 13, z + 3, 2) for x in (-0.4, 13.4) for z in (-0.4, 13.4)] + [box(4, 2.5, -0.7, 12, 10.5, 0.5, 3)])
    # Outbreak chest: stone, with growths in the colour of the rarity it has reached; more growths the higher it is.
    for i, (name, colour) in enumerate(RARITY.items()):
        c = hexc(colour)
        out['outbreak_' + name] = (9111 + i, atlas([stone((96, 104, 110), r), stone((112, 120, 126), r), band(shade(c, 0.7), r), gem(c, r, shade(c, 0.4)), stone(shade(c, 0.6), r)]),
                                   chest_body() + [box(0.2, 8.9, 0.2, 15.8, 10.1, 15.8, 2)] + growth(3, 3 + 4 * i, 100 + i) + latch(3, 3, 3, 7))
    return out

# ---- model json --------------------------------------------------------------------------------------------------
def face_uv(tile, w, h):
    tx, ty = (tile % 4) * 4.0, (tile // 4) * 4.0
    return [tx, ty, round(tx + min(16, w) / 4.0, 3), round(ty + min(16, h) / 4.0, 3)]

def model(name, boxes):
    els = []
    for (x1, y1, z1, x2, y2, z2, t) in boxes:
        dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
        faces = {'north': face_uv(t, dx, dy), 'south': face_uv(t, dx, dy), 'east': face_uv(t, dz, dy), 'west': face_uv(t, dz, dy), 'up': face_uv(t, dx, dz), 'down': face_uv(t, dx, dz)}
        els.append({'from': [x1, y1, z1], 'to': [x2, y2, z2], 'faces': {k: {'uv': v, 'texture': '#0'} for k, v in faces.items()}})
    tex = 'ascensiondungeons:block/chest/' + name
    return {'textures': {'0': tex, 'particle': tex}, 'elements': els}

# ---- preview (isometric, painter's algorithm; a sanity check, not how the game lights it) ---------------------------
def preview(all_designs, path, scale=7):
    cols = 4
    cell = int(34 * scale)
    rows = (len(all_designs) + cols - 1) // cols
    img = Image.new('RGB', (cols * cell, rows * cell + 4), (38, 42, 50))
    d = ImageDraw.Draw(img)
    for n, (name, (mid, tex, boxes)) in enumerate(all_designs.items()):
        ox, oy = (n % cols) * cell + cell // 2, (n // cols) * cell + int(cell * 0.42)
        def proj(x, y, z):
            X, Z = 16 - z, 16 - x      # look at the north (front) and west faces
            return (ox + (X - Z) * 0.866 * scale, oy + (X + Z) * 0.5 * scale - y * scale + 6 * scale)
        px = tex.convert('RGB').load()
        def texel(t, u, v): return px[(t % 4) * TILE + int(u) % TILE, (t // 4) * TILE + int(v) % TILE]
        def hidden(x, y, z, me):
            # A texel is not drawn when the point just outside it lies inside another box (the face is buried).
            for b in boxes:
                if b is not me and b[0] < x < b[3] and b[1] < y < b[4] and b[2] < z < b[5]: return True
            return False
        for me in sorted(boxes, key=lambda b: -(b[0] + b[3]) / 2 - (b[2] + b[5]) / 2 + (b[1] + b[4]) / 2 * 0.001 + (b[4]) * 0.5):
            (x1, y1, z1, x2, y2, z2, t) = me
            def quad(pts, c, probe):
                if not hidden(probe[0], probe[1], probe[2], me): d.polygon([proj(*p) for p in pts], fill=c)
            step = 1.0
            x = x1
            while x < x2:                      # top
                z = z1
                while z < z2:
                    xe, ze = min(x2, x + step), min(z2, z + step)
                    quad([(x, y2, z), (xe, y2, z), (xe, y2, ze), (x, y2, ze)], texel(t, x - x1, z - z1), ((x + xe) / 2, y2 + 0.05, (z + ze) / 2)); z += step
                x += step
            x = x1
            while x < x2:                      # north (front)
                y = y1
                while y < y2:
                    xe, ye = min(x2, x + step), min(y2, y + step)
                    quad([(x, y, z1), (xe, y, z1), (xe, ye, z1), (x, ye, z1)], shade(texel(t, x - x1, y2 - y - 1), 0.82), ((x + xe) / 2, (y + ye) / 2, z1 - 0.05)); y += step
                x += step
            z = z1
            while z < z2:                      # west
                y = y1
                while y < y2:
                    ze, ye = min(z2, z + step), min(y2, y + step)
                    quad([(x1, y, z), (x1, y, ze), (x1, ye, ze), (x1, ye, z)], shade(texel(t, z - z1, y2 - y - 1), 0.62), (x1 - 0.05, (y + ye) / 2, (z + ze) / 2)); y += step
                z += step
        d.text((ox - cell // 2 + 6, (n // cols) * cell + 4), '%s  (%d)' % (name, mid), fill=(230, 230, 230))
    img.save(path)

def main():
    pack = sys.argv[1]
    ds = designs()
    os.makedirs(os.path.join(pack, 'assets/ascensiondungeons/textures/block/chest'), exist_ok=True)
    os.makedirs(os.path.join(pack, 'assets/ascensiondungeons/models/chest'), exist_ok=True)
    os.makedirs(os.path.join(pack, 'assets/minecraft/models/item'), exist_ok=True)
    overrides = []
    for name, (mid, tex, boxes) in ds.items():
        tex.save(os.path.join(pack, 'assets/ascensiondungeons/textures/block/chest', name + '.png'))
        with open(os.path.join(pack, 'assets/ascensiondungeons/models/chest', name + '.json'), 'w') as f: json.dump(model(name, boxes), f, separators=(',', ':'))
        overrides.append({'predicate': {'custom_model_data': mid}, 'model': 'ascensiondungeons:chest/' + name})
    overrides.sort(key=lambda o: o['predicate']['custom_model_data'])
    with open(os.path.join(pack, 'assets/minecraft/models/item/popped_chorus_fruit.json'), 'w') as f:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/popped_chorus_fruit'}, 'overrides': overrides}, f, indent=2)
    with open(os.path.join(pack, 'pack.mcmeta'), 'w') as f:
        json.dump({'pack': {'pack_format': 34, 'description': 'Ascension Dungeons: Outbreak chest models'}}, f, indent=2)
    if len(sys.argv) > 2: preview(ds, sys.argv[2])
    print('wrote', len(ds), 'chest models:', ', '.join('%s=%d' % (n, v[0]) for n, v in ds.items()))

if __name__ == '__main__':
    main()
