#!/usr/bin/env python3
"""
exportar.py — Converte o transcript de uma sessão do Claude Code em Markdown.

O Claude Code grava cada sessão como JSONL em
    ~/.claude/projects/<cwd-sanitizado>/<uuid>.jsonl
Esse arquivo é ótimo para máquina e ilegível para gente. Este script vira
um documento que dá para ler, revisar e commitar.

Uso:
    python3 docs/sessoes/exportar.py                      # sessão mais recente
    python3 docs/sessoes/exportar.py --listar             # ver as disponíveis
    python3 docs/sessoes/exportar.py --sessao <uuid>
    python3 docs/sessoes/exportar.py -o docs/sessoes/x.md

O que NÃO entra no export:
  - blocos de 'thinking' (raciocínio interno do modelo, volumoso e sem
    valor para quem lê depois)
  - saídas de ferramenta acima do limite: são truncadas, com aviso

⚠️  ANTES DE COMMITAR: revise a saída. Um transcript registra tudo que
    passou pelo terminal. Se em algum momento da sessão um segredo apareceu
    numa saída de comando, ele está aqui. Este script avisa sobre padrões
    conhecidos, mas a conferência final é humana.
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

LIMITE_RESULTADO = 1800   # chars por saída de ferramenta antes de truncar
LIMITE_INPUT = 1200       # chars por input de ferramenta

# Padrões que merecem uma segunda olhada antes de publicar.
SUSPEITOS = [
    (r'sk-ant-[A-Za-z0-9_-]{20,}', 'chave da API da Anthropic'),
    (r'gh[pousr]_[A-Za-z0-9]{30,}', 'token do GitHub'),
    (r'AKIA[0-9A-Z]{16}', 'access key da AWS'),
    (r'-----BEGIN [A-Z ]*PRIVATE KEY-----', 'chave privada'),
    (r'(?i)\b(password|senha|secret|api[_-]?key)\s*[=:]\s*["\']?[^\s"\',]{8,}', 'credencial atribuída'),
]


def raiz_projeto() -> Path:
    return Path(__file__).resolve().parent.parent.parent


def dir_transcripts(cwd: Path) -> Path:
    # O Claude Code troca '/' por '-' no caminho absoluto do projeto.
    return Path.home() / '.claude' / 'projects' / str(cwd).replace('/', '-')


def texto_de(conteudo) -> str:
    """Extrai texto de um content que pode ser string ou lista de blocos."""
    if isinstance(conteudo, str):
        return conteudo
    if not isinstance(conteudo, list):
        return ''
    partes = []
    for bloco in conteudo:
        if isinstance(bloco, dict) and bloco.get('type') == 'text':
            partes.append(bloco.get('text', ''))
        elif isinstance(bloco, str):
            partes.append(bloco)
    return '\n'.join(partes)


def corta(txt: str, limite: int) -> str:
    txt = txt.rstrip()
    if len(txt) <= limite:
        return txt
    sobra = len(txt) - limite
    return txt[:limite] + f'\n\n… [truncado: mais {sobra:,} caracteres]'.replace(',', '.')


def resumo_ferramenta(nome: str, entrada: dict) -> str:
    """Uma linha que diga o que a chamada fez, sem despejar o JSON inteiro."""
    if not isinstance(entrada, dict):
        return str(entrada)[:200]
    if nome == 'Bash':
        return entrada.get('command', '')
    if nome in ('Read', 'Write'):
        return entrada.get('file_path', '')
    if nome == 'Edit':
        return entrada.get('file_path', '')
    if nome in ('Grep', 'Glob'):
        return f"{entrada.get('pattern', '')}  {entrada.get('path', '')}".strip()
    if nome == 'TodoWrite':
        todos = entrada.get('todos', [])
        return f'{len(todos)} itens'
    return json.dumps(entrada, ensure_ascii=False)[:LIMITE_INPUT]


def conteudo_resultado(bloco: dict) -> str:
    c = bloco.get('content')
    if isinstance(c, str):
        return c
    if isinstance(c, list):
        return '\n'.join(
            b.get('text', '') for b in c
            if isinstance(b, dict) and b.get('type') == 'text'
        )
    return ''


def exportar(caminho: Path) -> tuple[str, dict]:
    linhas_md: list[str] = []
    stats = {'usuario': 0, 'assistente': 0, 'ferramentas': 0}
    # tool_use_id -> nome, para casar o resultado com a chamada
    nomes: dict[str, str] = {}

    with open(caminho, encoding='utf-8', errors='replace') as fh:
        for linha in fh:
            linha = linha.strip()
            if not linha:
                continue
            try:
                d = json.loads(linha)
            except json.JSONDecodeError:
                continue

            tipo = d.get('type')
            if tipo not in ('user', 'assistant'):
                continue

            msg = d.get('message') or {}
            conteudo = msg.get('content')

            if tipo == 'user':
                # Mensagens 'user' também carregam os tool_result. Separamos.
                resultados = [
                    b for b in conteudo
                    if isinstance(b, dict) and b.get('type') == 'tool_result'
                ] if isinstance(conteudo, list) else []

                txt = texto_de(conteudo).strip()
                if txt and not txt.startswith('<'):
                    stats['usuario'] += 1
                    linhas_md.append(f'\n---\n\n## 👤 Felipe\n\n{txt}\n')

                for r in resultados:
                    nome = nomes.get(r.get('tool_use_id'), 'ferramenta')
                    saida = corta(conteudo_resultado(r), LIMITE_RESULTADO)
                    if saida.strip():
                        erro = ' ❌' if r.get('is_error') else ''
                        linhas_md.append(
                            f'<details><summary>saída de <code>{nome}</code>{erro}</summary>\n\n'
                            f'```\n{saida}\n```\n\n</details>\n'
                        )

            else:  # assistant
                txt = texto_de(conteudo).strip()
                if txt:
                    stats['assistente'] += 1
                    linhas_md.append(f'\n### 🤖 Claude\n\n{txt}\n')

                if isinstance(conteudo, list):
                    for b in conteudo:
                        if isinstance(b, dict) and b.get('type') == 'tool_use':
                            stats['ferramentas'] += 1
                            nome = b.get('name', '?')
                            nomes[b.get('id')] = nome
                            arg = corta(resumo_ferramenta(nome, b.get('input', {})), LIMITE_INPUT)
                            linhas_md.append(f'> 🔧 **{nome}**\n>\n> ```\n> ' +
                                             arg.replace('\n', '\n> ') + '\n> ```\n')

    return '\n'.join(linhas_md), stats


def auditar(texto: str) -> list[tuple[str, str]]:
    achados = []
    for padrao, descricao in SUSPEITOS:
        for m in re.finditer(padrao, texto):
            trecho = m.group(0)
            if len(trecho) > 60:
                trecho = trecho[:60] + '…'
            achados.append((descricao, trecho))
    return achados


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--sessao', help='UUID da sessão (padrão: a mais recente)')
    p.add_argument('--listar', action='store_true', help='listar sessões disponíveis')
    p.add_argument('-o', '--saida', help='arquivo .md de saída')
    p.add_argument('--projeto', default=None, help='raiz do projeto (padrão: deduzida)')
    args = p.parse_args()

    raiz = Path(args.projeto).resolve() if args.projeto else raiz_projeto()
    d = dir_transcripts(raiz)
    if not d.is_dir():
        print(f'❌ Sem transcripts para {raiz}\n   (procurei em {d})', file=sys.stderr)
        return 1

    sessoes = sorted(d.glob('*.jsonl'), key=lambda f: f.stat().st_mtime, reverse=True)
    if not sessoes:
        print(f'❌ Nenhum .jsonl em {d}', file=sys.stderr)
        return 1

    if args.listar:
        print(f'Sessões em {d}:\n')
        for f in sessoes:
            import datetime
            quando = datetime.datetime.fromtimestamp(f.stat().st_mtime)
            print(f'  {f.stem}  {quando:%Y-%m-%d %H:%M}  {f.stat().st_size/1048576:.1f} MB')
        return 0

    alvo = next((f for f in sessoes if f.stem == args.sessao), None) if args.sessao else sessoes[0]
    if alvo is None:
        print(f'❌ Sessão {args.sessao} não encontrada em {d}', file=sys.stderr)
        return 1

    print(f'Lendo  {alvo.name}  ({alvo.stat().st_size/1048576:.1f} MB)')
    md, stats = exportar(alvo)

    import datetime
    quando = datetime.datetime.fromtimestamp(alvo.stat().st_mtime)
    cabecalho = (
        f'# Sessão do Claude Code — {quando:%d/%m/%Y}\n\n'
        f'Transcript exportado de `{alvo.name}`.\n\n'
        f'| | |\n|---|---|\n'
        f'| Mensagens do Felipe | {stats["usuario"]} |\n'
        f'| Respostas do Claude | {stats["assistente"]} |\n'
        f'| Chamadas de ferramenta | {stats["ferramentas"]} |\n'
        f'| Última atividade | {quando:%d/%m/%Y %H:%M} |\n\n'
        f'> Blocos de raciocínio interno foram omitidos. Saídas de ferramenta\n'
        f'> longas aparecem truncadas e ficam recolhidas — clique para abrir.\n'
    )
    doc = cabecalho + md

    achados = auditar(doc)
    if achados:
        print(f'\n⚠️  {len(achados)} trecho(s) merecem revisão antes de commitar:')
        for desc, trecho in achados[:20]:
            print(f'     [{desc}] {trecho}')
        print('   Revise o arquivo. Se for falso positivo, siga.')
    else:
        print('✅ Nenhum padrão de segredo conhecido encontrado.')

    saida = Path(args.saida) if args.saida else \
        raiz / 'docs' / 'sessoes' / f'{quando:%Y-%m-%d}-sessao.md'
    saida.parent.mkdir(parents=True, exist_ok=True)
    saida.write_text(doc, encoding='utf-8')
    print(f'\n📄 {saida}  ({saida.stat().st_size/1024:.0f} KB)')
    print(f'   {stats["usuario"]} mensagens, {stats["assistente"]} respostas, '
          f'{stats["ferramentas"]} chamadas de ferramenta')
    return 0


if __name__ == '__main__':
    sys.exit(main())
