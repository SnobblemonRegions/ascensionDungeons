"""Geometry checks and the AUTO playthrough planner for dungeons built with kit.py.

The planner reads the dungeon's own mechanics (the same data the mod runs): it starts at the entry, computes what a walking /
swimming player can reach, fires every mechanic whose trigger is reachable and whose key/prerequisite is obtained, applies
its effects to a copy of the grid, and repeats until nothing changes. Then it asserts the ordering the design promises
(arena unreachable before the key, vault unreachable before the puzzle, ...) and that every room, boss target, chest and
landmark ends up reachable.
"""
from collections import deque
import numpy as np
import kit
from kit import base, props, is_air, is_water, nonsolid

H4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


class Model:
    """Per-state view of a grid: classification tables over the palette plus fast cell lookups."""
    def __init__(self, d, grid=None):
        self.d = d
        self.a = (d.a if grid is None else grid)
        self.W, self.H, self.L = d.W, d.H, d.L
        self.refresh()

    def refresh(self):
        pal = self.d.pal
        n = len(pal)
        self.free = np.zeros(n + 1, bool); self.wt = np.zeros(n + 1, bool); self.gd = np.zeros(n + 1, bool)
        self.lad = np.zeros(n + 1, bool); self.lava = np.zeros(n + 1, bool); self.air = np.zeros(n + 1, bool); self.pure = np.zeros(n + 1, bool)
        for i, s in enumerate(pal):
            bn = base(s)
            self.air[i] = is_air(s)
            self.pure[i] = bn in ('water', 'bubble_column')
            self.wt[i] = is_water(s)
            self.lava[i] = bn == 'lava'
            self.lad[i] = bn == 'ladder'
            self.free[i] = (nonsolid(s) and bn != 'lava') or (props(s).get('waterlogged') == 'true' and nonsolid(s))
            if bn == 'bubble_column': self.free[i] = True
            self.gd[i] = (not nonsolid(s)) and bn not in kit.UNSAFE_GROUND and not kit.is_partial_wall(s)
        # out-of-grid reads as solid rock
        self.out = n

    def pid(self, x, y, z):
        if 0 <= x < self.W and 0 <= y < self.H and 0 <= z < self.L: return int(self.a[y, z, x])
        return self.out

    def isfree(self, x, y, z): return bool(self.free[self.pid(x, y, z)])
    def iswater(self, x, y, z): return bool(self.wt[self.pid(x, y, z)])

    def stand(self, p):
        x, y, z = p
        i = self.pid(x, y, z)
        if not self.free[i] or not self.isfree(x, y + 1, z): return False
        if self.lad[i] or self.wt[i]: return True
        j = self.pid(x, y - 1, z)
        return bool(self.gd[j])

    def reach(self, starts, banned=None):
        seen = set(); q = deque()
        for s in starts:
            if self.stand(s): seen.add(s); q.append(s)
        while q:
            p = q.popleft()
            x, y, z = p
            nxt = []
            i = self.pid(x, y, z)
            if self.lad[i] or self.wt[i]:
                for dy in (1, -1):
                    c = (x, y + dy, z)
                    if self.isfree(*c) and (self.lad[i] or self.iswater(*c) or dy == 1): nxt.append(c)
            for dx, dz in H4:
                for dy in (1, 0, -1, -2, -3):
                    c = (x + dx, y + dy, z + dz)
                    if dy == 1 and not (self.isfree(x, y + 2, z)): continue
                    if dy <= -1 and not (self.isfree(c[0], y, c[2]) and self.isfree(c[0], y + 1, c[2])): continue
                    if dy == 1 and not (self.isfree(c[0], y + 1, c[2]) and self.isfree(c[0], y + 2, c[2])): continue
                    nxt.append(c)
            for c in nxt:
                if c in seen: continue
                if banned and banned(c): continue
                if self.stand(c): seen.add(c); q.append(c)
        return seen


def reach_all(d, m, fired=()):
    """Reachable cells from the entry, following active teleport pads (pad requires = a fired mechanic id)."""
    starts = [tuple(d.entry)]
    while True:
        r = m.reach(starts)
        grew = False
        for pd in d.pads:
            if pd.get('requires') and pd['requires'] not in fired: continue
            to = tuple(pd['to'])
            if to in starts: continue
            if any(in_box(pd['from'], c) for c in r):
                starts.append(to); grew = True
        for e in d.planner_edges:
            to = tuple(e['to'])
            if to not in starts and any(in_box(e['from'], c) for c in r):
                starts.append(to); grew = True
        if not grew: return r


# ---- effects on a grid (mirrors AuthoredDirector.applyEffect) ---------------------------------------------------

def _matches(d, pidx, only):
    s = d.pal[pidx]
    bn = base(s)
    if only is None: return True
    if only == 'air': return is_air(s)
    if only == 'water': return bn in ('water', 'bubble_column')
    if only == 'lava': return bn == 'lava'
    if only == 'solid': return not is_air(s) and bn not in ('water', 'lava')
    return ('minecraft:' + bn) == only or bn == only


