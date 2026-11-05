# -*- coding: utf-8 -*-
"""Escritor de NBT (formato Java/big-endian, gzipado).

Só o suficiente para gerar um schematic Sponge v3 — é o que o
gerar-bigamarket.py precisa. Não lê NBT, só escreve.

Por que existe: o NBT não guarda o tamanho dos números. Um 3 pode ser
TAG_Byte, TAG_Short, TAG_Int ou TAG_Long, e o jogo rejeita o schematic se
vier o tamanho errado. Então quem monta a árvore marca o tipo à mão, com
os embrulhos abaixo, em vez de deixar o int do Python decidir.

    nbtio.Int(3)              -> TAG_Int
    nbtio.Byte(0)             -> TAG_Byte
    nbtio.Short(35)           -> TAG_Short
    nbtio.IntArray([0, -1, 0])-> TAG_Int_Array
    nbtio.ByteArray(dados)    -> TAG_Byte_Array
    nbtio.List(8, ["", ""])   -> TAG_List de TAG_String (8 = id do elemento)
    {"a": ...}                -> TAG_Compound
    "texto"                   -> TAG_String

    nbtio.varint(n)           -> LEB128 sem sinal, usado no campo Blocks.Data
    nbtio.gravar(caminho, nome_da_raiz, dict)
"""

import gzip
import struct

# --- ids das tags, conforme a especificacao do NBT -----------
FIM_       = 0
BYTE       = 1
SHORT      = 2
INT        = 3
LONG       = 4
FLOAT      = 5
DOUBLE     = 6
BYTE_ARRAY = 7
STRING     = 8
LIST       = 9
COMPOUND   = 10
INT_ARRAY  = 11
LONG_ARRAY = 12


# --- embrulhos de tipo --------------------------------------
# Herdam de int/bytes/list para que o resto do codigo possa usa-los
# como numero ou sequencia normalmente.
class Byte(int):   pass
class Short(int):  pass
class Int(int):    pass
class Long(int):   pass
class Float(float):  pass
class Double(float): pass
class ByteArray(bytes): pass
class IntArray(list):   pass
class LongArray(list):  pass


class List(object):
    """TAG_List. O id do tipo do elemento e explicito porque uma lista
    vazia nao tem de onde deduzi-lo."""

    __slots__ = ("tipo", "itens")

    def __init__(self, tipo, itens):
        self.tipo = tipo
        self.itens = list(itens)


def varint(n):
    """LEB128 sem sinal. O campo Blocks.Data do Sponge v3 e uma
    sequencia desses, um por bloco, apontando para a paleta."""
    if n < 0:
        raise ValueError("varint nao aceita negativo: %r" % (n,))
    saida = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            saida.append(b | 0x80)
        else:
            saida.append(b)
            return bytes(saida)


def _tipo_de(v):
    # A ordem importa: bool e subclasse de int, ByteArray de bytes,
    # IntArray/LongArray de list. O especifico vem antes do geral.
    if isinstance(v, Byte):       return BYTE
    if isinstance(v, Short):      return SHORT
    if isinstance(v, Int):        return INT
    if isinstance(v, Long):       return LONG
    if isinstance(v, Float):      return FLOAT
    if isinstance(v, Double):     return DOUBLE
    if isinstance(v, ByteArray):  return BYTE_ARRAY
    if isinstance(v, IntArray):   return INT_ARRAY
    if isinstance(v, LongArray):  return LONG_ARRAY
    if isinstance(v, List):       return LIST
    if isinstance(v, dict):       return COMPOUND
    if isinstance(v, str):        return STRING
    if isinstance(v, bool):       return BYTE
    if isinstance(v, (bytes, bytearray)): return BYTE_ARRAY
    if isinstance(v, int):        return INT
    if isinstance(v, float):      return DOUBLE
    if isinstance(v, list):
        # Deliberado: uma lista crua nao diz o tipo dos elementos, e
        # errar isso gera um arquivo que o jogo recusa sem explicar.
        raise TypeError("lista crua e ambigua; use nbtio.List(tipo, itens) "
                        "ou IntArray/LongArray")
    raise TypeError("sem tag NBT para %r (%s)" % (v, type(v).__name__))


def _texto(s):
    bruto = s.encode("utf-8")
    if len(bruto) > 0xFFFF:
        raise ValueError("string NBT acima de 65535 bytes")
    return struct.pack(">H", len(bruto)) + bruto


def _carga(v, tipo, saida):
    if tipo == BYTE:
        saida += struct.pack(">b", int(v))
    elif tipo == SHORT:
        saida += struct.pack(">h", int(v))
    elif tipo == INT:
        saida += struct.pack(">i", int(v))
    elif tipo == LONG:
        saida += struct.pack(">q", int(v))
    elif tipo == FLOAT:
        saida += struct.pack(">f", float(v))
    elif tipo == DOUBLE:
        saida += struct.pack(">d", float(v))
    elif tipo == BYTE_ARRAY:
        bruto = bytes(v)
        saida += struct.pack(">i", len(bruto)) + bruto
    elif tipo == STRING:
        saida += _texto(v)
    elif tipo == LIST:
        saida += struct.pack(">Bi", v.tipo, len(v.itens))
        for item in v.itens:
            _carga(item, v.tipo, saida)
    elif tipo == COMPOUND:
        for nome, valor in v.items():
            t = _tipo_de(valor)
            saida += struct.pack(">B", t) + _texto(nome)
            _carga(valor, t, saida)
        saida += struct.pack(">B", FIM_)
    elif tipo == INT_ARRAY:
        saida += struct.pack(">i", len(v))
        for n in v:
            saida += struct.pack(">i", int(n))
    elif tipo == LONG_ARRAY:
        saida += struct.pack(">i", len(v))
        for n in v:
            saida += struct.pack(">q", int(n))
    else:
        raise TypeError("tag %r nao implementada na escrita" % (tipo,))


def serializar(nome, raiz):
    """Devolve os bytes do NBT, sem compressao."""
    if not isinstance(raiz, dict):
        raise TypeError("a raiz do NBT tem de ser um compound (dict)")
    saida = bytearray()
    saida += struct.pack(">B", COMPOUND) + _texto(nome)
    _carga(raiz, COMPOUND, saida)
    return bytes(saida)


def gravar(caminho, nome, raiz):
    """Grava o NBT gzipado. mtime=0 para o arquivo sair identico a cada
    execucao, o que deixa o schematic comparavel entre commits."""
    bruto = serializar(nome, raiz)
    with open(caminho, "wb") as f:
        with gzip.GzipFile(fileobj=f, mode="wb", mtime=0) as g:
            g.write(bruto)
    return len(bruto)
