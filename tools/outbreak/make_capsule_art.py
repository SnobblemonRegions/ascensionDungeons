#!/usr/bin/env python3
"""Writes the sealed Pokemon capsule art into resourcepack/: one lab tank per find tier, and the item model overrides that show it.

A sealed capsule (still at risk in a dive) is Cobblemon's ball for its tier with custom model data 9201..9205
(outbreak/CapsuleLook.modelId). This pack draws those as a steel-capped glass tank of tier-coloured fluid with only an anonymous
dark shape inside. It is our own art, drawn here from shapes: no Pokemon is depicted. Once a capsule is safe it becomes
Cobblemon's pokemon_model item, which each player's Cobblemon draws itself, so no Pokemon art is ever shipped.

Usage: python3 make_capsule_art.py <resourcepack dir>
Needs Pillow. Re-run after changing the tables; do not hand-edit the output.
"""
import json, os, sys
from PIL import Image, ImageDraw, ImageFilter

S = 64
# tier -> (model id, the ball item it rides on, fluid top colour, fluid bottom colour). Keep in step with outbreak/CapsuleLook.
TIERS = [
    ("standard", 9201, "poke_ball", (196, 226, 240), (140, 186, 214)),
    ("starter", 9202, "great_ball", (140, 232, 140), (64, 164, 86)),
    ("pseudo", 9203, "ultra_ball", (255, 182, 104), (214, 108, 48)),
    ("legendary", 9204, "master_ball", (246, 222, 150), (204, 162, 72)),
    ("mythical", 9205, "cherish_ball", (255, 164, 216), (148, 140, 255)),
]
OUTLINE = (24, 26, 34, 255)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shape(top):
    """An anonymous curled shape: a body, a head and a tail, soft-edged and dark, drawn large and scaled down."""
    k = 8
    big = Image.new("L", (S * k, S * k), 0)
    d = ImageDraw.Draw(big)
    d.ellipse([22 * k, 26 * k, 42 * k, 46 * k], fill=255)            # body
    d.ellipse([27 * k, 17 * k, 39 * k, 29 * k], fill=255)            # head
    d.arc([17 * k, 30 * k, 37 * k, 50 * k], 90, 220, fill=255, width=4 * k)   # tail curling round
    mask = big.resize((S, S), Image.LANCZOS).filter(ImageFilter.GaussianBlur(0.6))
    dark = tuple(int(c * 0.28) for c in top)
    layer = Image.new("RGBA", (S, S), dark + (0,))
    layer.putalpha(mask.point(lambda a: int(a * 0.82)))
    return layer


def tank(top, bottom):
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    gx0, gx1, gy0, gy1 = 15, 48, 12, 51
    for y in range(gy0, gy1 + 1):
        c = lerp(top, bottom, (y - gy0) / (gy1 - gy0))
        for x in range(gx0, gx1 + 1):
            edge = abs((x - (gx0 + gx1) / 2) / ((gx1 - gx0) / 2))
            f = 1 - 0.35 * edge ** 2
            im.putpixel((x, y), tuple(int(v * f) for v in c) + (240,))
    im.alpha_composite(shape(top))
    # two faint glints where eyes might be: something is in there
    for (x, y) in [(31, 22), (35, 22)]:
        im.putpixel((x, y), (255, 255, 255, 150))
    for (bx, by, r) in [(19, 46, 1), (44, 40, 1), (21, 31, 0), (43, 21, 0), (24, 16, 0), (40, 47, 0)]:
        d.ellipse([bx - r, by - r, bx + r, by + r], fill=(255, 255, 255, 170))
    d.rectangle([gx0 - 1, gy0, gx0 - 1, gy1], fill=OUTLINE)
    d.rectangle([gx1 + 1, gy0, gx1 + 1, gy1], fill=OUTLINE)
    d.rectangle([gx0 + 2, gy0 + 2, gx0 + 3, gy1 - 6], fill=(255, 255, 255, 120))
    d.rectangle([gx0 + 5, gy0 + 2, gx0 + 5, gy0 + 14], fill=(255, 255, 255, 80))
    d.rectangle([gx1 - 3, gy0 + 4, gx1 - 3, gy1 - 3], fill=(255, 255, 255, 50))

    def cap(y0, y1, x0, x1, light):
        d.rectangle([x0, y0, x1, y1], fill=OUTLINE)
        for y in range(y0 + 1, y1):
            c = lerp((222, 226, 234), (120, 126, 140), (y - y0) / max(1, y1 - y0))
            d.line([x0 + 1, y, x1 - 1, y], fill=c + (255,))
        d.line([x0 + 2, y0 + 1, x1 - 2, y0 + 1], fill=(250, 252, 255, 255))
        if light:
            d.rectangle([29, y0 + 2, 34, y0 + 4], fill=OUTLINE)
            d.rectangle([30, y0 + 3, 33, y0 + 3], fill=top + (255,))

    cap(4, 11, 12, 51, True)
    cap(52, 59, 10, 53, False)
    for bx in (15, 31, 47):
        d.rectangle([bx, 55, bx + 1, 56], fill=(70, 74, 86, 255))
    d.rectangle([22, 1, 41, 4], fill=OUTLINE)
    d.rectangle([23, 2, 40, 3], fill=(170, 176, 190, 255))
    return im


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main(pack):
    overrides = [{"predicate": {"custom_model_data": mid}, "model": f"ascensiondungeons:item/capsule/{name}"} for name, mid, _, _, _ in TIERS]
    for name, mid, ball, top, bottom in TIERS:
        tex = os.path.join(pack, "assets/ascensiondungeons/textures/item/capsule", name + ".png")
        os.makedirs(os.path.dirname(tex), exist_ok=True)
        tank(top, bottom).save(tex)
        write_json(os.path.join(pack, "assets/ascensiondungeons/models/item/capsule", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"ascensiondungeons:item/capsule/{name}"}})
        # Cobblemon's own model for the ball, unchanged, plus the tank for the capsule numbers (all five, so any ball works).
        write_json(os.path.join(pack, "assets/cobblemon/models/item", ball + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"cobblemon:item/poke_balls/{ball}"}, "overrides": overrides})
    print("wrote", len(TIERS), "capsule tanks into", pack)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "resourcepack")
