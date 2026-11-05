# -*- coding: utf-8 -*-
"""Gera o schematic da Biga Market."""
import os
import sys

# O nbtio.py mora ao lado deste arquivo. Apontar explicitamente deixa o
# script rodar de qualquer diretorio, e tambem com python3 -I.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtio

W = L = 35
MAXY = 18
AR = None

b = {}                      # (x,y,z) -> blockstate string
def put(x, y, z, estado):
    if 0 <= x < W and 0 <= y <= MAXY and 0 <= z < L:
        b[(x, y, z)] = estado

# ---------------- paleta ----------------
PEDRA   = "minecraft:polished_blackstone"
PEDRA_L = "minecraft:polished_blackstone_slab[type=bottom,waterlogged=false]"
QUARTZO = "minecraft:smooth_quartz"
Q_LAJE  = "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"
Q_CINZ  = "minecraft:chiseled_quartz_block"
Q_PIL   = "minecraft:quartz_pillar[axis=y]"
MADEIRA = "minecraft:stripped_birch_wood[axis=%s]"
VIDRO   = "minecraft:glass"
LANTERNA= "minecraft:lantern[hanging=%s,waterlogged=false]"
FROG    = "minecraft:ochre_froglight[axis=y]"
GRAMA   = "minecraft:grass_block[snowy=false]"
AGUA    = "minecraft:water[level=0]"
MARLUZ  = "minecraft:sea_lantern"
def q_esc(face, metade="bottom"):
    return ("minecraft:smooth_quartz_stairs[facing=%s,half=%s,shape=straight,"
            "waterlogged=false]" % (face, metade))
def placa(face):
    return "minecraft:oak_wall_sign[facing=%s,waterlogged=false]" % face

# ---------------- regioes ----------------
def dentro(x, z): return 1 <= x <= 33 and 1 <= z <= 33
def patio(x, z):  return 8 <= x <= 26 and 8 <= z <= 26
def arcada(x, z): return dentro(x, z) and not patio(x, z)
def muro(x, z):   return x in (0, 34) or z in (0, 34)

PIL = {7, 11, 15, 19, 23, 27}
def colunata(x, z):
    return (7 <= x <= 27 and 7 <= z <= 27) and (x in (7, 27) or z in (7, 27))
def pilar(x, z):
    if not colunata(x, z): return False
    if x in (7, 27) and z in (7, 27): return True
    if x in (7, 27): return z in PIL
    return x in PIL

# portao: abertura no muro oeste
PORTAO_Z = (16, 17, 18)
def no_portao(x, z): return x == 0 and z in PORTAO_Z
def passagem(x, z): return x in (0, 1) and z in PORTAO_Z

# ---------------- as 62 bancas ----------------
# Cada banca: balcao em y=1, placa em y=2, e o bloco da frente livre em y=2
# para o item flutuante do /biga loja vitrine caber.
BANCAS = []                                  # (x, z, face_da_placa)
for z in range(2, 33, 2):
    if z not in (16, 18):                    # as duas que o portao come
        BANCAS.append((1, z, "east"))
for z in range(2, 33, 2):
    BANCAS.append((33, z, "west"))
for x in range(2, 33, 2):
    BANCAS.append((x, 1, "south"))
for x in range(2, 33, 2):
    BANCAS.append((x, 33, "north"))
POS_BANCA = {(x, z) for x, z, _ in BANCAS}

# ---------------- y=0: piso ----------------
for x in range(W):
    for z in range(L):
        if muro(x, z):
            put(x, 0, z, PEDRA)
        elif patio(x, z):
            # cruz de quartzo ligando os quatro lados, resto gramado
            put(x, 0, z, QUARTZO if (16 <= x <= 18 or 16 <= z <= 18) else GRAMA)
        else:
            put(x, 0, z, QUARTZO)
# faixa escura rente a parede, dá moldura ao piso da arcada
for x in range(W):
    for z in range(L):
        if (arcada(x, z) and (x in (1, 33) or z in (1, 33))
                and (x, z) not in POS_BANCA):
            put(x, 0, z, PEDRA)
# soleira do portao
for z in PORTAO_Z:
    put(0, 0, z, PEDRA)

# chafariz 7x7 com obelisco de quartzo no centro do patio
for x in range(14, 21):
    for z in range(14, 21):
        if x in (14, 20) or z in (14, 20):
            put(x, 0, z, PEDRA)                 # bordo
        elif 16 <= x <= 18 and 16 <= z <= 18:
            put(x, 0, z, QUARTZO)               # base do obelisco
        else:
            put(x, 0, z, AGUA)
for x in (16, 18):
    for z in (16, 18):
        put(x, 1, z, MARLUZ)                    # luz na base
