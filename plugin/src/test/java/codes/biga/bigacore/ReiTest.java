package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A montanha do Rei, escalada sem servidor.
 *
 * <h2>O que pode dar errado aqui e não lança exceção</h2>
 *
 * A montanha subir duas fiadas de uma vez num ponto: aquele lado deixa de
 * existir, todo mundo sobe pelo mesmo lugar e quem chegar primeiro no gargalo
 * fecha a passagem. O cume ser maior que a zona que conta: sobra chão plano
 * lá em cima em que estar não vale nada, e ninguém entende por quê. A zona
 * ser maior que o cume: conta-se quem está no ar ao lado da montanha. As
 * pedras da encosta virarem escada: sobe-se pela cobertura e a subida deixa
 * de custar. E a conta do relógio: tolerância maior que o alvo faz o reinado
 * nunca acabar, castigo maior que a tolerância faz cair ser definitivo.
 *
 * <p>Nada disso quebra. Tudo isso deixa o jogo de pé e errado.
 */
class ReiTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int RAIO = 24;
    private static final int BEIRA_ANEIS = 1;
    private static final int PE_RAIO = 18;
    private static final int PASSO = 2;
    private static final int ALTURA = 8;
    private static final int TOPO_RAIO = 3;
    private static final int PEDRAS = 8;
    private static final int PEDRA_RAIO = 12;
    private static final int PEDRA_ALTURA = 2;
    private static final int SAGUAO_ALTURA = 20;
    private static final int MARCO_RECUO = 8;
    private static final int MARCO_ALTURA = 22;

    private static final int MINIMO = 2;
    private static final int MAXIMO = 16;
    private static final int PARA_GANHAR = 60;
    private static final int TOLERANCIA = 40;
    private static final double EMPURRAO = 1.05;
    private static final double EMPURRAO_ALTURA = 0.42;
    private static final int EMPURRAO_CARENCIA = 12;
    private static final int QUEDA_ABAIXO = 6;
    private static final int CASTIGO = 3;
    private static final int TEMPO_LIMITE = 420;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = ReiTest.class.getResourceAsStream("/rei.yml")) {
            assertNotNull(in, "rei.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static int alturaDe(int r) {
        return ReiCatalog.alturaDe(r, PE_RAIO, PASSO, ALTURA);
    }

    // ------------------------------------------------------------------
    //  O arquivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a planta tem os números que o construtor e o jogo esperam")
    void plantaConfere() {
        assertEquals("rei", arena.getString("mundo"));
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(RAIO, arena.getInt("arena.raio"));
        assertEquals(BEIRA_ANEIS, arena.getInt("arena.beira-aneis"));
        assertEquals(PE_RAIO, arena.getInt("arena.pe-raio"));
        assertEquals(PASSO, arena.getInt("arena.passo"));
        assertEquals(ALTURA, arena.getInt("arena.altura"));
        assertEquals(TOPO_RAIO, arena.getInt("arena.topo-raio"));
        assertEquals(PEDRAS, arena.getInt("arena.pedras"));
        assertEquals(PEDRA_RAIO, arena.getInt("arena.pedra-raio"));
        assertEquals(PEDRA_ALTURA, arena.getInt("arena.pedra-altura"));
        assertEquals(SAGUAO_ALTURA, arena.getInt("arena.saguao-altura"));
        assertEquals(MARCO_RECUO, arena.getInt("arena.marco-recuo"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));

        assertEquals(MINIMO, arena.getInt("regras.minimo"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(PARA_GANHAR, arena.getInt("regras.segundos-para-ganhar"));
        assertEquals(TOLERANCIA, arena.getInt("regras.tolerancia-tiques"));
        assertEquals(EMPURRAO, arena.getDouble("regras.empurrao"), 1.0E-9);
        assertEquals(EMPURRAO_ALTURA, arena.getDouble("regras.empurrao-altura"),
                1.0E-9);
        assertEquals(EMPURRAO_CARENCIA, arena.getInt("regras.empurrao-carencia"));
        assertEquals(QUEDA_ABAIXO, arena.getInt("regras.queda-abaixo"));
        assertEquals(CASTIGO, arena.getInt("regras.castigo-segundos"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
    }

    @Test
    @DisplayName("todo material da paleta existe")
    void paletaEhValida() {
        Map<String, Object> paleta = arena.getConfigurationSection("paleta")
                .getValues(false);
        assertFalse(paleta.isEmpty(), "a paleta ficou vazia");
        for (Map.Entry<String, Object> item : paleta.entrySet()) {
            assertNotNull(Material.matchMaterial(
                            String.valueOf(item.getValue()).toUpperCase(Locale.ROOT)),
                    "paleta." + item.getKey() + " não é um material: "
                            + item.getValue());
        }
    }

    @Test
    @DisplayName("a paleta tem uma chave para cada tipo de célula")
    void paletaCobreTodosOsTipos() {
        for (String chave : List.of("chao", "beira", "encosta", "degrau",
                "topo", "topo-beira", "pedra", "pedra-topo")) {
            assertTrue(arena.contains("paleta." + chave),
                    "falta paleta." + chave);
        }
    }

    @Test
    @DisplayName("a placa do saguão é de parede")
    void placaEhDeParede() {
        String nome = arena.getString("paleta.placa", "");
        assertTrue(nome.toUpperCase(Locale.ROOT).endsWith("_WALL_SIGN"), nome);
    }

    @Test
    @DisplayName("o painel de instruções diz como se joga")
    void textosExplicamOJogo() {
        assertEquals("REI DA MONTANHA", arena.getString("textos.titulo"));
        assertFalse(arena.getString("textos.subtitulo", "").isBlank());
        List<String> regras = arena.getStringList("textos.regras");
        assertTrue(regras.size() >= 4, "o painel tem só " + regras.size()
                + " linha(s) de regra");
        assertTrue(regras.stream().anyMatch(linha -> linha.contains("ENTRAR")),
                "nenhuma linha diz para clicar na placa");
    }

    // ------------------------------------------------------------------
    //  A montanha
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a montanha de verdade passa na conferência")
    void montanhaConfere() {
        assertNull(ReiBuilder.conferirMontanha(PE_RAIO, PASSO, ALTURA, TOPO_RAIO));
    }

    @Test
    @DisplayName("a montanha sobe uma fiada por vez, do pé ao cume")
    void montanhaSobeDeUmEmUm() {
        assertEquals(0, alturaDe(PE_RAIO + 1), "a montanha começa antes do pé");
        int antes = 0;
        for (int r = PE_RAIO; r >= 0; r--) {
            int agora = alturaDe(r);
            assertTrue(agora >= antes, "a montanha desce na distância " + r);
            assertTrue(agora - antes <= 1, "a montanha sobe " + (agora - antes)
                    + " fiadas de uma vez na distância " + r);
            antes = agora;
        }
        assertEquals(ALTURA, alturaDe(0), "o meio não chegou ao cume");
    }

    /**
     * A montanha é redonda, e não um losango.
     *
     * A altura sai da distância ARREDONDADA, e não do disco de inteiros. Se
     * saísse do disco, na diagonal ela seria mais baixa que no eixo e o lado
     * da quina viraria o caminho fácil — numa corrida em que todo mundo sobe
     * ao mesmo tempo, isso decide a partida antes de ela começar.
     */
    @Test
    @DisplayName("subir custa o mesmo em qualquer direção")
    void montanhaEhRedonda() {
        for (int passo = 1; passo <= 40; passo++) {
            double angulo = 2 * Math.PI * passo / 40;
            int anterior = 0;
            for (int r = PE_RAIO + 1; r >= 0; r--) {
                int x = (int) Math.round(Math.cos(angulo) * r);
                int z = (int) Math.round(Math.sin(angulo) * r);
                int alto = alturaDe(ReiCatalog.distancia(x, z));
                assertTrue(alto - anterior <= 1, "no rumo " + passo
                        + " a montanha sobe " + (alto - anterior)
                        + " fiadas de uma vez em (" + x + "," + z + ")");
                anterior = Math.max(anterior, alto);
            }
            assertEquals(ALTURA, anterior, "o rumo " + passo
                    + " não chega ao cume");
        }
    }

    @Test
    @DisplayName("o cume plano é exatamente a zona que conta")
    void cumeEhAZonaQueConta() {
        for (int x = -PE_RAIO; x <= PE_RAIO; x++) {
            for (int z = -PE_RAIO; z <= PE_RAIO; z++) {
                int r = ReiCatalog.distancia(x, z);
                boolean noCume = alturaDe(r) >= ALTURA;
                boolean naZona = r <= TOPO_RAIO;
                assertEquals(naZona, noCume, "a casa (" + x + "," + z
                        + ") está no cume=" + noCume + " e na zona=" + naZona
                        + ": ou sobra chão que não conta, ou conta chão que "
                        + "não existe");
            }
        }
    }

    @Test
    @DisplayName("o topo é pequeno, mas cabe uma briga")
    void topoEhPequeno() {
        int casas = 0;
        for (int x = -TOPO_RAIO; x <= TOPO_RAIO; x++) {
            for (int z = -TOPO_RAIO; z <= TOPO_RAIO; z++) {
                if (ReiCatalog.distancia(x, z) <= TOPO_RAIO) {
                    casas++;
                }
            }
        }
        assertTrue(casas >= 20, "o topo tem só " + casas + " casas: não cabe "
                + "nem o rei e quem vem tomá-lo");
        assertTrue(casas <= 80, "o topo tem " + casas + " casas: é grande "
                + "demais para alguém conseguir limpá-lo");
    }

    @Test
    @DisplayName("a conferência pega passo que vira parede")
    void conferenciaPegaParede() {
        String problema = ReiBuilder.conferirMontanha(PE_RAIO, 1, 30, 1);
        assertNotNull(problema, "passo 1 com altura 30 é uma parede e passou");
    }

    @Test
    @DisplayName("a conferência pega montanha que não chega ao cume")
    void conferenciaPegaMontanhaBaixa() {
        String problema = ReiBuilder.conferirMontanha(10, 4, 8, 3);
        assertNotNull(problema, "um pé de 10 com passo 4 não dá 8 fiadas e "
                + "a conferência passou");
        assertTrue(problema.contains("só chega"), problema);
    }

    @Test
    @DisplayName("a conferência pega zona do topo que não bate com o cume")
    void conferenciaPegaZonaErrada() {
        String problema = ReiBuilder.conferirMontanha(PE_RAIO, PASSO, ALTURA, 7);
        assertNotNull(problema, "uma zona de raio 7 num cume de raio 3 passou");
        assertTrue(problema.contains("cume"), problema);
    }

    @Test
    @DisplayName("as pedras ficam na encosta, longe do topo e do pé")
    void pedrasFicamNaEncosta() {
        List<int[]> pedras = ReiCatalog.pedrasDaEncosta(PEDRAS, PEDRA_RAIO);
        assertEquals(PEDRAS, pedras.size());
        for (int[] onde : pedras) {
            int r = ReiCatalog.distancia(onde[0], onde[1]);
            assertTrue(r > TOPO_RAIO + 2, "pedra a " + r + " do meio: colada "
                    + "no topo");
            assertTrue(r < PE_RAIO - 2, "pedra a " + r + " do meio: fora da "
                    + "encosta");
        }
        for (int i = 0; i < pedras.size(); i++) {
            for (int j = i + 1; j < pedras.size(); j++) {
                double entre = Math.hypot(pedras.get(i)[0] - pedras.get(j)[0],
                        pedras.get(i)[1] - pedras.get(j)[1]);
                assertTrue(entre >= 5, "duas pedras a "
                        + String.format(Locale.ROOT, "%.2f", entre)
                        + ": juntas viram um muro em volta da montanha");
            }
        }
    }

    /**
     * As pedras não viram escada.
     *
     * Uma pedra de duas fiadas num degrau faz um salto de dois para a casa de
     * dentro — o que seria um atalho que não custa nada e tornaria a encosta
     * inútil. Com a pedra sentada na altura DA PRÓPRIA CASA, o topo dela fica
     * sempre a duas fiadas do chão ao lado, e duas fiadas ninguém sobe.
     */
    @Test
    @DisplayName("as pedras não viram atalho para o topo")
    void pedrasNaoSaoAtalho() {
        for (int[] onde : ReiCatalog.pedrasDaEncosta(PEDRAS, PEDRA_RAIO)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = onde[0] + dx;
                    int z = onde[1] + dz;
                    int topoDaPedra = alturaDe(ReiCatalog.distancia(x, z))
                            + PEDRA_ALTURA;
                    // A casa mais alta a um passo da pedra, por dentro.
                    int maisAlto = 0;
                    for (int vx = -1; vx <= 1; vx++) {
                        for (int vz = -1; vz <= 1; vz++) {
                            maisAlto = Math.max(maisAlto, alturaDe(
                                    ReiCatalog.distancia(x + vx, z + vz)));
                        }
                    }
                    assertTrue(topoDaPedra - maisAlto >= 1, "em (" + x + ","
                            + z + ") o topo da pedra está na fiada "
                            + topoDaPedra + " e o chão ao lado na " + maisAlto
                            + ": a pedra não é cobertura, é degrau");
                }
            }
        }
    }

    @Test
    @DisplayName("as largadas ficam no chão, fora da montanha")
    void largadasFicamNoChao() {
        for (int quantos = 2; quantos <= MAXIMO; quantos++) {
            List<int[]> pontos = ArenaComum.largadas(quantos, RAIO);
            assertEquals(quantos, pontos.size());
            for (int[] onde : pontos) {
                int r = ReiCatalog.distancia(onde[0], onde[1]);
                assertEquals(0, alturaDe(r), "a largada em (" + onde[0] + ","
                        + onde[1] + ") cai em cima da montanha, na fiada "
                        + alturaDe(r));
                assertTrue(ArenaComum.noDiscoMeio(onde[0], onde[1],
                                RAIO - BEIRA_ANEIS),
                        "a largada em (" + onde[0] + "," + onde[1]
                                + ") cai na beira do disco");
            }
        }
    }

    /**
     * Quem joga sozinho não nasce no cume.
     *
     * O {@link ArenaComum#largadas} larga quem está sozinho em (0,0), que
     * aqui é o TOPO — a rodada de treino começaria ganha. É por isso que o
     * catálogo pede sempre duas largadas no mínimo.
     */
    @Test
    @DisplayName("o jogador sozinho não nasce em cima do topo")
    void treinoSozinhoNaoNasceNoTopo() {
        assertEquals(0, ArenaComum.largadas(1, RAIO).get(0)[0]);
        assertEquals(0, ArenaComum.largadas(1, RAIO).get(0)[1]);
        List<int[]> dois = ArenaComum.largadas(2, RAIO);
        for (int[] onde : dois) {
            assertEquals(0, alturaDe(ReiCatalog.distancia(onde[0], onde[1])),
                    "a largada de dois já cai na montanha");
        }
    }

    // ------------------------------------------------------------------
    //  O relógio e o bastão
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a tolerância dá tempo de voltar e não dá a partida")
    void toleranciaEhCurta() {
        assertTrue(TOLERANCIA >= 20, "tolerância de " + TOLERANCIA
                + " tiques: um empurrão de sorte apaga a partida inteira");
        assertTrue(TOLERANCIA <= PARA_GANHAR * 20 / 10, "tolerância de "
                + TOLERANCIA + " tiques é mais de um décimo do alvo: dá para "
                + "reinar de longe");
        assertTrue(CASTIGO * 20 > TOLERANCIA, "o castigo da queda (" + CASTIGO
                + "s) é menor que a tolerância (" + TOLERANCIA + " tiques): "
                + "quem for jogado da montanha volta a reinar sem ter subido");
    }

    @Test
    @DisplayName("o minuto cabe no relógio da partida com folga")
    void minutoCabeNoRelogio() {
        assertTrue(PARA_GANHAR * 4 <= TEMPO_LIMITE, "o alvo é " + PARA_GANHAR
                + "s e o limite " + TEMPO_LIMITE + "s: não dá tempo nem de "
                + "quatro tentativas");
        assertTrue(PARA_GANHAR >= 20, "um alvo de " + PARA_GANHAR
                + "s se fecha por acidente");
    }

    @Test
    @DisplayName("o empurrão tira do topo sem atravessar a arena")
    void empurraoEhDoTamanhoCerto() {
        assertTrue(EMPURRAO >= 0.6 && EMPURRAO <= 2.0, "empurrão de " + EMPURRAO
                + ": ou não tira ninguém do topo, ou joga a pessoa fora do "
                + "mundo");
        assertTrue(EMPURRAO_ALTURA > 0 && EMPURRAO_ALTURA < EMPURRAO,
                "a altura do empurrão (" + EMPURRAO_ALTURA + ") tem de "
                        + "levantar sem virar pulo");
        assertTrue(EMPURRAO_CARENCIA >= 8, "carência de " + EMPURRAO_CARENCIA
                + " tiques: dá para empurrar vinte vezes por segundo e "
                + "ninguém mais anda");
        assertTrue(EMPURRAO_CARENCIA <= 40, "carência de " + EMPURRAO_CARENCIA
                + " tiques é mais de dois segundos entre golpes");
    }

    /**
     * Um empurrão do topo chega ao chão.
     *
     * O topo tem raio 3 e a encosta desce dali; um empurrão que não leve o
     * jogador para fora do raio do topo não tira ninguém do lugar. A conta é
     * grosseira de propósito: velocidade inicial horizontal contra o atrito
     * do ar, por uns poucos tiques de voo.
     */
    @Test
    @DisplayName("um empurrão tira o rei da zona do topo")
    void empurraoTiraDoTopo() {
        double distancia = 0;
        double velocidade = EMPURRAO;
        for (int tique = 0; tique < 20 && velocidade > 0.02; tique++) {
            distancia += velocidade;
            velocidade *= 0.91;
        }
        assertTrue(distancia > TOPO_RAIO + 1, "um empurrão leva só "
                + String.format(Locale.ROOT, "%.2f", distancia)
                + " blocos, e o topo tem raio " + TOPO_RAIO
                + ": não tira ninguém de lá");
    }

    @Test
    @DisplayName("quem cai da montanha cai mesmo, e não raspa o limite")
    void quedaEhClara() {
        assertTrue(QUEDA_ABAIXO >= 4, "o limite de queda está colado no chão");
        assertTrue(QUEDA_ABAIXO <= 12, "o limite de queda está longe demais");
        assertTrue(Y - QUEDA_ABAIXO < Y, "o limite de queda está acima do chão");
    }

    @Test
    @DisplayName("a arena cabe a sala cheia")
    void arenaCabeASalaCheia() {
        double area = Math.PI * RAIO * RAIO;
        assertTrue(area / MAXIMO >= 60, "sobram só "
                + Math.round(area / MAXIMO) + " blocos por jogador");
    }
}
