// Live check of an Outbreak dive in the water dungeon, played by a mineflayer bot against a real dev server.
//
// It loots rarity chests, banks at a checkpoint, uses a curse altar, waits for a timer chest, opens an outbreak chest and a
// strongbox, charges the portal and extracts; then it checks a knock-out, a walk-out and a disconnect.
//
// How to run (the bot library cannot parse Cobblemon's packets, so the server runs without Cobblemon and the dungeon uses its
// vanilla fallback mobs; Pokémon combat, aggro for Pokémon and AscensionLib payouts are NOT covered by this check):
//   1. npm install mineflayer@4 in some folder; copy this file there.
//   2. In build.gradle, temporarily change Cobblemon's `modImplementation` to `modCompileOnly` (do not commit that).
//   3. run/server.properties: online-mode=false, enforce-secure-profile=false. run/ops.json: op "RunBot"
//      (offline UUID 221e52a3-45e9-3caa-ab49-a8d5537a85c0, level 4). run/eula.txt: eula=true.
//   4. JAVA_TOOL_OPTIONS="-Dascensiondungeons.outbreakSpeed=6" ./gradlew runServer -PcobbleRaidsJar=<CobbleRaids jar>
//   5. node dive_bot.js   (prints PASS/FAIL lines and "N/N passed", then stops the server)
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const results = [];
function check(name, ok, detail) { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail ? '  -> ' + String(detail).slice(0, 260) : '')); }
function connect() {
  return new Promise(resolve => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'RunBot', version: '1.21.1' });
    bot.log = []; bot.bars = [];
    bot.on('message', m => bot.log.push(m.toString()));
    bot.on('actionBar', m => bot.bars.push(m.toString()));
    bot.on('kicked', r => console.log('KICKED', JSON.stringify(r).slice(0, 120)));
    bot.on('error', e => {});
    bot.once('spawn', async () => { await sleep(2500); resolve(bot); });
  });
}
async function cmd(bot, c, wait = 1200) { const n = bot.log.length; bot.chat(c); await sleep(wait); return bot.log.slice(n).join(' | '); }
let seq = 1;
async function use(bot, x, y, z) {   // right-click a block, standing next to it
  await cmd(bot, `/tp @s ${x + 1} ${y} ${z}`, 700);
  let opened = false;
  const onOpen = () => { opened = true; };
  bot._client.once('open_window', onOpen);
  bot._client.write('block_place', { hand: 0, location: { x, y, z }, direction: 1, cursorX: 0.5, cursorY: 0.5, cursorZ: 0.5, insideBlock: false, sequence: seq++ });
  await sleep(1800);
  bot._client.removeListener('open_window', onOpen);
  if (opened) {
    try { const w = bot.currentWindow; if (w) { for (let s = 0; s < 27; s++) { try { await bot.clickWindow(s, 0, 1); } catch (e) {} } bot.closeWindow(w); } } catch (e) {}
    await sleep(600);
  }
  return opened;
}
async function inv(bot) { const r = await cmd(bot, '/data get entity @s Inventory', 1200); return { tagged: (r.match(/ascension_run/g) || []).length, items: (r.match(/id: "/g) || []).length, raw: r }; }
async function startRun(bot) {
  await cmd(bot, '/clear', 600);
  const r = await cmd(bot, '/dungeon type water', 1000);
  for (let i = 0; i < 60; i++) { const s = await cmd(bot, '/dungeon status', 1000); if (/ACTIVE/.test(s)) break; }
  await sleep(3500);
  await cmd(bot, '/effect give @s minecraft:resistance infinite 255 true', 500);
  const d = await cmd(bot, '/dungeon dive', 1500);
  const feats = [...d.matchAll(/feature (\w+) (\S+) (-?\d+) (-?\d+) (-?\d+)/g)].map(m => ({ kind: m[1], rarity: m[2], x: +m[3], y: +m[4], z: +m[5] }));
  return { request: r, describe: d, feats };
}
const stageOf = async bot => { const d = await cmd(bot, '/dungeon dive', 900); const m = d.match(/stage=(\d+)/); return m ? +m[1] : -1; };

(async () => {
  let bot = await connect();
  try {
    // ---------------- A: the full loop, ending in an extraction
    let run = await startRun(bot);
    check('A run is an Outbreak dive with features', run.feats.length >= 8, run.describe.split('|')[0] + ' feats=' + run.feats.length);
    const kinds = k => run.feats.filter(f => f.kind === k);
    check('A intro explains banking and the portal', bot.log.some(l => /Outbreak is building/.test(l)) && bot.log.some(l => /banked at a checkpoint/.test(l)));
    const timer = kinds('TIMER_CHEST')[0];
    let opened = await use(bot, timer.x, timer.y, timer.z);
    check('A timer chest is sealed at stage 0', !opened && bot.bars.some(b => /Sealed until/.test(b)), bot.bars.slice(-1)[0]);
    const chests = kinds('CHEST');
    opened = await use(bot, chests[0].x, chests[0].y, chests[0].z);
    let i1 = await inv(bot);
    check('A chest loot is marked with the run', opened && i1.items > 0 && i1.tagged === i1.items, `${chests[0].rarity} chest: ${i1.items} stacks, ${i1.tagged} marked`);
    let r = await cmd(bot, '/dungeon dive bank', 1500);
    let i2 = await inv(bot);
    check('A a checkpoint banks: items stay, marks go', /haul is banked/.test(r) && i2.items === i1.items && i2.tagged === 0, r);
    opened = await use(bot, chests[1].x, chests[1].y, chests[1].z);
    let i3 = await inv(bot);
    check('A loot after the checkpoint is at risk again', opened && i3.tagged > 0 && i3.tagged < i3.items, `${i3.items} stacks, ${i3.tagged} marked`);
    const altar = kinds('ALTAR')[0];
    const before = await stageOf(bot);
    const n = bot.log.length;
    await use(bot, altar.x, altar.y, altar.z);
    const after = await stageOf(bot);
    check('A curse altar surges the Outbreak', bot.log.slice(n).some(l => /altar's curse/.test(l)) && after > before, `stage ${before} -> ${after}`);
    for (let i = 0; i < 90 && (await stageOf(bot)) < 2; i++) await sleep(1000);
    check('A stage announcements were made', bot.log.some(l => /The Outbreak grows: stage/.test(l)), bot.log.filter(l => /Outbreak grows/.test(l)).length + ' announcement(s)');
    opened = await use(bot, timer.x, timer.y, timer.z);
    check('A timer chest unseals at stage 2', opened);
    const ob = kinds('OUTBREAK_CHEST')[0];
    opened = await use(bot, ob.x, ob.y, ob.z);
    check('A outbreak chest opens', opened, 'base rarity ' + ob.rarity);
    const sb = kinds('STRONGBOX')[0];
    const n2 = bot.log.length;
    opened = await use(bot, sb.x, sb.y, sb.z);
    check('A strongbox opens and wakes guardians', opened && bot.log.slice(n2).some(l => /strongbox wakes the dungeon/.test(l)));
    const portal = kinds('PORTAL')[0];
    const before4 = await inv(bot);
    await cmd(bot, `/tp @s ${portal.x} ${portal.y} ${portal.z}`, 800);
    let extracted = false;
    for (let i = 0; i < 120 && !extracted; i++) {
      for (const t of ['drowned', 'stray', 'zombie', 'skeleton']) bot.chat(`/kill @e[type=${t},distance=..22]`);
      await sleep(1000);
      extracted = bot.log.some(l => /Extracted! You keep everything/.test(l));
    }
    check('A portal charges from kills and extracts the player', extracted, bot.log.filter(l => /Extracted|portal is open/.test(l)).slice(-2).join(' | '));
    await sleep(2500);
    const i4 = await inv(bot);
    check('A extraction keeps every item and clears the marks', i4.items === before4.items && i4.items > i3.items && i4.tagged === 0, `${before4.items} stacks before, ${i4.items} after, ${i4.tagged} marked`);
    r = await cmd(bot, '/dungeon status');
    check('A the player is out of the run', /not in a dungeon run/.test(r), r);

    // ---------------- B: knocked out
    run = await startRun(bot);
    const c2 = run.feats.filter(f => f.kind === 'CHEST');
    await use(bot, c2[0].x, c2[0].y, c2[0].z);
    const b1 = await inv(bot);
    await cmd(bot, '/effect clear @s', 500);
    const n3 = bot.log.length;
    await cmd(bot, '/kill @s', 2500);
    const hp = await cmd(bot, '/data get entity @s Health', 1000);
    const b2 = await inv(bot);
    check('B a lethal hit knocks out instead of killing', bot.log.slice(n3).some(l => /Knocked out/.test(l)) && /entity data: [1-9][\d.]*f/.test(hp), hp);
    check('B unbanked loot is taken, nothing else', b1.tagged > 0 && b2.items === 0 && b2.tagged === 0, `${b1.items} stacks before (${b1.tagged} marked), ${b2.items} after`);
    r = await cmd(bot, '/dungeon status');
    check('B the player is out of the run', /not in a dungeon run/.test(r), r);

    // ---------------- C: banked loot survives a walk-out, unbanked does not
    run = await startRun(bot);
    const c3 = run.feats.filter(f => f.kind === 'CHEST');
    await use(bot, c3[0].x, c3[0].y, c3[0].z);
    await cmd(bot, '/dungeon dive bank', 1200);
    const k1 = await inv(bot);
    await use(bot, c3[1].x, c3[1].y, c3[1].z);
    const k2 = await inv(bot);
    const n4 = bot.log.length;
    await cmd(bot, '/dungeon leave', 3000);
    const k3 = await inv(bot);
    check('C walking out keeps banked loot and loses the rest', bot.log.slice(n4).some(l => /left without extracting/.test(l)) && k3.items === k1.items && k3.tagged === 0 && k2.items > k1.items,
      `${k1.items} banked, ${k2.items} carried, ${k3.items} kept`);

    // ---------------- D: disconnecting inside, then logging back in
    run = await startRun(bot);
    const c4 = run.feats.filter(f => f.kind === 'CHEST');
    await use(bot, c4[0].x, c4[0].y, c4[0].z);
    const d1 = await inv(bot);
    bot.quit();
    await sleep(6000);
    bot = await connect();
    const d2 = await inv(bot);
    check('D loot from a run left by disconnecting is swept at login', d1.tagged > 0 && d2.items === 0 && bot.log.some(l => /Unbanked loot/.test(l)), `${d1.items} stacks before, ${d2.items} after`);
  } catch (e) { console.log('SCENARIO ERROR', e); results.push(false); }
  const failed = results.filter(x => !x).length;
  console.log(`\n${results.length - failed}/${results.length} passed`);
  bot.chat('/stop');
  await sleep(2000);
  process.exit(failed ? 1 : 0);
})();
setTimeout(() => { console.log('TIMEOUT'); process.exit(2); }, 560000);
