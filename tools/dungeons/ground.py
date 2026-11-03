"""The Worldroot Fault: the Ground dungeon (id `ground`).

Rooms: Burrow Habitat -> Excavation Gallery -> Faultline Steps -> Dune Court -> Buried Vault; arena Worldroot Fault.
Layout : strata of sandstone; the Faultline Steps are a V-cut into the earth (steps down, a trench floor, steps up) around a tall strata pillar; the Dune
         Court is a bowl of sand dunes with a colossus half buried in it.
Hazard : Faultline Steps - the trench floor tiles crumble when stood on (a one-block drop) and tremors jolt the ground where you stand after a telegraph.
Puzzle : the Sandgate Control (two levers in order) opens the sand-gate to the fault; the Excavation Trial yields a Fossil Tool, offered at the court's Earth
         Offering to open the Buried Vault; its Worldroot Key opens the arena. Survey Alignment (levers matching sandstone/smooth clues) opens the Sealed Survey
         Vault; the Burrow Route Switch opens the Hidden Burrow Nest.
Boss   : the Worldroot Warden (husk) behind three raw-gold wards; tremors rack the arena while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever
import numpy as np

W, H, L = 300, 46, 140
FLOOR = 7
SS, RSS, SMS, CSS, TER = b('sandstone'), b('red_sandstone'), b('smooth_sandstone'), b('cut_sandstone'), b('terracotta')
SAND, DIRT, MUD, DOAK = b('sand'), b('coarse_dirt'), b('packed_mud'), b('dark_oak_log', axis='y')
OTER, YTER, BTER = b('orange_terracotta'), b('yellow_terracotta'), b('brown_terracotta')
RGB, GLOW = b('raw_gold_block'), b('glowstone')
COURT, ARENA = (214, 32), (272, 32)


def build():
    d = kit.Dungeon('ground', 'The Worldroot Fault', W, H, L, biome='minecraft:badlands')
    d.loot_table = 'ascensiondungeons:chests/sunscorched_necropolis'
    d.shell = ('sandstone', 'red_sandstone', 'terracotta', 'cut_sandstone', 'orange_terracotta', 'yellow_terracotta', 'brown_terracotta', 'coarse_dirt', 'packed_mud')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, SS)
    C.key(d, 'fossil_tool', 'minecraft:flint', 'Fossil Tool', 'A chipped blade older than the bones it freed. The Earth Offering in the Dune Court wants it.')
    C.key(d, 'worldroot_key', 'minecraft:tripwire_hook', 'Worldroot Key', 'Opens the root-barred door to the Worldroot Fault.')
    habitat(d); gallery(d); fault(d); dunes(d); tomb(d); arena(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R5', False), ('sandgate', 'R3', True), ('sandgate', 'R5', False), ('offering', 'R5', True),
                     ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, SS, [(RSS, 0.14), (OTER, 0.10), (YTER, 0.06), (BTER, 0.06), (DIRT, 0.05), (MUD, 0.05), (CSS, 0.05)])
    return d


# ================================================================ R1 Burrow Habitat (cave around 24,30)
def habitat(d):
    d.ellipsoid(24, 14, 30, 22, 13, 19, kit.AIR)
    d.fill(2, 0, 10, 48, FLOOR, 52, SS)
    for x in range(3, 48):
        for z in range(11, 52):
            if d.get(x, 8, z) == kit.AIR: d.set(x, FLOOR, z, d.rng.choice([SAND, SMS, SS, SAND, DIRT]))
    d.entry = (7, 8, 30)
    # Terraced Burrow Mound (focal): a stepped mound of dirt and mud, riddled with tunnel mouths
    mx, mz = 28, 28
    for k, r in enumerate((9, 7, 5, 3)):
        d.disc(mx, mz, r, 8 + k * 2, 9 + k * 2, MUD if k % 2 else DIRT)
    for (dx, dz) in ((9, 0), (-9, 0), (0, 9), (0, -9), (6, 6), (-6, -6)):
        x, z = mx + dx, mz + dz
        d.air(x - 1 if abs(dx) < 9 else x - (1 if dx < 0 else 0), 8, z, x + 1, 9, z + 1) if False else d.air(x, 8, z, x + (1 if dx >= 0 else -1), 9, z + (1 if dz >= 0 else -1))
    d.set(mx, 16, mz, GLOW)
    d.landmark('terraced_burrow_mound', 'Terraced Burrow Mound', 'focal', (mx - 10, 8, mz - 10), (mx + 10, 17, mz + 10))
    # Sandstone Root Arch (focal): a sandstone arch hung with dark oak roots
    ax = 12
    S.arch(d, ax, 22, 8, 8, 9, CSS, axis='z', depth=2)
    for k in range(8):
        d.fill(ax, 17 - (k % 3), 22 + k, ax + 1, 17, 22 + k, DOAK) if False else d.fill(ax, 15 - (k % 3) * 2, 23 + k, ax, 17, 23 + k, DOAK)
    d.set(ax, 18, 26, GLOW)
    d.landmark('sandstone_root_arch', 'Sandstone Root Arch', 'focal', (ax - 1, 8, 21), (ax + 2, 19, 31))
    # Prospector's Cache (discovery): a half-dug claim in the south-east
    px, pz = 40, 44
    d.fill(px - 3, 7, pz - 2, px + 3, 7, pz + 2, b('gravel')); d.set(px - 1, 8, pz, b('oak_fence')); d.set(px + 1, 8, pz, b('oak_fence'))
    d.add_chest((px, 8, pz + 1), 'north', 'loot'); d.fill(px - 3, 8, pz - 2, px - 3, 10, pz + 2, b('oak_fence')); d.set(px, 8, pz - 2, b('lantern', hanging=False, waterlogged=False))
    d.set(px + 2, 8, pz + 1, b('barrel', facing='up', open=False))
    d.landmark('prospector_s_cache', "Prospector's Cache", 'discovery', (px - 3, 7, pz - 2), (px + 3, 10, pz + 2))
    # Hidden Burrow Nest (discovery) behind the north wall, opened by the Burrow Route Switch
    nx, nz = 24, 8
    d.air(nx - 3, 8, 3, nx + 3, 11, 9)
    d.air(nx - 1, 8, 10, nx + 1, 11, 13)
    d.fill(nx - 3, 7, 3, nx + 3, 7, 9, DIRT); d.fill(nx - 1, 7, 10, nx + 1, 7, 13, DIRT)
    d.add_chest((nx, 8, 4), 'south', 'loot'); d.fill(nx - 2, 8, 4, nx - 2, 8, 6, b('hay_block')); d.set(nx + 2, 8, 5, b('candle', candles=3, lit=True, waterlogged=False))
    d.set(nx, 9, 6, b('lantern', hanging=False, waterlogged=False)) if False else d.set(nx + 2, 8, 7, b('lantern', hanging=False, waterlogged=False))
    d.seal('nest_wall', 'minecraft:coarse_dirt', (nx - 1, 8, 10), (nx + 1, 11, 11))
    d.landmark('hidden_burrow_nest', 'Hidden Burrow Nest', 'discovery', (nx - 3, 7, 3), (nx + 3, 11, 13))
    # Burrow Route Switch (interactive): one lever on a post near the entrance
    d.fill(14, 8, 36, 16, 11, 36, SMS)
    C.wall_lever(d, 15, 9, 35, 's') if False else None
    d.fill(13, 8, 38, 17, 11, 38, SMS)
    C.wall_lever(d, 15, 9, 37, 's')
    d.set(14, 8, 37, b('candle', candles=1, lit=True, waterlogged=False)) if False else d.set(14, 8, 37, b('cut_sandstone'))
    d.set(16, 8, 37, b('cut_sandstone')); d.set(15, 8, 37, b('chiseled_sandstone')); d.set(15, 10, 37, b('chiseled_sandstone'))
    d.landmark('burrow_route_switch', 'Burrow Route Switch', 'interactive', (13, 8, 36), (17, 11, 38))
    C.lever_seq(d, 'burrow', [(15, 9, 37)], [C.clear(C.bx((nx - 1, 8, 10), (nx + 1, 11, 11))), C.sound((nx, 9, 10), 'minecraft:block.gravel.break', 2, 0.6),
                                             C.say('Earth slumps away from a burrow mouth in the north wall.', 'gold')])
    d.air(44, 8, 29, 62, 11, 31)
    d.room('R1', 'Burrow Habitat', (2, 8, 3), (50, 26, 52))


# ================================================================ R2 Excavation Gallery (x62..120, z16..46)
def gallery(d):
    d.air(62, 8, 16, 120, 22, 46)
    S.floor_pattern(d, 62, 16, 120, 46, FLOOR, [SMS, CSS, OTER], 3)
    for x in range(66, 120, 6):
        d.fill(x, 8, 16, x, 21, 16, CSS); d.fill(x, 8, 46, x, 21, 46, CSS); d.set(x, 19, 17, b('lantern', hanging=True, waterlogged=False)) if False else None
    for x in range(68, 118, 10): d.set(x, 22, 31, GLOW)
    # Excavator Wheel (focal): a bucket-wheel excavator, wheel upright on an arm
    wx, wy, wz = 96, 14, 20
    d.fill(wx - 4, 8, wz - 1, wx + 4, 9, wz + 4, b('iron_block') if False else SMS)
    S.wheel(d, wx, wy, wz + 1, 6, 'z', b('stripped_dark_oak_log', axis='z'), b('dark_oak_planks'), b('iron_block'), thick=3)
    for k in range(12):
        a_ = k * math.pi / 6
        x, y = int(round(wx + 7 * math.cos(a_))), int(round(wy + 7 * math.sin(a_)))
        if y >= 8: d.set(x, y, wz + 1, b('oak_planks'))
    d.fill(wx, 8, wz + 3, wx, wy, wz + 3, b('iron_block')) if False else d.fill(wx - 1, 8, wz + 4, wx + 1, 12, wz + 4, SMS)
    d.landmark('excavator_wheel', 'Excavator Wheel', 'focal', (wx - 8, 8, wz - 1), (wx + 8, 22, wz + 5))
    # Fossil Pocket (discovery): bones in a wall pocket
    fx = 72
    S.nook(d, fx, 8, 16, 'n', depth=4, w=5, h=4)
    d.fill(fx - 2, 7, 12, fx + 2, 7, 15, DIRT)
    for x in range(fx - 2, fx + 3, 2): d.fill(x, 8, 12, x, 8 + (x % 3), 12, b('bone_block', axis='y'))
    d.add_chest((fx, 8, 13), 'south', 'loot'); d.set(fx - 2, 8, 14, b('lantern', hanging=False, waterlogged=False))
    d.landmark('fossil_pocket', 'Fossil Pocket', 'discovery', (fx - 2, 7, 12), (fx + 2, 11, 16))
    # Excavation Trial (interactive): a plate in a dig pit at the south-west
    px, pz = 74, 38
    d.disc(px, pz, 4, FLOOR, FLOOR, DIRT); d.disc(px, pz, 1, FLOOR, FLOOR, b('gravel'))
    d.set(px, 8, pz, b('stone_pressure_plate', powered=False))
    for (x, z) in S.pts_ring(px, pz, 4, 6): d.fill(x, 8, z, x, 10, z, CSS); d.set(x, 11, z, b('oak_fence'))
    d.landmark('excavation_trial', 'Excavation Trial', 'interactive', (px - 5, 7, pz - 5), (px + 5, 11, pz + 5))
    C.trial(d, 'excavation_trial', (px, 8, pz), 'R2',
            [{'say': 'The pit shakes. Things dig toward you.', 'spawns': [C.mob('minecraft:husk', 3, 'Dune Digger'), C.mob('minecraft:skeleton', 2, 'Sand Archer')]},
             {'say': 'The sand boils.', 'spawns': [C.mob('minecraft:husk', 4, 'Dune Digger'), C.mob('minecraft:spider', 2, 'Burrow Crawler')]}],
            [C.say('The shaking stops. A chipped Fossil Tool lies in the gravel.', 'gold'), C.give_key('fossil_tool', (px, 8, pz))])
    # Sandgate Control (interactive): two levers on the east end open the gate to the fault
    vx = 114
    for k, z in enumerate((24, 38)):
        d.fill(vx, 8, z - 1, vx + 1, 11, z + 1, CSS)
        C.wall_lever(d, vx - 1, 9, z, 'e')
    d.landmark('sandgate_control', 'Sandgate Control', 'interactive', (vx - 2, 8, 23), (vx + 1, 12, 39))
    d.air(120, 8, 29, 130, 11, 33)
    d.seal('sandgate', 'minecraft:sandstone', (122, 8, 29), (123, 11, 33))
    C.lever_seq(d, 'sandgate', [(vx - 1, 9, 24), (vx - 1, 9, 38)], [C.clear(C.bx((122, 8, 29), (123, 11, 33))), C.sound((122, 9, 31), 'minecraft:block.sand.break', 2, 0.5),
                                                                  C.say('The sandgate runs open like an hourglass. A fault yawns beyond.', 'gold')],
                on_step=[[C.sound((114, 9, 24), 'minecraft:block.piston.extend', 1.5, 0.7), C.say('Sand hisses behind the wall. The second lever.', 'gold')]],
                jam='The gate is packed solid. The north lever first.')
    C.encounter(d, 'R2', 'Wheels and spoil heaps. The diggers never left.',
                [C.mob('minecraft:husk', 3, 'Dune Digger'), C.mob('minecraft:skeleton', 3, 'Sand Archer'), C.mob('minecraft:spider', 2, 'Burrow Crawler')])
    d.room('R2', 'Excavation Gallery', (62, 8, 16), (121, 22, 46))


# ================================================================ R3 Faultline Steps (V-cut x124..188, strata pillar in the trench)
def fault(d):
    d.air(124, 2, 22, 188, 24, 42)
    d.fill(124, 0, 22, 188, 1, 42, SS)
    # steps down (x124..135, six steps of two), trench floor x136..174 (feet y2), steps up (x175..188)
    for i in range(6):
        top = 7 - i
        d.fill(124 + 2 * i, 1, 22, 125 + 2 * i, top, 42, RSS if i % 2 else SS)
        d.air(124 + 2 * i, top + 1, 22, 125 + 2 * i, 24, 42)
    d.fill(136, 1, 22, 174, 1, 42, TER)
    for i in range(6):
        top = 2 + i
        d.fill(175 + 2 * i, 1, 22, 176 + 2 * i, top, 42, RSS if i % 2 else SS)
        d.air(175 + 2 * i, top + 1, 22, 176 + 2 * i, 24, 42)
    d.fill(187, 1, 22, 188, 7, 42, SS)
    d.air(188, 8, 29, 198, 11, 35)
    d.air(124, 8, 29, 124, 11, 35)
    # Fault Pillar (focal): a tall column of coloured strata standing in the trench
    px, pz = 155, 32
    for k in range(18):
        mat = [SS, RSS, OTER, SMS, YTER, BTER][k % 6]
        r = 3 if k % 5 else 4
        d.disc(px, pz, r, 2 + k, 2 + k, mat)
    d.fill(px, 20, pz, px, 22, pz, GLOW) if False else d.set(px, 21, pz, GLOW)
    d.landmark('fault_pillar', 'Fault Pillar', 'focal', (px - 5, 2, pz - 5), (px + 5, 22, pz + 5))
    for x in range(126, 188, 8): d.set(x, 14, 22, GLOW); d.set(x, 14, 42, GLOW)
    # rubble in the trench
    S.rubble(d, 138, 24, 172, 40, 2, [SS, RSS, TER, b('gravel')], 24)
    C.crumble(d, 'fault_tiles', C.bx((136, 1, 22), (174, 1, 42)), only='terracotta', delay=22, restore=150, sound='minecraft:block.stone.break')
    cells = [[142, 2, 28], [148, 2, 36], [164, 2, 28], [168, 2, 36], [158, 2, 24]]
    C.pulse(d, 'tremors', cells=cells, period=84, warn=36, radius=2.2, damage=3.0, kind='fall', height=2.0, stagger=19,
            warnParticle='minecraft:poof', particle='minecraft:explosion', sound='minecraft:entity.generic.explode', vol=0.7, pitch=0.5, push=[0.0, 0.55, 0.0])
    d.room('R3', 'Faultline Steps', (124, 2, 22), (190, 24, 42))


# ================================================================ R4 Dune Court (centre 214,32 r22)
def dunes(d):
    cx, cz = COURT
    d.air(188, 8, 29, 192, 11, 35)
    d.disc(cx, cz, 22, 8, 26, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, SMS); d.disc(cx, cz, 20, FLOOR, FLOOR, SAND, rim=0) if False else d.disc(cx, cz, 20, FLOOR, FLOOR, SAND)
    # dunes: gentle mounds of sand on the sand floor (never over the doors)
    for k in range(14):
        x, z = d.rng.randint(cx - 17, cx + 17), d.rng.randint(cz - 17, cz + 17)
        if abs(z - cz) < 4 and abs(x - cx) > 12: continue
        if abs(x - cx) < 4 and abs(z - cz) > 12: continue
        if (x - cx) ** 2 + (z - cz) ** 2 > 18 ** 2 or (x - cx) ** 2 + (z - cz) ** 2 < 100: continue
        h = d.rng.randint(1, 3)
        for hh in range(h): d.disc(x, z, 4 - hh, 8 + hh, 8 + hh, SAND)
    for (x, z) in S.pts_ring(cx, cz, 21, 16, 0.2): d.fill(x, 8, z, x, 22, z, CSS); d.set(x, 15, z, GLOW)
    # Buried Colossus (focal): a giant head and one open hand rising from the sand
    d.ellipsoid(cx, 12, cz - 2, 6, 5, 6, SS); d.ellipsoid(cx, 12, cz - 2, 5, 4, 5, SS)
    d.fill(cx - 4, 12, cz - 8, cx + 4, 15, cz - 7, SS)
    d.fill(cx - 2, 13, cz - 8, cx - 1, 14, cz - 8, b('black_concrete')); d.fill(cx + 1, 13, cz - 8, cx + 2, 14, cz - 8, b('black_concrete'))
    d.fill(cx - 1, 11, cz - 8, cx + 1, 11, cz - 8, b('chiseled_sandstone'))
    d.fill(cx + 8, 8, cz + 4, cx + 12, 9, cz + 8, SS)
    for k in range(5): d.fill(cx + 8 + k, 10, cz + 4 + (k % 2) * 4, cx + 8 + k, 13 - k % 2, cz + 4 + (k % 2) * 4, SS)
    d.landmark('buried_colossus', 'Buried Colossus', 'focal', (cx - 7, 8, cz - 9), (cx + 13, 18, cz + 9))
    # Buried Supply Wagon (discovery): a wagon sinking into the dunes
    wx, wz = cx - 15, cz + 10
    d.fill(wx - 3, 8, wz - 2, wx + 3, 8, wz + 2, b('oak_planks')); d.fill(wx - 3, 9, wz - 2, wx + 3, 9, wz - 2, b('oak_planks')); d.fill(wx - 3, 9, wz + 2, wx + 3, 9, wz + 2, b('oak_planks'))
    d.fill(wx - 3, 9, wz - 2, wx - 3, 9, wz + 2, b('oak_planks'))
    d.add_chest((wx, 9, wz), 'east', 'loot'); d.set(wx + 1, 9, wz, b('barrel', facing='up', open=False))
    d.ellipsoid(wx - 4, 8, wz, 3, 2, 3, SAND, only_air=True)
    d.landmark('buried_supply_wagon', 'Buried Supply Wagon', 'discovery', (wx - 4, 8, wz - 3), (wx + 3, 10, wz + 3))
    # Survey Alignment (interactive): four survey posts under sandstone (on) / smooth (off) clues; opens the Sealed Survey Vault
    pats = [True, False, True, True]
    lv = []
    for k, x in enumerate((cx - 9, cx - 3, cx + 3, cx + 9)):
        z = cz - 19
        d.fill(x - 1, 8, z - 1, x + 1, 12, z - 1, CSS)
        C.wall_lever(d, x, 9, z, 'n')
        d.set(x, 11, z, SS if pats[k] else SMS); d.set(x, 8, z, b('chiseled_sandstone'))
        lv.append(((x, 9, z), pats[k]))
    d.landmark('survey_alignment', 'Survey Alignment', 'interactive', (cx - 11, 8, cz - 21), (cx + 11, 12, cz - 18))
    sx, sz = cx + 12, cz - 20
    d.air(sx, 8, sz - 6, sx + 6, 11, sz - 2)
    d.air(sx + 2, 8, sz - 1, sx + 4, 11, sz + 5)
    d.fill(sx, 7, sz - 6, sx + 6, 7, sz - 2, SMS); d.fill(sx + 2, 7, sz - 1, sx + 4, 7, sz + 5, SMS)
    d.add_chest((sx + 3, 8, sz - 6), 'south', 'loot'); d.set(sx + 1, 8, sz - 5, b('cartography_table')); d.set(sx + 5, 8, sz - 5, b('lectern', facing='west', powered=False))
    d.set(sx + 3, 8, sz - 3, b('lantern', hanging=False, waterlogged=False))
    d.seal('survey_wall', 'minecraft:sandstone', (sx + 2, 8, sz - 1), (sx + 4, 11, sz))
    d.landmark('sealed_survey_vault', 'Sealed Survey Vault', 'discovery', (sx, 7, sz - 6), (sx + 6, 11, sz + 1))
    C.toggle(d, 'survey', lv, [C.clear(C.bx((sx + 2, 8, sz - 1), (sx + 4, 11, sz))), C.sound((sx + 3, 9, sz - 1), 'minecraft:block.sand.break', 2, 0.6),
                               C.say('Sand drains from a survey vault in the north-east wall.', 'gold')],
             'The survey lines up.')
    # Earth Offering (interactive) in the south; the Buried Vault gate behind it
    ox, oz = cx, cz + 17
    d.fill(ox, 8, oz, ox, 9, oz, CSS); d.set(ox, 10, oz, b('chiseled_sandstone'))
    for x in (ox - 2, ox + 2): d.fill(x, 8, oz, x, 11, oz, SS); d.set(x, 12, oz, GLOW)
    d.fill(ox - 3, 7, oz - 1, ox + 3, 7, oz + 1, CSS); d.set(ox - 1, 8, oz - 1, b('chiseled_sandstone')); d.set(ox + 1, 8, oz - 1, b('chiseled_sandstone')); d.set(ox, 8, oz - 1, b('lantern', hanging=False, waterlogged=False))
    d.landmark('earth_offering', 'Earth Offering', 'interactive', (ox - 3, 7, oz - 1), (ox + 3, 12, oz + 1))
    d.air(cx - 1, 8, 54, cx + 1, 11, 66)
    d.seal('tomb_gate', 'minecraft:sandstone', (cx - 1, 8, 56), (cx + 1, 11, 57))
    C.lock(d, 'offering', C.bx((ox, 10, oz)), 'fossil_tool', [C.clear(C.bx((cx - 1, 8, 56), (cx + 1, 11, 57))), C.sound((cx, 9, 56), 'minecraft:block.sand.break', 2, 0.5),
                                                            C.say('The tool fits the groove and the sand pours out of a gate in the south wall.', 'gold')],
           'A block of carved sandstone with a groove for a blade.', 'sets the Fossil Tool in the groove and the gate runs open.')
    d.air(cx + 20, 8, 31, cx + 38, 11, 33)
    d.seal('arena_door', 'minecraft:sandstone', (cx + 23, 8, 31), (cx + 24, 11, 33))
    C.lock(d, 'arena_door', C.bx((cx + 23, 8, 31), (cx + 24, 11, 33)), 'worldroot_key', [C.clear(C.bx((cx + 23, 8, 31), (cx + 24, 11, 33))),
                                                                                         C.sound((cx + 23, 9, 32), 'minecraft:block.sand.break', 2, 0.4), C.say('The root-barred door to the Worldroot Fault opens.', 'gold')],
           'A door barred by a thick root, a keyhole where the root meets the stone.', 'turns the Worldroot Key and the root-barred door opens.')
    C.encounter(d, 'R4', 'Dunes drift in a windless room. Survey posts stand in the north; a grooved altar in the south.',
                [C.mob('minecraft:husk', 3, 'Dune Digger'), C.mob('minecraft:skeleton', 3, 'Sand Archer')])
    d.room('R4', 'Dune Court', (192, 8, 3), (238, 26, 54))


# ================================================================ R5 Buried Vault (x196..234, z68..100)
def tomb(d):
    cx = COURT[0]
    d.air(196, 8, 68, 232, 18, 100)
    d.air(cx - 1, 8, 58, cx + 1, 11, 68)
    S.floor_pattern(d, 196, 68, 232, 100, FLOOR, [SMS, CSS, OTER], 2)
    for x in range(200, 230, 6):
        for z in (74, 90):
            d.fill(x, 8, z, x + 1, 13, z + 1, CSS); d.set(x, 14, z, GLOW)
    d.fill(cx - 3, 8, 82, cx + 3, 9, 86, SMS); d.fill(cx - 2, 10, 83, cx + 2, 10, 85, CSS)         # a sarcophagus
    d.add_chest((cx, 8, 97), 'north', 'key', key='worldroot_key')
    d.fill(cx - 2, 8, 98, cx + 2, 10, 99, SS); d.set(cx - 2, 8, 97, b('candle', candles=3, lit=True, waterlogged=False)); d.set(cx + 2, 8, 97, b('candle', candles=3, lit=True, waterlogged=False))
    d.room('R5', 'Buried Vault', (196, 8, 58), (232, 18, 100))


# ================================================================ R6 Worldroot Fault (centre 272,32 r22)
def arena(d):
    cx, cz = ARENA
    d.air(236, 8, 31, 252, 11, 33)
    d.disc(cx, cz, 22, 8, 32, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, SS)
    d.disc(cx, cz, 19, FLOOR, FLOOR, RSS, rim=15); d.disc(cx, cz, 7, FLOOR, FLOOR, OTER, rim=5)
    # the fault: a fissure through the middle (feet y2) crossed by four stone causeways; root columns rise out of it
    xs, zs = np.arange(W)[None, :], np.arange(L)[:, None]
    fis = (np.abs(zs - cz) <= 3) & (np.abs(xs - cx) <= 14)
    for y in range(2, 8): d.a[y][fis] = d.pid(kit.AIR)
    d.a[1][fis] = d.pid(TER)
    for dx in (-8, 0, 8): d.fill(cx + dx - 1, 7, cz - 3, cx + dx + 1, 7, cz + 3, SMS)
    d.fill(cx - 14, 1, cz - 3, cx - 14, 6, cz + 3, SS) if False else None
    for dx in (-5, 5):
        d.fill(cx + dx, 2, cz - 1, cx + dx, 14, cz + 1, DOAK); d.fill(cx + dx - 1, 14, cz - 2, cx + dx + 1, 16, cz + 2, b('dark_oak_log', axis='x'))
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 15, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 22, z + 1, CSS)
        d.fill(x - 1, 23, z - 1, x + 2, 23, z + 2, RSS)
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, RGB); targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): d.fill(x, 8, z, x, 17, z, SS); d.set(x, 18, z, GLOW)
    C.boss(d, 'worldroot_warden', 'R6', 'minecraft:husk', 'The Worldroot Warden', (cx, 9, cz + 8), targets=targets,
           on_block='minecraft:raw_gold_block', off_block='minecraft:sandstone', health=8.0, scale=2.2, color='YELLOW',
           equipment={'mainhand': 'minecraft:iron_axe'},
           intro='The Worldroot Warden climbs out of the fault. Break the three raw-gold wards on the pillars to crack its crust.',
           death='The Worldroot Warden sinks back into the fault, root by root.', break_text='The wards crack! The Warden is exposed!',
           adds=[C.mob('minecraft:husk', 2, 'Dune Digger') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('The ground bucks.', 'red'), C.spawn('minecraft:husk', 2, 'R6', 'Dune Digger')]},
                   {'at': 0.33, 'effects': [C.say('Roots tear free of the fault.', 'red'), C.spawn('minecraft:spider', 2, 'R6', 'Burrow Crawler'), C.spawn('minecraft:skeleton', 2, 'R6', 'Sand Archer')]}])
    C.pulse(d, 'arena_tremors', box=C.bx((254, 8, 10), (292, 30, 54)), period=58, warn=34, count=2, radius=2.4, damage=3.5, kind='fall', height=2.0,
            warnParticle='minecraft:poof', particle='minecraft:explosion', sound='minecraft:entity.generic.explode', vol=0.7, pitch=0.5, push=[0.0, 0.55, 0.0],
            after='boss_awake:worldroot_warden', until='boss:worldroot_warden', scatter=True, **{'target': 'players'})
    C.finish(d, 'worldroot_warden', 'The Worldroot Fault. Open the sandgate and brave the fault steps.', 'The ground settles. The fault closes shortly.')
    d.room('R6', 'Worldroot Fault', (254, 2, 10), (292, 33, 54))
