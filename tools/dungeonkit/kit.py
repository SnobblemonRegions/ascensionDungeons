"""Dungeon kit: a numpy block grid with building primitives, plus export of the Sponge v3 schematic and layout.json
that the mod's authored-dungeon engine reads (gen/authored + mc/Authored*).

Coordinates are schematic coordinates (x east, y up, z south). Palette entries are full block-state strings. Use `b()`
to build them. Run scripts WITHOUT -I (numpy is needed).
"""
import gzip, io, json, os, random, struct
import numpy as np

AIR = 'minecraft:air'


def b(name, **p):
    """b('oak_stairs', facing='north', half='bottom') -> 'minecraft:oak_stairs[facing=north,half=bottom]'."""
    s = name if ':' in name else 'minecraft:' + name
    if p:
        s += '[' + ','.join('%s=%s' % (k, str(v).lower()) for k, v in sorted(p.items())) + ']'
    return s


def base(s):
    return s.split('[')[0].split(':', 1)[1]


def props(s):
    if '[' not in s: return {}
    return dict(kv.split('=') for kv in s[s.index('[') + 1:-1].split(','))


def stair(kind, facing, top=False, water=False):
    return b(kind + '_stairs', facing=facing, half='top' if top else 'bottom', shape='straight', waterlogged=water)


def slab(kind, top=False, water=False):
    return b(kind + '_slab', type='top' if top else 'bottom', waterlogged=water)


def wall(kind): return b(kind + '_wall')


def ladder(facing, water=False): return b('ladder', facing=facing, waterlogged=water)


def chest(facing, water=False): return b('chest', facing=facing, type='single', waterlogged=water)


def lever(face, facing, powered=False): return b('lever', face=face, facing=facing, powered=powered)


def lantern(hanging=False, soul=False, water=False): return b('soul_lantern' if soul else 'lantern', hanging=hanging, waterlogged=water)


def chain(axis='y', water=False): return b('chain', axis=axis, waterlogged=water)


def trapdoor(kind, facing, half='bottom', open_=False, water=False):
    return b(kind + '_trapdoor', facing=facing, half=half, open=open_, powered=False, waterlogged=water)


def pillar(kind, axis='y'): return b(kind, axis=axis)


def water(): return b('water', level=0)


def button(kind, face, facing): return b(kind + '_button', face=face, facing=facing, powered=False)


def plate(kind): return b(kind + '_pressure_plate', powered=False)


def sign(rot=0, water_=False): return b('oak_sign', rotation=rot, waterlogged=water_)


def candle(n=3, lit=True, water_=False): return b('candle', candles=n, lit=lit, waterlogged=water_)


def sea_pickle(n=3, water_=True): return b('sea_pickle', pickles=n, waterlogged=water_)


def coral_fan(kind, water_=True): return b(kind + '_coral_fan', waterlogged=water_)


FACINGS = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}

