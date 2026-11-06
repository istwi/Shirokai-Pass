# Validação e limites

**Versão 0.2.0:** implementação ampliada; testes funcionais novos ficam para a etapa final no servidor. Os resultados abaixo identificados como 0.1.0 são históricos e não validam os novos menus. A compilação do código e dos testes é verificada separadamente.


Os testes usam um mundo descartável em `run/`, Fabric Loader 0.16.10, Fabric API 0.116.1+1.21.1 e JDK 21. Não dependem de NPC, EasyNPC, Shirokai Trainers ou Cobblemon. `run/` e os artefatos são ignorados pelo Git.

## Resultado histórico da versão 0.1.0

- Build e remapeamento do mod concluídos; **18 testes do núcleo passaram**, com zero falhas, erros ou testes ignorados.
- **13 verificações funcionais** de menus/notificações/resgates passaram.
- **8 verificações adicionais** passaram: reinício real, recibo por RCON, página do nível 25, recompensa Premium composta por item e comando, sorteio ponderado, ausência de Cobblemon, inventário cheio e recuperação da fila.
- **10 verificações do harness de outro mod** passaram, com o marcador final de sucesso.
- Entrega real de Pokémon e provedor externo de permissões permanecem não executados, conforme os limites abaixo.

Os detalhes de resultados do núcleo e smoke estão em `docs/test-results-v0.1.0.json`.

## Reproduzir o núcleo

```
./gradlew test build
```

O núcleo cobre curva de XP e limite 100, valores inválidos/overflow, persistência, transações repetidas e concorrentes, isolamento de temporadas, eventos, bypass, set/remove/reset, resgates grátis/Premium, redução de XP sem liberar novo resgate, período extra, sorteio persistido, entregas ambíguas, falhas de disco e estado corrompido. Resultados em `build/test-results/test/`.

## Teste funcional de menus

`tools/smoke/smoke.cjs` usa um cliente de protocolo **sem o mod instalado no cliente**. Ele valida os pacotes de contêiner vanilla, ícones, lore, navegação e cliques, sem depender de inspeção visual. Não substitui uma revisão estética com o cliente Minecraft oficial.

Use somente um servidor local descartável em modo offline, vinculado a `127.0.0.1`, com uma temporada chamada `teste`, recompensas padrão e jogador não OP `PassTester`. Mantenha dificuldade pacífica. Prepare 400 XP para esse jogador e um evento 2x ativo, sem resgates prévios. Para repetir do zero, use outro mundo descartável, sem apagar dados de produção.

```
cd tools/smoke
npm ci
npm test
```

Ao aparecer `AWARD_NOW`, execute no console, em sequência, três concessões de 100 XP com IDs novos:

```
passeadmin xp add PassTester 100 teste/evento-1
passeadmin xp add PassTester 100 teste/evento-2
passeadmin xp add PassTester 100 teste/evento-3
```

O cliente espera total 1.000 XP e nível 6, uma notificação resumida, e executa 13 verificações funcionais. Recompensas resgatadas mudam o estado desse mundo de teste. O cliente de protocolo pode emitir um diagnóstico de decodificação de receitas do Fabric; os contêineres, itens e mensagens usados pelas verificações são decodificados e suas asserções devem passar. Falhas de asserção não devem ser ignoradas.

Após o smoke básico, habilite Premium para PassTester, faça uma concessão por RCON de 100 XP com ID `rcon/test-1`, repita-a e use `xp set` para retornar a 1.000 XP. Pare e reinicie o servidor antes do teste estendido. Com `MC_RCON_PASSWORD` configurada, execute `MC_EXTENDED=1 npm test` para verificar as oito situações adicionais. Esse teste altera prêmios, sobe o nível para 25, limpa o inventário do jogador de teste e deixa uma recompensa Pokémon pendente por falta de Cobblemon. Use exclusivamente o mundo descartável descrito acima.

## Integração por outro mod

```
./gradlew integrationModJar
```

O JAR em `build/integration/` é **somente um harness de desenvolvimento, com nomes de mappings de desenvolvimento**. Não copie para produção. Para o teste local, copie-o para `run/mods/`, tenha uma temporada ativa e evento 2x persistido, inicie o servidor de desenvolvimento com a propriedade JVM `-Dshirokai.integration=true` e execute `./gradlew runServer`. A propriedade também pode ser passada por `JAVA_TOOL_OPTIONS` durante esse teste.

