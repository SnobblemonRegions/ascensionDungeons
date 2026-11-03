"""Add the Cobblemon section to every authored dungeon's layout.json (water is hand-authored in water.py and skipped).
Usage: python cobblemonize.py [type ...]      Idempotent: re-running rewrites the same keys.
Each spawn spec gets "pokemon": <pool> (vanilla "entity" stays as the fallback); the boss runs real-time (pokemon "boss")
with a CobbleRaids pool as the alternative when authoredRealtimeBoss is off."""
import json, os, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
RES = os.path.join(ROOT, 'src/main/resources/data/ascensiondungeons/dungeons')

# type: (tier, wild_low, wild_mid, trial, boss, raid pool, boss level)
T = {
    'grass': (2, 'bulbasaur oddish bellsprout hoppip sunkern budew snivy', 'ivysaur gloom weepinbell skiploom sunflora roselia servine', 'torterra sceptile roserade leafeon ludicolo breloom venusaur', 'venusaur sceptile torterra roserade', 'venusaur sceptile torterra virizion', 55),
    'electric': (2, 'pikachu voltorb mareep shinx elekid pachirisu electrike', 'raichu electrode flaaffy luxio electabuzz emolga manectric', 'jolteon ampharos luxray electivire magnezone zebstrika raichu', 'raichu electivire luxray ampharos', 'zapdos raikou electivire luxray', 55),
    'ice': (3, 'snorunt swinub snover sneasel spheal vanillite cubchoo', 'glalie piloswine abomasnow sneasel vanillish beartic dewgong', 'weavile mamoswine froslass glaceon cloyster lapras walrein', 'glalie mamoswine weavile abomasnow', 'articuno regice glastrier kyurem', 58),
    'flying': (2, 'pidgey spearow starly taillow wingull swablu rufflet', 'pidgeotto fearow staravia swellow pelipper altaria braviary', 'pidgeot staraptor swanna honchkrow skarmory tropius togekiss', 'pidgeot staraptor braviary togekiss', 'articuno zapdos lugia tornadus', 55),
    'psychic': (3, 'abra drowzee ralts spoink meditite elgyem munna', 'kadabra hypno kirlia grumpig medicham beheeyem musharna', 'alakazam gardevoir gallade bronzong espeon xatu slowking', 'alakazam gardevoir gallade espeon', 'mewtwo latios latias cresselia', 58),
    'ghost': (3, 'gastly shuppet duskull misdreavus litwick yamask phantump', 'haunter banette dusclops mismagius lampent cofagrigus gourgeist', 'gengar chandelure dusknoir drifblim spiritomb trevenant sableye', 'gengar chandelure dusknoir mismagius', 'giratina spectrier darkrai gengar', 58),
    'dragon': (4, 'dratini bagon gible axew deino jangmoo fraxure', 'dragonair shelgon gabite fraxure zweilous hakamoo noibat', 'dragonite salamence garchomp haxorus hydreigon goodra kommoo', 'dragonite salamence garchomp haxorus', 'rayquaza dialga palkia garchomp', 62),
    'bug': (2, 'caterpie weedle pinsir scyther wurmple ledyba spinarak', 'metapod kakuna beautifly dustox ariados ledian pinsir', 'butterfree beedrill scizor heracross yanmega vespiquen volcarona', 'scizor heracross volcarona vespiquen', 'genesect volcarona heracross scizor', 52),
    'steel': (3, 'magnemite aron beldum klink bronzor shieldon pawniard', 'magneton lairon metang klang bronzong bastiodon bisharp', 'steelix aggron metagross magnezone excadrill lucario scizor', 'metagross aggron steelix excadrill', 'registeel metagross dialga genesect', 58),
    'fairy': (2, 'clefairy jigglypuff togepi snubbull cottonee spritzee flabebe', 'clefable wigglytuff togetic granbull whimsicott aromatisse floette', 'gardevoir togekiss sylveon azumarill florges mawile alcremie', 'togekiss sylveon gardevoir florges', 'xerneas tapulele cresselia sylveon', 55),
    'dark': (3, 'poochyena houndour sneasel murkrow purrloin zorua pawniard', 'mightyena houndoom sneasel honchkrow liepard zoroark bisharp', 'umbreon tyranitar weavile absol krookodile hydreigon mandibuzz', 'tyranitar umbreon hydreigon weavile', 'darkrai yveltal tyranitar hoopa', 58),
    'rock': (2, 'geodude onix rhyhorn larvitar nosepass roggenrola dwebble', 'graveler onix rhydon pupitar lunatone boldore crustle', 'golem tyranitar rampardos bastiodon probopass gigalith aerodactyl', 'tyranitar rampardos golem gigalith', 'regirock terrakion tyranitar diancie', 55),
    'ground': (2, 'sandshrew diglett geodude phanpy trapinch numel sandile', 'sandslash dugtrio graveler donphan vibrava camerupt krokorok', 'garchomp excadrill rhyperior flygon golurk steelix nidoking', 'garchomp excadrill rhyperior flygon', 'groudon landorus terrakion garchomp', 58),
    'poison': (2, 'ekans zubat grimer koffing gulpin stunky skorupi', 'arbok golbat muk weezing swalot skuntank drapion', 'crobat toxicroak nidoking nidoqueen roserade gliscor muk', 'nidoking toxicroak crobat drapion', 'nihilego naganadel nidoking toxicroak', 52),
    'fighting': (3, 'mankey machop tyrogue meditite timburr throh pancham', 'primeape machoke hitmontop medicham gurdurr sawk pangoro', 'machamp lucario conkeldurr hitmonlee hitmonchan blaziken infernape', 'machamp lucario conkeldurr blaziken', 'terrakion cobalion lucario machamp', 58),
    'normal': (1, 'rattata pidgey meowth lillipup bidoof zigzagoon eevee', 'raticate pidgeotto persian herdier bibarel linoone eevee', 'snorlax tauros kangaskhan ursaring lopunny dodrio audino', 'snorlax tauros kangaskhan ursaring', 'arceus snorlax tauros kangaskhan', 50),
}
POOL_FOR = {'waves': 'trial', 'relapseAdds': 'trial', 'phases': 'trial', 'encounters': 'wild_mid'}


