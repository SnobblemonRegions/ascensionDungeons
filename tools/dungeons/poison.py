"""The Venomheart Marsh: the Poison dungeon (id `poison`).

Rooms: Fungal Marsh -> Alchemical Gallery -> Sludge Causeway -> Spore Court -> Quarantine Vault; arena Venomheart Chamber.
Layout : the Sludge Causeway is a ZIGZAG walkway (west, north, east, south, east) over a lake of sludge; falling in poisons you, ladders on support columns lead back.
Hazard : toxic gas bursts land where you stand after a telegraph (poison on top of the damage) and the causeway tiles slough away under your feet.
         The Ventilation Switch on the last leg clears the air for good.
Puzzle : the two Purification Valves (in order) open the gallery to the causeway; the Spore Trial yields a Reagent, offered at the Spore Court to open the
         Quarantine Vault; its Venom Key opens the arena. The valves also open the Sealed Reagent Cache; the Ventilation Switch opens the Hidden Drain Passage.
Boss   : the Venomheart (cave spider) behind three slime wards; gas bursts fill the chamber while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever
import numpy as np

W, H, L = 300, 46, 140
FLOOR = 7
MS, GT, MOSS, MYC, PODZ = b('mossy_stone_bricks'), b('green_terracotta'), b('moss_block'), b('mycelium'), b('podzol')
RMB = b('red_mushroom_block', up=True, down=True, north=True, south=True, east=True, west=True)
BMB = b('brown_mushroom_block', up=True, down=True, north=True, south=True, east=True, west=True)
STEM = b('mushroom_stem', up=True, down=True, north=True, south=True, east=True, west=True)
SLIME, OXC, GLASS, LIME = b('slime_block'), b('waxed_oxidized_copper'), b('lime_stained_glass'), b('lime_concrete')
YC, BC, GLOW = b('yellow_concrete'), b('black_concrete'), b('shroomlight')
COURT, ARENA = (214, 32), (272, 32)


def build():
    d = kit.Dungeon('poison', 'The Venomheart Marsh', W, H, L, biome='minecraft:swamp')
    d.loot_table = 'ascensiondungeons:chests/fungal_grotto'
    d.shell = ('mossy_stone_bricks', 'green_terracotta', 'moss_block', 'stone_bricks', 'cracked_stone_bricks', 'mud_bricks')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, MS)
    C.key(d, 'reagent', 'minecraft:fermented_spider_eye', 'Venom Reagent', 'Sickly green and still bubbling. The Reagent Offering in the Spore Court wants it.')
    C.key(d, 'venom_key', 'minecraft:tripwire_hook', 'Venom Key', 'Opens the sealed hatch to the Venomheart Chamber.')
    marsh(d); lab(d); causeway(d); court(d); vault(d); chamber(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R5', False), ('purify', 'R3', True), ('purify', 'R4', True), ('purify', 'R5', False), ('offering', 'R5', True),
                     ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, MS, [(GT, 0.10), (b('stone_bricks'), 0.14), (b('cracked_stone_bricks'), 0.08), (b('mud_bricks'), 0.06), (MOSS, 0.08)])
    return d


# ================================================================ R1 Fungal Marsh (cave around 24,30)
def marsh(d):
    d.ellipsoid(24, 16, 30, 22, 15, 19, kit.AIR)
    d.fill(2, 0, 10, 48, FLOOR, 52, MS)
    for x in range(3, 48):
        for z in range(11, 52):
            if d.get(x, 8, z) == kit.AIR:
                fl = d.rng.choice([MYC, PODZ, MOSS, MYC, MS])
                d.set(x, FLOOR, z, fl)
                if fl == MYC and d.rng.random() < 0.08: d.set(x, 8, z, b(d.rng.choice(['red_mushroom', 'brown_mushroom'])))
    d.entry = (7, 8, 30)
    # Giant Sporecap (focal): a huge mushroom with a spotted cap
    cx, cz = 28, 28
    d.fill(cx - 1, 8, cz - 1, cx + 1, 16, cz + 1, STEM)
    d.ellipsoid(cx, 19, cz, 9, 4, 9, RMB, only_air=True)
    d.ellipsoid(cx, 18, cz, 8, 3, 8, kit.AIR)
    d.fill(cx - 1, 17, cz - 1, cx + 1, 17, cz + 1, STEM)
    for (dx, dz) in ((4, 2), (-3, 4), (-5, -2), (2, -5), (6, -1)): d.set(cx + dx, 22 if abs(dx) < 5 else 21, cz + dz, b('white_wool'))
    d.landmark('giant_sporecap', 'Giant Sporecap', 'focal', (cx - 9, 8, cz - 9), (cx + 9, 23, cz + 9))
    # Hollow Fungus Nest (discovery): a rotted stem on its side that you can walk into
    nx, nz = 12, 14
    d.fill(nx - 4, 8, nz - 2, nx + 4, 11, nz + 2, STEM); d.air(nx - 3, 8, nz - 1, nx + 3, 10, nz + 1); d.air(nx - 4, 8, nz, nx - 4, 9, nz)
    d.fill(nx - 3, 8, nz - 1, nx + 3, 8, nz + 1, b('brown_mushroom_block', up=False, down=False, north=False, south=False, east=False, west=False)) if False else d.fill(nx - 3, 7, nz - 1, nx + 3, 7, nz + 1, MOSS)
    d.add_chest((nx + 2, 8, nz), 'west', 'loot'); d.set(nx - 1, 8, nz, b('red_mushroom')); d.set(nx, 8, nz - 1, b('brown_mushroom'))
    d.landmark('hollow_fungus_nest', 'Hollow Fungus Nest', 'discovery', (nx - 4, 7, nz - 2), (nx + 4, 11, nz + 2))
    # Antidote Station (interactive): a cauldron-and-barrel hut that mends and shields against poison
    ax, az = 38, 44
    S.hut(d, ax - 3, az - 3, ax + 3, az + 3, 8, 4, MS, b('oak_planks'), door='n', floor=MOSS)
    d.set(ax - 2, 8, az + 2, b('brewing_stand', has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)); d.set(ax, 8, az + 2, b('cauldron')); d.set(ax + 2, 8, az + 2, b('barrel', facing='up', open=False))
    d.set(ax, 11, az, b('lantern', hanging=True, waterlogged=False))
    d.landmark('antidote_station', 'Antidote Station', 'interactive', (ax - 4, 7, az - 4), (ax + 4, 12, az + 4))
    C.zone(d, 'antidote_rest', C.bx((ax - 3, 8, az - 3), (ax + 3, 11, az + 3)), 'minecraft:regeneration', 1, 100, 40)
    C.zone(d, 'antidote_ward', C.bx((ax - 3, 8, az - 3), (ax + 3, 11, az + 3)), 'minecraft:resistance', 0, 300, 100)
    d.air(44, 8, 29, 62, 11, 31)
    d.room('R1', 'Fungal Marsh', (2, 8, 10), (50, 28, 52))


# ================================================================ R2 Alchemical Gallery (x62..120, z16..46)
def lab(d):
    d.air(62, 8, 16, 120, 22, 46)
    S.floor_pattern(d, 62, 16, 120, 46, FLOOR, [MS, b('stone_bricks'), GT], 2)
    for x in range(66, 120, 6):
        d.fill(x, 8, 16, x, 21, 16, b('stone_bricks')); d.fill(x, 8, 46, x, 21, 46, b('stone_bricks')); d.set(x, 19, 17, GLOW) if False else None
    for x in range(68, 118, 10): d.set(x, 22, 31, GLOW)
    # Alchemical Retort (focal): a giant glass flask on a stand with a still tube
    rx, rz = 92, 22
    d.fill(rx - 3, 8, rz - 3, rx + 3, 9, rz + 3, b('stone_bricks')); d.fill(rx - 2, 8, rz - 2, rx + 2, 8, rz + 2, b('cauldron')) if False else None
    d.ellipsoid(rx, 14, rz, 4, 4, 4, GLASS); d.ellipsoid(rx, 14, rz, 3, 3, 3, LIME)
    d.ellipsoid(rx, 14, rz, 2, 2, 2, kit.AIR) if False else None
    d.fill(rx, 18, rz, rx, 21, rz, GLASS); d.line((rx, 21, rz), (rx + 8, 20, rz + 6), GLASS); d.fill(rx + 8, 14, rz + 6, rx + 8, 19, rz + 6, GLASS)
    d.fill(rx + 6, 8, rz + 5, rx + 10, 9, rz + 7, b('stone_bricks')); d.fill(rx + 7, 10, rz + 6, rx + 9, 13, rz + 6, GLASS)
    d.fill(rx - 1, 10, rz - 1, rx + 1, 10, rz + 1, b('blast_furnace', facing='south', lit=False)) if False else d.set(rx, 9, rz, b('furnace', facing='south', lit=False))
    d.landmark('alchemical_retort', 'Alchemical Retort', 'focal', (rx - 4, 8, rz - 4), (rx + 10, 21, rz + 7))
    # Abandoned Laboratory (discovery): benches and shelves in a bay on the north wall
    lx = 72
    S.nook(d, lx, 8, 16, 'n', depth=4, w=7, h=4)
    d.fill(lx - 3, 8, 12, lx + 3, 8, 12, b('oak_planks')); d.set(lx - 2, 9, 12, b('brewing_stand', has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)); d.set(lx + 2, 9, 12, b('cauldron'))
    d.fill(lx - 3, 9, 15, lx + 3, 10, 15, b('bookshelf')) if False else d.fill(lx - 3, 9, 13, lx - 3, 10, 14, b('bookshelf'))
    d.add_chest((lx, 8, 13), 'south', 'loot'); d.set(lx + 3, 9, 12, b('lantern', hanging=False, waterlogged=False))
    d.landmark('abandoned_laboratory', 'Abandoned Laboratory', 'discovery', (lx - 3, 8, 12), (lx + 3, 11, 16))
    # Sealed Reagent Cache (discovery): behind a sealed hatch in the south wall, opened by the valves
    cx = 80
    d.air(cx, 8, 47, cx + 6, 11, 52)
    d.fill(cx, 7, 47, cx + 6, 7, 52, GT)
    d.add_chest((cx + 3, 8, 52), 'north', 'loot'); d.set(cx + 1, 8, 50, b('brewing_stand', has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)); d.set(cx + 5, 8, 50, b('cauldron'))
    d.seal('cache_hatch', 'minecraft:green_terracotta', (cx + 2, 8, 46), (cx + 4, 11, 46))
    d.landmark('sealed_reagent_cache', 'Sealed Reagent Cache', 'discovery', (cx, 7, 46), (cx + 6, 11, 52))
    # Purification Valves (interactive): two valve-levers on the east end, in order
    vx = 114
    for k, z in enumerate((24, 38)):
        d.fill(vx, 8, z - 1, vx + 1, 11, z + 1, b('stone_bricks'))
        C.wall_lever(d, vx - 1, 9, z, 'e')
        d.fill(vx - 1, 8, z - 1, vx - 1, 8, z + 1, b('iron_block')) if False else d.set(vx - 1, 10, z, b('iron_block'))
    d.landmark('purification_valve', 'Purification Valve', 'interactive', (vx - 2, 8, 23), (vx + 1, 12, 39))
    d.air(120, 8, 29, 130, 11, 33)
    d.seal('hatch_to_causeway', 'minecraft:green_terracotta', (122, 8, 29), (123, 11, 33))
    C.lever_seq(d, 'purify', [(vx - 1, 9, 24), (vx - 1, 9, 38)], [C.clear(C.bx((122, 8, 29), (123, 11, 33))), C.clear(C.bx((cx + 2, 8, 46), (cx + 4, 11, 46))),
                                                                  C.sound((122, 9, 31), 'minecraft:block.piston.extend', 2, 0.5),
                                                                  C.say('The valves bleed the line. The hatch to the sludge causeway opens, and a cache hatch opens in the south wall.', 'green')],
                on_step=[[C.sound((114, 9, 24), 'minecraft:block.piston.extend', 1.5, 0.7), C.say('A valve screams and gives. The second.', 'green')]],
                jam='The valve is seized. The north valve first.')
    C.encounter(d, 'R2', 'Glass hisses. The lab is still running without anyone.',
                [C.mob('minecraft:zombie', 3, 'Sickened Assistant'), C.mob('minecraft:cave_spider', 3, 'Venom Mite'), C.mob('minecraft:skeleton', 2, 'Gas Gunner')])
    d.room('R2', 'Alchemical Gallery', (62, 8, 16), (121, 22, 52))


# ================================================================ R3 Sludge Causeway (zigzag over a sludge lake, x130..190 z14..54)
def causeway(d):
    d.air(130, 2, 14, 190, 30, 54)
    d.fill(129, 1, 14, 191, 1, 54, GT)
    d.air(124, 8, 29, 132, 11, 35)
    # zigzag deck (3 wide, feet y8): A west rim -> x149 ; B north at x148..150 to z19..21 ; C east to x169 ; D south at x168..170 to z43..45 ; E east to the rim
    def deck(x1, z1, x2, z2): d.fill(x1, 7, z1, x2, 7, z2, GT)
    deck(130, 31, 150, 33); deck(148, 19, 150, 33); deck(148, 19, 170, 21); deck(168, 19, 170, 45); deck(168, 43, 190, 45)
    d.air(191, 8, 40, 196, 11, 48)
    for x in range(131, 150, 4): d.set(x, 8, 31, b('mossy_stone_brick_wall')); d.set(x, 8, 33, b('mossy_stone_brick_wall'))
    for x in range(152, 170, 4): d.set(x, 8, 19, b('mossy_stone_brick_wall')); d.set(x, 8, 21, b('mossy_stone_brick_wall'))
    for x in range(172, 190, 4): d.set(x, 8, 43, b('mossy_stone_brick_wall')); d.set(x, 8, 45, b('mossy_stone_brick_wall'))
    # sludge: the pit floor is poison; support columns carry ladders back to the deck; bars split the floor
    for y in range(2, 8): d.set(130, y, 29, kit.ladder('east'))
    d.fill(155, 1, 22, 155, 7, 22, MS); d.fill(171, 1, 30, 171, 7, 30, MS)
    for y in range(2, 8): d.set(155, y, 23, kit.ladder('south')); d.set(172, y, 30, kit.ladder('east'))
    d.fill(190, 1, 41, 190, 7, 41, kit.AIR) if False else None
    for y in range(2, 8): d.set(190, y, 41, kit.ladder('west'))
    for x in (140, 160, 180): d.fill(x, 2, 14, x, 6, 54, b('iron_bars'))
    d.fill(145, 2, 22, 165, 6, 22, kit.AIR) if False else None
    # Corroded Reservoir (focal): a rusted tank on stilts at the north-west corner
    rx, rz = 135, 20
    d.ellipsoid(rx, 14, rz, 5, 4, 5, OXC); d.ellipsoid(rx, 14, rz, 4, 3, 4, kit.AIR); d.ellipsoid(rx, 13, rz, 3, 2, 3, LIME)
    for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)): d.fill(rx + dx, 1, rz + dz, rx + dx, 11, rz + dz, b('iron_bars'))
    d.fill(rx, 18, rz, rx, 20, rz, b('iron_bars')); d.set(rx, 21, rz, GLOW)
    d.landmark('corroded_reservoir', 'Corroded Reservoir', 'focal', (rx - 5, 2, rz - 5), (rx + 5, 21, rz + 5))
    # Ventilation Switch (interactive): on the last leg; clears the gas and opens the Hidden Drain Passage
    vx, vz = 182, 42
    d.fill(vx, 8, vz - 1, vx + 2, 11, vz - 1, b('stone_bricks'))
    C.wall_lever(d, vx + 1, 9, vz, 'n') if False else None
    d.set(vx + 1, 8, vz - 1, b('stone_bricks'))
    C.wall_lever(d, vx + 1, 9, vz, 'n')
    d.set(vx, 9, vz, b('iron_bars')) if False else None
    d.fill(vx, 8, vz, vx, 10, vz, b('iron_bars')); d.fill(vx + 2, 8, vz, vx + 2, 10, vz, b('iron_bars'))
    d.landmark('ventilation_switch', 'Ventilation Switch', 'interactive', (vx, 8, vz - 1), (vx + 2, 11, vz))
    # Corroded Locker (discovery): a rusted locker on a stilted shelf near the start
    lx, lz = 140, 40
    d.fill(lx - 2, 7, lz - 2, lx + 2, 7, lz + 2, OXC); d.fill(lx - 2, 1, lz - 2, lx + 2, 6, lz - 2, b('iron_bars')) if False else None
    d.fill(lx - 2, 1, lz - 2, lx + 2, 6, lz + 2, OXC) if False else None
    d.air(130, 8, 36, 142, 11, 44)
    d.fill(130, 7, 36, 142, 7, 44, GT)
    d.add_chest((140, 8, 43), 'north', 'loot'); d.set(137, 8, 43, b('barrel', facing='north', open=False)); d.set(139, 8, 43, b('iron_block')); d.set(141, 8, 43, b('lantern', hanging=False, waterlogged=False))
    d.landmark('corroded_locker', 'Corroded Locker', 'discovery', (136, 7, 40), (142, 11, 44))
    d.air(130, 8, 34, 131, 11, 36)
    for z in range(16, 54, 6): d.set(129, 16, z, GLOW); d.set(191, 16, z, GLOW)
    C.lever_seq(d, 'ventilation', [(vx + 1, 9, vz)], [C.sound((vx + 1, 9, vz), 'minecraft:block.piston.contract', 2, 0.6),
                                                       C.say('Fans spin up. The gas thins to nothing, and a drain hatch opens somewhere in the Spore Court.', 'green'),
                                                       C.clear(C.bx((213, 8, 5), (215, 11, 6)))])
    C.zone(d, 'sludge_poison', C.bx((130, 2, 14), (190, 5, 54)), 'minecraft:poison', 1, 80, 20)
    C.crumble(d, 'causeway_tiles', C.bx((130, 7, 14), (190, 7, 54)), only='green_terracotta', delay=24, restore=170, sound='minecraft:block.slime_block.break')
    C.pulse(d, 'gas_bursts', box=C.bx((130, 2, 14), (190, 30, 54)), period=74, warn=34, count=1, radius=2.0, damage=2.0, kind='magic',
            warnParticle='minecraft:sneeze', particle='minecraft:sneeze', effect='minecraft:poison', duration=100, amp=0, count_=0,
            sound='minecraft:entity.slime.squish', vol=1.2, pitch=0.7, until='ventilation', scatter=False, **{'target': 'players'})
    d.room('R3', 'Sludge Causeway', (129, 2, 14), (196, 30, 54))


# ================================================================ R4 Spore Court (centre 214,32 r22)
def court(d):
    cx, cz = COURT
    d.air(188, 8, 40, 194, 11, 48)
    d.air(190, 8, 29, 193, 11, 35)
    d.disc(cx, cz, 22, 8, 28, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, MS)
    for x in range(cx - 21, cx + 22):
        for z in range(cz - 21, cz + 22):
            if d.get(x, 8, z) == kit.AIR and d.rng.random() < 0.6: d.set(x, FLOOR, z, d.rng.choice([MYC, MOSS, PODZ]))
    # route from the causeway's last leg into the court
    for x in range(190, 194): d.air(x, 8, 43, x, 11, 45)
    d.air(190, 8, 43, 194, 11, 45)
    for (x, z) in S.pts_ring(cx, cz, 21, 16, 0.2): d.fill(x, 8, z, x, 22, z, STEM); d.set(x, 15, z, GLOW)
    # Venom Orchid (focal): a giant orchid on a dais, five purple petals and a yellow lip
    d.disc(cx, cz, 5, 8, 8, MS); d.fill(cx, 9, cz, cx, 14, cz, b('green_wool'))
    for k in range(5):
        a_ = k * 2 * math.pi / 5
        for r in range(1, 7):
            x, z = int(round(cx + r * math.cos(a_))), int(round(cz + r * math.sin(a_)))
            y = 14 + r // 2
            d.set(x, y, z, b('purple_wool') if r % 2 else b('magenta_wool')); d.set(x, y - 1, z, b('purple_wool')) if r > 2 else None
            if abs(math.cos(a_ + 0.6)) > 0.5: d.set(x, y + 1, z, b('magenta_wool')) if r > 3 else None
    d.set(cx, 15, cz, b('yellow_wool')); d.set(cx, 16, cz, GLOW)
    d.landmark('venom_orchid', 'Venom Orchid', 'focal', (cx - 7, 8, cz - 7), (cx + 7, 19, cz + 7))
    # Quarantine Monument (focal): an obelisk striped with hazard colours, ringed with chains
    mx, mz = cx - 14, cz - 4
    for k in range(14):
        d.fill(mx - 1, 8 + k, mz - 1, mx + 1, 8 + k, mz + 1, YC if (k // 2) % 2 else BC)
    d.set(mx, 22, mz, GLOW)
    for (x, z) in S.pts_ring(mx, mz, 4, 6): d.fill(x, 8, z, x, 11, z, b('chain', axis='y', waterlogged=False)) if False else d.fill(x, 8, z, x, 9, z, b('iron_bars'))
    d.landmark('quarantine_monument', 'Quarantine Monument', 'focal', (mx - 5, 8, mz - 5), (mx + 5, 22, mz + 5))
    # Spore Trial Circle (interactive): a ring of mushrooms round a plate
    px, pz = cx + 13, cz + 10
    d.disc(px, pz, 5, FLOOR, FLOOR, MYC); d.disc(px, pz, 1, FLOOR, FLOOR, MS)
    d.set(px, 8, pz, b('stone_pressure_plate', powered=False))
    for k, (x, z) in enumerate(S.pts_ring(px, pz, 4, 8)): d.set(x, 8, z, b('red_mushroom' if k % 2 else 'brown_mushroom'))
    for (x, z) in S.pts_ring(px, pz, 6, 4, 0.8): d.fill(x, 8, z, x, 10, z, STEM); d.set(x, 11, z, GLOW)
    d.landmark('spore_trial_circle', 'Spore Trial Circle', 'interactive', (px - 7, 7, pz - 7), (px + 7, 11, pz + 7))
    C.trial(d, 'spore_trial', (px, 8, pz), 'R4',
            [{'say': 'The spores rise. Things shake themselves free of the mould.', 'spawns': [C.mob('minecraft:zombie', 3, 'Sickened Assistant'), C.mob('minecraft:cave_spider', 3, 'Venom Mite')]},
             {'say': 'The cloud thickens.', 'spawns': [C.mob('minecraft:zombie', 4, 'Sickened Assistant'), C.mob('minecraft:cave_spider', 3, 'Venom Mite'), C.mob('minecraft:skeleton', 2, 'Gas Gunner')]}],
            [C.say('The spores settle. A vial of Venom Reagent glows on the plate.', 'gold'), C.give_key('reagent', (px, 8, pz))])
    # Hidden Drain Passage (discovery): a storm-drain behind the north wall, opened by the Ventilation Switch
    d.air(cx - 6, 8, 3, cx + 6, 11, 4)
    d.air(cx - 1, 8, 5, cx + 1, 11, 13)
    d.air(cx - 6, 8, 0, cx - 5, 11, 4) if False else None
    d.fill(cx - 6, 7, 3, cx + 6, 7, 4, GT); d.fill(cx - 1, 7, 5, cx + 1, 7, 13, GT)
    d.add_chest((cx + 5, 8, 3), 'west', 'loot'); d.set(cx - 5, 8, 3, b('barrel', facing='east', open=False)); d.set(cx, 8, 3, b('lantern', hanging=False, waterlogged=False))
    d.seal('drain_grate', 'minecraft:iron_bars', (cx - 1, 8, 5), (cx + 1, 11, 5)) if False else None
    d.seal('drain_hatch', 'minecraft:green_terracotta', (206, 8, 3), (208, 11, 4)) if False else None
    d.seal('drain_hatch', 'minecraft:green_terracotta', (cx - 1, 8, 5), (cx + 1, 11, 6))
    d.landmark('hidden_drain_passage', 'Hidden Drain Passage', 'discovery', (cx - 6, 7, 3), (cx + 6, 11, 13))
    # Reagent Offering (interactive): a dish in the south with the Quarantine Vault hatch behind it
    ox, oz = cx, cz + 17
    d.fill(ox, 8, oz, ox, 9, oz, MS); d.set(ox, 10, oz, b('cauldron'))
    for x in (ox - 2, ox + 2): d.fill(x, 8, oz, x, 11, oz, STEM); d.set(x, 12, oz, GLOW)
    d.fill(ox - 3, 7, oz - 1, ox + 3, 7, oz + 1, MYC); d.set(ox - 1, 8, oz - 1, b('red_mushroom')); d.set(ox + 1, 8, oz - 1, b('brown_mushroom'))
    d.landmark('reagent_offering', 'Reagent Offering', 'interactive', (ox - 3, 7, oz - 1), (ox + 3, 12, oz + 1))
    d.air(cx - 1, 8, 54, cx + 1, 11, 66)
    d.seal('quarantine_hatch', 'minecraft:green_terracotta', (cx - 1, 8, 56), (cx + 1, 11, 57))
    C.lock(d, 'offering', C.bx((ox, 10, oz)), 'reagent', [C.clear(C.bx((cx - 1, 8, 56), (cx + 1, 11, 57))), C.sound((cx, 9, 56), 'minecraft:block.slime_block.break', 2, 0.5),
                                                        C.say('The dish froths. A quarantine hatch hisses open in the south wall.', 'green')],
           'A cauldron dish, dry and stained, waiting for a reagent.', 'tips the Venom Reagent into the dish and a hatch hisses open.')
    d.air(cx + 20, 8, 31, cx + 38, 11, 33)
    d.seal('arena_door', 'minecraft:green_terracotta', (cx + 23, 8, 31), (cx + 24, 11, 33))
    C.lock(d, 'arena_door', C.bx((cx + 23, 8, 31), (cx + 24, 11, 33)), 'venom_key', [C.clear(C.bx((cx + 23, 8, 31), (cx + 24, 11, 33))),
                                                                                      C.sound((cx + 23, 9, 32), 'minecraft:block.slime_block.break', 2, 0.4), C.say('The sealed hatch to the Venomheart Chamber opens.', 'gold')],
           'A sealed hatch, hazard-striped, with a keyhole in the wheel.', 'turns the Venom Key and the sealed hatch hisses open.')
    C.encounter(d, 'R4', 'Spores drift in the court. A dry dish waits in the south.',
                [C.mob('minecraft:zombie', 3, 'Sickened Assistant'), C.mob('minecraft:cave_spider', 3, 'Venom Mite')])
    d.room('R4', 'Spore Court', (192, 8, 3), (238, 28, 54))


# ================================================================ R5 Quarantine Vault (x196..234, z68..100)
def vault(d):
    cx = COURT[0]
    d.air(196, 8, 68, 232, 18, 100)
    d.air(cx - 1, 8, 58, cx + 1, 11, 68)
    S.floor_pattern(d, 196, 68, 232, 100, FLOOR, [MS, GT], 2)
    for x in range(200, 230, 6):
        for z in (74, 90):
            d.fill(x, 8, z, x + 1, 11, z + 1, GLASS); d.fill(x, 12, z, x + 1, 12, z + 1, b('iron_block')); d.fill(x, 8, z, x + 1, 8, z + 1, LIME)
            d.set(x + 2, 8, z, b('yellow_concrete')); d.set(x - 1, 8, z, b('black_concrete'))
    d.add_chest((cx, 8, 97), 'north', 'key', key='venom_key')
    d.fill(cx - 2, 8, 98, cx + 2, 10, 99, GT); d.set(cx - 2, 8, 97, b('lantern', hanging=False, waterlogged=False)); d.set(cx + 2, 8, 97, b('lantern', hanging=False, waterlogged=False))
    d.landmark_vault = None
    d.room('R5', 'Quarantine Vault', (196, 8, 58), (232, 18, 100))


# ================================================================ R6 Venomheart Chamber (centre 272,32 r22)
def chamber(d):
    cx, cz = ARENA
    d.air(236, 8, 31, 252, 11, 33)
    d.disc(cx, cz, 22, 8, 30, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, MS); d.disc(cx, cz, 18, FLOOR, FLOOR, GT, rim=14); d.disc(cx, cz, 8, FLOOR, FLOOR, MYC, rim=4)
    d.ellipsoid(cx, 12, cz, 4, 4, 4, SLIME) if False else None
    d.disc(cx, cz, 4, 8, 8, GT); d.fill(cx - 1, 9, cz - 1, cx + 1, 11, cz + 1, LIME); d.set(cx, 12, cz, GLOW)
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 14, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 22, z + 1, STEM)
        d.ellipsoid(x, 24, z, 3, 2, 3, RMB, only_air=True)
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, SLIME); targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): d.fill(x, 8, z, x, 16, z, b('stone_bricks')); d.set(x, 17, z, GLOW)
    C.boss(d, 'venomheart', 'R6', 'minecraft:cave_spider', 'The Venomheart', (cx, 9, cz + 8), targets=targets,
           on_block='minecraft:slime_block', off_block='minecraft:green_terracotta', health=10.0, scale=4.0, color='GREEN',
           intro='The Venomheart uncurls from its sac. Burst the three slime wards on the pillars to open its guard.',
           death='The Venomheart bursts and the chamber exhales.', break_text='The slime wards burst! The Venomheart is exposed!',
           adds=[C.mob('minecraft:cave_spider', 3, 'Venom Mite') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('The sac pulses and the air thickens.', 'red'), C.spawn('minecraft:cave_spider', 3, 'R6', 'Venom Mite')]},
                   {'at': 0.33, 'effects': [C.say('The Venomheart bleeds green.', 'red'), C.spawn('minecraft:zombie', 3, 'R6', 'Sickened Assistant')]}])
    C.pulse(d, 'heart_gas', box=C.bx((254, 8, 10), (292, 30, 54)), period=58, warn=30, count=2, radius=2.2, damage=2.5, kind='magic',
            warnParticle='minecraft:sneeze', particle='minecraft:sneeze', effect='minecraft:poison', duration=100, amp=0, count_=0,
            sound='minecraft:entity.slime.squish', after='boss_awake:venomheart', until='boss:venomheart', scatter=True, **{'target': 'players'})
    C.finish(d, 'venomheart', 'The Venomheart Marsh. Bleed the lab valves, then cross the sludge before the gas finds you.', 'The gas clears. The marsh closes shortly.')
    d.room('R6', 'Venomheart Chamber', (254, 8, 10), (292, 31, 54))
