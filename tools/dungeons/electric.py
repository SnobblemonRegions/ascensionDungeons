"""The Stormheart Works: the Electric dungeon (id `electric`).

Rooms: Charged Garden -> Coil Gallery -> Cable Ravine -> Dynamo Court -> Capacitor Vault; arena Stormheart Reactor.
Hazard : Cable Ravine - telegraphed lightning lands on whoever walks the copper beam; a Grounding Switch on the mid-span island stops it.
Puzzle : Relay Alignment Panel - five levers must match the copper bulbs above them (lit = on); the Spark Trial yields a Capacitor,
         offered at the Dynamo Court altar to open the Capacitor Vault; its Reactor Key opens the arena.
Extra  : Power Routing Console - press the cable lamps in the order of the colour code (shown on the wall) to open the Insulated Cache.
Boss   : the Stormheart (skeleton) behind three charged glowstone pylons; lightning rains on the reactor while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever

W, H, L = 270, 48, 140
FLOOR = 7
PD, PDT, DB = b('polished_deepslate'), b('deepslate_tiles'), b('deepslate_bricks')
CU, CUX, CUW, CUO = b('waxed_copper_block'), b('waxed_exposed_copper'), b('waxed_weathered_copper'), b('waxed_oxidized_copper')
CC_, CCX, CCO = b('waxed_cut_copper'), b('waxed_exposed_cut_copper'), b('waxed_oxidized_cut_copper')
GRATE, GLOW, ROD = b('waxed_copper_grate'), b('glowstone'), b('lightning_rod', facing='up', powered=False, waterlogged=False)
LAMPS = ['red', 'yellow', 'lime', 'light_blue']
COURT, ARENA = (170, 32), (234, 32)


def bulb(lit): return b('copper_bulb', lit=lit, powered=False)


def build():
    d = kit.Dungeon('electric', 'The Stormheart Works', W, H, L, biome='minecraft:meadow')
    d.loot_table = 'ascensiondungeons/chests/ancient_foundry'.replace('ascensiondungeons/', 'ascensiondungeons:')
    d.shell = ('polished_deepslate', 'deepslate_bricks', 'deepslate_tiles', 'tuff_bricks', 'waxed_copper_block', 'waxed_cut_copper')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, PD)
    C.key(d, 'capacitor', 'minecraft:amethyst_shard', 'Charged Capacitor', 'Humming with stored current. The Capacitor Offering in the Dynamo Court wants it.')
    C.key(d, 'reactor_key', 'minecraft:tripwire_hook', 'Reactor Key', 'Opens the shielded door to the Stormheart Reactor.')
    garden(d); coils(d); cable(d); dynamo(d); vault(d); reactor(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R5', False), ('relay', 'R3', True), ('relay', 'R5', False), ('relay', 'R6', False),
                     ('offering', 'R5', True), ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, PD, [(DB, 0.2), (PDT, 0.15), (b('tuff_bricks'), 0.07), (b('waxed_cut_copper'), 0.03)])
    return d


def hall(d, x1, z1, x2, z2, y2, floor_mats=None, cell=3):
    d.air(x1, 8, z1, x2, y2, z2)
    if floor_mats: S.floor_pattern(d, x1, z1, x2, z2, FLOOR, floor_mats, cell)


def coil(d, x, z, y, h, r=1):
    """A tesla coil: stacked copper, ringed windings, lightning rod and end rods on top."""
    d.fill(x - 1, y, z - 1, x + 1, y, z + 1, CCO)
    d.fill(x, y + 1, z, x, y + h, z, CU)
    for k in range(2, h, 2): d.fill(x - r, y + k, z - r, x + r, y + k, z + r, CCX if (k // 2) % 2 else CUW)
    d.set(x, y + h + 1, z, ROD)
    d.set(x, y + h, z, b('copper_bulb', lit=True, powered=False))


# ================================================================ R1 Charged Garden (x6..44, z6..44)
def garden(d):
    hall(d, 6, 6, 44, 44, 24, [PDT, b('waxed_cut_copper')], 4)
    d.entry = (9, 8, 25)
    for (x, z) in S.pts_ring(25, 25, 14, 12, 0.2):
        d.fill(x - 1, FLOOR, z - 1, x + 1, FLOOR, z + 1, b('grass_block', snowy=False))
        d.set(x, 8, z, b(['allium', 'cornflower', 'dandelion', 'azure_bluet'][(x + z) % 4]))
    # Storm Coil (focal)
    d.disc(25, 25, 4, FLOOR, FLOOR, b('waxed_oxidized_cut_copper'))
    coil(d, 25, 25, 8, 14, 2)
    for yy in (10, 14, 18): d.disc(25, 25, 4 - (yy - 10) // 4, yy, yy, CCX, rim=2)
    d.landmark('storm_coil', 'Storm Coil', 'focal', (20, 8, 20), (30, 24, 30))
    # Copper Spire (focal): a tapering spire at the north-east corner
    S.spire(d, 38, 12, 8, 16, 3, CUX)
    d.set(38, 24, 12, ROD)
    for yy in (10, 14, 18): d.disc(38, 12, 3 - (yy - 8) // 6, yy, yy, CUO, rim=1)
    d.landmark('copper_spire', 'Copper Spire', 'focal', (35, 8, 9), (41, 24, 15))
    # Static Habitat Alcove (discovery): an amethyst nook in the south wall
    S.nook(d, 15, 8, 44, 's', depth=5, w=5, h=4)
    d.fill(13, 7, 45, 17, 7, 49, b('amethyst_block'))
    for (x, z) in [(14, 47), (16, 47), (15, 49), (13, 48), (17, 48)]: d.set(x, 8, z, b('amethyst_cluster', facing='up', waterlogged=False))
    d.set(15, 8, 46, b('lightning_rod', facing='up', powered=False, waterlogged=False)) if False else None
    d.add_chest((15, 8, 49), 'north', 'loot')
    d.set(14, 8, 49, b('candle', candles=2, lit=True, waterlogged=False))
    d.landmark('static_habitat_alcove', 'Static Habitat Alcove', 'discovery', (13, 7, 44), (17, 10, 49))
    d.air(44, 8, 24, 56, 11, 26)
    C.encounter(d, 'R1', None, [])
    d.room('R1', 'Charged Garden', (6, 8, 6), (44, 24, 44))


# ================================================================ R2 Coil Gallery (x56..110, z14..38)
def coils(d):
    hall(d, 56, 14, 110, 38, 20, [PDT, GRATE], 2)
    for x in range(60, 109, 8):
        coil(d, x, 16, 8, 8); coil(d, x, 36, 8, 8)
        d.set(x, 20, 26, b('chain', axis='y', waterlogged=False)) if False else None
    for x in range(60, 109, 8): d.set(x, 20, 26, b('sea_lantern')) if False else d.set(x, 20, 26, GLOW)
    # Charged Crystal Array (focal): amethyst shards in a copper cradle at the hall's centre
    cx, cz = 84, 26
    d.disc(cx, cz, 5, 8, 8, CCO); d.disc(cx, cz, 4, FLOOR, FLOOR, b('waxed_cut_copper'))
    S.crystal(d, cx, cz, 9, 7, b('amethyst_block'), b('amethyst_block'))
    for (x, z) in S.pts_ring(cx, cz, 4, 6): S.crystal(d, x, z, 9, 3, b('amethyst_block'))
    for (x, z) in S.pts_ring(cx, cz, 2, 4, 0.5): d.set(x, 9, z, b('amethyst_cluster', facing='up', waterlogged=False))
    d.set(cx + 5, 9, cz, ROD); d.set(cx - 5, 9, cz, b('copper_bulb', lit=True, powered=False))
    d.landmark('charged_crystal_array', 'Charged Crystal Array', 'focal', (cx - 6, 8, cz - 6), (cx + 6, 17, cz + 6))
    # Shattered Dynamo (focal): a broken machine with a rubble tail
    dx, dz = 66, 30
    d.fill(dx, 8, dz, dx + 6, 12, dz + 4, CUW); d.fill(dx + 1, 9, dz + 1, dx + 5, 11, dz + 3, kit.AIR)
    d.air(dx + 2, 12, dz, dx + 4, 12, dz + 2); d.air(dx, 11, dz + 4, dx + 2, 12, dz + 4)
    d.set(dx + 3, 9, dz + 2, b('lightning_rod', facing='up', powered=False, waterlogged=False))
    d.fill(dx + 2, 8, dz + 2, dx + 4, 8, dz + 2, b('redstone_block'))
    S.rubble(d, dx - 2, dz - 2, dx + 9, dz + 7, 8, [CUO, CCX, b('iron_block'), b('copper_block')], 25)
    d.landmark('shattered_dynamo', 'Shattered Dynamo', 'focal', (dx - 2, 8, dz - 2), (dx + 8, 12, dz + 6))
    # Maintenance Locker (discovery): lockers (barrels) in a wall bay on the north side
    mx = 96
    S.nook(d, mx, 8, 14, 'n', depth=3, w=5, h=3)
    d.add_chest((mx, 8, 12), 'south', 'loot')
    d.set(mx - 2, 8, 12, b('barrel', facing='south', open=False)); d.set(mx + 2, 8, 12, b('barrel', facing='south', open=False))
    d.set(mx, 10, 12, b('lantern', hanging=True, waterlogged=False))
    d.landmark('maintenance_locker', 'Maintenance Locker', 'discovery', (mx - 2, 8, 11), (mx + 2, 10, 14))
    # Spark Trial Circle (interactive): a plate in a ring of rods at the west end
    px, pz = 64, 22
    d.disc(px, pz, 4, FLOOR, FLOOR, CCO); d.disc(px, pz, 1, FLOOR, FLOOR, CU)
    d.set(px, 8, pz, b('heavy_weighted_pressure_plate', power=0)) if False else d.set(px, 8, pz, b('light_weighted_pressure_plate', power=0))
    for (x, z) in S.pts_ring(px, pz, 4, 6): d.fill(x, 8, z, x, 9, z, CUX); d.set(x, 10, z, ROD)
    d.landmark('spark_trial_circle', 'Spark Trial Circle', 'interactive', (px - 5, 7, pz - 5), (px + 5, 11, pz + 5))
    C.trial(d, 'spark_trial', (px, 8, pz), 'R2',
            [{'say': 'The rods crackle. Sparks take shape.', 'spawns': [C.mob('minecraft:zombie', 3, 'Static Husk'), C.mob('minecraft:skeleton', 2, 'Arc Gunner')]},
             {'say': 'The coils overload.', 'spawns': [C.mob('minecraft:zombie', 4, 'Static Husk'), C.mob('minecraft:spider', 2, 'Short Circuit')]}],
            [C.say('The current drains into a crystal. A Charged Capacitor lies on the plate.', 'gold'), C.give_key('capacitor', (px, 8, pz))], hold=30)
    # Insulated... (the cache is in the court). Relay Alignment Panel (interactive): five levers under five copper bulbs
    pats = [True, True, False, True, False]
    lv = []
    for k, x in enumerate((92, 95, 98, 101, 104)):
        d.set(x, 8, 38, lever('wall', 'north'))
        d.fill(x, 9, 38, x, 10, 38, kit.AIR)
        d.set(x, 11, 38, bulb(pats[k]))
        lv.append(((x, 8, 38), pats[k]))
    d.fill(91, 12, 38, 105, 12, 38, CCO) if False else None
    d.landmark('relay_alignment_panel', 'Relay Alignment Panel', 'interactive', (90, 7, 36), (106, 12, 38))
    d.air(110, 8, 29, 121, 11, 31)
    d.seal('relay_gate', 'minecraft:waxed_oxidized_copper', (112, 8, 29), (113, 11, 31))
    C.toggle(d, 'relay', lv, [C.clear(C.bx((112, 8, 29), (113, 11, 31))), C.sound((112, 9, 30), 'minecraft:block.copper_bulb.turn_on', 2, 0.7),
                              C.say('The relay gate retracts. Wind and humming come from the ravine beyond.', 'yellow')],
             'The bulbs steady. The relay is aligned.')
    C.encounter(d, 'R2', 'Coils spit along the walls. Something with circuits for a pulse is awake.',
                [C.mob('minecraft:zombie', 3, 'Static Husk'), C.mob('minecraft:skeleton', 3, 'Arc Gunner'), C.mob('minecraft:spider', 2, 'Short Circuit')])
    d.room('R2', 'Coil Gallery', (56, 8, 14), (111, 20, 38))


# ================================================================ R3 Cable Ravine (x114..144, z10..54)
def cable(d):
    d.air(114, 2, 10, 144, 30, 54)
    d.fill(113, 1, 10, 145, 1, 54, b('waxed_exposed_cut_copper'))
    for x in range(114, 145):
        for z in range(10, 55):
            if d.rng.random() < 0.05: d.set(x, 2, z, b('lightning_rod', facing='up', powered=False, waterlogged=False))
    d.air(110, 8, 29, 121, 11, 31)
    # the beam: 3 wide copper across at z29..31 with an island in the middle (x126..132)
    d.fill(114, 7, 29, 144, 7, 31, CC_)
    d.fill(125, 7, 26, 133, 7, 34, CCO)
    for x in (114, 119, 139, 144):
        d.fill(x, 8, 29, x, 11, 29, b('iron_bars')); d.fill(x, 8, 31, x, 11, 31, b('iron_bars'))
    # Cable Suspension Mast (focal) on the island
    mx, mz = 129, 30
    d.fill(mx, 8, mz, mx, 24, mz, CUX); d.fill(mx - 1, 8, mz - 1, mx + 1, 8, mz + 1, CCO)
    for k, (x, z) in enumerate([(114, 10), (144, 10), (114, 54), (144, 54), (114, 30), (144, 30)]):
        d.line((mx, 24, mz), (x, 12 + (k % 2) * 4, z), b('chain', axis='y', waterlogged=False) if False else b('iron_bars'))
    d.set(mx, 25, mz, ROD); d.set(mx, 23, mz, GLOW)
    d.landmark('cable_suspension_mast', 'Cable Suspension Mast', 'focal', (125, 8, 26), (133, 26, 34))
    # Grounding Switch (interactive): beside the mast
    d.fill(127, 8, 33, 127, 9, 33, CUW)
    d.set(127, 10, 33, kit.b('lever', face='wall', facing='north', powered=False)) if False else None
    d.set(128, 8, 33, lever('floor', 'south'))
    d.set(126, 8, 33, ROD); d.set(130, 8, 33, b('copper_bulb', lit=True, powered=False))
    d.landmark('grounding_switch', 'Grounding Switch', 'interactive', (126, 8, 32), (130, 10, 34))
    C.lever_seq(d, 'grounding', [(128, 8, 33)], [C.sound((129, 9, 30), 'minecraft:block.beacon.deactivate', 2, 0.5), C.say('The mast earths the storm. The ravine falls quiet.', 'yellow')])
    # ladders to climb back out
    for y in range(2, 8): d.set(114, y, 33, kit.ladder('east')); d.set(144, y, 27, kit.ladder('west'))
    d.air(113, 8, 32, 114, 11, 36); d.air(145, 8, 24, 148, 11, 28)
    d.air(145, 8, 28, 150, 11, 32) if False else None
    d.air(145, 8, 29, 150, 11, 31)
    # Buried Cable Niche (discovery): a hollow in the south wall at the bottom of the ravine
    d.air(122, 2, 55, 126, 5, 58)
    d.fill(122, 1, 55, 126, 1, 58, CCO)
    d.add_chest((124, 2, 58), 'north', 'loot')
    d.fill(122, 3, 57, 126, 3, 57, b('iron_bars')) if False else None
    d.set(123, 2, 56, b('copper_bulb', lit=True, powered=False)); d.set(125, 2, 56, b('copper_bulb', lit=True, powered=False))
    for x in range(122, 127): d.set(x, 6, 56, CUX) if False else None
    d.landmark('buried_cable_niche', 'Buried Cable Niche', 'discovery', (122, 1, 54), (126, 5, 58))
    for z in range(12, 54, 8): d.set(113, 14, z, GLOW); d.set(145, 14, z, GLOW)
    C.pulse(d, 'cable_lightning', box=C.bx((114, 2, 10), (144, 30, 54)), period=70, warn=34, count=1, radius=2.2, damage=5.0, kind='lightning',
            warnParticle='minecraft:electric_spark', particle='minecraft:electric_spark', bolt=True, sound='minecraft:entity.lightning_bolt.thunder', vol=1.2, pitch=1.6,
            until='grounding', scatter=False, **{'target': 'players'})
    d.room('R3', 'Cable Ravine', (116, 2, 10), (148, 30, 54))


# ================================================================ R4 Dynamo Court (centre 170,32 r22)
def dynamo(d):
    cx, cz = COURT
    d.disc(cx, cz, 22, 8, 26, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, PDT)
    d.disc(cx, cz, 20, FLOOR, FLOOR, GRATE, rim=16)
    d.disc(cx, cz, 14, FLOOR, FLOOR, b('waxed_cut_copper'), rim=11)
    # the dynamo itself: a great ringed rotor (decor in the middle of the hall)
    d.disc(cx, cz, 6, 8, 8, CCO); d.disc(cx, cz, 4, 9, 14, CUW); d.disc(cx, cz, 5, 11, 11, CCX, rim=4)
    d.disc(cx, cz, 5, 13, 13, CCX, rim=4); d.set(cx, 15, cz, GLOW); d.set(cx, 16, cz, ROD)
    for (x, z) in S.pts_ring(cx, cz, 21, 16):
        d.fill(x, 8, z, x, 25, z, CU if (x + z) % 2 else CUX); d.set(x, 15, z, GLOW)
    d.air(148, 8, 29, 151, 11, 31)
    # Power Routing Console (interactive): four lamp pads; colour code on the wall reads red, blue, yellow, lime
    wx, wz = 164, 12
    d.fill(wx - 6, 8, wz + 1, wx + 6, 14, wz + 1, DB) if False else None
    btn = []
    for k, c in enumerate(LAMPS):
        x = wx - 4 + k * 3
        d.fill(x, 8, wz + 2, x, 9, wz + 2, CCO)
        d.set(x, 10, wz + 2, b(c + '_concrete'))
        d.set(x, 8, wz + 3, b('stone_button', face='floor', facing='north', powered=False)) if False else None
        d.set(x, 9, wz + 3, b('stone_button', face='floor', facing='north', powered=False)) if False else None
        d.fill(x, 8, wz + 3, x, 8, wz + 3, CCX)
        d.set(x, 9, wz + 3, b('stone_button', face='floor', facing='north', powered=False))
        btn.append((x, 9, wz + 3))
    for k, c in enumerate(['red', 'light_blue', 'yellow', 'lime']): d.set(wx - 4 + k * 3, 13, wz + 1, b(c + '_concrete'))
    d.landmark('power_routing_console', 'Power Routing Console', 'interactive', (wx - 6, 8, wz), (wx + 6, 14, wz + 4))
    d.air(166, 8, 3, 174, 11, 9)
    d.air(172, 8, 3, 178, 11, 9)
    d.fill(166, 7, 3, 178, 7, 9, CCO)
    d.air(166, 8, 3, 178, 11, 9)
    d.add_chest((176, 8, 4), 'south', 'loot')
    d.set(166, 8, 4, b('lantern', hanging=False, waterlogged=False)); d.set(177, 8, 6, b('light_weighted_pressure_plate', power=0))
    d.set(172, 11, 6, b('copper_bulb', lit=True, powered=False))
    d.seal('cache_door', 'minecraft:waxed_oxidized_cut_copper', (166, 8, 9), (174, 11, 10))
    d.landmark('insulated_cache', 'Insulated Cache', 'discovery', (166, 7, 3), (178, 11, 10))
    C.sequence(d, 'routing', btn, [0, 2, 1, 3], [C.clear(C.bx((166, 8, 9), (174, 11, 10))), C.sound((170, 9, 9), 'minecraft:block.copper_door.open', 2, 0.8),
                                                  C.say('A shielded panel slides back in the north wall.', 'yellow')],
               'The lamps hum in order.', 'The lamps drop out. The code reads red, blue, yellow, lime.')
    # Abandoned Engineer's Bench (discovery): a bench and a chest in the south-west
    bx_, bz_ = 152, 46
    d.fill(bx_, 8, bz_, bx_ + 3, 8, bz_ + 1, b('smooth_stone_slab', type='bottom', waterlogged=False)) if False else None
    d.set(bx_, 8, bz_, b('crafting_table')); d.set(bx_ + 1, 8, bz_, b('anvil', facing='north')); d.set(bx_ + 2, 8, bz_, b('smithing_table')); d.set(bx_ + 3, 8, bz_, b('grindstone', face='floor', facing='north'))
    d.add_chest((bx_ + 1, 8, bz_ + 2), 'north', 'loot')
    d.fill(bx_ - 1, 8, bz_ - 1, bx_ - 1, 10, bz_ + 3, CUW); d.fill(bx_ + 4, 8, bz_ - 1, bx_ + 4, 10, bz_ + 3, CUW)
    d.set(bx_ + 1, 11, bz_ + 1, b('lantern', hanging=True, waterlogged=False)) if False else None
    d.landmark('abandoned_engineer_s_bench', "Abandoned Engineer's Bench", 'discovery', (bx_ - 1, 8, bz_ - 1), (bx_ + 4, 11, bz_ + 3))
    # Capacitor Offering (interactive): an altar cradle in the south; the Capacitor Vault gate is behind it
    ax, az = 170, 50
    d.set(ax, 8, az, b('lightning_rod', facing='up', powered=False, waterlogged=False)) if False else None
    d.fill(ax, 8, az, ax, 8, az, CUO); d.set(ax, 9, az, b('amethyst_block'))
    for (x, z) in ((ax - 2, az), (ax + 2, az)): d.fill(x, 8, z, x, 12, z, CUX); d.set(x, 13, z, ROD)
    d.landmark('capacitor_offering', 'Capacitor Offering', 'interactive', (ax - 2, 8, az - 1), (ax + 2, 13, az + 1))
    d.air(169, 8, 54, 171, 11, 67)
    d.seal('vault_gate', 'minecraft:waxed_oxidized_cut_copper', (169, 8, 56), (171, 11, 57))
    C.lock(d, 'offering', C.bx((ax, 9, az)), 'capacitor', [C.clear(C.bx((169, 8, 56), (171, 11, 57))), C.sound((170, 9, 56), 'minecraft:block.copper_door.open', 2, 0.7),
                                                          C.say('The altar surges. The gate to the Capacitor Vault slides open.', 'yellow')],
           'A cradle of copper, shaped for a charged crystal.', 'seats the capacitor and the gate surges open.')
    d.air(192, 8, 31, 213, 11, 33)
    d.seal('arena_door', 'minecraft:waxed_oxidized_cut_copper', (193, 8, 31), (194, 11, 33))
    C.lock(d, 'arena_door', C.bx((193, 8, 31), (194, 11, 33)), 'reactor_key', [C.clear(C.bx((193, 8, 31), (194, 11, 33))), C.sound((193, 9, 32), 'minecraft:block.copper_door.open', 2, 0.5),
                                                                              C.say('The shielded door to the Stormheart Reactor opens.', 'gold')],
           'A shielded door with a copper keyway.', 'turns the Reactor Key and the shield door yawns open.')
    C.encounter(d, 'R4', 'The rotor spins down. The colour code on the north wall: red, blue, yellow, lime.',
                [C.mob('minecraft:zombie', 3, 'Static Husk'), C.mob('minecraft:skeleton', 3, 'Arc Gunner')])
    d.room('R4', 'Dynamo Court', (148, 8, 3), (192, 26, 54))


# ================================================================ R5 Capacitor Vault (x150..190, z67..100)
def vault(d):
    hall(d, 150, 67, 190, 100, 18, [PDT, CCO], 2)
    d.air(169, 8, 58, 171, 11, 66)
    for x in range(154, 188, 6):
        for z in (72, 90):
            d.fill(x, 8, z, x + 1, 12, z + 1, CUW); d.set(x, 13, z, ROD); d.set(x + 1, 13, z, GLOW)
    for x in range(154, 188, 6): d.fill(x, 8, 80, x, 9, 80, b('amethyst_block'))
    d.add_chest((170, 8, 97), 'north', 'key', key='reactor_key')
    d.fill(168, 8, 98, 172, 10, 99, CUX)
    d.room('R5', 'Capacitor Vault', (150, 8, 58), (190, 18, 100))


# ================================================================ R6 Stormheart Reactor (centre 234,32 r22)
def reactor(d):
    cx, cz = ARENA
    d.air(192, 8, 31, 213, 11, 33)
    d.disc(cx, cz, 22, 8, 30, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, PDT)
    d.disc(cx, cz, 18, FLOOR, FLOOR, GRATE, rim=12)
    d.disc(cx, cz, 7, 8, 8, CCO); d.disc(cx, cz, 4, 8, 20, CUW); d.disc(cx, cz, 3, 21, 21, CCX); d.set(cx, 22, cz, GLOW); d.set(cx, 23, cz, ROD)
    d.air(cx - 1, 8, cz - 7, cx + 1, 10, cz - 5)
    d.disc(cx, cz, 4, 9, 20, CUW)
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 15, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 22, z + 1, CU if k % 2 else CUX); d.set(x, 23, z, ROD)
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, GLOW)
            targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 14): d.fill(x, 8, z, x, 16, z, CCO); d.set(x, 17, z, GLOW)
    C.boss(d, 'stormheart', 'R6', 'minecraft:skeleton', 'The Stormheart', (cx, 9, cz + 8), targets=targets,
           on_block='minecraft:glowstone', off_block='minecraft:waxed_copper_block', health=5.0, scale=1.8, color='YELLOW',
           equipment={'mainhand': 'minecraft:bow', 'head': 'minecraft:iron_helmet', 'chest': 'minecraft:iron_chestplate'},
           intro='The Stormheart wakes. Break the three charged pylons on the ring to ground it.',
           death='The Stormheart sputters out. The reactor falls silent.', break_text='The pylons burn out! Strike the Stormheart now!',
           adds=[C.mob('minecraft:zombie', 2, 'Static Husk') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('Lightning crawls over the floor.', 'red'), C.spawn('minecraft:skeleton', 2, 'R6', 'Arc Gunner')]},
                   {'at': 0.33, 'effects': [C.say('The core overloads.', 'red'), C.spawn('minecraft:zombie', 3, 'R6', 'Static Husk')]}])
    C.pulse(d, 'reactor_storm', box=C.bx((194, 8, 10), (256, 30, 54)), period=60, warn=30, count=2, radius=2.0, damage=4.5, kind='lightning',
            warnParticle='minecraft:electric_spark', particle='minecraft:electric_spark', bolt=True, sound='minecraft:entity.lightning_bolt.thunder', vol=1.0, pitch=1.8,
            after='boss_awake:stormheart', until='boss:stormheart', scatter=True, **{'target': 'players'})
    C.finish(d, 'stormheart', 'The Stormheart Works. Align the relay, cross the cable ravine, and bring a capacitor to the court.',
             'The current dies. The works close shortly.')
    d.room('R6', 'Stormheart Reactor', (195, 8, 10), (256, 31, 54))
