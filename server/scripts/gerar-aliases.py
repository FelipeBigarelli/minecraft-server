#!/usr/bin/env python3
"""
Gera o itemAliases.yml do ChestShop a partir do economy.yml.

POR QUE ISTO EXISTE
-------------------
A placa da Biga Market mostra o item em português ("Ovo de Aldeao"). Quem
resolve esse texto de volta para o Material é o ChestShop, pelo itemAliases.yml.
Sem a linha do item lá dentro, a placa vira decoração: ela aparece bonita na
parede e NÃO vende nada, sem erro nenhum no console.

O cabeçalho do arquivo gerado no servidor já mandava rodar
`python3 server/scripts/gerar-aliases.py` — e o script não estava no repositório.
Ele foi reescrito aqui em 11/11/2026, quando a seção de raridades entrou no
catálogo e 20 nomes novos precisaram de apelido de uma vez.

COMO USAR
---------
    python3 server/scripts/gerar-aliases.py                  # mostra o que mudaria
    python3 server/scripts/gerar-aliases.py --escrever       # grava, com backup

O destino padrão é ~/minecraft/plugins/ChestShop/itemAliases.yml; troque com
--destino. O ChestShop lê o arquivo no boot, então depois de gravar é preciso
reiniciar o servidor (ou /reload confirm, que é pior).

NÃO depende de PyYAML: o economy.yml é lido por expressão regular, porque o
único formato que importa aqui é `  chave: "Nome"` dentro da seção nomes:.
"""

import argparse
import datetime
import os
import re
import sys
import unicodedata

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ECONOMY = os.path.join(RAIZ, "plugin", "src", "main", "resources", "economy.yml")
DESTINO = os.path.expanduser("~/minecraft/plugins/ChestShop/itemAliases.yml")

# Limite da linha 4 da placa. Nome maior é cortado pelo cliente e deixa de
# casar com o apelido, que é a falha silenciosa que este script existe para
# evitar.
MAX_PLACA = 15

CABECALHO = """\
# =============================================================
# GERADO pelo projeto BigaCore a partir de economy.yml -> nomes:
#
# Traduz o nome do item nas placas da Biga Market. A linha 4 da
# placa precisa ser resolvivel pelo ChestShop; sem este arquivo,
# um nome em portugues NAO seria reconhecido e a loja quebraria.
#
# Formato: <MATERIAL>: <apelido mostrado/aceito na placa>
# Regenerar: python3 server/scripts/gerar-aliases.py --escrever
# =============================================================
"""


def sem_acento(texto):
    """Apelido vai sem acento: é assim que o arquivo atual já está escrito."""
    normal = unicodedata.normalize("NFD", texto)
    return "".join(c for c in normal if unicodedata.category(c) != "Mn")


def secao(conteudo, nome):
    """O corpo de uma seção de primeiro nível do YAML, sem depender de PyYAML."""
    achou = re.search(rf"^{nome}:\n(.*?)(?=^\S|\Z)", conteudo, re.S | re.M)
    return achou.group(1) if achou else ""


def ler_economy(caminho):
    with open(caminho, encoding="utf-8") as arquivo:
        conteudo = arquivo.read()

    nomes = {}
    for chave, valor in re.findall(r'^  ([a-z0-9_]+): *"([^"]+)"',
                                   secao(conteudo, "nomes"), re.M):
        nomes[chave] = valor

    parede = re.findall(r"^    - ([a-z0-9_]+)",
                        secao(conteudo, "admin-shop"), re.M)
    return nomes, parede


def conferir(nomes, parede):
    """Os dois erros que viram placa morta em jogo."""
    problemas = []

    for chave in parede:
        if chave not in nomes:
            problemas.append(f"{chave} esta na parede e nao tem nome em nomes:")

    for chave, nome in sorted(nomes.items()):
        if len(nome) > MAX_PLACA:
            problemas.append(
                f'{chave}: "{nome}" tem {len(nome)} caracteres (limite {MAX_PLACA})')

    repetidos = {}
    for chave, nome in nomes.items():
        repetidos.setdefault(sem_acento(nome).lower(), []).append(chave)
    for nome, chaves in sorted(repetidos.items()):
        if len(chaves) > 1:
            problemas.append(f'o apelido "{nome}" serve a {len(chaves)} itens: '
                             + ", ".join(sorted(chaves)))

    return problemas


def montar(nomes):
    linhas = [CABECALHO, ""]
    for chave, nome in sorted(nomes.items()):
        linhas.append(f"{chave.upper()}: {sem_acento(nome)}")
    return "\n".join(linhas) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--escrever", action="store_true",
                        help="grava o arquivo (sem isto, so mostra o resumo)")
    parser.add_argument("--destino", default=DESTINO)
    parser.add_argument("--economy", default=ECONOMY)
    args = parser.parse_args()

    nomes, parede = ler_economy(args.economy)
    if not nomes:
        print("Nenhum nome encontrado em economy.yml -> nomes:", file=sys.stderr)
        return 1

    problemas = conferir(nomes, parede)
    for problema in problemas:
        print("ERRO:", problema, file=sys.stderr)
    if problemas:
        print("\nNada gravado: corrija o economy.yml primeiro.", file=sys.stderr)
        return 1

    conteudo = montar(nomes)
    print(f"{len(nomes)} apelidos a partir de {args.economy}")
    print(f"{len(parede)} itens na parede do Admin Shop")

    if not args.escrever:
        print(f"\n(ensaio) use --escrever para gravar em {args.destino}")
        return 0

    if os.path.isfile(args.destino):
        carimbo = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
        backup = f"{args.destino}.bak-{carimbo}"
        with open(args.destino, "rb") as origem, open(backup, "wb") as copia:
            copia.write(origem.read())
        print(f"backup: {backup}")

    with open(args.destino, "w", encoding="utf-8") as arquivo:
        arquivo.write(conteudo)
    print(f"gravado: {args.destino}")
    print("O ChestShop le este arquivo no boot — reinicie o servidor.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
