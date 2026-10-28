"""A trava que recusa mexer num runtime que ainda está no ar.

O caminho do processo Java não é testável aqui sem subir uma JVM de verdade,
então o que se cobre é o outro sinal — o `session.lock` do mundo — mais a
garantia de que um runtime realmente parado não dá falso positivo. Um falso
positivo é o erro caro: ele bloqueia o setup de alguém cujo servidor está
desligado, e a pessoa não tem como saber que a culpa é da trava.
"""
from __future__ import annotations

import fcntl
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

from servidor_parado import RuntimeEmUso, garantir_parado  # noqa: E402

# Prende o lock e fica parado até ser morto, anunciando quando conseguiu.
PRENDE_LOCK = """
import fcntl, sys, time
with open(sys.argv[1], 'r+b') as f:
    fcntl.lockf(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
    print('preso', flush=True)
    time.sleep(300)
"""


class TestGarantirParado(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.servidor = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)

    def test_runtime_vazio_passa(self):
        garantir_parado(self.servidor)

    def test_mundo_sem_lock_preso_passa(self):
        mundo = self.servidor / "world"
        mundo.mkdir()
        (mundo / "session.lock").write_bytes(b"\xde\xad\xbe\xef")
        garantir_parado(self.servidor)

    def test_session_lock_preso_recusa(self):
        """O lock precisa ser de OUTRO processo para a trava enxergá-lo.

        `fcntl.lockf` é de posse do processo, não do descritor: quem já
        segura o lock consegue pegá-lo de novo sem bloquear. Travar o
        arquivo aqui dentro do próprio teste, portanto, não reproduz nada
        — e foi exatamente assim que a primeira versão deste teste passou
        a impressão errada de que a trava não funcionava.
        """
        mundo = self.servidor / "world"
        mundo.mkdir()
        trava = mundo / "session.lock"
        trava.write_bytes(b"\xde\xad\xbe\xef")

        refem = subprocess.Popen(
            [sys.executable, "-I", "-c", PRENDE_LOCK, str(trava)],
            stdout=subprocess.PIPE,
        )
        self.addCleanup(refem.stdout.close)
        self.addCleanup(refem.wait)
        self.addCleanup(refem.terminate)

        # Espera o filho confirmar que o lock já está nas mãos dele.
        self.assertEqual(refem.stdout.readline(), b"preso\n")

        with self.assertRaises(RuntimeEmUso):
            garantir_parado(self.servidor)

    def test_libera_depois_que_o_lock_sai(self):
        """Sem isto, a trava poderia ficar presa achando que o mundo segue em uso."""
        mundo = self.servidor / "world"
        mundo.mkdir()
        trava = mundo / "session.lock"
        trava.write_bytes(b"\xde\xad\xbe\xef")

        with trava.open("r+b") as fluxo:
            fcntl.lockf(fluxo, fcntl.LOCK_EX | fcntl.LOCK_NB)
            fcntl.lockf(fluxo, fcntl.LOCK_UN)

        garantir_parado(self.servidor)


if __name__ == "__main__":
    unittest.main()
