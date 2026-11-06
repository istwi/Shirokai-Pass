# SHIROKAI PASS — Atualização dos requisitos

Mantenha integralmente todas as funcionalidades já definidas no projeto Shirokai Pass, incluindo:

- Minecraft 1.21.1, Fabric e Java 21.
- Cobblemon 1.8.3.
- 100 níveis por temporada.
- XP progressiva.
- Recompensas gratuitas e Premium.
- Recompensas fixas e aleatórias.
- Sorteios com porcentagens e pesos configuráveis.
- Recompensas por comandos, itens e Pokémon.
- Resgate individual e resgatar tudo.
- Temporadas com histórico permanente.
- Período extra de resgate.
- Eventos de XP em dobro.
- Editor administrativo e edição em lote.
- Prévia administrativa.
- Anúncios configuráveis.
- Placeholders.
- Notificações discretas.
- Persistência e proteção contra duplicação.

Os requisitos abaixo complementam e atualizam a especificação anterior. Em caso de conflito, estes requisitos têm prioridade.

## 1. Sistema universal de concessão de XP

O Shirokai Pass NÃO deve depender de NPCs, batalhas Pokémon, ginásios ou qualquer mod específico para conceder XP.

O sistema deve ser genérico.

Qualquer sistema autorizado do servidor poderá conceder XP utilizando comandos.

### Comando principal obrigatório

`/passeadmin xp add <player> <amount>`

Exemplo:

`/passeadmin xp add Jogador 100`

Resultado:

O jogador recebe 100 XP na temporada ativa do Shirokai Pass.

Esse comando deve funcionar independentemente da origem da recompensa.

### Exemplos de integração

Um NPC pode executar:

`/passeadmin xp add Jogador 100`

Um sistema de missões pode executar:

`/passeadmin xp add Jogador 250`

Um mod de ginásios pode executar:

`/passeadmin xp add Jogador 1000`

Um evento personalizado pode executar:

`/passeadmin xp add Jogador 500`

Um administrador pode executar:

`/passeadmin xp add Jogador 10000`

Todos devem utilizar exatamente o mesmo sistema de XP.

### Requisitos técnicos

- Registrar o comando usando Brigadier.
- Permitir execução pelo console.
- Permitir execução por sistemas de comandos de outros mods, quando utilizarem uma fonte de comando autorizada.
- Permitir execução via RCON autorizado.
- Não exigir que o jogador destinatário tenha OP.
- Não exigir que o jogador destinatário tenha permissões administrativas.
- Exigir autorização da fonte que executa o comando.
- Suportar nomes de jogadores e resolução segura por UUID.
- Permitir XP para jogadores offline.
- Atualizar o progresso quando o jogador estiver online.
- Persistir alterações imediatamente de maneira segura.
- Respeitar a temporada ativa.
- Respeitar o nível máximo.
- Respeitar multiplicadores de XP quando aplicáveis.
- Validar quantidades negativas, zero e overflow.
- Não permitir que jogadores comuns concedam XP livremente.

### Placeholders de outros mods

O Shirokai Pass não deve exigir uma sintaxe específica de placeholder.

Exemplo:

Um mod externo pode utilizar `%player%` para representar o jogador.

Nesse caso, a configuração do mod externo poderá executar:

`/passeadmin xp add %player% 100`

O próprio mod externo deverá substituir `%player%` pelo nome real antes de executar o comando.

O Shirokai Pass deverá receber o comando final e processá-lo normalmente.

Não implementar dependência obrigatória de um sistema de placeholders para o funcionamento do comando.

### Comandos adicionais

`/passeadmin xp set <player> <amount>`

Define a XP total do jogador na temporada atual.

`/passeadmin xp remove <player> <amount>`

Remove XP.

`/passeadmin xp reset <player>`

Redefine a XP.

`/passeadmin xp get <player>`

Consulta a XP.

`/passeadmin level set <player> <level>`

Define o nível ajustando a XP ao requisito acumulado correspondente.

`/passeadmin level get <player>`

Consulta o nível.

Os comandos `set`, `remove` e `reset` não devem receber multiplicadores de XP.

