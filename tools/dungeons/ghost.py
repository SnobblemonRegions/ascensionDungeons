"""The Hollow Bell Crypt: the Ghost dungeon (id `ghost`).

Rooms: Mourning Garden -> Whisper Gallery -> Processional Crypt -> Lantern Court -> Reliquary Vault; arena Hollow Bell Sanctum.
Hazard : the Processional Crypt is dark (a darkness effect) and whispers: wither bolts drop where you stand after a telegraph. The Crypt Seal
         Switch at the far end of the nave lights the braziers and silences it. The Spirit Guide Station in the garden grants night vision.
Puzzle : Memorial Sequence - four gravestones must be touched in the order of the candle colours on the epitaph wall to open the way out of the
         crypt; the Whisper Trial yields a Spirit Lantern, offered at the Lantern Court to open the Reliquary Vault; its Bell Key opens the arena.
Extra  : the Crypt Seal Switch also unseals the Hidden Mourning Niche.
Boss   : the Hollow Bell (vex) behind three froglight wards; wither bolts fall on the sanctum while it is awake.
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dungeonkit'))
import kit, shapes as S, common as C
from kit import b, lever

W, H, L = 330, 46, 140
FLOOR = 7
DBR, DSL, COB, BS = b('deepslate_bricks'), b('deepslate_tiles'), b('cobbled_deepslate'), b('blackstone')
PBS, MC = b('polished_blackstone_bricks'), b('mossy_cobblestone')
SOUL, SLAN = b('soul_lantern', hanging=False, waterlogged=False), b('soul_lantern', hanging=True, waterlogged=False)
FROG, DLOG, CHAIN = b('verdant_froglight'), b('dark_oak_log', axis='y'), b('chain', axis='y', waterlogged=False)
COLS = ['red', 'blue', 'yellow', 'green']
COURT, ARENA = (228, 32), (284, 32)


def build():
    d = kit.Dungeon('ghost', 'The Hollow Bell Crypt', W, H, L, biome='minecraft:dark_forest')
    d.loot_table = 'ascensiondungeons:chests/crimson_cathedral'
    d.shell = ('deepslate_bricks', 'deepslate_tiles', 'cobbled_deepslate', 'blackstone', 'polished_blackstone_bricks', 'mossy_cobblestone')
    d.fill(0, 0, 0, W - 1, H - 1, L - 1, DBR)
    C.key(d, 'spirit_lantern', 'minecraft:soul_lantern', 'Spirit Lantern', 'A cold flame that answers. The Lantern Court offering wants it.')
    C.key(d, 'bell_key', 'minecraft:tripwire_hook', 'Bell Key', 'Opens the tolling door of the Hollow Bell Sanctum.')
    garden(d); gallery(d); crypt(d); court(d); vault(d); sanctum(d)
    d.order_rules = [('start', 'R3', False), ('start', 'R4', False), ('gallery_gate', 'R3', True), ('gallery_gate', 'R4', False), ('memorial', 'R4', True),
                     ('memorial', 'R5', False), ('offering', 'R5', True), ('offering', 'R6', False), ('arena_door', 'R6', True)]
    S.weather_pass(d, DBR, [(DSL, 0.18), (COB, 0.15), (BS, 0.08), (MC, 0.07)])
    return d


def dead_tree(d, x, z, h):
    d.fill(x, 8, z, x, 8 + h, z, DLOG)
    for k, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        d.line((x, 8 + h - 2 - k % 2, z), (x + dx * 3, 8 + h + k % 2, z + dz * 3), DLOG)


def gravestone(d, x, z, y=8):
    d.fill(x, y, z, x, y + 1, z, b('polished_andesite')); d.set(x, y + 2, z, b('stone_slab', type='bottom', waterlogged=False))


# ================================================================ R1 Mourning Garden (cave around 24,30)
def garden(d):
    d.ellipsoid(24, 14, 30, 21, 12, 20, kit.AIR)
    d.fill(2, 0, 8, 46, FLOOR, 52, DBR)
    for x in range(3, 46):
        for z in range(9, 52):
            if d.get(x, 8, z) == kit.AIR:
                fl = d.rng.choice([MC, COB, b('podzol'), BS, b('podzol')])
                d.set(x, FLOOR, z, fl)
                if fl == b('podzol') and d.rng.random() < 0.12: d.set(x, 8, z, b('dead_bush'))
    d.entry = (7, 8, 30)
    # Lantern Tree (focal): a dead oak hung with soul lanterns on chains
    tx, tz = 26, 30
    dead_tree(d, tx, tz, 14)
    for k, (dx, dz, h) in enumerate([(3, 0, 3), (-3, 0, 2), (0, 3, 4), (0, -3, 3), (2, 2, 3), (-2, -2, 2)]):
        x, z = tx + dx, tz + dz
        d.fill(x, 22 - h, z, x, 22, z, CHAIN); d.set(x, 21 - h, z, SLAN) if False else d.set(x, 21 - h, z, b('soul_lantern', hanging=True, waterlogged=False))
    d.fill(tx - 2, 8, tz - 2, tx + 2, 8, tz + 2, b('coarse_dirt')) if False else None
    d.landmark('lantern_tree', 'Lantern Tree', 'focal', (tx - 4, 8, tz - 4), (tx + 4, 23, tz + 4))
    # gravestones (decor)
    for (x, z) in [(14, 20), (16, 22), (12, 40), (18, 42), (38, 20), (36, 44), (34, 22)]: gravestone(d, x, z)
    # Quiet Spirit Habitat (discovery): a mossy crypt niche with candles
    nx, nz = 14, 12
    S.nook(d, nx, 8, nz, 'n', depth=4, w=5, h=4)
    d.fill(nx - 2, 8, nz - 3, nx + 2, 8, nz - 3, b('moss_block')); d.fill(nx - 1, 7, nz - 3, nx + 1, 7, nz - 1, b('moss_block'))
    for (x, z) in ((nx - 1, nz - 2), (nx + 1, nz - 2)): d.set(x, 8, z, b('candle', candles=3, lit=True, waterlogged=False))
    d.add_chest((nx, 8, nz - 3), 'south', 'loot')
    d.landmark('quiet_spirit_habitat', 'Quiet Spirit Habitat', 'discovery', (nx - 2, 7, nz - 4), (nx + 2, 11, nz))
    # Spirit Guide Station (interactive): a small shrine that grants sight in the dark
    sx, sz = 38, 40
    S.hut(d, sx - 3, sz - 3, sx + 3, sz + 3, 8, 4, PBS, DSL, door='w', floor=DSL)
    d.set(sx, 8, sz, b('soul_campfire', facing='north', lit=True, signal_fire=False, waterlogged=False)) if False else d.set(sx, 8, sz, SOUL)
    d.set(sx + 2, 8, sz - 2, b('candle', candles=4, lit=True, waterlogged=False)); d.set(sx - 2, 8, sz + 2, b('candle', candles=2, lit=True, waterlogged=False))
    d.set(sx, 11, sz, b('soul_lantern', hanging=True, waterlogged=False))
    d.landmark('spirit_guide_station', 'Spirit Guide Station', 'interactive', (sx - 4, 7, sz - 4), (sx + 4, 12, sz + 4))
    C.zone(d, 'guide_sight', C.bx((sx - 3, 8, sz - 3), (sx + 3, 11, sz + 3)), 'minecraft:night_vision', 0, 600, 100)
    C.zone(d, 'guide_rest', C.bx((sx - 3, 8, sz - 3), (sx + 3, 11, sz + 3)), 'minecraft:regeneration', 0, 80, 40)
    d.air(44, 8, 29, 62, 11, 31)
    d.room('R1', 'Mourning Garden', (2, 8, 8), (46, 26, 52))


# ================================================================ R2 Whisper Gallery (x60..120, z18..44)
def gallery(d):
    d.air(60, 8, 18, 120, 18, 44)
    S.floor_pattern(d, 60, 18, 120, 44, FLOOR, [DSL, PBS], 2)
    for x in range(64, 120, 6):
        d.fill(x, 8, 18, x, 17, 18, PBS); d.fill(x, 8, 44, x, 17, 44, PBS)
        d.set(x, 15, 19, SLAN) if False else None
    for x in range(66, 118, 12): d.set(x, 17, 31, b('soul_lantern', hanging=True, waterlogged=False)) if d.get(x, 18, 31) != kit.AIR else d.set(x, 17, 31, DSL)
    # coffins along the walls
    for x in range(66, 116, 8):
        d.fill(x, 8, 20, x + 2, 8, 21, b('polished_blackstone_slab', type='bottom', waterlogged=False)) if False else d.fill(x, 8, 20, x + 2, 8, 21, PBS)
        d.fill(x, 8, 41, x + 2, 8, 42, PBS)
    # Broken Mausoleum (focal): a collapsed stone hall in the middle
    mx, mz = 86, 31
    d.fill(mx - 6, 8, mz - 5, mx + 6, 13, mz + 5, PBS)
    d.air(mx - 5, 8, mz - 4, mx + 5, 13, mz + 4)
    d.air(mx - 1, 8, mz - 5, mx + 1, 11, mz - 5); d.air(mx - 1, 8, mz + 5, mx + 1, 11, mz + 5)
    d.air(mx + 2, 12, mz - 5, mx + 6, 13, mz - 2); d.air(mx - 6, 11, mz + 1, mx - 3, 13, mz + 5)
    for (x, z) in ((mx - 3, mz - 2), (mx + 3, mz - 2), (mx - 3, mz + 2), (mx + 3, mz + 2)): d.fill(x, 8, z, x, 11, z, DSL)
    d.fill(mx - 1, 8, mz, mx + 1, 8, mz, b('polished_blackstone_slab', type='bottom', waterlogged=False)) if False else d.fill(mx - 1, 8, mz, mx + 1, 8, mz, BS)
    d.set(mx, 9, mz, b('soul_lantern', hanging=False, waterlogged=False))
    S.rubble(d, mx - 9, mz - 8, mx + 9, mz + 8, 8, [PBS, COB, DSL, MC], 28)
    d.landmark('broken_mausoleum', 'Broken Mausoleum', 'focal', (mx - 7, 8, mz - 6), (mx + 7, 14, mz + 6))
    # Forgotten Epitaph (discovery): an inscribed wall panel and a chest behind the slab
    ex = 104
    S.nook(d, ex, 8, 18, 'n', depth=4, w=5, h=4)
    d.fill(ex - 2, 8, 15, ex + 2, 10, 15, b('chiseled_deepslate'))
    d.add_chest((ex, 8, 16), 'south', 'loot'); d.set(ex - 2, 8, 17, b('candle', candles=3, lit=True, waterlogged=False)); d.set(ex + 2, 8, 17, b('candle', candles=2, lit=True, waterlogged=False))
    d.landmark('forgotten_epitaph', 'Forgotten Epitaph', 'discovery', (ex - 2, 8, 14), (ex + 2, 11, 18))
    # Whisper Trial (interactive): a plate on a bier at the west end
    px, pz = 70, 31
    d.disc(px, pz, 4, FLOOR, FLOOR, PBS); d.disc(px, pz, 1, FLOOR, FLOOR, BS)
    d.set(px, 8, pz, b('polished_blackstone_pressure_plate', powered=False))
    for (x, z) in S.pts_ring(px, pz, 4, 6): d.fill(x, 8, z, x, 10, z, DSL); d.set(x, 11, z, b('soul_lantern', hanging=False, waterlogged=False))
    d.landmark('whisper_trial', 'Whisper Trial', 'interactive', (px - 5, 7, pz - 5), (px + 5, 11, pz + 5))
    C.trial(d, 'whisper_trial', (px, 8, pz), 'R2',
            [{'say': 'The whispers gather. Something answers the plate.', 'spawns': [C.mob('minecraft:zombie', 3, 'Mourner'), C.mob('minecraft:skeleton', 2, 'Grave Warden')]},
             {'say': 'The whispers grow loud.', 'spawns': [C.mob('minecraft:zombie', 4, 'Mourner'), C.mob('minecraft:vex', 2, 'Whisper')]}],
            [C.say('The whispers fall silent. A Spirit Lantern burns on the plate.', 'gold'), C.give_key('spirit_lantern', (px, 8, pz))])
    # gate to the crypt: a coffin-lid door opened by a lever at the east end
    d.fill(114, 8, 28, 114, 10, 28, DSL) if False else None
    d.set(113, 8, 26, lever('wall', 'east')) if False else None
    d.fill(116, 8, 24, 117, 10, 24, PBS); d.set(115, 9, 24, lever('wall', 'west')) if False else d.set(116, 9, 25, lever('wall', 'west')) if False else None
    d.fill(119, 8, 24, 119, 11, 24, PBS) if False else None
    gx = 118
    d.fill(gx, 8, 23, gx + 1, 9, 23, DSL) if False else None
    d.set(117, 8, 27, lever('wall', 'east')) if False else None
    d.fill(116, 8, 36, 116, 11, 36, PBS) if False else None
    d.fill(117, 8, 39, 117, 11, 39, PBS)
    d.set(117, 9, 38, lever('wall', 'north'))
    d.set(117, 8, 38, PBS) if False else None
    d.landmark_unused = None
    d.air(120, 8, 29, 130, 11, 33)
    d.seal('gallery_lid', 'minecraft:polished_blackstone_bricks', (122, 8, 29), (123, 11, 33))
    C.lever_seq(d, 'gallery_gate', [(117, 9, 38)], [C.clear(C.bx((122, 8, 29), (123, 11, 33))), C.sound((122, 9, 31), 'minecraft:block.stone.break', 2, 0.5),
                                                    C.say('A stone lid grinds aside. A long nave of statues lies beyond.', 'gray')])
    C.encounter(d, 'R2', 'The coffins hum. The whispers know your name.',
                [C.mob('minecraft:zombie', 3, 'Mourner'), C.mob('minecraft:skeleton', 3, 'Grave Warden')])
    d.room('R2', 'Whisper Gallery', (60, 8, 18), (120, 18, 44))


# ================================================================ R3 Processional Crypt (x124..202, z24..40)
def crypt(d):
    d.air(124, 8, 24, 202, 16, 40)
    d.air(120, 8, 29, 130, 11, 33)
    d.fill(124, 7, 24, 202, 7, 40, DSL)
    for x in range(124, 203, 2): d.set(x, 7, 32, PBS if x % 4 else DBR)
    for x in range(130, 200, 8):
        d.fill(x, 8, 24, x + 1, 15, 25, PBS); d.fill(x, 8, 39, x + 1, 15, 40, PBS)       # pilasters
    # Processional Statues (focal): robed mourners in pairs, ending at a great hooded figure
    for x in range(134, 196, 12):
        S.statue(d, x, 28, 8, DSL, PBS, eye=None, face='s') if False else None
        S.statue(d, x, 27, 8, PBS, DSL, eye=SOUL if False else None, face='s'); S.statue(d, x, 37, 8, PBS, DSL, eye=None, face='n')
    S.statue(d, 198, 32, 8, BS, PBS, eye=FROG, face='w')
    d.fill(197, 12, 31, 199, 15, 33, BS) if False else None
    d.landmark('processional_statue', 'Processional Statue', 'focal', (132, 8, 25), (200, 20, 39))
    # the epitaph wall at the west end: candle-colour strip (red, blue, yellow, green is shown as ids 0..3 in this order)
    order = [2, 0, 3, 1]                                              # yellow, red, green, blue
    d.fill(125, 9, 39, 138, 12, 39, b('chiseled_deepslate')) if False else None
    for k, c in enumerate(order):
        x = 150 + k * 2
        d.fill(x, 11, 40, x, 11, 40, b(COLS[c] + '_concrete')); d.set(x, 12, 40, b('candle', candles=1, lit=True, waterlogged=False)) if False else None
    # Memorial Sequence (interactive): four gravestones near the east end; touch them in the order of the colour strip
    btn = []
    for k, c in enumerate([0, 1, 2, 3]):
        x = 176 + k * 3
        d.fill(x, 8, 33, x, 9, 33, b('polished_andesite')); d.set(x, 8, 34, b('stone_button', face='floor', facing='north', powered=False))
        d.set(x, 10, 33, b(COLS[c] + '_concrete')); d.set(x - 1, 8, 34, b('candle', candles=1, lit=True, waterlogged=False)) if False else None
        btn.append((x, 8, 34))
    for k, c in enumerate(order): d.set(150 + k * 2, 11, 40, b(COLS[c] + '_concrete'))
    d.landmark('memorial_sequence', 'Memorial Sequence', 'interactive', (172, 8, 32), (186, 11, 36))
    d.air(202, 8, 29, 212, 11, 33)
    d.seal('memorial_door', 'minecraft:polished_blackstone_bricks', (203, 8, 29), (204, 11, 33))
    C.sequence(d, 'memorial', btn, order, [C.clear(C.bx((203, 8, 29), (204, 11, 33))), C.sound((203, 9, 31), 'minecraft:block.stone.break', 2, 0.5),
                                            C.say('The last of the dead are remembered. The door to the Lantern Court opens.', 'gray')],
               'The graves settle.', 'The dead are not remembered. Read the colour strip on the north wall again.')
    # Crypt Seal Switch (interactive): at the far north-east alcove; lights the braziers and unseals the Hidden Mourning Niche
    kx, kz = 190, 24
    S.nook(d, kx, 8, kz, 'n', depth=3, w=3, h=4)
    d.set(kx, 8, kz - 2, lever('floor', 'north')) if False else None
    d.fill(kx - 1, 8, kz - 3, kx + 1, 10, kz - 3, PBS)
    d.set(kx, 9, kz - 2, lever('wall', 'south'))
    d.set(kx - 1, 8, kz - 2, SOUL); d.set(kx + 1, 8, kz - 2, SOUL)
    d.landmark('crypt_seal_switch', 'Crypt Seal Switch', 'interactive', (kx - 1, 8, kz - 3), (kx + 1, 11, kz))
    # Hidden Mourning Niche (discovery): a small chapel behind the south wall
    d.air(150, 8, 41, 156, 11, 46)
    d.add_chest((153, 8, 46), 'north', 'loot'); d.set(151, 8, 44, b('candle', candles=3, lit=True, waterlogged=False)); d.set(155, 8, 44, b('candle', candles=3, lit=True, waterlogged=False))
    d.fill(150, 7, 41, 156, 7, 46, PBS); d.fill(152, 8, 45, 154, 8, 45, BS)
    d.seal('niche_wall', 'minecraft:polished_blackstone_bricks', (152, 8, 41), (154, 11, 41))
    d.landmark('hidden_mourning_niche', 'Hidden Mourning Niche', 'discovery', (150, 7, 41), (156, 11, 46))
    C.lever_seq(d, 'crypt_seal', [(kx, 9, kz - 2)], [C.clear(C.bx((152, 8, 41), (154, 11, 41))), C.sound((153, 9, 41), 'minecraft:block.stone.break', 2, 0.6),
                                                     C.say('The braziers catch. The whispers die and a wall opens in the south side of the nave.', 'gray')])
    C.zone(d, 'crypt_dark', C.bx((124, 8, 24), (202, 16, 40)), 'minecraft:darkness', 0, 120, 60, until='crypt_seal')
    C.pulse(d, 'wither_whispers', box=C.bx((124, 8, 24), (202, 16, 40)), period=80, warn=34, count=1, radius=1.7, damage=3.0, kind='wither',
            warnParticle='minecraft:soul', particle='minecraft:soul_fire_flame', effect='minecraft:wither', duration=40, amp=0,
            sound='minecraft:entity.wither.ambient', vol=0.6, pitch=1.6, until='crypt_seal', scatter=True, **{'target': 'players'})
    d.room('R3', 'Processional Crypt', (124, 8, 24), (203, 16, 40))


# ================================================================ R4 Lantern Court (centre 228,32 r22)
def court(d):
    cx, cz = COURT
    d.air(202, 8, 29, 212, 11, 33)
    d.disc(cx, cz, 22, 8, 26, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, DSL); d.disc(cx, cz, 16, FLOOR, FLOOR, PBS, rim=12)
    for (x, z) in S.pts_ring(cx, cz, 21, 16, 0.2):
        d.fill(x, 8, z, x, 20, z, PBS); d.set(x, 19, z, b('soul_lantern', hanging=False, waterlogged=False)) if False else d.set(x, 21, z, SOUL)
    for (x, z) in S.pts_ring(cx, cz, 18, 10, 0.1): d.set(x, 8, z, SOUL)
    # Mourning Bell (focal): a great bell hung in a stone frame in the middle of the court
    d.fill(cx - 5, 8, cz - 1, cx - 5, 18, cz + 1, PBS); d.fill(cx + 5, 8, cz - 1, cx + 5, 18, cz + 1, PBS)
    d.fill(cx - 5, 19, cz - 1, cx + 5, 19, cz + 1, PBS)
    d.fill(cx, 15, cz, cx, 18, cz, CHAIN)
    d.disc(cx, cz, 2, 12, 14, b('waxed_copper_block') if False else b('gold_block')); d.air(cx, 12, cz, cx, 12, cz); d.disc(cx, cz, 1, 15, 15, b('gold_block'))
    d.set(cx, 11, cz, b('iron_block')); d.set(cx, 12, cz, b('iron_block'))
    d.landmark('mourning_bell', 'Mourning Bell', 'focal', (cx - 6, 8, cz - 2), (cx + 6, 19, cz + 2))
    # Empty Throne Monument (focal): a tall black throne on a dais at the north wall
    tx, tz = cx, cz - 17
    d.fill(tx - 4, 8, tz - 2, tx + 4, 8, tz + 2, BS); d.fill(tx - 3, 9, tz - 1, tx + 3, 9, tz + 2, PBS)
    d.fill(tx - 1, 10, tz, tx + 1, 10, tz + 1, DSL); d.fill(tx - 1, 11, tz - 1, tx + 1, 17, tz - 1, BS)
    d.fill(tx - 2, 10, tz - 1, tx - 2, 13, tz, BS); d.fill(tx + 2, 10, tz - 1, tx + 2, 13, tz, BS)
    d.set(tx, 18, tz - 1, SOUL) if False else d.set(tx, 18, tz - 1, FROG)
    d.landmark('empty_throne_monument', 'Empty Throne Monument', 'focal', (tx - 4, 8, tz - 2), (tx + 4, 18, tz + 2))
    # Sealed Funeral Chest (discovery): a coffin in an alcove, sealed by lead-dark stone until the sequence is solved (here: open)
    fx, fz = cx - 17, cz + 8
    d.fill(fx - 2, 8, fz - 1, fx + 2, 8, fz + 1, BS)
    d.fill(fx - 2, 9, fz - 1, fx - 2, 11, fz + 1, PBS); d.fill(fx + 2, 9, fz - 1, fx + 2, 11, fz + 1, PBS)
    d.add_chest((fx, 9, fz), 'east', 'loot'); d.set(fx - 1, 9, fz, b('candle', candles=3, lit=True, waterlogged=False)); d.set(fx + 1, 9, fz, b('candle', candles=2, lit=True, waterlogged=False))
    d.landmark('sealed_funeral_chest', 'Sealed Funeral Chest', 'discovery', (fx - 2, 8, fz - 1), (fx + 2, 11, fz + 1))
    # Lantern Offering (interactive): a bowl in the south and the Reliquary Vault gate behind it
    ox, oz = cx, cz + 17
    d.fill(ox, 8, oz, ox, 9, oz, PBS); d.set(ox, 10, oz, b('soul_campfire', facing='north', lit=False, signal_fire=False, waterlogged=False)) if False else d.set(ox, 10, oz, b('blackstone'))
    for x in (ox - 2, ox + 2): d.fill(x, 8, oz, x, 11, oz, PBS); d.set(x, 12, oz, SOUL)
    d.landmark('lantern_offering', 'Lantern Offering', 'interactive', (ox - 2, 8, oz - 1), (ox + 2, 12, oz + 1))
    d.air(cx - 1, 8, 54, cx + 1, 11, 66)
    d.seal('vault_door', 'minecraft:polished_blackstone_bricks', (cx - 1, 8, 56), (cx + 1, 11, 57))
    C.lock(d, 'offering', C.bx((ox, 10, oz)), 'spirit_lantern', [C.clear(C.bx((cx - 1, 8, 56), (cx + 1, 11, 57))), C.sound((cx, 9, 56), 'minecraft:block.stone.break', 2, 0.5),
                                                              C.say('The lantern burns on the altar and a tomb door grinds open in the south.', 'gray')],
           'An altar bowl, empty. It wants a light of the dead.', 'sets the Spirit Lantern on the altar and a tomb door grinds open.')
    d.air(cx + 20, 8, 31, cx + 38, 11, 33)
    d.seal('arena_door', 'minecraft:polished_blackstone_bricks', (cx + 23, 8, 31), (cx + 24, 11, 33))
    C.lock(d, 'arena_door', C.bx((cx + 23, 8, 31), (cx + 24, 11, 33)), 'bell_key', [C.clear(C.bx((cx + 23, 8, 31), (cx + 24, 11, 33))),
                                                                                    C.sound((cx + 23, 9, 32), 'minecraft:block.bell.use', 2, 0.5), C.say('A bell tolls. The Hollow Bell Sanctum opens.', 'gold')],
           'A tolling door with a keyhole shaped like a clapper.', 'turns the Bell Key and the tolling door swings wide.')
    C.encounter(d, 'R4', 'Lanterns burn without flame. A great bell hangs unrung.',
                [C.mob('minecraft:zombie', 3, 'Mourner'), C.mob('minecraft:skeleton', 3, 'Grave Warden'), C.mob('minecraft:vex', 1, 'Whisper')])
    d.room('R4', 'Lantern Court', (206, 8, 8), (250, 26, 54))


# ================================================================ R5 Reliquary Vault (x210..248, z67..100)
def vault(d):
    cx = COURT[0]
    d.air(210, 8, 67, 246, 18, 100)
    d.air(cx - 1, 8, 58, cx + 1, 11, 66)
    S.floor_pattern(d, 210, 67, 246, 100, FLOOR, [DSL, PBS], 2)
    for x in range(214, 244, 6):
        for z in (72, 92):
            d.fill(x, 8, z, x + 1, 12, z + 1, PBS); d.set(x, 13, z, SOUL)
    for x in range(216, 242, 8): d.fill(x, 8, 82, x + 2, 8, 83, BS); d.set(x + 1, 9, 82, b('candle', candles=3, lit=True, waterlogged=False))
    # Reliquary Cache (discovery) is the key chest's plinth
    d.add_chest((cx, 8, 97), 'north', 'key', key='bell_key')
    d.fill(cx - 2, 8, 98, cx + 2, 10, 99, BS); d.set(cx - 2, 8, 97, b('candle', candles=3, lit=True, waterlogged=False)); d.set(cx + 2, 8, 97, b('candle', candles=3, lit=True, waterlogged=False))
    d.landmark('reliquary_cache', 'Reliquary Cache', 'discovery', (cx - 2, 8, 96), (cx + 2, 10, 99))
    d.room('R5', 'Reliquary Vault', (210, 8, 58), (246, 18, 100))


# ================================================================ R6 Hollow Bell Sanctum (centre 284,32 r22)
def sanctum(d):
    cx, cz = ARENA
    d.air(248, 8, 31, 264, 11, 33)
    d.disc(cx, cz, 22, 8, 30, kit.AIR)
    d.disc(cx, cz, 22, FLOOR, FLOOR, DSL); d.disc(cx, cz, 18, FLOOR, FLOOR, PBS, rim=15); d.disc(cx, cz, 9, FLOOR, FLOOR, BS, rim=6)
    d.fill(cx - 1, 8, cz - 1, cx + 1, 8, cz + 1, BS)
    d.fill(cx, 9, cz, cx, 20, cz, CHAIN) if False else None
    targets = []
    for k, (x, z) in enumerate(S.pts_ring(cx, cz, 14, 8, math.pi / 8)):
        d.fill(x, 8, z, x + 1, 22, z + 1, PBS)
        d.set(x, 23, z, SOUL) if False else d.set(x, 23, z, b('soul_lantern', hanging=False, waterlogged=False))
        if k % 3 == 0:
            tx = x - 1 if x > cx else x + 2
            d.set(tx, 8, z, BS); d.set(tx, 9, z, FROG); targets.append(C.bx((tx, 9, z)))
    for (x, z) in S.pts_ring(cx, cz, 21, 12, 0.26): d.fill(x, 8, z, x, 17, z, DSL); d.set(x, 18, z, SOUL)
    d.disc(cx, cz, 3, 8, 8, BS)
    C.boss(d, 'hollow_bell', 'R6', 'minecraft:vex', 'The Hollow Bell', (cx, 12, cz), targets=targets,
           on_block='minecraft:verdant_froglight', off_block='minecraft:blackstone', health=5.0, scale=3.0, color='PURPLE',
           equipment={'mainhand': 'minecraft:iron_sword'},
           intro='The Hollow Bell tolls and something answers from inside it. Put out the three froglights on the pillars.',
           death='The Hollow Bell cracks and falls silent.', break_text='The lights gutter out! The Hollow Bell is exposed!',
           adds=[C.mob('minecraft:vex', 2, 'Whisper') | {'room': 'R6'}],
           phases=[{'at': 0.66, 'effects': [C.say('The bell tolls again, louder.', 'red'), C.spawn('minecraft:vex', 2, 'R6', 'Whisper')]},
                   {'at': 0.33, 'effects': [C.say('The dead stand up around the bell.', 'red'), C.spawn('minecraft:zombie', 3, 'R6', 'Mourner')]}])
    C.pulse(d, 'bell_whispers', box=C.bx((266, 8, 10), (306, 30, 54)), period=60, warn=28, count=2, radius=1.9, damage=3.5, kind='wither',
            warnParticle='minecraft:soul', particle='minecraft:soul_fire_flame', effect='minecraft:wither', duration=40, amp=0,
            sound='minecraft:block.bell.use', vol=0.8, pitch=0.6, after='boss_awake:hollow_bell', until='boss:hollow_bell', scatter=True, **{'target': 'players'})
    C.finish(d, 'hollow_bell', 'The Hollow Bell Crypt. Light the garden shrine, pass the nave, and answer the dead.',
             'The bell is quiet. The crypt closes shortly.')
    d.room('R6', 'Hollow Bell Sanctum', (266, 8, 10), (306, 31, 54))