# ---- block classification (shared by the checks) ------------------------------------------------------------
NONSOLID = {
    'air', 'cave_air', 'void_air', 'light', 'seagrass', 'tall_seagrass', 'kelp', 'kelp_plant', 'vine', 'glow_lichen', 'lever',
    'torch', 'wall_torch', 'soul_torch', 'soul_wall_torch', 'redstone_torch', 'redstone_wall_torch', 'ladder', 'oak_sign', 'oak_wall_sign',
    'chain', 'lantern', 'soul_lantern', 'sea_pickle', 'short_grass', 'fern', 'tripwire', 'tripwire_hook', 'string', 'cobweb_no',
    'oak_button', 'stone_button', 'spruce_button', 'birch_button', 'jungle_button', 'acacia_button', 'dark_oak_button', 'mangrove_button',
    'crimson_button', 'warped_button', 'bamboo_button', 'cherry_button', 'polished_blackstone_button',
    'oak_pressure_plate', 'stone_pressure_plate', 'spruce_pressure_plate', 'birch_pressure_plate', 'light_weighted_pressure_plate',
    'heavy_weighted_pressure_plate', 'polished_blackstone_pressure_plate', 'dark_oak_pressure_plate', 'jungle_pressure_plate',
    'acacia_pressure_plate', 'mangrove_pressure_plate', 'crimson_pressure_plate', 'warped_pressure_plate', 'bamboo_pressure_plate',
    'cherry_pressure_plate', 'candle', 'flower_pot', 'moss_carpet', 'hanging_roots', 'spore_blossom', 'glow_berries', 'cave_vines',
    'cave_vines_plant', 'weeping_vines', 'twisting_vines', 'small_dripleaf', 'big_dripleaf_stem', 'lily_pad_no', 'rail', 'powered_rail',
    'poppy', 'dandelion', 'azure_bluet', 'cornflower', 'oxeye_daisy', 'allium', 'blue_orchid', 'lily_of_the_valley', 'red_tulip', 'orange_tulip', 'white_tulip', 'pink_tulip', 'dead_bush', 'pink_petals', 'wither_rose', 'torchflower', 'pitcher_plant_no', 'cobweb_no', 'sunflower_no', 'sweet_berry_bush', 'brown_mushroom', 'red_mushroom', 'crimson_roots', 'warped_roots', 'nether_sprouts', 'soul_fire_no', 'snow_no', 'lily_pad',
    'end_rod', 'amethyst_cluster', 'small_amethyst_bud', 'medium_amethyst_bud', 'large_amethyst_bud', 'pointed_dripstone',
}
LIQUID = {'water', 'bubble_column', 'lava'}
WATERLOGGABLE_SUFFIX = ('_stairs', '_slab', '_wall', '_fence', '_trapdoor', '_sign', '_wall_sign', '_coral_fan', '_coral_wall_fan', '_coral')
WATERLOGGABLE = {'chest', 'trapped_chest', 'ender_chest', 'ladder', 'lantern', 'soul_lantern', 'chain', 'iron_bars', 'sea_pickle', 'light',
                 'scaffolding', 'conduit', 'turtle_egg_no', 'candle', 'pointed_dripstone', 'lightning_rod', 'barrel_no'}
UNSAFE_GROUND = {'magma_block', 'campfire', 'soul_campfire', 'fire', 'soul_fire', 'cactus', 'sweet_berry_bush', 'lava', 'cauldron', 'lava_cauldron',
                 'powder_snow', 'wither_rose', 'bubble_column'}


def is_air(s): return base(s) in ('air', 'cave_air', 'void_air')
WATERY = ('water', 'bubble_column', 'seagrass', 'tall_seagrass', 'kelp', 'kelp_plant')       # these always hold water
def is_water(s): return base(s) in WATERY or (props(s).get('waterlogged') == 'true')
def is_pure_water(s): return base(s) in ('water', 'bubble_column')
def is_lava(s): return base(s) == 'lava'
def nonsolid(s):
    bn = base(s)
    return bn in NONSOLID or bn in LIQUID or bn.endswith('_coral_fan') or bn.endswith('_coral_wall_fan') or bn.endswith('_button') or bn.endswith('_pressure_plate') \
        or bn.endswith('_banner') or bn.endswith('_sapling') or bn in ('tall_grass', 'large_fern', 'sugar_cane')
def waterloggable(s):
    bn = base(s)
    return bn in WATERLOGGABLE or bn.endswith(WATERLOGGABLE_SUFFIX) and 'waterlogged' in s
def is_partial_wall(s): return base(s).endswith('_wall') or base(s).endswith('_fence')


