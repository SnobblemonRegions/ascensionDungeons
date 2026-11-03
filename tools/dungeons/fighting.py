"""The Grandmaster's Dojo: the Fighting dungeon (id `fighting`).

Rooms: Training Grove -> Sparring Gallery -> Ascetic Steps -> Tournament Court -> Champion Vault; arena Grandmaster Circle.
Layout : the second half is on a mountain: the Ascetic Steps climb 16 blocks and the Tournament Court, Vault and Circle all sit at floor level 24.
Hazard : Balance Route (a one-block beam across practice pillars over a pit; swinging logs sweep you off it) and the Ascetic Steps themselves (rolling logs shove you
         back down the stair after a telegraph). Falls are short; ladders and the stair lead back.
Puzzle : the lever at the far end of the Balance Route opens the way to the steps; the Sparring Trial yields a Champion's Token, offered at the court's Discipline
         Offering to open the Champion Vault; its Grandmaster Key opens the arena. The Challenge Board's three belt pads (in the order of the wall strip) open the
         Retired Master's Cache; the same lever also opens the Hidden Dojo Scroll niche on the steps.
Boss   : the Grandmaster (piglin brute) behind three gold wards; shockwaves roll across the circle while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever
import numpy as np

W, H, L = 300, 60, 140
FLOOR = 7
F = 24                         # floor feet level of the mountain-top rooms
SMS, STN, AND, PAN, BAMB = b('smooth_stone'), b('stone'), b('andesite'), b('polished_andesite'), b('bamboo_planks')
SLOG, DLOG, RED, WHT, GOLD = b('spruce_log', axis='y'), b('dark_oak_log', axis='y'), b('red_concrete'), b('white_concrete'), b('gold_block')
GLOW, CHN, GRASS, PODZ = b('shroomlight'), b('chain', axis='y', waterlogged=False), b('grass_block', snowy=False), b('podzol')
BELTS = ['white', 'yellow', 'black']
COURT, ARENA = (214, 32 + 0), (272, 32)


def build():
    d = kit.Dungeon('fighting', "The Grandmaster's Dojo", W, H, L, biome='minecraft:bamboo_jungle')
    d.loot_table = 'ascensiondungeons:chests/ancient_foundry'
    d.shell = ('smooth_stone', 'stone', 'andesite', 'polished_andesite', 'bamboo_planks', 'spruce_log')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, SMS)
    C.key(d, 'champion_token', 'minecraft:gold_nugget', "Champion's Token", 'Stamped with a fist. The Discipline Offering in the Tournament Court wants it.')
    C.key(d, 'grandmaster_key', 'minecraft:tripwire_hook', 'Grandmaster Key', 'Opens the dojo door to the Grandmaster Circle.')
    grove(d); sparring(d); steps(d); court(d); vault(d); circle(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R4', False), ('balance', 'R3', True), ('balance', 'R4', True), ('balance', 'R5', False), ('offering', 'R5', True),
                     ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, SMS, [(STN, 0.18), (AND, 0.14), (PAN, 0.08), (b('cracked_stone_bricks'), 0.04)])
    return d


# ================================================================ R1 Training Grove (cave around 24,30)
def grove(d):
    d.ellipsoid(24, 16, 30, 22, 15, 19, kit.AIR)
    d.fill(2, 0, 10, 48, FLOOR, 52, SMS)
    for x in range(3, 48):
        for z in range(11, 52):
            if d.get(x, 8, z) == kit.AIR: d.set(x, FLOOR, z, d.rng.choice([GRASS, PODZ, GRASS, PAN]))
    d.entry = (7, 8, 30)
    for (x, z) in [(10, 14), (14, 16), (38, 14), (40, 18), (12, 46), (40, 46), (44, 38)]:
        if d.get(x, FLOOR, z) in (GRASS, PODZ): d.fill(x, 8, z, x, 8 + d.rng.randint(5, 9), z, b('bamboo'))
    # Monumental Training Bell (focal): a bronze bell slung between two great posts
    bx, bz = 28, 28
    d.fill(bx - 5, 8, bz - 1, bx - 5, 17, bz + 1, DLOG); d.fill(bx + 5, 8, bz - 1, bx + 5, 17, bz + 1, DLOG)
    d.fill(bx - 5, 18, bz - 1, bx + 5, 18, bz + 1, DLOG)
    d.fill(bx, 15, bz, bx, 17, bz, CHN)
    d.disc(bx, bz, 2, 11, 14, GOLD); d.air(bx, 11, bz, bx, 12, bz); d.disc(bx, bz, 1, 15, 15, GOLD); d.set(bx, 10, bz, b('iron_block'))
    d.fill(bx - 7, 8, bz, bx - 6, 8, bz, PAN); d.fill(bx + 6, 8, bz, bx + 7, 8, bz, PAN)
    d.fill(bx - 1, 19, bz - 1, bx + 1, 19, bz + 1, SLOG) if False else None
    d.landmark('monumental_training_bell', 'Monumental Training Bell', 'focal', (bx - 6, 8, bz - 2), (bx + 6, 19, bz + 2))
    # Endurance Shrine (interactive): a stone shrine among weights; standing here toughens you
    sx, sz = 38, 40
    d.fill(sx - 3, 8, sz - 2, sx + 3, 8, sz + 2, PAN); d.fill(sx - 2, 9, sz - 1, sx + 2, 9, sz + 1, STN)
    d.fill(sx - 1, 10, sz, sx + 1, 12, sz, SMS); d.set(sx, 13, sz, GLOW)
    for (x, z) in ((sx - 3, sz - 2), (sx + 3, sz - 2), (sx - 3, sz + 2), (sx + 3, sz + 2)): d.fill(x, 9, z, x, 10, z, b('iron_block'))
    d.landmark('endurance_shrine', 'Endurance Shrine', 'interactive', (sx - 3, 8, sz - 2), (sx + 3, 13, sz + 2))
    C.zone(d, 'endurance_strength', C.bx((sx - 6, 8, sz - 5), (sx + 6, 14, sz + 5)), 'minecraft:strength', 0, 600, 100)
    C.zone(d, 'endurance_hide', C.bx((sx - 6, 8, sz - 5), (sx + 6, 14, sz + 5)), 'minecraft:resistance', 0, 300, 100)
    # Meditation Alcove (discovery): a quiet nook in the north wall
    mx = 18
    S.nook(d, mx, 8, 12, 'n', depth=4, w=5, h=4)
    d.fill(mx - 2, 7, 8, mx + 2, 7, 11, b('bamboo_planks'))
    d.set(mx - 1, 8, 9, b('candle', candles=3, lit=True, waterlogged=False)); d.set(mx + 1, 8, 9, b('candle', candles=2, lit=True, waterlogged=False))
    d.fill(mx - 1, 8, 10, mx + 1, 8, 10, b('white_carpet'))
    d.add_chest((mx, 8, 8), 'south', 'loot')
    d.landmark('meditation_alcove', 'Meditation Alcove', 'discovery', (mx - 2, 7, 8), (mx + 2, 11, 12))
    d.air(44, 8, 29, 62, 11, 31)
    d.room('R1', 'Training Grove', (2, 8, 8), (50, 28, 52))


# ================================================================ R2 Sparring Gallery (x62..122, z14..50); pit with a beam in the south half
def sparring(d):
    d.air(62, 8, 14, 122, 22, 50)
    S.floor_pattern(d, 62, 14, 122, 33, FLOOR, [BAMB, b('stripped_bamboo_block', axis='y')], 3)
    for x in range(66, 122, 6):
        d.fill(x, 8, 14, x, 21, 14, SLOG); d.fill(x, 8, 50, x, 21, 50, SLOG)
    for x in range(68, 120, 10): d.set(x, 22, 24, GLOW); d.set(x, 22, 42, GLOW)
    # Tournament Banner Hall (focal): the north half is hung with long banners of every school
    cols = ['red', 'white', 'black', 'yellow', 'blue', 'orange']
    for k, x in enumerate(range(66, 120, 4)):
        c = cols[k % 6]
        d.fill(x, 14, 15, x, 20, 15, b(c + '_wool')); d.set(x, 21, 15, b('dark_oak_fence'))
    d.landmark('tournament_banner_hall', 'Tournament Banner Hall', 'focal', (64, 8, 14), (120, 21, 16))
    # Sparring Trial (interactive): a ring of rope round a plate in the north half
    px, pz = 82, 24
    d.disc(px, pz, 6, FLOOR, FLOOR, WHT); d.disc(px, pz, 5, FLOOR, FLOOR, BAMB); d.disc(px, pz, 1, FLOOR, FLOOR, RED)
    d.set(px, 8, pz, b('stone_pressure_plate', powered=False))
    for (x, z) in S.pts_ring(px, pz, 6, 8, 0.4): d.fill(x, 8, z, x, 9, z, SLOG); d.set(x, 10, z, b('lantern', hanging=False, waterlogged=False)) if False else None
    d.landmark('sparring_trial', 'Sparring Trial', 'interactive', (px - 7, 7, pz - 7), (px + 7, 10, pz + 7))
    C.trial(d, 'sparring_trial', (px, 8, pz), 'R2',
            [{'say': 'The ring is yours. Three students step in to test you.', 'spawns': [C.mob('minecraft:zombie', 3, 'Dojo Student', equipment={'mainhand': 'minecraft:stick'})]},
             {'say': 'Their seniors follow.', 'spawns': [C.mob('minecraft:zombie', 3, 'Dojo Senior', equipment={'mainhand': 'minecraft:iron_sword'}), C.mob('minecraft:skeleton', 2, 'Disciple Archer')]}],
            [C.say("The bout is yours. A Champion's Token lies on the mat.", 'gold'), C.give_key('champion_token', (px, 8, pz))])
    # Training Equipment Locker (discovery): a rack of wooden weapons in a bay in the north-east
    lx = 108
    S.nook(d, lx, 8, 14, 'n', depth=4, w=5, h=4)
    d.fill(lx - 2, 8, 10, lx + 2, 9, 10, b('oak_planks')); d.set(lx - 2, 10, 10, b('oak_fence')); d.set(lx + 2, 10, 10, b('oak_fence'))
    d.add_chest((lx, 8, 11), 'south', 'loot'); d.set(lx - 2, 8, 13, b('barrel', facing='south', open=False)); d.set(lx + 2, 8, 13, b('barrel', facing='south', open=False))
    d.landmark('training_equipment_locker', 'Training Equipment Locker', 'discovery', (lx - 2, 8, 10), (lx + 2, 11, 14))
    # the pit (z34..49) with its practice pillars and the one-block beam along z42
    d.air(62, 2, 34, 121, 22, 49)
    d.fill(62, 1, 34, 121, 1, 49, b('stone'))
    S.rubble(d, 64, 36, 120, 48, 2, [STN, AND], 30)
    for x in range(64, 121, 4): d.fill(x, 7, 42, x, 7, 42, SMS)                                # beam, one block wide
    for x in range(64, 121):
        d.set(x, 7, 42, SMS)
    # Stone Practice Pillars (focal): columns of every height standing in the pit, the beam passing between them
    for k, x in enumerate(range(68, 118, 6)):
        for z in (37, 47):
            h = 3 + (k * 3 + z) % 6
            d.fill(x, 2, z, x + 1, 6 + h, z + 1, STN if k % 2 else AND)
    for x in range(70, 118, 12): d.fill(x, 7, 41, x + 1, 7, 43, SMS)                           # little standing platforms on the beam
    d.landmark('stone_practice_pillars', 'Stone Practice Pillars', 'focal', (64, 2, 36), (121, 20, 48))
    for x in (80, 100): d.fill(x, 2, 34, x, 6, 49, b('iron_bars'))                              # no walking the pit floor past the beam
    for x in (66, 90, 112):
        for y in range(2, 8): d.set(x, y, 34, kit.ladder('south'))
    # the beam leads to a platform and a lever at the east end
    d.fill(116, 7, 40, 121, 7, 44, SMS)
    d.fill(116, 8, 39, 120, 11, 39, STN)
    C.wall_lever(d, 118, 9, 40, 'n')
    d.landmark('balance_route_challenge', 'Balance Route Challenge', 'interactive', (116, 7, 39), (121, 11, 44))
    d.air(122, 8, 41, 130, 11, 43)
    d.seal('dojo_gate', 'minecraft:stone', (124, 8, 41), (125, 11, 43))
    C.lever_seq(d, 'balance', [(118, 9, 40)], [C.clear(C.bx((124, 8, 41), (125, 11, 43))), C.clear(C.bx((144, 13, 35), (146, 16, 35))),
                                                C.sound((124, 9, 42), 'minecraft:block.stone.break', 2, 0.5),
                                                C.say('The dojo gate rolls aside and a stair climbs the mountain beyond.', 'gold')])
    C.pulse(d, 'swinging_logs', cells=[[76, 8, 42], [90, 8, 42], [104, 8, 42]], period=72, warn=30, radius=2.0, damage=1.5, kind='fall', height=2.5, stagger=24,
            push=[0.0, 0.3, 0.95], warnParticle='minecraft:crit', particle='minecraft:cloud', count=10, sound='minecraft:entity.player.attack.sweep', vol=1.0, pitch=0.7)
    C.encounter(d, 'R2', 'Students bow. Then they do not stop bowing. The pit beyond is the real test.',
                [C.mob('minecraft:zombie', 3, 'Dojo Student', equipment={'mainhand': 'minecraft:stick'}), C.mob('minecraft:skeleton', 3, 'Disciple Archer')])
    d.room('R2', 'Sparring Gallery', (62, 8, 14), (123, 22, 50))


# ================================================================ R3 Ascetic Steps (x126..178, stair climbs from feet 8 to 24)
def steps(d):
    d.air(126, 8, 36, 180, 40, 46)
    for i in range(0, 17):
        x0 = 128 + 3 * i
        top = 7 + i
        d.fill(x0, 1, 36, x0 + 2, top, 46, STN if i % 2 else AND)
        d.air(x0, top + 1, 36, x0 + 2, 40, 46)
    d.air(176, 24, 29, 192, 27, 46)
    # Ascetic Gate (focal): a torii at the foot of the steps
    gx = 126
    d.fill(gx, 8, 37, gx, 16, 37, RED); d.fill(gx, 8, 45, gx, 16, 45, RED)
    d.fill(gx, 15, 36, gx, 15, 46, RED); d.fill(gx, 17, 35, gx, 17, 47, b('black_concrete'))
    d.set(gx, 14, 41, GLOW)
    d.landmark('ascetic_gate', 'Ascetic Gate', 'focal', (gx - 1, 8, 35), (gx + 1, 18, 47))
    for x in range(130, 178, 6): d.set(x, 30, 35, GLOW)
    # Hidden Dojo Scroll (discovery): a shrine niche north of the fifth landing, opened by the Balance Route lever
    d.air(143, 13, 29, 149, 16, 34)
    d.air(144, 13, 35, 146, 16, 36)
    d.fill(143, 12, 29, 149, 12, 34, b('bamboo_planks')); d.fill(144, 12, 35, 146, 12, 36, b('bamboo_planks'))
    d.add_chest((146, 13, 29), 'south', 'loot'); d.set(144, 13, 31, b('candle', candles=3, lit=True, waterlogged=False)); d.set(148, 13, 31, b('candle', candles=2, lit=True, waterlogged=False))
    d.set(146, 13, 33, b('lectern', facing='south', powered=False))
    d.seal('scroll_wall', 'minecraft:stone', (144, 13, 35), (146, 16, 35))
    d.landmark('hidden_dojo_scroll', 'Hidden Dojo Scroll', 'discovery', (143, 12, 29), (149, 16, 36))
    cells = [[134, 8 + 2, 41], [143, 8 + 5, 41], [152, 8 + 8, 41], [161, 8 + 11, 41], [170, 8 + 14, 41]]
    C.pulse(d, 'rolling_logs', cells=cells, period=90, warn=34, radius=2.4, damage=2.0, kind='fall', height=3.0, stagger=29,
            push=[-1.25, 0.35, 0.0], warnParticle='minecraft:crit', particle='minecraft:cloud', count=12, sound='minecraft:entity.player.attack.knockback', vol=1.0, pitch=0.7)
    d.room('R3', 'Ascetic Steps', (126, 8, 35), (178, 41, 47))


# ================================================================ R4 Tournament Court (elevated, centre 214,32 r22, floor feet 24)
def court(d):
    cx, cz = COURT
    d.disc(cx, cz, 22, F, 46, kit.AIR)
    d.disc(cx, cz, 22, F - 1, F - 1, SMS); d.disc(cx, cz, 18, F - 1, F - 1, BAMB, rim=14); d.disc(cx, cz, 10, F - 1, F - 1, WHT, rim=8)
    d.air(176, 24, 29, 192, 27, 35)
    for (x, z) in S.pts_ring(cx, cz, 21, 16, 0.2): d.fill(x, F, z, x, F + 18, z, SLOG); d.set(x, F + 10, z, GLOW)
    # Spectator Balcony (ring ledge at feet 32) with stairs from the floor on the west
    xs, zs = np.arange(W)[None, :], np.arange(L)[:, None]
    r2 = (xs - cx) ** 2 + (zs - cz) ** 2
    ring = (r2 >= 19 * 19) & (r2 <= 22 * 22 + 0.5)
    for y in range(F, 32): d.a[y][ring] = d.pid(SMS)
    for k in range(1, 9):
        z = cz - 7 + k
        d.fill(cx - 19, F, z, cx - 18, F - 1 + k, z, STN) if False else d.fill(cx - 20, F, z, cx - 17, F - 1 + k, z, STN)
    d.fill(cx - 20, F, cz + 2, cx - 17, 31, cz + 2, STN)
    d.air(cx - 22, 32, cz - 1, cx - 17, 36, cz + 5)
    d.air(cx - 23, F, 29, cx - 19, F + 3, 46); d.air(cx + 19, F, 31, cx + 23, F + 3, 33); d.air(cx - 1, F, cz + 19, cx + 1, F + 3, cz + 23); d.air(cx - 1, F, 9, cx + 1, F + 3, 14)
    # Spectator Balcony Stash (discovery) on the ring
    d.add_chest((cx - 19, 32, cz + 8), 'east', 'loot'); d.set(cx - 19, 32, cz + 6, b('barrel', facing='up', open=False)); d.set(cx - 19, 32, cz + 10, b('lantern', hanging=False, waterlogged=False))
    d.landmark('spectator_balcony_stash', 'Spectator Balcony Stash', 'discovery', (cx - 21, F, cz - 6), (cx - 16, 34, cz + 11))
    # Champion Statue (focal): a victorious fighter, fist raised, on a stepped plinth
    d.disc(cx, cz, 6, F, F, SMS); d.disc(cx, cz, 4, F + 1, F + 1, PAN)
    S.statue(d, cx, cz, F + 1, PAN, SMS, eye=GOLD, face='s')
    d.fill(cx + 2, F + 7, cz, cx + 3, F + 7, cz, PAN); d.fill(cx + 3, F + 8, cz, cx + 3, F + 11, cz, PAN)
    d.landmark('champion_statue', 'Champion Statue', 'focal', (cx - 6, F, cz - 6), (cx + 6, F + 14, cz + 6))
    # Champion's Challenge Board (interactive): three belt pads; the order is on the wall strip above them
    order = [1, 0, 2]                                                   # yellow, white, black
    btn = []
    for k, c in enumerate([0, 1, 2]):
        x = cx - 8 + k * 8
        d.fill(x - 1, F - 1, cz - 18, x + 1, F - 1, cz - 16, b(BELTS[c] + '_concrete'))
        d.set(x, F, cz - 17, b('stone_button', face='floor', facing='north', powered=False))
        btn.append((x, F, cz - 17))
    d.fill(cx - 10, F + 3, cz - 20, cx + 10, F + 7, cz - 20, SMS)
    for k, c in enumerate(order): d.fill(cx - 6 + k * 6, F + 4, cz - 20, cx - 4 + k * 6, F + 6, cz - 20, b(BELTS[c] + '_concrete'))
    d.landmark('champion_s_challenge_board', "Champion's Challenge Board", 'interactive', (cx - 10, F - 1, cz - 21), (cx + 10, F + 7, cz - 15))
    # Retired Master's Cache (discovery): a private room behind the board, opened by the three pads
    d.air(cx - 3, F, cz - 28, cx + 3, F + 3, cz - 24) if False else None
    d.air(cx - 3, F, 6, cx + 3, F + 3, 8)
    d.air(cx - 1, F, 9, cx + 1, F + 3, 12)
    d.fill(cx - 3, F - 1, 6, cx + 3, F - 1, 8, BAMB); d.fill(cx - 1, F - 1, 9, cx + 1, F - 1, 12, BAMB)
    d.add_chest((cx, F, 6), 'south', 'loot'); d.set(cx - 2, F, 7, b('candle', candles=3, lit=True, waterlogged=False)); d.set(cx + 2, F, 7, b('candle', candles=3, lit=True, waterlogged=False))
    d.fill(cx - 3, F, 8, cx - 3, F + 2, 8, SLOG) if False else None
    d.seal('master_wall', 'minecraft:smooth_stone', (cx - 1, F, 9), (cx + 1, F + 3, 10))
    d.landmark('retired_master_s_cache', "Retired Master's Cache", 'discovery', (cx - 3, F - 1, 6), (cx + 3, F + 3, 12))
    C.sequence(d, 'belts', btn, order, [C.clear(C.bx((cx - 1, F, 9), (cx + 1, F + 3, 10))), C.sound((cx, F + 1, 9), 'minecraft:block.stone.break', 2, 0.6),
                                        C.say('The board nods. A door in the north wall slides back on a retired master\'s room.', 'gold')],
               'The belts are in rank order.', 'Not that order. Read the strip above the board: yellow, white, black.')
    # Discipline Offering (interactive) in the south; the Champion Vault door behind it
    ox, oz = cx, cz + 17
    d.fill(ox, F, oz, ox, F + 1, oz, PAN); d.set(ox, F + 2, oz, GOLD)
    for x in (ox - 2, ox + 2): d.fill(x, F, oz, x, F + 3, oz, SLOG); d.set(x, F + 4, oz, GLOW)
    d.landmark('discipline_offering', 'Discipline Offering', 'interactive', (ox - 2, F, oz - 1), (ox + 2, F + 4, oz + 1))
    d.air(cx - 1, F, 54, cx + 1, F + 3, 66)
    d.seal('vault_gate', 'minecraft:smooth_stone', (cx - 1, F, 56), (cx + 1, F + 3, 57))
    C.lock(d, 'offering', C.bx((ox, F + 2, oz)), 'champion_token', [C.clear(C.bx((cx - 1, F, 56), (cx + 1, F + 3, 57))), C.sound((cx, F + 1, 56), 'minecraft:block.stone.break', 2, 0.5),
                                                                  C.say('The token is placed. The vault gate in the south wall rolls open.', 'gold')],
           'A stone dish at the champion\'s feet, shaped to hold a token.', "lays the Champion's Token in the dish and the vault gate rolls open.")
    d.air(cx + 20, F, 31, cx + 38, F + 3, 33)
    d.seal('arena_door', 'minecraft:smooth_stone', (cx + 23, F, 31), (cx + 24, F + 3, 33))
    C.lock(d, 'arena_door', C.bx((cx + 23, F, 31), (cx + 24, F + 3, 33)), 'grandmaster_key', [C.clear(C.bx((cx + 23, F, 31), (cx + 24, F + 3, 33))),
                                                                                           C.sound((cx + 23, F + 1, 32), 'minecraft:block.bell.use', 2, 0.6), C.say('The dojo door to the Grandmaster Circle slides open.', 'gold')],
           'A dojo door, one panel inscribed with a closed fist and a keyhole.', 'turns the Grandmaster Key and the dojo door slides open.')
    C.encounter(d, 'R4', 'The court is empty and the stands are not. Three pads wait in the north; a dish at the champion\'s feet.',
                [C.mob('minecraft:zombie', 3, 'Dojo Senior'), C.mob('minecraft:skeleton', 3, 'Disciple Archer')])
    d.room('R4', 'Tournament Court', (180, F, 3), (236, 46, 54))


# ================================================================ R5 Champion Vault (elevated, x196..234, z68..100)
def vault(d):
    cx = COURT[0]
    d.air(196, F, 68, 232, F + 10, 100)
    d.air(cx - 1, F, 58, cx + 1, F + 3, 68)
    S.floor_pattern(d, 196, 68, 232, 100, F - 1, [PAN, BAMB], 2)
    for x in range(200, 230, 6):
        for z in (74, 90):
            d.fill(x, F, z, x + 1, F + 4, z + 1, SLOG); d.set(x, F + 5, z, GLOW)
            d.fill(x - 1, F, z + 2, x + 2, F, z + 2, b('oak_planks')); d.set(x, F + 1, z + 2, b('oak_fence'))
    d.add_chest((cx, F, 97), 'north', 'key', key='grandmaster_key')
    d.fill(cx - 2, F, 98, cx + 2, F + 2, 99, PAN); d.set(cx - 2, F, 97, b('lantern', hanging=False, waterlogged=False)); d.set(cx + 2, F, 97, b('lantern', hanging=False, waterlogged=False))
    d.room('R5', 'Champion Vault', (196, F, 58), (232, F + 10, 100))


# ================================================================ R6 Grandmaster Circle (elevated, centre 272,32 r22)
def circle(d):
    cx, cz = ARENA
    d.air(236, F, 31, 252, F + 3, 33)
    d.disc(cx, cz, 22, F, 50, kit.AIR)
    d.disc(cx, cz, 22, F - 1, F - 1, SMS); d.disc(cx, cz, 17, F - 1, F - 1, BAMB, rim=14); d.disc(cx, cz, 9, F - 1, F - 1, WHT)
    d.disc(cx, cz, 4, F - 1, F - 1, RED)
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 14, 8, math.pi / 8)):
        d.fill(x, F, z, x + 1, F + 14, z + 1, SLOG)
        d.fill(x - 1, F + 15, z - 1, x + 2, F + 15, z + 2, DLOG if False else RED)
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, F + 1, z, GOLD); targets.append(C.bx((tx, F + 1, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): d.fill(x, F, z, x, F + 10, z, SMS); d.set(x, F + 11, z, GLOW)
    C.boss(d, 'grandmaster', 'R6', 'minecraft:piglin_brute', 'The Grandmaster', (cx, F + 1, cz + 7), targets=targets,
           on_block='minecraft:gold_block', off_block='minecraft:stone', health=5.0, scale=1.5, color='RED',
           equipment={'mainhand': 'minecraft:golden_axe'},
           intro='The Grandmaster bows, once. Break the three gold seals on the pillars to take away its stance.',
           death='The Grandmaster bows again, lower, and does not rise.', break_text='The seals break! The Grandmaster is open!',
           adds=[C.mob('minecraft:zombie', 2, 'Dojo Senior') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('The Grandmaster calls the senior class.', 'red'), C.spawn('minecraft:zombie', 2, 'R6', 'Dojo Senior')]},
                   {'at': 0.33, 'effects': [C.say('The floor rings like a drum.', 'red'), C.spawn('minecraft:skeleton', 2, 'R6', 'Disciple Archer'), C.spawn('minecraft:zombie', 2, 'R6', 'Dojo Senior')]}])
    C.pulse(d, 'circle_shockwaves', box=C.bx((254, F, 10), (292, F + 20, 54)), period=58, warn=30, count=2, radius=2.6, damage=3.5, kind='fall', height=2.5,
            push=[0.5, 0.45, 0.5], warnParticle='minecraft:crit', particle='minecraft:cloud', burst=14, sound='minecraft:entity.player.attack.knockback', vol=1.0, pitch=0.6,
            after='boss_awake:grandmaster', until='boss:grandmaster', scatter=True, **{'target': 'players'})
    C.finish(d, 'grandmaster', "The Grandmaster's Dojo. Cross the balance route, climb the ascetic steps, and take a champion's token.", 'The dojo is silent. It closes shortly.')
    d.room('R6', 'Grandmaster Circle', (254, F, 10), (292, F + 26, 54))