def pool(names, lo, hi):
    ns = names.split()
    return [{'species': n, 'weight': 15 if i < 4 else 8, 'min': lo, 'max': hi} for i, n in enumerate(ns)]


def tag(o, path):
    if isinstance(o, dict):
        is_spec = 'entity' in o and ('count' in o or o.get('do') == 'spawn')
        if is_spec and 'pokemon' not in o:
            ctx = [k for k in POOL_FOR if k in path]
            o['pokemon'] = POOL_FOR[ctx[0]] if ctx else 'wild_low'
        for k, v in o.items(): tag(v, path + [k])
    elif isinstance(o, list):
        for v in o: tag(v, path)


def patch(tid):
    tier, low, mid, trial, boss, raid, lvl = T[tid]
    p = os.path.join(RES, tid, 'layout.json')
    d = json.load(open(p, encoding='utf-8'))
    d['pokemon'] = {'tier': tier, 'scaling': {'perPlayer': 2}, 'pools': {
        'wild_low': pool(low, 12, 20), 'wild_mid': pool(mid, 22, 30), 'trial': pool(trial, 26, 34),
        'boss': [{'species': n, 'weight': 10, 'min': lvl, 'max': lvl + 5} for n in boss.split()]}}
    tag({k: d[k] for k in ('mechanics', 'encounters', 'hazards')}, [])
    for b in d['bosses']:
        tag(b.get('shield', {}), ['relapseAdds'])
        tag(b.get('phases', []), ['phases'])
        b.update({'pokemon': 'boss', 'pokemonHealth': 300, 'damageMultiplier': 1.8, 'pokemonScale': 1.7,
                  'raid': {'pool': ['cobbleraids:' + n for n in raid.split()], 'level': lvl}})
    json.dump(d, open(p, 'w', encoding='utf-8'), indent=1)
    return json.dumps(d).count('"pokemon": "')


for tid in (sys.argv[1:] or T):
    print(tid, 'tagged specs:', patch(tid))
