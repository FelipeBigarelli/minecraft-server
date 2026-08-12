# 🔁 Rodadas — protocolo de trabalho entre Felipe, ChatGPT e Claude Code

Esta pasta é o **canal de comunicação assíncrono** de três participantes que
nunca estão na mesma sessão:

| Quem | Onde vive | O que faz |
|---|---|---|
| **Felipe** | dono do servidor | decide, testa em jogo, aprova |
| **ChatGPT** | navegador | pesquisa plugins, revisa arquitetura, propõe |
| **Claude Code** | terminal, dentro do repositório | audita, implementa, testa, commita |

O ChatGPT **não tem acesso ao terminal nem ao runtime**. Ele lê o repositório
pelo GitHub. O Claude Code **não tem acesso ao navegador nem ao chat do
ChatGPT**. O Felipe é a ponte.

Por isso tudo que atravessa esse limite precisa estar **escrito no repositório**.

---

## 📐 O que é uma rodada

Uma rodada é um ciclo fechado:

```text
1. Claude Code implementa e commita
        ↓
2. Claude Code escreve rodadas/RODADA-NN-<assunto>.md
        ↓
3. Felipe manda o ChatGPT ler o repositório atualizado
        ↓
4. ChatGPT analisa e responde no MESMO arquivo (seção "Resposta do ChatGPT")
        ↓
5. Felipe traz a resposta de volta
        ↓
6. Claude Code implementa e abre a RODADA-NN+1
```

Uma rodada só fecha quando a decisão dela virou código ou virou um registro
explícito de "decidimos não fazer".

---

## 🔢 Numeração

```text
RODADA-01-estado-atual.md
RODADA-02-<assunto>.md
RODADA-03-<assunto>.md
```

Regras:

- **dois dígitos**, sempre — `01`, não `1`, para os arquivos ordenarem sozinhos;
- **um assunto por rodada**, em kebab-case no nome do arquivo;
- **nunca reescrever uma rodada fechada.** Se a decisão mudou, isso é uma rodada
  nova que referencia a anterior. O histórico é o valor desta pasta;
- rodada em aberto fica marcada com `🟡 ABERTA` no topo; fechada, `✅ FECHADA`.

---

## 🧱 Estrutura de um arquivo de rodada

Todo arquivo segue o mesmo esqueleto, para o ChatGPT sempre saber onde olhar:

```markdown
# RODADA NN — <assunto>

**Status:** 🟡 ABERTA | ✅ FECHADA
**Data:** DD/MM/AAAA
**Para:** ChatGPT
**De:** Claude Code

## 1. Contexto
## 2. O que mudou desde a rodada anterior
## 3. Estado atual verificável
## 4. Perguntas concretas para o ChatGPT
## 5. Restrições que não podem ser violadas
## 6. Resposta do ChatGPT     <- preenchido pelo ChatGPT
## 7. Decisão e encaminhamento <- preenchido pelo Claude Code
```

---

## ⚠️ Regras que valem para todos

Estas existem porque cada uma já custou tempo neste projeto:

1. **Número em documento envelhece.** Versão de plugin, build do Paper, preço de
   item — confirme no repositório ou na fonte oficial antes de agir. Se o
   documento e o estado real divergirem, **o estado real vence**.

2. **Nada de plugin "nulled".** Só Modrinth, Hangar, GitHub Releases oficiais,
   SpigotMC oficial ou site do projeto. Isso está em `PLANO-EXECUCAO.md` §0.2 e
   não é negociável.

3. **Um plugin por vez.** Instalar cinco de uma vez e o servidor não subir
   significa não saber qual foi.

4. **Compatibilidade com Paper 26.2 / Java 25 é pré-requisito**, não detalhe.
   Plugin sem suporte confirmado à 26.2 não entra.

5. **Proposta sem impacto econômico avaliado não é proposta.** Qualquer coisa
   que crie moeda precisa dizer de onde ela vem e qual é o teto.

---

## 📚 O que ler antes de opinar

Na ordem:

```text
1. rodadas/RODADA-<mais recente>.md   estado e pergunta atual
2. HANDOFF.md                          estado do projeto e armadilhas
3. ECONOMIA.md                         moeda, catálogo, Admin Shop, recompra
4. LOJA-SPAWN.md                       a Biga Market e a detecção de terreno
5. PLANO-EXECUCAO.md                   roadmap em fases
6. docs/sessoes/                        como chegamos até aqui
```
