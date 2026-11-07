# Compatibilidade de permissões — Shirokai Pass 0.3.2

## Causa raiz

O Shirokai Pass 0.3.1 empacotava `me.lucko:fabric-permissions-api:0.3.3`. O bytecode de `Util.commandSourceFromEntity` dessa biblioteca chama `ServerPlayerEntity.method_64396()`, inexistente no Minecraft 1.21.1. O POM da API 0.3.3 aponta para Minecraft 1.21.3. Não era uma falha do LuckPerms nem dos dados do jogador.

A API 0.3.2 também não serve: aponta para 1.21.2 e usa `Entity.method_5671(ServerWorld)`. A API **0.3.1** usa `Entity.method_5671()` sem argumentos, existente no 1.21.1. Embora publicada contra 1.20.4, essa assinatura permanece compatível com o alvo real testado.

O LuckPerms Fabric **5.4.140** oficial inclui a API **0.3.1**, byte a byte igual à do Maven Central (SHA-256 `1894f9db7ea6d70f95f6b088b92a8037e87df376e8774ee2b048ddcc0f967226`). O Fabric resolve uma versão global da biblioteca: a 0.3.3 trazida pelo Shirokai substituía a API compatível também nas chamadas de outros mods. Os testes anteriores cobriam permissões por command source, mas não esse overload de Entity com o LuckPerms real.

## Correção

- `build.gradle`: fixa estritamente a API 0.3.1 em compilação e inclusão, sem dependências transitivas. Minecraft 1.21.1, Fabric e Java 21 permanecem.
- `fabric.mod.json`: exige a API 0.3.1. Uma instalação que force uma API incompatível é recusada pelo loader antes da entrada de jogadores.
- `verifyBundledPermissions`, executado por `check/build`, exige uma única API 0.3.1 aninhada no JAR, sem classes sombreadas ou referência a `method_64396`.
- Tanto Shirokai quanto LuckPerms contêm sua cópia idêntica aninhada; o Fabric carrega **um único mod container** da API, confirmado no teste. Nenhuma segunda biblioteca de permissões foi acrescentada.
- As chamadas reais a `Permissions.check` continuam ativas. Falhas de provedor, incluindo `LinkageError`, são registradas e negam acesso, sem converter erro em privilégio OP. Essa proteção adicional não substitui a correção da dependência.
- Persistência atômica, proteção do login, recuperação, premium e menus foram preservados. GTS, CobbleHunts e outros mods não foram alterados.

## Testes

Build e JUnit: Linux **51 passaram**; Windows **50 passaram**, com **1 caso POSIX não aplicável** ignorado e nenhuma falha. [Execução nativa](https://github.com/istwi/Shirokai-Pass/actions/runs/42320350652).

Servidor Fabric Minecraft 1.21.1 real, Java 21, clientes de protocolo locais e LuckPerms **5.4.140 oficial**: 11 verificações incluindo inicialização, entrada/reconexão, overloads Source/Entity/UUID/GameProfile, concessão/revogação, negação explícita com OP, menu e persistência das permissões após reinício. Sem provedor externo: 4 verificações de permissões padrão, OP e reconexão. [Regressão automatizada no GitHub](https://github.com/istwi/Shirokai-Pass/actions/runs/42320240117).

No total, **48 verificações funcionais passaram**: permissões com LuckPerms (11), permissões sem provedor (4), armazenamento/login (7), dupe (11), premium (8) e administração (7). Os resultados adicionais de armazenamento, cliques rápidos/dupe, desconexão, queda forçada, premium e administração estão detalhados em [audit-0.3.2.json](audit-0.3.2.json). Os testes não carregam o modpack completo; não substituem uma validação visual ou das recompensas Cobblemon reais.

## Reprodução

```sh
./gradlew test build integrationModJar
npm ci --prefix tools/smoke
python3 tools/smoke/prepare-luckperms.py
MC_LUCKPERMS_JAR="$PWD/run-audit/fixture-libs/LuckPerms-Fabric-5.4.140.jar" python3 tools/smoke/audit.py permissions
python3 tools/smoke/audit.py fallback
python3 tools/smoke/audit.py storage
python3 tools/smoke/audit.py dupe
python3 tools/smoke/audit.py premium
python3 tools/smoke/audit.py admin
```

O download usa o CDN oficial do projeto e valida SHA-512 fixado. Os comandos usam mundos isolados, sem alterar dados de produção; execute sequencialmente. A workflow `permissions-regression.yml` reproduz os casos com/sem LuckPerms.

Para atualizar, pare o servidor, substitua apenas o JAR antigo do Shirokai Pass pelo `shirokai-pass-0.3.2.jar` e reinicie. Preserve o mundo e seus arquivos de progresso. Não mantenha duas versões do Shirokai Pass em `mods/`.
