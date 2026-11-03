"""End-to-end test of the AscensionDungeons mod's Cinderforge run on a scratch Fabric 1.21.1 server.

A mineflayer bot joins, is made op, types /dungeon type cinderforge, is teleported into the run level by the mod, then
walks a planned route (plans.json, schematic coordinates + the run level's origin) and performs scripted actions.

Usage: python mod_e2e.py <mod.jar> <plans.json> <scratch_dir> [scenario numbers...]
       env: CF_EXTRA_CMDS="cmd1;cmd2" (console commands sent after the bot joins), CF_OP_BEFORE=1
"""
import json, os, shutil, subprocess, sys, threading, time

JAVA = 'L:/claude-cobbleraids-work/jdk-21.0.12.1+1/bin/java.exe'
TESTSERVER = 'L:/ascensionDungeons/testserver'
NODE_MODULES = 'L:/claude-cobbleraids-work/bot/node_modules'
WALKER = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'walker.js')
PORT = 25592
ORIGIN = (0, 20, 0)          # CinderforgeLayout.ORIGIN_X/Y/Z


class Srv:
    def __init__(self, cwd):
        self.lines = []
        self.p = subprocess.Popen([JAVA, '-Xmx3G', '-Dascensiondungeons.debug=true', '-jar', 'fabric-server-launch.jar', 'nogui'], cwd=cwd, stdin=subprocess.PIPE,
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
        threading.Thread(target=self._rd, daemon=True).start()
    def _rd(self):
        for ln in self.p.stdout: self.lines.append(ln.rstrip())
    def send(self, c): self.p.stdin.write(c + '\n'); self.p.stdin.flush()
    def wait(self, sub, since, timeout):
        t = time.time()
        while time.time() - t < timeout:
            for ln in self.lines[since:]:
                if sub in ln: return ln
            if self.p.poll() is not None: raise RuntimeError('server exited; tail: %s' % self.lines[-8:])
            time.sleep(0.4)
        raise TimeoutError('timeout waiting for %r; tail: %s' % (sub, self.lines[-6:]))


def setup(jar, scratch):
    if os.path.exists(scratch): shutil.rmtree(scratch)
    os.makedirs(os.path.join(scratch, 'mods'))
    for name in ('fabric-server-launch.jar',): shutil.copy(os.path.join(TESTSERVER, name), scratch)
    for d in ('libraries', 'versions'): shutil.copytree(os.path.join(TESTSERVER, d), os.path.join(scratch, d))
    full = os.environ.get('CF_FULL_MODS') == '1'      # the whole test-server mod set (Cobblemon, Lootr, AscensionLib ...) minus our own jar
    for m in os.listdir(os.path.join(TESTSERVER, 'mods')):
        if m.startswith('AscensionDungeons'): continue
        if full or m.startswith(('fabric-api', 'lootr-fabric', 'cloth-config')): shutil.copy(os.path.join(TESTSERVER, 'mods', m), os.path.join(scratch, 'mods', m))
    if os.environ.get('CF_RAIDS') == '1':
        shutil.copy(os.path.join(TESTSERVER, 'mods-replaced-20261016', 'CobbleRaids-0.8.166-audit-hardening.jar'), os.path.join(scratch, 'mods'))
    if os.environ.get('CF_ASCENSION_JAR'):      # test a not-yet-deployed AscensionLib build
        for m in os.listdir(os.path.join(scratch, 'mods')):
            if m.startswith('AscensionLib'): os.remove(os.path.join(scratch, 'mods', m))
        shutil.copy(os.environ['CF_ASCENSION_JAR'], os.path.join(scratch, 'mods'))
    shutil.copy(jar, os.path.join(scratch, 'mods'))
    open(os.path.join(scratch, 'eula.txt'), 'w').write('eula=true\n')
    open(os.path.join(scratch, 'server.properties'), 'w').write(
        'server-port=%d\nonline-mode=false\nlevel-type=minecraft:flat\n'
        'generator-settings={"biome":"minecraft:the_void","layers":[],"structure_overrides":[]}\n'
        'spawn-monsters=true\nspawn-animals=false\nspawn-npcs=false\nview-distance=8\nsimulation-distance=6\n'
        'max-tick-time=-1\ndifficulty=normal\nenable-rcon=false\nspawn-protection=0\nallow-flight=true\n' % PORT)
    os.makedirs(os.path.join(scratch, 'config'), exist_ok=True)
    # shorten the run clock a little but keep it generous for slow walks: 20 minutes
    json.dump({'durationTicks': 20 * 60 * 20, 'maxActiveRuns': 8, 'prewarmRadiusChunks': 3}, open(os.path.join(scratch, 'config', 'ascensiondungeons.json'), 'w'))


def run_hook(sv, hook, bot_name, route_file, hook_id):
    """Run server console commands for the bot and check their output. '!text' means the output must NOT contain text."""
    ok, detail = True, []
    for cmd, expect in zip(hook['cmds'], hook.get('expects', [None] * len(hook['cmds']))):
        mark = len(sv.lines); sv.send(cmd.replace('{bot}', bot_name))
        if expect is None: time.sleep(0.6); detail.append('%s -> (sent)' % cmd[:60]); continue
        neg = expect.startswith('!'); needle = expect[1:] if neg else expect
        t0 = time.time(); seen = None
        while time.time() - t0 < (2.5 if neg else 8):
            seen = next((l for l in sv.lines[mark:] if needle in l), None)
            if seen: break
            time.sleep(0.25)
        good = (seen is None) if neg else (seen is not None)
        ok &= good; detail.append('%s -> %s %s' % (cmd[:70], 'ok' if good else 'MISSING', (seen or ' | '.join(sv.lines[mark:mark + 2]))[-90:]))
    print('   HOOK %s -> %s %s' % (hook_id, 'ok' if ok else 'FAILED', detail), flush=True)
    json.dump({'ok': ok, 'detail': detail}, open('%s.%s.done' % (route_file, hook_id), 'w'))


def main():
    jar, plans_path, scratch = sys.argv[1:4]
    only = {int(a) for a in sys.argv[4:]}
    plans = json.load(open(plans_path, encoding='utf-8'))['plans']
    setup(jar, scratch)
    sv = Srv(scratch)
    results = []
    try:
        sv.wait('Done (', 0, 240); print('server up', flush=True)
        # the scratch overworld is void: give players a floor to be returned to, as a real overworld would
        sv.send('forceload add 0 0 16 16'); time.sleep(2)
        sv.send('fill 0 63 0 16 63 16 minecraft:stone'); sv.send('setworldspawn 8 64 8'); time.sleep(1)
        errs = [l for l in sv.lines if '[ascensiondungeons]' in l.lower() and 'ERROR' in l]
        print('mod load errors:', errs[:3], flush=True)
        for n, plan in enumerate(plans, 1):
            if only and n not in only: continue
            print('\n=== %s' % plan['name'], flush=True)
            if plan['cells'] is None: results.append((n, plan['name'], 'NO ROUTE')); continue
            name = 'cf%d' % n
            route = {'cells': [[ORIGIN[0] + c[0], ORIGIN[1] + c[1], ORIGIN[2] + c[2]] for c in plan['cells']],
                     'actions': [dict(a, **({'pos': [ORIGIN[0] + a['pos'][0], ORIGIN[1] + a['pos'][1], ORIGIN[2] + a['pos'][2]]} if a.get('pos') else {}),
                                      **({'positions': [[ORIGIN[0] + p[0], ORIGIN[1] + p[1], ORIGIN[2] + p[2]] for p in a['positions']]} if a.get('positions') else {}))
                                 for a in plan.get('actions', [])],
                     'command': '/dungeon type cinderforge', 'commandDelayMs': 6000, 'timeoutMs': int(plan.get('timeoutMs', 600000))}
            rf = os.path.join(scratch, 'route%d.json' % n); json.dump(route, open(rf, 'w'))
            mark = len(sv.lines)
            bot = subprocess.Popen(['node', WALKER, rf, name, str(PORT)], env=dict(os.environ, NODE_PATH=NODE_MODULES),
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
            out = []
            threading.Thread(target=lambda: [out.append(l.rstrip()) for l in bot.stdout], daemon=True).start()
            sv.wait('%s joined the game' % name, mark, 90)
            sv.send('op %s' % name)
            for c in [x for x in os.environ.get('CF_EXTRA_CMDS', '').split(';') if x.strip()] + list(plan.get('consoleCmds', [])):
                sv.send(c.strip().replace('{bot}', name)); time.sleep(0.4)
            if plan.get('preTp'):
                sv.wait('entered fantasy:', mark, 120)      # the mod has moved the bot into the run level
                time.sleep(1.5)
                t = plan['preTp']; sv.send('execute as %s at @s run tp @s %.1f %d %.1f' % (name, ORIGIN[0] + t[0] + 0.5, ORIGIN[1] + t[1], ORIGIN[2] + t[2] + 0.5))   # console tp would land in the overworld
            t0 = time.time(); shown = 0; res = None
            while time.time() - t0 < plan.get('timeoutMs', 600000) / 1000 + 60:
                while shown < len(out):
                    ln = out[shown]; shown += 1
                    if ln.startswith('RESULT'): res = json.loads(ln[7:])
                    elif ln.startswith('HOOK '): run_hook(sv, plan['hooks'][ln[5:].strip()], name, rf, ln[5:].strip())
                    elif ln.startswith(('PROGRESS', 'START', 'DEATH', 'ERROR', 'KICKED', 'DAMAGE', 'ACTION', 'SAY', 'CHAT')): print('   ' + ln[:300], flush=True)
                if res or bot.poll() is not None: break
                time.sleep(0.5)
            for ln in out[shown:]:
                if ln.startswith('RESULT'): res = json.loads(ln[7:])
            try: bot.kill()
            except Exception: pass
            run_id = next((l.split('run ')[-1].strip() for l in out if l.startswith('CHAT Queued cinderforge run')), None)
            if run_id: sv.send('dungeon end %s' % run_id); time.sleep(1)      # free the level so later scenarios are not slowed by it
            if res is None: res = {'result': 'no result', 'tail': out[-4:]}
            json.dump(res, open(os.path.join(scratch, 'result%d.json' % n), 'w'), indent=1)
            results.append((n, plan['name'], res)); print('   RESULT', json.dumps(res)[:400], flush=True)
            time.sleep(2)
        # the overworld's own rules must be untouched by Cinderforge runs
        for rule, want in (('doMobSpawning', 'true'), ('doFireTick', 'true'), ('mobGriefing', 'true')):
            mark = len(sv.lines); sv.send('gamerule %s' % rule); time.sleep(1.2)
            got = next((l for l in sv.lines[mark:] if rule in l), '')
            print('overworld gamerule %s: %s' % (rule, 'OK' if want in got else 'CHANGED! ' + got), flush=True)
        srv_errs = [l for l in sv.lines if ' ERROR]' in l and 'data fixer' not in l.lower()]
        print('\nserver ERROR lines: %d' % len(srv_errs)); [print('   ' + l[:260]) for l in srv_errs[:12]]
    finally:
        try: sv.send('stop'); sv.p.wait(timeout=90)
        except Exception: sv.p.kill()
    print('\n================ SUMMARY')
    ok = True
    for n, name, res in results:
        plan_n = plans[n - 1]
        dmg_ok = True
        if isinstance(res, dict) and plan_n.get('expectDamage'): dmg_ok = res.get('damageTaken', 0) > 0
        if isinstance(res, dict) and plan_n.get('expectNoDamage'): dmg_ok = res.get('damageTaken', 1) == 0
        good = isinstance(res, dict) and res.get('result') == 'ok' and res.get('deaths', 1) == 0 and all(a.get('ok') for a in res.get('actions', [])) and dmg_ok
        ok &= good
        print('%s S%d %s -> %s' % ('PASS' if good else 'FAIL', n, name, 'reached all %d cells, %d/%d actions ok' % (res['total'], sum(1 for a in res['actions'] if a.get('ok')), len(res['actions'])) if good else res))
    print('RESULT:', 'PASS' if ok else 'FAIL')


if __name__ == '__main__': main()
