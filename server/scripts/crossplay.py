#!/usr/bin/env python3
"""Crossplay local: builds fixadas, pré-validação, staging e rollback de arquivos.

Não toca em mundos, economia, contas, chaves ou regras de firewall. Requer Linux,
Python 3.10+ e PyYAML do sistema (python3-yaml no Ubuntu/Debian).
"""
from __future__ import annotations

import argparse
import fcntl
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import socket
import stat
import subprocess
import sys
import tempfile
import zipfile

import yaml

API = "https://download.geysermc.org/v2/projects"
CONFIGS = ("Geyser-Spigot", "floodgate")


class CrossplayError(RuntimeError):
    """Falha segura e apresentável sem revelar dados dos arquivos."""


class UniqueLoader(yaml.SafeLoader):
    """Rejeita chaves duplicadas em vez de esconder configurações conflitantes."""


def unique_mapping(loader, node, deep=False):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if not isinstance(key, str) or key in result:
            raise CrossplayError("YAML contém chave duplicada ou não textual.")
        result[key] = loader.construct_object(value_node, deep=deep)
    return result


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)


def read_yaml(content: bytes) -> dict:
    try:
        value = yaml.load(content, Loader=UniqueLoader)
    except yaml.YAMLError as exc:
        raise CrossplayError("YAML inválido; revise o arquivo indicado, sem publicar segredos.") from exc
    if not isinstance(value, dict):
        raise CrossplayError("O arquivo YAML deve conter um objeto de configuração.")
    return value


def require(condition: bool, message: str) -> None:
    if not condition:
        raise CrossplayError(message)


def nested(data: dict, *keys, default=None):
    value = data
    for key in keys:
        if not isinstance(value, dict):
            return default
        value = value.get(key, default)
    return value


def read_lock(root: Path) -> dict:
    lock = json.loads((root / "crossplay.lock.json").read_text())
    require(lock.get("schema") == 1, "Schema do lock não suportado.")
    expected = {"geyser": ("Geyser-Spigot.jar", "Geyser-Spigot"),
                "floodgate": ("floodgate-spigot.jar", "floodgate")}
    plugins = lock.get("plugins", [])
    require(len(plugins) == 2, "O lock deve conter exatamente Geyser e Floodgate.")
    require({p.get("project") for p in plugins} == set(expected), "Projetos inesperados no lock.")
    for plugin in plugins:
        require((plugin.get("filename"), plugin.get("plugin_name")) == expected[plugin["project"]],
                "Nome de arquivo/plugin inesperado no lock.")
        require(re.fullmatch(r"\d+\.\d+\.\d+", str(plugin.get("version"))) is not None,
                "Versão não fixada no lock.")
        require(type(plugin.get("build")) is int and plugin["build"] > 0, "Build não fixada no lock.")
        require(re.fullmatch(r"[0-9a-f]{64}", str(plugin.get("sha256"))) is not None,
                "SHA256 inválido no lock.")
    return lock


def properties(content: bytes) -> tuple[str, dict]:
    text = content.decode("utf-8")
    result = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        require(not (len(line) - len(line.rstrip("\\"))) % 2,
                "server.properties usa continuação de linha; normalize antes de instalar crossplay.")
        match = re.match(r"\s*([^\s:=]+)\s*(?:[=:]\s*|\s+)(.*)$", line)
        require(match is not None, "Linha não reconhecida em server.properties.")
        key, value = match.groups()
        require("\\" not in key, "Chave escapada não suportada em server.properties.")
        require(key not in result, "Chave duplicada em server.properties; normalize antes de continuar.")
        result[key] = value.strip()
    return text, result


def chat_properties(content: bytes) -> bytes:
    text, values = properties(content)
    require(values.get("online-mode") == "true", "online-mode deve permanecer true. Não foi alterado.")
    if values.get("enforce-secure-profile") == "false":
        return content
    newline = "\r\n" if "\r\n" in text else "\n"
    lines = text.splitlines(keepends=True)
    found = False
    for index, line in enumerate(lines):
        if re.match(r"\s*enforce-secure-profile\s*(?:[=:]|\s)", line):
            lines[index] = "enforce-secure-profile=false" + newline
            found = True
    if not found:
        if lines and not lines[-1].endswith(("\n", "\r")):
            lines[-1] += newline
        lines.append("enforce-secure-profile=false" + newline)
    return "".join(lines).encode("utf-8")


