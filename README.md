# Shirokai Pass

Mod **server-side** para Minecraft **1.21.1**, Fabric e Java **21**. O cliente usa os menus de baú vanilla e não precisa instalar o mod. XP funciona sem NPCs, EasyNPC, Shirokai Trainers ou Cobblemon. A entrega de Pokémon exige Cobblemon no servidor; a versão alvo é **1.8.3**.

**Estado:** as validações de cada versão estão em `docs/VALIDACAO.md`. Releases de testes são marcadas como pré-release.

Este repositório estava vazio. A especificação disponível está em [docs/REQUISITOS.md](docs/REQUISITOS.md); a especificação anterior citada nesse arquivo não foi fornecida. Consulte [docs/VALIDACAO.md](docs/VALIDACAO.md) para os limites de validação.

## Interface minimalista (0.4.0)

`/passe` abre diretamente as recompensas em **36 slots**, com nove níveis por página. Sem decoração nem painel inicial: grátis na primeira linha, Premium na segunda, progresso na terceira e somente duas setas e um livro na quarta. As cores indicam **verde** (nível concluído sem prêmio elegível pendente), **amarelo** (recompensa disponível), **laranja** (próximo nível em progresso) e **cinza** (bloqueado). O Premium bloqueado é explicado no tooltip.

O livro reúne temporada, nível, XP do nível, Premium e tempo restante. Clique na recompensa para resgatar. Os ícones mostram itens reais configurados, incluindo componentes; conjuntos/sorteios têm um item representativo e um resumo no tooltip. Prêmios somente de comando usam papel; Pokémon usam uma Poké Bola quando o item do Cobblemon está disponível. A página 12 contém apenas o nível 100; os demais slots ficam vazios. A tela aberta se atualiza a cada segundo, além das atualizações imediatas de XP/resgate.

Dados, configurações, assinaturas, comandos e regras de resgate continuam compatíveis, sem migração. As correções de persistência/login e a API de permissões 0.3.1 permanecem. [Implementação e testes](docs/GUI-0.4.0.md).

## Correção de compatibilidade de permissões (0.3.2)

A 0.3.1 do Shirokai Pass incluía a Fabric Permissions API 0.3.3, destinada ao Minecraft 1.21.3. Isso podia causar `NoSuchMethodError` ao entrar no Minecraft 1.21.1. A 0.3.2 inclui a API 0.3.1 compatível, preserva as verificações e as correções anteriores de persistência/login. [Causa e testes com LuckPerms 5.4.140](docs/PERMISSIONS-FIX-0.3.2.md).

## Correção de login no Windows (0.3.1)

A versão 0.3.0 tentava abrir o diretório de dados com `FileChannel` após gravar o JSON, operação não suportada pelo provedor Windows. A 0.3.1 mantém o `fsync` do arquivo e a substituição segura, sem abrir diretórios como canais no Windows. Os arquivos existentes continuam em `<mundo>/shirokaipass/state.json`; não é preciso apagar ou recriar o progresso.

Se houver uma falha real de acesso, disco ou arquivo bloqueado, o jogador continua entrando e jogando; as alterações/entregas do passe ficam bloqueadas até uma recuperação validada. Após corrigir o problema do sistema de arquivos, use no console:

```text
passeadmin storage status
passeadmin storage recover
```

Dentro do jogo, adicione `/`. Exigem `shirokaipass.admin` (fallback OP 2). Há nova tentativa automática a cada 30 segundos; `recover` força a tentativa imediata. O comando só confirma recuperação após reler um estado válido e efetuar uma gravação real. `/passeadmin reload` também tenta recuperar caso o passe esteja indisponível.

Não apague os auxiliares `state.json.created` e `state.json.pending`. O primeiro impede que dados desaparecidos sejam tratados como uma temporada vazia; o segundo é um journal de recuperação, quando a substituição atômica não é suportada. Faça backup da pasta do passe junto com o mundo e os dados dos jogadores. Em uma entrega com resultado incerto, `REVIEW` exige conferência/compensação manual e não é repetido automaticamente.

[Causa, arquivos e resultados dos testes](docs/LOGIN-FIX-0.3.1.md).

## Compilar e instalar

Com JDK 21: `./gradlew build`. O JAR distribuível fica em `build/libs/shirokai-pass-0.4.2.jar` (não use o `sources`). Substitua o JAR antigo em `mods/` (não mantenha duas versões juntas) num servidor Fabric 1.21.1 com Fabric API. Faça backup do mundo antes de atualizar.

Na primeira inicialização são criados `config/shirokaipass.json` e, após a primeira alteração, `<mundo>/shirokaipass/state.json`. Não existe temporada automática. Crie uma pelo console:

```
passeadmin season create primeira 90d 7d
```

**Novas temporadas têm as 200 recompensas desativadas.** O editor contém modelos de um diamante na trilha grátis e dois na Premium, mas eles só ficam disponíveis após configurar e ativar cada prêmio ou intervalo. Temporadas existentes mantêm seus prêmios e resgates. A curva padrão requer 100 XP no primeiro nível, mais 25 por nível seguinte (133.750 XP acumulados para o nível 100). Cada temporada copia a curva, preservando o histórico.

## Jogadores

