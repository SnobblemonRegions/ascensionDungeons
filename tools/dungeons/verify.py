"""Full pipeline for one dungeon: kit checks + install, gradle build, vanilla world diff, mod E2E. Usage: python verify.py <id>"""
import os, subprocess, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
ENV = dict(os.environ, GRADLE_USER_HOME=r'L:\gradle-home-ascensiondungeons', JAVA_HOME=r'L:\claude-cobbleraids-work\jdk-21.0.12.1+1')


def sh(cmd, **kw):
    r = subprocess.run(cmd, cwd=ROOT, env=ENV, capture_output=True, text=True, **kw)
    return r.returncode, (r.stdout or '') + (r.stderr or '')


def main():
    did = sys.argv[1]
    code, out = sh([sys.executable, 'tools/dungeons/run.py', did, '--write'])
    lines = out.splitlines()
    print('\n'.join(l for l in lines if any(k in l for k in ('RESULT', 'VIOLATED', 'NEVER', 'UNREACH', 'NOT reach', 'unreach', 'leak', 'only', 'wrote', 'Traceback', 'Error', 'harmful', 'submerged')) and ' 0 leaks' not in l and 'standing on harmful blocks: 0' not in l and 'air pocket within 20 and no water-breathing zone: 0' not in l))
    if code != 0: print('KIT CHECKS FAILED'); print(out[-1500:]); return 1
    code, out = sh([os.path.join(ROOT, 'gradlew.bat'), 'build', '--offline', '-q'])
    if code != 0: print('GRADLE FAILED'); print(out[-1500:]); return 1
    jar = 'build/libs/AscensionDungeons-0.0.7-cinderforge.jar'
    code, out = sh([sys.executable, 'tools/cinderforge/vanilla_world_test.py', 'src/main/resources/data/ascensiondungeons/dungeons/%s/dungeon.schem' % did, 'L:/claude-cobbleraids-work/scratch-vwt', 'overworld', '3000'])
    print('\n'.join([l for l in out.splitlines() if any(k in l for k in ('mismatches', 'at schem', 'RESULT', 'sealed interior', 'lava expected'))][:14]))
    vok = 'RESULT: PASS' in out
    code, out = sh([sys.executable, 'tools/dungeons/e2e.py', jar, did, 'L:/claude-cobbleraids-work/scratch-' + did[:6]])
    print('\n'.join(l for l in out.splitlines() if l.startswith(('PASS', 'FAIL', 'RESULT'))))
    eok = 'RESULT: PASS' in out
    print('PIPELINE', did, 'vanilla', vok, 'e2e', eok)
    return 0 if vok and eok else 1


if __name__ == '__main__': sys.exit(main())
