"""Le e reescreve o constant pool de um .class.

So mexemos em entradas CONSTANT_Utf8 que sao apontadas por um
CONSTANT_String — isto e, literais de string que o codigo imprime.
Nome de classe, descritor de metodo e nome de campo tambem sao Utf8,
mas NAO sao apontados por CONSTANT_String, entao ficam intocados.

Referencias no class file sao por INDICE do pool, nunca por offset em
bytes, entao trocar o conteudo (e o tamanho) de um Utf8 e seguro desde
que o arquivo seja reescrito inteiro em ordem.
"""
import struct

# tag -> tamanho fixo em bytes do payload (None = tamanho variavel)
FIXO = {
    7: 2, 8: 2, 16: 2, 19: 2, 20: 2,          # Class, String, MethodType, Module, Package
    15: 3,                                     # MethodHandle
    9: 4, 10: 4, 11: 4, 12: 4, 17: 4, 18: 4,   # Fieldref/Methodref/.../Dynamic
    3: 4, 4: 4,                                # Integer, Float
    5: 8, 6: 8,                                # Long, Double
}

def parse(data):
    """Devolve (cabecalho, entradas, resto). entradas = [(tag, payload_bytes)]."""
    assert data[:4] == b"\xca\xfe\xba\xbe", "nao e um .class"
    pos = 8
    count = struct.unpack_from(">H", data, pos)[0]
    pos += 2
    entradas = []
    i = 1
    while i < count:
        tag = data[pos]
        ini = pos
        pos += 1
        if tag == 1:
            ln = struct.unpack_from(">H", data, pos)[0]
            pos += 2 + ln
        else:
            pos += FIXO[tag]
        entradas.append((tag, data[ini + 1:pos]))
        i += 2 if tag in (5, 6) else 1
    return data[:8], entradas, data[pos:]

def literais(entradas):
    """indice_utf8 -> texto, apenas dos Utf8 referenciados por CONSTANT_String."""
    # reconstroi o mapa de indices reais (Long/Double ocupam dois)
    idx, real = {}, 1
    for n, (tag, _) in enumerate(entradas):
        idx[real] = n
        real += 2 if tag in (5, 6) else 1
    alvo = {}
    for tag, payload in entradas:
        if tag == 8:
            ref = struct.unpack(">H", payload)[0]
            n = idx.get(ref)
            if n is not None and entradas[n][0] == 1:
                alvo[n] = entradas[n][1][2:].decode("utf-8", "replace")
    return alvo

def rebuild(cab, entradas, resto, trocas):
    """trocas: {indice_na_lista: novo_texto}."""
    out = bytearray(cab)
    count = 1
    for tag, _ in entradas:
        count += 2 if tag in (5, 6) else 1
    out += struct.pack(">H", count)
    for n, (tag, payload) in enumerate(entradas):
        out.append(tag)
        if tag == 1 and n in trocas:
            b = trocas[n].encode("utf-8")
            out += struct.pack(">H", len(b)) + b
        else:
            out += payload
    out += resto
    return bytes(out)
