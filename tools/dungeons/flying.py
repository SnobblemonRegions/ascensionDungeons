"""The Gale Eyrie: the Flying dungeon (id `flying`).

Rooms: High-Roof Aviary -> Wind Gallery -> Skybridge Chasm -> Updraft Court -> Cliffside Vault; arena Eye of the Gale.
Layout : a cloud-floored chasm of islands. The first skybridge span is MISSING until the Bridge Winch (two levers, in order) lowers it;
         the second span crumbles under you and gusts shove you sideways. Falls land on the cloud floor (6 blocks) and ladders lead back.
Puzzle : Updraft Court - ride the central updraft (a levitation column) to the upper ring, where three vane levers must match the
         blue/white wool clues above them to open the Cliffside Vault; its Gale Key opens the arena.
Extra  : the Beacon Trial (island B) yields a Feather Token; the Feather Offering opens the Windward Map Niche in the court wall.
Boss   : the Gale Warden (breeze) behind three wind-vane wards; gusts throw players about the arena while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever

W, H, L = 290, 50, 140
FLOOR = 7
WT, LGT, CAL, QB = b('white_terracotta'), b('light_gray_terracotta'), b('calcite'), b('quartz_bricks')
WOOL, LBW, CLOUD = b('white_wool'), b('light_blue_wool'), b('white_wool')
PLK, SPL = b('oak_planks'), b('spruce_planks')
SL, GLOW = b('sea_lantern'), b('glowstone')
COURT, ARENA = (206, 32), (264, 32)


def build():
    d = kit.Dungeon('flying', 'The Gale Eyrie', W, H, L, biome='minecraft:windswept_hills')
    d.loot_table = 'ascensiondungeons:chests/lapis_observatory'
    d.shell = ('white_terracotta', 'light_gray_terracotta', 'calcite', 'quartz_bricks', 'white_wool')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, WT)
    C.key(d, 'feather_token', 'minecraft:feather', 'Feather Token', 'Weightless. The Feather Offering in the Updraft Court wants it.')
    C.key(d, 'gale_key', 'minecraft:tripwire_hook', 'Gale Key', 'Opens the wind-sealed door to the Eye of the Gale.')
    aviary(d); gallery(d); chasm(d); court(d); vault(d); eye(d)
    d.order_rules = [('start', 'R4', False), ('start', 'R5', False), ('winch', 'R4', True), ('winch', 'R5', False), ('airflow', 'R5', True),
                     ('airflow', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, WT, [(LGT, 0.18), (CAL, 0.12), (QB, 0.06)])
    return d


# ================================================================ R1 High-Roof Aviary (dome around 28,30)
def aviary(d):
    d.ellipsoid(28, 12, 30, 24, 22, 24, kit.AIR)
    d.fill(2, 0, 4, 54, FLOOR, 56, WT)
    S.floor_pattern(d, 4, 6, 52, 54, FLOOR, [CAL, LGT], 4)
    d.entry = (8, 8, 30)
    # skylights
    for (x, z) in S.pts_ring(28, 30, 8, 6): d.set(x, 33, z, b('light_blue_stained_glass')) if d.get(x, 33, z) != kit.AIR else None
    # perches: pillars with wooden crossbars
    for (x, z, h) in [(14, 18, 9), (42, 18, 12), (14, 42, 11), (42, 42, 8), (28, 14, 14)]:
        d.fill(x, 8, z, x, 8 + h, z, b('stripped_oak_log', axis='y')); d.fill(x - 2, 9 + h, z, x + 2, 9 + h, z, b('oak_log', axis='x'))
        d.set(x, 10 + h, z, b('lantern', hanging=False, waterlogged=False))
    # Suspended Aviary (focal): a cage of bars hung on chains over the middle
    ax, az = 28, 30
    d.fill(ax - 4, 22, az - 4, ax + 4, 22, az + 4, QB)
    d.fill(ax - 4, 23, az - 4, ax + 4, 28, az + 4, b('iron_bars'))
    d.air(ax - 3, 23, az - 3, ax + 3, 27, az + 3)
    d.fill(ax - 4, 29, az - 4, ax + 4, 29, az + 4, QB)
    for (cx_, cz_) in ((ax - 4, az - 4), (ax + 4, az - 4), (ax - 4, az + 4), (ax + 4, az + 4)):
        d.fill(cx_, 30, cz_, cx_, 33, cz_, b('chain', axis='y', waterlogged=False))
    d.set(ax, 24, az, b('oak_log', axis='x')); d.fill(ax - 2, 24, az, ax + 2, 24, az, b('oak_log', axis='x'))
    d.landmark('suspended_aviary', 'Suspended Aviary', 'focal', (ax - 4, 8, az - 4), (ax + 4, 33, az + 4))
    # Abandoned Glider Camp (discovery): a lean-to and a wooden glider frame
    gx, gz = 12, 44
    S.pyramid(d, gx, gz, 8, 3, [b('white_wool'), b('light_blue_wool')]); d.air(gx - 1, 8, gz - 1, gx + 1, 9, gz + 1)
    d.fill(gx + 5, 8, gz, gx + 5, 11, gz, b('oak_fence')); d.fill(gx + 5, 11, gz - 3, gx + 5, 11, gz + 3, b('oak_planks'))
    d.fill(gx + 5, 12, gz - 3, gx + 5, 12, gz - 2, WOOL); d.fill(gx + 5, 12, gz + 2, gx + 5, 12, gz + 3, WOOL)
    d.add_chest((gx, 8, gz), 'east', 'loot'); d.set(gx - 2, 8, gz + 2, b('barrel', facing='up', open=False))
    d.landmark('abandoned_glider_camp', 'Abandoned Glider Camp', 'discovery', (gx - 3, 8, gz - 3), (gx + 6, 12, gz + 3))
    # Roost Steward Station (interactive): a hut where wings feel lighter
    hx, hz = 44, 46
    S.hut(d, hx - 3, hz - 3, hx + 3, hz + 3, 8, 4, SPL, PLK, door='w', floor=PLK)
    d.set(hx + 2, 8, hz + 2, b('barrel', facing='up', open=False)); d.set(hx + 2, 8, hz - 2, b('hay_block')); d.set(hx, 8, hz - 2, b('loom', facing='south')) if False else d.set(hx, 8, hz - 2, b('crafting_table'))
    d.set(hx, 11, hz, b('lantern', hanging=True, waterlogged=False))
    d.landmark('roost_steward_station', 'Roost Steward Station', 'interactive', (hx - 4, 7, hz - 4), (hx + 4, 12, hz + 4))
    C.zone(d, 'steward_rest', C.bx((hx - 3, 8, hz - 3), (hx + 3, 11, hz + 3)), 'minecraft:regeneration', 0, 80, 40)
    C.zone(d, 'steward_speed', C.bx((hx - 3, 8, hz - 3), (hx + 3, 11, hz + 3)), 'minecraft:speed', 0, 200, 80)
    d.air(44, 8, 29, 72, 11, 31)
    d.room('R1', 'High-Roof Aviary', (2, 8, 4), (54, 34, 56))


# ================================================================ R2 Wind Gallery (x72..130, z18..44)
def gallery(d):
    d.air(72, 8, 18, 130, 26, 44)
    S.floor_pattern(d, 72, 18, 130, 44, FLOOR, [QB, CAL], 3)
    for x in range(76, 130, 8):
        for z in (19, 43):
            S.column(d, x, z, 8, 18, CAL, w=2, cap=QB)
            d.set(x, 20, z + (1 if z < 30 else -1), b('lantern', hanging=False, waterlogged=False)) if False else None
    for x in range(76, 130, 8): d.set(x, 26, 31, SL)
    # Feather Arch (focal): a tall arch of white wool and quartz across the gallery
    ax = 84
    S.arch(d, ax, 26, 8, 8, 14, QB, axis='z', depth=2)
    for k in range(6):
        d.fill(ax, 22 + k % 2, 25 + k, ax + 1, 22 + k % 2, 25 + k, WOOL)
        d.fill(ax, 8 + k * 2, 24, ax, 9 + k * 2, 24, WOOL)
        d.fill(ax, 8 + k * 2, 35, ax, 9 + k * 2, 35, WOOL)
    d.landmark('feather_arch', 'Feather Arch', 'focal', (ax - 1, 8, 24), (ax + 2, 24, 36))
    # Hanging Cache (discovery): a ledge on chains reached by a short stair
    hx, hz = 112, 20
    d.fill(hx - 3, 14, hz - 1, hx + 3, 14, hz + 3, SPL)
    S.steps(d, hx - 4, hz + 1, 8, 6, 'e', 2, SPL, base=8) if False else None
    for k in range(6): d.fill(hx - 9 + k, 8, hz, hx - 9 + k, 8 + k, hz + 1, SPL)
    d.fill(hx - 3, 8, hz, hx - 3, 13, hz + 1, kit.AIR) if False else None
    d.add_chest((hx + 1, 15, hz + 1), 'south', 'loot')
    for (cx_, cz_) in ((hx - 3, hz - 1), (hx + 3, hz - 1)): d.fill(cx_, 15, cz_, cx_, 26, cz_, b('chain', axis='y', waterlogged=False))
    d.fill(hx - 3, 14, hz - 1, hx + 3, 14, hz - 1, QB)
    d.landmark('hanging_cache', 'Hanging Cache', 'discovery', (hx - 9, 8, hz - 1), (hx + 3, 26, hz + 3))
    # Windward Map Niche is in the Updraft Court (opened by the Feather Offering). Bridge Winch (interactive) at the east end
    wx = 124
    d.fill(wx, 8, 26, wx + 1, 10, 26, CAL); d.set(wx - 1, 9, 26, lever('wall', 'west'))
    d.fill(wx, 8, 36, wx + 1, 10, 36, CAL); d.set(wx - 1, 9, 36, lever('wall', 'west'))
    d.fill(wx, 8, 27, wx, 8, 35, b('chain', axis='y', waterlogged=False)) if False else None
    d.set(wx, 12, 31, b('iron_block')); d.fill(wx, 11, 29, wx, 13, 33, b('iron_bars')) if False else None
    d.fill(wx - 1, 8, 31, wx + 1, 8, 31, b('iron_block'))
    d.landmark('bridge_winch', 'Bridge Winch', 'interactive', (wx - 2, 8, 25), (wx + 2, 13, 37))
    C.lever_seq(d, 'winch', [(wx - 1, 9, 26), (wx - 1, 9, 36)],
                [C.fill(C.bx((141, 7, 30), (150, 7, 32)), 'minecraft:oak_planks'), C.sound((146, 8, 31), 'minecraft:block.chain.place', 2, 0.6),
                 C.say('Chains rattle across the chasm. The first skybridge span drops into place.', 'aqua')],
                on_step=[[C.sound((124, 9, 31), 'minecraft:block.chain.break', 2, 0.6), C.say('The winch clanks. Throw the other lever.', 'aqua')]],
                jam='The winch is jammed. The near lever first.')
    d.air(130, 8, 29, 140, 11, 33)
    C.encounter(d, 'R2', 'Wind howls through the colonnade. Shapes circle in the gale.',
                [C.mob('minecraft:skeleton', 3, 'Roost Archer'), C.mob('minecraft:zombie', 3, 'Windworn Wretch')])
    d.room('R2', 'Wind Gallery', (72, 8, 18), (131, 26, 44))


# ================================================================ R3 Skybridge Chasm (x134..180, z14..52)
def chasm(d):
    d.air(134, 2, 14, 175, 34, 52)
    d.fill(133, 1, 14, 176, 1, 52, CLOUD)
    d.air(130, 8, 29, 140, 11, 33)
    # islands (tops at feet y8)
    for (x0, x1) in ((134, 140), (151, 158), (168, 175)):
        d.fill(x0, 1, 26, x1, 7, 36, WT); d.fill(x0, 7, 26, x1, 7, 36, b('light_gray_terracotta'))
    S.floor_pattern(d, 134, 26, 140, 36, FLOOR, [QB, CAL], 2)
    # bridge 1 (initially absent: the Bridge Winch fills it) and bridge 2 (standing, crumbling, gusty)
    for x in range(141, 151, 3): d.fill(x, 8, 30, x, 9, 30, b('oak_fence')); d.fill(x, 8, 32, x, 9, 32, b('oak_fence')) if False else None
    d.fill(159, 7, 30, 167, 7, 32, SPL)
    for x in range(159, 168, 2): d.fill(x, 8, 30, x, 8, 30, b('oak_fence')); d.fill(x, 8, 32, x, 8, 32, b('oak_fence'))
    # ladders up from the cloud floor
    for y in range(2, 8): d.set(141, y, 33, kit.ladder('east')); d.set(150, y, 33, kit.ladder('west')); d.set(167, y, 29, kit.ladder('west'))
    for x in (146, 163): d.fill(x, 2, 14, x, 6, 52, b('iron_bars'))      # fallers land in the segment of the island they fell from
    # Broken Roost Tower (focal) on island B
    tx, tz = 154, 31
    S.tower(d, tx, tz, 3, 8, 13, QB, floor=CAL, door='w')
    d.air(tx - 1, 17, tz - 3, tx + 1, 21, tz - 1); d.air(tx + 1, 15, tz, tx + 3, 21, tz + 3)
    for k in range(5): d.set(tx - 3 + k, 20 + (k % 2), tz + 3, QB)
    S.rubble(d, 151, 26, 158, 36, 8, [QB, CAL, LGT, b('oak_planks')], 18)
    d.set(tx, 8, tz - 1, b('lantern', hanging=False, waterlogged=False))
    d.landmark('broken_roost_tower', 'Broken Roost Tower', 'focal', (tx - 4, 8, tz - 4), (tx + 4, 21, tz + 4))
    # Beacon Trial (interactive): a plate at island B's south edge ringed by wind-vanes
    px, pz = 154, 35
    d.disc(px, pz, 2, FLOOR, FLOOR, CAL)
    d.set(px, 8, pz, b('stone_pressure_plate', powered=False))
    for (x, z) in S.pts_ring(px, pz, 2, 4, 0.8): d.fill(x, 8, z, x, 10, z, QB); d.set(x, 11, z, LBW)
    d.landmark('beacon_trial', 'Beacon Trial', 'interactive', (px - 3, 7, pz - 3), (px + 3, 11, pz + 1))
    C.trial(d, 'beacon_trial', (px, 8, pz), 'R3',
            [{'say': 'The vanes spin. Something comes down the wind.', 'spawns': [C.mob('minecraft:skeleton', 3, 'Roost Archer')]},
             {'say': 'A gust of wretches.', 'spawns': [C.mob('minecraft:zombie', 3, 'Windworn Wretch'), C.mob('minecraft:skeleton', 2, 'Roost Archer')]}],
            [C.say('The wind lets go. A Feather Token drifts onto the plate.', 'gold'), C.give_key('feather_token', (px, 8, pz))])
    # Skybridge Beacon (focal) on island C
    bx, bz = 172, 31
    S.pyramid(d, bx, bz, 8, 3, [QB, CAL, QB])
    d.fill(bx, 11, bz, bx, 17, bz, QB); d.set(bx, 18, bz, GLOW)
    for (x, z) in ((bx - 1, bz), (bx + 1, bz), (bx, bz - 1), (bx, bz + 1)): d.fill(x, 11, z, x, 12, z, QB)
    for (x, z) in S.pts_ring(bx, bz, 4, 4, 0.78): d.fill(x, 8, z, x, 9, z, b('light_blue_wool'))
    d.landmark('skybridge_beacon', 'Skybridge Beacon', 'focal', (bx - 3, 8, bz - 3), (bx + 3, 19, bz + 3))
    d.air(175, 8, 29, 192, 11, 33)
    for z in range(16, 52, 7): d.set(133, 16, z, SL); d.set(176, 16, z, SL)
    C.crumble(d, 'bridge_two', C.bx((159, 7, 30), (167, 7, 32)), only='spruce_planks', delay=24, restore=160, sound='minecraft:block.wood.break')
    C.pulse(d, 'skybridge_gusts', box=C.bx((134, 2, 14), (180, 34, 52)), period=60, warn=22, count=1, radius=2.5, damage=1.5, kind='fall',
            push=[0.0, 0.5, 0.9], warnParticle='minecraft:cloud', particle='minecraft:cloud', sound='minecraft:entity.breeze.shoot', vol=1.2, pitch=0.8,
            scatter=False, **{'target': 'players'})
    d.room('R3', 'Skybridge Chasm', (134, 2, 14), (183, 34, 52))


# ================================================================ R4 Updraft Court (centre 206,32 r22, tall)
def court(d):
    cx, cz = COURT
    d.air(176, 8, 29, 188, 11, 33)
    d.disc(cx, cz, 22, 8, 36, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, QB)
    d.disc(cx, cz, 14, FLOOR, FLOOR, CAL, rim=12)
    # upper ring gallery (platform at feet y22), reached only by the updraft
    d.disc(cx, cz, 21, 21, 21, QB, rim=11)
    for (x, z) in S.pts_ring(cx, cz, 11, 24): d.fill(x, 22, z, x, 22, z, b('iron_bars'))
    for (x, z) in S.pts_ring(cx, cz, 21, 12): d.fill(x, 8, z, x, 34, z, CAL); d.set(x, 28, z, SL)
    # the updraft: a column of wind in the middle (levitation zone), with a ring of cloud and feathers around the base
    d.disc(cx, cz, 3, FLOOR, FLOOR, b('light_blue_wool'))
    for (x, z) in S.pts_ring(cx, cz, 3, 8): d.set(x, 8, z, b('white_wool'))
    C.zone(d, 'updraft', C.bx((cx - 2, 8, cz - 2), (cx + 2, 30, cz + 2)), 'minecraft:levitation', 2, 24, 8)
    # Wind Harp (focal): two posts with a frame and long chain strings
    hx, hz = cx + 11, cz
    d.fill(hx, 8, hz - 4, hx, 19, hz - 4, QB); d.fill(hx, 8, hz + 4, hx, 19, hz + 4, QB)
    d.fill(hx, 19, hz - 4, hx, 19, hz + 4, b('stripped_oak_log', axis='z')); d.fill(hx, 8, hz - 4, hx, 8, hz + 4, b('stripped_oak_log', axis='z'))
    for z in range(hz - 3, hz + 4): d.fill(hx, 9, z, hx, 18, z, b('chain', axis='y', waterlogged=False))
    d.set(hx, 20, hz, SL)
    d.landmark('wind_harp', 'Wind Harp', 'focal', (hx - 1, 8, hz - 4), (hx + 1, 20, hz + 4))
    # Upper Gallery Stash (discovery) + Airflow Alignment (interactive): up on the ring
    sx, sz = cx - 18, cz
    d.add_chest((sx, 22, sz), 'east', 'loot'); d.set(sx, 22, sz - 2, b('lantern', hanging=False, waterlogged=False)) if False else d.set(sx, 22, sz - 2, b('barrel', facing='up', open=False))
    d.fill(sx - 1, 22, sz - 3, sx - 1, 24, sz + 3, CAL)
    d.landmark('upper_gallery_stash', 'Upper Gallery Stash', 'discovery', (sx - 1, 22, sz - 3), (sx + 1, 24, sz + 3))
    pats = [True, False, True]
    lv = []
    for k, x in enumerate((cx - 4, cx, cx + 4)):
        z = cz - 19
        d.set(x, 22, z, lever('wall', 'south'))
        d.fill(x, 23, z, x, 24, z, kit.AIR)
        d.set(x, 25, z, LBW if pats[k] else WOOL)
        lv.append(((x, 22, z), pats[k]))
    d.fill(cx - 5, 22, cz - 20, cx + 5, 26, cz - 20, CAL)
    C.zone(d, 'featherfall', C.bx((cx - 10, 8, cz - 10), (cx + 10, 30, cz + 10)), 'minecraft:slow_falling', 0, 60, 10)
    d.planner_edges.append({'from': C.bx((cx - 2, 8, cz - 2), (cx + 2, 12, cz + 2)), 'to': [cx + 14, 22, cz]})
    d.planner_edges.append({'from': C.bx((cx + 12, 22, cz - 3), (cx + 16, 22, cz + 3)), 'to': [cx + 6, 8, cz]})
    d.landmark('airflow_alignment', 'Airflow Alignment', 'interactive', (cx - 6, 22, cz - 21), (cx + 6, 26, cz - 18))
    d.air(cx - 1, 8, 54, cx + 1, 11, 66)
    d.seal('vault_gate', 'minecraft:quartz_bricks', (cx - 1, 8, 56), (cx + 1, 11, 57))
    C.toggle(d, 'airflow', lv, [C.clear(C.bx((cx - 1, 8, 56), (cx + 1, 11, 57))), C.sound((cx, 9, 56), 'minecraft:entity.breeze.wind_burst', 2, 0.8),
                                C.say('The vanes lock into the wind. A gate far below the ring slides open in the south wall.', 'aqua')],
             'The vanes settle into the wind.')
    # Feather Offering (interactive) opens the Windward Map Niche (discovery)
    fx, fz = cx, cz + 19
    d.fill(fx, 8, fz, fx, 9, fz, QB); d.set(fx, 10, fz, b('white_wool'))
    for x in (fx - 2, fx + 2): d.fill(x, 8, fz, x, 12, fz, CAL); d.set(x, 13, fz, SL)
    d.landmark('feather_offering', 'Feather Offering', 'interactive', (fx - 2, 8, fz - 1), (fx + 2, 13, fz + 1))
    d.air(cx - 6, 8, cz - 20, cx - 2, 11, cz - 20) if False else None
    nx = cx + 17
    d.air(nx, 8, cz + 6, nx + 4, 11, cz + 10)
    d.add_chest((nx + 4, 8, cz + 8), 'west', 'loot')
    d.set(nx + 4, 8, cz + 6, b('lectern', facing='west', powered=False)); d.set(nx + 4, 8, cz + 10, b('lantern', hanging=False, waterlogged=False))
    d.seal('map_wall', 'minecraft:quartz_bricks', (nx, 8, cz + 6), (nx, 11, cz + 10))
    d.fill(nx, 7, cz + 6, nx + 4, 7, cz + 10, CAL); d.fill(nx + 1, 8, cz + 6, nx + 1, 9, cz + 6, QB); d.fill(nx + 1, 8, cz + 10, nx + 1, 9, cz + 10, QB); d.set(nx + 3, 11, cz + 8, SL)
    d.landmark('windward_map_niche', 'Windward Map Niche', 'discovery', (nx, 8, cz + 6), (nx + 4, 11, cz + 10))
    C.lock(d, 'offering', C.bx((fx, 10, fz)), 'feather_token', [C.clear(C.bx((nx, 8, cz + 6), (nx, 11, cz + 10))), C.sound((nx, 9, cz + 8), 'minecraft:entity.breeze.wind_burst', 2, 1.0),
                                                               C.say('A draught blows a niche open in the east wall.', 'aqua')],
           'A cushion of white wool, shaped to hold a feather.', 'lays the feather on the wool and a draught opens the wall.')
    d.air(cx + 20, 8, 31, cx + 35, 11, 33)
    d.seal('arena_door', 'minecraft:quartz_bricks', (cx + 23, 8, 31), (cx + 24, 11, 33))
    C.lock(d, 'arena_door', C.bx((cx + 23, 8, 31), (cx + 24, 11, 33)), 'gale_key', [C.clear(C.bx((cx + 23, 8, 31), (cx + 24, 11, 33))),
                                                                                    C.sound((cx + 23, 9, 32), 'minecraft:entity.breeze.wind_burst', 2, 0.6), C.say('The wind-door to the Eye of the Gale opens.', 'gold')],
           'A door of still air held in a quartz frame, a keyhole in its centre.', 'turns the Gale Key and the door of held wind lets go.')
    C.encounter(d, 'R4', 'The updraft roars up through the middle. Three vane levers sit on the ring gallery far above.',
                [C.mob('minecraft:skeleton', 3, 'Roost Archer'), C.mob('minecraft:zombie', 3, 'Windworn Wretch')])
    d.room('R4', 'Updraft Court', (184, 8, 8), (230, 36, 56))


# ================================================================ R5 Cliffside Vault (x188..226, z67..100)
def vault(d):
    cx = COURT[0]
    d.air(188, 8, 67, 224, 20, 100)
    d.air(cx - 1, 8, 58, cx + 1, 11, 66)
    S.floor_pattern(d, 188, 67, 224, 100, FLOOR, [QB, LGT], 2)
    for x in range(192, 222, 6):
        d.fill(x, 8, 70, x + 1, 14, 71, CAL); d.set(x, 15, 70, SL)
        d.fill(x, 8, 94, x + 1, 14, 95, CAL)
    # Cliff Nest (discovery): a bundle of hay and white wool in a high niche at the back
    nx, nz = 206, 99
    d.air(nx - 3, 8, nz - 2, nx + 3, 12, nz)
    d.fill(nx - 2, 8, nz - 1, nx + 2, 8, nz - 1, b('hay_block')); d.set(nx, 9, nz - 1, b('white_wool'))
    d.add_chest((nx, 9, nz), 'north', 'loot') if False else None
    d.add_chest((nx, 8, nz), 'south', 'key', key='gale_key')
    d.fill(nx - 3, 7, nz - 2, nx + 3, 7, nz, CAL); d.fill(nx - 3, 8, nz, nx - 3, 10, nz, QB); d.fill(nx + 3, 8, nz, nx + 3, 10, nz, QB); d.set(nx, 10, nz - 1, b('lantern', hanging=True, waterlogged=False)) if False else None
    d.landmark('cliff_nest', 'Cliff Nest', 'discovery', (nx - 3, 8, nz - 2), (nx + 3, 12, nz))
    d.room('R5', 'Cliffside Vault', (188, 8, 58), (224, 20, 100))


# ================================================================ R6 Eye of the Gale (centre 264,32 r22)
def eye(d):
    cx, cz = ARENA
    d.air(226, 8, 31, 244, 11, 33)
    d.disc(cx, cz, 22, 8, 36, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, QB, rim=8)
    d.disc(cx, cz, 8, 1, 1, CLOUD)                              # the Eye: a pit of cloud
    d.disc(cx, cz, 8, 2, 7, kit.AIR)
    d.disc(cx, cz, 18, FLOOR, FLOOR, CAL, rim=14)
    for k in range(4):                                          # four bridges over the eye
        a_ = k * math.pi / 2
        for r in range(0, 9):
            x, z = int(round(cx + r * math.cos(a_))), int(round(cz + r * math.sin(a_)))
            for w_ in (-1, 0, 1):
                d.set(x + (w_ if k % 2 else 0), 7, z + (w_ if k % 2 == 0 else 0), QB)
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 14, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 24, z + 1, CAL)
        d.fill(x - 1, 25, z - 1, x + 2, 25, z + 2, QB)
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, SL); targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): d.fill(x, 8, z, x, 20, z, QB); d.set(x, 21, z, SL)
    C.boss(d, 'gale_warden', 'R6', 'minecraft:breeze', 'The Gale Warden', (cx, 8, cz - 15), targets=targets,
           on_block='minecraft:sea_lantern', off_block='minecraft:quartz_block', health=4.0, scale=2.0, color='WHITE', no_gravity=False,
           intro='The Gale Warden spins up from the eye. Snuff the three wind-lanterns on the pillars to ground it.',
           death='The Gale Warden unravels into a last gust.', break_text='The lanterns gutter out! Strike the Warden now!',
           adds=[C.mob('minecraft:skeleton', 2, 'Roost Archer') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('The gale redoubles.', 'red'), C.spawn('minecraft:skeleton', 2, 'R6', 'Roost Archer')]},
                   {'at': 0.33, 'effects': [C.say('The eye roars.', 'red'), C.spawn('minecraft:zombie', 3, 'R6', 'Windworn Wretch')]}])
    C.pulse(d, 'eye_gusts', box=C.bx((246, 1, 10), (286, 36, 54)), period=56, warn=22, count=2, radius=2.6, damage=2.0, kind='fall',
            push=[0.7, 0.8, 0.0], warnParticle='minecraft:cloud', particle='minecraft:cloud', sound='minecraft:entity.breeze.shoot',
            after='boss_awake:gale_warden', until='boss:gale_warden', scatter=True, **{'target': 'players'})
    C.finish(d, 'gale_warden', 'The Gale Eyrie. Cross the skybridge and ride the updraft.', 'The wind settles. The eyrie closes shortly.')
    d.room('R6', 'Eye of the Gale', (246, 1, 10), (286, 37, 54))
