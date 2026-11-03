"""The Moonlit Promise: the Fairy dungeon (id `fairy`).

Rooms: Moonflower Grove -> Storybook Gallery -> Ribbonbridge Hollow -> Petal Court -> Wish Vault; arena Moonlit Promise Sanctum.
Hazard : Ribbonbridge Hollow - the way round the pit is a narrow ribbon ledge that frays away under you (the fall is short; ladders lead back up) and dazzle
         bursts that blind you for a few seconds where you stand, after a telegraph of sparkles.
Puzzle : the Story Sequence Pedestals (press the four in the order of the ribbon strip on the wall) open the ribbon gate and the Sealed Story Chest nook;
         the Moonlight Trial yields a Wish Star, offered at the court's Wish Fountain to open the Wish Vault; its Promise Key opens the arena.
Extra  : Petal Alignment (four levers matching the open/closed petal clues) opens the Starlit Alcove; the Tea Garden Station mends and shields.
Boss   : the Promise-Keeper (witch) behind three moonstone wards; dazzle bursts rain on the sanctum while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever
import numpy as np

W, H, L = 300, 46, 140
FLOOR = 7
PT, PW, WW, MW, BW = b('pink_terracotta'), b('pink_wool'), b('white_wool'), b('magenta_wool'), b('light_blue_wool')
CHP, CHL = b('cherry_planks'), b('cherry_log', axis='y')
LEAF = b('cherry_leaves', distance=7, persistent=True, waterlogged=False)
QZ, SQ, PQ = b('quartz_block'), b('smooth_quartz'), b('pink_concrete')
SL, GLOW, AME = b('sea_lantern'), b('shroomlight'), b('amethyst_block')
GRASS = b('grass_block', snowy=False)
STRIP = ['pink', 'light_blue', 'yellow', 'lime']
PIT, COURT, ARENA = (152, 32), (212, 32), (270, 32)


def build():
    d = kit.Dungeon('fairy', 'The Moonlit Promise', W, H, L, biome='minecraft:cherry_grove')
    d.loot_table = 'ascensiondungeons:chests/cherry_grove'
    d.shell = ('pink_terracotta', 'cherry_planks', 'calcite', 'magenta_terracotta', 'white_terracotta', 'pink_wool')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, PT)
    C.key(d, 'wish_star', 'minecraft:pink_dye', 'Wish Star', 'Faintly warm. The Wish Fountain in the Petal Court wants it.')
    C.key(d, 'promise_key', 'minecraft:tripwire_hook', 'Promise Key', 'Opens the moonlit door to the Moonlit Promise Sanctum.')
    grove(d); gallery(d); hollow(d); court(d); vault(d); sanctum(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R5', False), ('story', 'R3', True), ('story', 'R4', True), ('story', 'R5', False),
                     ('offering', 'R5', True), ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, PT, [(b('calcite'), 0.14), (b('magenta_terracotta'), 0.10), (b('white_terracotta'), 0.08), (CHP, 0.06)])
    return d


def lawn(d, x1, z1, x2, z2, flora=0.2):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if d.get(x, 8, z) == kit.AIR:
                d.set(x, FLOOR, z, GRASS if d.rng.random() < 0.85 else b('moss_block'))
                if d.rng.random() < flora and d.get(x, FLOOR, z) == GRASS: d.set(x, 8, z, b(d.rng.choice(['pink_petals', 'cornflower', 'allium', 'azure_bluet', 'short_grass', 'pink_tulip'])) if True else AIR)


# ================================================================ R1 Moonflower Grove (cave around 24,30)
def grove(d):
    d.ellipsoid(24, 15, 30, 22, 14, 19, kit.AIR)
    d.fill(2, 0, 10, 48, FLOOR, 52, PT)
    lawn(d, 3, 11, 47, 51)
    d.entry = (7, 8, 30)
    # Moonflower Tree (focal): a cherry tree with a cloud of blossom and hanging lanterns
    tx, tz = 28, 30
    S.tree(d, tx, tz, 8, 11, 2, CHL, LEAF, 10, 6)
    for k, (dx, dz) in enumerate([(4, 0), (-4, 0), (0, 4), (0, -4), (3, 3), (-3, -3)]):
        d.fill(tx + dx, 17 - k % 2, tz + dz, tx + dx, 18, tz + dz, b('chain', axis='y', waterlogged=False)); d.set(tx + dx, 16 - k % 2, tz + dz, b('lantern', hanging=True, waterlogged=False))
    d.set(tx, 20, tz, GLOW)
    d.landmark('moonflower_tree', 'Moonflower Tree', 'focal', (tx - 10, 8, tz - 10), (tx + 10, 24, tz + 10))
    # Hidden Blossom Nest (discovery): a petal bed in a hollow of the trunk-side roots
    nx, nz = 20, 40
    S.pyramid(d, nx, nz, 8, 4, [PW, b('white_wool')]); d.air(nx - 1, 8, nz - 1, nx + 1, 9, nz + 1); d.air(nx, 8, nz - 4, nx, 9, nz - 2)
    d.set(nx, 8, nz, b('pink_petals', flower_amount=4, facing='north')) if False else None
    d.add_chest((nx, 8, nz), 'north', 'loot'); d.set(nx - 1, 8, nz, b('candle', candles=3, lit=True, waterlogged=False)); d.set(nx + 1, 8, nz, b('candle', candles=2, lit=True, waterlogged=False))
    d.landmark('hidden_blossom_nest', 'Hidden Blossom Nest', 'discovery', (nx - 4, 8, nz - 4), (nx + 4, 11, nz + 1))
    # Traveling Storyteller's Camp (discovery): a striped tent and a story-wagon
    cx, cz = 14, 14
    S.pyramid(d, cx, cz, 8, 3, [WW, PW]); d.air(cx - 1, 8, cz - 1, cx + 1, 9, cz + 1)
    d.fill(cx + 5, 8, cz - 1, cx + 8, 8, cz + 1, CHP); d.fill(cx + 5, 9, cz - 1, cx + 8, 11, cz - 1, CHP); d.fill(cx + 5, 12, cz - 1, cx + 8, 12, cz + 1, WW)
    d.set(cx + 5, 7, cz - 1, b('dark_oak_log', axis='x')) if False else None
    d.set(cx + 4, 8, cz + 2, b('lantern', hanging=False, waterlogged=False)); d.add_chest((cx, 8, cz), 'east', 'loot')
    d.set(cx - 2, 8, cz + 2, b('barrel', facing='up', open=False))
    d.landmark('traveling_storyteller_s_camp', "Traveling Storyteller's Camp", 'discovery', (cx - 3, 8, cz - 3), (cx + 8, 12, cz + 3))
    # Moonlight Trial (interactive): a crescent ring of blooms round a plate
    px, pz = 12, 44
    d.disc(px, pz, 5, FLOOR, FLOOR, b('moss_block')); d.disc(px, pz, 1, FLOOR, FLOOR, QZ)
    d.set(px, 8, pz, b('light_weighted_pressure_plate', power=0))
    for k, (x, z) in enumerate(S.pts_ring(px, pz, 4, 12)):
        if k % 3 != 0: d.set(x, 8, z, b('pink_petals', flower_amount=2, facing='north')) if False else d.set(x, 8, z, b('pink_tulip'))
    for (x, z) in S.pts_ring(px, pz, 6, 4, 0.8): d.fill(x, 8, z, x, 10, z, CHL); d.set(x, 11, z, SL)
    d.landmark('moonlight_trial', 'Moonlight Trial', 'interactive', (px - 7, 7, pz - 7), (px + 7, 11, pz + 7))
    C.trial(d, 'moonlight_trial', (px, 8, pz), 'R1',
            [{'say': 'The petals close. Little lights circle in the dark.', 'spawns': [C.mob('minecraft:vex', 2, 'Will-o-Wisp'), C.mob('minecraft:zombie', 2, 'Gloomed Gardener')]},
             {'say': 'The moon climbs and the lights grow sharp.', 'spawns': [C.mob('minecraft:vex', 3, 'Will-o-Wisp'), C.mob('minecraft:witch', 1, 'Thorn Witch')]}],
            [C.say('The moon sets. A Wish Star rests in the petals.', 'gold'), C.give_key('wish_star', (px, 8, pz))])
    d.air(44, 8, 29, 62, 11, 31)
    d.room('R1', 'Moonflower Grove', (2, 8, 10), (50, 28, 52))


# ================================================================ R2 Storybook Gallery (x62..122, z16..46)
def gallery(d):
    d.air(62, 8, 16, 122, 22, 46)
    S.floor_pattern(d, 62, 16, 122, 46, FLOOR, [QZ, PQ, SQ], 3)
    for x in range(66, 122, 6):
        d.fill(x, 8, 16, x, 22, 16, b('cherry_log', axis='y')); d.fill(x, 8, 46, x, 22, 46, b('cherry_log', axis='y')); d.set(x, 19, 17, GLOW)
    for x in range(68, 120, 10): d.set(x, 22, 31, SL)
    # giant open books standing on the floor (decor)
    for (x, z) in ((70, 38), (86, 38), (104, 38)):
        d.fill(x, 8, z, x + 3, 12, z, b('white_concrete')); d.fill(x, 8, z + 1, x + 3, 9, z + 1, CHP) if False else d.fill(x, 8, z + 1, x + 3, 8, z + 3, CHP)
    # Storybook Castle Facade (focal): a pastel castle front on the north wall, towers and a gate
    cx = 92
    d.fill(cx - 12, 8, 14, cx + 12, 18, 15, b('white_concrete')); d.fill(cx - 12, 8, 14, cx + 12, 8, 15, SQ)
    for tx in (cx - 12, cx + 12, cx - 6, cx + 6):
        h = 24 if abs(tx - cx) == 12 else 21
        d.fill(tx - 1, 8, 14, tx + 1, h, 16, PQ)
        S.spire(d, tx, 15, h + 1, 5, 2, b('pink_terracotta') if False else PW)
        d.set(tx, h, 17, SL)
    d.fill(cx - 2, 8, 14, cx + 2, 13, 15, kit.AIR)
    d.fill(cx - 2, 14, 14, cx + 2, 14, 15, PQ)
    for x in range(cx - 10, cx + 11, 2): d.set(x, 19, 14, b('white_concrete')) if x % 4 else d.set(x, 19, 15, PQ)
    d.landmark('storybook_castle_facade', 'Storybook Castle Facade', 'focal', (cx - 13, 8, 14), (cx + 13, 25, 17))
    # Dollhouse Cache (discovery): a doll-sized house with a chest inside
    hx, hz = 74, 22
    S.hut(d, hx, hz, hx + 4, hz + 4, 8, 4, PQ, CHP, door='s', floor=QZ)
    d.add_chest((hx + 2, 8, hz + 2), 'south', 'loot'); d.set(hx + 1, 8, hz + 1, b('candle', candles=2, lit=True, waterlogged=False)); d.set(hx + 3, 8, hz + 1, b('flower_pot'))
    d.landmark('dollhouse_cache', 'Dollhouse Cache', 'discovery', (hx - 1, 8, hz - 1), (hx + 5, 13, hz + 5))
    # Story Sequence Pedestals (interactive): four pedestals; ribbon strip on the wall shows the order (lime, pink, light blue, yellow)
    order = [3, 0, 1, 2]
    btn = []
    for k, c in enumerate([0, 1, 2, 3]):
        x = 100 + k * 4
        d.fill(x - 1, 8, 40, x + 1, 8, 42, b(STRIP[c] + '_concrete'))
        d.set(x, 9, 41, b('stone_button', face='floor', facing='north', powered=False))
        btn.append((x, 9, 41))
    d.fill(100, 12, 45, 115, 14, 45, SQ)
    for k, c in enumerate(order): d.fill(102 + k * 3, 12, 45, 103 + k * 3, 14, 45, b(STRIP[c] + '_concrete'))
    d.landmark('story_sequence_pedestal', 'Story Sequence Pedestal', 'interactive', (98, 8, 39), (116, 14, 45))
    # Sealed Story Chest (discovery): a nook behind the south-west wall, opened by the same sequence
    sx = 66
    d.air(sx, 8, 47, sx + 6, 11, 52)
    d.fill(sx, 7, 47, sx + 6, 7, 52, PQ)
    d.add_chest((sx + 3, 8, 52), 'north', 'loot'); d.set(sx + 1, 8, 50, b('lectern', facing='east', powered=False)); d.set(sx + 5, 8, 50, b('lectern', facing='west', powered=False))
    d.set(sx + 3, 10, 51, b('lantern', hanging=True, waterlogged=False)) if False else d.set(sx + 3, 8, 50, b('candle', candles=3, lit=True, waterlogged=False))
    d.seal('story_wall', 'minecraft:pink_concrete', (sx + 2, 8, 46), (sx + 4, 11, 46))
    d.landmark('sealed_story_chest', 'Sealed Story Chest', 'discovery', (sx, 7, 46), (sx + 6, 11, 52))
    d.air(122, 8, 30, 134, 11, 33)
    d.seal('ribbon_gate', 'minecraft:pink_concrete', (124, 8, 30), (125, 11, 33))
    C.sequence(d, 'story', btn, order, [C.clear(C.bx((124, 8, 30), (125, 11, 33))), C.clear(C.bx((sx + 2, 8, 46), (sx + 4, 11, 46))),
                                        C.sound((124, 9, 31), 'minecraft:block.amethyst_block.chime', 2, 1.2),
                                        C.say('The story ends well. A ribbon gate unties itself, and a hidden page turns in the south-west wall.', 'light_purple')],
               'The story is told in order.', 'The story muddles. Read the ribbon strip on the south wall: lime, pink, light blue, yellow.')
    C.encounter(d, 'R2', 'Pages rustle by themselves. The stories in here have teeth.',
                [C.mob('minecraft:vex', 2, 'Will-o-Wisp'), C.mob('minecraft:zombie', 3, 'Gloomed Gardener'), C.mob('minecraft:witch', 1, 'Thorn Witch')])
    d.room('R2', 'Storybook Gallery', (62, 8, 14), (122, 22, 52))


# ================================================================ R3 Ribbonbridge Hollow (pit centre 152,32 r20; ribbon ledge on the north half)
def hollow(d):
    cx, cz = PIT
    xs, zs = np.arange(W)[None, :], np.arange(L)[:, None]
    pit = (xs - cx) ** 2 + (zs - cz) ** 2 <= 20 * 20 + 0.5
    for y in range(2, 34): d.a[y][pit] = d.pid(kit.AIR)
    d.fill(130, 0, 10, 175, 1, 55, PT)
    d.air(122, 8, 30, 134, 11, 33)
    # the ribbon ledge: an arc of ribbon along the north half of the rim (r 17..20), at feet y8
    ledge = ((xs - cx) ** 2 + (zs - cz) ** 2 <= 20 * 20 + 0.5) & ((xs - cx) ** 2 + (zs - cz) ** 2 >= 17 * 17) & (zs <= cz + 1)
    for y in range(2, 7): d.a[y][ledge] = d.pid(PT)
    d.a[7][ledge] = d.pid(PW)
    for (x, z) in S.pts_ring(cx, cz, 17, 40):
        if z <= cz: d.set(x, 8, z, b('pink_carpet')) if False else None
    for k in range(0, 40, 3):
        x, z = S.pts_ring(cx, cz, 17, 40)[k]
        if z < cz - 2: d.fill(x, 8, z, x, 8, z, b('chain', axis='y', waterlogged=False)) if False else d.set(x, 8, z, b('cherry_fence'))
    # the south half rim is solid wall; the pit floor is flower beds
    for x in range(131, 175):
        for z in range(11, 54):
            if pit[z, x] and d.rng.random() < 0.25: d.set(x, 2, z, b(d.rng.choice(['pink_petals', 'cornflower', 'allium', 'short_grass'])))
    d.a[1][pit] = d.pid(GRASS)
    d.air(132, 8, 28, 135, 11, 36)
    d.air(169, 8, 28, 175, 11, 36)
    # ladders back up from the pit floor beside each end, and a divider so the floor is no shortcut
    for y in range(2, 8): d.set(cx - 16, y, cz - 2, kit.ladder('east')); d.set(cx + 16, y, cz - 2, kit.ladder('west'))
    d.fill(cx, 2, cz - 17, cx, 6, cz + 17, b('iron_bars'))
    # Ribbon Arch (focal) over the ledge at its west end, and at the east end
    S.arch(d, cx - 20, cz - 5, 8, 4, 8, QZ, axis='z', depth=2) if False else None
    for ax in (cx - 18, cx + 18):
        S.arch(d, ax, cz - 3, 8, 6, 9, QZ, axis='z', depth=2)
        for k in range(4): d.fill(ax, 18 + k % 2, cz - 3 + k * 2, ax + 1, 19 + k % 2, cz - 3 + k * 2 + 1, PW if k % 2 else BW)
        d.set(ax, 20, cz, SL)
    d.landmark('ribbon_arch', 'Ribbon Arch', 'focal', (cx - 20, 8, cz - 5), (cx - 15, 20, cz + 3))
    for (x, z) in S.pts_ring(cx, cz, 19, 14, 0.2): d.set(x, 18, z, GLOW)
    d.set(cx, 22, cz, SL) if False else None
    C.crumble(d, 'ribbon_fray', C.bx((cx - 21, 7, cz - 21), (cx + 21, 7, cz + 1)), only='pink_wool', delay=24, restore=170, sound='minecraft:block.wool.break')
    C.pulse(d, 'dazzle', box=C.bx((131, 2, 10), (174, 34, 55)), period=80, warn=34, count=1, radius=2.2, damage=1.5, kind='magic',
            warnParticle='minecraft:end_rod', particle='minecraft:glow', effect='minecraft:blindness', duration=60, amp=0,
            sound='minecraft:block.amethyst_block.chime', vol=1.2, pitch=1.6, scatter=False, **{'target': 'players'})
    d.room('R3', 'Ribbonbridge Hollow', (130, 2, 10), (175, 34, 55))


# ================================================================ R4 Petal Court (centre 212,32 r22)
def court(d):
    cx, cz = COURT
    d.air(176, 8, 29, 190, 11, 35)
    d.disc(cx, cz, 22, 8, 28, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, QZ); d.disc(cx, cz, 18, FLOOR, FLOOR, PQ, rim=15); d.disc(cx, cz, 12, FLOOR, FLOOR, SQ, rim=9)
    lawn(d, cx - 21, cz - 21, cx + 21, cz + 21, 0.1) if False else None
    for (x, z) in S.pts_ring(cx, cz, 21, 16, 0.2): d.fill(x, 8, z, x, 22, z, CHL); d.set(x, 23, z, LEAF); d.set(x, 15, z, GLOW)
    # Wish Fountain (focal): a basin of glass and quartz with arcing jets of sea lantern and amethyst
    d.disc(cx, cz, 6, 8, 8, QZ); d.disc(cx, cz, 5, 9, 9, b('light_blue_stained_glass')); d.disc(cx, cz, 4, 8, 8, SL) if False else None
    d.fill(cx, 9, cz, cx, 15, cz, AME)
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 4, 8)):
        d.fill(x, 9, z, x, 12 + k % 2, z, b('light_blue_stained_glass') if k % 2 else b('white_stained_glass'))
    d.set(cx, 16, cz, SL)
    d.landmark('wish_fountain', 'Wish Fountain', 'focal', (cx - 7, 8, cz - 7), (cx + 7, 17, cz + 7))
    # Giant Teacup Pavilion (focal) with the Tea Garden Station inside
    tx, tz = cx - 15, cz + 6
    d.disc(tx, tz, 6, 8, 8, QZ); d.disc(tx, tz, 6, 9, 11, b('white_concrete')); d.disc(tx, tz, 5, 9, 11, kit.AIR)
    d.disc(tx, tz, 6, 12, 12, b('pink_concrete'), rim=3); d.air(tx + 5, 9, tz - 1, tx + 6, 11, tz + 1)
    d.fill(tx + 7, 9, tz - 2, tx + 8, 12, tz + 2, b('white_concrete')); d.air(tx + 7, 10, tz - 1, tx + 8, 11, tz + 1)    # the handle
    d.disc(tx, tz, 7, 8, 8, b('pink_concrete'), rim=6)
    d.fill(tx - 1, 9, tz - 1, tx + 1, 9, tz + 1, b('cherry_planks')); d.set(tx, 10, tz, b('flower_pot'))
    d.set(tx - 3, 9, tz, b('cauldron')); d.set(tx + 3, 9, tz + 2, b('barrel', facing='up', open=False)); d.set(tx, 11, tz, b('lantern', hanging=True, waterlogged=False)) if False else None
    d.fill(tx, 12, tz, tx, 13, tz, b('chain', axis='y', waterlogged=False)); d.set(tx, 11, tz, b('lantern', hanging=True, waterlogged=False))
    d.landmark('giant_teacup_pavilion', 'Giant Teacup Pavilion', 'focal', (tx - 7, 8, tz - 7), (tx + 8, 13, tz + 7))
    d.landmark('tea_garden_station', 'Tea Garden Station', 'interactive', (tx - 5, 8, tz - 5), (tx + 5, 12, tz + 5))
    C.zone(d, 'tea_mending', C.bx((tx - 5, 9, tz - 5), (tx + 5, 12, tz + 5)), 'minecraft:regeneration', 0, 100, 40)
    C.zone(d, 'tea_shield', C.bx((tx - 5, 9, tz - 5), (tx + 5, 12, tz + 5)), 'minecraft:absorption', 1, 400, 200)
    # Petal Alignment (interactive): four levers under open (pink petals) or closed (green bud) clues; opens the Starlit Alcove
    pats = [True, False, False, True]
    lv = []
    for k, x in enumerate((cx - 8, cx - 3, cx + 3, cx + 8)):
        z = cz - 19
        d.fill(x - 1, 8, z - 1, x + 1, 11, z - 1, CHL)
        C.wall_lever(d, x, 8, z, 'n')
        d.set(x, 10, z, b('pink_wool') if pats[k] else b('lime_wool'))
        lv.append(((x, 8, z), pats[k]))
    d.landmark('petal_alignment', 'Petal Alignment', 'interactive', (cx - 10, 8, cz - 21), (cx + 10, 11, cz - 18))
    d.air(cx - 3, 8, 3, cx + 3, 12, 9)
    d.fill(cx - 3, 7, 3, cx + 3, 7, 9, GRASS)
    for x in range(cx - 2, cx + 3, 2): d.set(x, 8, 5, b('pink_tulip'))
    d.add_chest((cx, 8, 4), 'south', 'loot'); d.set(cx - 3, 8, 4, b('lantern', hanging=False, waterlogged=False)); d.set(cx + 3, 8, 4, b('lantern', hanging=False, waterlogged=False))
    d.fill(cx - 3, 8, 9, cx + 3, 11, 9, kit.AIR)
    d.seal('starlit_wall', 'minecraft:pink_concrete', (cx - 1, 8, 9), (cx + 1, 11, 10))
    d.landmark('starlit_alcove', 'Starlit Alcove', 'discovery', (cx - 3, 7, 3), (cx + 3, 12, 10))
    C.toggle(d, 'petals', lv, [C.clear(C.bx((cx - 1, 8, 9), (cx + 1, 11, 10))), C.sound((cx, 9, 9), 'minecraft:block.amethyst_block.chime', 2, 1.0),
                               C.say('The petals open to the stars. A starlit alcove appears in the north wall.', 'light_purple')],
             'The petals open as one.')
    # Wish Offering at the fountain's south edge, with the Wish Vault gate behind it
    ox, oz = cx, cz + 17
    d.fill(ox, 8, oz, ox, 9, oz, QZ); d.set(ox, 10, oz, b('light_blue_stained_glass'))
    for x in (ox - 2, ox + 2): d.fill(x, 8, oz, x, 11, oz, CHL); d.set(x, 12, oz, SL)
    d.landmark('wish_offering', 'Wish Offering', 'interactive', (ox - 2, 8, oz - 1), (ox + 2, 12, oz + 1))
    d.air(cx - 1, 8, 54, cx + 1, 11, 66)
    d.seal('wish_gate', 'minecraft:pink_concrete', (cx - 1, 8, 56), (cx + 1, 11, 57))
    C.lock(d, 'offering', C.bx((ox, 10, oz)), 'wish_star', [C.clear(C.bx((cx - 1, 8, 56), (cx + 1, 11, 57))), C.sound((cx, 9, 56), 'minecraft:block.amethyst_block.chime', 2, 1.3),
                                                          C.say('You make a wish. Petals drift aside from a gate in the south wall.', 'light_purple')],
           'A glass dish at the fountain, waiting for a star.', 'drops the Wish Star in the dish and a gate of petals drifts aside.')
    d.air(cx + 20, 8, 31, cx + 38, 11, 33)
    d.seal('arena_door', 'minecraft:pink_concrete', (cx + 23, 8, 31), (cx + 24, 11, 33))
    C.lock(d, 'arena_door', C.bx((cx + 23, 8, 31), (cx + 24, 11, 33)), 'promise_key', [C.clear(C.bx((cx + 23, 8, 31), (cx + 24, 11, 33))),
                                                                                      C.sound((cx + 23, 9, 32), 'minecraft:block.amethyst_block.chime', 2, 0.7), C.say('The moonlit door to the Sanctum opens.', 'gold')],
           'A moonlit door with a keyhole shaped like a crescent.', 'turns the Promise Key and the moonlit door swings open.')
    C.encounter(d, 'R4', 'Petals fall though there is no wind. Levers wait in the north; a glass dish waits in the south.',
                [C.mob('minecraft:vex', 2, 'Will-o-Wisp'), C.mob('minecraft:zombie', 2, 'Gloomed Gardener'), C.mob('minecraft:witch', 1, 'Thorn Witch')])
    d.room('R4', 'Petal Court', (190, 8, 3), (234, 28, 54))


# ================================================================ R5 Wish Vault (x194..232, z68..100)
def vault(d):
    cx = COURT[0]
    d.air(194, 8, 68, 230, 20, 100)
    d.air(cx - 1, 8, 58, cx + 1, 11, 68)
    S.floor_pattern(d, 194, 68, 230, 100, FLOOR, [PQ, QZ], 2)
    for x in range(198, 228, 6):
        for z in (74, 90):
            d.fill(x, 8, z, x + 1, 13, z + 1, CHL); d.ellipsoid(x, 15, z, 3, 2, 3, LEAF, only_air=True); d.set(x, 13, z, SL)
    d.fill(cx - 3, 8, 82, cx + 3, 8, 88, SQ); d.fill(cx - 2, 9, 83, cx + 2, 9, 87, b('light_blue_stained_glass')); d.fill(cx - 1, 10, 84, cx + 1, 12, 86, AME); d.set(cx, 13, 85, SL)
    d.add_chest((cx, 8, 97), 'north', 'key', key='promise_key')
    d.fill(cx - 2, 8, 98, cx + 2, 10, 99, PQ); d.set(cx - 2, 8, 97, b('candle', candles=3, lit=True, waterlogged=False)); d.set(cx + 2, 8, 97, b('candle', candles=3, lit=True, waterlogged=False))
    d.room('R5', 'Wish Vault', (194, 8, 58), (230, 20, 100))


# ================================================================ R6 Moonlit Promise Sanctum (centre 270,32 r22)
def sanctum(d):
    cx, cz = ARENA
    d.air(234, 8, 31, 248, 11, 33)
    d.disc(cx, cz, 22, 8, 30, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, QZ); d.disc(cx, cz, 18, FLOOR, FLOOR, PQ, rim=14); d.disc(cx, cz, 9, FLOOR, FLOOR, SQ, rim=5)
    d.disc(cx, cz, 4, 8, 8, AME); d.fill(cx, 9, cz, cx, 12, cz, AME); d.set(cx, 13, cz, SL)
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 14, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 22, z + 1, CHL); d.ellipsoid(x, 24, z, 3, 2, 3, LEAF, only_air=True)
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, SL); targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): d.fill(x, 8, z, x, 16, z, CHL); d.set(x, 17, z, GLOW)
    C.boss(d, 'promise_keeper', 'R6', 'minecraft:witch', 'The Promise-Keeper', (cx, 9, cz + 8), targets=targets,
           on_block='minecraft:sea_lantern', off_block='minecraft:pink_concrete', health=6.0, scale=1.7, color='PINK',
           intro='The Promise-Keeper rises, smiling. Snuff the three moonstones on the trees to break its promise.',
           death='The Promise-Keeper curtsies, and the sanctum fills with petals.', break_text='The moonstones go dark! The Promise-Keeper is exposed!',
           adds=[C.mob('minecraft:vex', 2, 'Will-o-Wisp') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('A hundred small lights open their eyes.', 'red'), C.spawn('minecraft:vex', 2, 'R6', 'Will-o-Wisp')]},
                   {'at': 0.33, 'effects': [C.say('The Promise-Keeper stops smiling.', 'red'), C.spawn('minecraft:zombie', 3, 'R6', 'Gloomed Gardener')]}])
    C.pulse(d, 'sanctum_dazzle', box=C.bx((250, 8, 10), (292, 30, 54)), period=60, warn=28, count=2, radius=2.1, damage=2.5, kind='magic',
            warnParticle='minecraft:end_rod', particle='minecraft:glow', effect='minecraft:blindness', duration=60, amp=0,
            sound='minecraft:block.amethyst_block.chime', pitch=1.6, after='boss_awake:promise_keeper', until='boss:promise_keeper', scatter=True, **{'target': 'players'})
    C.finish(d, 'promise_keeper', 'The Moonlit Promise. Tell the story in order, cross the ribbon, and make a wish at the fountain.', 'The petals settle. The grove closes shortly.')
    d.room('R6', 'Moonlit Promise Sanctum', (250, 8, 10), (292, 31, 54))