### Integração opcional por API

Além dos comandos, disponibilizar uma API Java simples para outros mods Fabric concederem XP diretamente.

Exemplo conceitual:

`PassApi.addXp(playerUuid, amount)`

A API deve:

- Validar os parâmetros.
- Utilizar o mesmo sistema de progressão.
- Aplicar as mesmas regras.
- Respeitar temporadas.
- Persistir corretamente.
- Permitir identificação opcional da operação.
- Não duplicar lógica entre API e comandos.

A API não deve ser necessária para utilizar o mod.

### Proteção contra concessões duplicadas

Permitir uma versão opcional do comando com identificador único de transação.

O identificador deve permitir que sistemas externos evitem conceder XP duas vezes pelo mesmo evento.

Não exigir identificador para o comando simples.

O comando simples deve continuar funcionando normalmente.

O Shirokai Pass não precisa identificar a origem da XP nem verificar batalhas. O sistema externo é responsável por decidir quando concedê-la.

---

## 2. Novo menu inicial do passe

Alterar o comportamento de `/passe`.

O comando deve abrir uma página inicial de informações, em vez de abrir diretamente as recompensas.

Criar uma GUI server-side de 54 slots.

### Título

`✦ SHIROKAI PASS`

### Identidade visual

- Azul e ciano como cores principais.
- Dourado para Premium.
- Verde para recompensas disponíveis.
- Cinza para informações secundárias.
- Vidros decorativos discretos.
- Textos curtos e organizados.
- Sem descrições redundantes.
- Sem excesso de letras maiúsculas.

### Informações exibidas

**Perfil do jogador**

Usar a cabeça do jogador.

Exibir:

- Nome do jogador.
- Nível atual.
- XP total da temporada.
- XP atual dentro do nível.
- XP necessária para o próximo nível.
- Porcentagem de progresso.
- Status Premium.
- Nome da temporada.
- Tempo restante da temporada.

### Botões principais

**✦ Recompensas**

Abre as recompensas do passe.

**✦ Perfil**

Abre o perfil detalhado.

**✦ Premium**

Mostra o status Premium e as informações configuráveis para aquisição.

**✦ Histórico**

Mostra as temporadas anteriores.

**✦ Resgatar tudo**

Resgata todas as recompensas elegíveis da temporada.

**✦ Informações**

Mostra as regras da temporada, XP e funcionamento do passe.

**✦ Entregas pendentes**

Mostra recompensas aguardando entrega, caso existam.

### Requisitos

- Interface limpa.
- Layout centralizado e equilibrado.
- Botões com ícones configuráveis.
- Atualização das informações quando o progresso mudar.
- Não recalcular informações a cada tick.
- Não abrir interfaces desnecessárias.
- Não exigir instalação do mod no cliente.

---

## 3. Menu de recompensas

Criar o comando:

`/passe recompensas`

Esse comando abre diretamente o menu das recompensas na página correspondente ao nível atual.

Manter a GUI de 54 slots e as 15 páginas.

Cada página exibe sete níveis.

### Organização

- Recompensas gratuitas na parte superior.
- Recompensas Premium na parte inferior.
- Identificação dos níveis.
- Indicadores de recompensas bloqueadas, disponíveis e resgatadas.
- Botões de navegação.
- Botão de retorno ao menu principal.

### Botão de retorno

Utilizar:

- Item: `minecraft:arrow`
- Nome: `VOLTAR AO MENU`
- Ação: retornar ao menu inicial do Shirokai Pass.

Não executar `/menu` do servidor nesse botão, pois ele deve retornar à página inicial do passe.

### Navegação

- Página anterior.
- Próxima página.
- Primeira página.
- Última página.
- Ir ao nível atual.
- Voltar ao menu inicial.

Preservar a página atual após resgatar recompensas.

---

## 4. Perfil detalhado

Comando:

`/passe perfil`

Exibir:

