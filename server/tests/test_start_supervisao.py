"""O laço que mantém o servidor de pé — /restart, /stop e queda.

POR QUE ESTE TESTE EXISTE
-------------------------
O start.sh terminava em `exec java`: o script VIRAVA o servidor, e quando o
Java saía não sobrava shell nenhum para subir de novo. O /restart do console
derrubava o servidor e devolvia o terminal ao prompt — e o dono subia na mão
toda vez, sem nenhuma mensagem de erro para explicar.

A correção tem três partes, e as três falham caladas se quebrarem:

1. o Paper chama o restart-script com `sh`, que no Ubuntu é o dash — e o
   start.sh usa array e BASH_SOURCE. Sem a linha que reexecuta em bash, o
   script morre na primeira linha útil e ninguém vê, porque o Paper não lê a
   saída do processo filho;
2. /restart e /stop saem os DOIS com código 0. Sem o arquivo de sinal, ou o
   /stop vira um servidor que se recusa a desligar, ou o /restart não volta;
3. queda precisa subir de novo, mas não para sempre — senão o erro vira um
   laço silencioso enchendo o disco de log.

Cada item acima é um teste aqui embaixo. O "java" é falso: o que se mede é a
DECISÃO do laço, não o servidor.
"""
from __future__ import annotations

from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
START = SCRIPTS / "start.sh"
PEDIR_RESTART = SCRIPTS / "pedir-restart.sh"

MC_VERSION = "26.3"
PAPER_BUILD = "167"

# "java" de mentira: conta as chamadas e segue um roteiro de códigos de saída.
# ROTEIRO=0,0 quer dizer "primeira chamada sai 0, segunda sai 0".
JAVA_FALSO = """#!/bin/sh
echo x >> "$CONTADOR"
N=$(wc -l < "$CONTADOR" | tr -d ' ')
CODIGO=$(echo "$ROTEIRO" | cut -d, -f"$N")
[ -n "$CODIGO" ] || CODIGO=0
if [ "$N" = "$PEDE_RESTART_NA" ]; then
    if [ -n "$ATRASO_DO_SINAL" ]; then
        # Como o Paper faz de verdade: dispara e sai sem esperar.
        (sleep "$ATRASO_DO_SINAL"; sh "$PEDIR_RESTART" >/dev/null) &
    else
        sh "$PEDIR_RESTART" >/dev/null
    fi
fi
exit "$CODIGO"
"""


