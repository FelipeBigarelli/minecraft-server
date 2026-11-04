# Tradução pt-BR do WorldGuard

O WorldGuard não tem arquivo de idioma: as mensagens são literais de string
dentro dos `.class`. Este patch troca essas strings direto no constant pool
do bytecode e grava um jar novo.

**Toda atualização do WorldGuard apaga a tradução.** Depois de baixar um jar
novo, rode o patch de novo:

```bash
python3 -I patch_wg.py <jar-original> <jar-traduzido>
```

Se o jar novo tiver mexido no texto de alguma mensagem, o script avisa quais
frases do dicionário ele não encontrou — basta corrigir a chave em
`trad_a/b/c.py` e rodar outra vez.

## Por que só o constant pool

Nome de classe, descritor de método e nome de campo também são `CONSTANT_Utf8`.
O patch troca apenas os Utf8 apontados por um `CONSTANT_String`, que são os
literais que o código imprime — então nada estrutural é tocado. Referências
dentro do class file são por índice do pool, nunca por offset em bytes, então
mudar o tamanho de uma string é seguro desde que o arquivo seja reescrito
inteiro em ordem.

## Travas antes de gravar

O script se recusa a produzir um jar se:

- a contagem de `\x01`/`\x02` mudar entre original e tradução. Esses são os
  marcadores de argumento do `StringConcatFactory` (o `"a" + x` do Java 9+
  vira uma receita com `\u0001` no lugar de cada variável). Perder um
  significa a mensagem sair com os valores no lugar errado;
- os placeholders `%s`, `%id%`, `%what%`, `%page%` não baterem;
- alguma classe ficar ilegível depois da troca.

## O que não é traduzido, de propósito

Nome de comando, de flag e de permissão; templates como `/rg flags -w "..."`;
nomes de arquivo (`%s.report`); e mensagens de exceção interna que nunca
chegam ao jogador.
