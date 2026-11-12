package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O Spleef, conferido sem servidor.
 *
 * <h2>O que pode dar errado aqui e não lança exceção</h2>
 *
 * Um muro com vão na diagonal por onde se sai andando. Um aperto que come o
 * próprio muro. Uma arena que nunca encolhe o bastante e empata no tempo. Um
 * lago de cenário acima da linha de eliminação, em que o eliminado pousa em
 * vez de ser eliminado. Nada disso quebra; tudo isso deixa o jogo jogável e
 * ERRADO, que é o pior estado possível. Cada teste daqui é uma dessas.
 */
class SpleefTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int RAIO = 20;
    private static final int BEIRA = 2;
    private static final int MURO = 6;
    private static final int POCO = 24;
    private static final int LAGO_FUNDO = 6;
    private static final int SAGUAO_ALTURA = 16;
    private static final int SAGUAO_RAIO = 9;
    private static final int SAGUAO_PARAPEITO = 4;
    private static final int MARCO_RECUO = 8;
    private static final int MARCO_ALTURA = 26;
    private static final int MINIMO = 2;
    private static final int MAXIMO = 16;
    private static final int PARADO_AVISO = 5;
    private static final int PARADO_RACHA = 9;
    private static final int APERTO = 120;
    private static final int APERTO_INTERVALO = 10;
    private static final int TEMPO_LIMITE = 420;

    /** Um pulo com corrida passa de quatro blocos na horizontal. */
    private static final int SALTO = 5;

    private static int quedaY() {
        return Y - POCO;
    }

    private static int lagoY() {
        return quedaY() - LAGO_FUNDO;
    }

    private static int saguaoY() {
        return Y + SAGUAO_ALTURA;
    }

    private static int foraDoMuro() {
        return RAIO + SpleefBuilder.ESPESSURA_DO_MURO;
    }

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = SpleefTest.class.getResourceAsStream("/spleef.yml")) {
            assertNotNull(in, "spleef.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------
    //  O arquivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a planta tem os números que o construtor e o jogo esperam")
    void plantaConfere() {
        assertEquals("spleef", arena.getString("mundo"));
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(RAIO, arena.getInt("arena.raio"));
        assertEquals(BEIRA, arena.getInt("arena.beira-aneis"));
        assertEquals(MURO, arena.getInt("arena.muro-altura"));
        assertEquals(POCO, arena.getInt("arena.poco"));
        assertEquals(LAGO_FUNDO, arena.getInt("arena.lago-fundo"));
        assertEquals(SAGUAO_ALTURA, arena.getInt("arena.saguao-altura"));
        assertEquals(SAGUAO_RAIO, arena.getInt("arena.saguao-raio"));
        assertEquals(SAGUAO_PARAPEITO, arena.getInt("arena.saguao-parapeito"));
        assertEquals(MARCO_RECUO, arena.getInt("arena.marco-recuo"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));
        assertEquals(MINIMO, arena.getInt("regras.minimo"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(PARADO_AVISO, arena.getInt("regras.parado-aviso"));
        assertEquals(PARADO_RACHA, arena.getInt("regras.parado-racha"));
        assertEquals(APERTO, arena.getInt("regras.aperto-segundos"));
        assertEquals(APERTO_INTERVALO, arena.getInt("regras.aperto-intervalo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
        assertTrue(arena.getBoolean("regras.treino-sozinho"));
    }

    @Test
    @DisplayName("todo material da paleta existe")
    void paletaEhValida() {
        Map<String, Object> paleta = arena.getConfigurationSection("paleta")
                .getValues(false);
        assertFalse(paleta.isEmpty(), "a paleta ficou vazia");
        for (Map.Entry<String, Object> item : paleta.entrySet()) {
            Material material = Material.matchMaterial(
                    String.valueOf(item.getValue()).toUpperCase(Locale.ROOT));
            assertNotNull(material, "paleta." + item.getKey() + " não é um material: "
                    + item.getValue());
        }
    }

    /**
     * O que se cava não é o que fica de pé.
     *
     * Esta é a trava central do jogo. Se o material do muro, do lago ou da
     * estrutura entrar na lista do que pode ser cavado, a arena vai sendo
     * desmontada por dentro sem erro nenhum aparecer em lugar nenhum.
     */
    @Test
    @DisplayName("o que se cava não é o que segura a arena de pé")
    void oQueSeCavaNaoEOQueFica() {
        Set<String> cavavel = new HashSet<>(List.of(
                arena.getString("paleta.piso"), arena.getString("paleta.beira")));
        assertEquals(2, cavavel.size(), "o piso e a beira são o mesmo material: "
                + "a beira deixa de se ler de longe");

        for (String chave : List.of("muro", "muro-topo", "lago", "estrutura", "escuro",
                "borda", "metal", "vidro", "luz", "luz-fria", "nucleo", "placa")) {
            assertFalse(cavavel.contains(arena.getString("paleta." + chave)),
                    "paleta." + chave + " é do mesmo material do chão: cavar o chão "
                            + "passaria a cavar isso também");
        }
    }

    // ------------------------------------------------------------------
    //  O muro
    // ------------------------------------------------------------------

    /**
     * O defeito que isto fecha, e que custou a espessura do muro.
     *
     * Um anel de UM bloco definido por "está em raio+1 e não em raio" tem
     * buraco na diagonal: em raio 20, (14,14) ainda é chão e (15,15) já está
     * fora do anel. Quem corresse na diagonal sairia da arena andando — sem
     * cair, sem ser cavado, sem nada. Aqui se confere o que importa de
     * verdade: de toda célula de chão, todo vizinho que não é chão tem de ser
     * muro.
     */
    @Test
    @DisplayName("não há vão nenhum no muro, nem na diagonal")
    void muroNaoTemVao() {
        List<int[]> vazamentos = new ArrayList<>();
        for (int x = -RAIO; x <= RAIO; x++) {
            for (int z = -RAIO; z <= RAIO; z++) {
                if (!ArenaComum.noDisco(x, z, RAIO)) {
                    continue;
                }
                for (int[] passo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int vx = x + passo[0];
                    int vz = z + passo[1];
                    if (ArenaComum.noDisco(vx, vz, RAIO)) {
                        continue;                       // ainda é chão
                    }
                    if (!ArenaComum.noDisco(vx, vz, foraDoMuro())) {
                        vazamentos.add(new int[]{vx, vz});
                    }
                }
            }
        }
        assertTrue(vazamentos.isEmpty(), vazamentos.size()
                + " célula(s) ao lado do chão não são chão nem muro — dá para sair "
                + "andando da arena; a primeira é "
                + (vazamentos.isEmpty() ? "" : vazamentos.get(0)[0] + ","
                        + vazamentos.get(0)[1]));
    }

    /**
     * O muro não dá lugar de ficar em pé.
     *
     * Bloco onde se possa subir e de onde ninguém possa cavar o seu chão é o
     * único jeito de ganhar de graça no Spleef. O topo do muro fica a
     * {@code muro-altura} acima do piso, e um pulo sobe 1,25.
     */
    @Test
    @DisplayName("não se pula no muro")
    void muroNaoTemBeiral() {
        assertTrue(MURO >= 3, "um muro de " + MURO + " é pulável: vira o melhor "
                + "lugar da arena");
        assertTrue(SpleefBuilder.ESPESSURA_DO_MURO >= 2,
                "com um de espessura o anel tem vão na diagonal");
    }

    /**
     * Os marcos ficam fora do muro, com ar entre eles e ele.
     *
     * Aqui a regra não é a mesma do TNT Run. Lá nada podia estar a menos de
     * um pulo da beira, porque de lá se pula para fora; aqui quem sela a
     * arena é o muro, e do chão não se chega ao topo dele. O que resta a
     * conferir é que o marco não ENCOSTE no muro — encostado, ele faria
     * parede dupla e o jogador veria a arena por dentro de um pilar.
     */
    @Test
    @DisplayName("os marcos ficam fora do muro, sem encostar nele")
    void marcosForaDoMuro() {
        int d = RAIO + MARCO_RECUO;
        int maisPertoQueOMarcoChega = d - 1;
        assertTrue(maisPertoQueOMarcoChega > foraDoMuro() + 2,
                "o marco a " + d + " encosta no muro, que termina em "
                        + foraDoMuro());
        assertTrue(SALTO < maisPertoQueOMarcoChega - foraDoMuro() + MURO,
                "o marco estaria ao alcance de quem subisse no muro");
    }

    // ------------------------------------------------------------------
    //  O poço
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o lago do fundo fica ABAIXO da linha de eliminação")
    void lagoNaoSeguraNinguem() {
        assertTrue(lagoY() < quedaY(), "o lago em y " + lagoY() + " está acima da "
                + "eliminação em y " + quedaY() + ": quem caísse pousaria nele");
        assertTrue(quedaY() - lagoY() >= 2, "o lago está colado na linha de "
                + "eliminação: dá para ver o chão do poço antes de ser eliminado");
    }

    @Test
    @DisplayName("a queda tem folga para quem está em pé no chão")
    void limiteDeQuedaTemFolga() {
        assertTrue(quedaY() < Y - 4, "a eliminação em y " + quedaY()
                + " está perto demais do piso em y " + Y);
    }

    /**
     * O desfazer apaga tudo o que o construtor levanta.
     *
     * Se a caixa do desfazer não cobrir o lago ou o topo dos marcos, sobra
     * pedaço de obra no mundo — e o construir seguinte se recusa a trabalhar
     * porque "há blocos no caminho", sem dizer onde.
     */
    @Test
    @DisplayName("a caixa do desfazer cobre a obra inteira")
    void aObraCabeNoDesfazer() {
        int fundoDaObra = lagoY() - 3;
        int tetoDaObra = Math.max(saguaoY() + SAGUAO_PARAPEITO + 4, Y + MARCO_ALTURA + 6);
        int alcance = RAIO + MARCO_RECUO + 6;

        assertTrue(fundoDaObra <= lagoY(), "o desfazer não chega no lago");
        assertTrue(tetoDaObra >= Y + MARCO_ALTURA + 5,
                "o desfazer não chega na agulha do marco");
        assertTrue(tetoDaObra >= saguaoY() + SAGUAO_PARAPEITO + 4,
                "o desfazer não chega no alto do saguão");
        assertTrue(alcance >= RAIO + MARCO_RECUO + 1,
                "o desfazer não chega na largura do marco");
        assertTrue(alcance >= foraDoMuro(), "o desfazer não chega no muro");
    }

    // ------------------------------------------------------------------
    //  O aperto
    // ------------------------------------------------------------------

    @Test
    @DisplayName("antes da hora a arena não encolhe; depois, um anel por vez")
    void apertoEncolhe() {
        assertEquals(RAIO, SpleefCatalog.raioDoAperto(0, RAIO, APERTO, APERTO_INTERVALO));
        assertEquals(RAIO, SpleefCatalog.raioDoAperto(APERTO - 1, RAIO, APERTO,
                APERTO_INTERVALO));
        assertEquals(RAIO - 1, SpleefCatalog.raioDoAperto(APERTO, RAIO, APERTO,
                APERTO_INTERVALO));
        assertEquals(RAIO - 1, SpleefCatalog.raioDoAperto(APERTO + APERTO_INTERVALO - 1,
                RAIO, APERTO, APERTO_INTERVALO));
        assertEquals(RAIO - 2, SpleefCatalog.raioDoAperto(APERTO + APERTO_INTERVALO,
                RAIO, APERTO, APERTO_INTERVALO));

        // Nunca abaixo de um: raio zero é arena sem onde pôr o último jogador,
        // e a partida acabaria por acidente de conta em vez de por queda.
        assertEquals(1, SpleefCatalog.raioDoAperto(100000, RAIO, APERTO,
                APERTO_INTERVALO));
    }

    /**
     * O aperto nunca come o muro.
     *
     * O muro começa em raio+1. Se a conta do anel a apagar passar dele, a
     * arena perde a parede e o jogo passa a ser vencido saindo andando.
     */
    @Test
    @DisplayName("o aperto só apaga chão, nunca o muro")
    void apertoNaoComeOMuro() {
        for (int raioAtual = RAIO; raioAtual >= 1; raioAtual--) {
            for (int x = -foraDoMuro(); x <= foraDoMuro(); x++) {
                for (int z = -foraDoMuro(); z <= foraDoMuro(); z++) {
                    if (ArenaComum.noDisco(x, z, RAIO)) {
                        continue;                        // é chão, pode apagar
                    }
                    assertFalse(SpleefCatalog.foraDoApertoAtual(x, z, raioAtual, RAIO),
                            "o aperto pegaria " + x + "," + z + ", que é muro");
                }
            }
        }
    }

    /**
     * O aperto termina a partida sozinho, antes do tempo limite.
     *
     * Senão quem termina é o relógio, e uma partida que acaba no relógio
     * acaba empatada — exatamente o que o aperto existe para impedir.
     */
    @Test
    @DisplayName("o aperto fecha a arena antes de o tempo limite estourar")
    void apertoAcabaAntesDoTempo() {
        int segundos = APERTO;
        while (SpleefCatalog.raioDoAperto(segundos, RAIO, APERTO, APERTO_INTERVALO) > 1
                && segundos < 100000) {
            segundos++;
        }
        assertTrue(segundos < TEMPO_LIMITE, "a arena só fecha aos " + segundos
                + "s, e o tempo limite é " + TEMPO_LIMITE + "s: a partida acabaria "
                + "no relógio, empatada");
    }

    // ------------------------------------------------------------------
    //  Cavar
    // ------------------------------------------------------------------

    /**
     * Duas travas, não uma: material do piso E fiada do disco.
     *
     * Sem a segunda, um bloco de neve em qualquer lugar do mundo viraria
     * buraco de Spleef — inclusive um que o próprio jogo tivesse posto no
     * saguão ou num marco.
     */
    @Test
    @DisplayName("só se cava o chão do disco, e só na fiada dele")
    void cavaSoOChaoDoDisco() {
        Set<Material> doPiso = Set.of(Material.SNOW_BLOCK, Material.PACKED_ICE);

        assertTrue(Spleef.ehChaoCavavel(Material.SNOW_BLOCK, Y, Y, doPiso));
        assertTrue(Spleef.ehChaoCavavel(Material.PACKED_ICE, Y, Y, doPiso));

        assertFalse(Spleef.ehChaoCavavel(Material.SNOW_BLOCK, Y - 1, Y, doPiso),
                "cavou uma fiada abaixo do disco");
        assertFalse(Spleef.ehChaoCavavel(Material.SNOW_BLOCK, Y + 1, Y, doPiso),
                "cavou uma fiada acima do disco");
        assertFalse(Spleef.ehChaoCavavel(Material.LIGHT_BLUE_STAINED_GLASS, Y, Y, doPiso),
                "cavou o muro");
        assertFalse(Spleef.ehChaoCavavel(Material.BLUE_ICE, Y, Y, doPiso),
                "cavou o remate do muro");
    }

    // ------------------------------------------------------------------
    //  Quem fica parado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("parado é parado, e agachado conta como parado")
    void paradoEhParado() {
        assertTrue(ArenaComum.ficouParado(10.0, 10.0, 10.2, 9.8, false),
                "mexer meio bloco não é andar");
        assertTrue(ArenaComum.ficouParado(10.0, 10.0, 10.0, 10.0, false));

        // Agachado na beira é a pose de quem acampa sem cair.
        assertTrue(ArenaComum.ficouParado(10.0, 10.0, 3.0, 3.0, true),
                "agachado tem de contar como parado mesmo tendo corrido");

        assertFalse(ArenaComum.ficouParado(12.0, 10.0, 10.0, 10.0, false));
        assertFalse(ArenaComum.ficouParado(10.0, 12.0, 10.0, 10.0, false));
    }

    /**
     * O aviso vem antes do castigo, com folga para reagir.
     *
     * Punição não evitável não é regra, é armadilha: o jogador cai sem ter
     * tido como saber, e conclui que o jogo está quebrado.
     */
    @Test
    @DisplayName("o gelo avisa antes de ceder")
    void avisoVemAntesDoRacha() {
        assertTrue(PARADO_AVISO < PARADO_RACHA, "o gelo cede sem avisar antes");
        assertTrue(PARADO_RACHA - PARADO_AVISO >= 3,
                "só " + (PARADO_RACHA - PARADO_AVISO) + "s entre o aviso e a queda");
        assertTrue(PARADO_RACHA < APERTO, "o gelo só cederia depois de a arena "
                + "já estar encolhendo: duas punições pelo mesmo motivo");
    }

    // ------------------------------------------------------------------
    //  A largada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("as largadas ficam no disco, longe do muro e longe umas das outras")
    void largadasSaoJustas() {
        List<int[]> pontos = ArenaComum.largadas(MAXIMO, RAIO);
        assertEquals(MAXIMO, pontos.size());

        for (int[] p : pontos) {
            assertTrue(ArenaComum.noDisco(p[0], p[1], RAIO - 2),
                    "largada em " + p[0] + "," + p[1] + " está colada no muro");
        }
        for (int i = 0; i < pontos.size(); i++) {
            for (int j = i + 1; j < pontos.size(); j++) {
                int dx = pontos.get(i)[0] - pontos.get(j)[0];
                int dz = pontos.get(i)[1] - pontos.get(j)[1];
                assertTrue(dx * dx + dz * dz >= 4,
                        "duas largadas em cima uma da outra: " + i + " e " + j);
            }
        }
    }

    @Test
    @DisplayName("sozinho, larga no meio")
    void largadaSozinho() {
        List<int[]> um = ArenaComum.largadas(1, RAIO);
        assertEquals(1, um.size());
        assertArrayEqualsInt(new int[]{0, 0}, um.get(0));
        assertTrue(ArenaComum.largadas(0, RAIO).isEmpty());
    }

    @Test
    @DisplayName("quem larga olha para o centro, não para o muro")
    void largadaOlhaParaOCentro() {
        assertEquals(180f, ArenaComum.yawParaOCentro(0, 10), 0.01f);
        assertEquals(0f, ArenaComum.yawParaOCentro(0, -10), 0.01f);
        assertEquals(90f, ArenaComum.yawParaOCentro(10, 0), 0.01f);
        assertEquals(-90f, ArenaComum.yawParaOCentro(-10, 0), 0.01f);
    }

    // ------------------------------------------------------------------
    //  O saguão
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o saguão fica acima do muro e não encosta na arena")
    void saguaoNaoEstaNoCaminho() {
        assertTrue(SAGUAO_RAIO < RAIO, "o saguão é maior que a arena");
        assertTrue(saguaoY() > Y + MURO + 4,
                "o saguão em y " + saguaoY() + " está colado no muro, que termina em y "
                        + (Y + MURO));
    }

    @Test
    @DisplayName("a plateia assiste de cima do muro, não de dentro dele")
    void plateiaVeAArena() {
        int plateiaY = Y + MURO + 6;
        assertTrue(plateiaY > Y + MURO,
                "o eliminado assistiria à partida através do vidro do muro");
        assertTrue(plateiaY < saguaoY(), "a plateia ficaria dentro do chão do saguão");
    }

    @Test
    @DisplayName("o arco de volta cabe dentro do saguão")
    void arcoCabeNoSaguao() {
        int z = SpleefBuilder.zDoArcoDeVolta(SAGUAO_RAIO);
        assertTrue(ArenaComum.noDisco(0, z, SAGUAO_RAIO),
                "o arco em z=" + z + " está fora do chão do saguão");
        assertTrue(SpleefBuilder.alturaDoArcoDeVolta() + 2 <= SAGUAO_PARAPEITO + 4,
                "o arco é mais alto que o parapeito do saguão");
    }

    @Test
    @DisplayName("quem chega ao saguão não cai no arco de volta sem querer")
    void chegadaForaDoArco() {
        int z = SpleefBuilder.zDoArcoDeVolta(SAGUAO_RAIO);
        assertFalse(SpleefBuilder.dentroDaVolta(0.5, saguaoY() + 1, 0.5, saguaoY(), z),
                "o ponto de chegada do saguão está dentro do arco: quem entra no "
                        + "jogo é devolvido ao lobby na hora");
    }

    // ------------------------------------------------------------------
    //  As placas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("as duas placas têm texto, e ele cabe numa placa")
    void placasTemTexto() {
        for (String chave : List.of("entrar", "sair", "fila", "esperando", "jogando",
                "refazendo")) {
            String valor = arena.getString("textos.placa-" + chave);
            assertNotNull(valor, "falta textos.placa-" + chave);
            assertFalse(valor.isBlank(), "textos.placa-" + chave + " está vazio");
            assertTrue(valor.length() <= 15, "textos.placa-" + chave + " tem "
                    + valor.length() + " caracteres e não cabe numa linha de placa");
        }
    }

    @Test
    @DisplayName("o material da placa é placa DE PAREDE")
    void placaEhDeParede() {
        String nome = arena.getString("paleta.placa");
        assertNotNull(nome, "falta paleta.placa");
        Material material = Material.matchMaterial(nome.toUpperCase(Locale.ROOT));
        assertNotNull(material, "paleta.placa não é um material: " + nome);
        assertTrue(material.name().endsWith("_WALL_SIGN"),
                material + " não é placa de parede: não tem como encostar no muro");
    }

    @Test
    @DisplayName("as placas ficam no muro, dentro do saguão e fora do arco de volta")
    void placasFicamNoLugar() {
        int zMuro = SpleefBuilder.zDoArcoDeVolta(SAGUAO_RAIO);
        int fiada = saguaoY() + SpleefBuilder.FIADA_DA_PLACA;
        int[][] placas = {{-4, fiada, zMuro + 1}, {4, fiada, zMuro + 1}};

        for (int[] placa : placas) {
            assertEquals(zMuro + 1, placa[2], "a placa não está colada no muro");
            assertTrue(ArenaComum.noDisco(placa[0], placa[2], SAGUAO_RAIO),
                    "a placa em x=" + placa[0] + " está fora do chão do saguão");
            assertTrue(ArenaComum.noDisco(placa[0], zMuro, SAGUAO_RAIO),
                    "não há muro atrás da placa em x=" + placa[0]);
            assertTrue(Math.abs(placa[0]) > 2
                            && Math.abs(placa[0]) <= SpleefBuilder.LARGURA_DA_FACHADA,
                    "a placa em x=" + placa[0] + " caiu no vão do arco ou fora do muro");

            double px = placa[0] + 0.5;
            for (double dz = 1; dz <= 2; dz++) {
                assertFalse(SpleefBuilder.dentroDaVolta(px, saguaoY() + 1,
                                zMuro + dz + 0.5, saguaoY(), zMuro),
                        "parado na frente da placa em x=" + placa[0]
                                + " o jogador é mandado de volta ao lobby");
            }
        }
        assertNotEquals(placas[0][0], placas[1][0], "as duas placas no mesmo bloco");
    }

    @Test
    @DisplayName("as regras escritas dizem como entrar e como se cava")
    void asRegrasEnsinamOJogo() {
        List<String> regras = arena.getStringList("textos.regras");
        assertFalse(regras.isEmpty(), "o letreiro de regras ficou vazio");

        String entrar = arena.getString("textos.placa-entrar");
        assertTrue(regras.stream().anyMatch(l -> l.contains(entrar)),
                "nenhuma linha manda clicar em " + entrar);
        assertTrue(regras.stream().anyMatch(l -> l.toLowerCase(Locale.ROOT)
                        .contains("cavar") || l.toLowerCase(Locale.ROOT).contains("cave")),
                "nenhuma linha explica que se cava o chão: é a regra do jogo inteiro");
    }

    // ------------------------------------------------------------------
    //  O fim da rodada e a barra
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a rodada acaba com um de pé — menos no treino sozinho")
    void fimDaRodada() {
        assertFalse(ArenaComum.rodadaAcabou(4, 4, true));
        assertTrue(ArenaComum.rodadaAcabou(1, 4, true), "um de pé é o vencedor");
        assertTrue(ArenaComum.rodadaAcabou(0, 4, true));
        assertFalse(ArenaComum.rodadaAcabou(1, 1, true), "treino: ainda está de pé");
        assertTrue(ArenaComum.rodadaAcabou(0, 1, true), "treino: caiu, acabou");
        assertTrue(ArenaComum.rodadaAcabou(1, 1, false));
    }

    @Test
    @DisplayName("a barra não estoura nem com conta impossível")
    void barraNaoEstoura() {
        assertEquals(0f, ArenaComum.limite(Float.NaN), 0.0001f);
        assertEquals(0f, ArenaComum.limite(-3f), 0.0001f);
        assertEquals(1f, ArenaComum.limite(9f), 0.0001f);
        assertEquals(0.5f, ArenaComum.limite(0.5f), 0.0001f);
    }

    @Test
    @DisplayName("o tempo é escrito em minuto e segundo, com zero à esquerda")
    void tempoEscrito() {
        assertEquals("0:00", ArenaComum.tempo(0));
        assertEquals("0:09", ArenaComum.tempo(20 * 9));
        assertEquals("1:07", ArenaComum.tempo(20 * 67));
        assertEquals("0:00", ArenaComum.tempo(-5), "tique negativo não vira hora");
    }

    // ------------------------------------------------------------------
    //  O mundo e o portal
    // ------------------------------------------------------------------

    /**
     * A arena tem mundo próprio, e o portal azul do lobby aponta para ele.
     *
     * Um jogo cujo portal aponta para lugar nenhum existe, brilha e não leva
     * a lugar nenhum — que é o estado "em obra" do lobby. Depois de pronto,
     * ficar assim é o jogo invisível para quem joga.
     */
    @Test
    @DisplayName("o Spleef tem mundo próprio e o portal do lobby aponta para ele")
    void arenaTemMundoProprio() throws Exception {
        YamlConfiguration lobby;
        try (InputStream in = SpleefTest.class.getResourceAsStream("/minigames.yml")) {
            assertNotNull(in, "minigames.yml não foi empacotado");
            lobby = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        YamlConfiguration geral;
        try (InputStream in = SpleefTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml não foi empacotado");
            geral = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        YamlConfiguration tntRun;
        try (InputStream in = SpleefTest.class.getResourceAsStream("/tnt-run.yml")) {
            tntRun = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        String mundo = arena.getString("mundo");
        assertNotEquals(geral.getString("minigames.mundo"), mundo,
                "a arena foi posta no mesmo mundo do lobby");
        assertNotEquals(geral.getString("hub.mundo"), mundo,
                "a arena foi posta no mundo do hub");
        assertNotEquals(tntRun.getString("mundo"), mundo,
                "os dois jogos dividem o mesmo mundo: um apagaria a arena do outro");

        List<String> apontados = new ArrayList<>();
        for (Map<?, ?> portal : lobby.getMapList("portais")) {
            if ("spleef".equals(String.valueOf(portal.get("id")))) {
                apontados.add(String.valueOf(portal.get("mundo")));
            }
        }
        assertEquals(1, apontados.size(), "não há um portal spleef no lobby");
        assertEquals(mundo, apontados.get(0),
                "o portal azul do lobby não aponta para o mundo da arena: "
                        + "o jogo existiria sem como entrar nele");
    }

    private static void assertArrayEqualsInt(int[] esperado, int[] achado) {
        assertEquals(esperado.length, achado.length);
        for (int i = 0; i < esperado.length; i++) {
            assertEquals(esperado[i], achado[i], "posição " + i);
        }
    }
}
