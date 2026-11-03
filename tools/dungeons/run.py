"""Build a dungeon, run all checks, and (with --write) install it into the mod's resources.

Usage: python run.py <module> [--write] [--out DIR]     (module = water, grass, ...; run WITHOUT -I: numpy is needed)
"""
import importlib, json, os, sys, time
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'dungeonkit'))
import kit, check, export

RES = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'data', 'ascensiondungeons', 'dungeons')


def main():
    name = sys.argv[1]
    t0 = time.time()
    d = importlib.import_module(name).build()
    print('built %s: %dx%dx%d, palette %d (%.1fs)' % (d.id, d.W, d.H, d.L, len(d.pal), time.time() - t0))
    ok = True
    # 1 containment of water
    a0 = check.start_grid(d)
    leaks = check.containment(d, a0)
    print('water containment (start state): %d leaks %s' % (len(leaks), leaks[:6])); ok &= not leaks
    # 2 enclosure
    n, leak = check.enclosure(d, a0)
    print('enclosure: %d open cells reachable, %s' % (n, 'SEALED' if not leak else 'LEAK at %s' % leak[:3])); ok &= not leak
    # 3 playthrough
    pok, out, snaps, fired, afin = check.playthrough(d)
    print('\n'.join(out)); ok &= pok
    # ordering: what the design promises is locked behind what
    order = dict(snaps)
    for mid, room, expect in getattr(d, 'order_rules', []):
        if mid not in order: print('  order: mechanic %s never fired' % mid); ok = False; continue
        have = room in order[mid]
        print('  order: %-14s after %-12s %s' % (room, mid, 'reachable' if have else 'locked'), 'ok' if have == expect else 'VIOLATED'); ok &= have == expect
    # the high tide must stay swimmable and watertight (only for dungeons with a tide)
    if any(h['type'] == 'tide' for h in d.hazards):
        ah = check.tide_high_grid(d)
        mh = check.Model(d, ah); rh = mh.reach([tuple(d.entry)])
        court = next(r for r in d.rooms if r['id'] == 'R4')
        print('high tide: court reachable by swimming: %s' % any(check.in_box(court, p) for p in rh)); ok &= any(check.in_box(court, p) for p in rh)
        lk = check.containment(d, ah)
        print('water containment (high tide): %d leaks %s' % (len(lk), lk[:6])); ok &= not lk
    # 4 containment after all effects applied (water filled / cleared)
    leaks2 = check.containment(d, afin)
    print('water containment (all mechanics applied): %d leaks %s' % (len(leaks2), leaks2[:6])); ok &= not leaks2
    # 5 breathing and ground safety
    m = check.Model(d, afin)
    reached = check.reach_all(d, m, fired)
    zones = [h for h in d.hazards if h['type'] == 'zone' and h.get('effect') == 'minecraft:water_breathing']
    bad = check.breath(d, afin, reached, zones)
    print('submerged cells with no air pocket within 20 and no water-breathing zone: %d %s' % (len(bad), bad[:4])); ok &= not bad
    ug = check.unsafe_ground(d, afin, reached)
    print('reachable cells standing on harmful blocks: %d %s' % (len(ug), ug[:4])); ok &= not ug
    # 6 structure of the contract: rooms and landmarks
    roles = {}
    for lm in d.landmarks: roles[lm['role']] = roles.get(lm['role'], 0) + 1
    print('landmarks by role:', roles, '(contract: 5 focal, 5 interactive, 5 discovery)')
    ok &= roles == {'focal': 5, 'interactive': 5, 'discovery': 5}
    nonair = ~np.isin(d.a, [i for i, p in enumerate(d.pal) if kit.is_air(p) or kit.base(p) == 'water'])
    struct = nonair & ~d.mass if hasattr(d, 'mass') else nonair
    for lm in d.landmarks:
        sl = (slice(lm['min'][1], lm['max'][1] + 1), slice(lm['min'][2], lm['max'][2] + 1), slice(lm['min'][0], lm['max'][0] + 1))
        cells = struct[sl]
        kinds = len({kit.base(d.pal[i]) for i in set(d.a[sl][cells].flatten().tolist())})
        if int(cells.sum()) < {'focal': 40, 'interactive': 12, 'discovery': 8}[lm['role']] or kinds < 2:
            print('  landmark %s is thin: %d built cells, %d block kinds' % (lm['id'], int(cells.sum()), kinds)); ok = False
    print('RESULT:', 'ALL CHECKS PASS' if ok else 'CHECKS FAILED')
    if '--write' in sys.argv:
        out_dir = os.path.join(RES, d.id)
        n, data = export.write(d, out_dir)
        idx_path = os.path.join(RES, 'index.json')
        ids = json.load(open(idx_path, encoding='utf-8')) if os.path.exists(idx_path) else []
        if d.id not in ids: ids.append(d.id)
        json.dump(ids, open(idx_path, 'w', encoding='utf-8'))
        print('wrote', out_dir, 'palette', n, 'rooms', len(data['rooms']), 'lights', len(data['lights']),
              'spawn cells', sum(len(r['spawnCells']) for r in data['rooms']))
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