def validate_configs(geyser: dict, floodgate: dict) -> tuple[str, int]:
    require(geyser.get("config-version") == 8 and "remote" not in geyser,
            "Geyser: esperado schema 8 com java.auth-type. Revise a config existente; não foi sobrescrita.")
    require(nested(geyser, "java", "auth-type") == "floodgate", "Geyser: java.auth-type deve ser floodgate.")
    require(nested(geyser, "bedrock", "clone-remote-port", default=False) is False,
            "Geyser: clone-remote-port deve ser false neste setup.")
    require(nested(geyser, "bedrock", "transport", default="raknet") == "raknet",
            "Este perfil foi validado com RakNet. NetherNet exige revisão de portas e sinalização.")
    address = nested(geyser, "bedrock", "address", default="0.0.0.0")
    try:
        require(ipaddress.ip_address(address).version == 4, "Este perfil exige IPv4 no listener Bedrock.")
    except ValueError as exc:
        raise CrossplayError("Geyser: endereço IPv4 inválido.") from exc
    port = nested(geyser, "bedrock", "port")
    require(type(port) is int and 1024 <= port <= 65535, "Geyser: porta deve estar entre 1024 e 65535.")
    require(nested(geyser, "advanced", "bedrock", "validate-bedrock-login", default=True) is True,
            "A autenticação Bedrock não pode ser desativada.")
    for section, key in (("java", "use-haproxy-protocol"), ("bedrock", "use-haproxy-protocol"),
                         ("bedrock", "use-waterdogpe-forwarding")):
        require(nested(geyser, "advanced", section, key, default=False) is False,
                "Este servidor direto não usa encaminhamento de identidade por proxy.")
    require(nested(geyser, "advanced", "java", "use-direct-connection", default=True) is True,
            "Este perfil requer a conexão direta entre os plugins no mesmo Paper.")
    require(floodgate.get("config-version") == 3, "Floodgate: schema esperado = 3.")
    prefix = floodgate.get("username-prefix", ".")
    require(isinstance(prefix, str) and any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_" for c in prefix),
            "Floodgate: preserve um prefixo não alfanumérico para evitar colisões com nicks Java.")
    require(floodgate.get("key-file-name", "key.pem") == "key.pem", "Revise a chave personalizada antes de usar este instalador.")
    require(nested(floodgate, "player-link", "require-link", default=False) is False,
            "require-link deve ser false para não exigir conta Java.")
    return address, port


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def verify_jar(path: Path, plugin: dict) -> None:
    require(digest(path) == plugin["sha256"], f"SHA256 não confere: {plugin['filename']}.")
    try:
        with zipfile.ZipFile(path) as archive:
            metadata = read_yaml(archive.read("plugin.yml"))
    except (zipfile.BadZipFile, KeyError) as exc:
        raise CrossplayError(f"JAR inválido: {plugin['filename']}.") from exc
    require(metadata.get("name") == plugin["plugin_name"], "Identidade do plugin não confere.")
    require(str(metadata.get("version", "")).split("-SNAPSHOT")[0] == plugin["version"],
            "Versão interna do plugin não confere.")


def safe_path(server: Path, relative: str) -> Path:
    path = server / relative
    require(not Path(relative).is_absolute() and ".." not in Path(relative).parts, "Destino fora do runtime.")
    for part in (path, *path.parents):
        if part == server:
            break
        require(not part.is_symlink(), f"Link simbólico não permitido no destino: {relative}")
    return path


