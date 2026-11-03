import sys, collections
sys.path.insert(0, 'tools')
import nbt

path, pat = sys.argv[1], sys.argv[2]
name, d = nbt.load(path)
d = d.get('Schematic', d)
W, H, L = d['Width'], d['Height'], d['Length']
blk = d['Blocks'] if 'Blocks' in d else d
pal = blk['Palette'] if 'Palette' in blk else d['Palette']
data = blk['Data'] if 'Data' in blk else d['BlockData']
rev = {v: k for k, v in pal.items()}
ids = nbt.varints(data)
hits = collections.Counter()
pos = collections.defaultdict(list)
for i, p in enumerate(ids):
    n = rev[p]
    if any(s in n for s in pat.split(',')):
        x = i % W; z = (i // W) % L; y = i // (W * L)
        hits[n.split('[')[0]] += 1
        if len(pos[n.split('[')[0]]) < 6: pos[n.split('[')[0]].append((x, y, z))
for k, v in hits.items(): print(k, v, pos[k])
