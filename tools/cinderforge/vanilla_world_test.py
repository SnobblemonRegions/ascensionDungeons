"""Place a Cinderforge .schem in a real vanilla 1.21.1 server, simulate game time, then diff the saved world against it.

Usage: python vanilla_world_test.py <schem> <scratch_dir> <overworld|nether> [ticks]
No mods, own port, void world. Reads the region files directly (no client needed).
Exit text ends with RESULT: PASS/FAIL.
"""
import gzip, io, os, shutil, struct, subprocess, sys, threading, time, zlib
import numpy as np

JAVA = 'L:/claude-cobbleraids-work/jdk-21.0.12.1+1/bin/java.exe'
JAR = 'L:/ascensionDungeons/testserver/versions/1.21.1/server-1.21.1.jar'
LIBDIR = 'L:/ascensionDungeons/testserver/libraries'
def classpath():
    jars = [JAR]
    for d, _, fs in os.walk(LIBDIR):
        # vanilla needs its own libraries; skip the Fabric loader stack so nothing bootstraps Fabric
        if any(k in d.replace(os.sep, '/') for k in ('net/fabricmc', 'sponge-mixin', 'spongepowered')): continue
        jars += [os.path.join(d, f) for f in fs if f.endswith('.jar')]
    return ';'.join(jars)
sys.path.insert(0, 'L:/ascensionDungeons/tools')
import nbt as NBT


# ---------------------------------------------------------------- schem -> expected arrays, structure nbt
def load_schem(path):
    _, root = NBT.load(path); s = root['Schematic']
    pal = {v: k for k, v in s['Blocks']['Palette'].items()}
    data = np.array(NBT.varints(s['Blocks']['Data']), dtype=np.int32)
    W, H, L = s['Width'], s['Height'], s['Length']
    return pal, data.reshape(H, L, W), (W, H, L)          # [y][z][x]


def split(state):
    name, props = state, {}
    if '[' in state:
        name, rest = state.split('[', 1)
        for kv in rest.rstrip(']').split(','):
            k, v = kv.split('='); props[k] = v
    return name, props


def write_structure(pal, arr, size, path):
    o = io.BytesIO()
    def st(s): bs = s.encode(); o.write(struct.pack('>H', len(bs))); o.write(bs)
    def tag(t, n): o.write(struct.pack('>B', t)); st(n)
    o.write(b'\x0a'); st('')
    tag(3, 'DataVersion'); o.write(struct.pack('>i', 3955))
    tag(9, 'size'); o.write(b'\x03'); o.write(struct.pack('>i', 3)); o.write(struct.pack('>3i', *size))
    ids = sorted(pal); remap = {}
    air_ids = {i for i in ids if split(pal[i])[0] in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air')}
    keep = [i for i in ids if i not in air_ids]
    tag(9, 'palette'); o.write(b'\x0a'); o.write(struct.pack('>i', len(keep)))
    for n, i in enumerate(keep):
        remap[i] = n; name, props = split(pal[i])
        tag(8, 'Name'); st(name)
        if props:
            tag(10, 'Properties')
            for k, v in props.items(): tag(8, k); st(v)
            o.write(b'\x00')
        o.write(b'\x00')
    mask = ~np.isin(arr, list(air_ids))
    ys, zs, xs = np.nonzero(mask)
    tag(9, 'blocks'); o.write(b'\x0a'); o.write(struct.pack('>i', len(xs)))
    lut = np.full(max(ids) + 1, -1, dtype=np.int64)
    for i, n in remap.items(): lut[i] = n
    st_ids = lut[arr[ys, zs, xs]]
    buf = bytearray()
    pos_hdr = b'\x09' + struct.pack('>H', 3) + b'pos' + b'\x03' + struct.pack('>i', 3)
    st_hdr = b'\x03' + struct.pack('>H', 5) + b'state'
    for x, y, z, s_ in zip(xs.tolist(), ys.tolist(), zs.tolist(), st_ids.tolist()):
        buf += pos_hdr + struct.pack('>3i', x, y, z) + st_hdr + struct.pack('>i', s_) + b'\x00'
    o.write(buf)
    tag(9, 'entities'); o.write(b'\x0a'); o.write(struct.pack('>i', 0))
    o.write(b'\x00')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as f: f.write(o.getvalue())
    return len(xs)


# ---------------------------------------------------------------- region reading
def parse_nbt(data):
    b = io.BytesIO(data)
    def rs(): n = struct.unpack('>H', b.read(2))[0]; return b.read(n).decode('utf8', 'replace')
    def val(t):
        if t == 1: return struct.unpack('>b', b.read(1))[0]
        if t == 2: return struct.unpack('>h', b.read(2))[0]
        if t == 3: return struct.unpack('>i', b.read(4))[0]
        if t == 4: return struct.unpack('>q', b.read(8))[0]
        if t == 5: return struct.unpack('>f', b.read(4))[0]
        if t == 6: return struct.unpack('>d', b.read(8))[0]
        if t == 7: n = struct.unpack('>i', b.read(4))[0]; return b.read(n)
        if t == 8: return rs()
        if t == 9:
            et = b.read(1)[0]; n = struct.unpack('>i', b.read(4))[0]
            return [val(et) for _ in range(n)]
        if t == 10: return comp()
        if t == 11: n = struct.unpack('>i', b.read(4))[0]; return list(struct.unpack('>%di' % n, b.read(4 * n)))
        if t == 12: n = struct.unpack('>i', b.read(4))[0]; return np.frombuffer(b.read(8 * n), dtype='>i8')
    def comp():
        o = {}
        while True:
            t = b.read(1)[0]
            if t == 0: return o
            k = rs(); o[k] = val(t)
    t = b.read(1)[0]; rs(); return comp()


STATES, STATE_ID = [], {}
def gid(key):
    if key not in STATE_ID: STATE_ID[key] = len(STATES); STATES.append(key)
    return STATE_ID[key]


IGNORE = {'shape', 'up', 'down', 'north', 'south', 'east', 'west', 'distance'}   # recomputed by the game from neighbours


AIRN = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}