def apply_box(d, a, box, block, only):
    mn, mx = box['min'], box['max']
    pid = d.pid(block)
    sub = a[mn[1]:mx[1] + 1, mn[2]:mx[2] + 1, mn[0]:mx[0] + 1]
    if only is None: sub[:] = pid
    else:
        ok = np.array([_matches(d, i, only) for i in range(len(d.pal))])
        sub[ok[sub]] = pid


def apply_effects(d, a, effects, gained):
    for fx in effects:
        do = fx.get('do')
        if do == 'clear': apply_box(d, a, fx['box'], kit.AIR, fx.get('only'))
        elif do == 'fill': apply_box(d, a, fx['box'], fx['block'], fx.get('only'))
        elif do == 'give' and fx.get('key'): gained.add(fx['key'])
        elif do == 'mark': gained.add('mark:' + fx['id'])


def near(model, box, reachable, r=2):
    mn, mx = box['min'], box['max']
    for (x, y, z) in reachable:
        if mn[0] - r <= x <= mx[0] + r and mn[1] - r <= y <= mx[1] + r and mn[2] - r <= z <= mx[2] + r: return True
    return False


def box_of(p): return {'min': list(p), 'max': list(p)}


def in_box(b, p): return all(b['min'][i] <= p[i] <= b['max'][i] for i in range(3))


def start_grid(d):
    a = d.a.copy()
    for s in d.seals:
        apply_box(d, a, s, s['block'], None)
    return a


def playthrough(d, log=print):
    """Returns (ok, report lines). Stages are the mechanic firings in the order the planner finds them."""
    out, ok = [], True
    a = start_grid(d)
    gained, fired, stages = set(), set(), []
    m = Model(d, a)
    reached = reach_all(d, m)
    snaps = [('start', set())]
    changed = True
    while changed:
        changed = False
        m = Model(d, a)
        reached = reach_all(d, m, fired)
        if len(snaps) == 1 and not snaps[0][1]: snaps[0] = ('start', {r['id'] for r in d.rooms if any(in_box(r, p) for p in reached)})
        for ch in d.chests:
            if ch['kind'] == 'key' and ch.get('key') and ch['key'] not in gained and near(m, box_of(ch['pos']), reached, 2):
                gained.add(ch['key']); out.append('  key %s taken from chest %s' % (ch['key'], ch['pos'])); changed = True
        for mech in d.mechanics:
            mid = mech['id']
            if mid in fired: continue
            t = mech['type']
            if mech.get('requires') and ('mark:' + mech['requires']) not in gained and mech['requires'] not in fired: continue
            if t == 'lever_sequence':
                if not all(near(m, box_of(p), reached, 2) for p in mech['levers']): continue
                effs = []
                for st in mech.get('onStep', []): effs += st
                effs += mech.get('onDone', [])
            elif t == 'lock':
                if not near(m, mech['at'], reached, 2): continue
                if mech.get('key') and mech['key'] not in gained: continue
                effs = mech.get('onOpen', [])
            elif t == 'sequence':
                if not all(near(m, box_of(p), reached, 2) for p in mech['buttons']): continue
                effs = mech.get('onDone', [])
            elif t == 'toggle_pattern':
                if not all(near(m, box_of(l['pos']), reached, 2) for l in mech['levers']): continue
                effs = mech.get('onDone', [])
            elif t == 'trial':
                if not near(m, mech['plate'], reached, 1): continue
                effs = mech.get('onDone', [])
            elif t == 'offering':
                if not near(m, mech['at'], reached, 2): continue
                if mech.get('key') and mech['key'] not in gained: continue
                effs = mech.get('onOpen', [])
            else:
                continue
            fired.add(mid); changed = True
            apply_effects(d, a, effs, gained)
            if mech.get('marks'): gained.add('mark:' + mid)
            gained.add('mark:' + mid)
            m2 = Model(d, a); r2 = reach_all(d, m2, fired)
            stages.append((mid, len(r2) - len(reached)))
            snaps.append((mid, {r['id'] for r in d.rooms if any(in_box(r, p) for p in r2)}))
            out.append('  fired %-18s -> reachable cells %d -> %d' % (mid, len(reached), len(r2)))
            reached = r2
            break
    m = Model(d, a)
    reached = reach_all(d, m, fired)
    # every mechanic must have fired
    for mech in d.mechanics:
        if mech['type'] in ('lever_sequence', 'lock', 'trial', 'offering', 'sequence', 'toggle_pattern') and mech['id'] not in fired:
            out.append('  MECHANIC NEVER TRIGGERABLE: %s' % mech['id']); ok = False
    # every room reachable at the end
    for r in d.rooms:
        hit = any(in_box(r, p) for p in reached)
        out.append('  room %-22s reachable at end: %s' % (r['name'], hit)); ok &= hit
    for ch in d.chests:
        hit = near(m, box_of(ch['pos']), reached, 2)
        if not hit: out.append('  chest %s UNREACHABLE' % ch['pos']); ok = False
    for lm in d.landmarks:
        hit = near(m, lm, reached, 3)
        if not hit: out.append('  landmark %s (%s) NOT reachable' % (lm['name'], lm['id'])); ok = False
    for bs in d.bosses:
        for t in bs.get('shield', {}).get('targets', []):
            if not near(m, t, reached, 3): out.append('  boss target %s unreachable' % t); ok = False
    return ok, out, snaps, fired, a