put(17, 1, 17, Q_PIL); put(17, 2, 17, Q_PIL)
put(17, 3, 17, Q_CINZ); put(17, 4, 17, Q_PIL)
put(17, 5, 17, MARLUZ); put(17, 6, 17, Q_LAJE)
# bancos de quartzo virados para o chafariz
# o 17 fica de fora: e o eixo que liga portao -> chafariz nos quatro lados
for x in (15, 16, 18, 19):
    put(x, 1, 13, q_esc("south")); put(x, 1, 21, q_esc("north"))
for z in (15, 16, 18, 19):
    put(13, 1, z, q_esc("east"));  put(21, 1, z, q_esc("west"))

# ---------------- muro externo ----------------
for x in range(W):
    for z in range(L):
        if not muro(x, z):
            continue
        canto = x in (0, 34) and z in (0, 34)
        # coordenada que anda ao longo deste trecho de muro
        t = z if x in (0, 34) else x
        pilastra = canto or t % 4 == 1
        for y in range(1, 7):
            if no_portao(x, z):
                continue
            if pilastra or y == 1:
                put(x, y, z, PEDRA)          # pilastra, e rodape em volta
            elif y in (4, 5):
                put(x, y, z, VIDRO)              # janela acima de cada banca
            else:
                put(x, y, z, QUARTZO)
        put(x, 7, z, PEDRA)                      # cornija


# arco do portao
for z in (16, 18):
    put(0, 4, z, q_esc("south" if z == 16 else "north", "top"))
for y in (5, 6):
    for z in PORTAO_Z:
        put(0, y, z, QUARTZO if y == 5 else PEDRA)

for z in (15, 19):
    put(1, 6, z, LANTERNA % "true")              # lanternas ladeando a entrada

# ---------------- bancas ----------------
for x, z, face in BANCAS:
    put(x, 1, z, MADEIRA % "y")                  # balcao
    put(x, 2, z, placa(face))                    # placa em branco
# divisorias entre bancas
for z in range(1, 34):
    for x in (1, 33):
        if arcada(x, z) and (x, z) not in POS_BANCA and not passagem(x, z):
            for y in (1, 2, 3):
                put(x, y, z, PEDRA)
for x in range(1, 34):
    for z in (1, 33):
        if arcada(x, z) and (x, z) not in POS_BANCA:
            for y in (1, 2, 3):
                put(x, y, z, PEDRA)

# ---------------- colunata ----------------
for x in range(W):
    for z in range(L):
        if not colunata(x, z):
            continue
        if pilar(x, z):
            put(x, 1, z, PEDRA)
            for y in range(2, 6):
                put(x, y, z, Q_PIL)
            put(x, 6, z, Q_CINZ)
        else:
            put(x, 6, z, QUARTZO)                # arquitrave
            if (x + z) % 4 == 0:
                put(x, 6, z, FROG)               # luz embutida na viga

# ---------------- teto da arcada (y=7) ----------------
for x in range(W):
    for z in range(L):
        if arcada(x, z):
            viga = (x % 4 == 1) or (z % 4 == 1)
            put(x, 7, z, (MADEIRA % "y") if viga else QUARTZO)
# lanterna pendurada no corredor, a cada 4
for x in range(W):
    for z in range(L):
        if (arcada(x, z) and x % 4 == 3 and z % 4 == 3
                and not colunata(x, z) and (x, z) not in POS_BANCA):
            put(x, 6, z, LANTERNA % "true")
# parapeito do terraco e guarda-corpo do patio
for x in range(W):
    for z in range(L):
        if colunata(x, z):
            put(x, 8, z, Q_LAJE)

# lampiao nos quatro cantos da arcada
for (cx, cz) in ((4, 4), (4, 30), (30, 4), (30, 30)):
    for y in range(1, 4):
        put(cx, y, cz, Q_PIL)
    put(cx, 4, cz, MARLUZ)
    put(cx, 5, cz, PEDRA_L)



# ---------------- telhado em anel sobre a arcada ----------------
# Duas aguas: sobe da cornija ate a cumeeira no meio da arcada e desce de
# novo para a colunata. min() nos quatro lados resolve os rincoes sozinho.
TELHA   = "minecraft:polished_blackstone"
def p_esc(face, metade="bottom"):
    return ("minecraft:polished_blackstone_stairs[facing=%s,half=%s,"
            "shape=straight,waterlogged=false]" % (face, metade))
OPOSTO = {"west": "east", "east": "west", "north": "south", "south": "north"}

def borda_mais_perto(x, z):
    d = min(x, z, 34 - x, 34 - z)
    if x == d: return "west", d
    if z == d: return "north", d
    if 34 - x == d: return "east", d
    return "south", d

