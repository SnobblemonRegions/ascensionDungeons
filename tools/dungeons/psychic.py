"""The Mindstar Observatory: the Psychic dungeon (id `psychic`).

Rooms: Thought Garden -> Reflection Gallery -> Folded Stair Hall -> Astral Court -> Memory Vault; arena Mindstar Observatory.
Layout : the Folded Stair Hall is three floating platforms at different heights with no walkable link; teleport pads (stand 1.5 s) fold the
         stairs together. Fallers drift down on slow-fall and a floor pad returns them. Psychic bolts land where you stand.
Puzzle : the Memory Sequence Pedestals (press in the colour order shown on the wall strip) unseal the exit stair to the Astral Court;
         the Reflection Trial yields a Focus Crystal, offered at the court to open the Memory Vault; its Mind Key opens the arena.
Extra  : Constellation Alignment (star pads in the order of the ceiling's star colours) opens the Sealed Thought Tablet;
         the two Astral Lens levers open the Forgotten Observatory Desk.
Boss   : the Mindstar (evoker) behind three amethyst wards; psychic bolts rain on the observatory while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever

W, H, L = 300, 50, 140
FLOOR = 7
OBS, PUR, BLK, AMB = b('obsidian'), b('purple_concrete'), b('black_concrete'), b('amethyst_block')
PQ, SQ, END, GLOW = b('purpur_block'), b('smooth_quartz'), b('end_stone_bricks'), b('glowstone')
ROD = b('end_rod', facing='up')
GLASS = b('purple_stained_glass')
CRYS = ['purple', 'magenta', 'pink', 'light_blue', 'white']
COURT, ARENA = (206, 30), (262, 30)


def build():
    d = kit.Dungeon('psychic', 'The Mindstar Observatory', W, H, L, biome='minecraft:cherry_grove')
    d.loot_table = 'ascensiondungeons:chests/lapis_observatory'
    d.shell = ('end_stone_bricks', 'purpur_block', 'obsidian', 'smooth_quartz', 'amethyst_block', 'black_concrete')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, END)
    C.key(d, 'focus_crystal', 'minecraft:amethyst_shard', 'Focus Crystal', 'Cold and clear. The Focus Offering in the Astral Court wants it.')
    C.key(d, 'mind_key', 'minecraft:tripwire_hook', 'Mind Key', 'Opens the shuttered door to the Mindstar Observatory.')
    garden(d); gallery(d); folded(d); court(d); vault(d); observatory(d)
    d.order_rules = [('start', 'R4', False), ('start', 'R5', False), ('memory', 'R4', True), ('memory', 'R5', False), ('offering', 'R5', True),
                     ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, END, [(PQ, 0.15), (b('smooth_quartz'), 0.05), (b('purpur_pillar', axis='y'), 0.05)])
    return d


# ================================================================ R1 Thought Garden (x6..46, z6..46)
def garden(d):
    d.air(6, 8, 6, 46, 24, 46)
    S.floor_pattern(d, 6, 6, 46, 46, FLOOR, [SQ, PQ], 2)
    d.entry = (9, 8, 26)
    d.fill(6, 25, 6, 46, 25, 46, GLASS)
    for (x, z) in S.pts_ring(26, 26, 15, 16, 0.2):                       # hedges of azalea in a spiral-ish ring
        d.fill(x, 8, z, x, 9, z, b('azalea_leaves', distance=7, persistent=True, waterlogged=False))
    # Floating-Rune Sculpture (focal): an obsidian spindle with runes of glowstone and end rod circling it
    sx, sz = 26, 26
    d.disc(sx, sz, 3, 8, 8, OBS); d.fill(sx, 9, sz, sx, 15, sz, OBS)
    for k in range(3):
        yy = 11 + k * 3
        for i, (x, z) in enumerate(S.pts_ring(sx, sz, 4 - k % 2, 8, k * 0.4)):
            d.set(x, yy + (i % 2), z, GLOW if i % 3 == 0 else ROD if i % 3 == 1 else AMB)
    d.set(sx, 17, sz, GLOW)
    d.landmark('floating_rune_sculpture', 'Floating-Rune Sculpture', 'focal', (sx - 5, 8, sz - 5), (sx + 5, 18, sz + 5))
    # Scholar's Cache (discovery): a study nook at the south wall
    cx = 34
    S.nook(d, cx, 8, 46, 's', depth=4, w=5, h=4)
    d.fill(cx - 2, 8, 48, cx + 2, 10, 48, b('bookshelf'))
    d.set(cx, 8, 49, b('lectern', facing='north', powered=False)) if False else d.set(cx + 2, 8, 47, b('lectern', facing='west', powered=False))
    d.add_chest((cx - 2, 8, 47), 'east', 'loot'); d.set(cx, 11, 47, b('lantern', hanging=True, waterlogged=False)) if False else None
    d.set(cx, 8, 47, b('purple_carpet'))
    d.landmark('scholar_s_cache', "Scholar's Cache", 'discovery', (cx - 3, 8, 45), (cx + 3, 11, 49))
    d.air(46, 8, 25, 62, 11, 27)
    d.room('R1', 'Thought Garden', (6, 8, 6), (46, 25, 46))


# ================================================================ R2 Reflection Gallery (x58..118, z14..38)
def gallery(d):
    d.air(58, 8, 14, 118, 22, 38)
    S.floor_pattern(d, 58, 14, 118, 38, FLOOR, [SQ, BLK], 1)
    d.fill(58, 7, 25, 118, 7, 27, PUR)                                   # central aisle
    for x in range(62, 118, 6):
        d.fill(x, 8, 14, x, 22, 14, PUR); d.fill(x, 8, 38, x, 22, 38, PUR)
        d.fill(x + 1, 8, 15, x + 3, 21, 15, b('purple_stained_glass_pane')) if False else d.fill(x + 1, 8, 15, x + 3, 20, 15, GLASS)
        d.fill(x + 1, 8, 37, x + 3, 20, 37, GLASS)
    for x in range(62, 118, 8): d.set(x, 22, 26, GLOW)
    # Reflection Monolith (focal): a black slab with a polished mirror face
    mx, mz = 80, 26
    d.fill(mx - 1, 8, mz - 1, mx + 1, 8, mz + 1, OBS)
    d.fill(mx, 9, mz - 3, mx, 18, mz + 3, BLK)
    d.fill(mx + 1, 10, mz - 2, mx + 1, 17, mz + 2, b('polished_diorite'))
    d.fill(mx - 1, 10, mz - 2, mx - 1, 17, mz + 2, b('polished_diorite'))
    d.set(mx, 19, mz, AMB)
    d.landmark('reflection_monolith', 'Reflection Monolith', 'focal', (mx - 2, 8, mz - 4), (mx + 2, 20, mz + 4))
    # Mindglass Arch (focal): a tall arch of purple glass framed in amethyst
    ax = 98
    S.arch(d, ax, 22, 8, 8, 11, AMB, axis='z', depth=2)
    d.fill(ax, 19, 23, ax + 1, 20, 30, GLASS)
    d.fill(ax, 9, 22, ax, 18, 22, GLASS) if False else None
    d.landmark('mindglass_arch', 'Mindglass Arch', 'focal', (ax - 1, 8, 21), (ax + 2, 21, 31))
    # Reflection Trial (interactive): a plate ringed by mirrors at the east end
    px, pz = 108, 26
    d.disc(px, pz, 4, FLOOR, FLOOR, PUR); d.disc(px, pz, 1, FLOOR, FLOOR, AMB)
    d.set(px, 8, pz, b('light_weighted_pressure_plate', power=0))
    for (x, z) in S.pts_ring(px, pz, 4, 6): d.fill(x, 8, z, x, 11, z, b('polished_diorite')); d.set(x, 12, z, ROD)
    d.landmark('reflection_trial', 'Reflection Trial', 'interactive', (px - 5, 7, pz - 5), (px + 5, 12, pz + 5))
    C.trial(d, 'reflection_trial', (px, 8, pz), 'R2',
            [{'say': 'Your reflections step out of the mirrors.', 'spawns': [C.mob('minecraft:zombie', 3, 'Reflection', equipment={'mainhand': 'minecraft:iron_sword'})]},
             {'say': 'More of them. Fainter, faster.', 'spawns': [C.mob('minecraft:skeleton', 3, 'Reflection'), C.mob('minecraft:zombie', 2, 'Reflection')]}],
            [C.say('The mirrors go dark. A Focus Crystal hovers over the plate.', 'gold'), C.give_key('focus_crystal', (px, 8, pz))])
    # Hidden Mirror Alcove (discovery): behind a glass wall panel on the north side
    hx = 70
    S.nook(d, hx, 8, 14, 'n', depth=4, w=5, h=4)
    d.fill(hx - 2, 8, 11, hx + 2, 8, 11, b('polished_diorite')); d.set(hx, 8, 11, b('polished_diorite'))
    d.add_chest((hx, 8, 10), 'south', 'loot')
    d.fill(hx - 2, 9, 10, hx - 2, 11, 10, GLASS); d.fill(hx + 2, 9, 10, hx + 2, 11, 10, GLASS)
    d.landmark('hidden_mirror_alcove', 'Hidden Mirror Alcove', 'discovery', (hx - 2, 8, 10), (hx + 2, 11, 14))
    d.air(118, 8, 25, 124, 11, 27)
    C.encounter(d, 'R2', 'The gallery is full of you. Some of you move on their own.',
                [C.mob('minecraft:zombie', 3, 'Reflection'), C.mob('minecraft:skeleton', 3, 'Reflection'), C.mob('minecraft:witch', 1, 'Mindweaver')])
    d.room('R2', 'Reflection Gallery', (58, 8, 14), (118, 22, 38))


# ================================================================ R3 Folded Stair Hall (x124..164, z12..48, tall)
def folded(d):
    d.air(124, 2, 12, 164, 40, 48)
    d.fill(123, 1, 12, 165, 1, 48, OBS)
    # three platforms: tops at feet 8, 16, 24
    for (x0, x1, top) in ((124, 132, 8), (140, 148, 16), (156, 164, 24)):
        d.fill(x0, 1, 20, x1, top - 1, 32, PQ)
        d.fill(x0, top - 1, 20, x1, top - 1, 32, b('purple_glazed_terracotta') if False else SQ)
    # folded stairs: each platform has a flight that climbs into the ceiling and ends in thin air
    for (x0, top, direction) in ((126, 8, 'e'), (142, 16, 'e'), (158, 24, 'w')):
        S.steps(d, x0, 33, top, 5, direction, 1, PUR, base=top - 1) if False else None
    for (px, top) in ((125, 8), (141, 16), (157, 24)):
        for k in range(1, 6):
            d.fill(px + k - 1, top, 18, px + k - 1, top + k - 1, 18, PUR)
            d.set(px + k - 1, top + k, 18, b('purple_stained_glass'))
    # pads
    d.fill(130, 8, 25, 132, 8, 27, b('magenta_glazed_terracotta', facing='north')) if False else None
    for (px, top) in ((130, 8), (146, 16)):
        d.fill(px, top - 1, 25, px + 1, top - 1, 27, b('magenta_glazed_terracotta', facing='north'))
        d.set(px, top, 24, ROD); d.set(px, top, 28, ROD); d.set(px + 1, top, 24, ROD); d.set(px + 1, top, 28, ROD)
    C.pad(d, 'fold_one', C.bx((130, 8, 25), (131, 8, 27)), (141, 16, 26), 'The stairs fold. You step onto the second landing.', 30)
    C.pad(d, 'fold_two', C.bx((146, 16, 25), (147, 16, 27)), (157, 24, 26), 'The stairs fold again. The third landing.', 30)
    C.pad(d, 'fold_back', C.bx((124, 2, 12), (164, 3, 48)), (125, 8, 26), 'A pull behind the eyes. You are back at the first landing.', 40)
    C.zone(d, 'fold_fall', C.bx((124, 2, 12), (164, 39, 48)), 'minecraft:slow_falling', 0, 80, 20)
    C.zone(d, 'fold_dizzy', C.bx((124, 8, 12), (164, 39, 48)), 'minecraft:nausea', 0, 120, 100)
    # Twin Thought Statues (focal) flank the first landing
    d.fill(125, 8, 36, 125, 8, 36, SQ) if False else None
    S.statue(d, 128, 36, 8, SQ, PQ, eye=GLOW, face='n') if False else None
    S.statue(d, 128, 17, 8, PQ, SQ, eye=GLOW, face='s'); S.statue(d, 128, 35, 8, PQ, SQ, eye=GLOW, face='n')
    d.landmark('twin_thought_statues', 'Twin Thought Statues', 'focal', (125, 8, 15), (131, 20, 37))
    # Dream Habitat Niche (discovery): a nook in the second platform's back wall
    d.fill(143, 16, 33, 147, 19, 36, PQ); d.air(144, 16, 33, 146, 18, 36)
    d.fill(144, 15, 33, 146, 15, 36, b('purple_wool')); d.fill(144, 16, 35, 146, 16, 36, b('purple_carpet'))
    d.add_chest((145, 16, 36), 'north', 'loot')
    d.set(144, 17, 36, b('end_rod', facing='down')) if False else d.set(145, 18, 35, ROD)
    d.landmark('dream_habitat_niche', 'Dream Habitat Niche', 'discovery', (143, 15, 33), (147, 19, 36))
    # Memory Sequence Pedestals (interactive) on the third landing; the colour order is on the wall strip
    order_colours = [2, 0, 3, 1]                                         # pink, purple, light blue, magenta
    d.fill(162, 24, 18, 164, 28, 34, PQ) if False else None
    btn = []
    for k, c in enumerate([0, 1, 2, 3]):
        z = 21 + k * 3
        d.fill(161, 23, z - 1, 163, 23, z + 1, b(CRYS[c] + '_concrete'))
        d.set(162, 24, z, b('stone_button', face='floor', facing='north', powered=False))
        btn.append((162, 24, z))
    for k, c in enumerate(order_colours):
        d.fill(160 - 0, 26 + 0, 33, 160, 26, 33, SQ) if False else None
        d.set(158 + k * 2, 28, 33, b(CRYS[c] + '_concrete'))
    d.fill(157, 24, 33, 165, 30, 33, SQ) if False else None
    d.fill(157, 27, 33, 164, 29, 33, SQ)
    for k, c in enumerate(order_colours): d.set(158 + k * 2, 28, 33, b(CRYS[c] + '_concrete'))
    d.landmark('memory_sequence_pedestal', 'Memory Sequence Pedestal', 'interactive', (156, 23, 19), (164, 29, 34))
    d.air(164, 24, 25, 165, 27, 27)
    for i in range(20):
        fx = 165 + i
        top = max(8, 24 - i)
        d.air(fx, top, 25, fx, top + 4, 27)
    d.seal('exit_seal', 'minecraft:purpur_block', (166, 23, 25), (167, 27, 27))
    C.sequence(d, 'memory', btn, order_colours, [C.clear(C.bx((166, 23, 25), (167, 27, 27))), C.sound((166, 24, 26), 'minecraft:block.amethyst_block.chime', 2, 0.8),
                                                  C.say('The wall remembers you. The stair beyond unfolds.', 'light_purple')],
               'The colours settle into memory.', 'Forgotten. Read the colours on the wall again.')
    C.pulse(d, 'psychic_bolts', box=C.bx((124, 2, 12), (164, 40, 48)), period=76, warn=30, count=1, radius=1.8, damage=3.0, kind='magic',
            warnParticle='minecraft:enchant', particle='minecraft:witch', effect='minecraft:slowness', duration=60, amp=0,
            sound='minecraft:entity.evoker.cast_spell', vol=1.0, pitch=1.4, scatter=False, **{'target': 'players'})
    d.room('R3', 'Folded Stair Hall', (124, 2, 12), (166, 40, 48))


# ================================================================ R4 Astral Court (centre 206,30 r22)
def court(d):
    cx, cz = COURT
    d.disc(cx, cz, 22, 8, 30, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, SQ)
    d.disc(cx, cz, 14, FLOOR, FLOOR, BLK, rim=11)
    d.disc(cx, cz, 22, 31, 31, GLASS)
    for (x, z) in S.pts_ring(cx, cz, 21, 16, 0.2): d.fill(x, 8, z, x, 30, z, PUR); d.set(x, 16, z, GLOW)
    # stars on the dome: the ceiling's star colours in order (for Constellation Alignment): light blue, white, pink, purple, magenta
    star_order = [3, 4, 2, 0, 1]
    for k, c in enumerate(star_order):
        a_ = -math.pi / 2 + k * 0.5
        x, z = int(round(cx + 12 * math.cos(a_))), int(round(cz + 12 * math.sin(a_)))
        d.fill(x - 1, 30, z - 1, x + 1, 30, z + 1, b(CRYS[c] + '_concrete')); d.set(x, 29, z, ROD)
    # Astral Orrery (focal): nested rings of end rods and amethyst round a glowing core
    d.disc(cx, cz, 5, 8, 8, OBS); d.set(cx, 9, cz, OBS)
    d.fill(cx, 9, cz, cx, 14, cz, OBS); d.ellipsoid(cx, 15, cz, 2, 2, 2, GLOW)
    for r, yy in ((4, 12), (6, 15), (8, 18)):
        for i, (x, z) in enumerate(S.pts_ring(cx, cz, r, 6 * r // 2)):
            d.set(x, yy, z, AMB if i % 2 else ROD)
    d.landmark('astral_orrery', 'Astral Orrery', 'focal', (cx - 9, 8, cz - 9), (cx + 9, 19, cz + 9))
    # Constellation Alignment (interactive): five star pads on the floor of the north half, in the same colours
    btn = []
    for k, c in enumerate([0, 1, 2, 3, 4]):
        x, z = cx - 12 + k * 6, cz - 16 + (k % 2) * 3
        d.fill(x - 1, 7, z - 1, x + 1, 7, z + 1, b(CRYS[c] + '_concrete')); d.set(x, 8, z, b('stone_button', face='floor', facing='north', powered=False))
        btn.append((x, 8, z))
    d.landmark('constellation_alignment', 'Constellation Alignment', 'interactive', (cx - 14, 7, cz - 18), (cx + 14, 9, cz - 12))
    C.sequence(d, 'constellation', btn, star_order, [C.clear(C.bx((cx - 2, 8, 9), (cx + 2, 11, 10))), C.sound((cx, 9, 9), 'minecraft:block.amethyst_block.chime', 2, 1.0),
                                                      C.say('The stars chime in turn. A tablet-niche opens in the north wall.', 'light_purple')],
               'The stars sing in order.', 'The stars go dark. Read them on the dome: light blue, white, pink, purple, magenta.')
    d.air(cx - 2, 8, 4, cx + 2, 11, 8)
    d.add_chest((cx, 8, 5), 'south', 'loot')
    d.fill(cx - 2, 7, 4, cx + 2, 7, 8, PUR); d.set(cx + 2, 8, 5, b('lectern', facing='west', powered=False)); d.set(cx - 2, 8, 5, b('lantern', hanging=False, waterlogged=False))
    d.seal('tablet_wall', 'minecraft:purpur_block', (cx - 2, 8, 9), (cx + 2, 11, 10))
    d.landmark('sealed_thought_tablet', 'Sealed Thought Tablet', 'discovery', (cx - 2, 7, 4), (cx + 2, 11, 10))
    # Astral Lens Control (interactive): a tall lens with two levers; throw them in order
    lx, lz = cx + 15, cz - 14
    d.fill(lx - 1, 8, lz - 1, lx + 1, 8, lz + 1, OBS); d.fill(lx, 9, lz, lx, 13, lz, b('iron_block'))
    d.line((lx, 13, lz), (lx - 3, 17, lz + 3), b('iron_block')); d.ellipsoid(lx - 3, 18, lz + 3, 1, 1, 1, b('light_blue_stained_glass'))
    d.fill(lx - 2, 8, lz - 1, lx - 2, 9, lz - 1, PUR) if False else None
    d.set(lx + 2, 8, lz, lever('floor', 'east')); d.set(lx - 2, 8, lz, lever('floor', 'west'))
    d.landmark('astral_lens_control', 'Astral Lens Control', 'interactive', (lx - 4, 8, lz - 2), (lx + 3, 19, lz + 5))
    dx, dz = cx + 14, cz + 14
    d.air(dx, 8, dz, dx + 6, 11, dz + 6)
    d.fill(dx, 7, dz, dx + 6, 7, dz + 6, PUR)
    d.fill(dx + 2, 8, dz + 5, dx + 4, 8, dz + 5, b('bookshelf')); d.set(dx + 3, 8, dz + 3, b('lectern', facing='north', powered=False)) if False else None
    d.fill(dx + 2, 8, dz + 2, dx + 4, 8, dz + 2, b('smooth_quartz_slab', type='bottom', waterlogged=False)) if False else d.fill(dx + 2, 8, dz + 2, dx + 4, 8, dz + 2, SQ)
    d.add_chest((dx + 3, 8, dz + 4), 'north', 'loot'); d.set(dx + 3, 9, dz + 2, b('lantern', hanging=False, waterlogged=False))
    d.seal('desk_wall', 'minecraft:purpur_block', (dx - 1, 8, dz), (dx - 1, 11, dz + 3)) if False else None
    d.seal('desk_wall', 'minecraft:purpur_block', (dx, 8, dz), (dx + 1, 11, dz))
    d.landmark('forgotten_observatory_desk', 'Forgotten Observatory Desk', 'discovery', (dx, 7, dz), (dx + 6, 11, dz + 6))
    C.lever_seq(d, 'lens', [(lx + 2, 8, lz), (lx - 2, 8, lz)], [C.clear(C.bx((dx, 8, dz), (dx + 1, 11, dz))), C.sound((dx, 9, dz), 'minecraft:block.amethyst_block.chime', 2, 0.6),
                                                               C.say('The lens refocuses. A hidden study opens in the south-east wall.', 'light_purple')],
                on_step=[[C.sound((lx, 10, lz), 'minecraft:block.piston.extend', 1.5, 1.2), C.say('The lens swings. One more lever.', 'light_purple')]],
                jam='The lens is out of focus. Use the near lever first.')
    # Focus Offering (interactive): altar to the south, the Memory Vault gate behind it
    fx, fz = cx, 49
    d.fill(fx, 8, fz, fx, 9, fz, OBS); d.set(fx, 10, fz, AMB)
    for x in (fx - 2, fx + 2): d.fill(x, 8, fz, x, 12, fz, PUR); d.set(x, 13, fz, ROD)
    d.landmark('focus_offering', 'Focus Offering', 'interactive', (fx - 2, 8, fz - 1), (fx + 2, 13, fz + 1))
    d.air(cx - 1, 8, 53, cx + 1, 11, 66)
    d.seal('vault_gate', 'minecraft:purpur_block', (cx - 1, 8, 55), (cx + 1, 11, 56))
    C.lock(d, 'offering', C.bx((fx, 10, fz)), 'focus_crystal', [C.clear(C.bx((cx - 1, 8, 55), (cx + 1, 11, 56))), C.sound((cx, 9, 55), 'minecraft:block.amethyst_block.chime', 2, 0.5),
                                                             C.say('The altar focuses. The gate of the Memory Vault slides aside.', 'light_purple')],
           'A cold altar with a hollow shaped like a crystal.', 'sets the Focus Crystal on the altar and the vault gate slides aside.')
    d.air(cx + 20, 8, 29, cx + 32, 11, 31)
    d.seal('arena_door', 'minecraft:purpur_block', (cx + 23, 8, 29), (cx + 24, 11, 31))
    C.lock(d, 'arena_door', C.bx((cx + 23, 8, 29), (cx + 24, 11, 31)), 'mind_key', [C.clear(C.bx((cx + 23, 8, 29), (cx + 24, 11, 31))),
                                                                                    C.sound((cx + 23, 9, 30), 'minecraft:block.amethyst_block.break', 2, 0.5), C.say('The shutters of the Mindstar Observatory open.', 'gold')],
           'A shuttered door with a keyhole that is not quite where you look.', 'turns the Mind Key and the shutters fold away.')
    C.encounter(d, 'R4', 'The dome shows five stars in a fixed order. A crystal altar waits in the south.',
                [C.mob('minecraft:witch', 1, 'Mindweaver'), C.mob('minecraft:skeleton', 3, 'Reflection'), C.mob('minecraft:zombie', 2, 'Reflection')])
    d.room('R4', 'Astral Court', (184, 8, 3), (228, 31, 54))


# ================================================================ R5 Memory Vault (x188..226, z67..100)
def vault(d):
    cx = COURT[0]
    d.air(188, 8, 67, 224, 20, 100)
    d.air(cx - 1, 8, 58, cx + 1, 11, 66)
    S.floor_pattern(d, 188, 67, 224, 100, FLOOR, [PUR, SQ], 2)
    for x in range(192, 222, 6):
        d.fill(x, 8, 72, x + 1, 13, 73, PQ); S.crystal(d, x, 76, 8, 5, AMB, b('purple_stained_glass'))
        d.fill(x, 8, 92, x + 1, 13, 93, PQ); S.crystal(d, x, 88, 8, 4, AMB, b('purple_stained_glass'))
    d.set(cx, 20, 80, GLOW) if False else None
    d.add_chest((cx, 8, 97), 'north', 'key', key='mind_key')
    d.fill(cx - 2, 8, 98, cx + 2, 10, 99, PUR)
    d.room('R5', 'Memory Vault', (188, 8, 58), (224, 20, 100))


# ================================================================ R6 Mindstar Observatory (centre 262,30 r22)
def observatory(d):
    cx, cz = ARENA
    d.air(228, 8, 29, 240, 11, 31)
    d.disc(cx, cz, 22, 8, 32, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, SQ)
    for r, mat in ((20, PUR), (14, BLK), (8, PQ)): d.disc(cx, cz, r, FLOOR, FLOOR, mat, rim=r - 3)
    d.disc(cx, cz, 22, 33, 33, GLASS)
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 13, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 24, z + 1, PUR)
        d.set(x, 25, z, AMB); d.set(x + 1, 25, z + 1, ROD)
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 9, z, AMB); targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): d.fill(x, 8, z, x, 18, z, b('purpur_pillar', axis='y')); d.set(x, 19, z, GLOW)
    d.disc(cx, cz, 3, 8, 8, OBS); d.ellipsoid(cx, 12, cz, 2, 2, 2, GLOW)
    C.boss(d, 'mindstar', 'R6', 'minecraft:evoker', 'The Mindstar', (cx, 9, cz + 6), targets=targets,
           on_block='minecraft:amethyst_block', off_block='minecraft:obsidian', health=6.0, scale=1.8, color='PURPLE',
           intro='The Mindstar opens its eyes. Shatter the three amethyst wards on the pillars to break its focus.',
           death='The Mindstar goes dark, and every star in the dome with it.', break_text='The wards shatter! The Mindstar is exposed!',
           adds=[C.mob('minecraft:vex', 2, 'Thought Fragment') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('The Mindstar splits its attention.', 'red'), C.spawn('minecraft:vex', 2, 'R6', 'Thought Fragment')]},
                   {'at': 0.33, 'effects': [C.say('The dome shows only the Mindstar.', 'red'), C.spawn('minecraft:skeleton', 3, 'R6', 'Reflection')]}])
    C.pulse(d, 'mind_bolts', box=C.bx((242, 8, 8), (284, 32, 52)), period=60, warn=28, count=2, radius=2.0, damage=3.5, kind='magic',
            warnParticle='minecraft:enchant', particle='minecraft:witch', effect='minecraft:nausea', duration=100, amp=0,
            sound='minecraft:entity.evoker.cast_spell', after='boss_awake:mindstar', until='boss:mindstar', scatter=True, **{'target': 'players'})
    C.finish(d, 'mindstar', 'The Mindstar Observatory. Fold the stairs, remember the colours, and reach the Astral Court.',
             'The dome dims. The observatory closes shortly.')
    d.room('R6', 'Mindstar Observatory', (242, 8, 8), (284, 33, 52))
