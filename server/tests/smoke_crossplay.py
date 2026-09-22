#!/usr/bin/env python3
"""Boot real em runtime descartável do CI. Nunca aponta para ~/minecraft."""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import queue
import re
import socket
import struct
import subprocess
import threading
import time


def fingerprint(root: Path) -> dict[str, str]:
    return {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in root.rglob('*') if path.is_file()}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('runtime', type=Path)
    args = parser.parse_args()
    runtime = args.runtime.resolve()
    # Garante que um copiar/colar acidental fora do CI não use um mundo pessoal.
    runner_temp = os.environ.get('RUNNER_TEMP')
    if os.environ.get('CI') != 'true' or not runner_temp or not runtime.is_relative_to(Path(runner_temp).resolve()):
        raise SystemExit('Smoke test exige CI=true e runtime dentro de RUNNER_TEMP.')
    if (runtime / 'world').exists():
        raise SystemExit('Smoke test recusa mundos preexistentes.')
    props = runtime / 'server.properties'
    props.write_text(props.read_text().replace('server-port=25565', 'server-port=25566')
                     .replace('server-ip=\n', 'server-ip=127.0.0.1\n')
                     .replace('view-distance=8', 'view-distance=2')
                     .replace('simulation-distance=6', 'simulation-distance=2'))
    geyser = runtime / 'plugins/Geyser-Spigot/config.yml'
    geyser.write_text(geyser.read_text().replace('0.0.0.0', '127.0.0.1'))
    process = subprocess.Popen(['bash', 'scripts/start.sh'], cwd=runtime,
                               env={**os.environ, 'SERVER_DIR': str(runtime), 'RAM': '2G'},
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, bufsize=1)
    lines: list[str] = []
    events: queue.Queue[str | None] = queue.Queue()

    def reader() -> None:
        with (runtime / 'crossplay-smoke.log').open('w') as output:
            for line in process.stdout:
                output.write(line)
                output.flush()
                lines.append(line)
                print(line, end='', flush=True)
                events.put(line)
        events.put(None)

    thread = threading.Thread(target=reader, daemon=True)
    thread.start()
    error = None
    try:
        deadline = time.monotonic() + 240
        while True:
            line = events.get(timeout=max(0.1, deadline - time.monotonic()))
            if line is None:
                raise RuntimeError('Paper encerrou antes de ficar pronto.')
            # Não confundir com o Done emitido pelo Geyser.
            if 'Done (' in line and 'For help, type "help"' in line:
                break
            if time.monotonic() >= deadline:
                raise RuntimeError('Paper não ficou pronto.')

        magic = bytes.fromhex('00ffff00fefefefefdfdfdfd12345678')
        ping = b'\x01' + struct.pack('>q', int(time.time() * 1000)) + magic + struct.pack('>q', 42)
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.settimeout(8)
            sock.sendto(ping, ('127.0.0.1', 19132))
            pong, _ = sock.recvfrom(65535)
        if pong[:1] != b'\x1c' or b'MCPE;' not in pong:
            raise RuntimeError('Listener não respondeu ao ping Bedrock/RakNet esperado.')
        print('[smoke] Ping Bedrock UDP real respondeu com MOTD MCPE.')

        blocked = subprocess.run(['bash', 'scripts/install-crossplay.sh'], cwd=runtime,
                                 env={**os.environ, 'SERVER_DIR': str(runtime)},
                                 capture_output=True, text=True, timeout=15)
        if blocked.returncode == 0 or 'lock' not in blocked.stderr:
            raise RuntimeError('Instalação com servidor em execução não foi bloqueada pelo lock.')
        print('[smoke] Instalação concorrente bloqueada corretamente.')
        process.stdin.write('geyser version\nversion floodgate\nversion BigaCore\nversion ChestShop\n')
        process.stdin.flush()
    except (Exception, KeyboardInterrupt) as exc:
        error = exc
    finally:
        if process.poll() is None:
            try:
                process.stdin.write('stop\n')
                process.stdin.flush()
                process.wait(timeout=45)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                process.kill()  # Apenas runner descartável, nunca operação de produção.
                process.wait(timeout=10)
        thread.join(timeout=10)
    if error:
        raise error
    if process.returncode != 0:
        raise RuntimeError(f'Paper saiu com código {process.returncode}.')
    log = ''.join(lines)
    required = ('Started Geyser on', 'Enabling floodgate', 'BigaCore habilitado',
                'economia Vault conectada', '[teto] Teto de recompra ligado')
    for text in required:
        if text not in log:
            raise RuntimeError(f'Evidência ausente no log: {text}')
    if re.search(r'Could not load|Failed to load plugin|Error occurred while enabling|UnsupportedClassVersionError', log, re.I):
        raise RuntimeError('Falha de plugin no log.')
    key = runtime / 'plugins/floodgate/key.pem'
    if not key.is_file():
        raise RuntimeError('Floodgate não gerou a chave.')
    key_bytes = key.read_bytes()
    world_before = fingerprint(runtime / 'world')
    configs_before = {path: path.read_bytes() for path in (props, geyser, runtime / 'plugins/floodgate/config.yml')}
    subprocess.run(['bash', 'scripts/install-crossplay.sh'], cwd=runtime, check=True)
    subprocess.run(['bash', 'scripts/install-crossplay.sh', '--check'], cwd=runtime, check=True)
    if key.read_bytes() != key_bytes or fingerprint(runtime / 'world') != world_before:
        raise RuntimeError('A reinstalação alterou mundo ou chave.')
    if any(path.read_bytes() != content for path, content in configs_before.items()):
        raise RuntimeError('A reinstalação sobrescreveu config gerada no primeiro boot.')
    subprocess.run(['bash', 'scripts/doctor.sh'], cwd=runtime, check=True)
    print('[smoke] APROVADO: boot, economia, UDP, bloqueio concorrente e reinstalação sem alterar mundo/chave/configs.')
    print('[smoke] Login Microsoft e interação real no Nintendo Switch NÃO foram testados.')


if __name__ == '__main__':
    main()
