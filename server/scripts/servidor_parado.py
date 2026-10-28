#!/usr/bin/env python3
"""Recusa alterações em um runtime que ainda está no ar.

Dois sinais independentes, porque nenhum dos dois sozinho cobre todos os casos:
um processo Java cujo diretório de trabalho é este runtime, e o `session.lock`
do mundo preso por outro processo. O segundo pega instalações antigas iniciadas
sem o flock do `start.sh`; o primeiro pega um servidor que subiu mas ainda não
abriu nenhum mundo.

Saída 0 significa parado e seguro para mexer.

Esta trava nasceu dentro do instalador de crossplay. O crossplay saiu do
projeto; a trava continua valendo para qualquer script que escreva no runtime.
"""
from __future__ import annotations

import argparse
import fcntl
from pathlib import Path
import sys


class RuntimeEmUso(RuntimeError):
    """Falha apresentável, sem revelar conteúdo dos arquivos."""


def garantir_parado(servidor: Path) -> None:
    if not Path("/proc").is_dir():
        raise RuntimeEmUso("A verificação de processo exige Linux.")

    for processo in Path("/proc").glob("[0-9]*"):
        try:
            if (processo / "comm").read_text().strip() != "java":
                continue
            if (processo / "cwd").resolve() == servidor:
                raise RuntimeEmUso(
                    "Há um processo Java neste runtime. Use stop e aguarde encerrar."
                )
        except (FileNotFoundError, PermissionError, ProcessLookupError):
            continue

    for trava in servidor.glob("*/session.lock"):
        try:
            with trava.open("r+b") as fluxo:
                fcntl.lockf(fluxo, fcntl.LOCK_EX | fcntl.LOCK_NB)
                fcntl.lockf(fluxo, fcntl.LOCK_UN)
        except BlockingIOError as exc:
            raise RuntimeEmUso(
                "O mundo está em uso. Pare o servidor antes de continuar."
            ) from exc
        except (FileNotFoundError, PermissionError):
            continue


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        garantir_parado(args.server_dir.expanduser().resolve())
    except (RuntimeEmUso, OSError, ValueError) as exc:
        print(f"[ERRO] {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
