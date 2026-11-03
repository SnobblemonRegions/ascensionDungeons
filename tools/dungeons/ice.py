"""The Glacier Crown: the Ice dungeon (id `ice`).

Rooms: Frost Habitat -> Glacier Gallery -> Crevasse Crossing -> Aurora Court -> Rime Vault; arena Crown of Winter.
Hazard : Crevasse Crossing - a packed-ice bridge that cracks away under you (the fall is short; ladders lead back up) and freezing
         blasts that land where you stand after a telegraph (slowness on top of the damage).
Puzzle : Thermal Balance Shrine (3 levers must match the sea-lantern/blue-ice clues) opens the gallery; two Thaw Valves, thrown in order,
         melt the wall to the crevasse; the Rime Trial yields a Frost Shard, offered at the court's Frost Offering to open the Rime Vault;
         its Winter Key opens the arena.
Extra  : Aurora Alignment - press the four pedestals in the colour order painted across the court ceiling to open the Preserved Relic Niche.
Boss   : the Winter Warden (stray) behind three blue-ice wards; freezing blasts fall on the arena while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever

W, H, L = 290, 46, 140
FLOOR = 7
SNOW, PICE, BICE, CAL, ICE = b('snow_block'), b('packed_ice'), b('blue_ice'), b('calcite'), b('ice')
QZ, WG = b('quartz_block'), b('white_stained_glass')
SL = b('sea_lantern')
COURT, ARENA = (176, 30), (258, 30)
AURORA = ['cyan', 'magenta', 'lime', 'light_blue']


def build():
    d = kit.Dungeon('ice', 'The Glacier Crown', W, H, L, biome='minecraft:snowy_slopes')
    d.loot_table = 'ascensiondungeons:chests/glacial_vault'
    d.shell = ('snow_block', 'packed_ice', 'blue_ice', 'calcite', 'stone', 'diorite')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, SNOW)
    C.key(d, 'frost_shard', 'minecraft:prismarine_crystals', 'Frost Shard', 'Cold enough to ache. The Frost Offering in the Aurora Court wants it.')
    C.key(d, 'winter_key', 'minecraft:tripwire_hook', 'Winter Key', 'Opens the rime-sealed door to the Crown of Winter.')
    habitat(d); gallery(d); crevasse(d); court(d); vault(d); crown(d)
    d.order_rules = [('start', 'R2', False), ('start', 'R3', False), ('thermal', 'R2', True), ('thermal', 'R3', False), ('thaw', 'R3', True),
                     ('thaw', 'R5', False), ('thaw', 'R6', False), ('offering', 'R5', True), ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, SNOW, [(PICE, 0.30), (BICE, 0.06), (CAL, 0.10), (b('diorite'), 0.04)])
    return d


# ================================================================ R1 Frost Habitat (cave around 22,24)
def habitat(d):
    d.ellipsoid(22, 14, 24, 18, 12, 17, kit.AIR)
    d.fill(3, 0, 5, 42, FLOOR, 44, SNOW)
    d.entry = (8, 8, 24)
    for (x, z, h) in [(10, 14, 8), (36, 14, 9), (34, 36, 7), (12, 34, 6), (24, 40, 8)]:
        S.spire(d, x, z, 8, h, 2, PICE)                      # ice stalagmites
        S.spire(d, x, z, 26 - h, h, 2, PICE) if False else None
    for (x, z) in [(14, 20), (30, 20), (20, 30), (28, 33)]: d.fill(x, 21, z, x, 24, z, ICE) if False else S.spire(d, x, z, 14, 8, 2, BICE) if False else None
    # Snowbound Observatory (focal)
    ox, oz = 32, 26
    S.tower(d, ox, oz, 5, 8, 8, QZ, floor=CAL, door='w', glow=SL)
    d.ellipsoid(ox, 16, oz, 5, 4, 5, QZ, only_air=True)
    d.ellipsoid(ox, 16, oz, 4, 3, 4, kit.AIR)
    d.fill(ox - 4, 15, oz - 1, ox + 4, 15, oz + 1, kit.AIR) if False else None
    d.air(ox - 1, 18, oz - 1, ox + 1, 21, oz + 1)
    d.line((ox - 2, 9, oz), (ox + 2, 14, oz), b('iron_block'))
    d.set(ox, 8, oz, b('lectern', facing='west', powered=False)) if False else d.set(ox + 2, 8, oz + 2, b('lectern', facing='west', powered=False))
    d.landmark('snowbound_observatory', 'Snowbound Observatory', 'focal', (ox - 6, 8, oz - 6), (ox + 6, 20, oz + 6))
    # Frozen Expedition Camp (discovery)
    cx, cz = 14, 12
    for (x, z, col) in [(cx - 3, cz, 'red'), (cx + 3, cz + 1, 'orange')]:
        S.pyramid(d, x, z, 8, 3, [b(col + '_wool'), b(col + '_wool')])
        d.air(x - 1, 8, z - 1, x + 1, 9, z + 1)
    d.set(cx, 8, cz + 3, b('campfire', facing='north', lit=False, signal_fire=False, waterlogged=False))
    d.set(cx - 1, 8, cz + 4, b('barrel', facing='up', open=False)); d.set(cx + 1, 8, cz + 4, b('barrel', facing='up', open=False))
    d.add_chest((cx, 8, cz - 2), 'south', 'loot')
    d.landmark('frozen_expedition_camp', 'Frozen Expedition Camp', 'discovery', (cx - 6, 8, cz - 4), (cx + 6, 11, cz + 5))
    # Snowdrift Cache (discovery): a chest under a drift in the north-west of the cave
    dx_, dz_ = 18, 40
    S.pyramid(d, dx_, dz_, 8, 4, [SNOW, SNOW, CAL])
    d.air(dx_ - 1, 8, dz_ - 1, dx_ + 1, 8, dz_ + 1)
    d.air(dx_, 8, dz_ - 3, dx_, 9, dz_ - 2)
    d.add_chest((dx_, 8, dz_), 'north', 'loot')
    d.set(dx_ - 1, 8, dz_, b('lantern', hanging=False, waterlogged=False))
    d.landmark('snowdrift_cache', 'Snowdrift Cache', 'discovery', (dx_ - 4, 8, dz_ - 4), (dx_ + 4, 11, dz_ + 1))
    # Thermal Balance Shrine (interactive): three levers, each under a lantern (on) or a blue-ice block (off)
    pats = [True, False, True]
    lv = []
    for k, z in enumerate((20, 24, 28)):
        d.set(5, 8, z, lever('wall', 'east')) if False else None
    d.fill(40, 8, 20, 40, 12, 30, CAL) if False else None
    for k, z in enumerate((19, 22, 25)):
        d.set(38, 8, z, kit.b('lever', face='floor', facing='east', powered=False)) if False else None
    shrine_x = 41
    d.air(36, 8, 15, 41, 12, 23)
    for k, z in enumerate((17, 19, 21)):
        d.set(shrine_x, 8, z, lever('wall', 'west'))
        d.set(shrine_x, 11, z, SL if pats[k] else BICE)
        d.fill(shrine_x, 9, z, shrine_x, 10, z, kit.AIR)
        lv.append(((shrine_x, 8, z), pats[k]))
    d.fill(shrine_x - 1, 7, 16, shrine_x - 1, 7, 22, CAL)
    d.landmark('thermal_balance_shrine', 'Thermal Balance Shrine', 'interactive', (shrine_x - 2, 7, 16), (shrine_x, 12, 22))
    d.air(34, 8, 23, 56, 11, 25)
    d.seal('frost_gate', 'minecraft:packed_ice', (44, 8, 23), (45, 11, 25))
    C.toggle(d, 'thermal', lv, [C.clear(C.bx((44, 8, 23), (45, 11, 25))), C.sound((44, 9, 24), 'minecraft:block.glass.break', 2, 0.6),
                                C.say('The ice wall groans and cracks open. The gallery lies beyond.', 'aqua')],
             'The shrine balances. Warmth and cold settle.')
    d.room('R1', 'Frost Habitat', (3, 8, 5), (43, 26, 44))


# ================================================================ R2 Glacier Gallery (x52..120, z10..40)
def gallery(d):
    d.air(52, 8, 10, 120, 24, 40)
    for x in range(54, 120, 4):
        for z in (10, 40):
            S.spire(d, x, z, 8, 14, 1, PICE) if False else d.fill(x, 8, z, x, 20, z, PICE)
    for x in range(56, 118, 6):
        d.fill(x, 24, 10, x, 24, 40, PICE)
    # Glacier Arch (focal): a big natural arch of blue ice across the hall
    ax = 70
    d.fill(ax, 8, 11, ax + 2, 16, 14, BICE); d.fill(ax, 8, 36, ax + 2, 16, 39, BICE)
    for t in range(0, 12):
        yy = 17 + int(4 * math.sin(t / 11.0 * math.pi))
        d.fill(ax, yy, 14 + t * 2, ax + 2, yy + 1, 14 + t * 2 + 1, BICE)
    d.set(ax + 1, 18, 25, SL); d.set(ax + 1, 18, 26, QZ); d.fill(ax - 1, 8, 12, ax - 1, 12, 12, CAL); d.fill(ax - 1, 8, 38, ax - 1, 12, 38, CAL)
    d.landmark('glacier_arch', 'Glacier Arch', 'focal', (ax, 8, 11), (ax + 2, 22, 39))
    # Frozen Cascade (focal): a curtain of icicles pouring down the north wall
    for x in range(84, 100):
        h = 6 + (x * 7) % 9
        d.fill(x, 24 - h, 11, x, 23, 11, BICE if x % 3 else PICE)
        d.fill(x, 8, 11, x, 8 + (x * 5) % 3, 11, BICE)
    d.fill(84, 8, 10, 99, 24, 10, BICE)
    d.landmark('frozen_cascade', 'Frozen Cascade', 'focal', (84, 8, 10), (99, 24, 12))
    # Rime Trial Circle (interactive)
    px, pz = 104, 26
    d.disc(px, pz, 5, FLOOR, FLOOR, CAL); d.disc(px, pz, 2, FLOOR, FLOOR, BICE)
    d.set(px, 8, pz, b('stone_pressure_plate', powered=False))
    for (x, z) in S.pts_ring(px, pz, 5, 8): d.fill(x, 8, z, x, 10, z, PICE); d.set(x, 11, z, SL)
    d.landmark('rime_trial_circle', 'Rime Trial Circle', 'interactive', (px - 6, 7, pz - 6), (px + 6, 11, pz + 6))
    C.trial(d, 'rime_trial', (px, 8, pz), 'R2',
            [{'say': 'Frost spreads over the circle. Figures rise from the snow.', 'spawns': [C.mob('minecraft:stray', 3, 'Rime Wanderer')]},
             {'say': 'The cold deepens.', 'spawns': [C.mob('minecraft:stray', 3, 'Rime Wanderer'), C.mob('minecraft:zombie', 2, 'Frozen Husk')]}],
            [C.say('The frost lets go. A shard of ice rests on the plate.', 'gold'), C.give_key('frost_shard', (px, 8, pz))])
    # Frost Locker (discovery): a row of ice lockers in a bay on the south wall
    lx = 62
    S.nook(d, lx, 8, 40, 's', depth=4, w=5, h=4)
    d.set(lx - 2, 8, 42, b('barrel', facing='north', open=False)); d.set(lx + 2, 8, 42, b('barrel', facing='north', open=False))
    d.add_chest((lx, 8, 43), 'north', 'loot')
    d.fill(lx - 2, 11, 41, lx + 2, 11, 43, BICE)
    d.landmark('frost_locker', 'Frost Locker', 'discovery', (lx - 2, 8, 40), (lx + 2, 11, 43))
    # Thaw Valves (interactive): two valve wheels (levers) at the east end, thrown in order
    d.fill(112, 8, 12, 114, 11, 13, CAL) if False else None
    vx = 116
    for k, z in enumerate((14, 20)):
        d.fill(vx, 8, z, vx + 1, 10, z, b('iron_block')); d.set(vx - 1, 8, z, lever('wall', 'west')) if False else d.set(vx - 1, 9, z, lever('wall', 'west'))
        d.set(vx - 1, 8, z, b('iron_block')) if False else None
        d.fill(vx - 2, 8, z, vx - 2, 8, z, CAL)
    d.landmark('thaw_valve', 'Thaw Valve', 'interactive', (vx - 2, 8, 13), (vx + 1, 11, 21))
    d.air(118, 8, 27, 134, 11, 33)
    d.seal('thaw_wall', 'minecraft:packed_ice', (120, 8, 27), (122, 11, 33))
    C.lever_seq(d, 'thaw', [(vx - 1, 9, 14), (vx - 1, 9, 20)], [C.clear(C.bx((120, 8, 27), (122, 11, 33))), C.sound((120, 9, 30), 'minecraft:block.glass.break', 2, 0.5),
                                                            C.say('Steam hisses. The ice wall to the crevasse sags and melts away.', 'aqua')],
                on_step=[[C.sound((116, 9, 17), 'minecraft:block.piston.extend', 1.5, 0.6), C.say('A valve wheel groans round. One more.', 'aqua')]],
                jam='The valve is frozen solid. The first valve opens the way.')
    C.encounter(d, 'R2', 'Ice cracks overhead. The gallery is not empty.',
                [C.mob('minecraft:stray', 3, 'Rime Wanderer'), C.mob('minecraft:zombie', 3, 'Frozen Husk'), C.mob('minecraft:skeleton', 2, 'Icebow')])
    d.room('R2', 'Glacier Gallery', (52, 8, 10), (118, 24, 44))


# ================================================================ R3 Crevasse Crossing (x124..160, z12..52)
def crevasse(d):
    d.air(124, 2, 12, 160, 30, 52)
    d.fill(123, 1, 12, 161, 1, 52, CAL)
    d.air(118, 8, 27, 134, 11, 33)
    for x in range(124, 161):
        for z in range(12, 53):
            if d.rng.random() < 0.06: d.set(x, 2, z, SNOW)
            if d.rng.random() < 0.008: S.spire(d, x, z, 2, d.rng.randint(4, 9), 1, PICE)
    # the bridge: 3 wide packed ice at z29..31 from x124 to x160
    d.fill(124, 7, 29, 160, 7, 31, PICE)
    for x in range(124, 161, 4): d.fill(x, 8, 29, x, 8, 29, b('iron_bars')); d.fill(x, 8, 31, x, 8, 31, b('iron_bars'))
    for y in range(2, 8): d.set(124, y, 34, kit.ladder('east')); d.set(160, y, 26, kit.ladder('west'))
    d.air(122, 8, 33, 124, 11, 37); d.air(161, 8, 24, 164, 11, 28)
    d.air(161, 8, 28, 166, 11, 32)
    d.air(161, 8, 29, 166, 11, 31)
    # Ice Cave Nest (discovery): a hollow in the north wall of the crevasse floor
    d.air(138, 2, 8, 144, 6, 12)
    d.fill(138, 1, 8, 144, 1, 12, SNOW)
    d.add_chest((141, 2, 9), 'south', 'loot')
    for x in (139, 143): d.set(x, 2, 10, b('white_wool')); d.set(x, 3, 10, b('white_wool'))
    d.set(141, 2, 11, b('white_wool')); d.set(141, 2, 10, b('hay_block'))
    d.fill(138, 2, 8, 138, 4, 8, PICE); d.fill(144, 2, 8, 144, 4, 8, PICE); d.set(139, 2, 9, b('white_wool')); d.set(143, 2, 9, b('white_wool')); d.set(141, 3, 9, SL)
    d.landmark('ice_cave_nest', 'Ice Cave Nest', 'discovery', (138, 1, 8), (144, 6, 12))
    for z in range(14, 52, 7): d.set(123, 16, z, SL); d.set(161, 16, z, SL)
    C.crumble(d, 'bridge_ice', C.bx((124, 7, 29), (160, 7, 31)), only='packed_ice', delay=26, restore=170, sound='minecraft:block.glass.break')
    C.pulse(d, 'freezing_blasts', box=C.bx((124, 2, 12), (160, 30, 52)), period=84, warn=34, count=1, radius=1.8, damage=3.0, kind='freeze',
            warnParticle='minecraft:snowflake', particle='minecraft:snowflake', effect='minecraft:slowness', duration=100, amp=1,
            sound='minecraft:block.glass.break', vol=1.0, pitch=0.7, count_=0, **{'target': 'players'})
    d.room('R3', 'Crevasse Crossing', (123, 2, 8), (166, 30, 52))


# ================================================================ R4 Aurora Court (rect 170..216 x 8..52 with corner alcoves)
def court(d):
    x0, z0, x1, z1 = 166, 8, 214, 52
    d.air(x0, 8, z0, x1, 24, z1)
    S.floor_pattern(d, x0, z0, x1, z1, FLOOR, [CAL, SNOW], 4)
    d.air(161, 8, 29, 166, 11, 31)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    # painted aurora across the ceiling: stripes in the colour order cyan, magenta, lime, light blue
    for k, c in enumerate(AURORA):
        d.fill(x0 + 6 + k * 9, 25, z0, x0 + 6 + k * 9 + 5, 25, z1, b(c + '_stained_glass'))
        d.fill(x0 + 6 + k * 9, 24, z0 + 2, x0 + 6 + k * 9 + 5, 24, z0 + 2, b(c + '_concrete'))
    # Ice Crown (focal): a ring of leaning ice spikes round a dais in the middle
    d.disc(cx, cz, 7, 8, 8, CAL); d.disc(cx, cz, 5, 9, 9, QZ)
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 5, 10)):
        d.fill(x, 10, z, x, 14 + k % 3 * 2, z, BICE); d.set(x, 15 + k % 3 * 2, z, SL)
    d.fill(cx, 10, cz, cx, 18, cz, BICE); d.set(cx, 19, cz, SL)
    d.landmark('ice_crown', 'Ice Crown', 'focal', (cx - 7, 8, cz - 7), (cx + 7, 21, cz + 7))
    # Crystal Rime Tree (focal): a calcite trunk with blue-ice boughs in the north-west corner
    tx, tz = 176, 16
    d.fill(tx, 8, tz, tx, 16, tz, CAL)
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        d.line((tx, 14, tz), (tx + dx * 5, 17, tz + dz * 5), BICE); d.line((tx, 17, tz), (tx + dx * 4, 20, tz + dz * 4), b('white_stained_glass'))
    d.ellipsoid(tx, 19, tz, 5, 3, 5, WG, only_air=True)
    d.set(tx, 20, tz, SL)
    d.landmark('crystal_rime_tree', 'Crystal Rime Tree', 'focal', (tx - 6, 8, tz - 6), (tx + 6, 22, tz + 6))
    # Aurora Alignment Pedestals (interactive): four colour pedestals; press in the ceiling's order
    px = [cx - 12, cx - 4, cx + 4, cx + 12]
    btn = []
    for k, x in enumerate(px):
        z = 42
        d.fill(x - 1, 8, z - 1, x + 1, 8, z + 1, b(AURORA[k] + '_concrete'))
        d.set(x, 9, z, b('stone_button', face='floor', facing='north', powered=False))
        d.fill(x, 8, z + 2, x, 12, z + 2, CAL); d.set(x, 13, z + 2, b(AURORA[k] + '_stained_glass'))
        btn.append((x, 9, z))
    d.landmark('aurora_alignment_pedestal', 'Aurora Alignment Pedestal', 'interactive', (px[0] - 2, 7, 40), (px[3] + 2, 13, 44))
    # Preserved Relic Niche (discovery): behind the north wall
    d.air(cx - 2, 8, 3, cx + 2, 11, 7)
    d.air(cx - 3, 8, 0, cx + 3, 12, 3) if False else None
    d.add_chest((cx, 8, 4), 'south', 'loot')
    d.set(cx - 2, 8, 4, b('lantern', hanging=False, waterlogged=False)); d.fill(cx - 1, 8, 6, cx + 1, 8, 6, BICE)
    d.seal('relic_door', 'minecraft:packed_ice', (cx - 1, 8, 7), (cx + 1, 11, 8))
    d.fill(cx - 2, 8, 5, cx - 2, 9, 5, QZ); d.fill(cx + 2, 8, 5, cx + 2, 9, 5, QZ); d.set(cx + 2, 8, 4, b('lantern', hanging=False, waterlogged=False)); d.fill(cx - 2, 7, 3, cx + 2, 7, 6, QZ)
    d.landmark('preserved_relic_niche', 'Preserved Relic Niche', 'discovery', (cx - 3, 8, 3), (cx + 3, 12, 8))
    C.sequence(d, 'aurora', btn, [1, 0, 3, 2], [C.clear(C.bx((cx - 1, 8, 7), (cx + 1, 11, 8))), C.sound((cx, 9, 7), 'minecraft:block.glass.break', 2, 0.7),
                                                C.say('The aurora flares and a sealed niche opens in the north wall.', 'light_purple')],
               'The aurora ripples in order.', 'The colours scatter. Follow the ceiling: cyan, magenta, lime, light blue... or so it seems.')
    # Frost Offering (interactive) with the Rime Vault gate behind it
    ox_, oz_ = cx, 49
    d.fill(ox_, 8, oz_, ox_, 9, oz_, QZ); d.set(ox_, 10, oz_, b('blue_ice'))
    for x in (ox_ - 2, ox_ + 2): d.fill(x, 8, oz_, x, 12, oz_, PICE); d.set(x, 13, oz_, SL)
    d.landmark('frost_offering', 'Frost Offering', 'interactive', (ox_ - 2, 8, oz_ - 1), (ox_ + 2, 13, oz_ + 1))
    d.air(cx - 1, 8, 53, cx + 1, 11, 66)
    d.seal('vault_wall', 'minecraft:packed_ice', (cx - 1, 8, 55), (cx + 1, 11, 56))
    C.lock(d, 'offering', C.bx((ox_, 10, oz_)), 'frost_shard', [C.clear(C.bx((cx - 1, 8, 55), (cx + 1, 11, 56))), C.sound((cx, 9, 55), 'minecraft:block.glass.break', 2, 0.5),
                                                              C.say('The offering block shudders and the rime wall behind it falls away.', 'aqua')],
           'A block of blue ice with a hollow the size of a shard.', 'lays the shard in the ice and the wall behind falls away.')
    d.air(x1, 8, 29, x1 + 24, 11, 31)
    d.seal('arena_door', 'minecraft:packed_ice', (x1 + 2, 8, 29), (x1 + 3, 11, 31))
    C.lock(d, 'arena_door', C.bx((x1 + 2, 8, 29), (x1 + 3, 11, 31)), 'winter_key', [C.clear(C.bx((x1 + 2, 8, 29), (x1 + 3, 11, 31))),
                                                                                 C.sound((x1 + 2, 9, 30), 'minecraft:block.glass.break', 2, 0.5), C.say('The rime door to the Crown of Winter shatters.', 'gold')],
           'A door of black ice with a keyhole frozen over.', 'sets the Winter Key and the door of black ice shatters.')
    C.encounter(d, 'R4', 'The aurora paints the ceiling in four colours. Pedestals wait in the south.',
                [C.mob('minecraft:stray', 3, 'Rime Wanderer'), C.mob('minecraft:skeleton', 3, 'Icebow')])
    d.room('R4', 'Aurora Court', (x0, 8, 0), (x1, 25, 54))


# ================================================================ R5 Rime Vault (x168..214, z67..100)
def vault(d):
    cx = 190
    d.air(168, 8, 67, 212, 18, 100)
    d.air(cx - 1, 8, 53, cx + 1, 11, 66)
    S.floor_pattern(d, 168, 67, 212, 100, FLOOR, [BICE, CAL], 2)
    for x in range(172, 210, 8):
        for z in (72, 94):
            S.crystal(d, x, z, 8, 6, BICE, b('white_stained_glass')); d.set(x + 1, 8, z + 1, SL)
    for x in range(176, 206, 6): d.fill(x, 8, 82, x, 11, 82, PICE); d.set(x, 12, 82, SL)
    d.add_chest((cx, 8, 97), 'north', 'key', key='winter_key')
    d.fill(cx - 2, 8, 98, cx + 2, 10, 99, BICE)
    d.room('R5', 'Rime Vault', (168, 8, 57), (212, 18, 100))


# ================================================================ R6 Crown of Winter (centre 232,30 r22)
def crown(d):
    cx, cz = ARENA
    d.air(214, 8, 29, 237, 11, 31)
    d.disc(cx, cz, 22, 8, 30, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, CAL)
    d.disc(cx, cz, 18, FLOOR, FLOOR, PICE, rim=12)
    d.disc(cx, cz, 7, 8, 8, QZ)
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 14, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 22, z + 1, BICE)
        for yy in (23, 25): d.set(x, yy, z, PICE)
        d.set(x, 24, z, BICE)
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, BICE); targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): S.spire(d, x, z, 8, 9, 1, PICE); d.set(x, 18, z, SL)
    C.boss(d, 'winter_warden', 'R6', 'minecraft:stray', 'The Winter Warden', (cx, 9, cz), targets=targets,
           on_block='minecraft:blue_ice', off_block='minecraft:snow_block', health=6.0, scale=2.0, color='WHITE',
           equipment={'mainhand': 'minecraft:bow', 'head': 'minecraft:iron_helmet'},
           intro='The Winter Warden steps out of the frost. Shatter the three ice crowns on the pillars to break its ward.',
           death='The Winter Warden crumbles into snow.', break_text='The crowns shatter! Strike the Warden now!',
           adds=[C.mob('minecraft:stray', 2, 'Rime Wanderer') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('A blizzard rolls through the crown.', 'red'), C.spawn('minecraft:stray', 2, 'R6', 'Rime Wanderer')]},
                   {'at': 0.33, 'effects': [C.say('The Warden calls the deep winter.', 'red'), C.spawn('minecraft:zombie', 3, 'R6', 'Frozen Husk')]}])
    C.pulse(d, 'blizzard', box=C.bx((238, 8, 8), (282, 30, 52)), period=64, warn=30, count=2, radius=2.0, damage=3.5, kind='freeze',
            warnParticle='minecraft:snowflake', particle='minecraft:snowflake', effect='minecraft:slowness', duration=100, amp=1,
            sound='minecraft:block.glass.break', after='boss_awake:winter_warden', until='boss:winter_warden', scatter=True, **{'target': 'players'})
    C.finish(d, 'winter_warden', 'The Glacier Crown. Balance the shrine, melt the valves, and cross the crevasse.',
             'The winter breaks. The crown closes shortly.')
    d.room('R6', 'Crown of Winter', (238, 8, 8), (282, 31, 52))