class TestSupervisao(unittest.TestCase):

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)

        self.servidor = Path(self.temp.name) / "minecraft"
        (self.servidor / "scripts").mkdir(parents=True)
        (self.servidor / f"paper-{MC_VERSION}-{PAPER_BUILD}.jar").write_text("jar falso")

        # O pedir-restart.sh real: o teste não reescreve a regra que mede.
        shutil.copy(PEDIR_RESTART, self.servidor / "scripts" / "pedir-restart.sh")

        self.bin = Path(self.temp.name) / "bin"
        self.bin.mkdir()
        java = self.bin / "java"
        java.write_text(JAVA_FALSO)
        java.chmod(0o755)

        self.contador = Path(self.temp.name) / "chamadas"
        self.contador.write_text("")

    def rodar(self, roteiro="0", pede_restart_na="", interpretador="bash",
              atraso_do_sinal="", tentativas_sinal="1", pausa_sinal="0.05"):
        ambiente = dict(os.environ)
        ambiente.update({
            "PATH": f"{self.bin}:{os.environ['PATH']}",
            "SERVER_DIR": str(self.servidor),
            "MC_VERSION": MC_VERSION,
            "PAPER_BUILD": PAPER_BUILD,
            "RAM": "1G",
            "ESPERA_RESTART": "0",
            "ESPERA_QUEDA": "0",
            "CONTADOR": str(self.contador),
            "ROTEIRO": roteiro,
            "PEDE_RESTART_NA": pede_restart_na,
            "ATRASO_DO_SINAL": atraso_do_sinal,
            "TENTATIVAS_SINAL": tentativas_sinal,
            "PAUSA_SINAL": pausa_sinal,
            "PEDIR_RESTART": str(self.servidor / "scripts" / "pedir-restart.sh"),
        })

        return subprocess.run([interpretador, str(START)], env=ambiente,
                              capture_output=True, text=True, timeout=60)

    def chamadas(self):
        return len(self.contador.read_text().split())

    def test_stop_encerra_de_vez(self):
        # /stop sai com 0 e sem sinal. Subir de novo aqui faria o servidor
        # se recusar a desligar — o oposto do que o dono pediu.
        saida = self.rodar(roteiro="0")
        self.assertEqual(0, saida.returncode, saida.stderr)
        self.assertEqual(1, self.chamadas())

    def test_restart_sobe_de_novo_sozinho(self):
        # Primeira chamada pede restart e sai 0; a segunda é um /stop.
        saida = self.rodar(roteiro="0,0", pede_restart_na="1")
        self.assertEqual(0, saida.returncode, saida.stderr)
        self.assertEqual(2, self.chamadas(), "o servidor não subiu de novo")
        self.assertIn("Reinício pedido", saida.stdout)

    def test_restart_vence_a_corrida_com_o_shutdown_hook(self):
        # O Paper dispara o pedir-restart.sh de dentro de um shutdown hook e
        # NÃO espera ele terminar. Quem perde a corrida é o laço: ele acorda
        # do `wait`, olha para o sinal antes de o arquivo existir e desliga
        # como se fosse /stop. Intermitente, sem erro nenhum na tela.
        saida = self.rodar(roteiro="0,0", pede_restart_na="1",
                           atraso_do_sinal="0.4", tentativas_sinal="30",
                           pausa_sinal="0.1")
        self.assertEqual(0, saida.returncode, saida.stderr)
        self.assertEqual(2, self.chamadas(),
                         "o laço desistiu antes de o sinal chegar")

    def test_espera_pelo_sinal_tem_fim(self):
        # E a espera não pode virar trava: /stop é sinal que nunca chega.
        saida = self.rodar(roteiro="0", tentativas_sinal="3", pausa_sinal="0.05")
        self.assertEqual(0, saida.returncode, saida.stderr)
        self.assertEqual(1, self.chamadas())

    def test_sinal_de_restart_nao_sobra_para_a_proxima(self):
        # Se o arquivo de sinal ficasse para trás, o /stop seguinte viraria
        # um restart — e o servidor nunca mais desligaria.
        self.rodar(roteiro="0,0", pede_restart_na="1")
        self.assertFalse((self.servidor / ".reiniciar").exists())

    def test_queda_sobe_de_novo_mas_nao_para_sempre(self):
        saida = self.rodar(roteiro="1,1,1,1,1,1")
        self.assertNotEqual(0, saida.returncode)
        self.assertEqual(3, self.chamadas(),
                         "o laço tinha de desistir na terceira queda")
        self.assertIn("caiu", saida.stderr)

    def test_queda_unica_volta_e_segue(self):
        # Caiu uma vez, voltou, e na volta foi desligado no /stop.
        saida = self.rodar(roteiro="1,0")
        self.assertEqual(0, saida.returncode, saida.stderr)
        self.assertEqual(2, self.chamadas())

    def test_funciona_quando_chamado_com_sh(self):
        # É assim que o Paper chama o restart-script. Sem a reexecução em
        # bash, o dash morre em BASH_SOURCE e ninguém vê o erro.
        saida = self.rodar(roteiro="0", interpretador="sh")
        self.assertEqual(0, saida.returncode, saida.stderr)
        self.assertEqual(1, self.chamadas())

    def test_pedir_restart_escreve_o_sinal_no_lugar_certo(self):
        # O Paper roda o script a partir da pasta do servidor; o sinal tem de
        # cair na raiz do runtime, e não dentro de scripts/.
        subprocess.run(["sh", "scripts/pedir-restart.sh"], cwd=self.servidor,
                       check=True, capture_output=True)
        self.assertTrue((self.servidor / ".reiniciar").exists())
