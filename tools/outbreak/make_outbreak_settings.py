"""Writes dungeons/<type>/outbreak.json for every type dungeon, from that dungeon's own layout.json.

Usage: python tools/outbreak/make_outbreak_settings.py [src/main/resources/data/ascensiondungeons/dungeons]

A dungeon is a type dungeon when its folder is named after a Pokemon type; anything else (cinderforge, halloween_graveyard) is
outdated and is left alone. Pools, the trial, the locks and the themed fallback mobs come from the layout; the spells, colours
and names per type are the table below. Times are in seconds. Every number is provisional. Edit the table and re-run rather
than editing the generated files by hand.
"""
import json, os, sys

TYPES = ['normal', 'fire', 'water', 'grass', 'electric', 'ice', 'fighting', 'poison', 'ground', 'flying', 'psychic', 'bug', 'rock', 'ghost', 'dragon', 'dark', 'steel', 'fairy']

# (id, shown name, effect, damage, radius, warn s, cooldown s, range, strength)
SPELLS = {
    'normal':   [('hyper_voice', 'Hyper Voice', 'push', 4, 4.0, 1.5, 12, 10, 0.9), ('swift', 'Swift', 'none', 5, 2.5, 1.2, 8, 14, 0), ('yawn', 'Yawn', 'slow', 1, 3.5, 2.0, 14, 10, 5)],
    'water':    [('whirlpool', 'Whirlpool', 'pull', 5, 3.5, 2.0, 14, 12, 0.5), ('water_spout', 'Water Spout', 'knockup', 4, 2.5, 1.5, 12, 10, 0.9), ('bubble_beam', 'Bubble Beam', 'slow', 3, 3.0, 1.5, 9, 14, 4)],
    'grass':    [('vine_whip', 'Vine Whip', 'pull', 4, 3.0, 1.5, 11, 12, 0.6), ('stun_spore', 'Stun Spore', 'slow', 2, 4.0, 2.0, 14, 10, 6), ('solar_beam', 'Solar Beam', 'none', 9, 2.5, 3.0, 18, 16, 0)],
    'electric': [('thunderbolt', 'Thunderbolt', 'none', 7, 2.0, 1.2, 10, 16, 0), ('thunder_wave', 'Thunder Wave', 'slow', 1, 4.0, 1.8, 14, 12, 6), ('discharge', 'Discharge', 'push', 5, 4.0, 1.5, 13, 8, 0.8)],
    'ice':      [('blizzard', 'Blizzard', 'freeze', 4, 4.5, 2.5, 16, 12, 5), ('icy_wind', 'Icy Wind', 'slow', 3, 3.5, 1.5, 10, 12, 4), ('ice_shard', 'Ice Shard', 'none', 6, 2.0, 1.0, 8, 16, 0)],
    'fighting': [('seismic_toss', 'Seismic Toss', 'knockup', 5, 2.5, 1.5, 12, 8, 1.0), ('low_sweep', 'Low Sweep', 'slow', 4, 3.5, 1.2, 10, 8, 4), ('vacuum_wave', 'Vacuum Wave', 'push', 4, 3.0, 1.0, 9, 12, 1.0)],
    'poison':   [('sludge_bomb', 'Sludge Bomb', 'poison', 4, 3.0, 1.5, 11, 14, 5), ('toxic', 'Toxic', 'poison', 1, 3.5, 2.0, 16, 10, 10), ('smog', 'Smog', 'blind', 2, 4.5, 2.0, 15, 10, 5)],
    'ground':   [('earthquake', 'Earthquake', 'knockup', 6, 5.0, 2.5, 16, 10, 0.8), ('mud_shot', 'Mud Shot', 'slow', 4, 3.0, 1.5, 10, 14, 5), ('sand_tomb', 'Sand Tomb', 'pull', 4, 3.5, 2.0, 14, 12, 0.5)],
    'flying':   [('gust', 'Gust', 'push', 4, 4.0, 1.5, 11, 12, 1.1), ('air_slash', 'Air Slash', 'none', 6, 2.5, 1.2, 9, 16, 0), ('hurricane', 'Hurricane', 'knockup', 5, 4.0, 2.5, 16, 12, 1.1)],
    'psychic':  [('psychic', 'Psychic', 'levitate', 4, 3.0, 2.0, 14, 14, 3), ('hypnosis', 'Hypnosis', 'slow', 1, 3.5, 2.0, 14, 12, 6), ('psybeam', 'Psybeam', 'none', 6, 2.5, 1.3, 9, 16, 0)],
    'bug':      [('string_shot', 'String Shot', 'slow', 2, 3.5, 1.5, 10, 14, 6), ('bug_buzz', 'Bug Buzz', 'push', 5, 4.0, 1.5, 12, 8, 0.8), ('pin_missile', 'Pin Missile', 'none', 6, 2.0, 1.0, 8, 16, 0)],
    'rock':     [('rock_slide', 'Rock Slide', 'none', 8, 3.5, 2.5, 15, 14, 0), ('rock_tomb', 'Rock Tomb', 'slow', 4, 3.0, 1.5, 11, 12, 5), ('ancient_power', 'Ancient Power', 'knockup', 5, 3.0, 2.0, 14, 12, 0.9)],
    'ghost':    [('shadow_ball', 'Shadow Ball', 'none', 7, 2.5, 1.3, 9, 16, 0), ('night_shade', 'Night Shade', 'blind', 3, 4.0, 2.0, 14, 12, 5), ('curse', 'Curse', 'wither', 2, 3.0, 2.0, 16, 10, 6)],
    'dragon':   [('dragon_breath', 'Dragon Breath', 'burn', 5, 3.5, 1.8, 12, 12, 4), ('dragon_pulse', 'Dragon Pulse', 'none', 8, 2.5, 1.5, 10, 16, 0), ('twister', 'Twister', 'knockup', 5, 3.5, 2.0, 14, 12, 1.0)],
    'dark':     [('dark_pulse', 'Dark Pulse', 'none', 7, 3.0, 1.3, 9, 14, 0), ('snarl', 'Snarl', 'weaken', 3, 4.0, 1.5, 12, 10, 8), ('night_daze', 'Night Daze', 'blind', 4, 4.0, 2.0, 15, 12, 5)],
    'steel':    [('flash_cannon', 'Flash Cannon', 'none', 7, 2.5, 1.5, 10, 16, 0), ('metal_sound', 'Metal Sound', 'weaken', 2, 4.5, 1.8, 14, 10, 8), ('magnet_pull', 'Magnet Pull', 'pull', 4, 4.0, 2.0, 14, 14, 0.7)],
    'fairy':    [('moonblast', 'Moonblast', 'none', 7, 3.0, 1.8, 11, 16, 0), ('charm', 'Charm', 'weaken', 1, 4.0, 2.0, 14, 12, 8), ('fairy_wind', 'Fairy Wind', 'push', 4, 3.5, 1.3, 10, 12, 0.9)],
}
COLOUR = {'normal': 0xA8A77A, 'water': 0x6390F0, 'grass': 0x7AC74C, 'electric': 0xF7D02C, 'ice': 0x96D9D6, 'fighting': 0xC22E28, 'poison': 0xA33EA1,
          'ground': 0xE2BF65, 'flying': 0xA98FF3, 'psychic': 0xF95587, 'bug': 0xA6B91A, 'rock': 0xB6A136, 'ghost': 0x735797, 'dragon': 0x6F35FC,
          'dark': 0x705746, 'steel': 0xB7B7CE, 'fairy': 0xD685AD}