def ensure_stopped(server: Path) -> None:
    require(Path("/proc").is_dir(), "A verificação de processo deste instalador exige Linux.")
    for proc in Path("/proc").glob("[0-9]*"):
        try:
            if (proc / "comm").read_text().strip() == "java" and (proc / "cwd").resolve() == server:
                raise CrossplayError("Há um processo Java neste runtime. Use stop e aguarde encerrar.")
        except (FileNotFoundError, PermissionError, ProcessLookupError):
            continue
    for lock in server.glob("*/session.lock"):
        try:
            with lock.open("r+b") as stream:
                fcntl.lockf(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
                fcntl.lockf(stream, fcntl.LOCK_UN)
        except BlockingIOError as exc:
            raise CrossplayError("O mundo está em uso. Pare o servidor antes da instalação.") from exc


def duplicates(server: Path, plugins: list[dict]) -> None:
    names = {p["plugin_name"].lower(): p["filename"] for p in plugins}
    for path in (server / "plugins").glob("*.jar"):
        expected = None
        try:
            with zipfile.ZipFile(path) as archive:
                metadata = read_yaml(archive.read("plugin.yml"))
                expected = names.get(str(metadata.get("name", "")).lower())
        except (zipfile.BadZipFile, KeyError):
            pass
        # Detecta também arquivos com nome conhecido mas corrompidos/sem metadata.
        if expected is None:
            for plugin in plugins:
                if plugin["project"] in path.name.lower():
                    expected = plugin["filename"]
        if expected:
            require(path.name == expected, f"JAR alternativo/duplicado: {path.name}. Revise e mova-o para fora de plugins/.")


def download(plugin: dict, destination: Path) -> None:
    url = f"{API}/{plugin['project']}/versions/{plugin['version']}/builds/{plugin['build']}/downloads/spigot"
    print(f"[crossplay] Baixando {plugin['project']} {plugin['version']} build {plugin['build']}...", flush=True)
    subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error", "--proto", "=https",
                    "--proto-redir", "=https", "--retry", "2", "--connect-timeout", "15", "--max-time", "300",
                    "--output", str(destination), url], check=True)
    verify_jar(destination, plugin)


def atomic_copy(source: Path, destination: Path, mode: int) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=".crossplay-write-", dir=destination.parent)
    try:
        with os.fdopen(fd, "wb") as stream, source.open("rb") as incoming:
            shutil.copyfileobj(incoming, stream)
            stream.flush()
            os.fsync(stream.fileno())
            os.fchmod(stream.fileno(), mode)
        os.replace(temporary, destination)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def apply_files(server: Path, changes: dict[str, Path]) -> Path | None:
    changes = {name: source for name, source in changes.items()
               if not (server / name).is_file() or digest(server / name) != digest(source)}
    if not changes:
        return None
    root = safe_path(server, ".crossplay-backups")
    root.mkdir(mode=0o700, exist_ok=True)
    root.chmod(0o700)
    backup = Path(tempfile.mkdtemp(prefix="transaction-", dir=root))
    previous = {}
    for name in changes:
        path = safe_path(server, name)
        previous[name] = None
        if path.exists():
            require(path.is_file(), f"Destino não é arquivo regular: {name}")
            target = backup / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
            previous[name] = stat.S_IMODE(path.stat().st_mode)
    applied = []
    try:
        for name, source in changes.items():
            path = safe_path(server, name)
            mode = previous[name]
            if mode is None:
                mode = 0o600
                if name.endswith((".jar", ".sh", ".py")):
                    mode = 0o644
            atomic_copy(source, path, mode)
            applied.append(name)
    except BaseException:
        for name in reversed(applied):
            path = server / name
            if previous[name] is None:
                path.unlink(missing_ok=True)
            else:
                atomic_copy(backup / name, path, previous[name])
        raise
    return backup


