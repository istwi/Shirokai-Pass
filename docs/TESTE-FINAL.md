# Teste final — Shirokai Pass 0.3.0

Este é o roteiro da etapa final solicitada. A compilação não substitui estes testes. Use um servidor e um mundo de teste; não reutilize inventários de produção. O relatório da versão 0.1.0 é histórico e não atesta os novos menus.

## Preparar

- Java 21; Minecraft 1.21.1; Fabric Loader 0.16.10 ou compatível; Fabric API 0.116.1+1.21.1 ou compatível.
- `shirokai-pass-0.3.0.jar` em `mods/`. A Fabric Permissions API já está embutida; não é necessário outro JAR dessa biblioteca.
- Cobblemon **1.8.3**, com as dependências oficiais, para a rodada de entrega Pokémon.
- LuckPerms Fabric compatível para a rodada de permissões.
- Um administrador, um jogador sem OP e um segundo administrador para testar conflitos. Cliente Minecraft sem Shirokai Pass; servidor público usa autenticação online. Modo offline é exclusivo dos testes locais em loopback.
- Nenhum mod de NPC é necessário. Faça primeiro a rodada de XP sem NPCs, EasyNPC e Shirokai Trainers.

## Editor e temporadas

1. Abra `/passeadmin`; crie uma temporada com duração e período extra. Confira que os prêmios novos estão **desativados**, sem concessão de diamantes automática.
2. Abra o editor, escolha grátis/Premium e um nível. Alterne modo todos/sorteio, adicione item, comando e Pokémon, quantidade e peso.
3. Edite um campo curto na bigorna, confirme pelo corante **Salvar valor** no segundo slot. Teste Escape, valor vazio, quantidade negativa, peso zero e texto inválido.
4. Use **Conteúdo longo**; a resposta deve ficar privada, `cancelar` deve voltar, e uma barra invertida no final deve juntar a próxima mensagem. Expirar o prazo ou desconectar deve descartar a entrada pendente.
5. Segure um item nomeado/encantado antes de abrir o menu e use **Copiar item da mão**. Confira os componentes no prêmio e que o item original não foi consumido.
6. Cancele o rascunho: nada deve mudar. Reabra, ative o prêmio, revise e confirme: somente então deve mudar e persistir.
7. Aplique um prêmio aos níveis 10–20 pela seleção gráfica de intervalo, incluindo troca de trilha. Os 11 níveis devem mudar juntos; resgates antigos devem permanecer.
8. Dois administradores abrem o mesmo prêmio. O primeiro salva; o segundo deve receber aviso de conflito e não sobrescrever a primeira edição.
9. Configure 70/30 de peso, confira as porcentagens. O modo sorteio deve entregar somente uma escolha; o modo todos, todas as escolhas.
10. Tente itens inexistentes, comando inválido e um conjunto de itens maior que 36 slots: rejeitar antes de disponibilizar. Escolhas aleatórias individualmente também precisam caber em 36 slots.
11. Teste criar, renomear, encerrar, consultar histórico e resgatar no período extra. Não deve existir XP após o fim nem resgate após o prazo final.

## Permissões

12. Sem provedor externo, confira uso comum e fallback administrativo por OP. O console/RCON autorizado concede XP a jogadores sem OP.
13. Com LuckPerms, dê apenas `shirokaipass.use`: deve abrir o passe e não administrar.
14. Dê `shirokaipass.admin.rewards`: deve editar recompensas, mas não criar temporada/evento nem alterar anúncios. Lote exige também `shirokaipass.admin.bulk`.
15. Dê os nós de temporadas, eventos, Premium, anúncios e prévia separadamente e confirme cada limite. Retire a permissão com o menu/entrada de texto aberto: salvar deve ser negado.
16. Teste `shirokaipass.admin.xp.bypass` separadamente; não conceder OP temporário ao destinatário.

## XP, menus e entregas

17. Execute a matriz de 22 requisitos em `docs/VALIDACAO.md`, incluindo console, RCON, outro mod, API, offline, deduplicação, overflow, set sem bônus e reinício.
18. Confira o menu inicial no cliente oficial, a cabeça do jogador, cores, textos, botões, nível e XP. Valide 15 páginas, última com níveis 99–100, prévia sem resgates e seta de retorno ao passe.
19. Três concessões rápidas devem agrupar a actionbar e atualizar um menu já aberto sem consultas de progresso por tick.
20. Resgate item com componentes e comando; conferir inventário e recibo. Repita o clique e o resgate geral: não duplicar.
21. Com Cobblemon 1.8.3, configure `pikachu level=10` e depois espécies, formas, shiny e propriedades usadas pelo servidor. Teste equipe cheia e PC; confirme uma única entrega real e estado `DONE`.
22. Sem Cobblemon, o prêmio Pokémon deve ficar `READY`, com motivo visível, e nunca ser entregue parcialmente. Ao instalar o mod e tentar novamente, entregar uma vez.
23. Encha o inventário, resgate e confira pendência. Libere espaço e tente novamente. Reinicie com pendência: preservar prêmio sorteado e direito reservado.
24. Interrompa uma entrega em mundo descartável: ambiguidade deve ir para `REVIEW`, sem repetição automática. Após conferir/compensar, use `delivery <id> acknowledge`.
25. Teste cliques fora do menu, shift, arrastar, tecla numérica e jogar item fora: ícones de interface não podem entrar no inventário nem substituir itens reais.

## Registrar resultado

Marque cada caso como aprovado, reprovado ou não executado, com as versões dos mods. Se falhar, guarde o log e o estado do mundo de teste sem credenciais. Não declare Cobblemon/LuckPerms ou a revisão visual aprovados antes de executar as respectivas rodadas.

Automação existente: `./gradlew test build`, `./gradlew integrationModJar` e os scripts em `tools/smoke/`. Os dois novos casos do núcleo cobrem recompensas inicialmente desativadas e encerramento antecipado com preservação do período extra; a suíte completa será executada na etapa final.

## Assinatura Premium mensal

- Execute `/passeadmin premium Jogador 30 dias loja/pedido-1`; consultar `status` deve mostrar a validade em Brasília.
- Repetir o pedido não acrescenta dias. Um pedido novo soma 30 dias ao restante. Um mês representa 30 dias.
- Conceda enquanto o jogador estiver offline, reinicie e confira o prazo. Troque de temporada sem perder os dias restantes.
- Em um mundo de teste, conceda um minuto e deixe o menu aberto: expirar deve atualizar a interface e bloquear novos resgates Premium, inclusive sob cliques rápidos.
- Repetir um pedido após expiração ou revogação não pode reativar a assinatura. `false` deve revogar a assinatura e os acessos permanentes.
- `true` mantém acesso permanente na temporada atual; uma concessão temporária nessa temporada deve substituir esse acesso por uma validade.