ENEMY = {'normal': 'Restless', 'water': 'Tidebound', 'grass': 'Overgrown', 'electric': 'Charged', 'ice': 'Rimebound', 'fighting': 'Sparring', 'poison': 'Blighted',
         'ground': 'Burrowing', 'flying': 'Stormborne', 'psychic': 'Mindlost', 'bug': 'Swarming', 'rock': 'Stonebound', 'ghost': 'Restless', 'dragon': 'Scaled',
         'dark': 'Shadowed', 'steel': 'Riveted', 'fairy': 'Bewitched'}

# Depths materials for the dungeons whose own most-used blocks do not make a good wall, floor, accent and pillar.
PALETTE = {
    'ice': {'wall': 'minecraft:calcite', 'floor': 'minecraft:diorite', 'accent': 'minecraft:quartz_block', 'pillar': 'minecraft:packed_ice'},
}

def build(tid, layout):
    pools = layout.get('pokemon', {}).get('pools', {})
    assert all(k in pools for k in ('wild_low', 'wild_mid', 'trial', 'boss')), tid + ' is missing a pool'
    # Themed vanilla stand-ins for a server without Cobblemon: whatever this dungeon's own encounters use.
    ents = []
    for enc in layout['encounters']:
        for s in enc['spawns']:
            if s.get('entity') and s['entity'] not in ents: ents.append(s['entity'])
    if not ents: ents = ['minecraft:zombie']
    e = lambda i: ents[i % len(ents)]
    boss_entity = layout['bosses'][0]['entity'] if layout['bosses'] else e(0)
    spells = SPELLS[tid]
    mech = layout['mechanics']
    trial = next((m['id'] for m in mech if m['type'] == 'trial'), None)
    first_gate = next((m['id'] for m in mech if m['type'] in ('lever_sequence', 'toggle_pattern', 'sequence')), None)
    lock = next((m['id'] for m in mech if m['type'] == 'lock' and m['id'] != 'arena_door'), None)
    checkpoints = [c for c in (first_gate, trial, lock) if c]
    name = ENEMY[tid]
    return {
        '_comment': 'Generated by tools/outbreak/make_outbreak_settings.py from this dungeon\'s layout.json. Times are in seconds; every number is provisional. '
                    'features.models: true shows the chest models from resourcepack/; players without that pack would see a floating chorus fruit.',
        'type': tid,
        'colour': COLOUR[tid],
        'durationMinutes': 45,
        'clock': {'secondsPerStage': 360, 'maxStage': 5, 'spawnStep': 0.25, 'eliteStep': 0.06, 'eliteCap': 0.3, 'stagesPerTierStep': 2},
        'portal': {'windowSeconds': 30, 'growthPercent': 25, 'radius': 2.5, 'tollPercent': 35},
        'waves': {'everySeconds': 45, 'base': 2, 'perStage': 1, 'perExtraPlayer': 1, 'maxAlive': 14, 'spawns': [
            {'minStage': 0, 'entity': e(0), 'pokemon': 'wild_low', 'style': 'hunter', 'name': name + ' Hunter'},
            {'minStage': 1, 'entity': e(0), 'pokemon': 'wild_low', 'style': 'pack', 'name': name + ' Pack'},
            {'minStage': 2, 'entity': e(1), 'pokemon': 'wild_mid', 'style': 'skirmisher', 'name': name + ' Caster', 'spell': spells[2][0]},
            {'minStage': 3, 'entity': e(0), 'pokemon': 'wild_mid', 'style': 'ambusher', 'name': name + ' Lurker'}]},
        'elite': {'healthMultiplier': 2.0, 'damageMultiplier': 1.4, 'spells': [spells[0][0], spells[1][0]]},
        'guardians': {'entity': e(1), 'pokemon': 'trial', 'name': 'Strongbox Guardian'},
        'gatekeeper': {'entity': boss_entity, 'pokemon': 'boss', 'name': tid.capitalize() + ' Gatekeeper', 'health': 6.0, 'healthPerStage': 0.5, 'damagePerStage': 0.2,
                       'levelPerStage': 3, 'spells': [s[0] for s in spells]},
        'checkpoints': checkpoints,
        # The Depths: a procedural maze under the dungeon, different every run. 7 x 7 cells of 24 blocks, three quarters of them rooms.
        # Its blocks are the dungeon's own most-used building blocks unless 'palette': {'wall','floor','accent','pillar'} is given here.
        'depths': dict({'gridW': 7, 'gridH': 7, 'cell': 24, 'occupancy': 0.75, 'loops': 0.35}, **({'palette': PALETTE[tid]} if tid in PALETTE else {})),
        'features': {'chests': 16, 'outbreakChests': 5, 'timerChests': 2, 'strongboxes': 3, 'altars': 5, 'timerStage': 2, 'outbreakChestStagesPerUpgrade': 2,
                     'curseSeconds': 180, 'strongboxWave': 6, 'models': False},
        'spells': [{'id': s[0], 'name': s[1], 'effect': s[2], 'damage': s[3], 'radius': s[4], 'warnSeconds': s[5], 'cooldownSeconds': s[6], 'range': s[7], 'strength': s[8],
                    'colour': COLOUR[tid]} for s in spells],
    }

def main():
    root = sys.argv[1] if len(sys.argv) > 1 else 'src/main/resources/data/ascensiondungeons/dungeons'
    done, skipped = [], []
    for d in sorted(os.listdir(root)):
        path = os.path.join(root, d, 'layout.json')
        if not os.path.exists(path): continue
        if d not in TYPES: skipped.append(d); continue
        with open(path) as f: layout = json.load(f)
        with open(os.path.join(root, d, 'outbreak.json'), 'w') as f:
            json.dump(build(d, layout), f, indent=2); f.write('\n')
        done.append(d)
    print('wrote outbreak.json for %d type dungeons: %s' % (len(done), ', '.join(done)))
    print('left alone (not named after a type): %s' % (', '.join(skipped) or 'none'))
    print('types with no dungeon: %s' % (', '.join(t for t in TYPES if t not in done) or 'none'))

if __name__ == '__main__':
    main()
