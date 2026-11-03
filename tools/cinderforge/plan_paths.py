"""Plan walking routes through the Cinderforge full-dungeon schematic for the bot walk test.

Reads the saved .schem (exactly what the server will have), applies each scenario's setup (plugs/doors opened, lava
drained) to an in-memory copy, and finds shortest standing-cell routes. Writes plans.json (schematic coordinates).
Usage: python plan_paths.py <schem> <sidecar.json> <plans.json>
"""
import json, os, sys
from collections import deque
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__))); sys.path.insert(0, 'L:/ascensionDungeons/tools')
import nbt as NBT
import build_rooms12 as B
import build_rooms38 as R
from build_rooms38 import An, base, H4, islava

ORIGINS = {'R1': (8, 9, 0), 'R2': (0, 9, 21)}


class WalkAn(An):
    def stand(s, p):
        if not An.stand(s, p): return False
        c = s.get(p)
        if c is not None and base(c) == 'ladder': return True
        gr = s.get((p[0], p[1] - 1, p[2]))
        # walls are 1.5 tall (cannot be stepped or jumped onto); magma/campfires hurt
        return base(gr) not in ('magma_block', 'campfire', 'soul_campfire') and not base(gr).endswith(('_wall', 'cauldron'))


def load_grid(path):
    _, root = NBT.load(path); s = root['Schematic']
    pal = {v: k for k, v in s['Blocks']['Palette'].items()}
    W, H, L = s['Width'], s['Height'], s['Length']
    data = np.array(NBT.varints(s['Blocks']['Data']), dtype=np.int32).reshape(H, L, W)
    air = [i for i, k in pal.items() if k == 'minecraft:air']
    g = B.Grid()
    ys, zs, xs = np.nonzero(~np.isin(data, air))
    for x, y, z in zip(xs.tolist(), ys.tolist(), zs.tolist()): g.c[(x, y, z)] = pal[int(data[y, z, x])]
    return g, (W, H, L)


def bfs(an, start, goal, banned=lambda p: False):
    prev = {start: None}; q = deque([start])
    while q:
        p = q.popleft()
        if p == goal: break
        cur = an.get(p); nxt = []
        if cur is not None and base(cur) == 'ladder':
            for dy in (1, -1):
                c = (p[0], p[1] + dy, p[2])
                if an.free(c): nxt.append(c)
        for dx, dz in H4:
            for dy in (1, 0, -1, -2, -3):
                c = (p[0] + dx, p[1] + dy, p[2] + dz)
                if dy == 1 and not an.free((p[0], p[1] + 2, p[2])): continue
                if dy <= -1 and not (an.free((c[0], p[1], c[2])) and an.free((c[0], p[1] + 1, c[2]))): continue
                nxt.append(c)
        for c in nxt:
            if c in prev or banned(c): continue
            if an.stand(c): prev[c] = p; q.append(c)
    if goal not in prev: return None
    path = []; p = goal
    while p is not None: path.append(p); p = prev[p]
    return path[::-1]


def snap(an, c):
    if an.stand(c): return c
    for r in range(1, 4):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                for dy in (0, 1, -1):
                    q = (c[0] + dx, c[1] + dy, c[2] + dz)
                    if an.stand(q): return q
    raise ValueError('no standable cell near %s' % (c,))


