"""E2E for real-time Pokemon combat: dungeon Pokemon hit the bot, the bot's sent-out Pokemon hit back, a lethal hit faints it.

Usage: python combat_e2e.py <jar> <scratch dir>   (needs the full test-server mod set: Cobblemon, AscensionLib ...)
"""
import json, os, subprocess, sys, threading, time
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, '..', 'cinderforge'))
import mod_e2e as M


def main():
    jar, scratch = sys.argv[1:3]
    os.environ['CF_FULL_MODS'] = '1'
    M.setup(jar, scratch)
    sv = M.Srv(scratch)
    ok = True
    def verdict(label, cond, extra=''):
        nonlocal ok
        ok &= bool(cond); print('%s %s %s' % ('PASS' if cond else 'FAIL', label, extra), flush=True)
    bot = None
    try:
        sv.wait('Done (', 0, 300); print('server up', flush=True)
        sv.send('forceload add 0 0 16 16'); time.sleep(2)
        sv.send('fill 0 63 0 16 63 16 minecraft:stone'); sv.send('setworldspawn 8 64 8'); time.sleep(1)
        mark = len(sv.lines)
        bot = subprocess.Popen(['node', os.path.join(HERE, 'drive.js'), 'e2e1', str(M.PORT)], env=dict(os.environ, NODE_PATH=M.NODE_MODULES),
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
        out = []
        threading.Thread(target=lambda: [out.append(l.rstrip()) for l in bot.stdout], daemon=True).start()
        sv.wait('e2e1 joined the game', mark, 90)
        sv.send('op e2e1'); time.sleep(1)
        n = [0]
        def cmd(**c):
            n[0] += 1; c['id'] = n[0]; m0 = len(out)
            bot.stdin.write(json.dumps(c) + '\n'); bot.stdin.flush()
            t0 = time.time()
            while time.time() - t0 < 60:
                if any(l == 'DONE %d' % n[0] for l in out[m0:]): break
                time.sleep(0.1)
            return out[m0:]
        def lines_since(m, sub): return [l for l in sv.lines[m:] if sub in l]
        print(sv.lines and [l for l in sv.lines if 'real-time combat' in l][-1:], flush=True)
        verdict('combat bridge loaded', any('real-time combat true' in l for l in sv.lines))
        sv.send('gamemode survival e2e1'); time.sleep(1)
        cmd(chat='/dungeon type water', after=1000)
        sv.wait('entered fantasy:', mark, 120)
        time.sleep(3)
        sv.send('give e2e1 minecraft:diamond_sword'); sv.send('effect give e2e1 minecraft:resistance 600 3 true')
        sv.send('givepokemonother e2e1 charizard level=80'); time.sleep(2)
        m1 = len(sv.lines)
        pos = next(l for l in cmd(pos=1) if l.startswith('POS')).split()
        ox, oy, oz = float(pos[1]) - 12.5, float(pos[2]) - 8, float(pos[3]) - 20.5      # entry cell (12,8,20)
        print('origin', ox, oy, oz, flush=True)
        sv.send('execute as e2e1 at @s run tp @s %.1f %.1f %.1f' % (ox + 64.5, oy + 8, oz + 15.5))     # inside R2 (Aqueduct Gallery)
        time.sleep(8)
        hits = lines_since(m1, 'Combat:')
        verdict('dungeon Pokemon hit the player', any((' on e2e1 ' in l) for l in hits), '%d combat lines; sample %s' % (len(hits), hits[:2]))
        m2 = len(sv.lines)
        sv.send('execute as e2e1 run dungeon sendout'); time.sleep(1)
        sv.send('data get entity e2e1 Health'); time.sleep(12)
        hits2 = lines_since(m2, 'Combat:')
        verdict('player Pokemon hit a dungeon Pokemon', any(('Charizard used' in l and ' on e2e1 ' not in l) for l in hits2), '%d lines; sample %s' % (len(hits2), hits2[:3]))
        verdict('dungeon Pokemon hit the player Pokemon', any((' on Charizard ' in l) for l in hits2))
        m3 = len(sv.lines)
        sv.send('execute as e2e1 at @s run damage @e[type=cobblemon:pokemon,name=Charizard,limit=1,sort=nearest] 9999 minecraft:generic'); time.sleep(3)
        verdict('lethal hit did not kill the player Pokemon (no death in log)', not any('Charizard' in l and ('died' in l or 'was slain' in l) for l in sv.lines[m3:]))
        verdict('player Pokemon fainted instead of dying', any('fainted' in l for l in out), str([l for l in out if 'Charizard' in l][:3]))
        sv.send('execute as e2e1 run dungeon sendout'); time.sleep(4)
        verdict('fainted Pokemon is held back during the cooldown', any('recovering' in l for l in out), str([l for l in out if 'recover' in l][:2]))
        # --- the real-time boss
        sv.send('effect give e2e1 minecraft:resistance 900 4 true'); sv.send('give e2e1 minecraft:netherite_sword')
        sv.send('execute as e2e1 at @s run tp @s %.1f %.1f %.1f' % (ox + 134.5, oy + 9, oz + 102.5)); time.sleep(6)
        mb = len(sv.lines)
        for kind in ('burn', 'poison', 'bleed'): sv.send('execute as e2e1 run dungeon inflict ' + kind)
        time.sleep(4)
        verdict('burn/poison ticks through the status ledger', any('Status:' in l for l in sv.lines[m3:]) or any('Status:' in l for l in sv.lines), 'Status lines: %d; replies %s' % (sum('Status:' in l for l in sv.lines), [l for l in out if 'Applied' in l or 'No dungeon' in l or 'Use burn' in l][:3]))
        bl = [l for l in sv.lines if 'Real-time boss' in l]
        verdict('boss spawned as a real-time Pokemon', bool(bl), str(bl[-1:]))
        cmd(equip='netherite_sword')
        sv.send('gamemode creative e2e1')
        for t in ([144, 9, 107], [125, 9, 107], [138, 9, 91]):
            sv.send('execute as e2e1 at @s run tp @s %d %d %d' % (ox + t[0] + 0.5, oy + t[1] + 1, oz + t[2] + 0.5)); time.sleep(5)
            sv.send('execute as e2e1 at @s run tp @s %d %d %d' % (ox + t[0] + 0.5, oy + t[1] + 1, oz + t[2] + 0.5)); time.sleep(1.5)
            cmd(dig=[int(ox) + t[0], int(oy) + t[1], int(oz) + t[2]])
        time.sleep(1); sv.send('gamemode survival e2e1')
        verdict('wards broke', any('wards shatter' in l.lower() for l in out), str([l[:80] for l in out if l.startswith('CHAT') and 'ward' in l.lower()][-6:]))
        sv.send('execute as e2e1 at @s run tp @s %.1f %.1f %.1f' % (ox + 134.5, oy + 9, oz + 102.5))
        sv.send('execute as e2e1 run dungeon sendout')
        sv.send('data modify entity @e[type=cobblemon:pokemon,name=Charizard,limit=1] Pokemon.Level set value 100')
        for _ in range(7):
            cmd(fight=15000)
            if any('The dungeon is yours' in l or 'reservoir drains' in l for l in out): break
        for _ in range(3): sv.send('data modify entity e2e1 Health set value 20f')
        bh = [l for l in sv.lines[mb:] if 'Combat:' in l and (' on e2e1 ' not in l)]
        verdict('boss was fought in real time and fell', any('The dungeon is yours' in l or 'reservoir drains' in l for l in out), 'tail: %s' % bh[-3:])
        errs = [l for l in sv.lines[mark:] if ('/ERROR]' in l or 'Exception' in l) and 'is corrupt or missing' not in l]
        pass
        verdict('no errors', not errs, str(errs[:3]))
    finally:
        if bot: bot.kill()
        try: sv.send('stop'); sv.p.wait(30)
        except Exception: sv.p.kill()
        open(os.path.join(HERE, 'combat_e2e.log'), 'w').write('\n'.join(sv.lines))
    print('ALL PASS' if ok else 'SOME FAILED')

main()
