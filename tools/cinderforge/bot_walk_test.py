"""Bot walk test: place the Cinderforge dungeon on a clean vanilla 1.21.1 server and walk planned routes with a real
protocol client (mineflayer, real player physics).

Usage: python bot_walk_test.py <schem> <plans.json> <scratch_dir> [scenario numbers like 1 3 5]
"""
import json, os, shutil, subprocess, sys, threading, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vanilla_world_test as V

NODE_MODULES = 'L:/claude-cobbleraids-work/bot/node_modules'
WALKER = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'walker.js')
PORT = 25591


def main():
    schem, plans_path, scratch = sys.argv[1:4]
    only = {int(a) for a in sys.argv[4:]}
    plans = json.load(open(plans_path, encoding='utf-8'))['plans']
    pal, arr, (W, H, L) = V.load_schem(schem)
    if os.path.exists(scratch): shutil.rmtree(scratch)
    os.makedirs(scratch)
    V.write_structure(pal, arr, (W, H, L), os.path.join(scratch, 'world', 'generated', 'minecraft', 'structures', 'cinderforge.nbt'))
    open(os.path.join(scratch, 'eula.txt'), 'w').write('eula=true\n')
    open(os.path.join(scratch, 'server.properties'), 'w').write(
        'server-port=%d\nonline-mode=false\nlevel-type=minecraft:flat\n'
        'generator-settings={"biome":"minecraft:the_void","layers":[],"structure_overrides":[]}\n'
        'spawn-monsters=false\nspawn-animals=false\nspawn-npcs=false\nview-distance=8\nsimulation-distance=4\n'
        'max-tick-time=-1\ndifficulty=peaceful\ngenerate-structures=false\nenable-rcon=false\nspawn-protection=0\n' % PORT)
    x0, y0, z0 = 1600, 100, 1600
    results = []
    sv = V.Srv(scratch)
    try:
        sv.wait('Done (', 0, 180); print('server up', flush=True)
        sv.cmd('gamerule doMobSpawning false', 'Gamerule', 20)
        sv.cmd('forceload add %d %d %d %d' % (x0, z0, x0 + W, z0 + L), 'force loaded', 60)
        time.sleep(5)
        for n, plan in enumerate(plans, 1):
            if only and n not in only: continue
            print('\n=== %s' % plan['name'], flush=True)
            if plan['cells'] is None: print('   no route planned'); results.append((n, plan['name'], 'NO ROUTE')); continue
            sv.cmd('place template minecraft:cinderforge %d %d %d' % (x0, y0, z0), 'template', 240)   # re-place restores plugs/lava
            time.sleep(3)
            for st in plan['setup']:
                (a, c) = st['box']
                sv.send('fill %d %d %d %d %d %d minecraft:air%s' % (x0 + a[0], y0 + a[1], z0 + a[2], x0 + c[0], y0 + c[1], z0 + c[2],
                                                                     ' replace minecraft:lava' if st['only_lava'] else ''))
            time.sleep(2)
            route = {'cells': [[x0 + c[0], y0 + c[1], z0 + c[2]] for c in plan['cells']],
                     'actions': [dict(a, pos=[x0 + a['pos'][0], y0 + a['pos'][1], z0 + a['pos'][2]]) for a in plan.get('actions', [])]}
            rf = os.path.join(scratch, 'route%d.json' % n); json.dump(route, open(rf, 'w'))
            name = 'cfbot%d' % n
            env = dict(os.environ, NODE_PATH=NODE_MODULES)
            mark = len(sv.lines)
            bot = subprocess.Popen(['node', WALKER, rf, name, str(PORT)], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
            out = []
            threading.Thread(target=lambda: [out.append(l.rstrip()) for l in bot.stdout], daemon=True).start()
            sv.wait('%s joined the game' % name, mark, 90)
            s = route['cells'][0]
            sv.send('tp %s %.1f %d %.1f' % (name, s[0] + 0.5, s[1], s[2] + 0.5))
            t0 = time.time(); shown = 0; res = None
            while time.time() - t0 < 900:
                while shown < len(out):
                    ln = out[shown]; shown += 1
                    if ln.startswith('RESULT'): res = json.loads(ln[7:])
                    elif ln.startswith(('PROGRESS', 'START', 'DEATH', 'ERROR', 'KICKED', 'DAMAGE', 'ACTION')): print('   ' + ln, flush=True)
                if res or bot.poll() is not None: break
                time.sleep(0.5)
            for ln in out[shown:]:
                if ln.startswith('RESULT'): res = json.loads(ln[7:])
            try: bot.kill()
            except Exception: pass
            if res is None: res = {'result': 'no result', 'tail': out[-3:]}
            print('   RESULT', json.dumps(res), flush=True)
            results.append((n, plan['name'], res))
            time.sleep(2)
    finally:
        try: sv.send('stop'); sv.p.wait(timeout=90)
        except Exception: sv.p.kill()
    print('\n================ SUMMARY')
    ok = True
    for n, name, res in results:
        good = isinstance(res, dict) and res.get('result') == 'ok' and res.get('deaths', 1) == 0 and all(a.get('ok') for a in res.get('actions', []))
        ok &= good
        print('%s S%d %s -> %s' % ('PASS' if good else 'FAIL', n, name, res if not good else 'reached all %d cells, min health %s, slowest step %.1fs, %d/%d interactions ok' % (res['total'], res['minHealth'], res['maxStepSeconds'], sum(1 for a in res.get('actions', []) if a.get('ok')), len(res.get('actions', [])))))
    print('RESULT:', 'PASS' if ok else 'FAIL')


if __name__ == '__main__': main()
