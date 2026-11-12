package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O pátio do Pega-Pega, andado sem servidor.
 *
 * <h2>O que pode dar errado aqui e não lança exceção</h2>
 *
 * A galeria sair cortada num ponto: há um lado do pátio em que ninguém dá a
 * volta, e quem for encurralado ali perdeu antes de o pavio acender. Uma
 * escada com degrau de dois: ela não sobe, e o andar de cima some do jogo. O
 * fosso do plató largo demais: o meio do pátio vira enfeite. Estreito demais:
 * atravessa-se andando, e a decisão desaparece. Uma largada em cima de um
 * degrau: o jogador nasce com a cabeça dentro da pedra. O pavio não encurtar:
 * uma partida de dezesseis dura quinze rodadas.
 *
 * <p>Nada disso quebra. Tudo isso faz o jogo existir e não funcionar. Por
 * isso o teste ANDA o pátio célula por célula, com as regras de quem joga:
 * passo subindo um, queda de qualquer altura, salto de três e meio.
 */
class PegaPegaTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int RAIO = 20;
    private static final int MURO_ESPESSURA = 2;
    private static final int MURO_ALTURA = 7;
    private static final int ANEL_DENTRO = 12;
    private static final int ANEL_FORA = 16;
    private static final int ANEL_ALTURA = 4;
    private static final int PLATO_RAIO = 9;
    private static final int ESCADAS = 4;
    private static final int ESCADA_LARGURA = 3;
    private static final int BURACOS = 4;
    private static final int BURACO_TAMANHO = 2;
    private static final int COLUNAS = 4;
    private static final int COLUNA_RAIO = 18;
    private static final int COLUNA_ALTURA = 3;
    private static final int LARGADA_RAIO = 19;
    private static final int SAGUAO_ALTURA = 18;
    private static final int MARCO_RECUO = 8;
    private static final int MARCO_ALTURA = 20;

    private static final int MINIMO = 2;
    private static final int MAXIMO = 16;
    private static final int PAVIO = 25;
    private static final int PAVIO_DESCONTO = 3;
    private static final int PAVIO_MINIMO = 10;
    private static final int POR_BOMBA = 4;
    private static final double ALCANCE = 1.7;
    private static final int CARENCIA_TIQUES = 40;
    private static final int VELOCIDADE_EXTRA = 1;
    private static final int QUEDA_ABAIXO = 6;
    private static final int TEMPO_LIMITE = 420;

    private static Map<Long, Integer> patio;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = PegaPegaTest.class.getResourceAsStream("/pega-pega.yml")) {
            assertNotNull(in, "pega-pega.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        patio = patioPadrao();
    }

    private static Map<Long, Integer> patioPadrao() {
        return PegaPegaCatalog.patio(RAIO, MURO_ESPESSURA, MURO_ALTURA,
                ANEL_DENTRO, ANEL_FORA, ANEL_ALTURA, PLATO_RAIO, ESCADAS,
                ESCADA_LARGURA, BURACOS, BURACO_TAMANHO, COLUNAS, COLUNA_RAIO,
                COLUNA_ALTURA);
    }

    // ------------------------------------------------------------------
    //  O arquivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a planta tem os números que o construtor e o jogo esperam")
    void plantaConfere() {
        assertEquals("pega_pega", arena.getString("mundo"));
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(RAIO, arena.getInt("arena.raio"));
        assertEquals(MURO_ESPESSURA, arena.getInt("arena.muro-espessura"));
        assertEquals(MURO_ALTURA, arena.getInt("arena.muro-altura"));
        assertEquals(ANEL_DENTRO, arena.getInt("arena.anel-dentro"));
        assertEquals(ANEL_FORA, arena.getInt("arena.anel-fora"));
        assertEquals(ANEL_ALTURA, arena.getInt("arena.anel-altura"));
        assertEquals(PLATO_RAIO, arena.getInt("arena.plato-raio"));
        assertEquals(ESCADAS, arena.getInt("arena.escadas"));
        assertEquals(ESCADA_LARGURA, arena.getInt("arena.escada-largura"));
        assertEquals(BURACOS, arena.getInt("arena.buracos"));
        assertEquals(BURACO_TAMANHO, arena.getInt("arena.buraco-tamanho"));
        assertEquals(COLUNAS, arena.getInt("arena.colunas"));
        assertEquals(COLUNA_RAIO, arena.getInt("arena.coluna-raio"));
        assertEquals(COLUNA_ALTURA, arena.getInt("arena.coluna-altura"));
        assertEquals(LARGADA_RAIO, arena.getInt("arena.largada-raio"));
        assertEquals(SAGUAO_ALTURA, arena.getInt("arena.saguao-altura"));
        assertEquals(MARCO_RECUO, arena.getInt("arena.marco-recuo"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));

        assertEquals(MINIMO, arena.getInt("regras.minimo"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(PAVIO, arena.getInt("regras.pavio-segundos"));
        assertEquals(PAVIO_DESCONTO, arena.getInt("regras.pavio-desconto"));
        assertEquals(PAVIO_MINIMO, arena.getInt("regras.pavio-minimo"));
        assertEquals(POR_BOMBA, arena.getInt("regras.jogadores-por-bomba"));
        assertEquals(ALCANCE, arena.getDouble("regras.alcance"), 1.0E-9);
        assertEquals(CARENCIA_TIQUES, arena.getInt("regras.carencia-tiques"));
        assertEquals(VELOCIDADE_EXTRA, arena.getInt("regras.velocidade-extra"));
        assertEquals(QUEDA_ABAIXO, arena.getInt("regras.queda-abaixo"));
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
        for (String chave : List.of("piso", "piso-marca", "muro", "muro-faixa",
                "anel", "anel-beira", "plato", "plato-beira", "escada",
                "coluna", "coluna-topo", "nucleo")) {
            assertTrue(arena.contains("paleta." + chave),
                    "falta paleta." + chave + ", e o tipo de célula "
                            + "correspondente cairia no material padrão");
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
        assertEquals("PEGA-PEGA", arena.getString("textos.titulo"));
        assertFalse(arena.getString("textos.subtitulo", "").isBlank());
        List<String> regras = arena.getStringList("textos.regras");
        assertTrue(regras.size() >= 4, "o painel tem só " + regras.size()
                + " linha(s) de regra");
        assertTrue(regras.stream().anyMatch(linha -> linha.contains("ENTRAR")),
                "nenhuma linha diz para clicar na placa");
    }

    // ------------------------------------------------------------------
    //  O pátio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o piso vai até debaixo do muro, para o muro não flutuar")
    void pisoVaiAteDebaixoDoMuro() {
        int fora = RAIO + MURO_ESPESSURA;
        for (int x = -fora; x <= fora; x++) {
            for (int z = -fora; z <= fora; z++) {
                if (!ArenaComum.noDisco(x, z, fora)) {
                    continue;
                }
                assertTrue(patio.containsKey(ArenaComum.chave(x, 0, z)),
                        "buraco no piso em (" + x + "," + z + ")");
            }
        }
    }

    @Test
    @DisplayName("nada em que se pisa fica pendurado sobre o vazio")
    void nadaFicaSobreOVazio() {
        for (int[] casa : PegaPegaCatalog.ondeDaParaFicar(patio,
                RAIO + MURO_ESPESSURA, MURO_ALTURA)) {
            assertTrue(patio.containsKey(ArenaComum.chave(casa[0], 0, casa[2])),
                    "dá para ficar de pé em (" + casa[0] + "," + casa[1] + ","
                            + casa[2] + ") e não há piso embaixo: quem cair "
                            + "dali cai para fora do mundo");
        }
    }

    @Test
    @DisplayName("o muro fecha o pátio e não dá para pular")
    void muroFechaOPatio() {
        assertTrue(MURO_ALTURA >= ANEL_ALTURA + 3, "o muro tem " + MURO_ALTURA
                + " e a galeria " + ANEL_ALTURA + ": de cima da galeria "
                + "pula-se o muro");
        int fora = RAIO + MURO_ESPESSURA;
        for (int x = -fora; x <= fora; x++) {
            for (int z = -fora; z <= fora; z++) {
                if (!ArenaComum.noDisco(x, z, fora)
                        || ArenaComum.noDisco(x, z, RAIO)) {
                    continue;
                }
                for (int alto = 1; alto <= MURO_ALTURA; alto++) {
                    assertTrue(patio.containsKey(ArenaComum.chave(x, alto, z)),
                            "furo no muro em (" + x + "," + alto + "," + z + ")");
                }
            }
        }
    }

    @Test
    @DisplayName("a galeria é um anel fechado")
    void galeriaEhUmAnelFechado() {
        assertNull(PegaPegaBuilder.conferirGaleria(patio, ANEL_DENTRO, ANEL_FORA,
                ANEL_ALTURA));
    }

    @Test
    @DisplayName("uma galeria cortada é pega")
    void galeriaCortadaEhPega() {
        Map<Long, Integer> cortado = PegaPegaCatalog.patio(RAIO, MURO_ESPESSURA,
                MURO_ALTURA, ANEL_DENTRO, ANEL_FORA, ANEL_ALTURA, PLATO_RAIO,
                ESCADAS, ESCADA_LARGURA, BURACOS, 8, COLUNAS, COLUNA_RAIO,
                COLUNA_ALTURA);
        assertNotNull(PegaPegaBuilder.conferirGaleria(cortado, ANEL_DENTRO,
                        ANEL_FORA, ANEL_ALTURA),
                "buracos de 8 blocos cortam a galeria e a conferência passou");
    }

    @Test
    @DisplayName("os buracos da galeria caem no piso, e não em cima de nada")
    void buracosCaemNoPiso() {
        int meio = (ANEL_DENTRO + ANEL_FORA) / 2;
        for (long vazio : PegaPegaCatalog.buracosDaGaleria(BURACOS,
                BURACO_TAMANHO, meio)) {
            int x = ArenaComum.daChave(vazio, 40);
            int z = ArenaComum.daChave(vazio, 0);
            for (int alto = 1; alto <= ANEL_ALTURA; alto++) {
                assertFalse(patio.containsKey(ArenaComum.chave(x, alto, z)),
                        "o buraco em (" + x + "," + z + ") tem bloco na fiada "
                                + alto + ": a queda bate em alguma coisa");
            }
            assertTrue(patio.containsKey(ArenaComum.chave(x, 0, z)),
                    "o buraco em (" + x + "," + z + ") cai no vazio");
        }
    }

    // ------------------------------------------------------------------
    //  As escadas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("as escadas sobem um degrau por vez e chegam na galeria")
    void escadasSobemUmDeCadaVez() {
        assertNull(PegaPegaBuilder.conferirEscadas(patio, ESCADAS,
                ESCADA_LARGURA, RAIO, ANEL_ALTURA));
    }

    @Test
    @DisplayName("cada degrau está a um passo e uma fiada do seguinte")
    void degrausSeTocam() {
        List<int[]> passos = PegaPegaCatalog.escada(ESCADAS, ESCADA_LARGURA,
                RAIO, ANEL_ALTURA);
        for (int fiada = 1; fiada < ANEL_ALTURA; fiada++) {
            for (int[] passo : passos) {
                if (passo[1] != fiada) {
                    continue;
                }
                boolean achou = false;
                for (int[] acima : passos) {
                    if (acima[1] != fiada + 1) {
                        continue;
                    }
                    if (Math.abs(acima[0] - passo[0]) + Math.abs(acima[2] - passo[2])
                            <= 2) {
                        achou = true;
                        break;
                    }
                }
                assertTrue(achou, "o degrau em (" + passo[0] + "," + fiada + ","
                        + passo[2] + ") não tem nenhum degrau da fiada de cima "
                        + "ao lado: a escada tem um salto no meio");
            }
        }
    }

    @Test
    @DisplayName("uma escada que não existe é pega")
    void escadaSumidaEhPega() {
        Map<Long, Integer> semEscada = new HashMap<>(patio);
        for (int[] passo : PegaPegaCatalog.escada(ESCADAS, ESCADA_LARGURA,
                RAIO, ANEL_ALTURA)) {
            semEscada.remove(ArenaComum.chave(passo[0], passo[1], passo[2]));
        }
        assertNotNull(PegaPegaBuilder.conferirEscadas(semEscada, ESCADAS,
                        ESCADA_LARGURA, RAIO, ANEL_ALTURA),
                "apaguei as escadas e a conferência passou");
    }

    // ------------------------------------------------------------------
    //  O fosso
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o fosso do plató é saltável e é mesmo um fosso")
    void fossoEhSaltavel() {
        assertNull(PegaPegaBuilder.conferirFosso(patio, PLATO_RAIO, ANEL_DENTRO,
                ANEL_FORA, ANEL_ALTURA));
    }

    @Test
    @DisplayName("um fosso largo demais é pego")
    void fossoLargoEhPego() {
        Map<Long, Integer> largo = PegaPegaCatalog.patio(RAIO, MURO_ESPESSURA,
                MURO_ALTURA, ANEL_DENTRO, ANEL_FORA, ANEL_ALTURA, 6, ESCADAS,
                ESCADA_LARGURA, BURACOS, BURACO_TAMANHO, COLUNAS, COLUNA_RAIO,
                COLUNA_ALTURA);
        String problema = PegaPegaBuilder.conferirFosso(largo, 6, ANEL_DENTRO,
                ANEL_FORA, ANEL_ALTURA);
        assertNotNull(problema, "um plató de raio 6 deixa o fosso intransponível "
                + "e a conferência passou");
        assertTrue(problema.contains("não se salta"), problema);
    }

    @Test
    @DisplayName("um fosso estreito demais é pego")
    void fossoEstreitoEhPego() {
        Map<Long, Integer> estreito = PegaPegaCatalog.patio(RAIO, MURO_ESPESSURA,
                MURO_ALTURA, ANEL_DENTRO, ANEL_FORA, ANEL_ALTURA, 11, ESCADAS,
                ESCADA_LARGURA, BURACOS, BURACO_TAMANHO, COLUNAS, COLUNA_RAIO,
                COLUNA_ALTURA);
        String problema = PegaPegaBuilder.conferirFosso(estreito, 11, ANEL_DENTRO,
                ANEL_FORA, ANEL_ALTURA);
        assertNotNull(problema, "um plató de raio 11 encosta na galeria e a "
                + "conferência passou");
        assertTrue(problema.contains("andando"), problema);
    }

    // ------------------------------------------------------------------
    //  A ligação
    // ------------------------------------------------------------------

    @Test
    @DisplayName("tudo em que se pisa se alcança a partir do piso")
    void tudoSeAlcanca() {
        assertNull(PegaPegaBuilder.conferirLigacao(patio, RAIO, MURO_ALTURA));
    }

    @Test
    @DisplayName("uma ilha solta é pega")
    void ilhaSoltaEhPega() {
        Map<Long, Integer> comIlha = new HashMap<>(patio);
        comIlha.put(ArenaComum.chave(0, MURO_ALTURA - 1, 0),
                PegaPegaCatalog.PLATO);
        assertNotNull(PegaPegaBuilder.conferirLigacao(comIlha, RAIO, MURO_ALTURA),
                "pus uma plataforma solta no ar e a conferência passou");
    }

    @Test
    @DisplayName("os dois andares existem e têm chão de verdade")
    void osDoisAndaresExistem() {
        int embaixo = 0;
        int emCima = 0;
        for (int[] casa : PegaPegaCatalog.ondeDaParaFicar(patio, RAIO,
                MURO_ALTURA)) {
            if (!ArenaComum.noDisco(casa[0], casa[2], RAIO)) {
                continue;
            }
            if (casa[1] == 0) {
                embaixo++;
            } else if (casa[1] == ANEL_ALTURA) {
                emCima++;
            }
        }
        assertTrue(embaixo > 600, "o piso de baixo só tem " + embaixo
                + " casas pisáveis");
        assertTrue(emCima > 400, "o andar de cima só tem " + emCima
                + " casas pisáveis: galeria e plató juntos deveriam dar mais");
    }

    // ------------------------------------------------------------------
    //  A largada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("todas as largadas cabem, no piso e longe umas das outras")
    void largadasCabemTodas() {
        for (int quantos = 1; quantos <= MAXIMO; quantos++) {
            List<int[]> pontos = PegaPegaCatalog.largadas(patio, quantos,
                    LARGADA_RAIO, ANEL_FORA + 2);
            assertEquals(quantos, pontos.size(), "com " + quantos
                    + " jogadores só saíram " + pontos.size() + " largadas");
            for (int i = 0; i < pontos.size(); i++) {
                int[] um = pontos.get(i);
                assertTrue(PegaPegaCatalog.daParaFicar(patio, um[0], 0, um[1]),
                        "a largada em (" + um[0] + "," + um[1] + ") não tem "
                                + "pé direito: o jogador nasce dentro de pedra");
                assertTrue(ArenaComum.noDisco(um[0], um[1], RAIO),
                        "a largada em (" + um[0] + "," + um[1] + ") caiu fora "
                                + "do pátio");
                for (int j = i + 1; j < pontos.size(); j++) {
                    int[] outro = pontos.get(j);
                    assertTrue(Math.hypot(um[0] - outro[0], um[1] - outro[1]) >= 4,
                            "duas largadas grudadas com " + quantos
                                    + " jogadores");
                }
            }
        }
    }

    @Test
    @DisplayName("a largada é no anel de fora, longe do meio")
    void largadaEhNoAnelDeFora() {
        for (int[] onde : PegaPegaCatalog.largadas(patio, MAXIMO, LARGADA_RAIO,
                ANEL_FORA + 2)) {
            double doCentro = Math.hypot(onde[0], onde[1]);
            assertTrue(doCentro >= ANEL_FORA, "a largada em (" + onde[0] + ","
                    + onde[1] + ") caiu debaixo da galeria, a " + doCentro
                    + " do meio");
        }
    }

    // ------------------------------------------------------------------
    //  O pavio e as bombas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o pavio encurta a cada rodada e para no mínimo")
    void pavioEncurtaEPara() {
        int antes = Integer.MAX_VALUE;
        for (int rodada = 1; rodada <= 40; rodada++) {
            int pavio = pavioDaRodada(rodada);
            assertTrue(pavio <= antes, "o pavio da rodada " + rodada
                    + " é maior que o da anterior");
            assertTrue(pavio >= PAVIO_MINIMO, "o pavio da rodada " + rodada
                    + " caiu para " + pavio + ", abaixo do mínimo");
            antes = pavio;
        }
        assertEquals(PAVIO, pavioDaRodada(1));
        assertEquals(PAVIO - PAVIO_DESCONTO, pavioDaRodada(2));
        assertEquals(PAVIO_MINIMO, pavioDaRodada(40));
    }

    @Test
    @DisplayName("as bombas nunca pegam todo mundo de uma vez")
    void bombasNuncaPegamTodoMundo() {
        assertEquals(0, PegaPegaCatalog.quantasBombas(1, POR_BOMBA));
        assertEquals(0, PegaPegaCatalog.quantasBombas(0, POR_BOMBA));
        for (int vivos = 2; vivos <= 64; vivos++) {
            int bombas = PegaPegaCatalog.quantasBombas(vivos, POR_BOMBA);
            assertTrue(bombas >= 1, "com " + vivos + " vivos não saiu bomba "
                    + "nenhuma: a rodada nunca acabaria");
            assertTrue(bombas <= vivos - 1, "com " + vivos + " vivos saíram "
                    + bombas + " bombas: todo mundo explodiria junto e a "
                    + "partida acabaria sem vencedor");
        }
    }

    /**
     * A partida inteira cabe no relógio.
     *
     * Simula a sala cheia: a cada rodada explodem tantos quantos forem as
     * bombas e o pavio encurta. Se a conta não fechar antes do tempo-limite,
     * a partida de dezesseis termina no estouro do relógio — sem vencedor, e
     * depois de sete minutos.
     */
    @Test
    @DisplayName("uma partida de sala cheia acaba antes do relógio")
    void partidaCabeNoRelogio() {
        int vivos = MAXIMO;
        int segundos = 0;
        int rodada = 0;
        while (vivos > 1 && rodada < 100) {
            rodada++;
            segundos += pavioDaRodada(rodada);
            vivos -= PegaPegaCatalog.quantasBombas(vivos, POR_BOMBA);
        }
        assertEquals(1, vivos, "a partida terminou com " + vivos
                + " vivos em vez de um");
        assertTrue(segundos < TEMPO_LIMITE, "a partida de " + MAXIMO
                + " leva " + segundos + "s e o limite é " + TEMPO_LIMITE);
        assertTrue(segundos > 60, "a partida de " + MAXIMO + " acaba em "
                + segundos + "s: rápido demais para dar jogo");
    }

    @Test
    @DisplayName("o alcance é de encostar, e não de mirar de longe")
    void alcanceEhDeToque() {
        assertTrue(ALCANCE >= 1.0, "alcance de " + ALCANCE + ": menor que a "
                + "largura de um jogador, ninguém consegue passar a bomba");
        assertTrue(ALCANCE <= 2.5, "alcance de " + ALCANCE + ": passa-se a "
                + "bomba sem encostar, e aí o pátio não importa");
    }

    @Test
    @DisplayName("a carência dá tempo de fugir e não dura a rodada")
    void carenciaDaTempoDeFugir() {
        assertTrue(CARENCIA_TIQUES >= 20, "carência de " + CARENCIA_TIQUES
                + " tiques: dois jogadores colados trocam a bomba sem parar e "
                + "o pavio não decide nada");
        assertTrue(CARENCIA_TIQUES <= PAVIO_MINIMO * 20 / 2, "carência de "
                + CARENCIA_TIQUES + " tiques é mais de meia rodada curta");
    }

    @Test
    @DisplayName("quem carrega a bomba corre mais que quem foge")
    void bombaDaPressa() {
        assertTrue(VELOCIDADE_EXTRA >= 1, "sem velocidade extra, a bomba nunca "
                + "alcança ninguém num pátio de gente igual");
        assertTrue(VELOCIDADE_EXTRA <= 2, "velocidade " + VELOCIDADE_EXTRA
                + " faz o portador voar e fugir deixa de ser possível");
    }

    @Test
    @DisplayName("o pátio cabe a sala cheia sem ficar vazio")
    void patioCabeASalaCheia() {
        double area = Math.PI * RAIO * RAIO;
        double porJogador = area / MAXIMO;
        assertTrue(porJogador >= 50, "sobram só " + Math.round(porJogador)
                + " blocos por jogador");
        assertTrue(porJogador <= 300, "sobram " + Math.round(porJogador)
                + " blocos por jogador: ninguém se encontra");
    }

    @Test
    @DisplayName("quem cai do pátio tem rede de segurança")
    void quedaEhRedeDeSeguranca() {
        assertTrue(QUEDA_ABAIXO >= 4, "o limite de queda está colado no piso");
        assertTrue(QUEDA_ABAIXO <= 12, "o limite de queda está longe demais");
    }

    // ------------------------------------------------------------------
    //  Ferramentas
    // ------------------------------------------------------------------

    /** A mesma conta de {@code PegaPegaCatalog.pavioDaRodada}, sem o yml. */
    private static int pavioDaRodada(int rodada) {
        return Math.max(PAVIO_MINIMO, PAVIO - PAVIO_DESCONTO * Math.max(0, rodada - 1));
    }
}
