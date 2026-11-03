// Walks a planned route (list of standing cells, WORLD coordinates) with mineflayer's real player physics, performing
// scripted actions on the way. Usage: NODE_PATH=<mineflayer node_modules> node walker.js <route.json> <username> <port>
// route.json: { cells:[[x,y,z]...], actions:[{at:<cell index>, kind, ...}], command?:"/dungeon ...", commandDelayMs? }
// Prints PROGRESS / ACTION / CHAT lines and one final "RESULT {json}" line. Never sprints; jumps only for 1-block rises.
const fs = require('fs');
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');

const [, , routeFile, username, port] = process.argv;
const route = JSON.parse(fs.readFileSync(routeFile, 'utf8'));
const cells = route.cells;
let i = 1, stepStart = Date.now(), started = false, finished = false;
let deaths = 0, minHealth = 20, maxStep = 0, damageTaken = 0;
const log = (...a) => console.log(...a);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username, version: '1.21.1', auth: 'offline' });
bot.on('error', (e) => log('ERROR', e.message));
bot.on('kicked', (r) => log('KICKED', JSON.stringify(r)));
bot.on('death', () => { deaths++; log('DEATH at', bot.entity.position.toString()); finish('died'); });
bot.on('messagestr', (m) => { if (m && m.trim()) log('CHAT ' + m); });
let lastHealth = 20;
bot.on('health', () => {
  minHealth = Math.min(minHealth, bot.health);
  if (bot.health < lastHealth) {
    damageTaken += lastHealth - bot.health;
    const p = bot.entity.position, b = bot.blockAt(p.offset(0, -0.5, 0));
    log('DAMAGE', (lastHealth - bot.health) + ' hp at step ' + i + ' pos ' + p.floored().toString() + ' on ' + (b && b.name));
  }
  lastHealth = bot.health;
});
bot.once('spawn', () => {
  bot.physicsEnabled = false; log('SPAWN');
  if (route.command) setTimeout(() => { log('SAY ' + route.command); bot.chat(route.command); }, route.commandDelayMs || 4000);
});

const centre = (c) => new Vec3(c[0] + 0.5, c[1], c[2] + 0.5);
const actions = (route.actions || []).slice(); const actionLog = []; let busy = false;

function finish(result, extra = {}) {
  if (finished) return; finished = true;
  ['forward', 'back', 'left', 'right', 'jump', 'sprint', 'sneak'].forEach((k) => bot.setControlState(k, false));
  log('RESULT ' + JSON.stringify(Object.assign({ result, reached: i, total: cells.length, deaths, minHealth, damageTaken: +damageTaken.toFixed(1), maxStepSeconds: maxStep,
    actions: actionLog, pos: bot.entity ? bot.entity.position.toArray().map((v) => +v.toFixed(2)) : null }, extra)));
  setTimeout(() => { bot.quit(); process.exit(0); }, 300);
}

// wait until the server has moved us to the start AND the chunk under it has arrived, then enable physics
const gate = setInterval(() => {
  if (!bot.entity || started) return;
  const s = centre(cells[0]);
  if (bot.entity.position.distanceTo(s) > 2.5) return;
  const under = bot.blockAt(s.offset(0, -1, 0));
  if (!under || under.name === 'air') return;
  started = true; bot.physicsEnabled = true; stepStart = Date.now(); log('START at', s.toString(), 'standing on', under.name);
  clearInterval(gate);
}, 100);

