# 📜 Sessões do Claude Code

Histórico das sessões de trabalho neste projeto. Serve para **retomar o
contexto em outra máquina** ou meses depois, sem depender da memória de
ninguém.

---

## 🚀 Retomar o trabalho em outro PC

Cole isto numa sessão nova do Claude Code, já dentro do projeto clonado:

```
Leia, nesta ordem:

1. HANDOFF.md — estado da máquina, decisões e armadilhas. É a fonte da verdade.
2. PLANO-EXECUCAO.md — o plano faseado. Estamos na Fase 1.
3. docs/sessoes/ — o transcript da última sessão, se precisar do detalhe de
   como chegamos aqui.

Trate o HANDOFF como hipótese, não como verdade: rode a auditoria da seção 10
antes de confiar nele. Ele já esteve errado antes.

Regras que valem sempre (seção 9 do HANDOFF):
- NUNCA subir o servidor. Eu rodo no meu terminal. Prepare tudo e me peça.
- Backup antes de mexer em mundo ou versão.
- Português brasileiro, arquivos completos em vez de diffs.
- Se não testou, diga que não testou.
```

---

## 📅 Sessões

| Data | Arquivo | O que aconteceu |
|---|---|---|
| 04/08/2026 | [2026-08-04-auditoria-migracao-paper.md](2026-08-04-auditoria-migracao-paper.md) | Auditoria do HANDOFF (7 divergências), migração Spigot → Paper 26.2, código migrado para Adventure/MiniMessage, projeto publicado no GitHub, Fase 1 iniciada |

---

## 🔄 Exportar uma sessão nova

O Claude Code grava cada sessão como JSONL em
`~/.claude/projects/<caminho-do-projeto-sanitizado>/<uuid>.jsonl`. O script
converte isso em Markdown legível:

```bash
python3 docs/sessoes/exportar.py --listar          # ver o que existe
python3 docs/sessoes/exportar.py                   # exporta a mais recente
python3 docs/sessoes/exportar.py --sessao <uuid>   # uma específica
python3 docs/sessoes/exportar.py -o docs/sessoes/2026-09-01-narrador.md
```

Depois: revise, renomeie com um nome que diga o que aconteceu, adicione na
tabela acima e commite.

### ⚠️ Antes de commitar um transcript

Um transcript registra **tudo que passou pelo terminal naquela sessão**. Se um
comando imprimiu uma chave, ela está lá.

O script avisa sobre padrões conhecidos (`sk-ant-*`, `gh[pousr]_*`, chaves
privadas, atribuições tipo `password=`), mas isso é uma rede de segurança, não
uma garantia. **Leia o arquivo antes de subir.**

Vale ainda mais aqui do que em código normal: um segredo commitado fica no
histórico do Git para sempre. Apagar num commit seguinte não resolve — é
preciso reescrever o histórico e rotacionar a credencial.

### O que o export deixa de fora

- **Raciocínio interno do modelo.** Volumoso e sem valor para quem lê depois.
- **Saídas de ferramenta longas.** Truncadas com aviso de quanto foi cortado.
  O JSONL original continua na sua máquina se precisar do inteiro.

O que sobra: as mensagens, as respostas e uma linha por chamada de ferramenta
dizendo o que foi feito — que é o suficiente para reconstruir a história.