def compat(e, w):
    if e[0] in AIRN and w[0] in AIRN: return True
    if e[0] != w[0]: return False
    wp = dict(w[1])
    return all(v == wp.get(k) for k, v in e[1] if k not in IGNORE)


def read_world_blocks(regdir, x0, y0, z0, W, H, L, miny):
    """Return (names array [y][z][x] of str ids, lava level array, props dict for stairs shape ignored)."""
    sid = np.zeros((H, L, W), dtype=np.int32); STATES.clear(); STATE_ID.clear()
    gid(('minecraft:air', ()))
    cache = {}
    for cx in range(x0 >> 4, (x0 + W - 1 >> 4) + 1):
        for cz in range(z0 >> 4, (z0 + L - 1 >> 4) + 1):
            rx, rz = cx >> 5, cz >> 5
            fn = os.path.join(regdir, 'r.%d.%d.mca' % (rx, rz))
            if fn not in cache: cache[fn] = open(fn, 'rb').read() if os.path.exists(fn) else None
            raw = cache[fn]
            if raw is None: continue
            off = struct.unpack('>I', raw[4 * ((cx & 31) + (cz & 31) * 32):4 * ((cx & 31) + (cz & 31) * 32) + 4])[0]
            sec = (off >> 8) * 4096
            if (off >> 8) == 0: continue
            ln = struct.unpack('>I', raw[sec:sec + 4])[0]; comp = raw[sec + 4]
            ch = parse_nbt(zlib.decompress(raw[sec + 5:sec + 4 + ln]))
            for s in ch['sections']:
                bs = s.get('block_states');
                if not bs: continue
                sy = s['Y'] * 16
                if sy + 15 < y0 or sy > y0 + H - 1: continue
                pal = bs['palette']
                pmap = np.array([gid((p['Name'], tuple(sorted(p.get('Properties', {}).items())))) for p in pal], dtype=np.int32)
                if 'data' not in bs:
                    idx = np.zeros(4096, dtype=np.int32)
                else:
                    bits = max(4, (len(pal) - 1).bit_length()); per = 64 // bits
                    longs = bs['data'].astype('>i8').astype(np.uint64)
                    shifts = (np.arange(per, dtype=np.uint64) * np.uint64(bits))
                    vals = (longs[:, None] >> shifts[None, :]) & np.uint64((1 << bits) - 1)
                    idx = vals.reshape(-1)[:4096].astype(np.int32)
                idx = idx.reshape(16, 16, 16)               # [y][z][x]
                arrs = pmap[idx]
                for ly in range(16):
                    wy = sy + ly - y0
                    if not (0 <= wy < H): continue
                    for lz in range(16):
                        wz = cz * 16 + lz - z0
                        if not (0 <= wz < L): continue
                        xs0 = cx * 16 - x0
                        a, bnd = max(0, -xs0), min(16, W - xs0)
                        if a >= bnd: continue
                        sid[wy, wz, xs0 + a:xs0 + bnd] = arrs[ly, lz, a:bnd]
    return sid


