# Correção crítica de login — 0.3.1

## Causa raiz confirmada

O caminho era correto: `MinecraftServer.getSavePath(WorldSavePath.ROOT).resolve("shirokaipass/state.json")`. O dado era gravado em **state.json**, e não no diretório. A linha 107 da versão 0.3.0 abria o diretório pai com `FileChannel.open(..., READ)` para sincronizá-lo após o rename. O provedor NIO do Windows rejeita essa operação com `AccessDeniedException`. Assim, uma gravação já substituída no disco era tratada como falha, o serviço bloqueava operações futuras e `remember()` propagava a exceção pelo evento Fabric JOIN até a colocação do jogador no mundo.

## Alterações

- `AtomicFiles.java` e `Store.java`: caminho normalizado, validação de arquivos/diretórios/links, erro de acesso distinguido de ausência, arquivo temporário com `force(true)` e handles fechados antes do rename. Diretórios só são sincronizados quando o provedor suporta; erros reais de leitura/gravação não são ignorados.
- Em sistemas sem `ATOMIC_MOVE`, um journal próprio com checksum contém o snapshot completo antes da substituição. O replay exige schema válido; dados conflitantes válidos/arquivos desconhecidos são preservados e bloqueiam a recuperação. Uma cópia parcial é preservada em backup antes de aplicar o redo.
- Marcador `state.json.created`: um arquivo já existente que desaparece não se transforma silenciosamente em estado vazio num reinício.
- `PassService.java`: estado confirmado separado dos snapshots de resultado incerto; nenhuma mutação ocorre enquanto indisponível. Recuperação relê somente estados admissíveis, marca entregas interrompidas como `REVIEW` e realiza gravação real antes de liberar operações.
- `LoginGuard.java` e `ShirokaiPass.java`: o callback JOIN contém falhas do passe, registra o erro e não propaga a exceção para o Minecraft. Falha na leitura inicial também não impede o servidor e seus jogadores de iniciar.
- `Commands.java`: `/passeadmin storage status` e `/passeadmin storage recover`. Recuperação automática a cada 30 segundos e tentativa imediata por comando, sem reiniciar após reparar uma falha temporária.
- `StorageRegressionTest.java`, harness e controladores: falhas injetadas antes/depois do commit, permissões, conflitos de caminho, corrupção, recuperação e conexões reais. Workflow dedicado testa JDK 21 nativo em Windows e Linux.
- `build.gradle`: versão 0.3.1. Documentação e comandos atualizados.

## Compatibilidade e migração

Não houve mudança de caminho nem de schema do progresso; os snapshots 0.3.0 continuam sendo lidos, sem migração destrutiva. Os auxiliares são criados de forma aditiva. Não existe exclusão automática de diretórios/arquivos desconhecidos, fallback para outro arquivo vazio nem restauração automática de backup antigo. O sistema não altera ACLs/permissões do sistema operacional.

## Resultados executados

