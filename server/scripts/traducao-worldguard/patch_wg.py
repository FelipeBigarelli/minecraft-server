# -*- coding: utf-8 -*-
"""Aplica a traducao pt-BR no jar do WorldGuard.

So troca Utf8 do constant pool apontado por CONSTANT_String — literal de
string que o codigo imprime. Nome de classe, descritor e nome de campo
tambem sao Utf8 mas nao sao apontados por CONSTANT_String, entao passam
intocados.

Invariantes verificadas ANTES de gravar. Se qualquer uma falhar, nada e
escrito:
  - mesma quantidade de \x01 e \x02 (marcadores de argumento do
    StringConcatFactory) no original e na traducao, ou os valores saem
    trocados de lugar na mensagem;
  - mesmos placeholders %s / %id% / %what% / %page%;
  - todo arquivo que nao e .class copiado byte a byte.
"""
import sys, os, re, zipfile, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import classpool
import trad_a, trad_b, trad_c

TRAD = {}
for mod in (trad_a, trad_b, trad_c):
    TRAD.update(mod.TRAD)

PLACEHOLDER = re.compile(r"%(?:s|d|id%|what%|page%)")

def checar():
    problemas = []
    for orig, novo in TRAD.items():
        for marcador in ("\x01", "\x02"):
            if orig.count(marcador) != novo.count(marcador):
                problemas.append("marcador %r: %d -> %d | %r"
                                 % (marcador, orig.count(marcador),
                                    novo.count(marcador), orig[:50]))
        a = sorted(PLACEHOLDER.findall(orig))
        b = sorted(PLACEHOLDER.findall(novo))
        if a != b:
            problemas.append("placeholder %s -> %s | %r" % (a, b, orig[:50]))
        if not novo.strip() and orig.strip():
            problemas.append("traducao vazia | %r" % orig[:50])
    return problemas

def main(origem, destino):
    problemas = checar()
    if problemas:
        print("ABORTADO — %d problema(s) na traducao:" % len(problemas))
        for p in problemas:
            print("   ", p)
        return 1

    zin = zipfile.ZipFile(origem)
    trocadas = 0
    classes_tocadas = 0
    vistas = set()

    tmp = destino + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            dados = zin.read(info.filename)
            if info.filename.endswith(".class"):
                cab, ent, resto = classpool.parse(dados)
                lits = classpool.literais(ent)
                trocas = {}
                for idx, texto in lits.items():
                    if texto in TRAD:
                        trocas[idx] = TRAD[texto]
                        vistas.add(texto)
                if trocas:
                    dados = classpool.rebuild(cab, ent, resto, trocas)
                    trocadas += len(trocas)
                    classes_tocadas += 1
            novo = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            novo.compress_type = info.compress_type
            novo.external_attr = info.external_attr
            zout.writestr(novo, dados)

    # confere que o jar novo abre e que todas as classes ainda parseiam
    zv = zipfile.ZipFile(tmp)
    ruins = 0
    for n in zv.namelist():
        if n.endswith(".class"):
            try:
                classpool.parse(zv.read(n))
            except Exception:
                ruins += 1
    if ruins:
        os.remove(tmp)
        print("ABORTADO — %d classe(s) ficaram ilegiveis." % ruins)
        return 1

    shutil.move(tmp, destino)
    print("classes alteradas : %d" % classes_tocadas)
    print("strings trocadas  : %d (ocorrencias)" % trocadas)
    print("frases distintas  : %d de %d do dicionario" % (len(vistas), len(TRAD)))
    nao_achadas = set(TRAD) - vistas
    if nao_achadas:
        print("NAO encontradas no jar (%d):" % len(nao_achadas))
        for s in sorted(nao_achadas):
            print("   ", repr(s)[:70])
    print("entradas no jar   : %d (original: %d)" % (len(zv.namelist()), len(zin.namelist())))
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