# ---------------------------------------------------------------- server driver
class Srv:
    def __init__(s, cwd):
        s.lines = []; s.p = subprocess.Popen([JAVA, '-Xmx3G', '-cp', classpath(), 'net.minecraft.server.Main', 'nogui'], cwd=cwd, stdin=subprocess.PIPE,
                                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
        threading.Thread(target=s._rd, daemon=True).start()
    def _rd(s):
        for ln in s.p.stdout: s.lines.append(ln.rstrip())
    def send(s, c): s.p.stdin.write(c + '\n'); s.p.stdin.flush()
    def wait(s, sub, since, timeout):
        t = time.time()
        while time.time() - t < timeout:
            for ln in s.lines[since:]:
                if sub in ln: return ln
            time.sleep(0.5)
        raise TimeoutError('timeout waiting for %r; last lines: %s' % (sub, s.lines[-6:]))
    def cmd(s, c, expect, timeout=240):
        n = len(s.lines); s.send(c); return s.wait(expect, n, timeout)


def main():
    schem, scratch, dim = sys.argv[1], sys.argv[2], sys.argv[3]
    ticks = int(sys.argv[4]) if len(sys.argv) > 4 else 3000
    pal, arr, (W, H, L) = load_schem(schem)
    if os.path.exists(scratch): shutil.rmtree(scratch)
    os.makedirs(scratch)
    nblocks = write_structure(pal, arr, (W, H, L), os.path.join(scratch, 'world', 'generated', 'minecraft', 'structures', 'cinderforge.nbt'))
    print('structure written: %d non-air blocks, size %s' % (nblocks, (W, H, L)))
    open(os.path.join(scratch, 'eula.txt'), 'w').write('eula=true\n')
    open(os.path.join(scratch, 'server.properties'), 'w').write(
        'server-port=25591\nonline-mode=false\nlevel-type=minecraft:flat\n'
        'generator-settings={"biome":"minecraft:the_void","layers":[],"structure_overrides":[]}\n'
        'spawn-monsters=false\nspawn-animals=false\nspawn-npcs=false\nview-distance=3\nsimulation-distance=3\n'
        'max-tick-time=-1\nallow-nether=true\ngenerate-structures=false\nenable-rcon=false\n')
    x0, z0 = 1600, 1600
    y0 = 100 if dim == 'overworld' else 40   # nether: stay above the lava sea (y<=31)
    dimid = 'minecraft:overworld' if dim == 'overworld' else 'minecraft:the_nether'
    regdir = os.path.join(scratch, 'world', 'region' if dim == 'overworld' else 'DIM-1/region')
    miny = -64 if dim == 'overworld' else 0
    sv = Srv(scratch)
    try:
        sv.wait('Done (', 0, 180); print('server up')
        sv.cmd('gamerule doMobSpawning false', 'Gamerule', 20)
        sv.cmd('execute in %s run forceload add %d %d %d %d' % (dimid, x0, z0, x0 + W, z0 + L), 'force loaded', 60)
        time.sleep(5)
        if dim == 'nether':
            n = 0
            for ax in range(x0, x0 + W, 32):
                for ay in range(y0, y0 + H, 32):
                    for az in range(z0, z0 + L, 32):
                        sv.send('execute in %s run fill %d %d %d %d %d %d minecraft:air' % (dimid, ax, ay, az, min(ax + 31, x0 + W - 1), min(ay + 31, y0 + H - 1), min(az + 31, z0 + L - 1))); n += 1
            time.sleep(25); print('cleared nether volume with %d fills' % n)
        res = sv.cmd('execute in %s run place template minecraft:cinderforge %d %d %d' % (dimid, x0, y0, z0), 'template', 240)
        print('place:', res.split(']: ')[-1])
        errs = [l for l in sv.lines if 'ERROR' in l or 'Unable' in l or 'Failed' in l]
        print('errors after place:', len(errs), errs[:3])
        if len(sys.argv) > 5 and sys.argv[5] == 'leak':      # control: break one wall next to lava (schem coords 35 16 16)
            sv.send('execute in %s run setblock %d %d %d minecraft:air' % (dimid, x0 + 35, y0 + 16, z0 + 16)); print('CONTROL: removed one block beside a lava channel')
        time.sleep(3)
        r = sv.cmd('tick sprint %d' % ticks, 'Sprint', 600)
        print('tick:', r.split(']: ')[-1])
        sv.cmd('save-all flush', 'Saved', 120)
    finally:
        try: sv.send('stop'); sv.p.wait(timeout=90)
        except Exception: sv.p.kill()
    sid = read_world_blocks(regdir, x0, y0, z0, W, H, L, miny)
    AIRS = {'minecraft:cave_air', 'minecraft:void_air'}
    epal = {}
    for i, st in pal.items():
        nm, pr = split(st); nm = 'minecraft:air' if nm in AIRS else nm
        epal[i] = (nm, tuple(sorted(pr.items())))
    # distinct (expected palette id, world state id) pairs, then judge each pair once
    pairs = arr.astype(np.int64) * (len(STATES) + 1) + sid.astype(np.int64)
    uniq, counts = np.unique(pairs, return_counts=True)
    bad_name, bad_prop, samples = {}, {}, []
    badmask_codes = []
    for u, c in zip(uniq.tolist(), counts.tolist()):
        ei, wi = divmod(u, len(STATES) + 1); e, w = epal[ei], STATES[wi]
        if compat(e, w): continue
        badmask_codes.append(u)
        key = (e[0].replace('minecraft:', ''), w[0].replace('minecraft:', ''))
        if e[0] != w[0]: bad_name[key] = bad_name.get(key, 0) + c
        else: bad_prop[(key[0], str([kv for kv in e[1] if kv[0] not in IGNORE]), str([kv for kv in w[1] if kv[0] not in IGNORE]))] = c
    bad = np.isin(pairs, badmask_codes)
    # interior = free cells (air, plants, lanterns, ladders, lava ...) NOT connected to the box boundary through free cells
    FREE_N = AIRN | {'minecraft:lava', 'minecraft:crimson_roots', 'minecraft:nether_sprouts', 'minecraft:crimson_fungus', 'minecraft:warped_roots',
                     'minecraft:warped_fungus', 'minecraft:soul_lantern', 'minecraft:chain', 'minecraft:ladder', 'minecraft:lever'}
    free = np.isin(arr, [i for i, st in epal.items() if st[0] in FREE_N])
    ext = np.zeros_like(free); ext[0], ext[-1] = free[0], free[-1]; ext[:, 0], ext[:, -1] = free[:, 0], free[:, -1]
    ext[:, :, 0], ext[:, :, -1] = free[:, :, 0], free[:, :, -1]
    while True:
        n = ext.copy()
        n[1:] |= ext[:-1]; n[:-1] |= ext[1:]; n[:, 1:] |= ext[:, :-1]; n[:, :-1] |= ext[:, 1:]; n[:, :, 1:] |= ext[:, :, :-1]; n[:, :, :-1] |= ext[:, :, 1:]
        n &= free
        if (n == ext).all(): break
        ext = n
    interior = free & ~ext
    print('sealed interior cells: %d; open-to-outside free cells in the box: %d' % (interior.sum(), (free & ext).sum()))
    ignorable = ~interior & free        # only EMPTY space outside the shell is ignored; any solid cell that changed counts
    bad_outside = int((bad & ignorable).sum()); bad = bad & ~ignorable
    if bad_outside: print('mismatches in EMPTY space outside the sealed shell (box corners, natural terrain/lava reaching it): %d - ignored' % bad_outside)
    nd = int(bad.sum())
    print('blocks compared: %d, mismatches: %d (name %d, property %d)' % (W * H * L, nd, sum(bad_name.values()), sum(bad_prop.values())))
    for (e, a), k in sorted(bad_name.items(), key=lambda kv: -kv[1])[:12]: print('   expected %s -> world %s  x%d' % (e, a, k))
    for (n, e, a), k in sorted(bad_prop.items(), key=lambda kv: -kv[1])[:12]: print('   %s props expected %s world %s  x%d' % (n, e, a, k))
    lava_ids = [i for i, st in enumerate(STATES) if st[0] == 'minecraft:lava']
    lvl_bad = sum(int(((sid == i) & ~ignorable).sum()) for i in lava_ids if dict(STATES[i][1]).get('level') != '0')
    elava = np.isin(arr, [i for i, st in epal.items() if st[0] == 'minecraft:lava'])
    wlava = np.isin(sid, lava_ids)
    stray = int((wlava & ~elava & ~ignorable).sum())
    print('INSIDE the dungeon: lava expected %d, in world %d, flowing in world %d, lava where none expected %d, expected lava missing %d' % (
        (elava & interior).sum(), (wlava & interior).sum(), lvl_bad, stray, int((elava & ~wlava).sum())))
    ok = nd == 0 and lvl_bad == 0 and stray == 0
    if nd:
        ys, zs, xs = np.nonzero(bad)
        for k in range(min(8, len(xs))): print('   at schem (%d,%d,%d): expected %s world %s' % (xs[k], ys[k], zs[k], epal[int(arr[ys[k], zs[k], xs[k]])], STATES[int(sid[ys[k], zs[k], xs[k]])]))
    print('RESULT:', 'PASS' if ok else 'FAIL')


if __name__ == '__main__': main()