def install(server: Path, root: Path, minecraft: str, replace: bool = False) -> None:
    require(server.is_dir(), "Runtime não existe. Execute o setup primeiro.")
    lock = read_lock(root)
    require(minecraft == lock["minecraft"], "Versão Minecraft diferente do lock; valide a compatibilidade antes de instalar.")
    with safe_path(server, ".crossplay.lock").open("a") as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise CrossplayError("Servidor ou outra instalação detém o lock. Use stop primeiro.") from exc
        ensure_stopped(server)
        current = safe_path(server, "server.properties").read_bytes()
        updated = chat_properties(current)
        sources = {}
        snapshots = {"server.properties": current}
        configs = []
        for name in CONFIGS:
            relative = f"plugins/{name}/config.yml"
            path = safe_path(server, relative)
            source = root / relative
            if path.exists():
                source = path
            sources[relative] = source
            snapshots[relative] = path.read_bytes() if path.exists() else None
            configs.append(read_yaml(source.read_bytes()))
        address, port = validate_configs(*configs)
        _, props = properties(current)
        require(not (props.get("enable-query") == "true" and int(props.get("query.port", "25565")) == port),
                "A porta UDP escolhida já está configurada para Query.")
        duplicates(server, lock["plugins"])
        for plugin in lock["plugins"]:
            existing = safe_path(server, "plugins/" + plugin["filename"])
            if existing.exists() and not replace:
                verify_jar(existing, plugin)
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            try:
                probe.bind((address, port))
            except OSError as exc:
                raise CrossplayError("Listener UDP indisponível. Revise endereço/porta e pare outros serviços conflitantes.") from exc
        # Todos os downloads terminam e são verificados ANTES de trocar arquivos.
        with tempfile.TemporaryDirectory(prefix=".crossplay-stage-", dir=server) as temporary:
            stage = Path(temporary)
            changes = dict(sources)
            for plugin in lock["plugins"]:
                name = "plugins/" + plugin["filename"]
                existing = server / name
                if existing.exists() and digest(existing) == plugin["sha256"]:
                    verify_jar(existing, plugin)
                    continue
                path = stage / plugin["filename"]
                download(plugin, path)
                changes[name] = path
            patch = stage / "server.properties"
            patch.write_bytes(updated)
            changes["server.properties"] = patch
            script_dir = Path(__file__).resolve().parent
            for name in ("crossplay.py", "install-crossplay.sh"):
                changes["scripts/" + name] = script_dir / name
            changes["scripts/crossplay-config/crossplay.lock.json"] = root / "crossplay.lock.json"
            for name in CONFIGS:
                relative = f"plugins/{name}/config.yml"
                changes["scripts/crossplay-config/" + relative] = root / relative
            # Revalida processos após downloads demorados; start.sh usa o mesmo lock.
            ensure_stopped(server)
            for relative, before in snapshots.items():
                target = safe_path(server, relative)
                now = target.read_bytes() if target.exists() else None
                require(now == before, "Configuração mudou durante a instalação. Nada foi aplicado; tente novamente.")
            backup = apply_files(server, changes)
    if backup:
        print(f"[crossplay] Backup PRIVADO dos arquivos alterados: {backup}")
    print(f"[crossplay] Configurado: Java permanece autenticado; Bedrock {address}:{port}/UDP.")
    print("[crossplay] Nenhum mundo foi modificado e nenhum servidor foi iniciado.")


def check(server: Path, root: Path, minecraft: str) -> None:
    lock = read_lock(root)
    require(minecraft == lock["minecraft"], "Minecraft e crossplay.lock.json não correspondem.")
    duplicates(server, lock["plugins"])
    for plugin in lock["plugins"]:
        verify_jar(safe_path(server, "plugins/" + plugin["filename"]), plugin)
    configs = [read_yaml(safe_path(server, f"plugins/{name}/config.yml").read_bytes()) for name in CONFIGS]
    address, port = validate_configs(*configs)
    _, values = properties((server / "server.properties").read_bytes())
    require(values.get("online-mode") == "true", "online-mode deve ser true.")
    require(values.get("enforce-secure-profile") == "false", "enforce-secure-profile deve ser false para o chat Bedrock.")
    require(not (values.get("enable-query") == "true" and int(values.get("query.port", "25565")) == port),
            "Porta UDP em conflito com Query.")
    if not safe_path(server, "plugins/floodgate/key.pem").exists():
        print("[AVISO] Chave Floodgate ainda ausente; o plugin a gera no primeiro boot.")
    if values.get("white-list") != "true":
        print("[AVISO] Whitelist desativada; não exponha o servidor à internet sem revisar o acesso.")
    print(f"[OK] Crossplay: hashes e configurações aprovados. Listener previsto: {address}:{port}/UDP.")
    print("[INFO] Este diagnóstico não comprova login no Switch nem atravessamento de firewall.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("install", "check", "stopped"))
    parser.add_argument("--server-dir", type=Path, required=True)
    parser.add_argument("--config-root", type=Path, required=True)
    parser.add_argument("--minecraft", default="26.2")
    parser.add_argument("--replace-jars", action="store_true")
    args = parser.parse_args()
    try:
        server = args.server_dir.expanduser().resolve()
        root = args.config_root.expanduser().resolve()
        if args.mode == "install":
            install(server, root, args.minecraft, args.replace_jars)
        elif args.mode == "check":
            check(server, root, args.minecraft)
        else:
            ensure_stopped(server)
    except (CrossplayError, OSError, ValueError, subprocess.SubprocessError) as exc:
        print(f"[ERRO] Crossplay: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
