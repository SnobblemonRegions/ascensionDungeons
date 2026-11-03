"""The Elderroot Conservatory: the Grass dungeon (id `grass`).

Rooms: Rootwood Grove -> Conservatory Gallery -> Vine Ravine -> Bloom Court -> Seed Vault; arena Elderroot Sanctuary.
Hazard : Vine Ravine - a plank bridge that crumbles under your feet (fall = a few hearts, ladders lead back up) and thorn volleys
         that land where you stand after a telegraph.
Puzzle : Pruning Gate - four levers must match the blooming/bare shrubs above them (visual clue) to open the way to the ravine;
         Growth Trial in the grove yields an Elder Seed; the seed shrine in the Bloom Court opens the Seed Vault; its key opens the arena.
Extra  : Pollination Garden - press the four flower pads in the order of the sun (east, south, west, north) to open the Hidden Fern Tunnel.
Boss   : the Elderroot Warden (ravager) behind three bloom-bud wards; thorn volleys rain on the arena while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever

W, H, L = 240, 44, 140
FLOOR = 7
MC, MOSS, GRASS = b('mossy_cobblestone'), b('moss_block'), b('grass_block', snowy=False)
LEAF = b('oak_leaves', distance=7, persistent=True, waterlogged=False)
AZ = b('azalea_leaves', distance=7, persistent=True, waterlogged=False)
FAZ = b('flowering_azalea_leaves', distance=7, persistent=True, waterlogged=False)
SLEAF = b('spruce_leaves', distance=7, persistent=True, waterlogged=False)
LOG, DLOG, SLOG = b('oak_log', axis='y'), b('dark_oak_log', axis='y'), b('stripped_oak_log', axis='y')
PLK, DPLK = b('oak_planks'), b('dark_oak_planks')
MBR, SHL, GLASS, PODZ = b('mossy_stone_bricks'), b('shroomlight'), b('glass'), b('podzol')
FLOWERS = ['poppy', 'dandelion', 'azure_bluet', 'cornflower', 'oxeye_daisy', 'allium', 'short_grass', 'fern', 'short_grass']
COURT, ARENA = (166, 32), (216, 32)


def build():
    d = kit.Dungeon('grass', 'The Elderroot Conservatory', W, H, L, biome='minecraft:flower_forest')
    d.loot_table = 'ascensiondungeons:chests/verdant_canopy'
    d.shell = ('mossy_cobblestone', 'moss_block', 'cobblestone', 'mud_bricks', 'rooted_dirt')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, MC)
    C.key(d, 'elder_seed', 'minecraft:wheat_seeds', 'Elder Seed', 'A seed that hums. The Seed Offering Shrine in the Bloom Court wants it.')
    C.key(d, 'elder_key', 'minecraft:tripwire_hook', 'Elder Key', 'Opens the root-bound door to the Elderroot Sanctuary.')
    grove(d); gallery(d); ravine(d); court(d); vault(d); arena(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R5', False), ('pruning', 'R3', True), ('pruning', 'R4', True),
                     ('pruning', 'R5', False), ('pruning', 'R6', False), ('offering', 'R5', True), ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, MC, [(MOSS, 0.20), (b('cobblestone'), 0.12), (b('mud_bricks'), 0.08), (b('rooted_dirt'), 0.06)])
    return d


def lawn(d, x1, z1, x2, z2, y=FLOOR, flora=0.22):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if d.get(x, y + 1, z) == kit.AIR and d.get(x, y, z) not in (kit.AIR,):
                r = d.rng.random()
                d.set(x, y, z, GRASS if r < 0.7 else MOSS if r < 0.9 else PODZ)
                if d.rng.random() < flora and d.get(x, y + 1, z) == kit.AIR and d.get(x, y, z) == GRASS:
                    f = d.rng.choice(FLOWERS)
                    d.set(x, y + 1, z, b(f))


# ================================================================ R1 Rootwood Grove  (cave around 22,22)
def grove(d):
    d.ellipsoid(22, 16, 22, 17, 16, 17, kit.AIR)
    d.fill(3, 0, 3, 41, FLOOR, 41, MC)
    lawn(d, 4, 4, 40, 40)
    d.entry = (9, 8, 22)
    # Elderroot Tree (focal): a colossal trunk with buttress roots and a leaf canopy that touches the cave roof
    tx, tz = 24, 24
    S.tree(d, tx, tz, 8, 16, 3, LOG, LEAF, 12, 7)
    for (x, z) in S.pts_ring(tx, tz, 5, 8):
        d.fill(x, 8, z, x, 10, z, DLOG); d.fill((x + tx) // 2, 8, (z + tz) // 2, (x + tx) // 2, 12, (z + tz) // 2, DLOG)
    for k, (x, z) in enumerate(S.pts_ring(tx, tz, 3, 8, 0.4)): d.set(x, 20 + k % 3, z, SHL)
    d.landmark('elderroot_tree', 'Elderroot Tree', 'focal', (tx - 12, 8, tz - 12), (tx + 12, 31, tz + 12))
    # Root Cache (discovery): a nook between the buttress roots on the far side of the tree
    d.fill(tx - 1, 8, tz + 5, tx + 1, 10, tz + 6, DLOG); d.air(tx, 8, tz + 5, tx, 9, tz + 5)
    d.add_chest((tx, 8, tz + 6), 'north', 'loot')
    d.set(tx - 1, 8, tz + 6, b('lantern', hanging=False, waterlogged=False)); d.set(tx + 1, 8, tz + 6, b('podzol')) if False else d.set(tx + 1, 8, tz + 6, b('azure_bluet'))
    d.landmark('root_cache', 'Root Cache', 'discovery', (tx - 1, 8, tz + 5), (tx + 1, 10, tz + 6))
    # Hollow Log Nest (discovery): a fallen trunk you can walk into
    lx, lz = 11, 11
    d.fill(lx - 4, 8, lz - 2, lx + 4, 12, lz + 2, b('oak_log', axis='x'))
    d.fill(lx - 4, 9, lz - 1, lx + 4, 11, lz + 1, kit.AIR)
    d.fill(lx - 4, 9, lz, lx - 4, 10, lz, kit.AIR); d.fill(lx + 4, 9, lz, lx + 4, 10, lz, kit.AIR)
    d.fill(lx - 3, 9, lz - 1, lx + 3, 9, lz + 1, b('hay_block')) if False else None
    d.set(lx - 4, 9, lz, kit.AIR)
    d.add_chest((lx, 9, lz), 'south', 'loot')
    for x in range(lx - 3, lx + 4):
        d.set(x, 9, lz - 1, b('hay_block'))
    d.landmark('hollow_log_nest', 'Hollow Log Nest', 'discovery', (lx - 4, 8, lz - 2), (lx + 4, 12, lz + 2))
    # Growth Trial Circle (interactive): a ring of blooms round a plate on a moss mound
    gx, gz = 14, 33
    d.disc(gx, gz, 5, FLOOR, FLOOR, MOSS)
    d.disc(gx, gz, 2, FLOOR + 1, FLOOR + 1, MOSS)
    d.set(gx, 9, gz, b('stone_pressure_plate', powered=False))
    for k, (x, z) in enumerate(S.pts_ring(gx, gz, 4, 12)): d.set(x, 8, z, b(['poppy', 'cornflower', 'allium', 'azure_bluet'][k % 4]))
    for (x, z) in S.pts_ring(gx, gz, 6, 4, 0.8): S.column(d, x, z, 8, 3, LOG, cap=LEAF) if False else d.fill(x, 8, z, x, 10, z, SLOG)
    d.landmark('growth_trial_circle', 'Growth Trial Circle', 'interactive', (gx - 6, 7, gz - 6), (gx + 6, 10, gz + 6))
    C.trial(d, 'growth_trial', (gx, 9, gz), 'R1',
            [{'say': 'The ring of blooms wilts. Blighted things crawl out of the roots.', 'spawns': [C.mob('minecraft:zombie', 3, 'Blighted Gardener')]},
             {'say': 'Thorned things answer next.', 'spawns': [C.mob('minecraft:spider', 3, 'Briar Crawler'), C.mob('minecraft:zombie', 2, 'Blighted Gardener')]}],
            [C.say('The blooms open again. An Elder Seed lies in the moss.', 'gold'), C.give_key('elder_seed', (gx, 9, gz))])
    # Herbalist Station (interactive): a tidy hut where wounds mend
    hx, hz = 31, 11
    S.hut(d, hx - 3, hz - 3, hx + 3, hz + 3, 8, 4, DPLK, PLK, door='s', floor=PLK)
    d.set(hx - 2, 8, hz - 2, b('composter')); d.set(hx + 2, 8, hz - 2, b('flower_pot')); d.set(hx, 8, hz - 2, b('cauldron')); d.set(hx + 2, 8, hz + 2, b('hay_block'))
    d.set(hx - 2, 8, hz + 2, b('barrel', facing='up', open=False)); d.set(hx, 11, hz, b('lantern', hanging=True, waterlogged=False))
    d.landmark('herbalist_station', 'Herbalist Station', 'interactive', (hx - 4, 7, hz - 4), (hx + 4, 12, hz + 4))
    C.zone(d, 'herbal_mending', C.bx((hx - 3, 8, hz - 3), (hx + 3, 11, hz + 3)), 'minecraft:regeneration', 0, 80, 40)
    # corridor east
    d.air(34, 8, 21, 53, 11, 23)
    d.room('R1', 'Rootwood Grove', (4, 8, 4), (41, 32, 41))
    C.encounter(d, 'R1', None, []) if False else None


# ================================================================ R2 Conservatory Gallery (x52..118, z12..40)
def gallery(d):
    d.air(52, 8, 12, 111, 24, 40)
    d.fill(52, 25, 12, 111, 25, 40, GLASS)
    for x in range(52, 112, 6):
        d.fill(x, 8, 12, x, 24, 12, b('dark_oak_log', axis='y')); d.fill(x, 8, 40, x, 24, 40, b('dark_oak_log', axis='y'))
        d.fill(x, 24, 12, x, 24, 40, b('dark_oak_log', axis='z'))
    S.floor_pattern(d, 52, 12, 111, 40, FLOOR, [MBR, b('stone_bricks')], 3)
    for z in (16, 36):
        d.fill(54, FLOOR, z, 109, FLOOR, z + 1, GRASS)       # planter strips
        for x in range(54, 110, 2): d.set(x, 8, z, b(d.rng.choice(FLOWERS[:6])))
    # Hanging Conservatory (focal): leaf planters hung on chains over the middle of the hall
    for (px, pz) in [(66, 22), (74, 30), (82, 22), (90, 30), (98, 22)]:
        d.fill(px, 16, pz, px + 3, 16, pz + 3, DPLK)
        d.fill(px, 17, pz, px + 3, 17, pz + 3, GRASS)
        d.fill(px + 1, 18, pz + 1, px + 2, 19, pz + 2, FAZ)
        for (cx_, cz_) in ((px, pz), (px + 3, pz), (px, pz + 3), (px + 3, pz + 3)):
            d.fill(cx_, 17, cz_, cx_, 24, cz_, b('chain', axis='y', waterlogged=False)) if False else d.fill(cx_, 18, cz_, cx_, 24, cz_, b('chain', axis='y', waterlogged=False))
    d.landmark('hanging_conservatory', 'Hanging Conservatory', 'focal', (66, 8, 22), (101, 24, 33))
    # Thorn Arch (focal): a gate of dark oak woven with leaves and berries across the walk
    ax = 108
    S.arch(d, ax, 26, 8, 8, 10, DLOG, axis='z', accent=SHL)
    d.fill(ax - 1, 8, 25, ax - 1, 20, 25, b('dark_oak_log', axis='y')) if False else None
    for (y, z) in [(12, 25), (14, 27), (16, 25), (13, 34), (15, 33)]: d.set(ax, y, z, FAZ)
    for yy in range(8, 18): d.set(ax - 1, yy, 25, SLEAF) if yy % 2 else None
    d.landmark('thorn_arch', 'Thorn Arch', 'focal', (ax - 1, 8, 25), (ax + 1, 20, 35))
    # Forgotten Greenhouse (discovery): a glass annex off the south wall
    gx0, gz0 = 60, 41
    d.air(gx0 + 4, 8, 40, gx0 + 6, 11, 41)
    d.air(gx0, 8, 42, gx0 + 12, 13, 52)
    d.fill(gx0 - 1, 14, 41, gx0 + 13, 14, 53, GLASS)
    d.fill(gx0, 7, 42, gx0 + 12, 7, 52, GRASS)
    for k in range(10): d.set(gx0 + d.rng.randint(1, 11), 8, 42 + d.rng.randint(1, 9), b(d.rng.choice(FLOWERS)))
    d.fill(gx0 + 5, 8, 48, gx0 + 7, 8, 49, b('hay_block'))
    d.add_chest((gx0 + 6, 9, 49), 'north', 'loot')
    d.landmark('forgotten_greenhouse', 'Forgotten Greenhouse', 'discovery', (gx0, 7, 41), (gx0 + 12, 14, 52))
    # Pruning Gate (interactive): four levers against the south wall, matched by blooming/bare shrubs above them
    targets = [True, False, True, True]
    lv = []
    for k, x in enumerate((89, 92, 97, 103)):
        d.set(x, 8, 40, lever('wall', 'north'))
        lv.append(((x, 8, 40), targets[k]))
        d.fill(x, 9, 40, x, 10, 40, kit.AIR)
        d.set(x, 11, 40, FAZ if targets[k] else AZ)
        d.set(x, 12, 39, FAZ if targets[k] else b('dead_bush')) if False else None
    d.landmark('pruning_gate', 'Pruning Gate', 'interactive', (88, 7, 37), (104, 12, 40))
    d.air(112, 8, 28, 119, 11, 36)
    d.seal('pruning_hedge', 'minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]', (114, 8, 28), (115, 11, 36))
    C.toggle(d, 'pruning', lv, [C.clear(C.bx((114, 8, 28), (115, 11, 36))),
                                C.sound((114, 9, 32), 'minecraft:block.grass.break', 2, 0.6),
                                C.say('The hedge draws back from the gate. Beyond it, a ravine opens.', 'green')],
             done_text='The shrubs rustle: the pattern is right.')
    C.encounter(d, 'R2', 'Roots shift among the planters. The glasshouse is not empty.',
                [C.mob('minecraft:zombie', 3, 'Overgrown Groundskeeper'), C.mob('minecraft:spider', 3, 'Briar Crawler'), C.mob('minecraft:skeleton', 2, 'Thornbow')])
    d.room('R2', 'Conservatory Gallery', (52, 8, 12), (113, 25, 53))


# ================================================================ R3 Vine Ravine (x120..140, z8..56)
def ravine(d):
    d.air(120, 2, 8, 140, 23, 56)
    d.fill(119, 1, 8, 141, 1, 56, MOSS)
    for x in range(120, 141):
        for z in range(8, 57):
            if d.rng.random() < 0.18: d.set(x, 2, z, b(d.rng.choice(['fern', 'short_grass', 'poppy'])))
    d.air(110, 8, 28, 119, 11, 36)
    # the plank bridge (3 wide) across at z31..33, with fence rails
    d.fill(120, 7, 31, 140, 7, 33, PLK)
    d.fill(120, 8, 31, 140, 8, 31, b('oak_fence', east=False, north=False, south=False, waterlogged=False, west=False)) if False else None
    for x in range(120, 141, 4):
        d.fill(x, 8, 31, x, 9, 31, b('oak_fence')); d.fill(x, 8, 33, x, 9, 33, b('oak_fence'))
    d.fill(120, 10, 31, 140, 10, 31, b('oak_slab', type='bottom', waterlogged=False)) if False else None
    # ladders back up (west at z=29, east at z=35)
    for y in range(2, 8): d.set(120, y, 29, kit.ladder('east')); d.set(140, y, 35, kit.ladder('west'))
    d.air(141, 8, 28, 144, 11, 36)
    # hanging vines of leaves for atmosphere
    for _ in range(40):
        x, z = d.rng.randint(121, 139), d.rng.randint(9, 55)
        if abs(z - 32) < 3: continue
        h = d.rng.randint(3, 8)
        d.fill(x, 23 - h, z, x, 22, z, LEAF if d.rng.random() < 0.6 else AZ)
    for z in (14, 22, 42, 50):
        d.fill(121, 3, z, 123, 5, z + 2, MBR)               # mossy ledges
    # Hidden Fern Tunnel is on the court's north side; the ravine only has a glow of shroomlight
    for z in range(10, 56, 6): d.set(119, 14, z, SHL); d.set(141, 14, z, SHL)
    C.crumble(d, 'bridge_planks', C.bx((120, 7, 31), (140, 7, 33)), only='oak_planks', delay=24, restore=160)
    C.pulse(d, 'thorn_volleys', box=C.bx((120, 2, 8), (140, 23, 56)), period=90, warn=32, count=1, radius=1.6, damage=3.0,
            kind='magic', warnParticle='minecraft:happy_villager', particle='minecraft:crit', sound='minecraft:block.sweet_berry_bush.pick_berries',
            scatter=True, vol=1.2, pitch=0.9, **{'target': 'players'})
    d.landmark_dummy = None
    d.room('R3', 'Vine Ravine', (116, 2, 8), (144, 23, 56))


# ================================================================ R4 Bloom Court (centre 166,32 r22)
def court(d):
    cx, cz = COURT
    d.disc(cx, cz, 22, 8, 28, kit.AIR)
    for x in range(cx - 22, cx + 23):
        for z in range(cz - 22, cz + 23):
            if d.get(x, 8, z) == kit.AIR: d.set(x, 7, z, MBR)
    d.disc(cx, cz, 22, 29, 29, GLASS)
    d.disc(cx, cz, 21, 28, 28, GLASS) if False else None
    for (x, z) in S.pts_ring(cx, cz, 21, 16):
        d.fill(x, 8, z, x, 27, z, DLOG)
        d.set(x, 14, z, SHL); d.set(x, 22, z, SHL)
    d.disc(cx, cz, 20, FLOOR, FLOOR, GRASS, rim=14)
    lawn(d, cx - 21, cz - 21, cx + 21, cz + 21, flora=0.18)
    # Giant Lotus (focal) on a dais in the middle
    d.disc(cx, cz, 7, 8, 8, MBR); d.disc(cx, cz, 5, 9, 9, b('dark_oak_planks'))
    for k in range(12):
        a_ = k * math.pi / 6
        for r in range(2, 8):
            x, z = int(round(cx + r * math.cos(a_))), int(round(cz + r * math.sin(a_)))
            d.set(x, 10 + r // 2, z, b('pink_terracotta') if r % 2 else b('white_terracotta'))
            if r >= 5: d.set(x, 11 + r // 2, z, b('pink_terracotta'))
    d.disc(cx, cz, 2, 10, 11, b('yellow_wool')); d.set(cx, 12, cz, SHL)
    d.landmark('giant_lotus', 'Giant Lotus', 'focal', (cx - 8, 8, cz - 8), (cx + 8, 16, cz + 8))
    # Mossbound Watchtower (focal): a ladder-up tower on the west side
    wx, wz = 150, 46
    S.tower(d, wx, wz, 4, 8, 18, MBR, floor=MBR, door='n', glow=SHL, roof=MBR)
    for y in range(8, 25): d.set(wx, y, wz + 3, kit.ladder('north'))
    d.fill(wx - 2, 26, wz - 2, wx + 2, 26, wz + 2, kit.AIR); d.fill(wx - 3, 26, wz - 3, wx + 3, 26, wz + 3, MBR) if False else None
    for (x, z) in S.pts_ring(wx, wz, 4, 8): d.set(x, 27, z, MBR)
    d.landmark('mossbound_watchtower', 'Mossbound Watchtower', 'focal', (wx - 5, 8, wz - 5), (wx + 5, 27, wz + 5))
    # Flowerbed Strongbox (discovery): a box buried in a raised bed on the east side
    fx, fz = 182, 18
    d.fill(fx - 3, 8, fz - 2, fx + 3, 8, fz + 2, MBR); d.fill(fx - 2, 8, fz - 1, fx + 2, 8, fz + 1, GRASS)
    for x in range(fx - 2, fx + 3):
        for z in range(fz - 1, fz + 2): d.set(x, 9, z, b(d.rng.choice(FLOWERS[:6])))
    d.add_chest((fx, 9, fz), 'south', 'loot')
    d.landmark('flowerbed_strongbox', 'Flowerbed Strongbox', 'discovery', (fx - 3, 8, fz - 2), (fx + 3, 9, fz + 2))
    # Pollination Garden (interactive): four flower pads at the compass points; order of the sun: E, S, W, N
    pads = {'N': (cx, 23), 'E': (cx + 9, cz), 'S': (cx, 41), 'W': (cx - 9, cz)}
    btn = []
    for key_ in ('N', 'E', 'S', 'W'):
        x, z = pads[key_]
        d.disc(x, z, 2, FLOOR, FLOOR, MOSS); d.set(x, 8, z, b('stone_button', face='floor', facing='north', powered=False))
        for (fx_, fz_) in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)): d.set(fx_, 8, fz_, b({'N': 'cornflower', 'E': 'poppy', 'S': 'dandelion', 'W': 'allium'}[key_]))
        btn.append((x, 8, z))
    d.landmark('pollination_garden', 'Pollination Garden', 'interactive', (cx - 11, 7, 21), (cx + 11, 9, 43))
    d.air(165, 8, 3, 167, 11, 9)
    d.add_chest((166, 8, 4), 'south', 'loot')
    for z in (3, 4, 5, 6, 7, 8): d.set(165, 7, z, GRASS); d.set(167, 7, z, GRASS); d.set(166, 7, z, GRASS)
    for z in (4, 6, 8): d.set(165, 8, z, b('fern')); d.set(167, 8, z, b('fern'))
    d.seal('fern_door', 'minecraft:mossy_cobblestone', (165, 8, 9), (167, 11, 10))
    d.set(166, 8, 6, b('lantern', hanging=False, waterlogged=False)); d.set(166, 8, 7, b('moss_carpet'))
    d.landmark('hidden_fern_tunnel', 'Hidden Fern Tunnel', 'discovery', (165, 8, 3), (167, 11, 10))
    C.sequence(d, 'pollination', btn, [1, 2, 3, 0], [C.clear(C.bx((165, 8, 9), (167, 11, 10))), C.sound((166, 9, 9), 'minecraft:block.moss.break', 2, 0.6),
                                                      C.say('A curtain of ferns parts in the north wall.', 'green')],
               'The flowers open in turn.', 'The pads fall quiet. Think of the sun.')
    # Seed Offering Shrine (interactive) with the Seed Vault gate behind it
    sx, sz = 166, 50
    d.fill(sx - 2, 8, sz - 1, sx + 2, 8, sz + 1, MBR) if False else None
    d.set(sx, 8, sz, b('composter'))
    for (x, z) in ((sx - 2, sz), (sx + 2, sz)): d.fill(x, 8, z, x, 11, z, SLOG); d.set(x, 12, z, SHL)
    d.air(sx - 3, 8, sz - 1, sx + 3, 8, sz + 1); d.fill(sx - 3, 7, sz - 1, sx + 3, 7, sz + 1, MBR); d.set(sx - 1, 8, sz - 1, b('moss_carpet')); d.set(sx + 1, 8, sz - 1, b('moss_carpet')); d.set(sx, 8, sz - 1, b('flower_pot'))
    d.set(sx, 8, sz, b('composter'))
    for x_ in (sx - 2, sx + 2): d.fill(x_, 8, sz, x_, 11, sz, SLOG); d.set(x_, 12, sz, SHL)
    d.landmark('seed_offering_shrine', 'Seed Offering Shrine', 'interactive', (sx - 3, 7, sz - 1), (sx + 3, 12, sz + 1))
    d.air(165, 8, 54, 167, 11, 67)
    d.seal('vault_hedge', 'minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]', (165, 8, 56), (167, 11, 57))
    C.lock(d, 'offering', C.bx((sx, 8, sz)), 'elder_seed', [C.clear(C.bx((165, 8, 56), (167, 11, 57))), C.sound((166, 9, 56), 'minecraft:block.grass.break', 2, 0.5),
                                                          C.say('The hedge withers back. Stairs of root lead down to the Seed Vault.', 'green')],
           'A shrine of living moss, a hollow for a seed.', 'plants the Elder Seed and the hedge withers away.')
    # arena door
    d.air(188, 8, 31, 195, 11, 33)
    d.seal('arena_door', 'minecraft:dark_oak_planks', (189, 8, 31), (190, 11, 33))
    C.lock(d, 'arena_door', C.bx((189, 8, 31), (190, 11, 33)), 'elder_key', [C.clear(C.bx((189, 8, 31), (190, 11, 33))),
                                                                          C.sound((189, 9, 32), 'minecraft:block.wood.break', 2, 0.5), C.say('The way to the Elderroot Sanctuary is open.', 'gold')],
           'A door of knotted roots with a keyhole of bark.', 'turns the Elder Key and the door of roots unknots.')
    C.encounter(d, 'R4', 'The flowers bloom in the order of the sun: east, south, west, north. A seed shrine waits in the south.',
                [C.mob('minecraft:zombie', 3, 'Overgrown Groundskeeper'), C.mob('minecraft:skeleton', 3, 'Thornbow')])
    d.room('R4', 'Bloom Court', (144, 8, 3), (188, 30, 54))


# ================================================================ R5 Seed Vault (x150..182, z67..98)
def vault(d):
    d.air(150, 8, 67, 182, 18, 98)
    d.air(165, 8, 58, 167, 11, 66)
    S.floor_pattern(d, 150, 67, 182, 98, FLOOR, [MOSS, MBR], 2)
    for (x, z) in [(154, 71), (178, 71), (154, 94), (178, 94), (166, 80)]:
        d.fill(x, 8, z, x + 1, 15, z + 1, DLOG); d.fill(x - 1, 16, z - 1, x + 2, 16, z + 2, LEAF)
        d.set(x, 12, z + 2, SHL)
    for x in range(154, 180, 6):
        for z in (76, 88):
            d.set(x, 8, z, b('pumpkin') if (x + z) % 12 else b('melon')); d.set(x + 1, 8, z, b('hay_block'))
    d.add_chest((166, 8, 95), 'north', 'key', key='elder_key')
    d.fill(164, 8, 96, 168, 9, 97, MBR)
    d.room('R5', 'Seed Vault', (150, 8, 58), (182, 18, 98))


# ================================================================ R6 Elderroot Sanctuary (centre 216,32 r22)
def arena(d):
    cx, cz = ARENA
    d.air(188, 8, 31, 195, 11, 33)
    d.disc(cx, cz, 22, 8, 32, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, MOSS)
    d.disc(cx, cz, 14, FLOOR, FLOOR, GRASS, rim=10)
    d.disc(cx, cz, 6, FLOOR + 1, FLOOR + 1, b('dark_oak_planks'))
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 13, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 24, z + 1, DLOG)
        d.fill(x - 1, 25, z - 1, x + 2, 26, z + 2, LEAF)
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, SHL)
            targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 20, 12): d.fill(x, 8, z, x, 14, z, SLOG); d.set(x, 15, z, SHL)
    d.disc(cx, cz, 22, 33, 33, LEAF)
    C.boss(d, 'elderroot_warden', 'R6', 'minecraft:ravager', 'The Elderroot Warden', (cx, 10, cz), targets=targets,
           on_block='minecraft:shroomlight', off_block='minecraft:moss_block', health=2.5, scale=1.3, color='GREEN',
           intro='The Elderroot Warden tears free of the roots. Strike the three bloom buds on the pillars to break its bark.',
           death='The Elderroot Warden withers and the sanctuary exhales.',
           break_text='The bark splits! Strike the Warden now!',
           adds=[C.mob('minecraft:spider', 2, 'Briar Crawler', 1.0) | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('Thorns burst from the floor.', 'red'), C.spawn('minecraft:spider', 2, 'R6', 'Briar Crawler')]},
                   {'at': 0.33, 'effects': [C.say('The sanctuary writhes.', 'red'), C.spawn('minecraft:zombie', 3, 'R6', 'Overgrown Groundskeeper')]}])
    C.pulse(d, 'sanctuary_thorns', box=C.bx((194, 8, 10), (238, 33, 54)), period=70, warn=28, count=2, radius=1.7, damage=3.5, kind='magic',
            warnParticle='minecraft:happy_villager', particle='minecraft:crit', sound='minecraft:block.sweet_berry_bush.pick_berries', after='boss_awake:elderroot_warden',
            until='boss:elderroot_warden', scatter=True, **{'target': 'players'})
    C.finish(d, 'elderroot_warden', 'The Elderroot Conservatory. Prune the gate, cross the ravine, and carry the seed to the Bloom Court.',
             'The roots go still. The conservatory closes shortly.')
    d.room('R6', 'Elderroot Sanctuary', (191, 8, 10), (238, 34, 54))
