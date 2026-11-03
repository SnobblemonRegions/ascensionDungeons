// Interactive mineflayer driver: reads one JSON command per stdin line, prints "DONE <id>" when finished.
// Commands: {id, chat}, {id, wait}, {id, activate:[x,y,z]}, {id, dig:[x,y,z]}, {id, equip:"item_name"}, {id, block:[x,y,z]} (prints BLOCK name)
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const [, , name, port] = process.argv;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.1', auth: 'offline' });
bot.on('messagestr', (m) => { if (m && m.trim()) console.log('CHAT ' + m); });
bot.on('error', (e) => console.log('ERROR ' + e.message));
bot.on('kicked', (r) => console.log('KICKED ' + JSON.stringify(r)));
bot.once('spawn', () => { console.log('SPAWN'); });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function run(c) {
  if (c.chat) { bot.chat(c.chat); await sleep(c.after || 1500); }
  else if (c.wait) await sleep(c.wait);
  else if (c.activate) { const b = bot.blockAt(new Vec3(...c.activate)); if (!b) console.log('NOBLOCK'); else { try { await bot.activateBlock(b); } catch (e) { console.log('ACTERR ' + e.message); } } await sleep(800); }
  else if (c.dig) { const b = bot.blockAt(new Vec3(...c.dig)); try { await Promise.race([bot.dig(b, true), sleep(1500)]); } catch (e) { console.log('DIGERR ' + e.message); } await sleep(500); }
  else if (c.equip) { const it = bot.inventory.items().find((i) => i.name === c.equip); if (it) await bot.equip(it, 'hand'); else console.log('NOITEM ' + c.equip); }
  else if (c.block) { const b = bot.blockAt(new Vec3(...c.block)); console.log('BLOCK ' + (b ? b.name : 'null')); }
  else if (c.fight) {
    const end = Date.now() + c.fight;
    while (Date.now() < end) {
      const t = bot.nearestEntity((e) => e.type !== 'player' && e.type !== 'other' && e.position.distanceTo(bot.entity.position) < 5 && e.name !== 'item');
      if (t) { try { await bot.lookAt(t.position.offset(0, t.height / 2, 0)); bot.attack(t); } catch (e) { } }
      await sleep(600);
    }
  }
  else if (c.pos) { const p = bot.entity.position; console.log('POS ' + p.x.toFixed(1) + ' ' + p.y.toFixed(1) + ' ' + p.z.toFixed(1)); }
}
let q = Promise.resolve();
require('readline').createInterface({ input: process.stdin }).on('line', (l) => { q = q.then(async () => { const c = JSON.parse(l); await run(c); console.log('DONE ' + c.id); }); });
