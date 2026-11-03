"""Declaration helpers: short builders for the layout.json mechanics DSL, shared by all dungeon scripts."""
import kit

AIRB = kit.AIR


def bx(p1, p2=None):
    p2 = p2 or p1
    return {'min': [min(p1[0], p2[0]), min(p1[1], p2[1]), min(p1[2], p2[2])], 'max': [max(p1[0], p2[0]), max(p1[1], p2[1]), max(p1[2], p2[2])]}


def centre(box): return [(box['min'][i] + box['max'][i]) // 2 for i in range(3)]


# ---- effects
def say(text, color='aqua'): return {'do': 'say', 'text': text, 'color': color}
def sound(at, id, vol=1.5, pitch=0.8): return {'do': 'sound', 'at': list(at), 'id': id, 'vol': vol, 'pitch': pitch}
def particles(at, type_='minecraft:cloud', count=30, spread=(1, 1, 1)): return {'do': 'particles', 'at': list(at), 'type': type_, 'count': count, 'spread': list(spread)}
def clear(box, only=None):
    e = {'do': 'clear', 'box': box}
    if only: e['only'] = only
    return e
def fill(box, block, only=None):
    e = {'do': 'fill', 'box': box, 'block': block}
    if only: e['only'] = only
    return e
def give_key(key, at): return {'do': 'give', 'key': key, 'at': list(at)}
def spawn(entity, count, room, name=None, **kw):
    e = {'do': 'spawn', 'entity': entity, 'count': count, 'room': room}
    if name: e['name'] = name
    e.update(kw); return e
def potion(effect, amp=0, duration=100, box=None):
    e = {'do': 'effect', 'effect': effect, 'amp': amp, 'duration': duration}
    if box: e['box'] = box
    return e


# ---- mechanics
def key(d, id, item, name, lore): d.keys[id] = {'item': item, 'name': name, 'lore': lore}


def lever_seq(d, id, levers, on_done, on_step=None, jam=None):
    m = {'id': id, 'type': 'lever_sequence', 'levers': [list(l) for l in levers], 'onDone': on_done}
    if on_step: m['onStep'] = on_step
    if jam: m['jam'] = jam
    d.mechanics.append(m)


def lock(d, id, at_box, key_id, on_open, hint, verb, requires=None):
    m = {'id': id, 'type': 'lock', 'at': at_box, 'key': key_id, 'onOpen': on_open, 'hint': hint, 'verb': verb}
    if requires: m['requires'] = requires
    d.mechanics.append(m)


def sequence(d, id, buttons, order, on_done, done_text='The pattern is complete.', fail_text='Wrong. The sequence resets.'):
    d.mechanics.append({'id': id, 'type': 'sequence', 'buttons': [list(p) for p in buttons], 'order': order, 'onDone': on_done,
                        'doneText': done_text, 'failText': fail_text})


def toggle(d, id, levers_on, on_done, done_text='The mechanisms settle into place.'):
    """levers_on: list of (pos, wanted_powered)."""
    d.mechanics.append({'id': id, 'type': 'toggle_pattern', 'levers': [{'pos': list(p), 'on': on} for p, on in levers_on], 'onDone': on_done, 'doneText': done_text})


def trial(d, id, plate, room, waves, on_done, hold=40):
    d.mechanics.append({'id': id, 'type': 'trial', 'plate': bx(plate), 'room': room, 'hold': hold, 'waves': waves, 'onDone': on_done})


def encounter(d, room, say_text, spawns): d.encounters.append({'room': room, 'say': say_text, 'spawns': spawns})


def mob(entity, count, name=None, health=1.0, equipment=None, pokemon=None):
    e = {'entity': entity, 'count': count, 'health': health}
    if pokemon: e['pokemon'] = pokemon
    if name: e['name'] = name
    if equipment: e['equipment'] = equipment
    return e


def pulse(d, id, **kw):
    h = {'id': id, 'type': 'pulse'}; h.update(kw); d.hazards.append(h)


def zone(d, id, box, effect=None, amp=0, duration=80, every=20, damage=0, **kw):
    h = {'id': id, 'type': 'zone', 'box': box, 'every': every}
    if effect: h.update({'effect': effect, 'amp': amp, 'duration': duration})
    if damage: h['damage'] = damage
    h.update(kw); d.hazards.append(h)


def crumble(d, id, box, only=None, delay=20, restore=140, **kw):
    h = {'id': id, 'type': 'crumble', 'box': box, 'delay': delay, 'restore': restore}
    if only: h['only'] = only
    h.update(kw); d.hazards.append(h)


def pad(d, id, from_box, to, text=None, charge=50, requires=None):
    p = {'id': id, 'from': from_box, 'to': list(to), 'charge': charge}
    if text: p['text'] = text
    if requires: p['requires'] = requires
    d.pads.append(p)


def boss(d, id, room, entity, name, spawn, targets=None, on_block='minecraft:sea_lantern', off_block='minecraft:prismarine',
         health=9.0, scale=1.6, attack=None, color='RED', equipment=None, intro=None, death=None, adds=None, phases=None,
         ward_text='ward', break_text=None, relapse_text=None, window=360, no_gravity=False, aggro=True, awake_effects=None, raid=None):
    bs = {'id': id, 'room': room, 'entity': entity, 'name': name, 'spawn': list(spawn), 'health': health, 'scale': scale,
          'color': color, 'intro': intro or '%s awakens.' % name, 'deathText': death or '%s falls.' % name, 'aggro': aggro}
    if attack: bs['attack'] = attack
    if equipment: bs['equipment'] = equipment
    if no_gravity: bs['noGravity'] = True
    if raid: bs['raid'] = raid
    if targets:
        bs['shield'] = {'targets': targets, 'needed': len(targets), 'onBlock': on_block, 'offBlock': off_block, 'window': window,
                        'breakText': break_text or 'The wards shatter! Strike now!',
                        'relapseText': relapse_text or 'The wards reform and %s calls its servants.' % name,
                        'relapseAdds': adds or []}
    bs['phases'] = phases or []
    d.bosses.append(bs)
    return bs


def finish(d, boss_id, intro, message='The dungeon is yours. It will close shortly.', delay=45):
    d.completion = {'boss': boss_id, 'delaySeconds': delay, 'intro': intro, 'message': message}


def contract_rooms(d, names):
    """Record the catalogue names so the build can assert them."""
    d.catalogue_rooms = names


def wall_lever(d, x, y, z, wall, powered=False):
    """Place a wall lever at (x,y,z) attached to the solid block on side `wall` ('n','s','e','w' = where the wall is)."""
    face = {'n': 'south', 's': 'north', 'e': 'west', 'w': 'east'}[wall]
    d.set(x, y, z, kit.lever('wall', face, powered))
    return (x, y, z)
