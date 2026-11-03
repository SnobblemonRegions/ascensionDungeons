"""Writes a shots file aimed at ambience blocks (effect markers) of a district schematic.
Usage: python tools/effect_shots.py <file.schem> <block id substring> <out.json> [count]"""
import json, math, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview


def main():
    path, key, out = sys.argv[1], sys.argv[2], sys.argv[3]
    count = int(sys.argv[4]) if len(sys.argv) > 4 else 3
    s = preview.Schem(path)
    ids = [i for i, n in enumerate(s.names) if key in n]
    pts = np.argwhere(np.isin(s.ids, ids))  # y, z, x
    rng = np.random.default_rng(3)
    rng.shuffle(pts)

    def walkable(x, z):
        if not (0 <= x < s.w and 0 <= z < s.l):
            return False
        return (not s.air[s.ids[4][z][x]]) and s.air[s.ids[5][z][x]] and s.air[s.ids[6][z][x]] and not s.water[s.ids[4][z][x]]

    shots = []
    for y, z, x in pts:
        if len(shots) >= count:
            break
        for dist in (12, 16, 9, 20):
            found = None
            for k in range(8):
                a = 2 * math.pi * k / 8
                cx, cz = int(round(x - dist * math.sin(a))), int(round(z + dist * math.cos(a)))
                if walkable(cx, cz):
                    found = (cx, cz, a)
                    break
            if found:
                cx, cz, a = found
                yaw = math.degrees(a) % 360
                pitch = math.degrees(math.atan2(y - 6.6, dist))
                shots.append({"name": "%02d_%s" % (len(shots) + 1, key.replace('_', '')), "pos": [cx + .5, 6.6, cz + .5],
                              "yaw": round(yaw, 1), "pitch": round(max(-10, min(60, pitch)), 1)})
                break
    json.dump(shots, open(out, 'w'), indent=1)
    print(len(pts), 'markers;', len(shots), 'shots')


main()