async function doAction(a) {
  busy = true;
  ['forward', 'jump', 'sprint', 'sneak'].forEach((k) => bot.setControlState(k, false));
  await sleep(400);
  const rec = { kind: a.kind, pos: a.pos, ok: false };
  try {
    const pos = a.pos ? new Vec3(a.pos[0], a.pos[1], a.pos[2]) : null;
    const block = pos ? bot.blockAt(pos) : null;
    if (a.kind === 'wait') { await sleep(a.ms || 1000); rec.ok = true; }
    else if (a.kind === 'say') { bot.chat(a.text); await sleep(a.ms || 1500); rec.ok = true; }
    else if (a.kind === 'check') {
      // every listed position must (or, with mode 'none', must not) be one of the expected block names
      const names = a.positions.map((p) => { const b = bot.blockAt(new Vec3(p[0], p[1], p[2])); return b ? b.name : null; });
      const hit = names.map((n) => a.expect.includes(n));
      rec.ok = a.mode === 'none' ? hit.every((h) => !h) : hit.every((h) => h);
      rec.sample = [...new Set(names)].slice(0, 6); rec.n = names.length; rec.label = a.label;
    }
    else if (a.kind === 'hook') {
      // ask the test harness to run server console commands, then wait for its verdict
      log('HOOK ' + a.id);
      const done = routeFile + '.' + a.id + '.done', t0 = Date.now();
      while (!fs.existsSync(done) && Date.now() - t0 < (a.ms || 60000)) await sleep(200);
      if (fs.existsSync(done)) { const r = JSON.parse(fs.readFileSync(done, 'utf8')); rec.ok = r.ok; rec.detail = r.detail; rec.id = a.id; }
      else rec.error = 'hook timeout';
    }
    else if (a.kind === 'near') {
      const t = new Vec3(a.pos[0], a.pos[1], a.pos[2]), t0 = Date.now(); let d = 1e9;
      while (Date.now() - t0 < (a.ms || 6000)) { d = bot.entity.position.distanceTo(t); if (d <= (a.radius || 3)) break; await sleep(250); }
      rec.ok = d <= (a.radius || 3); rec.dist = +d.toFixed(1); rec.label = a.label;
    }
    else if (a.kind === 'dimension') {
      const t0 = Date.now(); let dim = null;
      const want = a.expect.replace('minecraft:', '');
      while (Date.now() - t0 < (a.ms || 70000)) { dim = bot.game.dimension; if (dim && dim.replace('minecraft:', '') === want) break; await sleep(500); }
      rec.ok = !!dim && dim.replace('minecraft:', '') === want; rec.dim = dim; rec.label = a.label;
    }
    else {
      if (!block) throw new Error('block not loaded');
      rec.block = block.name;
      await bot.lookAt(pos.offset(0.5, 0.5, 0.5), true);
      if (a.kind === 'lever') {
        const before = block.getProperties().powered;
        await bot.activateBlock(block); await sleep(a.ms || 700);
        const after = bot.blockAt(pos).getProperties().powered;
        rec.before = before; rec.after = after; rec.ok = a.expectToggle === false ? before === after : before !== after;
      } else if (a.kind === 'chest') {
        const w = await Promise.race([bot.openContainer(block), new Promise((_, rej) => setTimeout(() => rej(new Error('open timeout')), 6000))]);
        rec.slots = w.slots.length; rec.ok = true; bot.closeWindow(w); await sleep(300);
      } else if (a.kind === 'take') {
        const w = await Promise.race([bot.openContainer(block), new Promise((_, rej) => setTimeout(() => rej(new Error('open timeout')), 6000))]);
        const item = w.containerItems().find((it) => it.name === a.item);
        rec.found = item ? (item.displayName || item.name) : null;
        if (item) { await w.withdraw(item.type, null, item.count); }
        bot.closeWindow(w); await sleep(400);
        rec.ok = !!item && !!bot.inventory.items().find((it) => it.name === a.item);
      } else if (a.kind === 'use') {
        const held = bot.inventory.items().find((it) => it.name === a.item);
        if (held) await bot.equip(held, 'hand');
        rec.held = held ? held.name : null;
        await bot.activateBlock(block); await sleep(a.ms || 900);
        const after = bot.blockAt(pos);
        rec.after = after ? after.name : null; rec.ok = a.expectOpen === false ? !!after && after.name === block.name : !after || after.name === 'air';
      } else if (a.kind === 'attack') {
        // only the start-dig packet: bot.dig would also mark the block as air in the bot's own world, racing the server's real update
        bot._client.write('block_dig', { status: 0, location: block.position, face: 1, sequence: 0 });
        bot.swingArm('right'); await sleep(a.ms || 800); rec.ok = true;
      } else throw new Error('unknown action ' + a.kind);
    }
  } catch (e) { rec.error = e.message; }
  actionLog.push(rec); log('ACTION ' + JSON.stringify(rec));
  stepStart = Date.now(); lastMove = Date.now();   // time spent on an action must not count as being stuck
  busy = false;
}

let lastPos = null, lastMove = Date.now();
bot.on('physicsTick', () => {
  if (!started || finished || !bot.entity || busy) return;
  const p = bot.entity.position;
  if (actions.length && i - 1 >= actions[0].at) { doAction(actions.shift()); return; }
  if (i >= cells.length) { finish('ok'); return; }
  const t = centre(cells[i]), prev = cells[i - 1];
  const dx = t.x - p.x, dz = t.z - p.z, hd = Math.hypot(dx, dz), dy = t.y - p.y;
  const vertical = prev[0] === cells[i][0] && prev[2] === cells[i][2] && prev[1] !== cells[i][1];
  if (hd < 0.35 && Math.abs(dy) < (vertical ? 0.6 : 0.75)) {
    maxStep = Math.max(maxStep, (Date.now() - stepStart) / 1000); i++; stepStart = Date.now();
    if (i % 25 === 0) log('PROGRESS', i + '/' + cells.length, p.toString());
    return;
  }
  bot.look(Math.atan2(-dx, -dz), 0, true);
  const up = dy > 0.6;
  if (vertical && up) { bot.setControlState('jump', true); bot.setControlState('forward', hd > 0.3); }
  else if (vertical && !up) { bot.setControlState('jump', false); bot.setControlState('forward', false); }
  else { bot.setControlState('forward', hd > 0.28); bot.setControlState('jump', up && bot.entity.onGround); }
  if (!lastPos || p.distanceTo(lastPos) > 0.02) { lastPos = p.clone(); lastMove = Date.now(); }
  else if (Date.now() - lastMove > 1500 && !vertical) { bot.setControlState('jump', bot.entity.onGround); }
  if (Date.now() - stepStart > 12000) {
    const below = bot.blockAt(p.offset(0, -0.5, 0)), tb = bot.blockAt(t.offset(0, -1, 0));
    finish('stuck', { step: i, from: prev, to: cells[i], standingOn: below && below.name, targetFloor: tb && tb.name });
  }
});
setTimeout(() => finish('timeout'), route.timeoutMs || 900000);
