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
 c.lore=slot=>(c.slots[slot]?.components?.find(x=>x.type==='lore')?.data||[]).map(text);
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
 } else if(phase==='storage'){
  c=await connect('DupeTester');rcon('passeadmin season create audit 30d 7d');rcon('passeadmin xp set DupeTester 100');reward(1,[item('diamond')]);
  assert.ok(rcon('list').includes('DupeTester'));record('Login com armazenamento normal','jogador permanece no mundo');
  rcon('auditstorage fail');other=await connect('LoginDuringFailure');await pause(1200);assert.equal(other.closed,false);assert.ok(rcon('list').includes('LoginDuringFailure'));
  assert.match(rcon('passeadmin storage status'),/indisponível/);record('Login com arquivo de estado substituído por diretório','jogador conectado; passe bloqueado, sem Invalid player data');
  const data=path.join(world,'shirokaipass/state.json'),backup=path.join(world,'shirokaipass/state.json.audit-saved');const before=fs.readFileSync(backup,'utf8');
  await c.command('passe recompensas');await wait(()=>c.name(10)==='Grátis • 1','cached menu');for(let i=0;i<20;i++)c.click(10);await pause(300);assert.equal(count('DupeTester','diamond'),0);assert.equal(fs.readFileSync(backup,'utf8'),before);
  record('Resgates durante falha','20 cliques negados; nenhum item e backup intacto');
  rcon('passeadmin xp add DupeTester 100 blocked-event');assert.equal(fs.readFileSync(backup,'utf8'),before);record('XP durante falha','sem falso sucesso ou alteração do snapshot confirmado');
  rcon('auditstorage repair');assert.match(rcon('passeadmin storage recover'),/recuperado/);assert.equal(profile('DupeTester').xp,100);assert.equal(id('LoginDuringFailure')!==undefined,true);
  await c.command('passe recompensas');for(let i=0;i<30;i++)c.click(10);await wait(()=>deliveries('DupeTester','free:1')[0]?.status==='DONE','claim after repair');assert.equal(count('DupeTester','diamond'),1);record('Recuperação sem reiniciar','100 XP preservidos; nome do jogador novo salvo; 1 prêmio após reparo');
  await c.quit();await other.quit();
 } else if(phase==='storage_corrupt'){
  const data=path.join(world,'shirokaipass/state.json'),backup=data+'.startup-saved';const broken=fs.readFileSync(data,'utf8');assert.equal(broken,'broken-storage-fixture');
  c=await connect('LoginWithCorruptState');await pause(1200);assert.equal(c.closed,false);assert.ok(rcon('list').includes('LoginWithCorruptState'));assert.match(rcon('passeadmin storage status'),/indisponível/);assert.equal(fs.readFileSync(data,'utf8'),broken);record('Login após reinício com JSON corrompido','servidor e login ativos; arquivo corrompido não sobrescrito');
  fs.copyFileSync(backup,data);assert.match(rcon('passeadmin storage recover'),/recuperado/);assert.equal(profile('DupeTester').xp,100);assert.equal(deliveries('DupeTester','free:1').length,1);assert.equal(deliveries('DupeTester','free:1')[0].status,'DONE');
  await c.quit();c=await connect('DupeTester');await c.command('passe recompensas');for(let i=0;i<30;i++)c.click(10);await pause(300);assert.equal(count('DupeTester','diamond'),1);record('Reparo após falha de leitura e reinício','XP, inventário e recibo preservados; nenhum novo prêmio');await c.quit();
 } else if(phase==='admin'){
  c=await connect('AdminTester');other=await connect('ObserverTester');rcon('op AdminTester');rcon('passeadmin season create audit 30d 7d');
  const goEditor=async client=>{await client.command('passeadmin editor');await wait(()=>client.name(10)==='Grátis • 1','editor grid');};
  const save=async client=>{client.click(50);await wait(()=>client.name(30)==='Salvar alterações','confirm');client.click(30);await wait(()=>client.name(10)==='Grátis • 1','saved grid');};
  const input=async(client,slot,value)=>{client.click(slot);await wait(()=>client.name(1)==='Salvar valor','anvil input');client.client.write('name_item',{name:value});await pause(100);client.click(1);await pause(200);};
  await goEditor(c);c.click(10);await wait(()=>c.name(4).startsWith('Modo:'),'reward draft');c.click(10);await wait(()=>c.name(22)==='Quantidade: 1','choice');
  await input(c,22,'2');await wait(()=>c.name(22)==='Quantidade: 2','quantity saved into draft');assert.equal(state().seasons.audit.rewards['free:1'].choices[0].count,1);
  record('Editor usa rascunho sem alterar prêmio antes da confirmação','quantidade 2 no rascunho, 1 no arquivo');
  c.click(22);await wait(()=>c.name(1)==='Salvar valor','anvil reopen');const old=c.window;c.client.write('close_window',{windowId:old});await wait(()=>c.window!==old&&c.name(22)==='Quantidade: 2','Escape returns to draft');
  assert.equal(count('AdminTester','paper'),0);assert.equal(count('AdminTester','lime_dye'),0);record('Bigorna e cancelamento não vazam ícones','zero papéis/corantes no inventário');
  c.click(49);await wait(()=>c.name(48).startsWith('Recompensa:'),'back to draft');c.click(48);await pause(120);c.click(50);await wait(()=>c.name(30)==='Salvar alterações','confirmation');for(let i=0;i<50;i++)c.click(30);await wait(()=>c.name(10)==='Grátis • 1','save spam');
  assert.equal(state().seasons.audit.rewards['free:1'].choices[0].count,2);assert.equal(state().seasons.audit.rewards['free:1'].enabled,true);assert.equal(count('AdminTester','diamond'),0);
  record('50 cliques na confirmação do editor','configuração salva uma vez; nenhum item concedido');
  rcon('passeadmin xp set AdminTester 1000');await c.command('passeadmin preview');await wait(()=>c.name(10)==='Grátis • 1','preview');for(let i=0;i<40;i++){c.click(10);c.click(51);}await pause(400);assert.equal(deliveries('AdminTester').length,0);assert.equal(count('AdminTester','diamond'),0);record('Prévia administrativa sob cliques rápidos','nenhum resgate ou item');
  await goEditor(c);c.click(12);await wait(()=>c.name(4).startsWith('Modo:'),'third draft');c.click(10);await wait(()=>c.name(13)==='Tipo: item','choice type');c.click(13);await wait(()=>c.name(13)==='Tipo: command','command type');c.click(29);await pause(100);c.client.chat('give {player} minecraft:emerald 3');await wait(()=>c.name(13)==='Tipo: command','private chat returns');assert.ok(!other.chats.some(m=>m.includes('give {player} minecraft:emerald 3')));record('Conteúdo longo permanece privado','outro jogador não recebe o texto');
  rcon('op ObserverTester');await goEditor(c);c.click(10);await wait(()=>c.name(4).startsWith('Modo:'),'admin A draft');await goEditor(other);other.click(10);await wait(()=>other.name(4).startsWith('Modo:'),'admin B draft');
  c.click(48);await pause(100);await save(c);other.click(50);await wait(()=>other.name(30)==='Salvar alterações','admin B confirm');other.click(30);await wait(()=>other.chats.some(m=>m.includes('Outro administrador')),'conflict refused');assert.equal(state().seasons.audit.rewards['free:1'].enabled,false);record('Dois administradores editam o mesmo prêmio','segundo salvamento recusado; primeira edição preservada');
  await goEditor(c);c.click(12);await wait(()=>c.name(4).startsWith('Modo:'),'bulk draft');c.click(48);await pause(100);c.click(47);await wait(()=>c.name(20).startsWith('Do nível:'),'range');await input(c,20,'10');await wait(()=>c.name(20)==='Do nível: 10','from');await input(c,24,'12');await wait(()=>c.name(24)==='Até o nível: 12','to');c.click(49);await pause(120);await save(c);
  for(const level of [10,11,12])assert.equal(state().seasons.audit.rewards['free:'+level].enabled,true);for(const level of [9,13])assert.equal(state().seasons.audit.rewards['free:'+level].enabled,false);record('Edição gráfica em lote com confirmação','somente níveis 10–12 alterados');await c.quit();await other.quit();
 } else if(phase==='premium_setup'){
  c=await connect('DupeTester');other=await connect('ObserverTester');
  rcon('passeadmin season create audit 30d 7d');rcon('passeadmin xp set DupeTester 225');
  for(const level of [1,2])rcon('passeadmin rewards set audit '+level+' true '+JSON.stringify({enabled:true,mode:'fixed',choices:[item('gold_ingot')]}));
  const begin=Date.now();assert.match(rcon('passeadmin premium DupeTester 30 dias loja/month-1'),/Premium válido até/);
  const first=state().premiumUntil[id('DupeTester')];assert.ok(first>=begin+30*86400000&&first<=Date.now()+30*86400000);
  assert.match(rcon('passeadmin premium DupeTester 30 dias loja/month-1'),/já processado/);assert.equal(state().premiumUntil[id('DupeTester')],first);
  rcon('passeadmin premium DupeTester 30 dias loja/month-2');assert.equal(state().premiumUntil[id('DupeTester')],first+30*86400000);
  record('Compra mensal e renovação com ID de pedido','30 dias + 30 dias; replay não concede dias extras');
  await other.command('passeadmin premium DupeTester 30 dias');assert.equal(state().premiumUntil[id('DupeTester')],first+30*86400000);record('Jogador comum não concede Premium','assinatura inalterada');
  await c.quit();await other.quit();rcon('passeadmin premium DupeTester false');rcon('passeadmin premium DupeTester 1 minuto loja/short');
  assert.ok(state().premiumUntil[id('DupeTester')]>Date.now());record('Premium concedido por nome offline','validade persistida antes do reinício');
 } else if(phase==='premium_expire'){
  const until=state().premiumUntil[id('DupeTester')];assert.ok(until>Date.now(),'restart took longer than the one-minute test subscription');
  c=await connect('DupeTester');await c.command('passe');await wait(()=>c.lore(13).includes('Premium: Ativo'),'premium profile after restart');record('Premium offline preservado após reinício','perfil e prazo ativos sem nova concessão');
  await c.command('passe recompensas');await wait(()=>c.lore(28)[0]==='Disponível','premium available');c.click(28);await wait(()=>deliveries('DupeTester','premium:1')[0]?.status==='DONE','premium claim');assert.equal(count('DupeTester','gold_ingot'),1);record('Assinatura temporária permite resgate Premium','um prêmio entregue');
  await wait(()=>Date.now()>=until+1500,'automatic expiry',75000);await wait(()=>c.lore(29)[0]==='Bloqueada','open menu refreshed at expiry');
  for(let i=0;i<50;i++)c.click(29);await pause(400);assert.equal(deliveries('DupeTester','premium:2').length,0);assert.equal(count('DupeTester','gold_ingot'),1);assert.match(rcon('passeadmin premium DupeTester status'),/inativo/);
  record('Expiração automática + 50 cliques no prêmio','menu aberto atualizado; segundo prêmio bloqueado sem desativação manual');
  assert.match(rcon('passeadmin premium DupeTester 1 minuto loja/short'),/já processado/);assert.equal(state().premiumUntil[id('DupeTester')],until);record('Replay após vencimento','pedido antigo não reativa assinatura');
  const begin=Date.now();rcon('passeadmin premium DupeTester 1 mes');assert.ok(state().premiumUntil[id('DupeTester')]>=begin+30*86400000);
  rcon('passeadmin premium DupeTester true');rcon('passeadmin premium DupeTester false');assert.match(rcon('passeadmin premium DupeTester status'),/inativo/);assert.equal(state().premiumUntil[id('DupeTester')],undefined);record('Compatibilidade true/false e mês de 30 dias','remoção revoga assinatura e acesso permanente');await c.quit();
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
