# Recompensas Pokémon — 0.4.1

O comando antigo `pokegive <jogador> <propriedades>` era executado pelo console,
mas `pokegive` exige um jogador como executor. O Cobblemon 1.8.3 reproduziu o erro
`A player is required to run this command here` no teste desta correção.

A entrega agora usa `PokemonProperties` e `PlayerPartyStore.add`, confirmando seu
retorno. O Cobblemon encaminha ao PC quando a equipe está cheia. Espécies inválidas
são rejeitadas antes de publicar a recompensa. Falhas de preparação mantêm a
reserva em `READY`; falhas após iniciar efeitos externos permanecem em `REVIEW`.
O modelo nos menus é o `PokemonItem` do Cobblemon; aparência no cliente oficial
não foi verificada.

Ao iniciar, somente entregas em `REVIEW` com um único prêmio Pokémon e a mensagem
exata do erro antigo voltam a `READY`. O comando antigo falhava antes de criar o
Pokémon. Entregas mistas ou interrompidas não são liberadas automaticamente.
O resgate original é mantido, inclusive seu ID, sem apagar o histórico.

## Validação executada

- `./gradlew test build remapIntegrationModJar --no-daemon`: 54 testes Java,
  nenhuma falha ou caso ignorado; inspeção da API de permissões embutida aprovada.
- Servidor **Minecraft 1.21.1**, **Fabric Loader 0.17.2**, **Fabric API
  0.116.6+1.21.1**, **Cobblemon 1.8.3+1.21.1** e JAR Shirokai Pass **0.4.1**
  em namespace de produção: cenário `pokemon` aprovado. O harness reproduz o erro
  antigo, cria o item `cobblemon:pokemon_model`, rejeita espécie inexistente,
  resgata um Pokémon, verifica aumento real do armazenamento e recibo `DONE`,
  repete o resgate sem duplicar e confirma overflow de equipe cheia ao PC.
- Regressão do menu executada durante a implementação: 15 verificações aprovadas.

O JAR Cobblemon usado veio do Maven oficial indicado pelo projeto:
`https://maven.impactdev.net/repository/development/com/cobblemon/fabric/1.8.3+1.21.1/fabric-1.8.3+1.21.1.jar`.
SHA-512 conferido com o arquivo publicado pelo repositório:
`07434ae412db64d45b7140d09aa2b6682a49a3563a0b0190b410a9060e03244d01a44037a0b5e36c478b4a6fb1c17853320f7992f3eb5661e2c602ce66c79c9e`.

## Reproduzir o teste real

Prepare um servidor Fabric descartável em `run-audit/`, com o launcher,
Minecraft 1.21.1, as versões acima e `build/libs/shirokai-pass-0.4.1.jar`
em `run-audit/mods/`. Não use um mundo de produção: o controlador cria seu
próprio mundo, propriedades, senha RCON e aceita a EULA para esse teste local.
Compile com `./gradlew build remapIntegrationModJar` e instale o cliente com
`npm ci --prefix tools/smoke`.

```sh
export NODE_PATH="$PWD/tools/smoke/node_modules"
export MC_HARNESS_JAR="$PWD/build/integration/shirokai-pass-test-harness-intermediary.jar"
export MC_SERVER_COMMAND_JSON='["java","-Dshirokai.audit=true","-jar","fabric-server-launch.jar","nogui"]'
python3 tools/smoke/audit.py pokemon
```

`MC_SERVER_COMMAND_JSON` é uma lista de argumentos, executada em `run-audit/`.
Pode apontar para outro launcher Fabric instalado. O harness é exclusivo dos
servidores descartáveis e não faz parte do JAR distribuível. Não execute dois
controladores simultaneamente (portas locais 25585 e 25595).

## Atualizar o servidor

Faça backup do mundo. Pare o servidor, substitua o JAR antigo pelo
`shirokai-pass-0.4.1.jar` e reinicie, mantendo somente uma versão do passe em
`mods/`. As entregas antigas elegíveis voltam à fila; login ou uma tentativa em
`/passe pendentes` entrega o Pokémon. Outros casos `REVIEW` continuam exigindo
conferência. `delivery acknowledge` encerra uma pendência; não entrega Pokémon.
