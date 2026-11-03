"""Writes tools/shots/<biome>.json from compact tuples: python tools/mkshots.py glacial_vault "name,x,z,yaw,pitch" ..."""
import json, sys
name = sys.argv[1]
out = []
for t in sys.argv[2:]:
    n, x, z, yaw, pitch = t.split(',')
    out.append({"name": n, "pos": [float(x), 6.6, float(z)], "yaw": float(yaw), "pitch": float(pitch)})
json.dump(out, open('tools/shots/%s.json' % name, 'w'), indent=1)