- `/passe`: passe de 36 slots aberto no nível atual.
- `/passe recompensas`: mesma tela; 12 páginas, nove níveis por página e última somente com o nível 100.
- `/passe perfil`, `/passe premium` e `/passe info`: mesma tela com informações no livro central.
- `/passe historico`: temporadas anteriores. `/passe pendentes`: fila de entrega; clique no livro para tentar as entregas prontas.
- `/passe resgatartudo`: reserva todas as recompensas elegíveis da temporada atual.

No histórico, clique em uma temporada para abrir seus prêmios e resgatar durante o período extra. O Premium permanente (`true`) é individual por temporada. A assinatura temporária é por UUID e continua válida ao trocar de temporada. Reduzir XP ou desativar Premium não desfaz resgates já registrados. Inventário cheio mantém prêmios prontos na fila; `/passe pendentes` ou o próximo login tenta novamente.

## Premium mensal automático

No console da loja ou do servidor:

```text
passeadmin premium Jogador 30 dias
passeadmin premium Jogador 1 mes
passeadmin premium Jogador status
```

Dentro do jogo, use `/` antes do comando. Uma renovação soma o período ao prazo restante; se expirou, o novo período começa agora. Um mês representa **30 dias**. Também são aceitos `horas` e `minutos`. O prazo é por UUID, é salvo imediatamente, continua offline e após reinício e acompanha as novas temporadas. Não é necessário desativar manualmente: menus, placeholders e resgates verificam a validade real, e o menu aberto é atualizado ao vencer.

Para a loja repetir notificações sem conceder dias duas vezes, use um ID único de pedido:

```text
passeadmin premium Jogador 30 dias loja/pedido-12345
```

O mesmo ID com os mesmos parâmetros não renova novamente; parâmetros diferentes são rejeitados. Sem ID, cada execução representa uma nova concessão e soma o prazo. Um pagamento repetido após remoção ou expiração também não reativa a assinatura. O comando não processa cobranças: a loja deve chamar o comando a cada compra/renovação aprovada.

`/passeadmin premium Jogador true` continua concedendo acesso permanente **na temporada atual**. Uma concessão temporária substitui esse acesso permanente da temporada atual por uma validade. `/passeadmin premium Jogador false` revoga a assinatura e os acessos permanentes armazenados. Os resgates anteriores são preservados. A assinatura ativa permite a trilha Premium das temporadas ainda dentro do prazo de resgate e nas quais o jogador já cumpriu os níveis.

Quem executa precisa de `shirokaipass.admin.premium` ou da permissão global; o destinatário não precisa de OP. Nomes offline já conhecidos e UUID explícito são aceitos. As datas de resposta usam o horário de Brasília.

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

Abra `/passeadmin` para o painel administrativo. A seleção de nível/trilha do editor usa a grade de 36 slots. Os formulários administrativos de edição mantêm seus 54 slots: adicione ou remova prêmios, selecione item/comando/Pokémon, quantidade e peso, alterne todos os prêmios/sorteio e ative a recompensa. As chances percentuais aparecem no rascunho. Clique em **Revisar e salvar** e confirme; cancelar descarta as alterações. Nenhum JSON é necessário.

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

A **Fabric Permissions API v0 0.3.1 está embutida no JAR**, fixada para Minecraft 1.21.1. A integração foi testada com **LuckPerms Fabric 5.4.140**, que inclui a mesma API. O loader exige essa versão compatível; o build verifica uma única cópia no JAR. Sem provedor externo, jogadores comuns têm `shirokaipass.use`, e os nós administrativos exigem nível OP 2; bypass exige OP 4. Falha no provedor nega acesso e registra erro. Console mantém o nível nativo.

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

## Recompensas Pokémon

O passe entrega Pokémon pela API de armazenamento do Cobblemon, à equipe e, quando
ela está cheia, ao PC. Pokémon não são itens do inventário Minecraft. A espécie
é validada ao salvar a recompensa; use, por exemplo, `pikachu level=10 shiny`.
O menu do passe e a lista do editor exibem o modelo do Pokémon fornecido pelo
Cobblemon. Para enxergar esse modelo, o cliente precisa do Cobblemon.

Uma entrega pendente aparece em vermelho no prêmio, com o motivo e a opção de
tentar novamente. O resgate reservado é preservado, sem criar outra recompensa.
Entregas em `REVIEW` precisam de conferência administrativa: não confirme com
`delivery acknowledge` antes de conferir/compensar a entrega, pois esse comando
somente encerra a pendência e não entrega o Pokémon.

A versão 0.4.1 também libera automaticamente para nova tentativa as entregas
antigas em `REVIEW` que contêm somente um Pokémon e o erro exato
`A player is required to run this command here`. Após atualizar e reiniciar,
abra `/passe pendentes` ou clique no prêmio pendente. Entregas mistas ou com
resultado incerto continuam exigindo revisão para evitar duplicação.

### Apresentação do passe — 0.4.2

O livro de informações separa jornada e passe, com barra de XP e valores coloridos.
As recompensas destacam status, Pokémon em lilás, itens em branco, chances em
amarelo e ações em verde. Títulos em negrito e espaços separam os detalhes;
componentes e lore dos itens configurados continuam preservados.
