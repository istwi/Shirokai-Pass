# GUI minimalista — 0.4.0

O passe agora abre diretamente em um contêiner vanilla `GENERIC_9X4` (36 slots), mantendo o título `✦ SHIROKAI PASS`.

| Linha | Slots | Conteúdo |
| --- | --- | --- |
| 1 | 0–8 | Recompensas grátis dos nove níveis |
| 2 | 9–17 | Premium dos mesmos níveis |
| 3 | 18–26 | Indicador do nível correspondente |
| 4 | 27 / 31 / 35 | Anterior / informações / próxima |

Os demais slots da navegação são vazios. São 12 páginas; a última só tem o nível 100, sem preencher os espaços restantes. As setas permanecem nos lugares fixos e param nas extremidades. Sem temporada, ficam apenas navegação e livro informando que o início é aguardado.

## Uso e estados

`/passe` e `/passe recompensas` abrem no nível atual. Um clique esquerdo no prêmio solicita o resgate pela mesma camada persistente usada anteriormente. A página permanece aberta. O ícone é uma cópia do item configurado, com quantidade limitada à pilha exibida, componentes preservados e quantidade real no tooltip. A configuração e a entrega não são alteradas pelo limite visual. Recompensas com vários prêmios usam um item representativo; o tooltip resume até quatro escolhas, chances e quantidade restante. Comando e Pokémon têm ícones representativos quando não há um item de prêmio.

- Verde: nível alcançado sem recompensa elegível pendente para aquele jogador.
- Amarelo: ao menos um prêmio daquele nível pode ser resgatado.
- Laranja: próximo nível em progresso.
- Cinza: nível futuro bloqueado.

Um jogador sem Premium pode concluir sua trilha grátis; o prêmio Premium continua identificado como bloqueado no tooltip. Ativar Premium pode tornar o indicador amarelo novamente. O livro reúne temporada, nível, XP do nível/necessário, validade do Premium e tempo da temporada ou do resgate extra. Não há botão de resgatar tudo na tela; o comando `/passe resgatartudo` continua disponível.

A tela se atualiza a cada segundo e em concessões de XP, mudanças administrativas de XP/Premium e resgates. Isso cobre edição das recompensas, fim de temporada, mudança de temporada e vencimento do Premium com a GUI aberta. Refresh é isolado pela proteção de falhas existente e não faz gravações.

## Compatibilidade e proteção

Não houve mudança de schema, armazenamento, recibos, reserva/entrega, assinaturas ou configurações. O caminho é o mesmo `world/shirokaipass/state.json`. A GUI continua chamando `mod.claim`, com validação de nível, Premium, prazo e reserva única feita por `PassService`. Falhas de gravação bloqueiam entrega; resultados ambíguos continuam em `REVIEW`.

Todos os tipos de clique que não sejam clique esquerdo simples são ignorados e sincronizados. O handler nunca chama a movimentação vanilla: shift, arrastar, teclado numérico, clone, drop, double-click e posições fora do contêiner não movem os ícones. Prévia administrativa não resgata. A seleção do editor usa o layout horizontal; os formulários administrativos existentes são preservados. Histórico e pendências continuam disponíveis pelos comandos, em contêineres de 36 slots.

Minecraft **1.21.1**, Fabric, Java **21** e API de permissões **0.3.1** permanecem. O código da integração Cobblemon **1.8.3**, GTS e CobbleHunts não foi alterado. Não foi adicionado nenhum mod obrigatório nem biblioteca.

## Reproduzir

```sh
./gradlew test build integrationModJar
npm ci --prefix tools/smoke
python3 tools/smoke/audit.py gui
python3 tools/smoke/audit.py dupe
python3 tools/smoke/audit.py storage
python3 tools/smoke/audit.py premium
python3 tools/smoke/audit.py admin
python3 tools/smoke/audit.py fallback
python3 tools/smoke/prepare-luckperms.py
MC_LUCKPERMS_JAR="$PWD/run-audit/fixture-libs/LuckPerms-Fabric-5.4.140.jar" python3 tools/smoke/audit.py permissions
```

Os controladores usam servidores/mundos descartáveis e clientes de protocolo locais; execute sequencialmente. Não validam aparência no cliente oficial ou o modpack completo com Cobblemon. As workflows de GUI e permissões executam esses cenários no GitHub; a workflow de regressão de armazenamento verifica Linux e Windows. O fluxo de release existente compila, testa e publica JAR, sources e checksums.

## Resultados executados

**63 verificações funcionais passaram:** GUI (15), dupe (11), armazenamento/login (7), premium (8), editor administrativo (7), permissões sem provedor (4) e LuckPerms Fabric 5.4.140 real (11). Foram testados inicialização, entrada, reconexão, reinício, nível 100, quatro cores, itens com componentes, paginação, resgates diretos, ativação/vencimento do Premium com a tela aberta, pacotes forjados, inventário cheio, interrupção durante entrega e recuperação sem duplicação.

**JUnit:** Linux 51 aprovados; Windows 50 aprovados, 1 caso POSIX não aplicável ignorado, nenhuma falha. O JAR foi inspecionado para garantir Minecraft 1.21.1, exatamente uma API de permissões 0.3.1 aninhada, ausência do método incompatível e ausência dos harnesses de teste.

[Resultados de cada cenário](audit-0.4.0.json) · [GUI no GitHub](https://github.com/istwi/Shirokai-Pass/actions/runs/42323407631) · [LuckPerms no GitHub](https://github.com/istwi/Shirokai-Pass/actions/runs/42323412666) · [Linux/Windows](https://github.com/istwi/Shirokai-Pass/actions/runs/42323415859)