def main():
    schem, sidecar, out = sys.argv[1:4]
    g, size = load_grid(schem)
    meta = json.load(open(sidecar, encoding='utf-8')); lo = meta['origin_combined']
    org = dict(ORIGINS); org.update({k: tuple(v) for k, v in meta['room_origins'].items()})
    def S(room, x, y, z): o = org[room]; return (o[0] + x - lo[0], o[1] + y - lo[1], o[2] + z - lo[2])
    an = WalkAn(g, [], None, None)
    spawn = S('R1', 12, 1, 3)
    def box(room, a, c): return (S(room, *a), S(room, *c))

    def clear(boxes, only_lava=False):
        for a, c in boxes:
            for x in range(min(a[0], c[0]), max(a[0], c[0]) + 1):
                for y in range(min(a[1], c[1]), max(a[1], c[1]) + 1):
                    for z in range(min(a[2], c[2]), max(a[2], c[2]) + 1):
                        v = g.c.get((x, y, z))
                        if v is None: continue
                        if only_lava and not islava(v): continue
                        g.c.pop((x, y, z))

    def run(name, setup, goals, ban=None):
        """setup = list of (box, only_lava); applied to g for planning and emitted for the server."""
        saved = {}
        for (a, c), ol in setup:
            for x in range(min(a[0], c[0]), max(a[0], c[0]) + 1):
                for y in range(min(a[1], c[1]), max(a[1], c[1]) + 1):
                    for z in range(min(a[2], c[2]), max(a[2], c[2]) + 1):
                        p = (x, y, z)
                        if p in g.c and (not ol or islava(g.c[p])): saved[p] = g.c.pop(p)
        cells = [snap(an, spawn)]; actions = []
        for goal in goals:
            act = None
            if isinstance(goal, dict): act, goal = goal['action'], goal['cell']
            goal = snap(an, goal)
            seg = bfs(an, cells[-1], goal, ban or (lambda p: False))
            if seg is None: print('  %s: NO ROUTE %s -> %s' % (name, cells[-1], goal)); cells = None; break
            cells += seg[1:]
            if act: actions.append(dict(act, at=len(cells) - 1))
        for p, v in saved.items(): g.c[p] = v
        print('  %s: %s cells' % (name, None if cells is None else len(cells)))
        return {'name': name, 'setup': [{'box': [list(a), list(c)], 'only_lava': ol} for (a, c), ol in setup], 'actions': actions,
                'goals': [list(x['cell'] if isinstance(x, dict) else x) for x in goals], 'cells': None if cells is None else [list(c) for c in cells]}

    r3_east = box('R3', (27, 1, 16), (27, 4, 18)); r3_west = box('R3', (4, 1, 16), (5, 4, 18))
    r4_res = box('R4', (17, -13, 17), (31, -7, 31)); r4_plug = box('R4', (16, -13, 23), (16, -11, 25)); r4_gate = box('R4', (23, -5, 32), (25, -5, 32))
    doors = [box('R2', (24, 1, 20), (24, 2, 20)), box('R2', (24, 1, 43), (24, 2, 43))]
    plugs = [box('R2', (21, -6, 23), (21, -5, 23)), box('R2', (21, -6, 40), (21, -5, 40))]
    trench = box('R2', (19, -6, 24), (21, -4, 39))
    deckB = lambda p: S('R2', 0, 0, 24)[2] <= p[2] <= S('R2', 0, 0, 39)[2] and p[1] >= S('R2', 0, 1, 0)[1] - 1 and S('R2', 17, 0, 0)[0] <= p[0] <= S('R2', 23, 0, 0)[0]
    plans = []
    plans.append(run('S1 default: spawn -> Room 3 nave -> Room 4 tier 3', [], [S('R3', 16, 1, 12), S('R4', 24, -5, 34)]))
    plans.append(run('S2 islands: spawn -> island I1 chest -> island I2 chest', [], [S('R2', 13, -2, 30), S('R2', 27, -2, 33)]))
    plans.append(run('S3 east door open: Room 3 -> Room 6 -> gantry -> Room 7 top -> Room 8 crucible',
                     [(r3_east, False)], [S('R3', 26, 1, 17), S('R6', 23, 1, 8), S('R6', 20, 7, 17), S('R7', 3, 41, 13), S('R8', 27, 3, 24)]))
    plans.append(run('S4 cistern drained: Room 4 reservoir floor -> tunnel -> Room 5 altar',
                     [(r4_res, True), (r4_plug, False), (r4_gate, False)], [S('R4', 24, -5, 34), S('R4', 20, -13, 24), S('R5', 12, 0, 21)]))
    plans.append(run('S5 trench bypass: Pylon 1 passage -> trench -> Pylon 2 passage (span B deck banned)',
                     [(d, False) for d in doors + plugs] + [(trench, True)], [S('R2', 20, 1, 21), S('R2', 20, 1, 42)], ban=deckB))
    # interaction scenarios: every lever and chest in the schematic, found from the blocks themselves
    DIRS = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
    levers, chests = [], []
    for (x, y, z), v in g.c.items():
        if base(v) == 'lever': levers.append(((x, y, z), v))
        if base(v) == 'chest': chests.append(((x, y, z), v))
    def front(pos, state):
        f = state.split('facing=')[1].split(',')[0].rstrip(']'); d = DIRS[f]
        return (pos[0] + d[0], pos[1] - (1 if base(state) == 'lever' else 0), pos[2] + d[1])
    r5chest = S('R5', 12, 1, 22)
    acts_default = [{'cell': front(p, v), 'action': {'kind': 'lever', 'pos': list(p)}} for p, v in sorted(levers)]
    acts_default += [{'cell': front(p, v), 'action': {'kind': 'chest', 'pos': list(p)}} for p, v in sorted(chests) if p != r5chest]
    plans.append(run('S6 interactions (default state): pull every lever, open every chest except the Reliquary key chest', [], acts_default))
    p5 = [(c, v) for c, v in chests if c == r5chest]
    plans.append(run('S7 interactions (drained): open the Reliquary key chest',
                     [(r4_res, True), (r4_plug, False), (r4_gate, False)],
                     [{'cell': front(p, v), 'action': {'kind': 'chest', 'pos': list(p)}} for p, v in p5]))
    json.dump({'plans': plans, 'origin_combined': lo}, open(out, 'w'))
    print('wrote', out)


if __name__ == '__main__': main()