for x in range(W):
    for z in range(L):
        lado, d = borda_mais_perto(x, z)
        if d > 7:
            continue                              # patio: fica aberto pro ceu
        degrau = min(d, 7 - d)                    # 0,1,2,3,3,2,1,0
        topo = 8 + degrau
        for y in range(8, topo):
            put(x, y, z, TELHA)
        if degrau == 3:
            put(x, topo, z, TELHA)                # cumeeira, 2 de largura
        else:
            face = lado if d < 3 else OPOSTO[lado]
            put(x, topo, z, p_esc(face))

# ---------------- frontao do portao ----------------
# Empena sobre a entrada: da verticalidade sem roubar nenhuma banca.
ALTURA_FRONTAO = {17: 14, 16: 13, 18: 13, 15: 12, 19: 12,
                  14: 11, 20: 11, 13: 10, 21: 10}
for z, topo in ALTURA_FRONTAO.items():
    for x in (0, 1):
        for y in range(8, topo):
            put(x, y, z, QUARTZO if x == 0 else TELHA)
        put(x, topo, z, PEDRA)
    if z in (13, 21):
        put(0, topo + 1, z, "minecraft:lantern[hanging=false,waterlogged=false]")
put(0, 15, 17, Q_CINZ)
put(0, 16, 17, "minecraft:sea_lantern")

# ---------------- patio: arvores e flores ----------------
FOLHA = "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]"
TRONCO = "minecraft:oak_log[axis=y]"
def arvore(cx, cz):
    for y in range(1, 6):
        put(cx, y, cz, TRONCO)
    for y, r in ((4, 2), (5, 2), (6, 1)):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) == r and abs(dz) == r:
                    continue
                if dx == 0 and dz == 0 and y < 6:
                    continue
                put(cx + dx, y, cz + dz, FOLHA)
    put(cx, 7, cz, FOLHA)

for (cx, cz) in ((11, 11), (11, 23), (23, 11), (23, 23)):
    arvore(cx, cz)

FLORES = ["minecraft:poppy", "minecraft:dandelion", "minecraft:azure_bluet",
          "minecraft:cornflower"]
pontos = [(9, 14), (9, 20), (25, 14), (25, 20), (14, 9), (20, 9),
          (14, 25), (20, 25), (10, 17), (24, 17), (17, 10), (17, 24)]
for n, (fx, fz) in enumerate(pontos):
    if b.get((fx, 0, fz), "").startswith("minecraft:grass_block"):
        put(fx, 1, fz, FLORES[n % len(FLORES)])

# ---------------- grava ----------------
alturas = max(y for (_, y, _) in b)
H = alturas + 1
paleta, ordem = {}, []
def idx(est):
    if est not in paleta:
        paleta[est] = len(ordem); ordem.append(est)
    return paleta[est]
AR_I = idx("minecraft:air")

dados = bytearray()
for y in range(H):
    for z in range(L):
        for x in range(W):
            dados += nbtio.varint(idx(b.get((x, y, z), "minecraft:air")))

placas_be = []
for x, z, face in BANCAS:
    # Texto PURO, nao JSON: o MC 26.3 guarda as linhas da placa como string
    # simples. Gravar '""' aqui escreveria duas aspas visiveis em cada linha, e
    # a placa deixaria de contar como "em branco" para o /biga loja placas.
    vazio = {"color": "black", "has_glowing_text": nbtio.Byte(0),
             "messages": nbtio.List(8, ["", "", "", ""])}
    placas_be.append({
        "Id": "minecraft:sign",
        "Pos": nbtio.IntArray([x, 2, z]),
        "Data": {"id": "minecraft:sign", "is_waxed": nbtio.Byte(0),
                 "components": {},
                 "front_text": dict(vazio), "back_text": dict(vazio)},
    })

root = {"Schematic": {
    "Version": nbtio.Int(3),
    "DataVersion": nbtio.Int(5023),
    "Width": nbtio.Short(W), "Height": nbtio.Short(H), "Length": nbtio.Short(L),
    "Offset": nbtio.IntArray([0, -1, 0]),
    "Blocks": {
        "Palette": {est: nbtio.Int(i) for est, i in paleta.items()},
        "Data": nbtio.ByteArray(dados),
        "BlockEntities": nbtio.List(10, placas_be),
    },
}}
destino = sys.argv[1]
nbtio.gravar(destino, "", root)

print("gravado: %s" % destino)
print("dimensao: %d x %d x %d  (mundo Y 64..%d)" % (W, H, L, 64 + H - 1))
print("blocos solidos: %d   paleta: %d estados" % (len(b), len(ordem)))
print("placas (bancas): %d" % len(BANCAS))
