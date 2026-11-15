#!/usr/bin/env python3
"""Generate the user's two 100-level tracks without guessing VIP groups or random species."""
import json,re,unicodedata
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=root/'examples/recompensas-original.txt'
items={'poke ball':'cobblemon:poke_ball','great ball':'cobblemon:great_ball','ultra ball':'cobblemon:ultra_ball','master ball':'cobblemon:master_ball','rare candy':'cobblemon:rare_candy','ferro':'minecraft:iron_ingot','ouro':'minecraft:gold_ingot','redstone':'minecraft:redstone','lapis':'minecraft:lapis_lazuli','diamante':'minecraft:diamond','diamantes':'minecraft:diamond','esmeraldas':'minecraft:emerald','bloco de ferro':'minecraft:iron_block','blocos de ferro':'minecraft:iron_block','bloco de ouro':'minecraft:gold_block','blocos de ouro':'minecraft:gold_block','bloco de diamante':'minecraft:diamond_block','blocos de diamante':'minecraft:diamond_block','blocos de esmeralda':'minecraft:emerald_block','bloco de netherite':'minecraft:netherite_block','blocos de netherite':'minecraft:netherite_block'}
for name,item in {'fogo':'fire','agua':'water','trovao':'thunder','folha':'leaf','brilhante':'shiny','noturna':'dusk','alvorecer':'dawn','gelo':'ice','lua':'moon','sol':'sun'}.items():items['pedra '+('do ' if name in ['fogo','trovao','alvorecer','gelo','sol'] else 'da ' if name in ['agua','folha','lua'] else '')+name]='cobblemon:'+item+'_stone'
crates={'basica':'inicial_rara','epic':'caixa_epica','paradoxo':'caixa_paradoxo','ultra beast':'caixa_ultra_beasts','mitica':'caixa_mitica','lendaria':'caixa_lendaria','shiny':'shiny'}
pokemon={'riolu','gastly','larvitar','lucario','gible','dratini','dragonite','bagon','beldum','garchomp','tyranitar','salamence'}
def norm(t):return ''.join(c for c in unicodedata.normalize('NFKD',t.lower()) if not unicodedata.combining(c))
book={'schema':1,'free':{},'premium':{}};track='free'
for line in source.read_text().splitlines():
 if line.strip()=='Passe Pago':track='premium'
 match=re.match(r'^(\d+)\s+(.+)$',line)
 if not match:continue
 level,text=match.groups();choices=[];notes=[]
 for part in text.split(' + '):
  if part.startswith('$'):
   amount=part[1:];factor=1000000 if amount.lower().endswith('m') else 1000 if amount.lower().endswith('k') else 1
   value=int(float(amount.rstrip('kKMm'))*factor);choices.append({'type':'command','value':f'eco deposit {value} impactor:dollars {{player}}','count':1,'weight':1});continue
  m=re.match(r'^(\d+) (.+)$',part)
  if not m:notes.append('Configurar grupo e duração LuckPerms: '+part);continue
  count,name=m.groups();count=int(count);name=norm(name)
  if name in items:choices.append({'type':'item','value':items[name],'count':count,'weight':1})
  elif name in ['token','tokens']:choices.append({'type':'command','value':f'eco deposit {count} impactor:tokens {{player}}','count':1,'weight':1})
  elif name.startswith('caixa ') and name[6:] in crates:
   choices.append({'type':'crate','value':crates[name[6:]],'count':count,'weight':1})
  elif name in pokemon:choices.append({'type':'pokemon','value':name,'count':1,'weight':1})
  else:notes.append('Definir espécie/pool e propriedades: '+part)
 book[track][level]={'enabled':not notes,'description':text,'setupNote':'; '.join(notes),'mode':'fixed','choices':choices}
assert len(book['free'])==len(book['premium'])==100
out=json.dumps(book,ensure_ascii=False,indent=2)+'\n'
(root/'examples/shirokai_01.json').write_text(out)
(root/'src/main/resources/default-rewards.json').write_text(out)
print('200 recompensas; desativadas:',[(t,n) for t in ['free','premium'] for n,r in book[t].items() if not r['enabled']])