- Cabeça do jogador.
- Nome.
- Nível atual.
- XP acumulada.
- XP necessária para o próximo nível.
- Progresso percentual.
- Premium ativo ou inativo.
- Temporada atual.
- Recompensas gratuitas resgatadas.
- Recompensas Premium resgatadas.
- Recompensas disponíveis.
- Entregas pendentes.
- Tempo restante da temporada.
- Quantidade de temporadas participadas.
- Maior nível alcançado anteriormente.

Permitir retornar ao menu principal.

---

## 5. Eventos de XP em dobro

Manter os comandos:

`/passeadmin event xp start <multiplier> <duration>`

`/passeadmin event xp stop`

`/passeadmin event xp status`

`/passeadmin event xp list`

O multiplicador deve funcionar independentemente da origem da XP.

Exemplo:

Evento 2x ativo.

Um sistema externo executa:

`/passeadmin xp add Jogador 100`

Resultado:

O jogador recebe 200 XP.

O evento não deve alterar comandos `xp set`, `xp remove` ou `xp reset`.

Permitir um parâmetro opcional para administradores ignorarem o multiplicador em concessões específicas, mediante permissão apropriada.

Registrar a XP original, multiplicador e XP final.

---

## 6. Notificações de XP

Quando o jogador receber XP por qualquer comando externo, mostrar uma notificação discreta.

Exemplo:

`✦ +100 XP do passe`

Durante evento:

`✦ +200 XP do passe (2x)`

Priorizar actionbar.

Agrupar notificações recebidas em sequência para evitar spam.

Se o jogador subir vários níveis, mostrar apenas uma notificação resumida.

Exemplo:

`✦ Você alcançou o nível 25 do passe!`

Não enviar mensagens de XP para jogadores offline.

Ao entrar novamente, atualizar o progresso e permitir consultar as mudanças.

---

## 7. Permissões

Criar permissões específicas:

- `shirokaipass.use`
- `shirokaipass.admin`
- `shirokaipass.admin.xp`
- `shirokaipass.admin.premium`
- `shirokaipass.admin.rewards`
- `shirokaipass.admin.seasons`
- `shirokaipass.admin.events`
- `shirokaipass.admin.preview`
- `shirokaipass.admin.bulk`
- `shirokaipass.admin.announcements`

Jogadores comuns devem conseguir abrir `/passe`.

Jogadores comuns não devem conseguir executar `/passeadmin xp add`.

O console e integrações autorizadas devem conseguir conceder XP.

Não conceder OP temporário para executar comandos.

---

## 8. Testes adicionais obrigatórios

Além de todos os testes já definidos no projeto, validar:

1. Conceder XP pelo console.
2. Conceder XP por comando executado por outro mod.
3. Conceder XP sem NPCs instalados.
4. Conceder XP sem Shirokai Trainers.
5. Conceder XP sem EasyNPC.
6. Conceder XP a jogadores sem OP.
7. Impedir que jogadores comuns executem comandos administrativos.
8. Conceder XP a jogadores offline.
9. Aplicar XP em dobro a concessões externas.
10. Não aplicar XP em dobro ao comando `set`.
11. Abrir o menu inicial com `/passe`.
12. Exibir corretamente o perfil no menu inicial.
13. Abrir recompensas com `/passe recompensas`.
14. Navegar pelas 15 páginas.
15. Retornar ao menu inicial.
16. Atualizar o perfil após receber XP.
17. Atualizar o nível após receber XP.
18. Exibir notificações discretas.
19. Agrupar notificações recebidas em sequência.
20. Impedir duplicação quando uma transação externa utiliza identificador único.
21. Preservar o progresso após reiniciar o servidor.
22. Confirmar funcionamento em servidor Fabric dedicado sem instalação obrigatória no cliente.

## 9. Critério de implementação

O sistema de XP deve ser um componente central e independente do Shirokai Pass.

Não criar uma arquitetura baseada em NPCs.

Não exigir integração com um mod específico para conceder XP.

O comando `/passeadmin xp add <player> <amount>` deve ser suficiente para integrar o Shirokai Pass com qualquer sistema que consiga executar comandos autorizados no servidor.

**Resultado esperado:** um passe com 100 níveis, temporadas, recompensas gratuitas e Premium, interface organizada e sistema universal de XP, utilizável por comandos de qualquer integração compatível.