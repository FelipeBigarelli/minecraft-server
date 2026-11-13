package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A leitura de mapa pronto, conferida sem servidor e sem mapa.
 *
 * <h2>Por que o teste escreve o arquivo que vai ler</h2>
 *
 * Porque o mapa de verdade não mora no repositório — ele é de outra pessoa, e
 * o repositório é público. Então o teste monta um NBT à mão, byte por byte,
 * no formato do bloco de estrutura, e confere que a leitura devolve o que foi
 * escrito. É o mesmo caminho que o mapa baixado percorre, com a vantagem de o
 * arquivo ser pequeno o bastante para caber lido na tela quando quebrar.
 *
 * <p>{@link #oMapaDeVerdadeAbre} é o complemento: quando os arquivos baixados
 * estiverem no lugar, ele abre um de verdade; quando não estiverem, ele se
 * cala em vez de reprovar o build de quem não os tem.
 */
class EstruturaDeMapaTest {

    // ------------------------------------------------------------------
    //  Um escritor de NBT, só para este teste
    // ------------------------------------------------------------------

    private static void texto(DataOutputStream saida, String qual)
            throws IOException {
        saida.writeUTF(qual);
    }

    private static void etiqueta(DataOutputStream saida, int tipo, String nome)
            throws IOException {
        saida.writeByte(tipo);
        texto(saida, nome);
    }

    /** {@code pos:[a,b,c]} — a lista de inteiros que o formato usa. */
    private static void trio(DataOutputStream saida, String nome, int a, int b,
                             int c) throws IOException {
        etiqueta(saida, Nbt.LISTA, nome);
        saida.writeByte(Nbt.INTEIRO);
        saida.writeInt(3);
        saida.writeInt(a);
        saida.writeInt(b);
        saida.writeInt(c);
    }

    private static void trioDobro(DataOutputStream saida, String nome,
                                  double a, double b, double c)
            throws IOException {
        etiqueta(saida, Nbt.LISTA, nome);
        saida.writeByte(Nbt.DOBRO);
        saida.writeInt(3);
        saida.writeDouble(a);
        saida.writeDouble(b);
        saida.writeDouble(c);
    }

    private record BlocoCru(int x, int y, int z, int estado) {
    }

    private record MarcoCru(double x, double y, double z, List<String> tags) {
    }

    /**
     * Escreve uma estrutura no formato do bloco de estrutura.
     *
     * @param paleta nomes de bloco, com ou sem propriedades no segundo mapa
     */
    private static byte[] estrutura(List<String> paleta,
                                    List<Map<String, String>> propriedades,
                                    List<BlocoCru> blocos,
                                    List<MarcoCru> marcos,
                                    boolean comGzip) throws IOException {
        ByteArrayOutputStream bruto = new ByteArrayOutputStream();
        DataOutputStream saida = new DataOutputStream(comGzip
                ? new GZIPOutputStream(bruto) : bruto);

        saida.writeByte(Nbt.COMPOSTO);
        texto(saida, "");

        trio(saida, "size", 48, 48, 48);

        etiqueta(saida, Nbt.INTEIRO, "DataVersion");
        saida.writeInt(4325);

        etiqueta(saida, Nbt.LISTA, "palette");
        saida.writeByte(Nbt.COMPOSTO);
        saida.writeInt(paleta.size());
        for (int k = 0; k < paleta.size(); k++) {
            etiqueta(saida, Nbt.TEXTO, "Name");
            texto(saida, paleta.get(k));
            Map<String, String> props = k < propriedades.size()
                    ? propriedades.get(k) : Map.of();
            if (!props.isEmpty()) {
                etiqueta(saida, Nbt.COMPOSTO, "Properties");
                for (Map.Entry<String, String> par : props.entrySet()) {
                    etiqueta(saida, Nbt.TEXTO, par.getKey());
                    texto(saida, par.getValue());
                }
                saida.writeByte(Nbt.FIM);
            }
            saida.writeByte(Nbt.FIM);
        }

        etiqueta(saida, Nbt.LISTA, "blocks");
        saida.writeByte(Nbt.COMPOSTO);
        saida.writeInt(blocos.size());
        for (BlocoCru bloco : blocos) {
            trio(saida, "pos", bloco.x(), bloco.y(), bloco.z());
            etiqueta(saida, Nbt.INTEIRO, "state");
            saida.writeInt(bloco.estado());
            saida.writeByte(Nbt.FIM);
        }

        etiqueta(saida, Nbt.LISTA, "entities");
        saida.writeByte(Nbt.COMPOSTO);
        saida.writeInt(marcos.size());
        for (MarcoCru marco : marcos) {
            trioDobro(saida, "pos", marco.x(), marco.y(), marco.z());
            etiqueta(saida, Nbt.COMPOSTO, "nbt");
            etiqueta(saida, Nbt.TEXTO, "id");
            texto(saida, "minecraft:marker");
            etiqueta(saida, Nbt.LISTA, "Tags");
            saida.writeByte(Nbt.TEXTO);
            saida.writeInt(marco.tags().size());
            for (String tag : marco.tags()) {
                texto(saida, tag);
            }
            saida.writeByte(Nbt.FIM);
            saida.writeByte(Nbt.FIM);
        }

        saida.writeByte(Nbt.FIM);
        saida.close();
        return bruto.toByteArray();
    }

    /** Uma peça simples: um bloco no canto e uma jaula. */
    private static byte[] pecaSimples(int bloco, double jaulaX, double jaulaZ,
                                      boolean comGzip) throws IOException {
        return estrutura(List.of("minecraft:stone", "minecraft:oak_stairs"),
                List.of(Map.of(), Map.of("facing", "north", "half", "bottom")),
                List.of(new BlocoCru(bloco, 1, bloco, 0),
                        new BlocoCru(bloco + 1, 1, bloco, 1)),
                List.of(new MarcoCru(jaulaX, 41.0, jaulaZ, List.of("cage"))),
                comGzip);
    }

    // ------------------------------------------------------------------
    //  A grade das peças
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a grade de três por três põe a peça do meio na origem")
    void aGradeTemMeio() {
        assertArray(new int[]{-47, 0, -47},
                EstruturaDeMapa.deslocamentoDaPeca(1, 3, 47));
        assertArray(new int[]{0, 0, -47},
                EstruturaDeMapa.deslocamentoDaPeca(2, 3, 47));
        assertArray(new int[]{47, 0, -47},
                EstruturaDeMapa.deslocamentoDaPeca(3, 3, 47));
        assertArray(new int[]{-47, 0, 0},
                EstruturaDeMapa.deslocamentoDaPeca(4, 3, 47));
        assertArray(new int[]{0, 0, 0},
                EstruturaDeMapa.deslocamentoDaPeca(5, 3, 47));
        assertArray(new int[]{47, 0, 47},
                EstruturaDeMapa.deslocamentoDaPeca(9, 3, 47));
    }

    @Test
    @DisplayName("uma grade de uma peça só não desloca nada")
    void aGradeDeUmaPeca() {
        assertArray(new int[]{0, 0, 0},
                EstruturaDeMapa.deslocamentoDaPeca(1, 1, 47));
    }

    @Test
    @DisplayName("nenhuma peça da grade fica em cima de outra")
    void aGradeNaoSeRepete() {
        for (int lado : new int[]{1, 3, 5}) {
            List<String> vistos = new ArrayList<>();
            for (int k = 1; k <= lado * lado; k++) {
                int[] onde = EstruturaDeMapa.deslocamentoDaPeca(k, lado, 47);
                String chave = onde[0] + "/" + onde[2];
                assertFalse(vistos.contains(chave), "a grade de " + lado
                        + " põe duas peças em " + chave);
                vistos.add(chave);
            }
        }
    }

    // ------------------------------------------------------------------
    //  A paleta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o estado sai no formato do jogo, com as propriedades em ordem")
    void oEstadoSaiEmTexto() {
        assertEquals("minecraft:stone", EstruturaDeMapa.estadoDoPaleta(
                Map.of("Name", "minecraft:stone")));
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom]",
                EstruturaDeMapa.estadoDoPaleta(Map.of(
                        "Name", "minecraft:oak_stairs",
                        "Properties", Map.of("half", "bottom",
                                "facing", "north"))));
        // A MESMA entrada escrita com as propriedades em outra ordem tem de
        // dar o MESMO texto: é o que permite guardar estado traduzido.
        assertEquals(
                EstruturaDeMapa.estadoDoPaleta(Map.of("Name", "minecraft:x",
                        "Properties", Map.of("a", "1", "b", "2"))),
                EstruturaDeMapa.estadoDoPaleta(Map.of("Name", "minecraft:x",
                        "Properties", Map.of("b", "2", "a", "1"))));
    }

    @Test
    @DisplayName("nome sem namespace ganha minecraft:, e nome vazio é descartado")
    void oNomeGanhaOEspaco() {
        assertEquals("minecraft:stone",
                EstruturaDeMapa.estadoDoPaleta(Map.of("Name", "stone")));
        assertEquals("", EstruturaDeMapa.estadoDoPaleta(Map.of("Name", "  ")));
        assertEquals("", EstruturaDeMapa.estadoDoPaleta(Map.of()));
    }

    // ------------------------------------------------------------------
    //  A leitura de arquivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("lê as nove peças e soma o deslocamento de cada uma")
    void leAsNovePecas(@TempDir Path pasta) throws IOException {
        for (int k = 1; k <= 9; k++) {
            Files.write(pasta.resolve("mapa-" + k + ".nbt"),
                    pecaSimples(k, k, k, true));
        }
        EstruturaDeMapa mapa = EstruturaDeMapa.ler(pasta.toFile(), "mapa", 3, 47);
        assertEquals(18, mapa.blocos().size(), "duas por peça, nove peças");
        assertEquals(9, mapa.marcos().size());

        // A peça 1 nasce no canto de trás à esquerda: o bloco dela está em
        // (1,1,1) dentro da peça e em (1-47, 1, 1-47) no mapa.
        assertTrue(mapa.blocos().contains(
                new EstruturaDeMapa.Bloco(1 - 47, 1, 1 - 47, "minecraft:stone")),
                "a peça 1 não foi deslocada");
        assertTrue(mapa.blocos().contains(
                new EstruturaDeMapa.Bloco(5, 1, 5, "minecraft:stone")),
                "a peça 5 não podia ter sido deslocada");
    }

    @Test
    @DisplayName("lê peça sem gzip também")
    void leSemGzip(@TempDir Path pasta) throws IOException {
        Files.write(pasta.resolve("mapa-1.nbt"), pecaSimples(1, 0, 0, false));
        EstruturaDeMapa mapa = EstruturaDeMapa.ler(pasta.toFile(), "mapa", 1, 47);
        assertEquals(2, mapa.blocos().size());
    }

    @Test
    @DisplayName("a propriedade do bloco atravessa a leitura")
    void aPropriedadeAtravessa(@TempDir Path pasta) throws IOException {
        Files.write(pasta.resolve("mapa-1.nbt"), pecaSimples(0, 0, 0, true));
        EstruturaDeMapa mapa = EstruturaDeMapa.ler(pasta.toFile(), "mapa", 1, 47);
        assertTrue(mapa.blocos().stream().anyMatch(bloco -> bloco.estado()
                        .equals("minecraft:oak_stairs[facing=north,half=bottom]")),
                "a escada perdeu a propriedade: " + mapa.blocos());
    }

    @Test
    @DisplayName("peça faltando não derruba o mapa, e nenhuma peça derruba")
    void aPecaQueFalta(@TempDir Path pasta) throws IOException {
        Files.write(pasta.resolve("mapa-5.nbt"), pecaSimples(0, 0, 0, true));
        EstruturaDeMapa mapa = EstruturaDeMapa.ler(pasta.toFile(), "mapa", 3, 47);
        assertEquals(2, mapa.blocos().size(), "a única peça presente tinha de "
                + "ser lida mesmo faltando oito");

        IOException erro = assertThrows(IOException.class, () ->
                EstruturaDeMapa.ler(pasta.toFile(), "outro", 3, 47));
        assertTrue(erro.getMessage().contains("nenhuma peça"), erro.getMessage());
    }

    @Test
    @DisplayName("arquivo que não é NBT dá erro com nome do arquivo")
    void oArquivoQueNaoEhNbt(@TempDir Path pasta) throws IOException {
        Files.writeString(pasta.resolve("mapa-1.nbt"), "isto aqui é um texto");
        IOException erro = assertThrows(IOException.class, () ->
                EstruturaDeMapa.ler(pasta.toFile(), "mapa", 1, 47));
        assertTrue(erro.getMessage().contains("mapa-1.nbt")
                        || erro.getMessage().contains("raiz"),
                erro.getMessage());
    }

    @Test
    @DisplayName("lista de tamanho absurdo é recusada antes de gastar memória")
    void aListaAbsurda() {
        IOException erro = assertThrows(IOException.class, () ->
                Nbt.quantos(Integer.MAX_VALUE, 8));
        assertTrue(erro.getMessage().contains("corrompido"), erro.getMessage());
        assertEquals(0, assertDoesNotThrowInt(() -> Nbt.quantos(-5, 4)));
    }

    // ------------------------------------------------------------------
    //  As medidas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o centro vem das jaulas, e não da caixa dos blocos")
    void oCentroVemDasJaulas() {
        // Oito jaulas num anel em volta de (10, 41, 20), e um bloco de enfeite
        // longe, que torceria a caixa.
        List<EstruturaDeMapa.Marco> jaulas = new ArrayList<>();
        int[][] anel = {{0, 0}, {20, 0}, {0, 40}, {20, 40},
                {10, -10}, {10, 50}, {-10, 20}, {30, 20}};
        for (int[] onde : anel) {
            jaulas.add(new EstruturaDeMapa.Marco(onde[0], 41, onde[1],
                    java.util.Set.of("cage")));
        }
        EstruturaDeMapa mapa = new EstruturaDeMapa(
                List.of(new EstruturaDeMapa.Bloco(500, 1, 500, "minecraft:stone")),
                jaulas);

        assertArray(new int[]{10, 41, 20}, mapa.centroPelosMarcos("cage"));
        assertNull(mapa.centroPelosMarcos("naoExiste"));
        assertEquals(8, mapa.comEtiqueta("cage").size());
        assertEquals(0, mapa.comEtiqueta("normalChest").size());
    }

    @Test
    @DisplayName("o deslocamento leva o centro para a origem e a jaula para a fiada")
    void oDeslocamentoCentraliza() {
        assertArray(new int[]{-22, 59, -23},
                EstruturaDeMapa.deslocamentoParaArena(new int[]{22, 41, 23}, 100));
        // Sem marcador, o mapa fica onde está em vez de voar para um canto.
        assertArray(new int[]{0, 0, 0},
                EstruturaDeMapa.deslocamentoParaArena(null, 100));
    }

    @Test
    @DisplayName("a caixa envolve todos os blocos, e some quando não há nenhum")
    void aCaixaEnvolve() {
        EstruturaDeMapa mapa = new EstruturaDeMapa(List.of(
                new EstruturaDeMapa.Bloco(-3, 2, 7, "minecraft:stone"),
                new EstruturaDeMapa.Bloco(10, -1, 0, "minecraft:stone")),
                List.of());
        assertArray(new int[]{-3, -1, 0, 10, 2, 7}, mapa.caixa());
        assertNull(new EstruturaDeMapa(List.of(), List.of()).caixa());
        assertTrue(new EstruturaDeMapa(List.of(), List.of()).vazio());
    }

    // ------------------------------------------------------------------
    //  O mapa de verdade, quando ele estiver no lugar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o mapa baixado de verdade abre, quando está instalado")
    void oMapaDeVerdadeAbre() throws IOException {
        File pasta = new File(System.getProperty("user.home"),
                "minecraft/plugins/BigaCore/mapas/sky-wars");
        assumeTrue(new File(pasta, "map1-1.nbt").isFile(),
                "os mapas baixados não estão instalados nesta máquina");

        EstruturaDeMapa mapa = EstruturaDeMapa.ler(pasta, "map1", 3, 47);
        assertTrue(mapa.blocos().size() > 1000, "o mapa veio com só "
                + mapa.blocos().size() + " blocos");
        assertEquals(8, mapa.comEtiqueta("cage").size(),
                "um Sky Wars de oito tem oito jaulas");
        assertTrue(mapa.comEtiqueta("normalChest").size() >= 8,
                "faltou baú de ilha no mapa");
        assertNotNull(mapa.centroPelosMarcos("cage"));
        for (EstruturaDeMapa.Bloco bloco : mapa.blocos()) {
            assertTrue(bloco.estado().startsWith("minecraft:"),
                    "estado sem namespace: " + bloco.estado());
        }
    }

    // ------------------------------------------------------------------

    private static void assertArray(int[] esperado, int[] veio) {
        assertNotNull(veio);
        assertEquals(java.util.Arrays.toString(esperado),
                java.util.Arrays.toString(veio));
    }

    private interface Conta {
        int vale() throws IOException;
    }

    private static int assertDoesNotThrowInt(Conta conta) {
        try {
            return conta.vale();
        } catch (IOException falhou) {
            throw new AssertionError(falhou);
        }
    }
}
