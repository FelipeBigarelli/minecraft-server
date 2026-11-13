package codes.biga.bigacore;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Um leitor de NBT, que é o formato em que o Minecraft guarda tudo.
 *
 * <h2>Por que existe, se o servidor já sabe ler NBT</h2>
 *
 * Sabe, e não empresta. A leitura de NBT do Paper mora em classes internas
 * que mudam de pacote a cada versão, e um plugin que as chama passa a quebrar
 * em toda atualização — justamente o tipo de dependência que este servidor
 * não tem em lugar nenhum. O formato, por outro lado, é pequeno e está
 * congelado desde 2011: treze tipos de etiqueta, cada um com um tamanho fixo
 * ou um tamanho escrito na frente. Cabe num arquivo.
 *
 * <h2>O que ele lê</h2>
 *
 * Arquivos de ESTRUTURA — os que o bloco de estrutura do jogo salva, e os que
 * vêm dentro de um mapa baixado. São sempre um compound na raiz, com ou sem
 * gzip por cima; o cabeçalho de gzip é reconhecido pelos dois primeiros
 * bytes, então o mesmo método serve para os dois casos.
 *
 * <h2>O que ele devolve</h2>
 *
 * Java puro: {@link Map} para compound, {@link List} para lista e vetor,
 * {@code String}, e as caixas de número. Nada de classe própria para cada
 * etiqueta. Quem lê uma estrutura quer três campos de dentro dela, não um
 * modelo de objetos do formato inteiro, e um mapa é o que deixa esses três
 * campos a uma linha de distância.
 *
 * <h2>O limite de tamanho</h2>
 *
 * Um arquivo de estrutura de um mapa de minijogo tem alguns megabytes. Um
 * arquivo de região tem centenas, e um arquivo corrompido pode dizer que uma
 * lista tem dois bilhões de itens. {@link #MAXIMO_DE_ITENS} corta a leitura
 * antes de a memória acabar: é melhor um erro dizendo que o arquivo não serve
 * do que um servidor que morre tentando abri-lo.
 */
public final class Nbt {

    private Nbt() {
    }

    public static final int FIM = 0;
    public static final int BYTE = 1;
    public static final int CURTO = 2;
    public static final int INTEIRO = 3;
    public static final int LONGO = 4;
    public static final int FLUTUANTE = 5;
    public static final int DOBRO = 6;
    public static final int VETOR_DE_BYTE = 7;
    public static final int TEXTO = 8;
    public static final int LISTA = 9;
    public static final int COMPOSTO = 10;
    public static final int VETOR_DE_INTEIRO = 11;
    public static final int VETOR_DE_LONGO = 12;

    /** Teto de itens de uma lista ou vetor, contra arquivo corrompido. */
    public static final int MAXIMO_DE_ITENS = 1 << 24;

    /** Teto de profundidade, contra arquivo que se aninha sem parar. */
    public static final int MAXIMO_DE_FUNDURA = 512;

    /**
     * Lê um arquivo inteiro, com ou sem gzip.
     *
     * @return o compound da raiz
     */
    public static Map<String, Object> ler(File arquivo) throws IOException {
        try (InputStream cru = new BufferedInputStream(
                new FileInputStream(arquivo), 1 << 16)) {
            cru.mark(2);
            int um = cru.read();
            int dois = cru.read();
            cru.reset();
            boolean comprimido = um == 0x1f && dois == 0x8b;
            try (DataInputStream dados = new DataInputStream(comprimido
                    ? new GZIPInputStream(cru, 1 << 16)
                    : cru)) {
                return lerRaiz(dados);
            }
        }
    }

    /** Lê de um fluxo já aberto: a raiz tem de ser um compound. */
    public static Map<String, Object> lerRaiz(DataInputStream dados)
            throws IOException {
        int tipo = dados.readUnsignedByte();
        if (tipo != COMPOSTO) {
            throw new IOException("a raiz do NBT é do tipo " + tipo
                    + " e tinha de ser um compound (" + COMPOSTO + ")");
        }
        dados.readUTF();
        return lerComposto(dados, 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> lerComposto(DataInputStream dados,
                                                   int fundura)
            throws IOException {
        fundo(fundura);
        Map<String, Object> saida = new LinkedHashMap<>();
        while (true) {
            int tipo = dados.readUnsignedByte();
            if (tipo == FIM) {
                return saida;
            }
            String nome = dados.readUTF();
            saida.put(nome, valor(dados, tipo, fundura + 1));
        }
    }

    private static Object valor(DataInputStream dados, int tipo, int fundura)
            throws IOException {
        switch (tipo) {
            case BYTE:
                return dados.readByte();
            case CURTO:
                return dados.readShort();
            case INTEIRO:
                return dados.readInt();
            case LONGO:
                return dados.readLong();
            case FLUTUANTE:
                return dados.readFloat();
            case DOBRO:
                return dados.readDouble();
            case VETOR_DE_BYTE: {
                int quantos = quantos(dados.readInt(), 1);
                byte[] vetor = new byte[quantos];
                dados.readFully(vetor);
                return vetor;
            }
            case TEXTO:
                return dados.readUTF();
            case LISTA: {
                int interno = dados.readUnsignedByte();
                int quantos = quantos(dados.readInt(), 1);
                List<Object> lista = new ArrayList<>(Math.min(quantos, 1024));
                for (int k = 0; k < quantos; k++) {
                    lista.add(valor(dados, interno, fundura + 1));
                }
                return lista;
            }
            case COMPOSTO:
                return lerComposto(dados, fundura);
            case VETOR_DE_INTEIRO: {
                int quantos = quantos(dados.readInt(), 4);
                int[] vetor = new int[quantos];
                for (int k = 0; k < quantos; k++) {
                    vetor[k] = dados.readInt();
                }
                return vetor;
            }
            case VETOR_DE_LONGO: {
                int quantos = quantos(dados.readInt(), 8);
                long[] vetor = new long[quantos];
                for (int k = 0; k < quantos; k++) {
                    vetor[k] = dados.readLong();
                }
                return vetor;
            }
            default:
                throw new IOException("etiqueta NBT desconhecida: " + tipo);
        }
    }

    private static void fundo(int fundura) throws IOException {
        if (fundura > MAXIMO_DE_FUNDURA) {
            throw new IOException("o NBT se aninha mais de "
                    + MAXIMO_DE_FUNDURA + " vezes: o arquivo não é confiável");
        }
    }

    /** O tamanho declarado de uma lista, recusado quando é absurdo. */
    static int quantos(int declarado, int bytesPorItem) throws IOException {
        if (declarado < 0) {
            return 0;
        }
        if (declarado > MAXIMO_DE_ITENS) {
            throw new IOException("o NBT declara " + declarado + " itens de "
                    + bytesPorItem + " byte(s): acima do teto de "
                    + MAXIMO_DE_ITENS + ", o arquivo está corrompido ou não é "
                    + "uma estrutura");
        }
        return declarado;
    }

    // ------------------------------------------------------------------
    //  Atalhos de leitura, para quem usa não precisar converter à mão
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> composto(Object qual) {
        return qual instanceof Map ? (Map<String, Object>) qual : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> lista(Object qual) {
        return qual instanceof List ? (List<Object>) qual : List.of();
    }

    public static String texto(Object qual, String padrao) {
        return qual instanceof String ? (String) qual : padrao;
    }

    public static int inteiro(Object qual, int padrao) {
        return qual instanceof Number ? ((Number) qual).intValue() : padrao;
    }

    public static double dobro(Object qual, double padrao) {
        return qual instanceof Number ? ((Number) qual).doubleValue() : padrao;
    }

    /** Os três primeiros números de uma lista, para pos e size. */
    public static int[] trio(Object qual) {
        List<Object> lista = lista(qual);
        if (lista.size() < 3) {
            return null;
        }
        return new int[]{inteiro(lista.get(0), 0), inteiro(lista.get(1), 0),
                inteiro(lista.get(2), 0)};
    }

    /** Os três primeiros números de uma lista de double, para a pos de entidade. */
    public static double[] trioDobro(Object qual) {
        List<Object> lista = lista(qual);
        if (lista.size() < 3) {
            return null;
        }
        return new double[]{dobro(lista.get(0), 0), dobro(lista.get(1), 0),
                dobro(lista.get(2), 0)};
    }
}
