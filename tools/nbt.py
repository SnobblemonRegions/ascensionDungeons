import gzip, struct, io


def load(path):
    d = gzip.open(path).read()
    b = io.BytesIO(d)

    def rs():
        n = struct.unpack('>H', b.read(2))[0]
        return b.read(n).decode('utf8', 'replace')

    def val(t):
        if t == 1: return struct.unpack('>b', b.read(1))[0]
        if t == 2: return struct.unpack('>h', b.read(2))[0]
        if t == 3: return struct.unpack('>i', b.read(4))[0]
        if t == 4: return struct.unpack('>q', b.read(8))[0]
        if t == 5: return struct.unpack('>f', b.read(4))[0]
        if t == 6: return struct.unpack('>d', b.read(8))[0]
        if t == 7:
            n = struct.unpack('>i', b.read(4))[0]
            return b.read(n)
        if t == 8: return rs()
        if t == 9:
            et = b.read(1)[0]
            n = struct.unpack('>i', b.read(4))[0]
            return [val(et) for _ in range(n)]
        if t == 10: return comp()
        if t == 11:
            n = struct.unpack('>i', b.read(4))[0]
            return list(struct.unpack('>%di' % n, b.read(4 * n)))
        if t == 12:
            n = struct.unpack('>i', b.read(4))[0]
            return list(struct.unpack('>%dq' % n, b.read(8 * n)))

    def comp():
        o = {}
        while True:
            t = b.read(1)[0]
            if t == 0: return o
            k = rs()
            o[k] = val(t)

    t = b.read(1)[0]
    name = rs()
    return name, comp()


def varints(data):
    out = []
    i = 0
    n = len(data)
    while i < n:
        v = 0
        s = 0
        while True:
            c = data[i]
            i += 1
            v |= (c & 0x7f) << s
            if not c & 0x80: break
            s += 7
        out.append(v)
    return out
