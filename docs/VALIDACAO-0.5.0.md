# Validação — 0.5.0

Base:66ed2d2 (0.4.2). Java21/Minecraft1.21.1/Fabric preservados.

- 62 testes JUnit aprovados, zero falhas/erros/skips.
- Setup: 6 verificações, dois boots: passe automático,
  catálogo separado, edição durante XP, reload, comandos de tempo em português,
  troca de id e relógio/progresso preservados no reinício.
- GUI: 15 verificações, dois boots: recompensas/componentes,
  resgates, cliques hostis, Premium e persistência. O controlador aguarda todos os
  ícones configurados, incluindo Premium, antes de avaliar o estado atualizado.
- Armazenamento: 7 verificações, dois boots: falhas de
  escrita, estado indisponível com login permitido, reparo e corrupção/reinício.
- Pack real: 1 cenário agregado com Cobblemon1.8.3,
  Impactor5.3.5, LuckPerms5.4.140, PAPI2.4.2 e Shirokai Crates0.2.1. Verificadas
  três chaves aceitas pela assinatura nativa, jogador sem OP, claim duplicado sem
  novas chaves e preflight de caixa desconhecida sem entregar o outro prêmio.
- Bundled Fabric Permissions API0.3.1 verificada, exatamente uma cópia compatível.
- JAR inspecionado: sem harness de testes; catálogo incorporado igual ao exemplo.

JUnit:build/test-results/test. Resumo legível por máquina:
build/validation-0.5.0.json. Logs e resumos de cada auditoria:

- setup: run-audit/results-world-20261114-222611/summary.json
- gui: run-audit/results-world-20261114-223102/summary.json
- storage: run-audit/results-world-20261114-221112/summary.json
- crates: run-audit/results-world-20261114-222237/summary.json

Loom1.9.2 não remapeava os metadados Kotlin necessários ao Cobblemon no runner de
desenvolvimento. Ferramentas atualizadas para Loom1.10.5/Kotlin2.2.20/Gradle8.12.1;
o cache derivado antigo do Cobblemon foi substituído por remapeamento novo.
Nenhum JAR oficial/checksum/TLS foi alterado para contornar a falha. O teste com
o modpack passou após essa correção.

A sintaxe de moeda do Impactor foi auditada nas anotações e no CurrencyParser do
JAR5.3.5: eco deposit <amount> [currency] [target]. O comando givekey foi auditado
no código do Crates0.2.1: exige count e jogador admin. Por isso usa-se a fábrica
nativa existente, não o comando de console ou OP temporário. Não foi certificado
pagamento/VIP no servidor do usuário nem renderização/login de cliente oficial.
Seis níveis Premium ficam explicitamente desativados até completar a configuração.

JAR:releases/shirokai-pass-0.5.0.jar
SHA256:de29bb17af1ea9858505802e9f76ad74f7270fc45b705b098963ce9252e081d3