class Dungeon:
    def __init__(self, id, name, W, H, L, biome='minecraft:the_void'):
        self.id, self.name, self.W, self.H, self.L, self.biome = id, name, W, H, L, biome
        self.pal = [AIR]
        self.idx = {AIR: 0}
        self.a = np.zeros((H, L, W), dtype=np.int32)       # [y, z, x]
        self.rooms = []        # {'id','name','min','max'}
        self.landmarks = []    # {'id','name','role','min','max'}
        self.chests = []
        self.seals = []        # {'id','block','min','max'}  (placed at generation instead of the schematic's block)
        self.keys = {}
        self.mechanics, self.hazards, self.encounters, self.bosses, self.pads = [], [], [], [], []
        self.completion = {}
        self.pokemon = {}          # optional Cobblemon section: {'tier', 'pools': {name: [{species, weight, min, max}]}, 'scaling'}
        self.planner_edges = []   # [{'from': box, 'to': [x,y,z]}] movement the planner cannot model (levitation, slow fall)
        self.entry = None
        self.markers = {}
        self.loot_table = 'ascensiondungeons:chests/flooded_ruins'
        self.rng = random.Random(20261017)

    # ---- palette / cells --------------------------------------------------------------------------------
    def pid(self, s):
        i = self.idx.get(s)
        if i is None:
            i = self.idx[s] = len(self.pal)
            self.pal.append(s)
        return i

    def get(self, x, y, z):
        if not (0 <= x < self.W and 0 <= y < self.H and 0 <= z < self.L): return None
        return self.pal[self.a[y, z, x]]

    def set(self, x, y, z, s):
        if 0 <= x < self.W and 0 <= y < self.H and 0 <= z < self.L:
            self.a[y, z, x] = self.pid(s if s is not None else AIR)

    def fill(self, x1, y1, z1, x2, y2, z2, s):
        x1, x2 = sorted((x1, x2)); y1, y2 = sorted((y1, y2)); z1, z2 = sorted((z1, z2))
        x1, y1, z1 = max(x1, 0), max(y1, 0), max(z1, 0)
        self.a[y1:min(y2, self.H - 1) + 1, z1:min(z2, self.L - 1) + 1, x1:min(x2, self.W - 1) + 1] = self.pid(s if s is not None else AIR)

    def air(self, x1, y1, z1, x2, y2, z2): self.fill(x1, y1, z1, x2, y2, z2, AIR)

    def replace(self, x1, y1, z1, x2, y2, z2, pred, s):
        """Replace the cells of a box whose block satisfies pred(state_string) by s."""
        x1, x2 = sorted((x1, x2)); y1, y2 = sorted((y1, y2)); z1, z2 = sorted((z1, z2))
        sub = self.a[max(y1, 0):y2 + 1, max(z1, 0):z2 + 1, max(x1, 0):x2 + 1]
        ok = np.array([bool(pred(p)) for p in self.pal])
        sub[ok[sub]] = self.pid(s)

    def hollow(self, x1, y1, z1, x2, y2, z2, shell, inner=AIR, t=1):
        self.fill(x1, y1, z1, x2, y2, z2, shell)
        self.fill(x1 + t, y1 + t, z1 + t, x2 - t, y2 - t, z2 - t, inner)

    def mask_disc(self, cx, cz, r, x1=None, z1=None, x2=None, z2=None):
        xs = np.arange(self.W)[None, :]; zs = np.arange(self.L)[:, None]
        return (xs - cx) ** 2 + (zs - cz) ** 2 <= r * r + 0.5          # [z, x]

    def disc(self, cx, cz, r, y1, y2, s, rim=None):
        """Filled circle (cylinder) of radius r from y1..y2. rim = optional inner radius excluded (ring)."""
        m = self.mask_disc(cx, cz, r)
        if rim is not None: m &= ~self.mask_disc(cx, cz, rim)
        y1, y2 = sorted((y1, y2))
        pid = self.pid(s)
        for y in range(max(y1, 0), min(y2, self.H - 1) + 1):
            self.a[y][m] = pid

    def ellipsoid(self, cx, cy, cz, rx, ry, rz, s, only_air=False):
        xs = np.arange(self.W)[None, None, :]; ys = np.arange(self.H)[:, None, None]; zs = np.arange(self.L)[None, :, None]
        m = ((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2 + ((zs - cz) / rz) ** 2 <= 1.0
        if only_air: m &= (self.a == 0)
        self.a[m] = self.pid(s)

    def line(self, p, q, s):
        n = int(max(abs(q[i] - p[i]) for i in range(3))) or 1
        for k in range(n + 1):
            self.set(*(int(round(p[i] + (q[i] - p[i]) * k / n)) for i in range(3)), s)

    def scatter(self, x1, z1, x2, z2, y, pred_ground, s, count, above_ok=lambda st: True, rng=None):
        """Place s at (x, y, z) at random cells where the cell below satisfies pred_ground and the cell is air."""
        rng = rng or self.rng
        placed = 0
        for _ in range(count * 20):
            if placed >= count: break
            x, z = rng.randint(x1, x2), rng.randint(z1, z2)
            if is_air(self.get(x, y, z)) and pred_ground(self.get(x, y - 1, z)):
                self.set(x, y, z, s); placed += 1
        return placed

    # ---- layout declarations -------------------------------------------------------------------------------
    def room(self, id, name, mn, mx):
        self.rooms.append({'id': id, 'name': name, 'min': list(mn), 'max': list(mx)})

    def landmark(self, id, name, role, mn, mx):
        self.landmarks.append({'id': id, 'name': name, 'role': role, 'min': list(mn), 'max': list(mx)})

    def add_chest(self, pos, facing, kind='loot', key=None, water_=False, table=None):
        self.set(*pos, chest(facing, water_))
        c = {'pos': list(pos), 'kind': kind, 'state': chest(facing, water_)}
        if key: c['key'] = key
        if table: c['table'] = table
        self.chests.append(c)

    def seal(self, id, block, mn, mx):
        """A block volume that stands from the start (it overrides the schematic) until an effect clears or fills it."""
        self.seals.append({'id': id, 'block': block, 'min': list(mn), 'max': list(mx)})

    def mark(self, name, pos): self.markers[name] = list(pos)

    # ---- export ----------------------------------------------------------------------------------------------
    def write_schem(self, path):
        W, H, L = self.W, self.H, self.L
        flat = self.a.reshape(-1)           # [y, z, x] -> index x + z*W + y*W*L : matches the Sponge order
        o = io.BytesIO()
        def st(s): bs = s.encode('utf8'); o.write(struct.pack('>H', len(bs))); o.write(bs)
        def tag(t, n): o.write(struct.pack('>B', t)); st(n)
        o.write(b'\x0a'); st('')
        tag(10, 'Schematic')
        tag(3, 'Version'); o.write(struct.pack('>i', 3))
        tag(3, 'DataVersion'); o.write(struct.pack('>i', 3955))
        tag(2, 'Width'); o.write(struct.pack('>h', W)); tag(2, 'Height'); o.write(struct.pack('>h', H)); tag(2, 'Length'); o.write(struct.pack('>h', L))
        tag(11, 'Offset'); o.write(struct.pack('>i', 3)); o.write(struct.pack('>3i', 0, 0, 0))
        tag(10, 'Metadata'); tag(8, 'Name'); st(self.id); tag(8, 'Author'); st('claude'); o.write(b'\x00')
        tag(10, 'Blocks')
        tag(10, 'Palette')
        used = sorted(set(flat.tolist()))
        remap = {old: new for new, old in enumerate(used)}
        for old in used: tag(3, self.pal[old]); o.write(struct.pack('>i', remap[old]))
        o.write(b'\x00')
        # re-encode data with the compact palette
        data = bytearray()
        for v in flat.tolist():
            v = remap[v]
            while v & ~0x7f:
                data.append((v & 0x7f) | 0x80); v >>= 7
            data.append(v)
        tag(7, 'Data'); o.write(struct.pack('>i', len(data))); o.write(data)
        tag(9, 'BlockEntities'); o.write(b'\x0a'); o.write(struct.pack('>i', 0))
        o.write(b'\x00'); o.write(b'\x00'); o.write(b'\x00')
        with gzip.open(path, 'wb') as f: f.write(o.getvalue())
        return len(used)
