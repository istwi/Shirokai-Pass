/* Hostile-click regression suite. Only run against the disposable server created by audit.py. */
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),{spawnSync}=require('node:child_process');
const mc=require('minecraft-protocol');
const phase=process.argv[2], results=[];
const world=process.env.MC_WORLD;
function record(name,detail){results.push({name,status:'passed',detail});console.log('PASS',name,detail||'');}
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function wait(condition,label,timeout=15000){const start=Date.now();while(!condition()){if(Date.now()-start>timeout)throw Error('Timeout: '+label);await pause(40);}}
function rcon(command){const r=spawnSync('python3',[path.join(__dirname,'rcon.py'),command],{env:process.env,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();}
const state=()=>JSON.parse(fs.readFileSync(path.join(world,'shirokaipass/state.json'),'utf8'));
const profile=name=>{const entry=Object.entries(state().names).find(([n])=>n===name.toLowerCase());return entry&&state().players['audit/'+entry[1]];};
const id=name=>state().names[name.toLowerCase()];
const deliveries=(name,key)=>Object.values(state().deliveries).filter(d=>d.player===id(name)&&(!key||d.reward===key));
function count(name,item){const response=rcon(`clear ${name} minecraft:${item} 0`);const match=response.match(/Found (\d+) matching/);if(match)return Number(match[1]);if(response.includes('No items'))return 0;throw Error('Unexpected item-count response: '+response);}
function text(n){if(!n)return '';if(typeof n==='string')return n;if(n.type==='string')return n.value;if(n.type==='compound'){const v=n.value;return text(v.text||v['']||v.translate)+(v.extra?.value?.value||[]).map(x=>text({type:'compound',value:x})).join('');}return '';}
async function connect(username){
 const client=mc.createClient({host:'127.0.0.1',port:Number(process.env.MC_PORT),username,version:'1.21.1',auth:'offline'});
 const c={client,online:false,window:0,state:0,slots:[],closed:false,chats:[]};
 client.on('state',s=>{if(s==='configuration')client.write('settings',{locale:'en_us',viewDistance:2,chatFlags:0,chatColors:true,skinParts:127,mainHand:1,enableTextFiltering:false,enableServerListing:false});});
 client.on('ping',p=>client.write('pong',p));client.on('position',p=>client.write('teleport_confirm',{teleportId:p.teleportId}));
 client.on('login',()=>c.online=true);client.on('end',()=>c.closed=true);client.on('error',e=>{c.error=e;});
 client.on('open_window',p=>{c.window=p.windowId;c.slots=[];});
 client.on('window_items',p=>{if(p.windowId===c.window){c.state=p.stateId;c.slots=p.items;}});
 client.on('set_slot',p=>{if(p.windowId===c.window){c.state=p.stateId;c.slots[p.slot]=p.item;}});
 client.on('system_chat',p=>c.chats.push(text(p.content)));
 c.name=slot=>text(c.slots[slot]?.components?.find(x=>x.type==='custom_name')?.data);
 c.command=async command=>{client.write('chat_command',{command});await pause(200);};
 c.click=(slot,mode=0,mouseButton=0,forged=false)=>client.write('window_click',{windowId:c.window,stateId:c.state,slot,mode,mouseButton,changedSlots:forged?[{location:54,item:{itemCount:64,itemId:require('minecraft-data')('1.21.1').itemsByName.diamond.id,addedComponentCount:0,removedComponentCount:0,components:[],removeComponents:[]}}]:[],cursorItem:{itemCount:0}});
 c.quit=async()=>{client.end();await wait(()=>c.closed,'disconnect');};
 await wait(()=>c.online||c.error,'login',30000);if(c.error)throw c.error;await pause(250);return c;
}
function reward(level,choices,mode='fixed'){const json=JSON.stringify({enabled:true,mode,choices});rcon(`passeadmin rewards set audit ${level} false ${json}`);}
const item=(value,count=1)=>({type:'item',value:'minecraft:'+value,count,weight:1});
(async()=>{let c,other;try{
 if(phase==='rapid'){
  c=await connect('DupeTester');other=await connect('ObserverTester');
  rcon('passeadmin season create audit 30d 7d');rcon('scoreboard objectives add audit dummy');rcon('passeadmin xp set DupeTester 1000');
  reward(1,[item('diamond')]);reward(2,[item('emerald'),item('gold_ingot')],'random');
  reward(3,[{type:'command',value:'scoreboard players add DupeTester audit 1',count:1,weight:1}]);
  await c.command('passe recompensas');await wait(()=>c.name(10)==='Grátis • 1','rewards menu');
  for(let i=0;i<150;i++)c.click(10);
  await wait(()=>deliveries('DupeTester','free:1').length===1&&deliveries('DupeTester','free:1')[0].status==='DONE','rapid claim');await pause(400);
  assert.equal(count('DupeTester','diamond'),1);record('150 cliques rápidos no mesmo prêmio','1 recibo, 1 diamante');
  const start=Date.now();for(let i=0;i<100;i++)c.click(51);
  await wait(()=>deliveries('DupeTester').length===3&&deliveries('DupeTester').every(d=>d.status==='DONE'),'claim-all burst');await pause(400);
  assert.equal(count('DupeTester','diamond'),1);assert.equal(count('DupeTester','emerald')+count('DupeTester','gold_ingot'),1);
  assert.match(rcon('scoreboard players get DupeTester audit'),/has 1 /);record('100 cliques rápidos em resgatar tudo','3 recompensas únicas; sorteio e comando executados 1 vez; '+(Date.now()-start)+'ms');
  const before=rcon('data get entity DupeTester Inventory');
  for(const mode of [1,2,3,4,5,6])for(let i=0;i<15;i++)c.click(10,mode,mode===5?0:0,true);
  for(let i=0;i<20;i++){c.click(0);c.click(-999);c.click(80,1,0,true);}
  await pause(800);assert.equal(rcon('data get entity DupeTester Inventory'),before);assert.equal(deliveries('DupeTester').length,3);
  record('Shift, tecla numérica, clone, drop, arrastar, double-click e slots forjados','inventário idêntico; nenhum ícone movido');
  await c.command('passeadmin xp add DupeTester 999');await pause(200);assert.equal(profile('DupeTester').xp,1000);record('Administração por jogador sem OP','XP não alterada');
  await c.command('passe recompensas');c.click(49);await wait(()=>c.name(28)==='✦ Recompensas','back home');record('Retorno após spam de cliques','menu inicial do passe');
  await c.quit();await other.quit();
  rcon('passeadmin xp add DupeTester 100 audit/offline-1');rcon('passeadmin xp add DupeTester 100 audit/offline-1');assert.equal(profile('DupeTester').xp,1100);
  record('XP offline por nome conhecido e transação repetida','+100 uma vez; 1100 XP persistidos');
 } else if(phase==='restart'){
  assert.equal(profile('DupeTester').xp,1100);assert.equal(profile('DupeTester').claimed.length,3);
  assert.match(rcon('passeadmin xp add DupeTester 100 audit/offline-1'),/já processada/);assert.equal(profile('DupeTester').xp,1100);
  c=await connect('DupeTester');assert.equal(count('DupeTester','diamond'),1);await c.command('passe recompensas');for(let i=0;i<50;i++)c.click(51);await pause(500);assert.equal(count('DupeTester','diamond'),1);
  record('Reinício normal + recibos + inventário','1100 XP, 3 resgates, 1 diamante; nenhum novo prêmio após repetir clique');
  reward(4,[item('diamond',2)]);rcon('clear DupeTester');rcon('give DupeTester minecraft:stone 2304');await c.command('passe recompensas');for(let i=0;i<30;i++)c.click(13);
  await wait(()=>deliveries('DupeTester','free:4').length===1,'full inventory reservation');assert.equal(deliveries('DupeTester','free:4')[0].status,'READY');assert.equal(count('DupeTester','diamond'),0);
  record('Inventário cheio + 30 cliques','1 reserva READY, nenhum diamante entregue');await c.quit();
 } else if(phase==='crash'){
  assert.equal(deliveries('DupeTester','free:4')[0].status,'READY');c=await connect('DupeTester');rcon('clear DupeTester');await c.command('passe pendentes');await wait(()=>c.name(48)==='Tentar entregas prontas','pending menu');for(let i=0;i<30;i++)c.click(48);
  await wait(()=>deliveries('DupeTester','free:4')[0].status==='DONE','pending delivered');assert.equal(count('DupeTester','diamond'),2);record('Pendência preservada após reinício e entregue uma vez','2 diamantes após liberar inventário');
  reward(5,[item('diamond',3),{type:'command',value:'auditcheckpoint',count:1,weight:1}]);rcon('save-all flush');await c.command('passe recompensas');c.click(14);
  await wait(()=>fs.existsSync(path.join(world,'audit-checkpoint.json')),'crash checkpoint');assert.equal(deliveries('DupeTester','free:5')[0].status,'DELIVERING');record('Interrupção controlada durante entrega','arquivo DELIVERING confirmado antes do SIGKILL');
  clientClose(c); // Do not wait for the blocked server. The Python controller now kills this exact PID.
 } else if(phase==='recovery'){
  const interrupted=deliveries('DupeTester','free:5');assert.equal(interrupted.length,1);assert.equal(interrupted[0].status,'REVIEW');
  c=await connect('DupeTester');const before=count('DupeTester','diamond');await c.command('passe recompensas');for(let i=0;i<60;i++){c.click(14);c.click(51);}await pause(600);assert.equal(count('DupeTester','diamond'),before);assert.equal(deliveries('DupeTester','free:5').length,1);assert.equal(deliveries('DupeTester','free:5')[0].status,'REVIEW');
  record('Recuperação após SIGKILL + repetição de cliques','REVIEW preservado; '+before+' diamantes antes/depois; sem reentrega automática');
  await c.quit();
 } else throw Error('Unknown phase: '+phase);
 fs.mkdirSync(process.env.MC_RESULTS,{recursive:true});fs.writeFileSync(path.join(process.env.MC_RESULTS,phase+'.json'),JSON.stringify({phase,passed:results.length,results},null,2));
 console.log('SUCCESS',phase,results.length);process.exit(0);
}catch(error){console.error(error.stack||error);if(c)clientClose(c);if(other)clientClose(other);process.exit(1);}})();
function clientClose(c){try{c.client.end();c.client.socket?.destroy();}catch{}}
