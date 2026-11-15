# Novo passe — 0.5.0

Minecraft 1.21.1, Fabric, Java 21. Esta versão começa com armazenamento novo,
sem importar o state.json ou o config/shirokaipass.json das versões antigas,
conforme solicitado. Os arquivos antigos não são apagados nem usados.

## Instalação e arquivos

Pare o servidor, substitua somente o JAR do Shirokai Pass em mods/ e reinicie.
A pasta é criada dentro do mundo usado pelo servidor, normalmente:

```text
world/shirokaipass/
  config.json                 # aparência, textos e opções gerais
  passe.json                  # temporada, duração e curva de XP
  rewards/
    shirokai_01.json           # 100 recompensas grátis + 100 Premium
  data/
    players.json              # XP, Premium, resgates, entregas, recibos e relógio persistido
```

`/passeadmin arquivos` mostra o caminho real e os atalhos. Mundos com nomes
personalizados usam sua própria pasta; não assuma sempre o nome world.

O primeiro início cria e ativa o passe de 90 dias + 7 dias de resgate extra.
O modelo incorporado é a tabela enviada pelo usuário, também disponível em
examples/shirokai_01.json. Não é necessário criar a temporada por comando.
Para usar o fluxo manual antigo, defina autoStart=false em passe.json; nesse modo
os comandos/editor controlam as temporadas e o arquivo não aplica o catálogo/prazo.

## Tempo e nova temporada

Exemplo de passe.json:

```json
{
  "schema": 1,
  "autoStart": true,
  "id": "shirokai_01",
  "name": "Passe Shirokai — Temporada 1",
  "durationDays": 90,
  "graceDays": 7,
  "timerRevision": "1",
  "baseXp": 100,
  "incrementXp": 25
}
```

| Objetivo | Ação |
| --- | --- |
| Adicionar 7 dias ao prazo | `/passeadmin tempo adicionar 7` |
| Ficar com 30 dias a partir de agora | `/passeadmin tempo definir 30` |
| Reiniciar o prazo usando a duração configurada | `/passeadmin tempo reiniciar` |
| Aplicar mudanças nos JSONs | `/passeadmin reload` |
| Começar outro passe com XP/resgates separados | Trocar id e name no passe.json, depois reload |

No console, omita a barra inicial. Exigem shirokaipass.admin.seasons ou a permissão
administrativa global. Não é preciso OP no destinatário.

- durationDays é a duração TOTAL desde o início salvo, não os dias restantes.
- graceDays é o período extra de resgate, sem ganho de XP.
- Para reiniciar por arquivo, troque timerRevision de "1" para "2" e dê reload.
  O valor é uma identificação: só uma mudança efetiva dispara o reset.
- Reiniciar o prazo mantém XP, assinaturas e resgates. Reiniciar o servidor ou
  repetir reload com a mesma revisão NÃO renova o tempo.
- O comando adicionar (add) altera durationDays sem mudar o início; definir/reiniciar (set/reset) mudam a revisão
  e gravam a nova configuração. O arquivo permanece fácil de inspecionar.
- Outro id cria uma temporada com progresso/resgates próprios e encerra o ganho
  de XP da anterior. Se rewards/novo_id.json não existir, recebe o modelo da tabela.
  Para reutilizar suas recompensas personalizadas, copie o arquivo anterior com o novo nome.
- Assinaturas Premium mensais são globais por UUID, como nas versões anteriores:
  continuam durante sua validade entre temporadas. Não são um cargo LuckPerms.
- Alterar a curva de XP não reduz progresso existente silenciosamente: se o novo
  teto for menor que XP já salvo, o reload é rejeitado. Use outro id para começar do zero.
- Encerramento por `/passeadmin season end` é mantido em reload comum. Somente uma
  alteração explícita de prazo/revisão pode reabrir o período de XP.

## Editar recompensas

Edite rewards/<id>.json e dê reload. Ganhar XP/salvar jogadores não reescreve
esse arquivo. O editor administrativo continua funcionando e grava o catálogo
separado; aplique edições manuais com reload antes de editar a mesma recompensa pela GUI.

Os níveis são as chaves "1" a "100" dentro de free e premium. Sempre mantenha
os 100 de cada trilha; para pausar um prêmio, use enabled=false, sem remover o nível.

```json
{
  "enabled": true,
  "description": "1 Caixa Epic + 3 Diamantes",
  "setupNote": "",
  "mode": "fixed",
  "choices": [
    {"type": "crate", "value": "caixa_epica", "count": 1, "weight": 1},
    {"type": "item", "value": "minecraft:diamond", "count": 3, "weight": 1}
  ]
}
```

