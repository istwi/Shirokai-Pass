# Validação atual — 0.4.0

A nova interface de 36 slots foi validada com 63 verificações funcionais, 51 testes Java no Linux e 50 no Windows (1 caso POSIX não aplicável). [Layout, reprodução e limites](GUI-0.4.0.md) · [Resultados caso a caso](audit-0.4.0.json). As correções anteriores de permissões, login e persistência foram preservadas e retestadas.

# Histórico — 0.3.2

A compatibilidade de permissões e os testes com LuckPerms Fabric 5.4.140 estão em [PERMISSIONS-FIX-0.3.2.md](PERMISSIONS-FIX-0.3.2.md). Resultados caso a caso: [audit-0.3.2.json](audit-0.3.2.json).

# Histórico — 0.3.1

A correção de login/persistência está documentada em [LOGIN-FIX-0.3.1.md](LOGIN-FIX-0.3.1.md), com 48 testes Linux, 47 Windows + 1 caso POSIX não aplicável, e 33 verificações funcionais. Para reproduzir as falhas de login, execute `python3 tools/smoke/audit.py storage` depois de compilar `integrationModJar`.

# Validação — versão 0.3.0

O código e cada alteração desta rodada foram enviados à branch `main`. Os resultados executados, caso a caso, estão em [AUDITORIA-0.3.0.md](AUDITORIA-0.3.0.md) e no JSON correspondente. Os testes antigos foram preservados em `VALIDACAO-v0.2.0.md` e `test-results-v0.1.0.json`.

## Reproduzir

Com JDK 21, Node e Python:

```sh
./gradlew test build integrationModJar
npm ci --prefix tools/smoke
python3 tools/smoke/audit.py
python3 tools/smoke/audit.py premium
python3 tools/smoke/audit.py admin
```

Defina `NODE_PATH` para `tools/smoke/node_modules` se a resolução do Node não encontrar as dependências. Os testes usam somente loopback, nomes artificiais e mundos novos em `run-audit/`, sem alterar o mundo de produção. Senha RCON aleatória, arquivos de servidor e logs ficam ignorados pelo Git. Não execute dois controladores ao mesmo tempo: eles usam as portas 25585 e 25595.

O primeiro modo testa 150 cliques no mesmo prêmio, 100 em resgatar tudo, ações de inventário forjadas, permissões, XP offline, reinícios normais e uma queda forçada durante entrega. O controlador confirma o PID do servidor de teste em um checkpoint e envia SIGKILL somente a esse processo. A retomada deve produzir `REVIEW`, não outra entrega automática.

O modo `premium` testa compra de 30 dias, renovação, pedido repetido, concessão offline, reinício e uma assinatura de um minuto que expira com o menu aberto. O modo `admin` testa edição gráfica, bigorna, rascunho, confirmação repetida, privacidade, conflitos entre administradores e lote.

## Interpretação

- Não observar duplicação nos cenários executados não prova ausência de todos os bugs possíveis.
- Itens/comandos de outros mods não compartilham uma transação distribuída com o JSON do passe. Na queda durante entrega, `REVIEW` impede repetição automática; conferir e compensar manualmente quando necessário.
- Os clientes dos testes são clientes de protocolo sem Shirokai Pass instalado. Isso valida contêineres e cliques vanilla; não substitui uma revisão estética no Minecraft oficial.
- A entrega real de Pokémon no **Cobblemon 1.8.3** e a revisão visual com resource packs continuam não executadas. A integração real com **LuckPerms Fabric 5.4.140** foi validada na 0.3.2, além do funcionamento sem provedor externo.
- Os testes locais usam autenticação offline somente no servidor isolado. Servidor de produção deve manter autenticação online.

O [roteiro manual](TESTE-FINAL.md) complementa a automação. A release é publicada como pré-release enquanto essas integrações externas não forem validadas.
