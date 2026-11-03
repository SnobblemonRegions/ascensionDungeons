"""Runtime layout for the Cinderforge mod runtime, derived from the SAME grid that produced the schematic.

All coordinates are schematic coordinates (the mod adds its world origin). Called from build_rooms38.build_full().
"""
import json, math, random

NAMES = {'R1': 'Ashen Gatehouse', 'R2': 'Blackstone Causeway', 'R3': 'Crimson Barracks', 'R4': 'Magma Cistern',
         'R5': 'Warped Reliquary', 'R6': 'The Smelting Works', 'R7': 'Obsidian Spiral', 'R8': 'The Forge Heart'}


def export(G, an, rms, mks, r12, boxes, lo, out_path, B, R, cube):
    """G grid, an = An over G (walls are not ground), rms = {n: Rm}, mks = {n: marker dict}, r12 = markers of rooms 1-2."""
    rng = random.Random(20261016)
    sub = lambda c: [c[0] - lo[0], c[1] - lo[1], c[2] - lo[2]]
    P = lambda n, *c: sub(rms[n].pos(*c))
    P12 = lambda room, *c: sub((B.R1 if room == 1 else B.R2).pos(*c))
    box = lambda a, c: {'min': a, 'max': c}
    room_boxes = {'R1': boxes[0], 'R2': boxes[1]}
    for i, n in enumerate(sorted(rms)): room_boxes['R%d' % n] = boxes[2 + i]
    def free(p): return R.isfree(G.c.get(p))
    out = {'version': 1, 'rooms': [], 'lights': []}
    light_seen = set()
    for rid in ['R1', 'R2', 'R3', 'R4', 'R5', 'R6', 'R7', 'R8']:
        a, c = room_boxes[rid]
        stand = []
        for x in range(a[0], c[0] + 1):
            for y in range(a[1], c[1] + 1):
                for z in range(a[2], c[2] + 1):
                    p = (x, y, z)
                    if not an.stand(p): continue
                    # spawn cells are plain air with clear headroom over a floor that cannot hurt or hold a mob up oddly
                    if not R.isair(G.c.get(p)): continue
                    if not all(R.isair(G.c.get((x, y + k, z))) for k in (1, 2, 3)): continue
                    g = G.c.get((x, y - 1, z))
                    if g is None or R.base(g) in ('magma_block', 'campfire', 'soul_campfire', 'soul_fire', 'fire', 'ladder', 'chest', 'lava', 'cauldron', 'lava_cauldron'): continue
                    if any(R.islava(G.c.get((x + dx, y, z + dz))) or R.islava(G.c.get((x + dx, y - 1, z + dz))) for dx, dz in R.H4): continue
                    stand.append(p)
        rng.shuffle(stand)
        spawn = [sub(p) for p in stand[:90]]
        # lights: one per 5x5x5 bucket of standable floor, 3 blocks above it (invisible 'light' blocks, server-side only)
        n_l = 0; buckets = {}
        for p in stand: buckets.setdefault((p[0] // 5, p[1] // 5, p[2] // 5), p)
        for p in buckets.values():
            q = (p[0], p[1] + 3, p[2])
            if q not in light_seen and R.isair(G.c.get(q)):
                light_seen.add(q); out['lights'].append(sub(q)); n_l += 1
        out['rooms'].append({'id': rid, 'name': NAMES[rid], 'min': sub(a), 'max': sub(c), 'spawnCells': spawn,
                             'standable': len(stand), 'lights': n_l})
    out['entry'] = sub(B.R1.pos(12, 1, 3))
    # ---- mechanics (all positions derived from the markers and the grid itself)
    out['levers'] = []
    stage_of = {tuple(P(4, *mks[4]['r4_lever%d' % s])): s for s in (1, 2, 3)}
    for p, v in G.c.items():
        if R.base(v) == 'lever':
            s = stage_of.get(tuple(sub(p)))
            if s: out['levers'].append({'pos': sub(p), 'stage': s})
    mb = lambda n, k: [[*P(n, *mks[n][k][0])], [*P(n, *mks[n][k][1])]]
    out['eastPlug'] = box(*mb(3, 'r3_east_plug')); out['westPlug'] = box(*mb(3, 'r3_west_plug'))
    out['reservoir'] = box(*mb(4, 'r4_reservoir')); out['tunnelPlug'] = box(*mb(4, 'r4_plug')); out['descentGate'] = box(*mb(4, 'r4_descent_gate'))
    out['liftArrival'] = P(3, *mks[3]['r3_lift_arrival'])
    out['trench'] = box(P12(2, 19, -6, 24), P12(2, 21, -4, 39))
    out['trenchPlugs'] = [box(P12(2, 21, -6, 23), P12(2, 21, -5, 23)), box(P12(2, 21, -6, 40), P12(2, 21, -5, 40))]
    out['hiddenDoors'] = [box(P12(2, 24, 1, 20), P12(2, 24, 2, 20)), box(P12(2, 24, 1, 43), P12(2, 24, 2, 43))]
    out['portcullis'] = box(P12(1, 10, 1, 20), P12(1, 14, 4, 20))
    deck_y = (B.R2.pos(0, 0, 0))[1]
    out['geysers'] = [sub(p) for p, v in sorted(G.c.items()) if p[1] == deck_y and R.base(v) == 'magma_block' and boxes[1][0][2] <= p[2] <= boxes[1][1][2]]
    out['chests'] = []
    r5c = tuple(P(5, *mks[5]['r5_chest']))
    for p, v in sorted(G.c.items()):
        if R.base(v) != 'chest': continue
        s = tuple(sub(p)); kind = 'key' if s == r5c else 'niche' if room_boxes['R4'][0][1] <= p[1] <= room_boxes['R4'][1][1] and room_boxes['R4'][0][2] <= p[2] <= room_boxes['R4'][1][2] and room_boxes['R4'][0][0] <= p[0] <= room_boxes['R4'][1][0] else 'island'
        out['chests'].append({'pos': list(s), 'kind': kind, 'state': v})
    out['furnaces'] = [{'mouth': P(6, c, 1, 6), 'top': P(6, c, 2, 6), 'dir': [0, 1]} for c in (13, 23, 33)]
    out['overseerSpawn'] = P(6, *mks[6]['r6_overseer'])
    out['overseerGate'] = box(P(6, 44, 1, 11), P(6, 45, 4, 13))      # sealed at run start, opened when the Overseer dies
    out['r7'] = {'centre': P(7, 12, 0, 12), 'radius': 9.5, 'floorY': P(7, 12, 0, 12)[1], 'topY': P(7, 12, 44, 12)[1], 'entry': P(7, 1, 1, 12)}
    out['bossSpawn'] = P(8, 24, 6, 24)
    out['heatVents'] = [box(P(8, vx, 0, vz), P(8, vx + 1, 0, vz + 1)) for (vx, vz) in R.VENTS]
    out['liftPad'] = box(P(8, 23, 2, 23), P(8, 25, 2, 25))
    out['crucibleLava'] = box(P(8, 23, 2, 23), P(8, 25, 2, 25))
    out['crucibleRim'] = box(P(8, 22, 3, 22), P(8, 26, 3, 26))      # low wall ring around the crucible lava; opened when the Heart dies
    out['arena'] = {'centre': P(8, 24, 0, 24), 'radius': 22}
    out['keyId'] = 'forge_key'
    json.dump(out, open(out_path, 'w'), separators=(',', ':'))
    return {'rooms': {r['id']: (r['standable'], len(r['spawnCells']), r['lights']) for r in out['rooms']},
            'levers': len(out['levers']), 'geysers': len(out['geysers']), 'chests': len(out['chests']), 'lights': len(out['lights'])}
