// Live check of an Outbreak dive in the water dungeon, played by a mineflayer bot against a real dev server.
//
// It gets in the way a player does (/dive, the picker, a warning, a second click), loots rarity chests, banks at a checkpoint, uses a curse altar, waits for a timer chest, opens an outbreak chest and a
// strongbox, checks the first-dive tutorial (welcome, title, Guide bar, guide window, graduation), walks out through the tunnel and down the stairway into the Depths and back up again (its own client physics, no
// teleport), reads the extraction screen, leaves free on its first extraction and pays the 35% toll on its second; then it checks a
// knock-out, a walk-out, a disconnect, and beating the gatekeeper.
//
// How to run (the bot library cannot parse Cobblemon's packets, so the server runs without Cobblemon and the dungeon uses its
// vanilla fallback mobs; Pokémon combat, aggro for Pokémon and AscensionLib payouts are NOT covered by this check):
//   1. npm install mineflayer@4 mineflayer-pathfinder in some folder; copy this file there.
//   2. In build.gradle, temporarily change Cobblemon's `modImplementation` to `modCompileOnly` (do not commit that).
//   3. run/server.properties: online-mode=false, enforce-secure-profile=false. run/ops.json: op "RunBot"
//      (offline UUID 221e52a3-45e9-3caa-ab49-a8d5537a85c0, level 4). run/eula.txt: eula=true.
//   4. JAVA_TOOL_OPTIONS="-Dascensiondungeons.outbreakSpeed=12" ./gradlew runServer -PcobbleRaidsJar=<CobbleRaids jar>
//   5. node dive_bot.js   (prints PASS/FAIL lines and "N/N passed", then stops the server)
const mineflayer = require('mineflayer');
const { pathfinder, Movements, goals } = require('mineflayer-pathfinder');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const results = [];
function check(name, ok, detail) { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail ? '  -> ' + String(detail).slice(0, 260) : '')); }
function connect() {
  return new Promise(resolve => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'RunBot', version: '1.21.1' });
    bot.loadPlugin(pathfinder);
    bot.log = []; bot.bars = []; bot.titles = []; bot.boss = {};
    // a title arrives as plain text, JSON or an NBT text component depending on the library version: keep whatever it is as a string to search
    bot.on('title', (text, type) => { bot.titles.push((type || 'title') + ':' + (typeof text === 'string' ? text : JSON.stringify(text))); });
    const bossText = b => { try { return b.title.toString(); } catch (e) { return String(b.title); } };
    bot.on('bossBarCreated', b => { bot.boss[b.entityUUID] = bossText(b); });
    bot.on('bossBarUpdated', b => { bot.boss[b.entityUUID] = bossText(b); });
    bot.on('bossBarDeleted', b => { delete bot.boss[b.entityUUID]; });
    bot.on('message', m => bot.log.push(m.toString()));
    bot.on('actionBar', m => bot.bars.push(m.toString()));
    bot.on('kicked', r => console.log('KICKED', JSON.stringify(r).slice(0, 120)));
    bot.lastForced = 0;
    bot.on('forcedMove', () => { bot.lastForced = Date.now(); });
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
  for (let i = 0; i < 16 && !opened; i++) await sleep(500);     // freshly generated ground can take a few seconds
  await sleep(600);
  bot._client.removeListener('open_window', onOpen);
  if (opened) {
    try { const w = bot.currentWindow; if (w) { for (let s = 0; s < 27; s++) { try { await bot.clickWindow(s, 0, 1); } catch (e) {} } bot.closeWindow(w); } } catch (e) {}
    await sleep(600);
  }
  return opened;
}
// Walk there on foot, the way a player does: no digging, no building, no teleport. Counts any move the server forced on the bot.
async function walkTo(bot, x, y, z, range, ms = 150000) {
  // These checks are about the walk. At twelve times speed the Outbreak is several stages up by now and a spell can throw the
  // bot into the air as it sets off, so the enemies around it are removed first and a walk that stops short is tried again.
  let result = null;
  for (let attempt = 0; attempt < 3; attempt++) {
    await cmd(bot, '/kill @e[type=!minecraft:player,type=!minecraft:text_display,type=!minecraft:item_display,type=!minecraft:item,distance=..48]', 600);
    await cmd(bot, '/effect clear @s minecraft:levitation', 300);
    for (let i = 0; i < 20 && !bot.entity.onGround; i++) await sleep(250);
    // After a /tp the bot library goes on sending its old position for a while and the server puts it back each time (the
    // "moved too quickly" lines in the server log). Let that finish, or it is counted against the walk.
    for (let i = 0; i < 60 && Date.now() - bot.lastForced < 1500; i++) await sleep(250);
    if (attempt > 0) { bot.clearControlStates(); bot.setControlState('back', true); await sleep(400); bot.clearControlStates(); await sleep(300); }   // step back off whatever it was pressed against
    const one = await walkOnce(bot, x, y, z, range, ms);
    // The walk is judged on the attempt that gets there; where an earlier attempt stuck is kept and printed.
    const stuck = (result ? result.stuck : []).concat(one.ok && one.short <= range + 2 ? [] : [`${one.at} after ${one.forced} correction(s)${one.why ? ', ' + one.why : ''}`]);
    result = { ...one, jump: Math.max(one.jump, result ? result.jump : 0), drop: Math.max(one.drop, result ? result.drop : 0), from: result ? result.from : one.from, tries: attempt + 1, stuck };
    if (one.ok && one.short <= range + 2) break;
    result.ok = false;
  }
  return result;
}
async function walkOnce(bot, x, y, z, range, ms) {
  const mv = new Movements(bot);
  mv.canDig = false; mv.allow1by1towers = false; mv.allowParkour = false; mv.scafoldingBlocks = [];
  bot.pathfinder.setMovements(mv);
  const from = bot.entity.position.clone();
  // A correction is the server nudging the bot back (a shove from a mob or a spell does that now and then); a jump is how far
  // one moved it. Being carried somewhere would show as a jump of many blocks.
  let forced = 0, lowest = from.y, jump = 0, last = from.clone();
  const onForced = () => { forced++; jump = Math.max(jump, last.distanceTo(bot.entity.position)); last = bot.entity.position.clone(); };
  const onMove = () => { lowest = Math.min(lowest, bot.entity.position.y); last = bot.entity.position.clone(); };
  bot.on('forcedMove', onForced); bot.on('move', onMove);
  let why = '', walking = true;
  try {
    // Give up on this attempt early if the server keeps putting the bot back: it is pressed against something and not getting past.
    const pushedBack = (async () => { while (walking && forced < 80) await sleep(250); if (walking) throw new Error('kept being put back'); await new Promise(() => {}); })();
    pushedBack.catch(() => {});
    bot.once('goal_reached', () => { walking = false; });
    await Promise.race([bot.pathfinder.goto(new goals.GoalNear(x, y, z, range)), sleep(ms).then(() => { throw new Error('timed out'); }), pushedBack]);
  } catch (e) { why = String(e && e.message || e); try { bot.pathfinder.setGoal(null); } catch (e2) {} }
  walking = false;
  bot.removeListener('forcedMove', onForced); bot.removeListener('move', onMove);
  const at = bot.entity.position;
  const short = Math.round(Math.hypot(at.x - x - 0.5, at.y - y, at.z - z - 0.5) * 10) / 10;       // how far from where it was going
  return { ok: !why, why, forced, jump: Math.round(jump * 10) / 10, short, from: from.floored().toString(), at: at.floored().toString(), far: Math.round(from.distanceTo(at)), drop: Math.round(from.y - lowest) };
}
async function inv(bot) {
  const r = await cmd(bot, '/data get entity @s Inventory', 1200);
  const sum = re => [...r.matchAll(re)].reduce((n, m) => n + Number(m[1]), 0);
  return { tagged: (r.match(/ascension_run/g) || []).length, items: (r.match(/id: "/g) || []).length, raw: r,
           total: sum(/count: (\d+)/g), markedTotal: sum(/count: (\d+), Slot: \d+b, components: \{"minecraft:custom_data": \{ascension_run/g) };
}
// Step onto the portal and wait for the extraction window; returns {window, shown} or null.
async function tollWindow(bot, portal) {
  const opened = new Promise(resolve => { const t = setTimeout(() => resolve(null), 8000); bot.once('windowOpen', w => { clearTimeout(t); resolve(w); }); });
  await cmd(bot, `/tp @s ${portal.x + 4} ${portal.y} ${portal.z}`, 600);   // step off first, so stepping on shows the screen
  await cmd(bot, `/tp @s ${portal.x} ${portal.y} ${portal.z}`, 600);
  const w = await opened;
  if (!w) return null;
  await sleep(600);
  let shown = 0; const names = [];
  for (let i = 0; i < 36; i++) { const it = w.slots[i]; if (it) { shown += it.count; names.push(it.count + 'x' + it.name); } }
  return { window: w, shown, names, title: (typeof w.title === 'string' ? w.title : JSON.stringify(w.title)).slice(0, 200), pay: w.slots[45] && w.slots[45].name, fight: w.slots[49] && w.slots[49].name };
}
// A player's way in: /dive opens the picker; a dungeon that is not the recommended first one warns on the first click and
// starts on the second. Leaves the bot inside a water run, like startRun.
async function enterByPicker(bot) {
  await cmd(bot, '/clear', 600);
  const text = it => JSON.stringify(it && it.nbt ? it.nbt : it && it.components ? it.components : it || {});
  const opened = new Promise(resolve => { const t = setTimeout(() => resolve(null), 6000); bot.once('windowOpen', w => { clearTimeout(t); resolve(w); }); });
  bot.chat('/dive');
  const w = await opened;
  await sleep(600);
  const items = w ? w.slots.slice(0, 18).map((it, i) => ({ i, s: text(it) })).filter(x => x.s !== '{}') : [];
  const water = items.find(x => /Drowned Reservoir/.test(x.s)), normal = items.find(x => /Hearthwarden Village/.test(x.s));
  check('A /dive opens a picker of the seventeen type dungeons, gentlest first', !!w && items.length === 17 && !!normal && normal.i === 0 && !!water
    && /Choose a dungeon/.test(typeof w.title === 'string' ? w.title : JSON.stringify(w.title)), w ? items.length + ' dungeons' : 'no window');
  check('A the gentlest dungeon is recommended for a first dive, and a harder one says so', !!normal && /Recommended for your first dive/.test(normal.s) && !!water
    && /This is a tier 3 dungeon/.test(water.s) && /Enemies: level 12 to 30/.test(water.s) && !/Recommended/.test(water.s), water ? water.s.slice(0, 200) : '');
  if (!w || !water) return null;
  const n = bot.log.length;
  try { await bot.clickWindow(water.i, 0, 0); } catch (e) {}
  await sleep(1200);
  const st = await cmd(bot, '/dungeon status', 900);
  check('A the first click only warns', bot.log.slice(n).some(l => /before you go in/.test(l)) && bot.log.slice(n).some(l => /For a first dive, The Hearthwarden Village/.test(l))
    && /not in a dungeon run/.test(st), bot.log.slice(n).filter(l => /first dive|before you go in/.test(l)).join(' | '));
  const n2 = bot.log.length;
  try { await bot.clickWindow(water.i, 0, 0); } catch (e) {}
  let active = false;
  for (let i = 0; i < 60 && !active; i++) { const s = await cmd(bot, '/dungeon status', 1000); active = /ACTIVE/.test(s); }
  check('A the second click goes in', active && bot.log.slice(n2).some(l => /Entering The Drowned Reservoir \(Water, tier 3\)/.test(l)), bot.log.slice(n2).filter(l => /Entering/.test(l))[0]);
  return finishStart(bot, 'picker');
}
async function startRun(bot) {
  await cmd(bot, '/clear', 600);
  const r = await cmd(bot, '/dungeon type water', 1000);
  for (let i = 0; i < 60; i++) { const s = await cmd(bot, '/dungeon status', 1000); if (/ACTIVE/.test(s)) break; }
  return finishStart(bot, r);
}
async function finishStart(bot, r) {
  await sleep(3500);
  await cmd(bot, '/effect give @s minecraft:resistance infinite 255 true', 500);
  const d = await cmd(bot, '/dungeon dive', 1500);
  const feats = [...d.matchAll(/feature (\w+) (\S+) (-?\d+) (-?\d+) (-?\d+)( depths)?/g)].map(m => ({ kind: m[1], rarity: m[2], x: +m[3], y: +m[4], z: +m[5], depths: !!m[6] }));
  return { request: r, describe: d, feats };
}
const stageOf = async bot => { const d = await cmd(bot, '/dungeon dive', 900); const m = d.match(/stage=(\d+)/); return m ? +m[1] : -1; };

// Click the pay button and wait to be let out. If the haul changed while the screen was open (a stage rising adds a win), the
// server shows the screen again instead of taking something other than what was shown: confirm that one too.
async function confirmPay(bot, since) {
  for (let attempt = 0; attempt < 3; attempt++) {
    const n = bot.log.length;
    try { await bot.clickWindow(45, 0, 0); } catch (e) {}
    for (let i = 0; i < 20; i++) {
      await sleep(500);
      if (bot.log.slice(since).some(l => /Extracted! You leave with/.test(l))) return true;
      if (bot.log.slice(n).some(l => /Your haul changed while the screen was open/.test(l))) break;
    }
    await sleep(800);
  }
  return false;
}

(async () => {
  let bot = await connect();
  try {
    // ---------------- A: the full loop, ending in an extraction
    let r = await cmd(bot, '/dive party', 900);
    const selfInvite = await cmd(bot, '/dive invite RunBot', 900);
    check('A the party commands answer: alone, and nobody invites themselves', /You are diving alone/.test(r) && /already in your own party/.test(selfInvite), r + ' || ' + selfInvite);
    let run = await enterByPicker(bot);
    if (!run) run = await startRun(bot);
    check('A run is an Outbreak dive with features', run.feats.length >= 8, run.describe.split('|')[0] + ' feats=' + run.feats.length);
    const portalBar = () => Object.values(bot.boss).find(t => /^Extraction portal/.test(t));
    check("A the portal's bar points the way out and says the first extraction is free", /^Extraction portal (\S+ \d+m(, (above|below) you)?|right here): your first extraction is free$/.test(portalBar() || ''), portalBar());
    const kinds = k => run.feats.filter(f => f.kind === k);
    // a player who has never dived gets the tutorial instead of the briefing: a welcome in chat and on screen, and a Guide bar
    const guideBar = () => Object.values(bot.boss).find(t => /^Guide: /.test(t));
    check('A a first dive opens with the tutorial welcome, not the briefing', bot.log.some(l => /Guide: Welcome to your first dive/.test(l)) && bot.log.some(l => /at risk until you leave through the extraction portal/.test(l))
      && !bot.log.some(l => /Outbreak is building/.test(l)), bot.log.filter(l => /Guide: /.test(l)).join(' | '));
    check('A the welcome is shown as a title on screen', bot.titles.some(t => /Welcome to your first dive/.test(t)), bot.titles.join(' | '));
    check('A the Guide bar names the first goal and points the way', /^Guide: Open a loot chest  \S+ \d+m/.test(guideBar() || ''), guideBar());
    let w = null;
    const guideOpened = new Promise(resolve => { const t = setTimeout(() => resolve(null), 6000); bot.once('windowOpen', x => { clearTimeout(t); resolve(x); }); });
    bot.chat('/dungeon guide');
    w = await guideOpened;
    await sleep(500);
    const pages = w ? w.slots.slice(0, 27).filter(Boolean) : [];
    check('A /dungeon guide opens a window with a page for every lesson', !!w && pages.length >= 21 && pages.length <= 27 && /Dive guide/.test(typeof w.title === 'string' ? w.title : JSON.stringify(w.title)), w ? pages.length + ' pages' : 'no window');
    if (w) { const before = (await inv(bot)).total; try { await bot.clickWindow(0, 0, 0); } catch (e) {} await sleep(300); bot.closeWindow(w); await sleep(400);
      check('A nothing can be taken out of the guide', (await inv(bot)).total === before, before + ' item(s) before and after'); }
    const timer = kinds('TIMER_CHEST')[0];
    let opened = await use(bot, timer.x, timer.y, timer.z);
    check('A timer chest is sealed at stage 0', !opened && bot.bars.some(b => /Sealed until/.test(b)), bot.bars.slice(-1)[0]);
    const chests = kinds('CHEST');
    opened = await use(bot, chests[0].x, chests[0].y, chests[0].z);
    let i1 = await inv(bot);
    check('A chest loot is marked with the run', opened && i1.items > 0 && i1.tagged === i1.items, `${chests[0].rarity} chest: ${i1.items} stacks, ${i1.tagged} marked`);
    r = await cmd(bot, '/dungeon dive bank', 1500);
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
    // how far the curse pushes the clock is unit-tested; here: it is announced, the altar is spent, and the stage never falls
    const spent = (await cmd(bot, '/dungeon dive', 1200)).includes(`feature ALTAR - ${altar.x} ${altar.y} ${altar.z} used`) || /ALTAR \S+ -?\d+ -?\d+ -?\d+( depths)? used/.test(bot.log.slice(n).join(' '));
    check('A curse altar is taken: announced, spent, and the Outbreak does not fall', bot.log.slice(n).some(l => /altar's curse/.test(l)) && spent && after >= before, `stage ${before} -> ${after}`);
    for (let i = 0; i < 150 && (await stageOf(bot)) < 2; i++) await sleep(1000);
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
    // the Depths: walk out of the entrance room through the tunnel and down the stairway, loot a chest there, and walk back up
    const mouth = kinds('DESCENT')[0], door = kinds('RETURN')[0];
    check('A the dive has a tunnel to the Depths and many more features than rooms above', !!mouth && !!door && run.feats.length >= 30, run.feats.length + ' features');
    // chests[0] and chests[1] were opened above; Lootr rolls a chest once per player, so take one not yet opened
    const far = c => Math.abs(c.x - door.x) + Math.abs(c.z - door.z);
    const deep = chests.slice(2).filter(c => c.depths).sort((a, b) => far(a) - far(b));
    await cmd(bot, `/tp @s ${mouth.x} ${mouth.y} ${mouth.z}`, 2500);       // start at the tunnel's mouth, in the entrance room
    let nb = bot.bars.length;
    // The walk proves the way down: through the tunnel and down the stairway to the maze's doorway. Deeper rooms are a fight at
    // twelve times speed (nests, hazard rooms, a full Outbreak), which is the dungeon working, not the way down failing.
    // Six blocks past the doorway, along the way the tunnel runs, is inside the maze's first room.
    const along = Math.abs(door.z - mouth.z) > Math.abs(door.x - mouth.x) ? [0, Math.sign(door.z - mouth.z)] : [Math.sign(door.x - mouth.x), 0];
    const inside = { x: door.x + 6 * along[0], y: door.y, z: door.z + 6 * along[1] };
    const down = await walkTo(bot, inside.x, inside.y, inside.z, 1);
    check('A the player walks through the tunnel and down the stairway into the Depths, with no teleport',
      down.ok && down.forced <= 3 && down.jump < 2 && down.drop >= 8 && bot.bars.slice(nb).some(b => /descend into the Depths/.test(b)),
      JSON.stringify(down));
    const before5 = await inv(bot);
    opened = await use(bot, deep[0].x, deep[0].y, deep[0].z);
    const after5 = await inv(bot);
    // count items, not stacks: new loot of a kind already carried merges into the stack that is there
    check('A a chest down in the Depths gives marked loot', opened && after5.markedTotal > before5.markedTotal, `${deep.length} unopened rarity chests are in the Depths`);
    await cmd(bot, `/tp @s ${door.x} ${door.y} ${door.z}`, 2500);           // back to the doorway, to walk up from the bottom of the stairs
    nb = bot.bars.length;
    const up = await walkTo(bot, mouth.x, mouth.y, mouth.z, 1);
    check('A the player walks back up the stairway to the entrance room, with no teleport', up.ok && up.forced <= 3 && up.jump < 2 && bot.bars.slice(nb).some(b => /climb back/.test(b)), JSON.stringify(up));
    // by now the tutorial has explained what the player met, one lesson at a time, and the Guide bar has moved on to getting out
    const told = bot.log.filter(l => /^Guide: /.test(l)).map(l => l.replace('Guide: ', ''));
    check('A the tutorial explained things as they were met', ['The Outbreak', 'Loot chests', 'Your loot is at risk', 'The Depths'].every(t => told.includes(t)) && new Set(told).size === told.length, told.join(' | '));
    check('A the Guide bar now asks the player to get out', /^Guide: Get back to the extraction portal and leave/.test(guideBar() || ''), guideBar());
    // extraction: the screen shows exactly what the toll would take. This is the player's first extraction, so it is free.
    const portal = kinds('PORTAL')[0];
    const before4 = await inv(bot);
    const toll = await tollWindow(bot, portal);
    const expected = Math.max(1, Math.round(before4.markedTotal * 0.35));
    check('A the extraction screen shows what the 35% toll would take, and offers the first extraction free', !!toll && toll.shown === expected && toll.pay === 'lime_concrete'
      && toll.fight === 'netherite_sword' && /your first is free/.test(toll.title),
      toll ? `${before4.markedTotal} unbanked item(s): shows ${toll.shown} [${toll.names.join(', ')}], expected ${expected}` : 'no window');
    try { await bot.clickWindow(0, 0, 0); } catch (e) {}          // try to take a shown item: nothing may move
    await sleep(800);
    const still = await inv(bot);
    check('A nothing can be taken out of the extraction screen', still.total === before4.total, `${before4.total} item(s) before, ${still.total} after the click`);
    const toll2 = await tollWindow(bot, portal);
    const n5 = bot.log.length;
    const extracted = await confirmPay(bot, n5);
    await sleep(2000);
    const i4 = await inv(bot);
    check('A the first extraction is free: nothing shown is taken, and the player is let out', extracted && !!toll2 && toll2.shown > 0 && i4.total === before4.total && i4.tagged === 0
      && bot.log.slice(n5).some(l => /Your first extraction is free: nothing was taken/.test(l)),
      `${before4.total} item(s) before, toll showed ${toll2 ? toll2.shown : '?'}, ${i4.total} after, ${i4.tagged} marked`);
    r = await cmd(bot, '/dungeon status');
    check('A the player is out of the run', /not in a dungeon run/.test(r), r);
    // leaving through the portal finishes the tutorial
    r = await cmd(bot, '/dungeon tutorial', 1000);
    const recapA = bot.log.slice(n5).filter(l => /^Dive recap|^  (You extracted|Outbreak: stage|Kept: |Tip: )/.test(l)).map(l => l.trim());
    check('A the dive ends with a recap: how it ended, the stage, what was kept and lost, and a tip', recapA.length === 5 && recapA[0] === 'Dive recap: The Drowned Reservoir'
      && recapA[1] === 'You extracted free: your first extraction cost nothing.' && /^Outbreak: stage \d \(.+\) after \d+m \d\ds\. Chests opened: \d+(\. Deepest: The [A-Za-z ]+)?(\. Nests and Alphas beaten: \d+)?\.$/.test(recapA[2])
      && /^Kept: \d+ items?, \d+ wins?\. Lost: 0 items, 0 wins\.$/.test(recapA[3]) && /^Tip: /.test(recapA[4]), recapA.join(' | '));
    check('A the free extraction is explained, finishes the tutorial and is used up', bot.log.some(l => /Guide: Extracted/.test(l)) && bot.log.some(l => /Your first extraction was free/.test(l))
      && bot.titles.some(t => /Extracted/.test(t)) && /Dive tutorial: finished/.test(r) && /extractions 1/.test(r) && /First extraction: used/.test(r), r);

    // ---------------- A2: the second extraction pays the toll
    const nB = bot.log.length;
    run = await startRun(bot);
    check('A2 a graduate gets the short briefing and no Guide bar', bot.log.slice(nB).some(l => /Outbreak is building/.test(l)) && !bot.log.slice(nB).some(l => /Guide: Welcome/.test(l))
      && !Object.values(bot.boss).some(t => /^Guide: /.test(t)), Object.values(bot.boss).join(' | '));
    {
      const cs = run.feats.filter(f => f.kind === 'CHEST');
      await use(bot, cs[0].x, cs[0].y, cs[0].z);
      await use(bot, cs[1].x, cs[1].y, cs[1].z);
      const gate = run.feats.filter(f => f.kind === 'PORTAL')[0];
      const had = await inv(bot);
      const due = Math.max(1, Math.round(had.markedTotal * 0.35));
      const t1 = await tollWindow(bot, gate);
      check('A2 the second time the screen asks for the toll', !!t1 && t1.shown === due && t1.pay === 'red_concrete' && /fight or pay/.test(t1.title),
        t1 ? `${had.markedTotal} unbanked item(s): shows ${t1.shown}, expected ${due}, title "${t1.title}"` : 'no window');
      const nT = bot.log.length;
      const out = await confirmPay(bot, nT);
      await sleep(2000);
      const left = await inv(bot);
      const recap2 = bot.log.slice(nT).filter(l => /^  (You extracted|Kept: )/.test(l)).map(l => l.trim());
      check('A2 the recap counts exactly what the toll took', recap2[0] === 'You extracted by paying the toll.' && !!t1 && new RegExp('^Kept: ' + (had.total - t1.shown) + ' items?, \\d+ wins?\\. Lost: ' + t1.shown + ' items?, ').test(recap2[1] || ''),
        recap2.join(' | '));
      check('A2 paying the toll takes exactly what was shown and lets the player out', out && !!t1 && left.total === had.total - t1.shown && left.tagged === 0,
        `${had.total} item(s) before, toll showed ${t1 ? t1.shown : '?'}, ${left.total} after, ${left.tagged} marked | ${bot.log.slice(nT).filter(l => /Toll paid/.test(l))[0] || ''}`);
      await cmd(bot, '/clear', 600);
    }

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
    check('B the knock-out is recapped with a tip', bot.log.slice(n3).some(l => /^Dive recap: The Drowned Reservoir/.test(l)) && bot.log.slice(n3).some(l => /^  You were knocked out/.test(l))
      && bot.log.slice(n3).some(l => /^  Tip: /.test(l)), bot.log.slice(n3).filter(l => /knocked out|Tip: |Kept: /.test(l)).join(' | '));
    check('B a lethal hit knocks out instead of killing', bot.log.slice(n3).some(l => /Knocked out/.test(l)) && /entity data: [1-9][\d.]*f/.test(hp), hp);
    // a player's first knock-out is softer: half of the unbanked loot is kept (rounded their way), unmarked
    const keptB = b1.markedTotal - Math.floor(b1.markedTotal / 2);
    check('B the first knock-out keeps half of the unbanked loot', b1.markedTotal > 1 && b1.total === b1.markedTotal && b2.total === keptB && b2.tagged === 0
      && bot.log.slice(n3).some(l => /It was your first, so you keep half/.test(l)) && bot.log.slice(n3).some(l => /Guide: Knocked out/.test(l))
      && bot.log.slice(n3).some(l => /This was your first knock-out, so you kept half/.test(l)), `${b1.markedTotal} unbanked item(s) before, ${b2.total} after (expected ${keptB}), ${b2.tagged} marked`);
    r = await cmd(bot, '/dungeon status');
    check('B the player is out of the run', /not in a dungeon run/.test(r), r);
    r = await cmd(bot, '/dungeon tutorial', 900);
    check('B the softer knock-out is used up', /First knock-out: used/.test(r) && /knock-outs 1/.test(r), r);
    // ---------------- B2: the second knock-out is the ordinary one
    run = await startRun(bot);
    {
      const cs = run.feats.filter(f => f.kind === 'CHEST');
      await use(bot, cs[0].x, cs[0].y, cs[0].z);
      const had = await inv(bot);
      await cmd(bot, '/effect clear @s', 500);
      const nK = bot.log.length;
      await cmd(bot, '/kill @s', 2500);
      const left = await inv(bot);
      check('B2 a second knock-out takes everything unbanked', had.tagged > 0 && left.items === 0 && bot.log.slice(nK).some(l => /Knocked out\. You keep what you banked/.test(l))
        && bot.log.slice(nK).some(l => /You lost the loot you had not banked/.test(l)) && !bot.log.slice(nK).some(l => /It was your first/.test(l)),
        `${had.items} stacks before (${had.tagged} marked), ${left.items} after`);
      r = await cmd(bot, '/dungeon status');
      check('B2 the player is out of the run', /not in a dungeon run/.test(r), r);
    }
    // the tutorial can be started over and switched off
    r = await cmd(bot, '/dungeon tutorial reset', 900);
    const r2 = await cmd(bot, '/dungeon tutorial', 900);
    const r3 = await cmd(bot, '/dungeon tutorial off', 900);
    const r4 = await cmd(bot, '/dungeon tutorial', 900);
    check('B the tutorial can be started over and switched off, and starting over does not give the free extraction back', /started over/.test(r) && /does not come back/.test(r) && /Dive tutorial: on/.test(r2)
      && /Dives 0/.test(r2) && /First extraction: used/.test(r2) && /tutorial off/.test(r3) && /Dive tutorial: off/.test(r4), [r, r2, r3, r4].join(' || '));

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
    // ---------------- E: beating the gatekeeper lets the player leave with everything
    run = await startRun(bot);
    const c5 = run.feats.filter(f => f.kind === 'CHEST');
    await use(bot, c5[0].x, c5[0].y, c5[0].z);
    const g1 = await inv(bot);
    const portal5 = run.feats.filter(f => f.kind === 'PORTAL')[0];
    const toll5 = await tollWindow(bot, portal5);
    const n6 = bot.log.length;
    try { await bot.clickWindow(49, 0, 0); } catch (e) {}
    await sleep(2500);
    check('E challenging calls out the gatekeeper', !!toll5 && bot.log.slice(n6).some(l => /calls out the gatekeeper/.test(l)), bot.log.slice(n6).slice(-1)[0]);
    await cmd(bot, `/tp @s ${portal5.x} ${portal5.y} ${portal5.z}`, 600);
    let won = false;
    for (let i = 0; i < 40 && !won; i++) {
      for (const t of ['drowned', 'stray', 'zombie', 'skeleton']) bot.chat(`/kill @e[type=${t},distance=..40]`);
      await sleep(1000);
      won = bot.log.slice(n6).some(l => /Extracted! You leave with/.test(l));
    }
    await sleep(2000);
    const g2 = await inv(bot);
    check('E the gatekeeper falls, the portal opens and the player keeps everything', won && bot.log.slice(n6).some(l => /gatekeeper has fallen/.test(l)) && g2.total === g1.total && g2.tagged === 0,
      `${g1.total} item(s) before, ${g2.total} after, ${g2.tagged} marked`);
  } catch (e) { console.log('SCENARIO ERROR', e); results.push(false); }
  const failed = results.filter(x => !x).length;
  console.log(`\n${results.length - failed}/${results.length} passed`);
  bot.chat('/stop');
  await sleep(2000);
  process.exit(failed ? 1 : 0);
})();
setTimeout(() => { console.log('TIMEOUT'); process.exit(2); }, 575000);
