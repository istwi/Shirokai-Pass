/* Dedicated local test server only. Requires a season with default rewards and a non-OP PassTester. */
const assert = require('node:assert/strict');
const mc = require('minecraft-protocol');
const fs = require('node:fs');
const client = mc.createClient({host: '127.0.0.1', port: Number(process.env.MC_PORT || 25565), username: 'PassTester', version: '1.21.1', auth: 'offline'});
let windowId = 0, stateId = 0, slots = [], chats = [], actions = [], online = false;
const results = [];
function text(n) {
  if (!n) return '';
  if (typeof n === 'string') return n;
  if (n.type === 'string') return n.value;
  if (n.type === 'compound') {
    const v = n.value;
    return (v.text ? text(v.text) : v[''] ? text(v['']) : v.translate ? text(v.translate) : '') + (v.extra?.value?.value || []).map(x => text({type: 'compound', value:x})).join('');
  }
  return '';
}
const component = (slot, type) => slots[slot]?.components?.find(c => c.type === type)?.data;
const name = slot => text(component(slot, 'custom_name'));
const lore = slot => (component(slot, 'lore') || []).map(text);
const wait = async (condition, label, timeout = 10000) => {
  const start = Date.now();
  while (!condition()) {if (Date.now()-start > timeout) throw Error(`Timeout: ${label}`); await new Promise(r=>setTimeout(r,30));}
};
const pause = () => new Promise(r=>setTimeout(r,180));
async function command(command) { client.write('chat_command', {command}); await pause(); }
async function click(slot) {client.write('window_click', {windowId, stateId, slot, mouseButton:0, mode:0, changedSlots:[], cursorItem:{itemCount:0}}); await pause();}
function pass(label) {results.push(label);console.log('PASS',label);}
client.on('state',state => {if(state === 'configuration') client.write('settings',{locale:'pt_br',viewDistance:2,chatFlags:0,chatColors:true,skinParts:127,mainHand:1,enableTextFiltering:false,enableServerListing:false});});
client.on('ping',p=>client.write('pong',p));
client.on('position',p=>client.write('teleport_confirm',{teleportId:p.teleportId}));
client.on('open_window',p=>{windowId=p.windowId;slots=[];assert.equal(p.inventoryType,3);assert.equal(text(p.windowTitle),'✦ SHIROKAI PASS');});
client.on('window_items',p=>{if(p.windowId===windowId){stateId=p.stateId;slots=p.items;}});
client.on('set_slot',p=>{if(p.windowId===windowId){stateId=p.stateId;slots[p.slot]=p.item;}});
client.on('system_chat',p=>{chats.push(text(p.content));if(p.isActionBar)actions.push(text(p.content));});
client.on('action_bar',p=>actions.push(text(p.text)));
client.on('login',()=>{online=true;});
client.on('error',e=>{console.error(e);process.exitCode=1;});
(async()=>{
 try {
  await wait(()=>online,'login',30000);
  if(process.env.MC_EXTENDED) {await require('./extended.cjs')({command,click,wait,name,lore,pass,results});client.end();return;}
  await command('passe');await wait(()=>name(31)==='Informações do passe','pass');
  assert.equal(slots.length,72);assert.ok(lore(31).some(l=>l.startsWith('XP:')));pass('Pass: 36-slot vanilla container and one information book');
  await command('passeadmin xp add PassTester 999');await wait(()=>chats.some(c=>c.includes('command.unknown.argument')||c.includes('command.unknown.command')),'permission denial');pass('Non-OP administrative XP denied');
  console.log('AWARD_NOW');fs.writeFileSync('/tmp/shirokai-smoke-ready','ready');
  await wait(()=>lore(31).includes('Nível: 6 / 100'),'live XP refresh',90000);pass('XP and level refresh while menu stays open');
  await wait(()=>actions.some(c=>c.includes('nível 6')),'aggregated actionbar');
  assert.equal(actions.filter(c=>c.includes('passe')).length,1);pass('Burst grants produce one actionbar level summary');
  await command('passe recompensas');await wait(()=>lore(31).includes('Página 1 / 12'),'direct rewards');pass('Direct rewards opens current-level page');
  for(let page=1;page<=12;page++) {await wait(()=>lore(31).includes(`Página ${page} / 12`),`page ${page}`);if(page<12)await click(35);}
  assert.equal(name(18),'Nível 100');assert.ok(!slots[19]?.itemCount);pass('All 12 pages; last page contains only level 100');
  for(let i=0;i<11;i++)await click(27);await wait(()=>lore(31).includes('Página 1 / 12'),'first page');
  await click(0);await wait(()=>lore(0)[0]==='Resgatada','claim free 1');pass('Individual claim preserves page');
  await click(0);assert.equal(lore(0)[0],'Resgatada');pass('Repeated click keeps reward claimed');
  await command('passe perfil');await wait(()=>lore(31).includes('Nível: 6 / 100'),'profile alias');pass('Profile information consolidated in book');
  await command('passe historico');await wait(()=>name(0)==='teste','history');pass('Season history');
  await command('passe resgatartudo');await command('passe recompensas');await wait(()=>lore(5)[0]==='Resgatada','claim all');assert.equal(lore(9)[0],'Bloqueada');pass('Claim all awards six free levels; premium stays locked');
  await command('passe pendentes');await wait(()=>name(31)==='Entregas pendentes','pending');pass('Pending deliveries menu');
  fs.writeFileSync('/tmp/shirokai-smoke-results.json',JSON.stringify({passed:results.length,results},null,2));
  console.log(`SUCCESS: ${results.length} functional checks`);client.end();
 } catch(e) {console.error(e);client.end();process.exitCode=1;}
})();
