# Shirokai Pass

Java 21, Minecraft 1.21.1, Fabric. Leia docs/CONFIGURACAO-0.5.0.md antes de
alterar armazenamento, configuração ou recompensas. Use o checkout existente.

0.5.0 começa com dados novos por solicitação do usuário, sem importar state.json
legado. Store.directory(root) é o formato de produção: data/players.json sem
catálogo e rewards/<id>.json separados. O Store(Path) legado permanece para
compatibilidade da API/testes. Não reintroduzir snapshots permanentes duplicados
com as recompensas dentro de data. Ganhar XP não pode sobrescrever JSON editado.

SplitFiles usa commit recuperável com estado anterior e novo: validar todos os
alvos e não sobrescrever edição externa desconhecida após uma falha. Nunca
recuperar corrupção como estado vazio. Reset intencional é offline, removendo data/.
Não perder reservas, recibos ou estados DELIVERING/REVIEW ao mudar o armazenamento.

passe.json controla nome/id/duração/curva; timerRevision só reinicia o prazo uma
vez por mudança. Reload/restart com mesma revisão não renovam tempo. Alteração de
prazo não apaga XP/resgates. Novo id cria progresso de temporada separado; Premium
mensal segue global por UUID. autoStart=false preserva o fluxo de comandos manual.

Recompensas: item/command/pokemon/crate. type=crate exige Shirokai Crates0.2.1:
a fábrica de Tokens do serviço vivo gera as chaves assinadas; não copiar segredo,
fabricar assinatura nem promover jogador. Acesso privado é fixado/auditado nessa
versão, pois não há API pública de concessão e givekey só aceita jogador admin.
Ausência/versão incompatível mantém READY antes de efeitos externos.

Tabela original em examples/recompensas-original.txt; regenerar exemplos/recurso
com tools/generate_pass_example.py. IDs informados pelo usuário: inicial_rara,
caixa_epica, caixa_paradoxo, caixa_ultra_beasts, caixa_mitica, caixa_lendaria, shiny.
Não inventar grupos VIP ou espécies especiais: níveis Premium25/50/75/85/95/100
ficam pendentes/desativados até configurar suas escolhas. Comando de moeda
Impactor5.3.5: eco deposit <amount> <currency> <target>; não substituir por primária.

Build: python3 tools/gradle_cloud.py build integrationModJar.
Auditorias isoladas: NODE_PATH=$PWD/tools/smoke/node_modules python3 tools/smoke/audit.py
seguido de setup, gui ou storage (um por vez, portas locais25585/25595).
Pack nativo: python3 tools/prepare_native_pack.py; auditoria crates usa os JARs
oficiais com checksums fixados. Ferramentas Loom1.10.5/Kotlin2.2.20/Gradle8.12.1
permitem remapear metadados Kotlin do Cobblemon; não reutilizar cache derivado
inválido de Loom antigo, não alterar JAR/checksum/TLS para contornar erro.
Os controles aceitam a EULA de mundo descartável e usam clientes de protocolo;
não equivalem a login autenticado/renderização em cliente oficial.

Não publicar ambiente ou release sem solicitação. Preservar histórico remoto e
registrar os resultados reais de testes ao atualizar o JAR.
