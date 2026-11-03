"""Smoke test that the original district dungeon and the Fire pilot gate still work next to Cinderforge (full mod set)."""
import os, subprocess, sys, threading, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mod_e2e as E

jar, scratch = sys.argv[1], sys.argv[2]
os.environ['CF_FULL_MODS'] = '1'
E.setup(jar, scratch)
sv = E.Srv(scratch)
ok = True
try:
    sv.wait('Done (', 0, 300); print('server up')
    sv.send('forceload add 0 0 16 16'); time.sleep(2); sv.send('fill 0 63 0 16 63 16 minecraft:stone'); sv.send('setworldspawn 8 64 8')
    for label, cmd in (('legacy district run', '/dungeon start'), ('Fire pilot without CobbleRaids', '/dungeon type fire')):
        name = 'sm%d' % (1 if 'legacy' in label else 2)
        mark = len(sv.lines)
        bot = subprocess.Popen(['node', os.path.join(os.path.dirname(E.__file__), 'idle_bot.js'), name, str(E.PORT), cmd, '40000'],
                               env=dict(os.environ, NODE_PATH=E.NODE_MODULES), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        out = []
        threading.Thread(target=lambda: [out.append(l.rstrip()) for l in bot.stdout], daemon=True).start()
        sv.wait('%s joined the game' % name, mark, 90); sv.send('op %s' % name)
        bot.wait(timeout=90)
        log = '\n'.join(sv.lines[mark:])
        print('== %s: bot saw: %s' % (label, [l for l in out if l.startswith(('CHAT', 'DIM'))][-4:]))
        if 'legacy' in label: good = 'entered fantasy:' in log and 'DIM fantasy' in '\n'.join(out) or 'entered fantasy:' in log
        else: good = any('requires Cobblemon and CobbleRaids' in l for l in out)
        errs = [l for l in sv.lines[mark:] if ' ERROR]' in l and 'data fixer' not in l.lower()]
        print('   result: %s, server errors: %d %s' % ('OK' if good and not errs else 'FAIL', len(errs), errs[:2])); ok &= good and not errs
        sv.send('dungeon list'); time.sleep(1)
finally:
    try: sv.send('stop'); sv.p.wait(timeout=90)
    except Exception: sv.p.kill()
print('RESULT:', 'PASS' if ok else 'FAIL')
