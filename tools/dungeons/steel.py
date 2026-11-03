"""The Sovereign Foundry: the Steel dungeon (id `steel`).

Rooms: Reclaimed Foundry -> Assembly Gallery -> Gantry Crossing -> Anvil Court -> Armory Vault; arena Sovereign Forge.
Layout : rectilinear factory halls; the Gantry Crossing is a two-level gantry over a stamping pit: a lower deck, a lift pad (dead until the Gantry Control
         levers are thrown) and an upper deck that descends to the far side. Hydraulic presses slam down along the upper deck after a telegraph.
Puzzle : Assembly Alignment (four levers must match the redstone/iron gear clues) opens the gantry gate; the Riveter Trial yields a Sovereign Ingot, offered at
         the Anvil Court's forge to open the Armory Vault; its Sovereign Key opens the arena. Gantry Control also opens the Sealed Parts Vault in the gallery.
Boss   : the Iron Sovereign (iron golem) behind three redstone wards; presses slam on the forge floor while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever

W, H, L = 330, 46, 140
FLOOR = 7
SS, SSB, IRON, SMOOTH, ANV = b('stone'), b('stone_bricks'), b('iron_block'), b('smooth_stone'), b('anvil', facing='north')
POL, CBS, RED, COP, GRATE = b('polished_andesite'), b('cracked_stone_bricks'), b('redstone_block'), b('waxed_copper_block'), b('waxed_copper_grate')
BLAST, LAMP = b('blast_furnace', facing='south', lit=False), b('redstone_lamp', lit=False)
CHN = b('chain', axis='y', waterlogged=False)
GLOW = b('sea_lantern')
COURT, ARENA = (226, 32), (292, 32)


def build():
    d = kit.Dungeon('steel', 'The Sovereign Foundry', W, H, L, biome='minecraft:windswept_hills')
    d.loot_table = 'ascensiondungeons:chests/ancient_foundry'
    d.shell = ('stone', 'stone_bricks', 'cracked_stone_bricks', 'polished_andesite', 'andesite', 'iron_block')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, SS)
    C.key(d, 'sovereign_ingot', 'minecraft:iron_ingot', 'Sovereign Ingot', 'Still warm. The Forging Offering in the Anvil Court wants it.')
    C.key(d, 'sovereign_key', 'minecraft:tripwire_hook', 'Sovereign Key', 'Opens the blast door to the Sovereign Forge.')
    foundry(d); assembly(d); gantry(d); anvil(d); armory(d); forge(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R4', False), ('assembly', 'R3', True), ('assembly', 'R4', False), ('gantry', 'R4', True),
                     ('gantry', 'R5', False), ('offering', 'R5', True), ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, SS, [(SSB, 0.22), (b('andesite'), 0.12), (CBS, 0.06), (b('polished_andesite'), 0.06), (b('iron_block'), 0.01)])
    return d


def machine(d, x, z, w=3, h=3, dep=3, y=8):
    d.fill(x, y, z, x + w - 1, y + h - 1, z + dep - 1, b('iron_block') if (x + z) % 2 else b('smooth_stone'))
    d.fill(x, y + h, z, x + w - 1, y + h, z + dep - 1, b('iron_trapdoor', facing='north', half='bottom', open=False, powered=False, waterlogged=False)) if False else None
    d.set(x + w // 2, y + h, z + dep // 2, CHN)
    d.set(x, y + 1, z, b('redstone_lamp', lit=False)) if False else None


# ================================================================ R1 Reclaimed Foundry (x6..56, z8..52)
def foundry(d):
    d.air(6, 8, 8, 56, 24, 52)
    S.floor_pattern(d, 6, 8, 56, 52, FLOOR, [SSB, SMOOTH, POL], 3)
    d.entry = (9, 8, 30)
    for x in range(10, 56, 8):
        d.fill(x, 8, 8, x, 24, 8, b('iron_block')); d.fill(x, 8, 52, x, 24, 52, b('iron_block'))
        d.set(x, 22, 9, GLOW) if False else None
    for x in range(12, 56, 10): d.set(x, 24, 30, GLOW)
    # Foundry Furnace Heart (focal): a stacked blast-furnace tower with a chimney
    fx, fz = 40, 38
    d.fill(fx - 3, 8, fz - 3, fx + 3, 8, fz + 3, SSB)
    d.fill(fx - 2, 9, fz - 2, fx + 2, 13, fz + 2, b('polished_andesite'))
    for k, (dx, dz) in enumerate(((-2, -3), (0, -3), (2, -3))): d.set(fx + dx, 9, fz + dz, BLAST) if False else d.set(fx + dx, 9, fz - 3, b('blast_furnace', facing='north', lit=False))
    d.fill(fx - 1, 14, fz - 1, fx + 1, 20, fz + 1, b('stone_bricks')); d.fill(fx, 21, fz, fx, 23, fz, b('iron_bars'))
    d.set(fx, 13, fz, b('iron_block'))
    for (x, z) in S.pts_ring(fx, fz, 3, 8): d.set(x, 9, z, b('iron_bars'))
    d.landmark('foundry_furnace_heart', 'Foundry Furnace Heart', 'focal', (fx - 3, 8, fz - 3), (fx + 3, 23, fz + 3))
    # Iron Colossus (focal): a hulking iron figure standing at the east wall
    cx, cz = 52, 20
    S.statue(d, cx, cz, 8, IRON, SSB, eye=b('redstone_block'), face='w')
    d.fill(cx - 3, 14, cz - 1, cx - 3, 17, cz + 1, IRON) if False else None
    d.fill(cx - 1, 20, cz - 1, cx + 1, 22, cz + 1, IRON)
    d.landmark('iron_colossus', 'Iron Colossus', 'focal', (cx - 3, 8, cz - 3), (cx + 3, 22, cz + 3))
    # Reclaimed Machine Habitat (discovery): a nook of old parts in the north wall
    hx = 22
    S.nook(d, hx, 8, 8, 'n', depth=4, w=5, h=4)
    d.fill(hx - 2, 8, 5, hx + 2, 8, 5, b('iron_block')); d.set(hx, 9, 5, b('piston', facing='up', extended=False)) if False else d.set(hx, 9, 5, b('piston', facing='south', extended=False))
    d.set(hx - 2, 9, 5, b('hopper', facing='down', enabled=True)); d.set(hx + 2, 9, 5, b('dispenser', facing='south', triggered=False))
    d.add_chest((hx, 8, 4), 'south', 'loot')
    d.landmark('reclaimed_machine_habitat', 'Reclaimed Machine Habitat', 'discovery', (hx - 2, 8, 4), (hx + 2, 11, 8))
    # Armorer Station (interactive)
    ax, az = 14, 46
    S.hut(d, ax - 3, az - 3, ax + 3, az + 3, 8, 4, SSB, b('smooth_stone'), door='e', floor=POL)
    d.set(ax - 2, 8, az - 2, b('blast_furnace', facing='south', lit=False)); d.set(ax, 8, az - 2, b('anvil', facing='east')); d.set(ax + 2, 8, az - 2, b('grindstone', face='floor', facing='north'))
    d.set(ax - 2, 8, az + 2, b('smithing_table')); d.set(ax + 2, 8, az + 2, b('barrel', facing='up', open=False)); d.set(ax, 11, az, b('lantern', hanging=True, waterlogged=False))
    d.landmark('armorer_station', 'Armorer Station', 'interactive', (ax - 4, 7, az - 4), (ax + 4, 12, az + 4))
    C.zone(d, 'armorer_plating', C.bx((ax - 3, 8, az - 3), (ax + 3, 11, az + 3)), 'minecraft:resistance', 0, 300, 100)
    d.air(56, 8, 29, 70, 11, 31)
    d.room('R1', 'Reclaimed Foundry', (6, 8, 4), (56, 24, 52))


# ================================================================ R2 Assembly Gallery (x62..130, z14..46)
def assembly(d):
    d.air(62, 8, 14, 130, 22, 46)
    S.floor_pattern(d, 62, 14, 130, 46, FLOOR, [SMOOTH, POL], 2)
    for z in (22, 40):                                                    # conveyor belts
        d.fill(64, FLOOR, z, 128, FLOOR, z + 1, b('smooth_stone')); d.fill(64, 8, z, 128, 8, z, b('stone_slab', type='bottom', waterlogged=False)) if False else None
        for x in range(66, 128, 4): d.set(x, 8, z, b('smooth_stone_slab', type='bottom', waterlogged=False)); d.set(x + 1, 8, z + 1, b('iron_trapdoor', facing='north', half='bottom', open=False, powered=False, waterlogged=False))
    for x in range(66, 130, 8):
        d.fill(x, 8, 14, x, 22, 14, IRON); d.fill(x, 8, 46, x, 22, 46, IRON)
    for x in range(68, 128, 10): d.set(x, 22, 30, GLOW)
    # Assembly Crane (focal): twin gantry rails, a trolley and a chained hook
    cx = 96
    d.fill(cx, 8, 16, cx, 20, 16, IRON); d.fill(cx, 8, 44, cx, 20, 44, IRON)
    d.fill(cx - 1, 21, 16, cx + 1, 21, 44, b('iron_block')) if False else None
    d.fill(cx, 21, 16, cx, 21, 44, IRON)
    d.fill(cx - 1, 20, 28, cx + 1, 20, 32, SSB); d.fill(cx, 12, 30, cx, 19, 30, CHN)
    d.fill(cx - 1, 10, 29, cx + 1, 11, 31, IRON); d.set(cx, 9, 30, b('iron_bars'))
    d.landmark('assembly_crane', 'Assembly Crane', 'focal', (cx - 1, 8, 16), (cx + 1, 21, 44))
    # Riveter Trial (interactive): a plate on the shop floor
    px, pz = 72, 30
    d.disc(px, pz, 4, FLOOR, FLOOR, SSB); d.disc(px, pz, 1, FLOOR, FLOOR, IRON)
    d.set(px, 8, pz, b('heavy_weighted_pressure_plate', power=0))
    for (x, z) in S.pts_ring(px, pz, 4, 6): d.fill(x, 8, z, x, 10, z, IRON); d.set(x, 11, z, GLOW)
    d.landmark('riveter_trial', 'Riveter Trial', 'interactive', (px - 5, 7, pz - 5), (px + 5, 11, pz + 5))
    C.trial(d, 'riveter_trial', (px, 8, pz), 'R2',
            [{'say': 'The riveters start. Something answers the noise.', 'spawns': [C.mob('minecraft:zombie', 3, 'Scrap Drone', equipment={'mainhand': 'minecraft:iron_axe'}), C.mob('minecraft:skeleton', 2, 'Bolt Gunner')]},
             {'say': 'The line speeds up.', 'spawns': [C.mob('minecraft:zombie', 4, 'Scrap Drone'), C.mob('minecraft:skeleton', 2, 'Bolt Gunner')]}],
            [C.say('The line stops. A Sovereign Ingot lies on the plate, still warm.', 'gold'), C.give_key('sovereign_ingot', (px, 8, pz))])
    # Sealed Parts Vault (discovery): a shuttered bay, opened remotely by the Gantry Control
    vx = 112
    d.air(vx, 8, 10, vx + 8, 12, 13)
    d.fill(vx, 7, 10, vx + 8, 7, 13, IRON)
    d.add_chest((vx + 4, 8, 11), 'south', 'loot')
    for x in (vx + 1, vx + 7): d.fill(x, 8, 10, x, 10, 10, b('iron_bars')) if False else d.set(x, 8, 11, b('barrel', facing='south', open=False))
    d.set(vx + 4, 11, 11, b('lantern', hanging=True, waterlogged=False)) if False else d.set(vx + 2, 8, 12, b('anvil', facing='north'))
    d.seal('parts_shutter', 'minecraft:iron_block', (vx + 3, 8, 14), (vx + 5, 11, 14))
    d.air(vx + 3, 8, 14, vx + 5, 11, 14)
    d.landmark('sealed_parts_vault', 'Sealed Parts Vault', 'discovery', (vx, 7, 10), (vx + 8, 12, 14))
    # Assembly Alignment (interactive): four levers on the south wall under redstone (on) / iron (off) gear clues
    pats = [True, True, False, True]
    lv = []
    for k, x in enumerate((100, 105, 110, 115)):
        C.wall_lever(d, x, 8, 46, 's')
        d.fill(x, 9, 46, x, 10, 46, kit.AIR)
        d.set(x, 11, 46, RED if pats[k] else IRON)
        lv.append(((x, 8, 46), pats[k]))
    d.landmark('assembly_alignment', 'Assembly Alignment', 'interactive', (98, 7, 43), (117, 12, 46))
    d.air(130, 8, 29, 138, 11, 33)
    d.seal('line_gate', 'minecraft:iron_block', (132, 8, 29), (133, 11, 33))
    C.toggle(d, 'assembly', lv, [C.clear(C.bx((132, 8, 29), (133, 11, 33))), C.sound((132, 9, 31), 'minecraft:block.piston.extend', 2, 0.5),
                                 C.say('The line locks. A blast gate opens onto the stamping gantry.', 'gray')],
             'The gears mesh. The line is aligned.')
    C.encounter(d, 'R2', 'Belts run empty. Something is still working the line.',
                [C.mob('minecraft:zombie', 3, 'Scrap Drone'), C.mob('minecraft:skeleton', 3, 'Bolt Gunner')])
    d.room('R2', 'Assembly Gallery', (62, 8, 10), (130, 22, 46))


# ================================================================ R3 Gantry Crossing (pit x136..190, z12..52; decks along z31..33)
def gantry(d):
    d.air(136, 2, 12, 190, 36, 52)
    d.fill(135, 1, 12, 191, 1, 52, SMOOTH)
    d.air(130, 8, 29, 140, 11, 33); d.air(134, 8, 28, 135, 11, 36)
    # lower deck: west rim to x150 (feet y8); upper deck x151..190 (feet y16) then a stair down to the east rim
    d.fill(136, 7, 31, 150, 7, 33, IRON)
    for x in range(138, 150, 4): d.fill(x, 8, 31, x, 8, 31, b('iron_bars')); d.fill(x, 8, 33, x, 8, 33, b('iron_bars'))
    d.fill(151, 15, 31, 182, 15, 33, GRATE)
    for x in range(152, 183, 6): d.fill(x, 1, 31, x, 14, 33, IRON)
    for x in range(152, 183, 4): d.fill(x, 16, 31, x, 16, 31, b('iron_bars')); d.fill(x, 16, 33, x, 16, 33, b('iron_bars'))
    for i in range(8):
        x = 183 + i
        d.fill(x, 1, 31, x, 15 - i, 33, IRON)
    d.air(191, 8, 28, 200, 11, 36)
    for y in range(2, 8): d.set(136, y, 29, kit.ladder('east'))
    for y in range(2, 8): d.set(189, y, 35, kit.ladder('east')) if False else None
    d.fill(150, 2, 12, 150, 6, 52, b('iron_bars'))                       # the pit floor is split: fallers return to the side they fell from
    for y in range(2, 8): d.set(190, y, 29, kit.ladder('west'))
    # lift pad from the lower deck to the upper deck, dead until the Gantry Control is used
    d.fill(147, 7, 31, 149, 7, 33, b('smooth_stone')); d.fill(147, 6, 31, 149, 6, 33, GLOW) if False else None
    for (x, z) in ((147, 30), (149, 30), (147, 34), (149, 34)): d.set(x, 8, z, b('redstone_lamp', lit=False))
    C.pad(d, 'gantry_lift', C.bx((147, 8, 31), (149, 8, 33)), (153, 16, 32), 'The lift hauls you up to the upper deck.', 36, requires='gantry')
    # Gantry Control (interactive): two levers in a control booth on the west rim, in order
    d.fill(131, 8, 27, 135, 11, 27, IRON)
    C.wall_lever(d, 132, 9, 28, 'n'); C.wall_lever(d, 134, 9, 28, 'n')
    d.landmark('gantry_control', 'Gantry Control', 'interactive', (131, 8, 27), (135, 12, 29))
    C.lever_seq(d, 'gantry', [(132, 9, 28), (134, 9, 28)],
                [C.sound((148, 8, 32), 'minecraft:block.piston.extend', 2, 0.6), C.say('Hydraulics howl. The lift pad on the lower deck comes alive, and a shutter opens in the Assembly Gallery.', 'gray'),
                 C.clear(C.bx((115, 8, 14), (117, 11, 14)))],
                on_step=[[C.sound((132, 9, 28), 'minecraft:block.piston.extend', 1.5, 0.8), C.say('A pressure gauge climbs. One more lever.', 'gray')]],
                jam='The control board is locked. The west lever first.')
    # Maintenance Tunnel Locker (discovery): a service tunnel off the west corridor
    d.air(192, 8, 37, 200, 11, 43)
    d.fill(192, 7, 37, 200, 7, 43, IRON)
    d.add_chest((199, 8, 43), 'north', 'loot'); d.set(194, 8, 43, b('barrel', facing='north', open=False)); d.set(196, 8, 43, b('barrel', facing='north', open=False))
    d.set(197, 8, 40, b('anvil', facing='north')); d.set(195, 10, 40, b('lantern', hanging=True, waterlogged=False)) if False else None
    d.landmark('maintenance_tunnel_locker', 'Maintenance Tunnel Locker', 'discovery', (192, 7, 37), (200, 11, 43))
    for z in range(14, 52, 8): d.set(135, 18, z, GLOW); d.set(191, 18, z, GLOW)
    C.zone(d, 'pit_fall', C.bx((136, 2, 12), (190, 14, 52)), 'minecraft:slow_falling', 0, 60, 10)
    C.pulse(d, 'presses', cells=[[160, 16, 32], [168, 16, 32], [176, 16, 32]], period=84, warn=44, radius=2.0, damage=4.5, kind='fall', height=2.5, stagger=28,
            push=[0.0, -0.3, 0.0], warnParticle='minecraft:crit', particle='minecraft:explosion', count=6, sound='minecraft:block.anvil.land', vol=1.0, pitch=0.6)
    d.room('R3', 'Gantry Crossing', (136, 2, 12), (192, 36, 52))


# ================================================================ R4 Anvil Court (rect x202..250, z8..56)
def anvil(d):
    x0, z0, x1, z1 = 202, 8, 250, 56
    d.air(192, 8, 28, 201, 11, 36)
    d.air(x0, 8, z0, x1, 26, z1)
    S.floor_pattern(d, x0, z0, x1, z1, FLOOR, [SMOOTH, POL, SSB], 4)
    cx, cz = COURT
    for x in range(206, 250, 8):
        d.fill(x, 8, z0, x, 26, z0, IRON); d.fill(x, 8, z1, x, 26, z1, IRON); d.set(x, 22, z0 + 1, GLOW)
    for z in range(14, 56, 8): d.fill(x0, 8, z, x0, 26, z, IRON); d.fill(x1, 8, z, x1, 26, z, IRON)
    # Monumental Anvil (focal): a colossal anvil of iron on a stepped plinth
    d.fill(cx - 6, 8, cz - 4, cx + 6, 8, cz + 4, SSB); d.fill(cx - 5, 9, cz - 3, cx + 5, 9, cz + 3, POL)
    d.fill(cx - 2, 10, cz - 2, cx + 2, 13, cz + 2, IRON)
    d.fill(cx - 6, 14, cz - 3, cx + 6, 16, cz + 3, IRON)
    d.fill(cx + 6, 15, cz - 1, cx + 9, 16, cz + 1, IRON); d.fill(cx - 7, 15, cz - 2, cx - 7, 16, cz + 2, IRON)
    d.set(cx, 17, cz, GLOW); d.fill(cx - 3, 17, cz - 1, cx + 3, 17, cz + 1, b('smooth_stone'))
    d.landmark('monumental_anvil', 'Monumental Anvil', 'focal', (cx - 8, 8, cz - 5), (cx + 10, 18, cz + 5))
    # Buried Blueprint Desk (discovery): a drafting nook in the north-west corner
    bx_, bz_ = 206, 14
    d.fill(bx_, 8, bz_, bx_ + 5, 8, bz_ + 1, b('smooth_stone_slab', type='bottom', waterlogged=False)) if False else d.fill(bx_, 8, bz_, bx_ + 5, 8, bz_ + 1, SMOOTH)
    d.set(bx_ + 1, 9, bz_, b('lectern', facing='south', powered=False)); d.set(bx_ + 3, 9, bz_, b('cartography_table')); d.set(bx_ + 5, 9, bz_, b('crafting_table'))
    d.add_chest((bx_ + 3, 8, bz_ + 3), 'north', 'loot'); d.fill(bx_ - 1, 8, bz_ - 1, bx_ - 1, 11, bz_ + 4, IRON); d.fill(bx_ + 6, 8, bz_ - 1, bx_ + 6, 11, bz_ + 4, IRON)
    d.landmark('buried_blueprint_desk', 'Buried Blueprint Desk', 'discovery', (bx_ - 1, 8, bz_ - 1), (bx_ + 6, 11, bz_ + 4))
    # Forging Offering (interactive): a forge in the south; the Armory Vault door behind it
    ox, oz = cx, 50
    d.fill(ox, 8, oz, ox, 9, oz, SSB); d.set(ox, 10, oz, b('blast_furnace', facing='north', lit=False))
    for x in (ox - 2, ox + 2): d.fill(x, 8, oz, x, 12, oz, IRON); d.set(x, 13, oz, GLOW)
    d.landmark('forging_offering', 'Forging Offering', 'interactive', (ox - 2, 8, oz - 1), (ox + 2, 13, oz + 1))
    d.air(cx - 1, 8, 57, cx + 1, 11, 66)
    d.seal('armory_door', 'minecraft:iron_block', (cx - 1, 8, 58), (cx + 1, 11, 59))
    C.lock(d, 'offering', C.bx((ox, 10, oz)), 'sovereign_ingot', [C.clear(C.bx((cx - 1, 8, 58), (cx + 1, 11, 59))), C.sound((cx, 9, 58), 'minecraft:block.piston.extend', 2, 0.5),
                                                                C.say('The forge roars around the ingot. A heavy blast door lifts in the south wall.', 'gray')],
           'A cold forge, its crucible empty and waiting for a bar.', 'sets the Sovereign Ingot in the forge and the blast door lifts.')
    d.air(x1, 8, 31, 270, 11, 33)
    d.seal('arena_door', 'minecraft:iron_block', (x1 + 3, 8, 31), (x1 + 4, 11, 33))
    C.lock(d, 'arena_door', C.bx((x1 + 3, 8, 31), (x1 + 4, 11, 33)), 'sovereign_key', [C.clear(C.bx((x1 + 3, 8, 31), (x1 + 4, 11, 33))),
                                                                                       C.sound((x1 + 3, 9, 32), 'minecraft:block.piston.extend', 2, 0.4), C.say('The blast door to the Sovereign Forge grinds open.', 'gold')],
           'A blast door with a keyway cut like a gear.', 'turns the Sovereign Key and the blast door grinds open.')
    C.encounter(d, 'R4', 'The anvil rings once, by itself. A cold forge waits in the south.',
                [C.mob('minecraft:zombie', 3, 'Scrap Drone'), C.mob('minecraft:skeleton', 3, 'Bolt Gunner')])
    d.room('R4', 'Anvil Court', (x0, 8, 4), (x1, 26, 56))


# ================================================================ R5 Armory Vault (x204..248, z68..100)
def armory(d):
    cx = COURT[0]
    d.air(204, 8, 68, 248, 18, 100)
    d.air(cx - 1, 8, 57, cx + 1, 11, 68)
    S.floor_pattern(d, 204, 68, 248, 100, FLOOR, [SMOOTH, IRON], 2)
    for x in range(208, 246, 6):
        for z in (72, 92):
            d.fill(x, 8, z, x + 1, 12, z + 1, IRON); d.set(x, 13, z, GLOW)
            d.set(x + 2, 8, z, b('anvil', facing='north')); d.set(x - 1, 8, z, b('smithing_table'))
    d.add_chest((cx, 8, 97), 'north', 'key', key='sovereign_key')
    d.fill(cx - 2, 8, 98, cx + 2, 10, 99, IRON); d.set(cx - 2, 8, 97, b('lantern', hanging=False, waterlogged=False)); d.set(cx + 2, 8, 97, b('lantern', hanging=False, waterlogged=False))
    d.landmark('armory_cache', 'Armory Cache', 'discovery', (cx - 2, 8, 96), (cx + 2, 10, 99))
    d.room('R5', 'Armory Vault', (204, 8, 61), (248, 18, 100))


# ================================================================ R6 Sovereign Forge (rect x270..314, z10..54)
def forge(d):
    cx, cz = ARENA
    d.air(250, 8, 31, 270, 11, 33)
    d.air(270, 8, 10, 314, 30, 54)
    S.floor_pattern(d, 270, 10, 314, 54, FLOOR, [SMOOTH, IRON, POL], 4)
    d.fill(cx - 8, 8, cz - 8, cx + 8, 8, cz + 8, SSB); d.fill(cx - 5, 9, cz - 5, cx + 5, 9, cz + 5, SMOOTH)
    # Gear Crown (focal): a great cog on the ceiling above the floor with a chain to a crown of spikes
    for r in range(0, 9):
        d.disc(cx, cz, 8 - r // 3, 27 + (1 if r > 6 else 0), 27 + (1 if r > 6 else 0), IRON if r % 2 else SMOOTH) if r < 3 else None
    d.disc(cx, cz, 8, 28, 28, IRON); d.disc(cx, cz, 6, 28, 28, SMOOTH, rim=2)
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 9, 12)): d.fill(x, 27, z, x, 28, z, IRON)
    d.fill(cx, 20, cz, cx, 27, cz, CHN); d.set(cx, 19, cz, GLOW)
    d.landmark('gear_crown', 'Gear Crown', 'focal', (cx - 9, 8, cz - 9), (cx + 9, 29, cz + 9))
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 15, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 24, z + 1, IRON)
        d.set(x, 25, z, GLOW) if False else d.set(x, 25, z, b('iron_bars'))
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, RED); targets.append(C.bx((tx, 9, z)))
    for x in range(274, 314, 8): d.fill(x, 8, 10, x, 24, 10, IRON); d.fill(x, 8, 54, x, 24, 54, IRON); d.set(x, 22, 11, GLOW)
    C.boss(d, 'iron_sovereign', 'R6', 'minecraft:iron_golem', 'The Iron Sovereign', (cx, 10, cz), targets=targets,
           on_block='minecraft:redstone_block', off_block='minecraft:iron_block', health=3.0, scale=1.7, color='WHITE',
           intro='The Iron Sovereign unfolds from the anvil. Smash the three redstone cores on the pillars to cut its power.',
           death='The Iron Sovereign grinds to a halt and kneels.', break_text='The cores die! The Iron Sovereign is exposed!',
           adds=[C.mob('minecraft:skeleton', 2, 'Bolt Gunner') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('Presses slam in the ceiling.', 'red'), C.spawn('minecraft:zombie', 2, 'R6', 'Scrap Drone')]},
                   {'at': 0.33, 'effects': [C.say('The forge floods with scrap.', 'red'), C.spawn('minecraft:skeleton', 2, 'R6', 'Bolt Gunner'), C.spawn('minecraft:zombie', 2, 'R6', 'Scrap Drone')]}])
    C.pulse(d, 'forge_presses', box=C.bx((272, 8, 12), (312, 30, 52)), period=60, warn=36, count=2, radius=2.2, damage=4.5, kind='fall', height=2.5,
            push=[0.0, -0.3, 0.0], warnParticle='minecraft:crit', particle='minecraft:explosion', sound='minecraft:block.anvil.land', pitch=0.6,
            after='boss_awake:iron_sovereign', until='boss:iron_sovereign', scatter=True, **{'target': 'players'})
    C.finish(d, 'iron_sovereign', 'The Sovereign Foundry. Align the line, throw the gantry levers, and bring an ingot to the court.', 'The forge cools. The foundry closes shortly.')
    d.room('R6', 'Sovereign Forge', (272, 8, 10), (314, 31, 54))
