"""The Hearthwarden Village: the Normal dungeon (id `normal`).

Rooms: Meadow Refuge -> Abandoned Market Gallery -> Millrace Walk -> Village Commons -> Homestead Cellar; arena Hearthwarden Hall.
Layout : an abandoned village: a meadow, a street of stalls and houses, a walk beside a contained millrace under a great stone waterwheel, a round commons
         ringed with cottages, a cellar under the biggest one, and a timber hall for the boss.
Hazard : Millrace Walk - the race is a closed channel (3 deep, level with the path); surges shove walkers into it after a telegraph. The swim is harmless but
         slow and the bank is a step up.
Puzzle : the Training Yard trial yields a Supply Token; the Supply Exchange stall takes it and opens the gate to the millrace. The Route Signpost Puzzle (four posts,
         pressed in the order the road runs: east, north, west, south) opens the Homestead Cellar; its Hearth Key opens the arena.
Extra  : the Orchard Harvest Shrine's three levers (matching the ripe/bare trees) open the Buried Pantry.
Boss   : the Hearthwarden (vindicator) behind three hearth-stone wards; embers fly in the hall while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever
import numpy as np

W, H, L = 300, 46, 140
FLOOR = 7
SB, MSB, CSB, COB, MCOB = b('stone_bricks'), b('mossy_stone_bricks'), b('cracked_stone_bricks'), b('cobblestone'), b('mossy_cobblestone')
OPL, SPL, OLOG, SLOG, DOAK = b('oak_planks'), b('spruce_planks'), b('oak_log', axis='y'), b('spruce_log', axis='y'), b('dark_oak_planks')
GRASS, DIRT, HAY, WATER = b('grass_block', snowy=False), b('dirt_path'), b('hay_block'), kit.water()
LEAF = b('oak_leaves', distance=7, persistent=True, waterlogged=False)
LANT, GLOW = b('lantern', hanging=False, waterlogged=False), b('shroomlight')
RT, DR = b('red_terracotta'), b('white_terracotta')
FL = ['poppy', 'dandelion', 'azure_bluet', 'cornflower', 'oxeye_daisy', 'short_grass', 'short_grass']
COURT, ARENA = (214, 32), (272, 32)


def build():
    d = kit.Dungeon('normal', 'The Hearthwarden Village', W, H, L, biome='minecraft:plains')
    d.loot_table = 'ascensiondungeons:chests/verdant_canopy'
    d.shell = ('stone_bricks', 'mossy_stone_bricks', 'cracked_stone_bricks', 'cobblestone', 'mossy_cobblestone', 'oak_planks', 'spruce_planks')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, COB)
    C.key(d, 'supply_token', 'minecraft:bread', 'Supply Token', 'A ration stamp. The Supply Exchange in the market takes it.')
    C.key(d, 'hearth_key', 'minecraft:tripwire_hook', 'Hearth Key', 'Opens the oak doors of the Hearthwarden Hall.')
    meadow(d); market(d); millrace(d); commons(d); cellar(d); hall(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R5', False), ('exchange', 'R3', True), ('exchange', 'R4', True), ('exchange', 'R5', False),
                     ('signposts', 'R5', True), ('signposts', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, COB, [(MCOB, 0.20), (b('stone'), 0.14), (b('andesite'), 0.06)])
    return d


def lawn(d, x1, z1, x2, z2, flora=0.18):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if d.get(x, 8, z) == kit.AIR:
                d.set(x, FLOOR, z, GRASS if d.rng.random() < 0.82 else DIRT if False else b('coarse_dirt'))
                if d.get(x, FLOOR, z) == GRASS and d.rng.random() < flora: d.set(x, 8, z, b(d.rng.choice(FL)))


# ================================================================ R1 Meadow Refuge (cave around 24,30)
def meadow(d):
    d.ellipsoid(24, 15, 30, 22, 14, 19, kit.AIR)
    d.fill(2, 0, 10, 48, FLOOR, 52, COB)
    lawn(d, 3, 11, 47, 51)
    d.entry = (7, 8, 30)
    # Broken Windmill (focal): a stone tower with its sails snapped
    wx, wz = 30, 20
    S.tower(d, wx, wz, 4, 8, 14, MSB, floor=COB, door='s', glow=None)
    d.air(wx - 2, 17, wz - 4, wx + 2, 21, wz - 2); d.air(wx + 2, 15, wz + 2, wx + 4, 21, wz + 4)
    S.rubble(d, wx - 6, wz - 6, wx + 6, wz + 6, 8, [MSB, CSB, COB, OPL], 16)
    d.fill(wx, 22, wz, wx, 23, wz, b('oak_fence')); d.set(wx, 22, wz, OLOG)
    for k, (dx, dy) in enumerate(((3, 3), (-4, -1), (2, -4), (-2, 4))):
        d.line((wx, 20, wz - 5), (wx + dx * 2, 20 + dy * 2, wz - 5), SPL) if abs(dx) + abs(dy) > 3 else None
    d.fill(wx - 6, 20, wz - 5, wx + 1, 20, wz - 5, SPL); d.fill(wx, 14, wz - 5, wx, 20, wz - 5, SPL)
    d.landmark('broken_windmill', 'Broken Windmill', 'focal', (wx - 7, 8, wz - 7), (wx + 7, 24, wz + 7))
    # Abandoned Nest (discovery): a big woven nest in the tall grass, bird gone
    nx, nz = 38, 42
    d.disc(nx, nz, 3, 8, 8, HAY); d.disc(nx, nz, 2, 8, 8, b('oak_leaves', distance=1, persistent=True, waterlogged=False)) if False else d.disc(nx, nz, 2, 9, 9, HAY)
    d.set(nx, 9, nz, b('white_wool')); d.fill(nx - 3, 9, nz - 3, nx - 3, 10, nz - 3, b('oak_fence'))
    d.add_chest((nx + 1, 8, nz + 1), 'north', 'loot')
    d.landmark('abandoned_nest', 'Abandoned Nest', 'discovery', (nx - 4, 8, nz - 4), (nx + 4, 10, nz + 4))
    # Traveling Scholar's Camp (discovery)
    cx, cz = 12, 16
    S.pyramid(d, cx, cz, 8, 3, [b('brown_wool'), b('white_wool')]); d.air(cx - 1, 8, cz - 1, cx + 1, 9, cz + 1)
    d.set(cx + 4, 8, cz, b('lectern', facing='west', powered=False)); d.set(cx + 4, 8, cz + 2, b('cartography_table')); d.add_chest((cx, 8, cz), 'east', 'loot')
    d.set(cx + 3, 8, cz - 2, b('campfire', facing='north', lit=False, signal_fire=False, waterlogged=False)); d.set(cx - 2, 8, cz + 2, b('barrel', facing='up', open=False))
    d.landmark('traveling_scholar_s_camp', "Traveling Scholar's Camp", 'discovery', (cx - 3, 8, cz - 3), (cx + 5, 11, cz + 3))
    # Orchard Harvest Shrine (interactive): three levers matching ripe (apple leaves) / bare trees; opens the Buried Pantry in the commons
    ox, oz = 40, 36
    pats = [True, False, True]
    lv = []
    for k, x in enumerate((ox - 4, ox, ox + 4)):
        d.fill(x, 8, oz + 2, x, 12, oz + 2, SLOG) if False else None
        d.fill(x - 1, 8, oz + 3, x + 1, 12, oz + 3, MSB)
        C.wall_lever(d, x, 8, oz + 2, 's')
        d.fill(x, 9, oz + 2, x, 9, oz + 2, kit.AIR)
        d.set(x, 10, oz + 2, b('oak_leaves', distance=1, persistent=True, waterlogged=False) if pats[k] else b('stripped_oak_log', axis='y'))
        lv.append(((x, 8, oz + 2), pats[k]))
    d.landmark('orchard_harvest_shrine', 'Orchard Harvest Shrine', 'interactive', (ox - 5, 8, oz + 1), (ox + 5, 12, oz + 3))
    C.toggle(d, 'harvest', lv, [C.clear(C.bx((COURT[0] - 1, 8, 5), (COURT[0] + 1, 11, 6))), C.sound((ox, 9, oz + 2), 'minecraft:block.bell.use', 1.5, 1.2),
                                C.say('A bell rings once. Somewhere, a pantry hatch lifts.', 'gold')],
             'The orchard is in harvest.')
    # Rest Station zone: in the refuge itself, a bench under a lantern
    d.fill(14, 8, 30, 18, 8, 30, OPL); d.set(14, 9, 30, b('oak_fence')); d.set(18, 9, 30, b('oak_fence')); d.set(16, 9, 30, LANT)
    d.air(44, 8, 29, 62, 11, 31)
    d.room('R1', 'Meadow Refuge', (2, 8, 10), (50, 28, 52))


# ================================================================ R2 Abandoned Market Gallery (x62..120, z16..46)
def market(d):
    d.air(62, 8, 16, 120, 22, 46)
    S.floor_pattern(d, 62, 16, 120, 46, FLOOR, [COB, MCOB, SB], 3)
    for x in range(66, 120, 6):
        d.fill(x, 8, 16, x, 21, 16, SLOG); d.fill(x, 8, 46, x, 21, 46, SLOG)
    for x in range(68, 118, 10): d.set(x, 22, 31, GLOW)
    # Overgrown Market Pavilion (focal): a roofed stall hall, vines and moss over its beams
    px, pz = 90, 22
    for (x, z) in ((px - 7, pz - 4), (px + 7, pz - 4), (px - 7, pz + 4), (px + 7, pz + 4)): d.fill(x, 8, z, x, 15, z, OLOG)
    d.fill(px - 8, 16, pz - 5, px + 8, 16, pz + 5, SPL); d.fill(px - 8, 17, pz - 5, px + 8, 17, pz + 5, b('spruce_slab', type='bottom', waterlogged=False)) if False else d.fill(px - 7, 17, pz - 4, px + 7, 17, pz + 4, SPL)
    for k in range(10): d.set(px - 7 + k * 2 // 1 % 14, 15, pz - 5, LEAF) if False else d.set(px - 7 + (k * 3) % 15, 15, pz + 5 - (k % 3) * 10, LEAF)
    for x in range(px - 6, px + 7, 3):
        d.fill(x, 8, pz - 2, x + 1, 9, pz - 2, OPL); d.set(x, 10, pz - 2, b('red_wool'))
        d.set(x + 1, 10, pz - 2, b('white_wool'))
    d.landmark('overgrown_market_pavilion', 'Overgrown Market Pavilion', 'focal', (px - 8, 8, pz - 5), (px + 8, 17, pz + 5))
    # Attic Cache (discovery): a two-storey house with the chest in the loft, reached by a ladder
    hx, hz = 70, 36
    S.hut(d, hx, hz, hx + 6, hz + 6, 8, 4, SPL, OPL, door='n', floor=OPL)
    d.fill(hx + 1, 12, hz + 1, hx + 5, 12, hz + 5, OPL); d.air(hx + 1, 12, hz + 1, hx + 1, 12, hz + 1)
    d.fill(hx, 13, hz, hx + 6, 15, hz + 6, SPL); d.air(hx + 1, 13, hz + 1, hx + 5, 15, hz + 5); d.fill(hx - 1, 16, hz - 1, hx + 7, 16, hz + 7, OPL)
    for y in range(8, 13): d.set(hx + 1, y, hz + 2, kit.ladder('east')) if False else d.set(hx + 1, y, hz + 1, kit.ladder('south'))
    d.add_chest((hx + 4, 13, hz + 4), 'north', 'loot'); d.set(hx + 5, 13, hz + 1, LANT)
    d.landmark('attic_cache', 'Attic Cache', 'discovery', (hx - 1, 8, hz - 1), (hx + 7, 16, hz + 7))
    # Training Yard (interactive): straw dummies round a plate at the west end
    yx, yz = 74, 24
    d.disc(yx, yz, 5, FLOOR, FLOOR, b('coarse_dirt')); d.disc(yx, yz, 1, FLOOR, FLOOR, b('stone'))
    d.set(yx, 8, yz, b('stone_pressure_plate', powered=False))
    for (x, z) in S.pts_ring(yx, yz, 4, 6, 0.3):
        d.fill(x, 8, z, x, 9, z, b('oak_fence')); d.set(x, 10, z, HAY); d.set(x, 11, z, b('carved_pumpkin', facing='south'))
    d.landmark('training_yard', 'Training Yard', 'interactive', (yx - 6, 7, yz - 6), (yx + 6, 11, yz + 6))
    C.trial(d, 'training_yard', (yx, 8, yz), 'R2',
            [{'say': 'The dummies start to move. They have wooden swords.', 'spawns': [C.mob('minecraft:zombie', 3, 'Straw Fighter', equipment={'mainhand': 'minecraft:wooden_sword'})]},
             {'say': 'The veterans take the yard.', 'spawns': [C.mob('minecraft:zombie', 3, 'Yard Veteran', equipment={'mainhand': 'minecraft:stone_sword'}), C.mob('minecraft:skeleton', 2, 'Yard Archer')]}],
            [C.say('The yard falls quiet. A Supply Token lies on the plate.', 'gold'), C.give_key('supply_token', (yx, 8, yz))])
    # Supply Exchange (interactive): a stall at the east end; the token buys passage past the bar
    ex, ez = 112, 30
    d.fill(ex, 8, ez - 4, ex + 1, 9, ez - 4, OPL); d.fill(ex, 8, ez + 4, ex + 1, 9, ez + 4, OPL)
    d.fill(ex - 1, 8, ez - 3, ex - 1, 8, ez + 3, OPL); d.fill(ex - 1, 9, ez - 3, ex - 1, 9, ez - 3, b('oak_fence')) if False else None
    d.set(ex - 1, 9, ez, b('barrel', facing='west', open=False)); d.set(ex - 1, 9, ez - 2, b('barrel', facing='west', open=False)); d.set(ex - 1, 9, ez + 2, b('barrel', facing='west', open=False))
    d.fill(ex - 1, 10, ez - 4, ex + 1, 10, ez + 4, b('red_wool')) if False else d.fill(ex - 1, 11, ez - 4, ex + 1, 11, ez + 4, b('red_wool'))
    d.landmark('supply_exchange', 'Supply Exchange', 'interactive', (ex - 2, 8, ez - 5), (ex + 2, 12, ez + 5))
    d.air(120, 8, 29, 130, 11, 33)
    d.seal('market_gate', 'minecraft:oak_planks', (122, 8, 29), (123, 11, 33))
    C.lock(d, 'exchange', C.bx((ex - 1, 9, ez)), 'supply_token', [C.clear(C.bx((122, 8, 29), (123, 11, 33))), C.sound((122, 9, 31), 'minecraft:block.wood.break', 2, 0.6),
                                                                 C.say('The stallholder\'s ghost nods and the bar to the millrace swings up.', 'gold')],
           'A stall counter with a slot for a ration token.', 'slides the Supply Token across the counter.')
    C.encounter(d, 'R2', 'Awnings flap in no wind. The market still thinks it is open.',
                [C.mob('minecraft:zombie', 3, 'Straw Fighter'), C.mob('minecraft:skeleton', 3, 'Yard Archer'), C.mob('minecraft:spider', 2, 'Cellar Crawler')])
    d.room('R2', 'Abandoned Market Gallery', (62, 8, 16), (121, 22, 46))


# ================================================================ R3 Millrace Walk (x124..190, z18..46): a walk beside a contained channel
def millrace(d):
    d.air(124, 8, 18, 190, 24, 46)
    d.air(120, 8, 29, 124, 11, 33)
    S.floor_pattern(d, 124, 18, 190, 27, FLOOR, [COB, MCOB, SB], 2)
    S.floor_pattern(d, 124, 35, 190, 46, FLOOR, [COB, MCOB], 2)
    # the channel: z28..34, water y5..7 (surface level with both banks), closed at both ends and floored
    d.fill(124, 4, 28, 190, 4, 34, SB)
    d.fill(124, 5, 28, 190, 7, 34, WATER)
    d.fill(124, 5, 28, 124, 7, 34, SB); d.fill(190, 5, 28, 190, 7, 34, SB)
    # footbridge across the race at x=140 and at x=176 (planks), so both banks are one walk
    for bx in (140, 176): d.fill(bx, 7, 28, bx + 2, 7, 34, OPL); d.fill(bx, 8, 28, bx, 8, 34, b('oak_fence')); d.fill(bx + 2, 8, 28, bx + 2, 8, 34, b('oak_fence'))
    for x in range(126, 190, 6): d.fill(x, 8, 18, x, 23, 18, SLOG); d.fill(x, 8, 46, x, 23, 46, SLOG); d.set(x, 22, 26, GLOW); d.set(x, 22, 36, GLOW)
    # Stone Waterwheel (focal): a great wheel dipping its paddles into the race
    wx, wy, wz = 158, 14, 30
    S.wheel(d, wx, wy, wz, 6, 'z', b('stone_bricks'), b('stripped_oak_log', axis='z'), b('iron_block'), thick=3)
    for k in range(12):
        a_ = k * math.pi / 6 + 0.2
        x, y = int(round(wx + 7 * math.cos(a_))), int(round(wy + 7 * math.sin(a_)))
        if y >= 9: d.fill(x, y, wz, x, y, wz + 2, OPL)
    d.fill(wx - 1, 8, 26, wx + 1, 12, 26, SB) if False else None
    d.fill(wx - 3, 8, 24, wx + 3, 8, 26, b('stone_bricks')); d.fill(wx - 3, 9, 24, wx - 3, 14, 24, SLOG); d.fill(wx + 3, 9, 24, wx + 3, 14, 24, SLOG)
    d.landmark('stone_waterwheel', 'Stone Waterwheel', 'focal', (wx - 8, 8, 24), (wx + 8, 21, wz + 3))
    # Rootbound Bell Tower (focal): a ruined bell tower with a tree growing through it, at the south-east
    tx, tz = 184, 40
    S.tower(d, tx, tz, 3, 8, 12, MSB, floor=COB, door='w', glow=None)
    d.fill(tx, 8, tz, tx, 18, tz, SLOG); d.ellipsoid(tx, 21, tz, 5, 3, 5, LEAF, only_air=True)
    d.fill(tx - 1, 16, tz, tx + 1, 17, tz, b('gold_block')) if False else d.set(tx, 15, tz + 1, b('bell', facing='north', attachment='floor') if False else b('gold_block'))
    for (x, z) in S.pts_ring(tx, tz, 4, 6): d.fill(x, 8, z, x, 9, z, SLOG)
    d.landmark('rootbound_bell_tower', 'Rootbound Bell Tower', 'focal', (tx - 5, 8, tz - 5), (tx + 5, 24, tz + 5))
    d.air(190, 8, 29, 196, 11, 33) if False else None
    d.air(190, 8, 26, 196, 11, 36)
    # surges that shove walkers toward the race (kept off the bridges)
    cells = [[132, 8, 26], [148, 8, 26], [166, 8, 26], [150, 8, 36], [168, 8, 36], [182, 8, 26]]
    C.pulse(d, 'race_surge', cells=cells, period=84, warn=34, radius=2.2, damage=0.5, kind='fall', height=2.0, stagger=23,
            push=[0.0, 0.25, 0.95], warnParticle='minecraft:splash', particle='minecraft:bubble', burst=16, sound='minecraft:entity.generic.splash', vol=1.0, pitch=0.7)
    d.room('R3', 'Millrace Walk', (124, 8, 18), (192, 24, 46))


# ================================================================ R4 Village Commons (centre 214,32 r22)
def commons(d):
    cx, cz = COURT
    d.air(190, 8, 26, 194, 11, 36)
    d.disc(cx, cz, 22, 8, 26, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, COB); d.disc(cx, cz, 20, FLOOR, FLOOR, MCOB, rim=17); d.disc(cx, cz, 14, FLOOR, FLOOR, SB, rim=8)
    lawn(d, cx - 21, cz - 21, cx + 21, cz + 21, 0.1) if False else None
    for (x, z) in S.pts_ring(cx, cz, 21, 16, 0.2): d.fill(x, 8, z, x, 20, z, SLOG); d.set(x, 15, z, GLOW)
    # cottages round the edge (decor, enterable)
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 17, 8, 0.4)):
        if k % 2 == 0: S.hut(d, x - 2, z - 2, x + 2, z + 2, 8, 4, SPL if k % 4 else DOAK, OPL, door='s' if z < cz else 'n', floor=OPL)
    # Great Hearth (focal): a fenced hearth with a chimney in the middle of the commons
    d.disc(cx, cz, 5, 8, 8, SB); d.disc(cx, cz, 4, 9, 9, b('bricks'))
    for dy in (10, 11): d.disc(cx, cz, 4, dy, dy, b('bricks'), rim=3)
    d.air(cx - 1, 10, cz - 4, cx + 1, 11, cz - 4)
    d.set(cx, 9, cz, b('campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
    d.fill(cx - 1, 12, cz - 1, cx + 1, 18, cz + 1, b('bricks')); d.fill(cx, 12, cz, cx, 18, cz, kit.AIR) if False else None
    d.set(cx, 19, cz, b('campfire', facing='north', lit=True, signal_fire=True, waterlogged=False)) if False else None
    d.landmark('great_hearth', 'Great Hearth', 'focal', (cx - 5, 8, cz - 5), (cx + 5, 19, cz + 5))
    # Route Signpost Puzzle (interactive): four signposts at the compass points; press east, north, west, south
    posts = {'N': (cx, cz - 11), 'E': (cx + 11, cz), 'S': (cx, cz + 11), 'W': (cx - 11, cz)}
    col = {'N': 'blue', 'E': 'red', 'S': 'green', 'W': 'yellow'}
    btn = []
    for key_ in ('N', 'E', 'S', 'W'):
        x, z = posts[key_]
        d.fill(x, 8, z, x, 10, z, SLOG); d.fill(x - 1, 11, z, x + 1, 11, z, OPL); d.set(x, 12, z, b(col[key_] + '_banner', rotation=0))
        d.set(x, 8, z + 1 if key_ != 'S' else z - 1, b('stone_button', face='floor', facing='north', powered=False))
        btn.append((x, 8, z + 1 if key_ != 'S' else z - 1))
    d.landmark('route_signpost_puzzle', 'Route Signpost Puzzle', 'interactive', (cx - 13, 8, cz - 13), (cx + 13, 12, cz + 13))
    C.sequence(d, 'signposts', btn, [1, 0, 3, 2], [C.clear(C.bx((cx - 1, 8, 56), (cx + 1, 11, 57))), C.sound((cx, 9, 56), 'minecraft:block.wood.break', 2, 0.6),
                                                  C.say('The posts all point home. A cellar hatch opens in the south of the commons.', 'gold')],
               'The road runs true.', 'Wrong turning. The road runs east, north, west, south.')
    # Buried Pantry (discovery): under the cobbles in the north, opened by the Orchard Harvest Shrine
    d.air(cx - 3, 8, 3, cx + 3, 11, 6)
    d.air(cx - 1, 8, 7, cx + 1, 11, 13)
    d.fill(cx - 3, 7, 3, cx + 3, 7, 6, OPL); d.fill(cx - 1, 7, 7, cx + 1, 7, 13, OPL)
    d.add_chest((cx, 8, 3), 'south', 'loot'); d.set(cx - 2, 8, 4, b('barrel', facing='east', open=False)); d.set(cx + 2, 8, 4, b('barrel', facing='west', open=False)); d.set(cx, 8, 5, b('hay_block'))
    d.seal('pantry_hatch', 'minecraft:oak_planks', (cx - 1, 8, 5), (cx + 1, 11, 6))
    d.landmark('buried_pantry', 'Buried Pantry', 'discovery', (cx - 3, 7, 3), (cx + 3, 11, 13))
    # Rest Station (interactive): a bench and a lit lamp post west of the hearth
    rx, rz = cx - 17, cz + 8
    d.fill(rx - 2, 8, rz, rx + 2, 8, rz, OPL); d.set(rx - 2, 9, rz, b('oak_fence')); d.set(rx + 2, 9, rz, b('oak_fence')); d.set(rx, 9, rz, LANT)
    d.fill(rx, 9, rz - 2, rx, 11, rz - 2, b('oak_fence')); d.set(rx, 12, rz - 2, LANT)
    d.landmark('rest_station', 'Rest Station', 'interactive', (rx - 3, 8, rz - 3), (rx + 3, 12, rz + 2))
    C.zone(d, 'rest_mending', C.bx((rx - 4, 8, rz - 4), (rx + 4, 12, rz + 3)), 'minecraft:regeneration', 0, 100, 40)
    C.zone(d, 'rest_fed', C.bx((rx - 4, 8, rz - 4), (rx + 4, 12, rz + 3)), 'minecraft:saturation', 0, 40, 80)
    d.air(cx - 1, 8, 54, cx + 1, 11, 66)
    d.seal('cellar_hatch', 'minecraft:oak_planks', (cx - 1, 8, 56), (cx + 1, 11, 57))
    d.air(cx + 20, 8, 31, cx + 38, 11, 33)
    d.seal('arena_door', 'minecraft:spruce_planks', (cx + 23, 8, 31), (cx + 24, 11, 33))
    C.lock(d, 'arena_door', C.bx((cx + 23, 8, 31), (cx + 24, 11, 33)), 'hearth_key', [C.clear(C.bx((cx + 23, 8, 31), (cx + 24, 11, 33))),
                                                                                       C.sound((cx + 23, 9, 32), 'minecraft:block.wood.break', 2, 0.5), C.say('The great oak doors of the Hearthwarden Hall open.', 'gold')],
           'Great oak doors, banded with iron and a big keyhole.', 'turns the Hearth Key and the great oak doors swing open.')
    C.encounter(d, 'R4', 'A fire burns in the commons with no one to tend it. Four signposts stand at the compass points: the road runs east, north, west, south.',
                [C.mob('minecraft:zombie', 3, 'Straw Fighter'), C.mob('minecraft:skeleton', 3, 'Yard Archer')])
    d.room('R4', 'Village Commons', (192, 8, 3), (238, 26, 54))


# ================================================================ R5 Homestead Cellar (x196..234, z68..100)
def cellar(d):
    cx = COURT[0]
    d.air(196, 8, 68, 232, 16, 100)
    d.air(cx - 1, 8, 58, cx + 1, 11, 68)
    S.floor_pattern(d, 196, 68, 232, 100, FLOOR, [COB, MCOB, SB], 2)
    for x in range(200, 230, 6):
        d.fill(x, 8, 68, x, 15, 68, SLOG)
        for z in (74, 90):
            d.fill(x, 8, z, x + 2, 9, z + 1, b('barrel', facing='up', open=False)) if False else d.fill(x, 8, z, x + 2, 8, z + 1, OPL)
            d.set(x, 9, z, b('barrel', facing='up', open=False)); d.set(x + 2, 9, z, b('barrel', facing='up', open=False)); d.set(x + 1, 9, z, HAY)
    for x in range(204, 228, 8): d.set(x, 15, 80, b('lantern', hanging=True, waterlogged=False)) if d.get(x, 16, 80) != kit.AIR else d.set(x, 15, 80, GLOW)
    # Cellar Strongbox (discovery) is the key plinth
    d.add_chest((cx, 8, 97), 'north', 'key', key='hearth_key')
    d.fill(cx - 2, 8, 98, cx + 2, 10, 99, SB); d.set(cx - 2, 8, 97, LANT); d.set(cx + 2, 8, 97, LANT)
    d.landmark('cellar_strongbox', 'Cellar Strongbox', 'discovery', (cx - 2, 8, 96), (cx + 2, 10, 99))
    d.room('R5', 'Homestead Cellar', (196, 8, 58), (232, 16, 100))


# ================================================================ R6 Hearthwarden Hall (timber hall, rect x252..292, z12..52)
def hall(d):
    cx, cz = ARENA
    d.air(236, 8, 31, 252, 11, 33)
    d.air(252, 8, 12, 292, 26, 52)
    S.floor_pattern(d, 252, 12, 292, 52, FLOOR, [OPL, SPL], 2)
    for x in range(256, 292, 6):
        d.fill(x, 8, 12, x, 25, 12, SLOG); d.fill(x, 8, 52, x, 25, 52, SLOG)
    for z in range(16, 52, 6): d.fill(252, 8, z, 252, 25, z, SLOG); d.fill(292, 8, z, 292, 25, z, SLOG)
    for x in range(256, 292, 8): d.set(x, 24, 32, GLOW)
    # the long hearth down the middle (fenced by stone) and tables either side
    d.fill(cx - 12, 8, cz - 2, cx + 12, 8, cz + 2, SB); d.fill(cx - 11, 9, cz - 1, cx + 11, 9, cz + 1, b('bricks'))
    for x in range(cx - 10, cx + 11, 4): d.set(x, 10, cz, b('campfire', facing='north', lit=True, signal_fire=False, waterlogged=False))
    for z in (cz - 7, cz + 7):
        d.fill(cx - 10, 8, z, cx + 10, 8, z, OPL)
        for x in range(cx - 9, cx + 10, 6): d.set(x, 9, z, b('barrel', facing='up', open=False))
    targets = []
    for k, (x, z) in enumerate([(cx - 14, cz - 12), (cx + 14, cz - 12), (cx - 14, cz + 12), (cx + 14, cz + 12), (cx, cz - 15), (cx, cz + 15)]):
        d.fill(x, 8, z, x + 1, 20, z + 1, SLOG)
        d.fill(x - 1, 21, z - 1, x + 2, 21, z + 2, SPL)
        if k in (0, 3, 4):
            tx = x - 1 if x > cx else x + 2
            tz_ = z
            if k == 4: tx, tz_ = x + 2, z
            d.set(tx, 9, tz_, GLOW); targets.append(C.bx((tx, 9, tz_)))
    C.boss(d, 'hearthwarden', 'R6', 'minecraft:vindicator', 'The Hearthwarden', (cx, 9, cz + 9), targets=targets,
           on_block='minecraft:shroomlight', off_block='minecraft:stone_bricks', health=6.0, scale=1.6, color='RED',
           equipment={'mainhand': 'minecraft:iron_axe'},
           intro='The Hearthwarden rises from the high seat. Douse the three hearth-stones on the posts to put out its fire.',
           death='The Hearthwarden sets down its axe, and the fire dies to embers.', break_text='The hearth-stones go cold! The Hearthwarden is exposed!',
           adds=[C.mob('minecraft:zombie', 2, 'Straw Fighter') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('The Hearthwarden stokes the fire.', 'red'), C.spawn('minecraft:zombie', 2, 'R6', 'Straw Fighter')]},
                   {'at': 0.33, 'effects': [C.say('Embers fly from the long hearth.', 'red'), C.spawn('minecraft:skeleton', 2, 'R6', 'Yard Archer'), C.spawn('minecraft:zombie', 2, 'R6', 'Yard Veteran')]}])
    C.pulse(d, 'hall_embers', box=C.bx((254, 8, 14), (290, 24, 50)), period=62, warn=28, count=2, radius=2.0, damage=2.5, kind='fire', fire=40, height=2.5,
            warnParticle='minecraft:smoke', particle='minecraft:flame', sound='minecraft:block.fire.ambient', vol=0.9, pitch=1.2,
            after='boss_awake:hearthwarden', until='boss:hearthwarden', scatter=True, **{'target': 'players'})
    C.finish(d, 'hearthwarden', 'The Hearthwarden Village. Win a ration token in the yard, and the market will let you through.', 'The fires burn low. The village closes shortly.')
    d.room('R6', 'Hearthwarden Hall', (254, 8, 12), (292, 26, 52))
