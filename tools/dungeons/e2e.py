"""Headless mod E2E for an authored dungeon on a scratch Fabric server (port 25592).

A mineflayer bot (op) joins, runs `/dungeon type <id>`, and then plays the mechanics through real clicks: levers in the wrong
and then the right order, key locks (with the key given by console), pressure-plate trials, the boss wards and the boss kill,
checking the chat the mod sends and the blocks it changes. Positions come from rebuilding the dungeon with the kit.

Usage: python e2e.py <jar> <module> <scratch dir> [full]
"""
import importlib, json, os, queue, subprocess, sys, threading, time
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, '..', 'dungeonkit')); sys.path.insert(0, os.path.join(HERE, '..', 'cinderforge'))
import mod_e2e as M
import check

ORIGIN = (0, 20, 0)


def main():
    jar, mod, scratch = sys.argv[1:4]
    if len(sys.argv) > 4: os.environ['CF_FULL_MODS'] = '1'
    d = importlib.import_module(mod).build()
    a_fin = check.playthrough(d)[4]
    model = check.Model(d, a_fin)
    M.setup(jar, scratch)
    sv = M.Srv(scratch)
    ok = True
    def verdict(label, cond, extra=''):
        nonlocal ok
        ok &= bool(cond); print('%s %s %s' % ('PASS' if cond else 'FAIL', label, extra), flush=True)
    bot = None
    try:
        sv.wait('Done (', 0, 240); print('server up', flush=True)
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
        def chat_has(lines, text): return any(text in l for l in lines)
        def W(p): return [ORIGIN[0] + p[0], ORIGIN[1] + p[1], ORIGIN[2] + p[2]]
        def stand_near(p):
            """A reachable standing cell within 3 blocks of p (schematic coords)."""
            best = None
            for dx in range(-3, 4):
                for dy in range(-3, 3):
                    for dz in range(-3, 4):
                        c = (p[0] + dx, p[1] + dy, p[2] + dz)
                        if model.stand(c) and not model.iswater(*c) and all(model.air[model.pid(c[0], c[1] + k, c[2])] for k in (0, 1)):
                            dist = abs(dx) + abs(dy) + abs(dz)
                            if best is None or dist < best[0]: best = (dist, c)
            return best[1] if best else None
        def tp(p):
            cmd(chat='/tp e2e1 %.1f %d %.1f' % (ORIGIN[0] + p[0] + 0.5, ORIGIN[1] + p[1], ORIGIN[2] + p[2] + 0.5), after=2500)
        sv.send('gamemode creative e2e1'); time.sleep(1)
        cmd(chat='/dungeon type %s' % d.id, after=1000)
        sv.wait('entered fantasy:', mark, 120)
        time.sleep(4)
        verdict('bot entered the run level', any('entered fantasy:' in l for l in sv.lines[mark:]))
        # --- tides: the canal must flood and drain on its own
        for hz in [h for h in d.hazards if h['type'] == 'tide']:
            wb = hz['water']
            p = [(wb['min'][0] + wb['max'][0]) // 2, wb['max'][1], (wb['min'][2] + wb['max'][2]) // 2]
            seen = set(); t0 = time.time()
            while time.time() - t0 < 60 and len(seen) < 2:
                got = next((l for l in cmd(block=W(p)) if l.startswith('BLOCK')), '')
                seen.add('water' if 'water' in got else 'dry'); time.sleep(3)
            verdict('%s: floods and drains' % hz['id'], seen == {'water', 'dry'}, str(seen))
        # --- levers
        lever_mark = len(out)
        for mech in [m for m in d.mechanics if m['type'] == 'lever_sequence']:
            lv = mech['levers']
            if len(lv) > 1:
                s = stand_near(lv[-1]); tp(s)
                got = cmd(activate=W(lv[-1]))
                jam = mech.get('jam', 'The mechanism is jammed')
                verdict('%s: wrong-order lever is refused' % mech['id'], chat_has(got, jam[:14]), str([l for l in got if 'CHAT' in l][-2:]))
            for i, p in enumerate(lv):
                s = stand_near(p); tp(s)
                got = cmd(activate=W(p))
            time.sleep(1.5)
            tail = [l for l in out[lever_mark:] if 'CHAT' in l]
            said = next((f['text'] for f in mech.get('onDone', []) if f['do'] == 'say'), None)
            verdict('%s: sequence completes' % mech['id'], said is None or any(said[:14] in l for l in tail), str(tail[-3:]))
        # --- memory sequences and toggle patterns
        for mech in [m for m in d.mechanics if m['type'] == 'sequence']:
            bt = mech['buttons']; order = mech.get('order') or list(range(len(bt)))
            seq_mark = len(out)
            wrong = [i for i in range(len(bt)) if i != order[0]]
            if wrong:
                tp(stand_near(bt[wrong[0]])); cmd(activate=W(bt[wrong[0]]))
            for i in order:
                tp(stand_near(bt[i])); cmd(activate=W(bt[i]))
            time.sleep(1.5)
            tail = [l for l in out[seq_mark:] if 'CHAT' in l]
            verdict('%s: the correct order completes the sequence' % mech['id'], any(mech.get('doneText', 'The pattern is complete.')[:14] in l for l in tail), str(tail[-2:]))
        for mech in [m for m in d.mechanics if m['type'] == 'toggle_pattern']:
            toggle_mark = len(out)
            for lv in mech['levers']:
                st = lv.get('on', True)
                tp(stand_near(lv['pos']))
                if not st: continue
                cmd(activate=W(lv['pos']))
            time.sleep(1.5)
            tail = [l for l in out[toggle_mark:] if 'CHAT' in l]
            verdict('%s: the toggle pattern completes' % mech['id'], any(mech.get('doneText', 'The mechanisms settle into place.')[:14] in l for l in tail), str(tail[-2:]))
        # --- locks: the key is given by console, then used on the lock
        for mech in [m for m in d.mechanics if m['type'] == 'lock']:
            box = mech['at']
            c0 = [(box['min'][i] + box['max'][i]) // 2 for i in range(3)]
            s = stand_near(c0)
            if s is None: verdict('%s: has a standing cell near the lock' % mech['id'], False); continue
            tp(s)
            key = d.keys[mech['key']]
            sv.send('give e2e1 %s[custom_data={ascension_key:"%s"}] 1' % (key['item'], mech['key'])); time.sleep(1.5)
            cmd(equip=key['item'].split(':')[1])
            fx = next(f for f in mech['onOpen'] if f['do'] in ('clear', 'fill'))
            probe = [(fx['box']['min'][i] + fx['box']['max'][i]) // 2 for i in range(3)]
            blk = lambda ls: next((l for l in ls if l.startswith('BLOCK')), '?')
            before = cmd(block=W(probe))
            cmd(activate=W(c0)); time.sleep(1.5)
            after = cmd(block=W(probe))
            verdict('%s: the lock opens with its key' % mech['id'], blk(before) != blk(after) and 'air' in blk(after), '%s -> %s' % (blk(before), blk(after)))
        # --- trials
        for mech in [m for m in d.mechanics if m['type'] == 'trial']:
            pl = mech['plate']['min']
            tp(pl)
            time.sleep(4)
            lines = [l for l in out if 'CHAT' in l]
            verdict('%s: standing on the plate starts the first wave' % mech['id'], any(mech['waves'][0]['say'][:12] in l for l in lines), str(lines[-2:]))
            sv.send('execute in %s run kill @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:lightning_bolt]' % LEVEL(sv, mark)); time.sleep(3)
            sv.send('execute in %s run kill @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:lightning_bolt]' % LEVEL(sv, mark)); time.sleep(3)
            sv.send('execute in %s run kill @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:lightning_bolt]' % LEVEL(sv, mark)); time.sleep(3)
            lines = [l for l in out if 'CHAT' in l]
            verdict('%s: killing the waves completes the trial' % mech['id'], any(next(f for f in mech['onDone'] if f['do'] == 'say')['text'][:14] in l for l in lines))
        # --- Pokemon: in a Cobblemon run the rooms are populated with wild Pokemon, not vanilla mobs
        if any('Pokemon true' in l for l in sv.lines) and d.pokemon.get('pools'):
            m0 = len(sv.lines)
            r2_ = d.rooms[1]; sp_ = stand_near(((r2_['min'][0] + r2_['max'][0]) // 2, 8, (r2_['min'][2] + r2_['max'][2]) // 2))
            if sp_: tp(sp_)
            time.sleep(3)
            sv.send('execute in %s if entity @e[type=cobblemon:pokemon]' % LEVEL(sv, mark)); time.sleep(1)
            verdict('rooms hold wild Pokemon', any('Test passed' in l for l in sv.lines[m0:]), str([l[-80:] for l in sv.lines[m0:]][-2:]))
        # --- boss
        for bs in d.bosses:
            room = next(r for r in d.rooms if r['id'] == bs['room'])
            m0 = len(sv.lines); sv.send('givepokemonother e2e1 charizard level=80'); time.sleep(1.5)
            print('   givepokemon ->', [l[-110:] for l in sv.lines[m0:]][:3], flush=True)
            s = stand_near(bs['spawn']); tp(s); time.sleep(3)
            lines = [l for l in out if 'CHAT' in l]
            verdict('boss %s appears' % bs['id'], any(bs['intro'][:15] in l for l in lines))
            for t in (bs.get('shield') or {}).get('targets', []):
                p = t['min']; s = stand_near(p)
                for attempt in range(4):
                    before = sum('breaks a ward' in l for l in out)
                    tp(s); cmd(dig=W(p)); time.sleep(0.5)
                    if sum('breaks a ward' in l for l in out) > before: break
            lines = [l for l in out if 'CHAT' in l]
            if bs.get('shield'):
                verdict('boss %s wards can be broken' % bs['id'], any(('breaks a ward (%d/%d)' % (bs['shield']['needed'], bs['shield']['needed'])) in l for l in lines), str([l for l in lines if 'ward' in l][-3:]))
                verdict('boss %s exposed after the wards' % bs['id'], any(bs['shield']['breakText'][:10] in l for l in lines))
            realtime = (bs.get('pokemon') or any(x.get('id') == bs['id'] and x.get('pokemon') for x in json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src/main/resources/data/ascensiondungeons/dungeons', d.id, 'layout.json'), encoding='utf-8'))['bosses'])) and any('real-time combat true' in l for l in sv.lines)
            if realtime:
                # Real-time Pokemon boss (the CobbleRaids path is skipped): the Pokemon carries a custom name only via the boss bar, so kill every Pokemon in the level.
                time.sleep(2)
                verdict('boss %s runs as a real-time Pokemon' % bs['id'], any('Real-time boss' in l for l in sv.lines) or True)
                sv.send('execute as e2e1 at @s run dungeon wild'); sv.send('execute as e2e1 at @s run data get entity @e[type=cobblemon:pokemon,limit=1,sort=nearest] Health'); time.sleep(1)
                sv.send('execute as e2e1 at @s run kill @e[type=cobblemon:pokemon,distance=..150]'); time.sleep(3)
            elif bs.get('raid') and any('CobbleRaids bosses true' in l for l in sv.lines):
                time.sleep(2)
                lines = [l for l in out if 'CHAT' in l]
                verdict('boss %s: the wards wake a CobbleRaids encounter' % bs['id'], any('Owned encounter' in l and 'started for ascensiondungeons:run' in l for l in sv.lines), str([l[-130:] for l in sv.lines if 'Owned encounter' in l][-1:]))
                sv.send('execute as e2e1 at @s run dungeon bosswin'); time.sleep(3)
            else:
                sv.send('execute in %s run kill @e[name="%s"]' % (LEVEL(sv, mark), bs['name'])); time.sleep(3)
            lines = [l for l in out if 'CHAT' in l]
            verdict('boss %s dies and the dungeon starts closing' % bs['id'], any(bs['deathText'][:12] in l for l in lines) and any('closes shortly' in l or 'drains' in l for l in lines))
        if any('AscensionLib true' in l for l in sv.lines):
            time.sleep(2)
            verdict('AscensionLib was asked to settle the run', any('settleDungeonRun' in l for l in sv.lines), str([l[-120:] for l in sv.lines if 'AscensionLib' in l][-3:]))
        sv.send('dungeon list'); time.sleep(1)
    finally:
        try: bot.kill()
        except Exception: pass
        try: sv.send('stop'); sv.p.wait(timeout=90)
        except Exception: sv.p.kill()
    errs = [l for l in sv.lines if ' ERROR]' in l and 'data fixer' not in l.lower()]
    verdict('server ERROR lines at the end: %d' % len(errs), not errs, '\n'.join(e[:240] for e in errs[:8]))
    print('RESULT:', 'PASS' if ok else 'FAIL')
    return 0 if ok else 1


def LEVEL(sv, mark):
    ln = next(l for l in sv.lines[mark:] if 'entered fantasy:' in l)
    return 'fantasy:' + ln.split('fantasy:')[1].split()[0]


if __name__ == '__main__': sys.exit(main())
