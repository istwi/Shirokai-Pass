# Auditoria — Shirokai Pass 0.3.0

29 testes do núcleo e 26 verificações funcionais passaram. Não foi observada duplicação nos cenários abaixo. Os resultados são limitados aos cenários executados.

Os testes executaram em servidor Fabric dedicado 1.21.1, Java 21, clientes de protocolo vanilla, mundo isolado e Fabric Permissions API embutida.

## Verificações funcionais

| # | Teste | Resultado observado |
| --- | --- | --- |
| 1 | 150 cliques rápidos no mesmo prêmio | Aprovado — 1 recibo, 1 diamante |
| 2 | 100 cliques rápidos em resgatar tudo | Aprovado — 3 recompensas únicas; sorteio e comando executados 1 vez; 687ms |
| 3 | Shift, tecla numérica, clone, drop, arrastar, double-click e slots forjados | Aprovado — inventário idêntico; nenhum ícone movido |
| 4 | Administração por jogador sem OP | Aprovado — XP não alterada |
| 5 | Retorno após spam de cliques | Aprovado — menu inicial do passe |
| 6 | XP offline por nome conhecido e transação repetida | Aprovado — +100 uma vez; 1100 XP persistidos |
| 7 | Reinício normal + recibos + inventário | Aprovado — 1100 XP, 3 resgates, 1 diamante; nenhum novo prêmio após repetir clique |
| 8 | Inventário cheio + 30 cliques | Aprovado — 1 reserva READY, nenhum diamante entregue |
| 9 | Pendência preservada após reinício e entregue uma vez | Aprovado — 2 diamantes após liberar inventário |
| 10 | Interrupção controlada durante entrega | Aprovado — arquivo DELIVERING confirmado antes do SIGKILL |
| 11 | Recuperação após SIGKILL + repetição de cliques | Aprovado — REVIEW preservado; 2 diamantes antes/depois; sem reentrega automática |
| 12 | Compra mensal e renovação com ID de pedido | Aprovado — 30 dias + 30 dias; replay não concede dias extras |
| 13 | Jogador comum não concede Premium | Aprovado — assinatura inalterada |
| 14 | Premium concedido por nome offline | Aprovado — validade persistida antes do reinício |
| 15 | Premium offline preservado após reinício | Aprovado — perfil e prazo ativos sem nova concessão |
| 16 | Assinatura temporária permite resgate Premium | Aprovado — um prêmio entregue |
| 17 | Expiração automática + 50 cliques no prêmio | Aprovado — menu aberto atualizado; segundo prêmio bloqueado sem desativação manual |
| 18 | Replay após vencimento | Aprovado — pedido antigo não reativa assinatura |
| 19 | Compatibilidade true/false e mês de 30 dias | Aprovado — remoção revoga assinatura e acesso permanente |
| 20 | Editor usa rascunho sem alterar prêmio antes da confirmação | Aprovado — quantidade 2 no rascunho, 1 no arquivo |
| 21 | Bigorna e cancelamento não vazam ícones | Aprovado — zero papéis/corantes no inventário |
| 22 | 50 cliques na confirmação do editor | Aprovado — configuração salva uma vez; nenhum item concedido |
| 23 | Prévia administrativa sob cliques rápidos | Aprovado — nenhum resgate ou item |
| 24 | Conteúdo longo permanece privado | Aprovado — outro jogador não recebe o texto |
| 25 | Dois administradores editam o mesmo prêmio | Aprovado — segundo salvamento recusado; primeira edição preservada |
| 26 | Edição gráfica em lote com confirmação | Aprovado — somente níveis 10–12 alterados |

## Testes do núcleo

Todos os 29 casos passaram, sem erros ou casos ignorados. A lista de cada método executado está no [JSON da auditoria](audit-0.3.0.json). Cobrem progressão, persistência, transações concorrentes, período extra, Premium temporário, renovação, migração, revogação, deduplicação de pedidos, overflow e falhas de disco.

## Queda durante entrega

O controlador matou o PID exato do servidor isolado após confirmar `DELIVERING`. No reinício, o registro passou a `REVIEW`. O inventário permaneceu com dois diamantes antes/depois dos novos cliques, sem entrega automática adicional. Isso evita duplicação, mas uma entrega parcialmente executada pode precisar de compensação manual. Não há promessa de transação atômica entre o passe e efeitos de outros mods.

## Não executado nesta rodada

- Entrega real de Pokémon com Cobblemon 1.8.3
- Provedor externo LuckPerms
- Revisão visual no cliente oficial e resource packs

## Reproduzir

Consulte [VALIDACAO.md](VALIDACAO.md). Para repetir, use mundos novos e execute os três modos do controlador; o modo padrão inclui SIGKILL controlado. Não use o servidor de produção.
