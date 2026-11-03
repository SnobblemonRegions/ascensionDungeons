"""The Monarch's Hive: the Bug dungeon (id `bug`).

Rooms: Colony Habitat -> Silk Gallery -> Webbed Ravine -> Hive Court -> Amber Vault; arena Monarch Brood Chamber.
Layout : chambers are chains of overlapping ovoids (no straight halls): a hive, not a building.
Hazard : Webbed Ravine - the floor of the pit is a sheet of cobweb (slowness) and a missing silk span must be lowered by the Silk Bridge Winch; the
         spans sag away under you; stingers lance down where you stand after a telegraph (poison on top of the damage).
Puzzle : the Colony Route Switch opens the brood tunnel from the gallery; the Cocoon Trial yields Nectar, offered at the court to open the Amber Vault;
         its Brood Key opens the arena. The winch also opens the Hidden Larval Alcove.
Boss   : the Monarch (spider) behind three honey wards; stingers rain on the brood chamber while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever

W, H, L = 300, 46, 140
FLOOR = 7
BT, HC, OT, YT = b('brown_terracotta'), b('honeycomb_block'), b('orange_terracotta'), b('yellow_terracotta')
DRIP, MUD, WOOL, OWOOL, BWOOL = b('dripstone_block'), b('packed_mud'), b('white_wool'), b('orange_wool'), b('black_wool')
HONEY, WEB = b('honey_block'), b('cobweb')
AMB, GLOW = b('orange_stained_glass'), b('shroomlight')
COURT, ARENA = (206, 32), (264, 32)


def blob(d, cx, cy, cz, rx, ry, rz): d.ellipsoid(cx, cy, cz, rx, ry, rz, kit.AIR)


def build():
    d = kit.Dungeon('bug', "The Monarch's Hive", W, H, L, biome='minecraft:flower_forest')
    d.loot_table = 'ascensiondungeons:chests/fungal_grotto'
    d.shell = ('brown_terracotta', 'honeycomb_block', 'orange_terracotta', 'yellow_terracotta', 'dripstone_block', 'packed_mud')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, BT)
    C.key(d, 'nectar', 'minecraft:honey_bottle', 'Hive Nectar', 'Sweet and thick. The Nectar Offering in the Hive Court wants it.')
    C.key(d, 'brood_key', 'minecraft:tripwire_hook', 'Brood Key', 'Opens the waxen door to the Monarch Brood Chamber.')
    habitat(d); silk(d); ravine(d); court(d); amber(d); brood(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R5', False), ('route', 'R3', True), ('route', 'R4', False), ('winch', 'R4', True),
                     ('winch', 'R5', False), ('offering', 'R5', True), ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, BT, [(HC, 0.14), (OT, 0.10), (YT, 0.06), (DRIP, 0.05), (MUD, 0.06)])
    return d


def floor_honey(d, x1, z1, x2, z2):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if d.get(x, 8, z) == kit.AIR: d.set(x, FLOOR, z, d.rng.choice([HC, BT, OT, HC, YT]))


# ================================================================ R1 Colony Habitat (ovoid chain around 12..44, 30)
def habitat(d):
    blob(d, 22, 12, 30, 20, 11, 17); blob(d, 40, 11, 24, 12, 9, 11); blob(d, 36, 11, 42, 11, 9, 9)
    d.fill(2, 0, 10, 54, FLOOR, 54, BT)
    floor_honey(d, 2, 10, 54, 54)
    d.entry = (6, 8, 30)
    # Colony Mound (focal): a mud-and-wax mound with tunnel mouths
    mx, mz = 24, 30
    for k, (r, h) in enumerate(((7, 3), (5, 3), (3, 3))):
        d.disc(mx, mz, r, 8 + sum(x[1] for x in ((7, 3), (5, 3), (3, 3))[:k]), 8 + sum(x[1] for x in ((7, 3), (5, 3), (3, 3))[:k + 1]) - 1, MUD if k != 1 else HC)
    d.air(mx - 7, 8, mz, mx - 5, 9, mz); d.air(mx + 5, 8, mz, mx + 7, 9, mz); d.air(mx, 8, mz - 7, mx, 9, mz - 5)
    d.disc(mx, mz, 1, 17, 17, GLOW)
    d.landmark('colony_mound', 'Colony Mound', 'focal', (mx - 8, 8, mz - 8), (mx + 8, 18, mz + 8))
    # Giant Chrysalis (focal): a green ovoid hung by silk from the roof
    cx, cz = 40, 24
    d.ellipsoid(cx, 14, cz, 3, 6, 3, b('green_wool')); d.ellipsoid(cx, 14, cz, 2, 5, 2, b('lime_wool'))
    d.fill(cx, 20, cz, cx, 22, cz, b('chain', axis='y', waterlogged=False)) if False else d.fill(cx, 21, cz, cx, 22, cz, WEB)
    for k in range(5): d.set(cx + (k % 2) * 2 - 1, 8 + k * 2, cz + 3, b('brown_wool'))
    d.landmark('giant_chrysalis', 'Giant Chrysalis', 'focal', (cx - 4, 8, cz - 4), (cx + 4, 22, cz + 4))
    # Naturalist's Camp (discovery)
    gx, gz = 12, 44
    S.pyramid(d, gx, gz, 8, 3, [b('green_wool'), b('lime_wool')]); d.air(gx - 1, 8, gz - 1, gx + 1, 9, gz + 1)
    d.set(gx + 4, 8, gz, b('crafting_table')); d.set(gx + 4, 8, gz + 1, b('cartography_table')); d.add_chest((gx, 8, gz), 'east', 'loot')
    d.set(gx + 3, 8, gz - 2, b('azalea', ) if False else b('flower_pot'))
    d.landmark('naturalist_s_camp', "Naturalist's Camp", 'discovery', (gx - 3, 8, gz - 3), (gx + 5, 11, gz + 3))
    # Pollinator Station (interactive): flower beds round a hive block; rest and a taste of honey
    px, pz = 42, 44
    d.disc(px, pz, 4, FLOOR, FLOOR, b('grass_block', snowy=False))
    for (x, z) in S.pts_ring(px, pz, 3, 8): d.set(x, 8, z, b(['dandelion', 'poppy', 'cornflower', 'allium'][(x + z) % 4]))
    d.set(px, 8, pz, b('bee_nest', facing='north', honey_level=0)); d.set(px, 9, pz, b('beehive', facing='north', honey_level=0)) if False else None
    d.landmark('pollinator_station', 'Pollinator Station', 'interactive', (px - 5, 7, pz - 5), (px + 5, 10, pz + 5))
    C.zone(d, 'pollinator_rest', C.bx((px - 5, 8, pz - 5), (px + 5, 11, pz + 5)), 'minecraft:regeneration', 0, 80, 40)
    C.zone(d, 'pollinator_sugar', C.bx((px - 5, 8, pz - 5), (px + 5, 11, pz + 5)), 'minecraft:speed', 0, 160, 80)
    blob(d, 56, 9, 30, 8, 3, 2)
    d.air(50, 8, 29, 66, 11, 31)
    d.room('R1', 'Colony Habitat', (2, 8, 10), (54, 24, 54))


# ================================================================ R2 Silk Gallery (ovoid chain along x68..122)
def silk(d):
    for k in range(5):
        blob(d, 72 + k * 10, 12, 31 + (k % 2) * 3 - 1, 7, 8, 12)
    d.fill(62, 0, 14, 126, FLOOR, 50, BT)
    floor_honey(d, 62, 14, 126, 50)
    # Silk Canopy (focal): a ceiling of web and wool strands hanging down in long veils
    for x in range(68, 124, 3):
        for z in range(22, 42, 3):
            h = 3 + (x * z) % 5
            for y in range(19 - h, 20):
                if d.get(x, y, z) == kit.AIR: d.set(x, y, z, WEB if y % 2 else WOOL)
    d.landmark('silk_canopy', 'Silk Canopy', 'focal', (68, 8, 22), (122, 20, 42))
    # Hollow Hive Nest (discovery): a wax pocket in the north wall
    nx = 88
    blob(d, nx, 10, 18, 4, 3, 4)
    d.fill(nx - 3, 7, 14, nx + 3, 7, 20, HC)
    d.set(nx, 8, 16, b('bee_nest', facing='south', honey_level=0)); d.add_chest((nx + 2, 8, 16), 'west', 'loot'); d.set(nx - 2, 8, 16, b('honey_block'))
    d.landmark('hollow_hive_nest', 'Hollow Hive Nest', 'discovery', (nx - 4, 7, 14), (nx + 4, 12, 20))
    # Cocoon Trial (interactive): a plate ringed by wool cocoons at the west end
    px, pz = 74, 32
    d.disc(px, pz, 4, FLOOR, FLOOR, HC); d.disc(px, pz, 1, FLOOR, FLOOR, OT)
    d.set(px, 8, pz, b('light_weighted_pressure_plate', power=0))
    for (x, z) in S.pts_ring(px, pz, 4, 6):
        d.ellipsoid(x, 10, z, 1, 2, 1, WOOL, only_air=True)
    d.landmark('cocoon_trial', 'Cocoon Trial', 'interactive', (px - 5, 7, pz - 5), (px + 5, 13, pz + 5))
    C.trial(d, 'cocoon_trial', (px, 8, pz), 'R2',
            [{'say': 'The cocoons split. Chitin clicks in the dark.', 'spawns': [C.mob('minecraft:spider', 3, 'Hive Drone'), C.mob('minecraft:zombie', 2, 'Husked Forager')]},
             {'say': 'A second brood hatches.', 'spawns': [C.mob('minecraft:spider', 4, 'Hive Drone'), C.mob('minecraft:cave_spider', 2, 'Stinger')]}],
            [C.say('The brood falls still. A bottle of Hive Nectar sits in the wax.', 'gold'), C.give_key('nectar', (px, 8, pz))])
    # Colony Route Switch (interactive): a lever in a wax alcove at the east end; opens the brood tunnel to the ravine
    sx, sz = 116, 40
    blob(d, sx, 10, sz + 2, 3, 3, 3)
    d.fill(sx - 2, 7, sz - 1, sx + 2, 7, sz + 4, HC)
    d.fill(sx - 1, 8, sz + 4, sx + 1, 10, sz + 5, BT) if False else None
    d.set(sx, 9, sz + 4, lever('wall', 'north'))
    d.fill(sx, 8, sz + 4, sx, 8, sz + 4, HC) if False else None
    d.landmark('colony_route_switch', 'Colony Route Switch', 'interactive', (sx - 2, 7, sz - 1), (sx + 2, 11, sz + 5))
    d.air(114, 8, 30, 132, 11, 33)
    d.seal('brood_wax', 'minecraft:honeycomb_block', (124, 8, 30), (125, 11, 33))
    C.lever_seq(d, 'route', [(sx, 9, sz + 4)], [C.clear(C.bx((124, 8, 30), (125, 11, 33))), C.sound((124, 9, 31), 'minecraft:block.honey_block.break', 2, 0.6),
                                                C.say('The wax wall melts back. A brood tunnel runs on toward a webbed ravine.', 'gold')])
    C.encounter(d, 'R2', 'Silk veils hang from the ceiling. Something is pulling them tight.',
                [C.mob('minecraft:spider', 3, 'Hive Drone'), C.mob('minecraft:cave_spider', 3, 'Stinger'), C.mob('minecraft:zombie', 2, 'Husked Forager')])
    d.room('R2', 'Silk Gallery', (62, 8, 14), (122, 22, 50))


# ================================================================ R3 Webbed Ravine (oval pit centre 152,32; spans along z31..33)
def ravine(d):
    cx, cz = 154, 32
    import numpy as np
    xs, zs = np.arange(W)[None, :], np.arange(L)[:, None]
    mask = ((xs - cx) / 28.0) ** 2 + ((zs - cz) / 22.0) ** 2 <= 1.0
    for y in range(2, 36): d.a[y][mask] = d.pid(kit.AIR)
    d.fill(125, 2, 10, 183, 2, 54, b('moss_block')) if False else None
    d.air(114, 8, 30, 132, 11, 33); d.air(126, 8, 28, 128, 11, 36)
    for x in range(128, 182):
        for z in range(11, 54):
            if d.get(x, 2, z) == kit.AIR and ((x - cx) / 28.0) ** 2 + ((z - cz) / 22.0) ** 2 < 0.8: d.set(x, 2, z, WEB if d.rng.random() < 0.4 else kit.AIR)
    # islands and spans at z31..33 (tops feet y8): west rim -> island (x146..162) -> east rim; the first span is absent until the winch
    d.fill(146, 1, 26, 162, 7, 38, BT); d.fill(146, 7, 26, 162, 7, 38, HC)
    for x in range(126, 146):
        d.fill(x, 7, 31, x, 7, 33, WOOL if x % 3 else b('light_gray_wool'))
    d.air(133, 7, 31, 145, 7, 33)                                       # the missing span (lowered by the winch)
    for x in range(163, 182): d.fill(x, 7, 31, x, 7, 33, WOOL if x % 3 else b('light_gray_wool'))
    d.fill(182, 1, 26, 186, 7, 38, BT)
    d.air(182, 8, 28, 192, 11, 36)
    for y in range(2, 8): d.set(128, y, 29, kit.ladder('east')) if False else None
    for y in range(2, 8): d.set(127, y, 29, kit.ladder('east')); d.set(181, y, 35, kit.ladder('west')); d.set(145, y, 34, kit.ladder('west'))
    for x in (144, 164): d.fill(x, 2, 8, x, 6, 56, b('iron_bars'))   # fallers stay on the side they fell from
    # Silk-Wrapped Strongbox (discovery) on the island, bundled in cobweb and wool
    bx, bz = 154, 34
    d.fill(bx - 1, 8, bz - 1, bx + 1, 9, bz + 1, WOOL); d.air(bx, 8, bz, bx, 8, bz)
    d.add_chest((bx, 8, bz), 'north', 'loot'); d.set(bx, 9, bz, WEB); d.set(bx - 1, 8, bz, WEB) if False else None
    d.air(bx, 8, bz - 1, bx, 8, bz - 1)
    d.landmark('silk_wrapped_strongbox', 'Silk-Wrapped Strongbox', 'discovery', (bx - 2, 8, bz - 2), (bx + 2, 10, bz + 2))
    # Silk Bridge Winch (interactive) on the west rim: two levers, lowers the span and opens the Hidden Larval Alcove in the court
    wx, wz = 124, 27
    d.fill(wx - 1, 8, wz - 2, wx - 1, 11, wz + 2, BT) if False else None
    d.fill(124, 8, 27, 128, 11, 27, HC)
    d.set(125, 9, 28, lever('wall', 'south')); d.set(127, 9, 28, lever('wall', 'south'))
    d.landmark('silk_bridge_winch', 'Silk Bridge Winch', 'interactive', (124, 8, 27), (128, 12, 29))
    C.lever_seq(d, 'winch', [(125, 9, 28), (127, 9, 28)], [C.fill(C.bx((133, 7, 31), (145, 7, 33)), 'minecraft:white_wool'), C.sound((139, 8, 32), 'minecraft:block.wool.place', 2, 0.6),
                                                           C.say('Silk spools out across the pit. The span is down.', 'gold'),
                                                           C.clear(C.bx((196, 8, 19), (197, 11, 21)))],
                on_step=[[C.sound((125, 9, 28), 'minecraft:block.piston.extend', 1.5, 0.8), C.say('The winch clicks. The second lever.', 'gold')]],
                jam='The winch is stuck. The west lever first.')
    for (x, z) in S.pts_ring(cx, cz, 25, 12, 0.3): d.set(x, 16, z, GLOW)
    C.zone(d, 'web_floor', C.bx((125, 2, 10), (183, 3, 54)), 'minecraft:slowness', 2, 40, 10)
    C.crumble(d, 'silk_sag', C.bx((126, 7, 31), (181, 7, 33)), only='white_wool', delay=24, restore=170, sound='minecraft:block.wool.break')
    C.pulse(d, 'stingers', box=C.bx((125, 2, 10), (183, 34, 54)), period=78, warn=32, count=1, radius=1.6, damage=2.5, kind='magic',
            warnParticle='minecraft:crit', particle='minecraft:damage_indicator', effect='minecraft:poison', duration=100, amp=0,
            sound='minecraft:entity.bee.sting', vol=1.2, pitch=1.0, scatter=False, **{'target': 'players'})
    d.room('R3', 'Webbed Ravine', (126, 2, 8), (186, 34, 56))


# ================================================================ R4 Hive Court (centre 206,32 r22, honeycomb)
def court(d):
    cx, cz = COURT
    d.air(186, 8, 28, 192, 11, 36)
    d.disc(cx, cz, 22, 8, 26, kit.AIR)
    for r, mat in ((22, YT), (18, HC), (12, OT), (7, HC)): d.disc(cx, cz, r, FLOOR, FLOOR, mat, rim=r - 3)
    for (x, z) in S.pts_ring(cx, cz, 21, 16, 0.2): d.fill(x, 8, z, x, 22, z, b('honeycomb_block')); d.set(x, 14, z, GLOW)
    # Monarch Wing Monument (focal): two enormous butterfly wings on a stem
    wx, wz = cx, cz - 15
    d.fill(wx, 8, wz, wx, 20, wz, b('stripped_oak_log', axis='y'))
    for side in (-1, 1):
        for t in range(0, 12):
            for h in range(0, 9 - t // 2):
                x = wx + side * (2 + t); y = 12 + h + (t // 3)
                col = OWOOL if (t + h) % 4 else BWOOL
                d.set(x, y, wz, col)
        for t in range(0, 8):
            for h in range(0, 5 - t // 2):
                d.set(wx + side * (2 + t), 11 - h, wz, OWOOL if (t + h) % 3 else BWOOL)
    d.landmark('monarch_wing_monument', 'Monarch Wing Monument', 'focal', (wx - 14, 8, wz - 1), (wx + 14, 22, wz + 1))
    # Hidden Larval Alcove (discovery): a nursery behind the east-north wall, opened by the winch
    d.air(194, 8, 17, 200, 11, 23)
    d.fill(194, 7, 17, 200, 7, 23, HC)
    d.fill(195, 8, 18, 199, 8, 18, b('hay_block')); d.set(197, 9, 18, WOOL)
    d.add_chest((197, 8, 22), 'south', 'loot'); d.set(195, 8, 22, b('candle', candles=3, lit=True, waterlogged=False)); d.set(199, 8, 22, b('candle', candles=2, lit=True, waterlogged=False))
    d.seal('larval_wax', 'minecraft:honeycomb_block', (196, 8, 19), (197, 11, 21))
    d.landmark('hidden_larval_alcove', 'Hidden Larval Alcove', 'discovery', (194, 7, 17), (200, 11, 23))
    # Nectar Offering (interactive) in the south, with the Amber Vault gate behind
    ox, oz = cx, cz + 17
    d.fill(ox, 8, oz, ox, 9, oz, HC); d.set(ox, 10, oz, b('honey_block'))
    for x in (ox - 2, ox + 2): d.fill(x, 8, oz, x, 11, oz, YT); d.set(x, 12, oz, GLOW)
    d.landmark('nectar_offering', 'Nectar Offering', 'interactive', (ox - 2, 8, oz - 1), (ox + 2, 12, oz + 1))
    d.air(cx - 1, 8, 54, cx + 1, 11, 66)
    d.seal('amber_wax', 'minecraft:honeycomb_block', (cx - 1, 8, 56), (cx + 1, 11, 57))
    C.lock(d, 'offering', C.bx((ox, 10, oz)), 'nectar', [C.clear(C.bx((cx - 1, 8, 56), (cx + 1, 11, 57))), C.sound((cx, 9, 56), 'minecraft:block.honey_block.break', 2, 0.5),
                                                          C.say('The nectar spreads into the wax and a sweet wall melts away in the south.', 'gold')],
           'A basin of honey comb, dry, waiting for nectar.', 'pours the Nectar into the basin and the wax wall melts.')
    d.air(cx + 20, 8, 31, cx + 38, 11, 33)
    d.seal('arena_door', 'minecraft:honeycomb_block', (cx + 23, 8, 31), (cx + 24, 11, 33))
    C.lock(d, 'arena_door', C.bx((cx + 23, 8, 31), (cx + 24, 11, 33)), 'brood_key', [C.clear(C.bx((cx + 23, 8, 31), (cx + 24, 11, 33))),
                                                                                     C.sound((cx + 23, 9, 32), 'minecraft:block.honey_block.break', 2, 0.5), C.say('The waxen door to the Monarch Brood Chamber parts.', 'gold')],
           'A waxen door sealed with amber, a keyhole shaped like a mandible.', 'turns the Brood Key and the waxen door parts.')
    C.encounter(d, 'R4', 'The court hums. A basin of dry comb waits in the south.',
                [C.mob('minecraft:spider', 3, 'Hive Drone'), C.mob('minecraft:cave_spider', 3, 'Stinger')])
    d.room('R4', 'Hive Court', (186, 8, 8), (228, 26, 54))


# ================================================================ R5 Amber Vault (ovoid chain south of the court)
def amber(d):
    cx = COURT[0]
    for k in range(3): blob(d, 190 + k * 18, 12, 82, 12, 9, 14)
    d.fill(176, 0, 66, 236, FLOOR, 100, BT)
    floor_honey(d, 176, 66, 236, 100)
    d.air(cx - 1, 8, 58, cx + 1, 11, 69)
    # Amber Tree (focal): a trunk of honeycomb with a canopy of amber glass and honey
    tx, tz = cx, 82
    d.fill(tx - 1, 8, tz - 1, tx + 1, 16, tz + 1, HC); d.fill(tx, 17, tz, tx, 20, tz, HC)
    d.ellipsoid(tx, 19, tz, 7, 4, 7, AMB, only_air=True)
    for (x, z) in S.pts_ring(tx, tz, 5, 8): d.fill(x, 15, z, x, 18, z, b('honey_block') if False else AMB)
    d.set(tx, 20, tz, GLOW)
    d.landmark('amber_tree', 'Amber Tree', 'focal', (tx - 7, 8, tz - 7), (tx + 7, 23, tz + 7))
    # Amber Cache (discovery) is the key plinth
    d.air(cx - 3, 8, 93, cx + 3, 12, 98)
    d.add_chest((cx, 8, 96), 'north', 'key', key='brood_key')
    d.fill(cx - 2, 8, 97, cx + 2, 10, 98, AMB) if False else d.fill(cx - 2, 8, 97, cx + 2, 9, 98, HC)
    d.set(cx - 2, 8, 96, b('candle', candles=3, lit=True, waterlogged=False)); d.set(cx + 2, 8, 96, b('candle', candles=3, lit=True, waterlogged=False))
    d.landmark('amber_cache', 'Amber Cache', 'discovery', (cx - 3, 8, 94), (cx + 3, 10, 98))
    d.room('R5', 'Amber Vault', (176, 8, 58), (236, 22, 100))


# ================================================================ R6 Monarch Brood Chamber (centre 264,32 r22)
def brood(d):
    cx, cz = ARENA
    d.air(228, 8, 31, 244, 11, 33)
    d.disc(cx, cz, 22, 8, 30, kit.AIR)
    for r, mat in ((22, YT), (19, HC), (13, OT)): d.disc(cx, cz, r, FLOOR, FLOOR, mat, rim=r - 3)
    d.disc(cx, cz, 8, 8, 8, HC); d.disc(cx, cz, 5, 8, 9, OT)
    for (x, z) in S.pts_ring(cx, cz, 18, 8, 0.2):
        d.ellipsoid(x, 11, z, 2, 3, 2, HC, only_air=True); d.air(x, 8, z, x, 12, z); d.set(x, 8, z, b('honey_block')) if False else None
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 13, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 22, z + 1, b('honeycomb_block'))
        d.fill(x - 1, 23, z - 1, x + 2, 23, z + 2, OT)
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, HONEY); targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): d.fill(x, 8, z, x, 16, z, DRIP); d.set(x, 17, z, GLOW)
    C.boss(d, 'monarch', 'R6', 'minecraft:spider', 'The Monarch', (cx, 9, cz), targets=targets,
           on_block='minecraft:honey_block', off_block='minecraft:terracotta', health=5.0, scale=2.4, color='YELLOW',
           intro='The Monarch unfolds from its throne of comb. Break the three honey wards on the pillars to crack its chitin.',
           death='The Monarch curls up and the hive goes silent.', break_text='The wards crack! The Monarch is exposed!',
           adds=[C.mob('minecraft:cave_spider', 2, 'Stinger') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('The brood cells split open.', 'red'), C.spawn('minecraft:cave_spider', 3, 'R6', 'Stinger')]},
                   {'at': 0.33, 'effects': [C.say('The Monarch calls every drone.', 'red'), C.spawn('minecraft:spider', 2, 'R6', 'Hive Drone')]}])
    C.pulse(d, 'brood_stingers', box=C.bx((246, 8, 10), (286, 30, 54)), period=60, warn=28, count=2, radius=1.9, damage=3.0, kind='magic',
            warnParticle='minecraft:crit', particle='minecraft:damage_indicator', effect='minecraft:poison', duration=100, amp=0,
            sound='minecraft:entity.bee.sting', after='boss_awake:monarch', until='boss:monarch', scatter=True, **{'target': 'players'})
    C.finish(d, 'monarch', "The Monarch's Hive. Open the brood tunnel, lower the silk span, and carry nectar to the court.", 'The hum fades. The hive closes shortly.')
    d.room('R6', 'Monarch Brood Chamber', (246, 8, 10), (286, 31, 54))