# ---- static checks ----------------------------------------------------------------------------------------------

def containment(d, a=None):
    """Water must not be able to spread to air: every wet cell (water, or a waterloggable block touching wet) has no air /
    replaceable neighbour sideways or below. Returns the list of leaks."""
    a = d.a if a is None else a
    m = Model(d, a)
    pal = d.pal
    is_w = m.wt[a]                                             # waterlogged or water cells
    target = np.array([(nonsolid(s) or is_air(s)) and not is_water(s) and not kit.waterloggable(s) for s in pal])[a]   # flows in / breaks
    # waterloggable blocks next to wet cells become wet themselves: iterate to a fixpoint (sideways, from above, from below)
    wl = np.array([kit.waterloggable(s) and not is_water(s) for s in pal])[a]
    wet = is_w.copy()
    for _ in range(60):
        grown = wet.copy()
        for ax, sh in ((2, 1), (2, -1), (1, 1), (1, -1), (0, -1)):          # water spreads sideways and DOWN, never up
            grown |= np.roll(wet, sh, axis=ax) & wl
        if (grown == wet).all(): break
        wet = grown
    air = target
    leaks = []
    ys, zs, xs = np.nonzero(wet)
    for y, z, x in zip(ys.tolist(), zs.tolist(), xs.tolist()):
        for dx, dz in H4:
            nx, nz = x + dx, z + dz
            if not (0 <= nx < d.W and 0 <= nz < d.L): leaks.append((x, y, z, 'edge')); continue
            if air[y, nz, nx]: leaks.append((x, y, z, 'side(%d,%d)' % (dx, dz)))
        if y > 0 and air[y - 1, z, x]: leaks.append((x, y, z, 'below'))
    return leaks


def waterlogged_ok(s): return False


def enclosure(d, a=None):
    """Flood fill from the entry through everything that is not solid: must not touch the grid boundary."""
    a = d.a if a is None else a
    m = Model(d, a)
    nonblock = m.free[a] | m.air[a] | m.wt[a] | m.lava[a]
    start = tuple(d.entry)
    seen = {start}; q = deque([start]); leak = []
    while q:
        x, y, z = q.popleft()
        for dx, dy, dz in N6:
            n = (x + dx, y + dy, z + dz)
            if n in seen: continue
            if not (0 <= n[0] < d.W and 0 <= n[1] < d.H and 0 <= n[2] < d.L): leak.append(n); continue
            if nonblock[n[1], n[2], n[0]]: seen.add(n); q.append(n)
    return len(seen), leak


def breath(d, a, reached, zones):
    """Reachable cells whose head is underwater and that are farther than 20 steps of swimming from an air pocket, outside every
    water_breathing zone."""
    m = Model(d, a)
    sub = [c for c in reached if m.iswater(c[0], c[1] + 1, c[2]) and m.iswater(*c)]
    if not sub: return []
    # BFS over water cells from every cell whose head is air
    dist = {}
    q = deque()
    for c in reached:
        if m.iswater(*c) and not m.iswater(c[0], c[1] + 1, c[2]): dist[c] = 0; q.append(c)
    while q:
        c = q.popleft()
        if dist[c] >= 40: continue
        for dx, dy, dz in N6:
            n = (c[0] + dx, c[1] + dy, c[2] + dz)
            if n in dist or not (m.iswater(*n) and m.isfree(*n)): continue
            dist[n] = dist[c] + 1; q.append(n)
    bad = []
    for c in sub:
        if dist.get(c, 99) <= 20: continue
        if any(in_box(z['box'], c) for z in zones): continue
        bad.append(c)
    return bad


def unsafe_ground(d, a, reached):
    m = Model(d, a)
    bad = []
    for (x, y, z) in reached:
        j = m.pid(x, y - 1, z)
        if base(d.pal[j]) in kit.UNSAFE_GROUND: bad.append((x, y, z, base(d.pal[j])))
    return bad


def tide_high_grid(d, a=None):
    """The grid with every tide hazard flooded (water fills the air cells of its water box)."""
    a = (start_grid(d) if a is None else a).copy()
    for h in d.hazards:
        if h['type'] == 'tide': apply_box(d, a, h['water'], kit.water(), 'air')
    return a
