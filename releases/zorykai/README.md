# Zorykai Pass — variante visual 0.5.0

Base: `istwi/Shirokai-Pass`, branch `main`, commit
`f6c36c9fff9df3d11c7e217565557f9896fde071`.
Na consulta de todas as branches remotas, somente `main` estava disponível.
O JAR original foi conferido pelo checksum publicado no próprio repositório.

Alterações realizadas diretamente no JAR: nomes visuais Shirokai → Zorykai,
preservando maiúsculas/minúsculas, e cabeçalhos `✦ ZORYKAI` brancos em negrito.
O texto seguinte conserva a cor original; avisos sem cor definida usam cinza.

Nenhum código-fonte foi editado. Permanecem idênticos: pacotes, classes,
instruções JVM, campos, métodos, IDs, namespaces, comandos, permissões,
placeholders, dados de recompensas e biblioteca de permissões embutida.
O nome técnico `shirokai-pass-0.5.0.jar` foi mantido deliberadamente.
`visual-changes.json` contém a lista exata de textos alterados e os hashes.

Validação: 62 testes da base aprovados e 15 verificações do menu do JAR editado,
incluindo resgates repetidos, paginação, cliques hostis, componentes e reinício.
Também foram conferidos o novo título e os códigos de cor/negrito recebidos
pelo cliente de protocolo. Não houve revisão visual no cliente oficial.

Para instalar, pare o servidor, faça backup e substitua o JAR antigo por este.
Mantenha somente uma versão do passe. Não renomeie os caminhos de configuração,
os comandos ou os dados existentes. Configurações e nomes de temporada já
salvos continuam prevalecendo; nomes personalizados antigos devem ser ajustados
somente nos respectivos campos visuais, preservando IDs e configurações.

Confira a integridade com `sha256sum -c SHA256SUMS.txt` nesta pasta.
