# Shirokai Pass

Mod **server-side** para Minecraft **1.21.1**, Fabric e Java **21**. O cliente usa os menus de baú vanilla e não precisa instalar o mod. XP funciona sem NPCs, EasyNPC, Shirokai Trainers ou Cobblemon. A entrega de Pokémon exige Cobblemon no servidor; a versão alvo é **1.8.3**.

**Estado da versão 0.2.0:** pacote inicial para testes; compilado, com testes de regressão da versão ampliada ainda pendentes. Não confunda compilação com validação completa no jogo.

Este repositório estava vazio. A especificação disponível está em [docs/REQUISITOS.md](docs/REQUISITOS.md); a especificação anterior citada nesse arquivo não foi fornecida. Consulte [docs/VALIDACAO.md](docs/VALIDACAO.md) para os limites de validação.

## Compilar e instalar

Com JDK 21: `./gradlew build`. O JAR distribuível fica em `build/libs/shirokai-pass-0.2.0.jar` (não use o `sources`). Substitua o JAR antigo em `mods/` (não mantenha duas versões juntas) num servidor Fabric 1.21.1 com Fabric API. Faça backup do mundo antes de atualizar.

Na primeira inicialização são criados `config/shirokaipass.json` e, após a primeira alteração, `<mundo>/shirokaipass/state.json`. Não existe temporada automática. Crie uma pelo console:

```
passeadmin season create primeira 90d 7d
```

**Novas temporadas têm as 200 recompensas desativadas.** O editor contém modelos de um diamante na trilha grátis e dois na Premium, mas eles só ficam disponíveis após configurar e ativar cada prêmio ou intervalo. Temporadas existentes mantêm seus prêmios e resgates. A curva padrão requer 100 XP no primeiro nível, mais 25 por nível seguinte (133.750 XP acumulados para o nível 100). Cada temporada copia a curva, preservando o histórico.

## Jogadores

- `/passe`: página inicial de 54 slots.
- `/passe recompensas`: abre uma das 15 páginas no nível atual; sete níveis por página, última com dois.
- `/passe perfil`, `/passe premium`, `/passe historico`, `/passe info`, `/passe pendentes`.
- `/passe resgatartudo`: reserva todas as recompensas elegíveis da temporada atual.

No histórico, clique em uma temporada para abrir seus prêmios e resgatar durante o período extra. Premium é individual por temporada. Reduzir XP ou desativar Premium não desfaz resgates já registrados. Inventário cheio mantém prêmios prontos na fila; o botão de pendências ou o próximo login tenta novamente.

## XP universal

```
/passeadmin xp add Jogador 100
/passeadmin xp add Jogador 100 missao/UUID/evento-123
/passeadmin xp add Jogador 100 bypass
/passeadmin xp add Jogador 100 missao/UUID/evento-123 bypass
/passeadmin xp set Jogador 1000
/passeadmin xp remove Jogador 100
/passeadmin xp reset Jogador
/passeadmin xp get Jogador
/passeadmin level set Jogador 25
/passeadmin level get Jogador
```

O destinatário aceita nome online, nome de jogador que já entrou neste mundo, ou UUID explícito. Para nomes offline desconhecidos, use um UUID previamente verificado; não são inventados UUIDs nem feitas consultas de identidade sem garantia. Jogadores não precisam de OP. Console, RCON autenticado e fontes autorizadas de outros mods utilizam o mesmo Brigadier.

IDs de transação são globais e permanentes: use um ID único por evento e jogador. Repetir o ID com os mesmos parâmetros não concede novamente, inclusive após trocar de temporada; parâmetros diferentes são rejeitados. O comando simples não deduplica eventos sem ID. Evite o ID literal `bypass`, reservado pela sintaxe do comando. Somente `add` aplica o evento multiplicador, com arredondamento para baixo e rejeição de overflow; a XP é limitada ao nível 100. `set` aceita zero; `remove` e `add` exigem valores positivos.