Este objeto é um exemplo de UM nível; o arquivo completo inclui schema, free e
premium. mode=fixed entrega todas as escolhas; random sorteia UMA escolha por peso.
Não mude uma recompensa composta para random se deseja manter todos seus prêmios.

| type | value | count |
| --- | --- | --- |
| item | ID Minecraft/Cobblemon, com componentes opcionais | quantidade, 1..2304 |
| crate | ID de caixa existente no Shirokai Crates 0.2.1 | quantidade de chaves, 1..2304 |
| command | comando de console, sem precisar da barra | 1 |
| pokemon | propriedades Cobblemon, começando pela espécie | 1 |

Dinheiro usa `eco deposit <valor> impactor:dollars {player}`; tokens usam
`eco deposit <valor> impactor:tokens {player}`. A sintaxe e o parser de moeda foram
conferidos no Impactor 5.3.5. Cadastre as moedas no seu Impactor ou substitua os IDs
pelos seus; não há fallback para a moeda primária. As entregas por comando mantêm
as regras de resultado/incerteza das versões anteriores; não existe transação
única envolvendo o passe e os bancos de outros mods.

IDs de caixas da tabela:

| Caixa | ID |
| --- | --- |
| Básica | inicial_rara |
| Epic | caixa_epica |
| Paradoxo | caixa_paradoxo |
| Ultra Beast | caixa_ultra_beasts |
| Mítica | caixa_mitica |
| Lendária | caixa_lendaria |
| Shiny | shiny |

Esses IDs precisam existir no catálogo do seu Crates. A concessão NÃO usa o
comando givekey: em 0.2.1 ele exige jogador administrador e quantidade obrigatória.
O passe reutiliza a fábrica nativa de chaves assinadas, sem OP temporário, copiar
segredo ou fabricar NBT. Falta de caixa/mod/compatibilidade deixa o prêmio pendente
antes de qualquer entrega do conjunto. A versão suportada é fixada em 0.2.1.

### Seis níveis Premium pendentes

A tabela inteira foi convertida, sem substituir prêmios não especificados.
Estão desativados os níveis Premium 25, 50, 75, 85, 95 e 100:

- Falta definir espécies/propriedades dos Pokémon shiny aleatórios e do lendário
  shiny perfeito. Nenhuma espécie foi inventada para substituir o prêmio.
- Faltam os IDs dos grupos VIP Médio e VIP Top. Para LuckPerms, adicione seu comando
  administrativo de concessão temporária com o grupo e prazo corretos.

A descrição original e setupNote indicam o que falta em cada nível. Prêmios
conhecidos de um nível composto também aguardam: não há entrega parcial que
consuma o direito antes de configurar o restante. Após completar choices, valide
as espécies/grupos no seu servidor e ative enabled=true.

## Editar/apagar os jogadores

players.json fica separado das recompensas. O mapa players usa chaves
`id_da_temporada/UUID` para XP, premium permanente e claimed. Há também nomes,
assinaturas temporárias, entregas e recibos de transações no mesmo arquivo.
Não é um arquivo individual por jogador.

**Pare o servidor antes de editar/apagar data.** O estado em memória não é
recarregado por `/passeadmin reload`; esse comando é para configurações/recompensas.
Para zerar TODOS os dados, remova a pasta data/ com o servidor parado e reinicie.
As recompensas e passe.json ficam intactos. Isso também remove Premium, histórico,
recibos e resgates: jogadores poderão ganhar os prêmios outra vez e o relógio
inicializará do zero. Para apenas começar outra temporada, prefira mudar o id.

Durante uma gravação pode existir data/commit.json e arquivos .pending. São
journals de recuperação, não configurações. Não edite/remova um deles isoladamente.
Uma falha interrompida é reexecutada integralmente antes de carregar o passe;
um arquivo alterado manualmente durante essa transação bloqueia a recuperação
sem sobrescrever seu conteúdo. Corrupção não vira estado vazio automaticamente.

## Validação e limites

Testes JUnit cobrem separação, edição de catálogo durante XP, revisão de tempo,
reinício, reset intencional, commit interrompido e preservação de edição externa.
Auditorias Fabric exercitam comandos, GUI, reinício e falhas de armazenamento.
O teste opcional crates usa os mods reais e verifica chaves pela assinatura nativa,
sem promover o jogador, sem duplicação e sem prêmios parciais em caixa ausente.

Os IDs de caixas/moedas e cargos do seu servidor precisam corresponder ao JSON.
Os testes não equivalem a instalar/configurar essas caixas ou grupos no seu servidor.
