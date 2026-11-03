"""Plan the mod E2E scenarios (plans_mod.json). Routes are STATEFUL: after each mechanic fires, the planning grid is mutated
the way the mod mutates the world, so later legs route through what the mechanic opened.

Usage: python plan_mod.py <schem> <sidecar.json> <runtime.json> <plans_mod.json>

M1 progression (survival)  wrong-order levers, drain, key, east door, sealed gate
M2 full playthrough        creative bot + console kills: mobs, levers, key, Overseer, spiral, vents, Heart, lift, completion
M3 geysers                 survival, peaceful: standing beside an erupting vent hurts
M4 rising lava (stand)     survival, peaceful: waiting in the spiral burns
M5 rising lava (climb)     survival, peaceful: climbing the ramp outruns it (no damage)
M6 furnaces                survival: standing in front of an active furnace hurts
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__))); sys.path.insert(0, 'L:/ascensionDungeons/tools')
import plan_paths as PP
from build_rooms38 import islava

AIRS = ['air', 'cave_air', 'void_air']


def main():
    schem, sidecar, runtime, out = sys.argv[1:5]
    g, size = PP.load_grid(schem)
    meta = json.load(open(sidecar, encoding='utf-8')); lo = meta['origin_combined']; org = {k: tuple(v) for k, v in meta['room_origins'].items()}
    org.update({'R1': (8, 9, 0), 'R2': (0, 9, 21)})
    rt = json.load(open(runtime, encoding='utf-8'))
    an = PP.WalkAn(g, [], None, None)
    DIRS = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
    S = lambda room, x, y, z: (org[room][0] + x - lo[0], org[room][1] + y - lo[1], org[room][2] + z - lo[2])

    def rng3(b): return [[x, y, z] for x in range(b['min'][0], b['max'][0] + 1) for y in range(b['min'][1], b['max'][1] + 1) for z in range(b['min'][2], b['max'][2] + 1)]
    def cells_of(b, pred=lambda v: True): return [tuple(c) for c in rng3(b) if tuple(c) in g.c and pred(g.c[tuple(c)])]
    def mutate(cells):
        for c in cells: g.c.pop(tuple(c), None)
    def sample(cells, n=40):
        cells = sorted(cells); step = max(1, len(cells) // n); return [list(c) for c in cells[::step]]
    def front(pos, kind):
        v = g.c[tuple(pos)]; f = v.split('facing=')[1].split(',')[0].rstrip(']'); d = DIRS[f]
        return (pos[0] + d[0], pos[1] - (1 if kind == 'lever' else 0), pos[2] + d[1])

    levers = {lv['stage']: lv['pos'] for lv in rt['levers']}
    res = rt['reservoir']; top = res['max'][1]
    lava_res = cells_of(res, islava)
    layer = lambda a, b: [c for c in lava_res if a <= c[1] <= b]
    key_chest = next(c for c in rt['chests'] if c['kind'] == 'key')['pos']
    plug = rt['eastPlug']; gate = rt['overseerGate']
    spawn = tuple(rt['entry'])
    east_front = (plug['min'][0] - 1, plug['min'][1], (plug['min'][2] + plug['max'][2]) // 2)
    drained = layer(res['min'][1], top - 4)
    tunnel = cells_of(rt['tunnelPlug']); dgate = cells_of(rt['descentGate'])
    trench_lava = cells_of(rt['trench'], islava)
    tplugs = [c for b in rt['trenchPlugs'] for c in cells_of(b)]; doors = [c for b in rt['hiddenDoors'] for c in cells_of(b)]
    east_cells = cells_of(plug)

    def build(name, legs, start=None, **extra):
        cells = [PP.snap(an, start or spawn)]; actions = []; ok = True
        for goal, acts, mut in legs:
            seg = PP.bfs(an, cells[-1], PP.snap(an, goal))
            if seg is None: print('  %s: NO ROUTE to %s from %s' % (name, goal, cells[-1])); ok = False; break
            cells += seg[1:]
            for a in acts: actions.append(dict(a, at=len(cells) - 1))
            mutate(mut() if callable(mut) else mut)
        plan = {'name': name, 'setup': [], 'actions': actions, 'goals': [], 'cells': cells if ok else None}
        plan.update(extra)
        print('  %s: %s cells, %d actions' % (name[:60], len(cells) if ok else None, len(actions)))
        return plan

    # ---- reusable leg groups
    def lever_legs(wrong_first):
        L = []
        if wrong_first: L.append((front(levers[3], 'lever'), [{'kind': 'lever', 'pos': levers[3], 'expectToggle': False}], []))
        L.append((front(levers[1], 'lever'), [{'kind': 'lever', 'pos': levers[1]}, {'kind': 'wait', 'ms': 3500},
            {'kind': 'check', 'label': 'stage 1: top two layers gone', 'positions': sample(layer(top - 1, top)), 'expect': AIRS},
            {'kind': 'check', 'label': 'stage 1: lower layers still lava', 'positions': sample(layer(top - 3, top - 2)), 'expect': ['lava']}], layer(top - 1, top)))
        L.append((front(levers[2], 'lever'), [{'kind': 'lever', 'pos': levers[2]}, {'kind': 'wait', 'ms': 3500},
            {'kind': 'check', 'label': 'stage 2: next two layers gone', 'positions': sample(layer(top - 3, top - 2)), 'expect': AIRS},
            {'kind': 'check', 'label': 'stage 2: deep lava remains', 'positions': sample(layer(res['min'][1], top - 4)), 'expect': ['lava']}], layer(top - 3, top - 2)))
        L.append((front(levers[3], 'lever'), [{'kind': 'lever', 'pos': levers[3]}, {'kind': 'wait', 'ms': 9000},
            {'kind': 'check', 'label': 'stage 3: reservoir empty', 'positions': sample(drained), 'expect': AIRS},
            {'kind': 'check', 'label': 'stage 3: tunnel plug gone', 'positions': [list(c) for c in tunnel], 'expect': AIRS},
            {'kind': 'check', 'label': 'stage 3: descent gate gone', 'positions': [list(c) for c in dgate], 'expect': AIRS},
            {'kind': 'wait', 'ms': 4000},
            {'kind': 'check', 'label': 'stage 3: causeway trench drained', 'positions': sample(trench_lava), 'expect': AIRS},
            {'kind': 'check', 'label': 'stage 3: trench plugs and hidden doors open', 'positions': [list(c) for c in tplugs + doors], 'expect': AIRS}],
            drained + tunnel + dgate + trench_lava + tplugs + doors))
        L.append((front(key_chest, 'chest'), [{'kind': 'take', 'pos': key_chest, 'item': 'tripwire_hook'}], []))
        L.append((east_front, [{'kind': 'use', 'pos': [plug['min'][0], plug['min'][1], east_front[2]], 'item': 'tripwire_hook'},
                               {'kind': 'check', 'label': 'east door open', 'positions': [list(c) for c in east_cells], 'expect': AIRS}], east_cells))
        return L

    plans = []
    # ---------------------------------------------------------------- M1
    legs = [(east_front, [{'kind': 'use', 'pos': [plug['min'][0], plug['min'][1], east_front[2]], 'item': 'none', 'expectOpen': False}], [])]
    legs += lever_legs(True)
    gate_front = (gate['min'][0] - 1, gate['min'][1], (gate['min'][2] + gate['max'][2]) // 2)
    legs.append((gate_front, [{'kind': 'check', 'label': 'overseer gate is sealed', 'positions': rng3(gate), 'expect': ['blackstone']}], []))
    plans.append(build('M1 progression: wrong-order levers, drain, key, east door, sealed gate', legs, consoleCmds=['gamemode creative {bot}'], timeoutMs=420000))

    # ---------------------------------------------------------------- M2
    def ent(t, n, extra=''): return 'execute if entity @e[type=minecraft:%s,name="%s"%s]' % (t, n, extra)
    hooks = {
        'mobsR2': {'cmds': [ent('blaze', 'Cinder Wisp'), 'execute if entity @e[type=minecraft:wither_skeleton]'], 'expects': ['Test passed', 'Test passed']},
        'mobsR3': {'cmds': [ent('piglin_brute', 'Barracks Brute')], 'expects': ['Test passed']},
        'overseer': {'cmds': [ent('wither_skeleton', 'Forge Overseer'), 'kill @e[type=minecraft:wither_skeleton,name="Forge Overseer"]'], 'expects': ['count: 1', 'Killed']},
        'heart': {'cmds': [ent('blaze', 'The Forge Heart')], 'expects': ['count: 1']},
        'shielded': {'cmds': [ent('blaze', 'The Forge Heart', ',nbt={Invulnerable:1b}')], 'expects': ['Test passed']},
        'exposed': {'cmds': [ent('blaze', 'The Forge Heart', ',nbt={Invulnerable:0b}'), 'kill @e[type=minecraft:blaze,name="The Forge Heart"]'], 'expects': ['Test passed', 'Killed']},
    }
    legs = [(S('R2', 20, 1, 30), [{'kind': 'wait', 'ms': 2500}, {'kind': 'hook', 'id': 'mobsR2'}], []),
            (S('R3', 16, 1, 12), [{'kind': 'wait', 'ms': 2500}, {'kind': 'hook', 'id': 'mobsR3'}], [])]
    legs += lever_legs(False)
    legs.append((S('R6', 6, 1, 9), [{'kind': 'wait', 'ms': 2500}, {'kind': 'hook', 'id': 'overseer'}, {'kind': 'wait', 'ms': 2500},
                                    {'kind': 'check', 'label': 'overseer gate opened', 'positions': rng3(gate), 'expect': AIRS}], []))
    legs.append((S('R7', 3, 41, 13), [{'kind': 'wait', 'ms': 1500}], []))
    vents = rt['heatVents']
    vent_goal = lambda v: (v['min'][0] - 1, v['min'][1] + 1, v['min'][2])
    for k in range(3):
        v = vents[k]
        acts = [{'kind': 'wait', 'ms': 1200}]
        if k == 0: acts += [{'kind': 'hook', 'id': 'heart'}]
        acts += [{'kind': 'attack', 'pos': v['min']}, {'kind': 'wait', 'ms': 700},
                 {'kind': 'check', 'label': 'vent %d cooled' % (k + 1), 'positions': rng3(v), 'expect': ['blackstone']}]
        if k == 1: acts += [{'kind': 'hook', 'id': 'shielded'}]
        legs.append((vent_goal(v), acts, []))
    pad = rt['liftPad']; rim = rt['crucibleRim']
    def after_heart():
        for c in rng3(pad): g.c[tuple(c)] = 'minecraft:chiseled_polished_blackstone'
        mutate([c for c in rng3(rim)])
        return []
    legs.append((vent_goal(vents[2]), [{'kind': 'wait', 'ms': 1200}, {'kind': 'hook', 'id': 'exposed'}, {'kind': 'wait', 'ms': 2500},
                                       {'kind': 'check', 'label': 'lift pad appeared', 'positions': rng3(pad), 'expect': ['chiseled_polished_blackstone']},
                                       {'kind': 'check', 'label': 'crucible rim opened', 'positions': rng3(rim), 'expect': AIRS}], after_heart))
    legs.append((S('R8', 24, 3, 24), [{'kind': 'near', 'pos': rt['liftArrival'], 'radius': 4, 'ms': 12000, 'label': 'lift carried the bot to the Barracks'},
                                      {'kind': 'dimension', 'expect': 'minecraft:overworld', 'ms': 100000, 'label': 'run completed, bot returned'}], []))
    plans.append(build('M2 full playthrough (creative): mobs, drain, key, Overseer, spiral, vents, Heart, lift, completion', legs,
                       consoleCmds=['gamemode creative {bot}'], hooks=hooks, timeoutMs=900000))

    # ---------------------------------------------------------------- M3 geysers
    ge = rt['geysers'][0]
    plans.append(build('M3 geyser: standing beside an erupting vent hurts', [((ge[0] + 1, ge[1] + 1, ge[2]), [{'kind': 'wait', 'ms': 9000}], [])],
                       consoleCmds=['difficulty peaceful', 'gamemode survival {bot}', 'attribute {bot} minecraft:generic.max_health base set 200', 'effect give {bot} minecraft:instant_health 1 7 true'],
                       expectDamage=True, timeoutMs=120000))
    # ---------------------------------------------------------------- M4 / M5 rising lava
    r7e = tuple(rt['r7']['entry'])
    plans.append(build('M4 rising lava: waiting in the spiral burns', [(r7e, [{'kind': 'wait', 'ms': 16000}], [])], start=r7e,
                       consoleCmds=['difficulty peaceful', 'gamemode survival {bot}'], preTp=list(r7e), expectDamage=True, timeoutMs=120000))
    plans.append(build('M5 rising lava: climbing the ramp outruns it', [(S('R7', 3, 41, 13), [], [])], start=r7e,
                       consoleCmds=['difficulty peaceful', 'gamemode survival {bot}'], preTp=list(r7e), expectNoDamage=True, timeoutMs=240000))
    # ---------------------------------------------------------------- M6 furnaces
    f0 = rt['furnaces'][0]['mouth']; fc = (f0[0], f0[1], f0[2] + 2)
    plans.append(build('M6 furnace: standing before an active furnace hurts', [(fc, [{'kind': 'wait', 'ms': 12000}], [])], start=fc,
                       consoleCmds=['difficulty normal', 'gamemode survival {bot}', 'attribute {bot} minecraft:generic.max_health base set 600',
                                    'effect give {bot} minecraft:instant_health 1 7 true'], preTp=list(fc), expectDamage=True, timeoutMs=120000))
    # ---------------------------------------------------------------- M7 wild Pokemon (needs Cobblemon: run with CF_FULL_MODS=1)
    plans.append(build('M7 wild Pokemon spawn in the Barracks (needs Cobblemon)', [(S('R3', 16, 1, 12), [{'kind': 'wait', 'ms': 25000}, {'kind': 'hook', 'id': 'pokemon'}], [])],
                       consoleCmds=['gamemode creative {bot}'], timeoutMs=200000,
                       hooks={'pokemon': {'cmds': ['execute if entity @e[type=cobblemon:pokemon]'], 'expects': ['Test passed']}}))
    json.dump({'plans': plans}, open(out, 'w'))


if __name__ == '__main__': main()
