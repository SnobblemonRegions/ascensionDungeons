"""Turns a finished kit.Dungeon into the files the mod loads: dungeon.schem + layout.json (+ a report)."""
import json, os
import numpy as np
import kit, check
from kit import base, is_air, is_water


def final_reach(d):
    ok, out, stages, fired, a = check.playthrough(d)
    fired = set(fired)
    m = check.Model(d, a)
    return m, check.reach_all(d, m, fired), a


def layout(d, origin=(0, 20, 0)):
    m, reached, a = final_reach(d)
    rng = d.rng
    rooms = []
    lights, light_seen = [], set()
    for r in d.rooms:
        stand = []
        for (x, y, z) in reached:
            if not check.in_box(r, (x, y, z)): continue
            if m.iswater(x, y, z) or m.iswater(x, y + 1, z): continue
            if not all(is_air(d.pal[m.pid(x, y + k, z)]) for k in (0, 1, 2, 3)): continue
            g = d.pal[m.pid(x, y - 1, z)]
            if base(g) in kit.UNSAFE_GROUND or not m.gd[m.pid(x, y - 1, z)]: continue
            if any(m.iswater(x + dx, y, z + dz) or m.iswater(x + dx, y - 1, z + dz) for dx, dz in check.H4): continue
            stand.append((x, y, z))
        stand.sort(); rng.shuffle(stand)
        rooms.append({'id': r['id'], 'name': r['name'], 'min': r['min'], 'max': r['max'], 'spawnCells': [list(c) for c in stand[:90]], 'standable': len(stand)})
        # lights: one invisible light per 5x5x5 bucket of reachable cells of the room (3 above the floor; waterlogged when in water)
        buckets = {}
        for c in reached:
            if check.in_box(r, c): buckets.setdefault((c[0] // 5, c[1] // 5, c[2] // 5), c)
        for (x, y, z) in buckets.values():
            for dy in (3, 2, 1, 0):
                q = (x, y + dy, z)
                s = d.pal[m.pid(*q)] if m.pid(*q) < len(d.pal) else None
                if q in light_seen or s is None: continue
                if is_air(s): lights.append([*q, 0]); light_seen.add(q); break
                if base(s) == 'water': lights.append([*q, 1]); light_seen.add(q); break
    data = {
        'version': 2, 'id': d.id, 'name': d.name, 'biome': d.biome, 'origin': list(origin), 'size': [d.W, d.H, d.L],
        'entry': list(d.entry), 'lightLevel': 10, 'lootTable': d.loot_table,
        'rooms': rooms, 'landmarks': d.landmarks, 'lights': lights, 'seals': d.seals, 'chests': d.chests, 'keys': d.keys,
        'mechanics': d.mechanics, 'hazards': d.hazards, 'encounters': d.encounters, 'bosses': d.bosses, 'pads': d.pads,
        'completion': d.completion, 'markers': d.markers, 'pokemon': d.pokemon,
    }
    return data


def write(d, outdir, origin=(0, 20, 0)):
    os.makedirs(outdir, exist_ok=True)
    n = d.write_schem(os.path.join(outdir, 'dungeon.schem'))
    data = layout(d, origin)
    with open(os.path.join(outdir, 'layout.json'), 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=1)
    return n, data