[CI Windows e Linux](https://github.com/istwi/Shirokai-Pass/actions/runs/42316417927): Linux **48 aprovados, zero falhas/ignorados**; Windows **47 aprovados, zero falhas e 1 ignorado por não ser POSIX**. A gravação/substituição nativa no Windows foi executada. Negação de escrita foi testada com permissões reais no Linux e falhas injetadas em ambos.

### Requisito de armazenamento → evidência

| Caso solicitado | Evidência |
| --- | --- |
| Login normal | Conexão real e permanência no mundo |
| Login com armazenamento indisponível | Novo jogador entra com state.json substituído por diretório |
| Caminho inexistente | Diretórios criados e round-trip |
| Arquivo esperado é diretório | Bloqueio sem excluir arquivos desconhecidos |
| Diretório esperado é arquivo | Erro controlado, conteúdo preservado |
| Negação de permissão | ACL POSIX real e falha injetada antes do commit |
| Falha durante gravação | Renomeação tardia e cópia não atômica parcial |
| Reinício após falha | Login com JSON corrompido e recuperação posterior |
| Recuperação de dados existentes | XP, UUIDs, recibos e Premium preservados |
| Duplicação | Recibo pós-commit, loot REVIEW e cliques repetidos |
| Integridade após recuperação | Schema/checksum, arquivo desaparecido e estado externo inesperado |

### Verificações funcionais, caso a caso

| # | Caso | Resultado |
| --- | --- | --- |
| 1 | Login com armazenamento normal | Aprovado — jogador permanece no mundo |
| 2 | Login com arquivo de estado substituído por diretório | Aprovado — jogador conectado; passe bloqueado, sem Invalid player data |
| 3 | Resgates durante falha | Aprovado — 20 cliques negados; nenhum item e backup intacto |
| 4 | XP durante falha | Aprovado — sem falso sucesso ou alteração do snapshot confirmado |
| 5 | Recuperação sem reiniciar | Aprovado — 100 XP preservados; nome do jogador novo salvo; 1 prêmio após reparo |
| 6 | Login após reinício com JSON corrompido | Aprovado — servidor e login ativos; arquivo corrompido não sobrescrito |
| 7 | Reparo após falha de leitura e reinício | Aprovado — XP, inventário e recibo preservados; nenhum novo prêmio |
| 8 | 150 cliques rápidos no mesmo prêmio | Aprovado — 1 recibo, 1 diamante |
| 9 | 100 cliques rápidos em resgatar tudo | Aprovado — 3 recompensas únicas; sorteio e comando executados 1 vez; 717ms |
| 10 | Shift, tecla numérica, clone, drop, arrastar, double-click e slots forjados | Aprovado — inventário idêntico; nenhum ícone movido |
| 11 | Administração por jogador sem OP | Aprovado — XP não alterada |
| 12 | Retorno após spam de cliques | Aprovado — menu inicial do passe |
| 13 | XP offline por nome conhecido e transação repetida | Aprovado — +100 uma vez; 1100 XP persistidos |
| 14 | Reinício normal + recibos + inventário | Aprovado — 1100 XP, 3 resgates, 1 diamante; nenhum novo prêmio após repetir clique |
| 15 | Inventário cheio + 30 cliques | Aprovado — 1 reserva READY, nenhum diamante entregue |
| 16 | Pendência preservada após reinício e entregue uma vez | Aprovado — 2 diamantes após liberar inventário |
| 17 | Interrupção controlada durante entrega | Aprovado — arquivo DELIVERING confirmado antes do SIGKILL |
| 18 | Recuperação após SIGKILL + repetição de cliques | Aprovado — REVIEW preservado; 2 diamantes antes/depois; sem reentrega automática |
| 19 | Compra mensal e renovação com ID de pedido | Aprovado — 30 dias + 30 dias; replay não concede dias extras |
| 20 | Jogador comum não concede Premium | Aprovado — assinatura inalterada |
| 21 | Premium concedido por nome offline | Aprovado — validade persistida antes do reinício |
| 22 | Premium offline preservado após reinício | Aprovado — perfil e prazo ativos sem nova concessão |
| 23 | Assinatura temporária permite resgate Premium | Aprovado — um prêmio entregue |
| 24 | Expiração automática + 50 cliques no prêmio | Aprovado — menu aberto atualizado; segundo prêmio bloqueado sem desativação manual |
| 25 | Replay após vencimento | Aprovado — pedido antigo não reativa assinatura |
| 26 | Compatibilidade true/false e mês de 30 dias | Aprovado — remoção revoga assinatura e acesso permanente |
| 27 | Editor usa rascunho sem alterar prêmio antes da confirmação | Aprovado — quantidade 2 no rascunho, 1 no arquivo |
| 28 | Bigorna e cancelamento não vazam ícones | Aprovado — zero papéis/corantes no inventário |
| 29 | 50 cliques na confirmação do editor | Aprovado — configuração salva uma vez; nenhum item concedido |
| 30 | Prévia administrativa sob cliques rápidos | Aprovado — nenhum resgate ou item |
| 31 | Conteúdo longo permanece privado | Aprovado — outro jogador não recebe o texto |
| 32 | Dois administradores editam o mesmo prêmio | Aprovado — segundo salvamento recusado; primeira edição preservada |
| 33 | Edição gráfica em lote com confirmação | Aprovado — somente níveis 10–12 alterados |

Total: **33 verificações funcionais aprovadas**. As primeiras sete focam o bug de login; as restantes cobrem cliques, offline/reinícios/SIGKILL, Premium mensal e editor. Os logs do servidor de falha foram verificados para ausência de `Couldn't place player in world` e `Invalid player data`. Resultados estruturados: [audit-0.3.1.json](audit-0.3.1.json).

Os fixtures foram corrigidos para usar nomes vanilla de até 16 caracteres e para não herdar OP de uma rodada administrativa anterior. Somente as execuções concluídas após essas correções compõem os resultados acima.

## Recuperação e limites

Substitua o JAR antigo, preservando a pasta de dados, e reinicie uma vez para carregar a atualização. Se restar uma falha real de permissão/arquivo bloqueado, repare o acesso e execute `passeadmin storage recover`. Se existir conflito de caminhos ou um arquivo corrompido, ele permanece preservado para reparo; não remova dados de progresso para tentar voltar a entrar no servidor. O login já permanece liberado enquanto o passe está bloqueado.

Uma falha no meio de efeitos externos não pode oferecer transação distribuída com Minecraft/outros mods. `REVIEW` preserva o recibo e evita repetição, mas pode exigir compensação manual; a correção não promete recuperar dados destruídos pelo hardware ou um backup incoerente. Backups devem incluir pasta do passe e dados de jogadores juntos.

Compatibilidade compilada: Minecraft **1.21.1**, Fabric, Java **21**. Servidor dedicado de teste executado no Linux, testes de arquivos também executados no Windows. Entrega real de Pokémon/Cobblemon 1.8.3, LuckPerms real e revisão estética no cliente oficial permanecem fora desta rodada.

JAR: `build/libs/shirokai-pass-0.3.1.jar`; release publicada pelo workflow existente, com checksum e sources.
