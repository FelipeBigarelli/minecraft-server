# 📜 Sessões e decisões de trabalho

Histórico para retomar o contexto em outra máquina ou meses depois sem depender
da memória de uma única conversa.

O `HANDOFF.md` é a fonte principal do **estado corrente**. Esta pasta registra
como chegamos nele.

---

## 🆕 PC novo: primeiro monte o ambiente

Se esta é uma máquina recém-formatada ou diferente da anterior, **não comece pelo
HANDOFF**. Primeiro conclua [../../NOVO-PC.md](../../NOVO-PC.md): Launcher, Git,
clone, setup, restauração do mundo, doctor e primeiro boot.

Depois volte aqui para retomar desenvolvimento.

---

## 🚀 Retomar o trabalho em outro PC

Cole isto numa sessão nova do Claude Code, já dentro do projeto clonado e com o
fluxo de `NOVO-PC.md` concluído:

```text
Leia, nesta ordem:

1. HANDOFF.md — estado atual, decisões e armadilhas.
2. PLANO-EXECUCAO.md — plano faseado.
3. README.md e COMO-RODAR.md — operação atual.
4. docs/sessoes/ — use os resumos recentes se precisar entender como chegamos aqui.

Antes de agir, confira o estado real da máquina com:
    cd ~/minecraft && bash scripts/doctor.sh

Trate o HANDOFF como hipótese, não como verdade absoluta: audite o estado real
relevante para a tarefa antes de agir.

Regras que valem sempre:
- NUNCA subir o servidor por conta própria. Eu rodo no meu terminal.
- Backup antes de mexer em mundo ou versão.
- Se não testou, diga que não testou.
- Não instale plugin sem confirmar versão/fonte atual.
```

---

## 📅 Sessões

| Data | Arquivo | O que aconteceu |
|---|---|---|
| 11/08/2026 | [2026-08-11-auditoria-correcoes-github.md](2026-08-11-auditoria-correcoes-github.md) | Auditoria do repositório atual, separação de backup privado/snapshot do mundo, persistência de RAM/path, correção do plano SQLite/scanner e CI |
| 04/08/2026 | [2026-08-04-auditoria-migracao-paper.md](2026-08-04-auditoria-migracao-paper.md) | Transcript histórico da auditoria/migração Spigot → Paper 26.2, Adventure/MiniMessage e publicação inicial |

---

## 📝 Formato recomendado daqui para frente

Prefira **resumos de sessão** como o arquivo de 11/08:

- objetivo;
- decisões;
- arquivos alterados;
- riscos encontrados;
- validações executadas;
- próximos passos.

Um transcript integral só deve ser versionado quando o detalhe cronológico tiver
valor real. Transcripts carregam muito ruído e podem registrar paths locais,
saídas de terminal ou dados que não precisam ficar públicos.

---

## 🔄 Exportar uma sessão do Claude Code

O script histórico continua disponível:

```bash
python3 docs/sessoes/exportar.py --listar
python3 docs/sessoes/exportar.py
python3 docs/sessoes/exportar.py --sessao <uuid>
python3 docs/sessoes/exportar.py -o docs/sessoes/AAAA-MM-DD-sessao.md
```

### ⚠️ Antes de commitar um transcript

Um transcript registra o que passou pela sessão. Se um comando imprimiu uma
credencial, ela pode estar no arquivo.

O script procura alguns padrões conhecidos, mas isso é uma rede de segurança,
não garantia. **Revise o arquivo antes de subir.**

Um segredo commitado fica no histórico do Git; apagar o arquivo num commit
seguinte não é o mesmo que revogar/rotacionar a credencial.

### O que o export deixa de fora

- blocos de raciocínio interno;
- saídas de ferramenta muito longas, que aparecem truncadas.

O JSONL original continua local caso o detalhe completo seja necessário.
