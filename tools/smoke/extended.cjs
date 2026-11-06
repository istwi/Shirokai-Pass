/* Additional destructive smoke checks for the same disposable world AFTER the basic smoke. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {spawnSync} = require('node:child_process');
const path = require('node:path');
module.exports = async ({command,click,wait,name,lore,pass,results}) => {
 const rcon = cmd => {const r=spawnSync('python3',[path.join(__dirname,'rcon.py'),cmd],{encoding:'utf8',env:process.env});assert.equal(r.status,0,r.stderr);return r.stdout;};
 const state = ()=>JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../run/world/shirokaipass/state.json'),'utf8'));
 const id='f8ac07b7-9784-3b87-a2ea-8f9e54d98628';
 const progress=()=>state().players[`teste/${id}`];
 const delivery=reward=>Object.values(state().deliveries).find(d=>d.player===id&&d.reward===reward);
 assert.equal(progress().xp,1000);assert.equal(progress().claimed.length,6);assert.equal(progress().premium,true);
 await command('passe perfil');await wait(()=>lore(13).includes('XP total: 1000'),'restart XP');assert.ok(lore(30).includes('Grátis resgatadas: 6'));pass('Full server restart preserves XP, claims and premium');
 assert.match(rcon('passeadmin xp add PassTester 100 rcon/test-1'),/já processada/);assert.equal(progress().xp,1000);pass('RCON authenticated retry after restart deduplicates');
 rcon('passeadmin level set PassTester 25');await command('passe recompensas');await wait(()=>name(4).includes('• 4 / 15'),'level-25 page');pass('Direct rewards opens page 4 for level 25');
 rcon('passeadmin rewards set teste 25 true {"mode":"fixed","choices":[{"type":"item","value":"minecraft:diamond","count":2,"weight":1},{"type":"command","value":"give {player} minecraft:emerald 2","count":1,"weight":1}]}');
 await command('passe recompensas');await wait(()=>name(4).includes('• 4 / 15'),'reopen edited rewards');await click(31);await wait(()=>lore(31)[0]==='Resgatada','premium mixed claim');
 assert.equal(delivery('premium:25').status,'DONE');const inventory=rcon('data get entity PassTester Inventory');assert.ok(inventory.includes('minecraft:emerald'));assert.ok(inventory.includes('minecraft:diamond'));pass('Premium reward delivers items plus a console command');
 rcon('passeadmin rewards set teste 25 false {"mode":"random","choices":[{"type":"item","value":"minecraft:diamond","count":1,"weight":70},{"type":"item","value":"minecraft:emerald","count":1,"weight":30}]}');
 await command('passe recompensas');await click(13);await wait(()=>lore(13)[0]==='Resgatada','random reward');assert.equal(delivery('free:25').prizes.length,1);assert.equal(delivery('free:25').status,'DONE');pass('Weighted random reward persists exactly one delivered choice');
 rcon('passeadmin rewards set teste 24 true {"mode":"fixed","choices":[{"type":"pokemon","value":"pikachu level=10","count":1,"weight":1}]}');
 await command('passe recompensas');await click(30);await wait(()=>delivery('premium:24')?.status==='READY','missing mod pending');assert.match(delivery('premium:24').error,/Cobblemon/);pass('Missing Cobblemon queues Pokemon reward without duplicate delivery');
 rcon('clear PassTester');rcon('give PassTester minecraft:stone 2304');await click(45);await click(16);await wait(()=>delivery('free:7')?.status==='READY','inventory full pending');assert.match(delivery('free:7').error,/slots/);pass('Full inventory keeps item reward READY without loss');
 rcon('clear PassTester');await command('passe pendentes');await wait(()=>name(48)==='Tentar entregas prontas','pending');await click(48);await wait(()=>delivery('free:7').status==='DONE','retry item delivery');assert.equal(delivery('premium:24').status,'READY');pass('Pending item delivered once after freeing inventory; Pokemon remains pending');
 fs.writeFileSync('/tmp/shirokai-extended-results.json',JSON.stringify({passed:results.length,results},null,2));
 console.log(`SUCCESS: ${results.length} extended checks`);
};