Esse segundo mod Fabric executa os comandos com fonte autorizada e não autorizada, testa ID com namespace/barra, deduplicação, permissão de bypass e a API a partir de uma thread externa. Exija o marcador `INTEGRATION_HARNESS_SUCCESS` no log, além de cada `HARNESS PASS`. O harness fica inativo sem a propriedade e não entra no JAR principal.

## RCON e reinício

`tools/smoke/rcon.py` usa somente a biblioteca padrão Python. O servidor descartável deve habilitar RCON, com senha própria, e ficar vinculado a loopback. Informe a senha pelo ambiente `MC_RCON_PASSWORD`; nunca por chat. Exemplo de consulta:

```
python tools/smoke/rcon.py passeadmin xp get PassTester
```

Pare pelo console com `stop`, reinicie usando o mesmo mundo e consulte novamente o total. Verifique também a quantidade de resgates, o Premium e os recibos de transação. O teste deve preservar 1.000 XP, seis recompensas grátis resgatadas e a transação previamente registrada. Tentar o mesmo recibo não aumenta XP.

## Matriz do TXT

| Requisito | Evidência |
| --- | --- |
| 1. Console concede XP | Comandos executados em servidor dedicado; total e recibos conferidos |
| 2. Comando de outro mod | Harness Fabric separado com fonte autorizada |
| 3–5. Sem NPCs/Trainers/EasyNPC | Nenhum desses mods instalado durante os testes |
| 6. Destinatário sem OP | Cliente PassTester recebe XP por console |
| 7. Jogador comum sem administração | Comando negado no cliente; fonte nível 0 negada no harness |
| 8. Jogador offline | UUID offline recebe XP; nome conhecido reutilizável após logout |
| 9–10. Multiplicador externo / set | Console e núcleo testam 2x e set sem bônus |
| 11–12. Home e perfil | Pacotes e componentes da cabeça conferidos pelo smoke |
| 13–15. Recompensas, 15 páginas e retorno | Smoke navega e valida títulos/slots da última página e seta |
| 16–17. Atualização de XP e nível | Lore muda enquanto o mesmo menu fica aberto |
| 18–19. Notificação discreta e agrupada | Actionbar única com resumo de nível para três concessões |
| 20. Deduplicação | Testes de concorrência, reinício, outra temporada e comandos com ID |
| 21. Reinício | Núcleo reabre arquivo; servidor dedicado reutiliza o mesmo mundo |
| 22. Cliente sem instalação | Cliente de protocolo vanilla entra e usa contêiner genérico |

## Ainda exige validação no servidor de destino

- Entrega real de Pokémon com **Cobblemon 1.8.3** instalado. O adaptador implementado executa `pokegive`, mas essa versão do mod não foi instalada no teste local. A tentativa de obter metadados Maven retornou HTTP 403; isso não valida nem invalida a compatibilidade.
- Revisão visual no Minecraft oficial e com os resource packs usados pelo servidor.
- Regras concedidas por um provedor externo de permissões: foi validado o fallback nativo por nível da fonte, não uma instalação real de LuckPerms/Fabric Permissions API. A API agora está embutida no JAR; a integração com um provedor real ainda precisa ser executada.
- Falhas no meio de efeitos externos não são uma transação distribuída. O estado `REVIEW` evita repetição automática; a equipe precisa conferir e compensar manualmente quando necessário.
- O editor gráfico, campos de bigorna/chat, edição em lote e conflitos entre administradores da versão 0.2.0 precisam passar pelo roteiro final em `docs/TESTE-FINAL.md`.
- A versão 0.2.0 aceita componentes nativos de itens e cópia do item da mão; é necessário validar visualmente e conferir os dados no roteiro final.
- Snapshots JSON síncronos priorizam integridade e simplicidade. Não houve benchmark de alta concorrência/população; monitore latência antes de ampliar o uso.

A compilação bem-sucedida e os testes locais não significam publicação, upload para GitHub ou instalação em um servidor de produção.
