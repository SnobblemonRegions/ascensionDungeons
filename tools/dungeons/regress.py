"""Re-run the mod E2E for every authored dungeon against the current jar. Usage: python regress.py [jar]   (CF_FULL=1 runs with Cobblemon + AscensionLib; CF_ASCENSION_JAR overrides the library jar)"""
import json, os, subprocess, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
ids = json.load(open(os.path.join(ROOT, 'src/main/resources/data/ascensiondungeons/dungeons/index.json')))
jar = sys.argv[1] if len(sys.argv) > 1 else 'build/libs/AscensionDungeons-0.0.7-cinderforge.jar'
bad = []
for did in ids:
    r = subprocess.run([sys.executable, 'tools/dungeons/e2e.py', jar, did, 'L:/claude-cobbleraids-work/scratch-' + did[:6]] + (['full'] if os.environ.get('CF_FULL') else []), cwd=ROOT, capture_output=True, text=True)
    ok = 'RESULT: PASS' in r.stdout
    fails = [l for l in r.stdout.splitlines() if l.startswith('FAIL')]
    print('%-9s %s %s' % (did, 'PASS' if ok else 'FAIL', fails[:2]), flush=True)
    if not ok: bad.append(did)
print('REGRESSION', 'ALL PASS' if not bad else 'FAILED: %s' % bad)
