"""Regressões offline: nunca baixam plugins nem executam um mundo real."""
import contextlib
import fcntl
import importlib.util
import io
import json
from pathlib import Path
import shutil
import socket
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
CONFIG = SCRIPTS.parent / "config"
spec = importlib.util.spec_from_file_location("crossplay", SCRIPTS / "crossplay.py")
cp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cp)


class CrossplayTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.server = self.root / "runtime"
        self.server.mkdir()
        self.config = self.root / "config"
        shutil.copytree(CONFIG / "plugins/Geyser-Spigot", self.config / "plugins/Geyser-Spigot")
        shutil.copytree(CONFIG / "plugins/floodgate", self.config / "plugins/floodgate")
        self.lock = json.loads((CONFIG / "crossplay.lock.json").read_text())
        self.jars = {}
        for p in self.lock["plugins"]:
            jar = self.root / p["filename"]
            with zipfile.ZipFile(jar, "w") as z:
                z.writestr("plugin.yml", f"name: {p['plugin_name']}\nversion: {p['version']}-SNAPSHOT\n")
            p["sha256"] = cp.digest(jar)
            self.jars[p["filename"]] = jar
        (self.config / "crossplay.lock.json").write_text(json.dumps(self.lock))
        # Evita disputar uma porta fixa com outros testes locais.
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        path = self.config / "plugins/Geyser-Spigot/config.yml"
        path.write_text(path.read_text().replace("19132", str(port)).replace("0.0.0.0", "127.0.0.1"))
        self.props = self.server / "server.properties"
        self.original = b"# pessoal\nonline-mode=true\nenforce-secure-profile=true\nlevel-name=mundo-meu\nrcon.password=SEGREDO_LOCAL\n"
        self.props.write_bytes(self.original)
        (self.server / "world").mkdir()
        (self.server / "world/level.dat").write_bytes(b"MUNDO_NAO_TOCAR")
        self.stdout = contextlib.redirect_stdout(io.StringIO())
        self.stdout.__enter__()
        self.addCleanup(self.stdout.__exit__, None, None, None)

    def fake_download(self, plugin, path):
        shutil.copyfile(self.jars[plugin["filename"]], path)
        cp.verify_jar(path, plugin)

    def install(self, replace=False):
        with patch.object(cp, "download", side_effect=self.fake_download):
            cp.install(self.server, self.config, "26.2", replace)

    def config_objects(self):
        return [cp.read_yaml((self.config / f"plugins/{n}/config.yml").read_bytes()) for n in cp.CONFIGS]

    def test_install_preserves_world_secrets_and_online_mode(self):
        self.install()
        self.assertEqual((self.server / "world/level.dat").read_bytes(), b"MUNDO_NAO_TOCAR")
        self.assertEqual(self.props.read_bytes(), self.original.replace(b"enforce-secure-profile=true", b"enforce-secure-profile=false"))
        backups = list(self.server.glob(".crossplay-backups/*/server.properties"))
        self.assertEqual(len(backups), 1)
        self.assertEqual(backups[0].read_bytes(), self.original)
        self.assertEqual((self.server / ".crossplay-backups").stat().st_mode & 0o777, 0o700)
        cp.check(self.server, self.config, "26.2")

    def test_idempotent_without_network_or_new_backup(self):
        self.install()
        files = {p: p.read_bytes() for p in self.server.rglob("*") if p.is_file()}
        with patch.object(cp, "download", side_effect=AssertionError("Não deveria baixar")):
            cp.install(self.server, self.config, "26.2")
        self.assertEqual(files, {p: p.read_bytes() for p in self.server.rglob("*") if p.is_file()})

    def test_concurrent_config_edit_is_not_overwritten(self):
        def edit(plugin, path):
            self.fake_download(plugin, path)
            self.props.write_bytes(self.original + b"# alterado durante download\n")
        with patch.object(cp, "download", side_effect=edit), self.assertRaises(cp.CrossplayError):
            cp.install(self.server, self.config, "26.2")
        self.assertTrue(self.props.read_bytes().endswith(b"# alterado durante download\n"))
        self.assertFalse((self.server / "plugins/Geyser-Spigot.jar").exists())

    def test_failed_second_download_changes_no_config_or_plugins(self):
        def fail(plugin, path):
            if plugin["project"] == "floodgate":
                raise cp.CrossplayError("Rede indisponível")
            self.fake_download(plugin, path)
        with patch.object(cp, "download", side_effect=fail), self.assertRaises(cp.CrossplayError):
            cp.install(self.server, self.config, "26.2")
        self.assertEqual(self.props.read_bytes(), self.original)
        self.assertFalse(list(self.server.glob("plugins/*.jar")))
        self.assertFalse(list(self.server.glob(".crossplay-stage-*")))

    def test_refuses_offline_mode(self):
        self.props.write_bytes(self.original.replace(b"online-mode=true", b"online-mode=false"))
        with self.assertRaises(cp.CrossplayError):
            self.install()
        self.assertFalse((self.server / "plugins").exists())

    def test_refuses_duplicate_security_property(self):
        with self.assertRaises(cp.CrossplayError):
            cp.chat_properties(self.original + b"online-mode=false\n")

    def test_preserves_crlf_and_unrelated_fields(self):
        value = self.original.replace(b"\n", b"\r\n")
        self.assertEqual(cp.chat_properties(value), value.replace(b"enforce-secure-profile=true", b"enforce-secure-profile=false"))

    def test_appends_missing_chat_property(self):
        self.assertEqual(cp.chat_properties(b"online-mode=true"), b"online-mode=true\nenforce-secure-profile=false\n")

    def test_refuses_legacy_geyser_config(self):
        g, f = self.config_objects()
        g["remote"] = {"auth-type": "online"}
        with self.assertRaises(cp.CrossplayError):
            cp.validate_configs(g, f)

    def test_refuses_unsigned_bedrock_login(self):
        g, f = self.config_objects()
        g["advanced"]["bedrock"]["validate-bedrock-login"] = False
        with self.assertRaises(cp.CrossplayError):
            cp.validate_configs(g, f)

    def test_refuses_empty_username_prefix(self):
        g, f = self.config_objects()
        f["username-prefix"] = ""
        with self.assertRaises(cp.CrossplayError):
            cp.validate_configs(g, f)

    def test_refuses_duplicate_yaml_keys(self):
        with self.assertRaises(cp.CrossplayError):
            cp.read_yaml(b"java:\n  auth-type: floodgate\n  auth-type: offline\n")

    def test_refuses_renamed_duplicate_jar(self):
        (self.server / "plugins").mkdir()
        shutil.copy(self.jars["Geyser-Spigot.jar"], self.server / "plugins/outro-nome.jar")
        with self.assertRaises(cp.CrossplayError):
            self.install()
        self.assertEqual(self.props.read_bytes(), self.original)

    def test_corrupt_existing_jar_requires_explicit_replace(self):
        self.install()
        jar = self.server / "plugins/Geyser-Spigot.jar"
        jar.write_bytes(b"corrompido")
        with self.assertRaises(cp.CrossplayError):
            self.install()
        self.install(replace=True)
        cp.verify_jar(jar, self.lock["plugins"][0])

    def test_preserves_existing_yaml_and_key(self):
        self.install()
        config = self.server / "plugins/Geyser-Spigot/config.yml"
        custom = config.read_bytes() + b"# meu comentario\n"
        config.write_bytes(custom)
        key = self.server / "plugins/floodgate/key.pem"
        key.write_bytes(b"CHAVE_PRIVADA")
        self.install()
        self.assertEqual(config.read_bytes(), custom)
        self.assertEqual(key.read_bytes(), b"CHAVE_PRIVADA")

    def test_refuses_symlink_destination(self):
        external = self.root / "external"
        external.mkdir()
        (self.server / "plugins").symlink_to(external)
        with self.assertRaises(cp.CrossplayError):
            self.install()
        self.assertEqual(list(external.iterdir()), [])

    def test_refuses_minecraft_mismatch(self):
        with self.assertRaises(cp.CrossplayError):
            cp.install(self.server, self.config, "1.20.2")

    def test_refuses_busy_install_lock(self):
        with (self.server / ".crossplay.lock").open("a") as stream:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaises(cp.CrossplayError):
                self.install()

    def test_refuses_query_port_conflict(self):
        g, _ = self.config_objects()
        self.props.write_bytes(self.original + f"enable-query=true\nquery.port={g['bedrock']['port']}\n".encode())
        with self.assertRaises(cp.CrossplayError):
            self.install()

    def test_refuses_busy_udp_port(self):
        g, _ = self.config_objects()
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.bind(("127.0.0.1", g["bedrock"]["port"]))
            with self.assertRaises(cp.CrossplayError):
                self.install()

    def test_rollback_restores_first_file_when_second_write_fails(self):
        a, b = self.root / "a", self.root / "b"
        a.write_bytes(b"nova-config")
        b.write_bytes(b"novo-jar")
        real_copy = cp.atomic_copy
        calls = []
        def fail_second(source, dest, mode):
            calls.append(dest)
            if len(calls) == 2:
                raise OSError("Falha simulada de disco")
            real_copy(source, dest, mode)
        with patch.object(cp, "atomic_copy", side_effect=fail_second), self.assertRaises(OSError):
            cp.apply_files(self.server, {"server.properties": a, "plugins/test.jar": b})
        self.assertEqual(self.props.read_bytes(), self.original)
        self.assertFalse((self.server / "plugins/test.jar").exists())

    def test_runtime_assets_support_readonly_check(self):
        self.install()
        root = self.server / "scripts/crossplay-config"
        cp.check(self.server, root, "26.2")
        self.assertEqual(cp.read_lock(root), self.lock)


if __name__ == "__main__":
    unittest.main()