```
/passeadmin event xp start 2 2h
/passeadmin event xp status
/passeadmin event xp list
/passeadmin event xp stop
```

Durações aceitam inteiro positivo e `s`, `m`, `h` ou `d`. Só um evento de XP fica ativo; iniciar outro encerra o anterior e preserva o histórico. Notificações online são agrupadas por janela de um segundo em actionbar. Subidas de vários níveis produzem um resumo; anúncios públicos podem ser desativados.

## Administração e recompensas

```
/passeadmin premium Jogador true
/passeadmin season list
/passeadmin season end
/passeadmin preview
/passeadmin editor
/passeadmin announcements false
/passeadmin reload
```

Abra `/passeadmin` para o painel administrativo. O editor é um menu de 54 slots: escolha um nível/trilha, adicione ou remova prêmios, selecione item/comando/Pokémon, quantidade e peso, alterne todos os prêmios/sorteio e ative a recompensa. As chances percentuais aparecem no rascunho. Clique em **Revisar e salvar** e confirme; cancelar descarta as alterações. Nenhum JSON é necessário.

Campos curtos usam a bigorna vanilla: digite no campo de nome e clique no corante **Salvar valor** do segundo slot. Escape retorna ao editor. Para textos extensos, **Conteúdo longo** solicita entrada privada no chat; digite `cancelar` para voltar. Termine um trecho com `\` para continuar na próxima mensagem, até 4.096 caracteres. O prazo de entrada é de dois minutos e o rascunho de quinze minutos.

**Copiar item da mão** preserva o item, nome, encantamentos e componentes sem consumir a pilha. Itens também aceitam a sintaxe de componentes do Minecraft 1.21.1, por exemplo `minecraft:diamond[minecraft:custom_name='"Prêmio"']`. O conjunto de itens deve caber em até 36 slots; quantidades ou conjuntos impossíveis são rejeitados antes de salvar.

Em **Intervalo**, escolha nível inicial/final e trilha para aplicar o rascunho a vários níveis, com confirmação. Essa operação exige as permissões de recompensas e lote. Se outro administrador alterar algum prêmio do intervalo enquanto o rascunho estiver aberto, o salvamento será recusado para preservar a edição dele. Resgates antigos nunca são liberados novamente.

O painel permite criar, renomear e encerrar temporadas, alterar período extra, iniciar/encerrar eventos de XP, editar anúncios e as informações Premium. Os comandos `rewards set` e `bulk` continuam disponíveis ao console e validam os mesmos tipos de prêmio.

Exemplos (JSON em uma linha):

```
/passeadmin rewards set primeira 1 false {"mode":"fixed","choices":[{"type":"item","value":"minecraft:diamond","count":3,"weight":1}]}
/passeadmin rewards set primeira 2 true {"mode":"random","choices":[{"type":"item","value":"minecraft:diamond","count":2,"weight":70},{"type":"pokemon","value":"pikachu level=10","count":1,"weight":30}]}
/passeadmin bulk primeira 10 20 false {"mode":"fixed","choices":[{"type":"command","value":"give {player} minecraft:emerald 5","count":1,"weight":1}]}
```

`fixed` entrega todas as escolhas. `random` sorteia exatamente uma: probabilidade = peso / soma dos pesos. Os menus exibem as porcentagens. O sorteio e os prêmios são persistidos antes da entrega, sem rerrolagem após falha ou edição. `pokemon` executa `pokegive <nome> <propriedades>` do Cobblemon; Pokémon e comandos têm `count: 1`. Para vários Pokémon, use várias escolhas em modo `fixed`. Itens aceitam quantidade 1–2304 e componentes nativos de Minecraft 1.21.1, respeitando o limite de 36 slots por recompensa. Para desativar por JSON, use `"enabled": false`; a ausência desse campo mantém a compatibilidade com prêmios antigos.

Comandos de recompensa executam como console, sem OP temporário no jogador. **Permissão de editar recompensas equivale a autorizar comandos de console.** Conceda somente à equipe de confiança. Um comando deve retornar sucesso; efeitos externos parciais exigem conferência manual.

`reload` lê apenas a configuração global; dados e temporadas são editados pelos comandos e não são reinicializados. `season end` encerra XP e preserva a duração do período extra. Uma temporada nova exige que a anterior tenha encerrado; seus IDs nunca são reutilizados.

## Persistência e entregas interrompidas

O estado é salvo sincronamente por troca atômica de arquivo, com `fsync`; a aplicação não substitui estado corrompido por estado vazio. Backups devem incluir o arquivo inteiro junto com os dados dos jogadores. Apenas uma instância do servidor pode usar o mesmo mundo.

Cada recompensa tem reserva única `(temporada, UUID, trilha, nível)`. A fila passa de `READY` a `DELIVERING` antes de efeitos externos e a `DONE` após sucesso. Uma queda durante `DELIVERING` produz `REVIEW` no reinício. Não há repetição automática de uma entrega ambígua: banco local, inventário Minecraft e comandos de outros mods não compartilham uma transação distribuída.

Confira inventário/PC/logs e o ID no menu de pendências. Depois de compensar manualmente se necessário, encerre a pendência:

```
/passeadmin delivery <id> acknowledge
```

Esse comando não entrega novamente. Não apague registros para tentar recuperar prêmios; isso enfraquece a proteção. IDs de XP, histórico e recibos não expiram; faça backups e monitore tamanho e latência de disco. Esta implementação usa snapshots JSON e é indicada inicialmente a servidores pequenos; alto volume requer avaliar armazenamento transacional incremental.

## Permissões

A **Fabric Permissions API v0 0.3.3 está embutida no JAR**, com chamadas diretas. Instale um provedor compatível, como LuckPerms Fabric, para configurar os nós. Sem provedor externo, jogadores comuns têm `shirokaipass.use`, e os nós administrativos exigem nível OP 2; bypass exige OP 4. Falha no provedor nega acesso e registra erro. Console mantém o nível nativo.

| Permissão | Acesso |
| --- | --- |
| `shirokaipass.use` | Menus e resgates do jogador |
| `shirokaipass.admin` | Todas as categorias administrativas; bypass é separado |
| `shirokaipass.admin.xp` | Consultar e alterar XP/nível |
| `shirokaipass.admin.xp.bypass` | Ignorar o multiplicador em uma concessão |
| `shirokaipass.admin.premium` | Ativar/desativar Premium e editar suas informações |
| `shirokaipass.admin.rewards` | Editar recompensas e conferir entregas |
| `shirokaipass.admin.bulk` | Edição em lote; requer também recompensas |
| `shirokaipass.admin.seasons` | Criar, editar e encerrar temporadas |
| `shirokaipass.admin.events` | Gerenciar eventos de XP |
| `shirokaipass.admin.preview` | Prévia administrativa sem resgate |
| `shirokaipass.admin.announcements` | Anúncios e regras exibidas |

`/passeadmin reload` exige o nó global. Para integrações, autorize a fonte que executa o comando; nunca dê OP temporário ao destinatário.


## Configuração e API

`config/shirokaipass.json` controla ícones dos botões iniciais, informações Premium, regras, curva das futuras temporadas e anúncios. Placeholders internos em anúncios/comandos: `{player}`, `{uuid}`, `{level}`, `{xp}`, `{season}`, `{premium}`. Mods externos substituem seus próprios `%player%` antes de executar comandos; nenhum sistema externo de placeholders é obrigatório.

```java
PassApi.addXp(playerUuid, 100);
PassApi.addXp(playerUuid, 100, "meumod/quest/UUID/123");
```

Retorna `CompletableFuture<Model.XpResult>`; agenda a operação na thread do servidor. Não bloqueie essa thread com `.join()`. A API é uma capacidade de código confiável no processo do servidor; seus chamadores devem autorizar a origem. `PassApi.expand(uuid, texto)` expõe os placeholders internos. A API não é necessária para integrar por comandos.
