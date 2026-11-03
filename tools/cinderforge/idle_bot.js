// Joins, runs one chat command, waits, reports what the server said, quits. Usage: node idle_bot.js <name> <port> "<command>" <waitMs>
const mineflayer = require('mineflayer');
const [, , name, port, cmd, waitMs] = process.argv;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.1', auth: 'offline' });
bot.on('messagestr', (m) => { if (m && m.trim()) console.log('CHAT ' + m); });
bot.on('error', (e) => console.log('ERROR ' + e.message));
bot.once('spawn', () => setTimeout(() => { console.log('SAY ' + cmd); bot.chat(cmd); }, 4000));
setTimeout(() => { console.log('DIM ' + bot.game.dimension); bot.quit(); process.exit(0); }, Number(waitMs));
