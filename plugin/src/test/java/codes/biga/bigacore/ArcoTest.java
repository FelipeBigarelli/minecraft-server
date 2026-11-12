package codes.biga.bigacore;

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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As contas do ARCO E FLECHA, conferidas sem servidor.
 *
 * <h2>O teste que vale mais aqui</h2>
 *
 * {@link #aForcaMinimaRecusaMeiaPuxadaEAceitaAPuxadaCheia} prende um número
 * do yml às duas pontas da física do arco do Minecraft. Ele existe porque as
 * duas pontas erram de maneiras que parecem azar:
 *
 * <ul>
 *   <li><b>Frouxo demais</b> e o tiro sem mira passa. Num jogo em que um
 *       acerto elimina, a jogada certa volta a ser encher o ar de flechas, e
 *       o jogo deixa de ser sobre mira sem ninguém anunciar nada.</li>
 *   <li><b>Apertado demais</b> (1,0 exato) e o arco "às vezes não atira": o
 *       servidor vê a corda chegar ao máximo um tique depois do cliente de
 *       vez em quando, e o jogador nunca descobre por quê.</li>
 * </ul>
 *
 * <p>{@link #aForcaDoArcoNaoEhOTempo} é o irmão dele e guarda o motivo: a
 * força é {@code (t² + 2t)/3} e não {@code t}. Meio segundo de arco dá 42% da
 * força, não 50% — quem escrever a regra em "tempo" e comparar com força
 * acerta por acaso e erra quando mexer no número.
 *
 * <h2>E o que prova que a arena é de arco</h2>
 *
 * {@link #aCoberturaCobreEDeixaLinhaDeTiro}: tem de haver muro para se
 * esconder e caminho para atirar. Sem muro, dezesseis arqueiros se eliminam
 * por flecha perdida nos primeiros dez segundos; com muro demais, ninguém se
 * acerta e toda partida vai no relógio.
 */
class ArcoTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int RAIO = 34;
    private static final int PAREDE_ALTURA = 5;
    private static final List<Integer> ANEIS = List.of(11, 20, 28);
    private static final List<Integer> MUROS_POR_ANEL = List.of(6, 9, 12);
    private static final int MURO_COMPRIMENTO = 7;
    private static final int MURO_ALTURA = 3;
    private static final int MARCO_ALTURA = 18;
    private static final double FORCA_MINIMA = 0.9;
    private static final int GRACA = 5;
    private static final int PONTOS_POR_ACERTO = 10;
    private static final int MAXIMO = 16;
    private static final int TEMPO_LIMITE = 300;

    /** Quantos raios do meio para fora o teste de linha de tiro percorre. */
    private static final int RAIOS = 72;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = ArcoTest.class.getResourceAsStream("/arco.yml")) {
            assertNotNull(in, "arco.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static List<int[]> cobertura() {
        return ArcoCatalog.blocosDaCobertura(ANEIS, MUROS_POR_ANEL,
                MURO_COMPRIMENTO);
    }

    // ------------------------------------------------------------------
    //  O yml
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o yml diz os números que o teste usa")
    void ymlBate() {
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(RAIO, arena.getInt("arena.raio"));
        assertEquals(PAREDE_ALTURA, arena.getInt("arena.parede-altura"));
        assertEquals(ANEIS, arena.getIntegerList("arena.aneis"));
        assertEquals(MUROS_POR_ANEL,
                arena.getIntegerList("arena.muros-por-anel"));
        assertEquals(MURO_COMPRIMENTO, arena.getInt("arena.muro-comprimento"));
        assertEquals(MURO_ALTURA, arena.getInt("arena.muro-altura"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));
        assertEquals(FORCA_MINIMA, arena.getDouble("regras.forca-minima"),
                1e-9);
        assertEquals(GRACA, arena.getInt("regras.graca-segundos"));
        assertEquals(PONTOS_POR_ACERTO,
                arena.getInt("regras.pontos-por-acerto"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
    }

    @Test
    @DisplayName("os números do yml passam nas três conferências")
    void aPlantaPassaNaConferencia() {
        assertNull(ArcoBuilder.conferirForca(FORCA_MINIMA));
        assertNull(ArcoBuilder.conferirCobertura(ANEIS, MUROS_POR_ANEL,
                MURO_COMPRIMENTO, MURO_ALTURA, PAREDE_ALTURA, RAIO));
        assertNull(ArcoBuilder.conferirRelogio(GRACA, TEMPO_LIMITE, RAIO,
                ANEIS));
    }

    @Test
    @DisplayName("o painel avisa que tiro fraco é recusado")
    void oPainelAvisaDoTiroFraco() {
        List<String> regras = arena.getStringList("textos.regras");
        assertTrue(regras.size() >= 5, "o painel tem só " + regras.size()
                + " linha(s)");
        assertTrue(regras.stream().anyMatch(
                        linha -> linha.toUpperCase().contains("RECUSADO")),
                "nenhuma linha avisa que o tiro fraco é recusado: " + regras);
        assertTrue(regras.stream().anyMatch(
                        linha -> linha.contains("muro")),
                "nenhuma linha diz que a flecha come o muro: " + regras);
    }

    // ------------------------------------------------------------------
    //  A física do arco
    // ------------------------------------------------------------------

    /**
     * A força mínima tem de recusar 18 tiques de arco e aceitar 19.
     *
     * Os dois lados do mesmo número, e nenhum dos dois se descobre jogando:
     * frouxo, o tiro sem mira passa e o jogo deixa de ser de mira; apertado,
     * o arco falha de vez em quando por um tique de diferença entre cliente e
     * servidor e o jogador acha que é azar.
     */
    @Test
    @DisplayName("a força mínima recusa meia puxada e aceita a puxada cheia")
    void aForcaMinimaRecusaMeiaPuxadaEAceitaAPuxadaCheia() {
        double dezoito = ArcoCatalog.forcaDe(18);
        double dezenove = ArcoCatalog.forcaDe(19);
        assertTrue(FORCA_MINIMA > dezoito, "a força mínima é " + FORCA_MINIMA
                + " e 18 tiques de arco já dão " + dezoito
                + ": o tiro sem mira passaria");
        assertTrue(FORCA_MINIMA <= dezenove, "a força mínima é " + FORCA_MINIMA
                + " e 19 tiques de arco dão só " + dezenove + ": o arco "
                + "falharia quando o servidor visse a corda um tique depois");
        assertEquals(19, ArcoCatalog.tiquesParaForca(FORCA_MINIMA));

        // E a conferência recusa as duas pontas.
        String frouxa = ArcoBuilder.conferirForca(0.5);
        assertNotNull(frouxa);
        assertTrue(frouxa.contains("sem mira"), frouxa);
        String apertada = ArcoBuilder.conferirForca(1.0);
        assertNotNull(apertada);
        assertTrue(apertada.contains("tique"), apertada);
        assertNotNull(ArcoBuilder.conferirForca(0.05));
        assertNotNull(ArcoBuilder.conferirForca(1.5));
    }

    /**
     * A força do arco não é proporcional ao tempo.
     *
     * {@code (t² + 2t)/3}: meio segundo de arco dá 42% da força e não 50%.
     * Quem escrever a regra pensando em tempo acerta por acaso no número de
     * hoje e erra no dia em que mexer nele.
     */
    @Test
    @DisplayName("a força do arco não é o tempo")
    void aForcaDoArcoNaoEhOTempo() {
        assertEquals(0.0, ArcoCatalog.forcaDe(0), 1e-9);
        assertEquals(1.0, ArcoCatalog.forcaDe(ArcoCatalog.TIQUES_DO_ARCO),
                1e-9);
        assertEquals(1.0, ArcoCatalog.forcaDe(60), 1e-9);
        double meio = ArcoCatalog.forcaDe(10);
        assertEquals(0.41666, meio, 1e-4);
        assertTrue(meio < 0.5, "meio segundo de arco dá " + meio
                + ": se fosse proporcional daria 0,5");
        double antes = -1;
        for (int t = 0; t <= ArcoCatalog.TIQUES_DO_ARCO; t++) {
            double agora = ArcoCatalog.forcaDe(t);
            assertTrue(agora >= antes, "a força caiu de " + antes + " para "
                    + agora + " em " + t + " tiques");
            antes = agora;
        }
        assertEquals(0, ArcoCatalog.tiquesParaForca(0.0));
        assertEquals(ArcoCatalog.TIQUES_DO_ARCO,
                ArcoCatalog.tiquesParaForca(1.0));
    }

    @Test
    @DisplayName("um segundo de arco é o custo de cada tiro")
    void umSegundoPorTiro() {
        int tiques = ArcoCatalog.tiquesParaForca(FORCA_MINIMA);
        assertTrue(tiques >= ArcoCatalog.TIQUES_DO_ARCO - 1,
                "o tiro custa " + tiques + " tiques: menos de um segundo não "
                        + "pune o tiro sem mira");
        // Em cinco minutos, ninguém dá mais de 300 tiros.
        int maisTiros = TEMPO_LIMITE * 20 / tiques;
        assertTrue(maisTiros <= TEMPO_LIMITE + 20, "daria para dar "
                + maisTiros + " tiros em " + TEMPO_LIMITE + "s");
    }

    // ------------------------------------------------------------------
    //  A cobertura
    // ------------------------------------------------------------------

    /**
     * Há muro para se esconder e caminho para atirar.
     *
     * A proporção de muro no chão, e a travessia de um tiro do meio para
     * fora. As duas são a mesma pergunta vista de dois lados, e é a pergunta
     * que decide se este jogo é de arco ou de sorte.
     */
    @Test
    @DisplayName("a cobertura cobre e deixa linha de tiro")
    void aCoberturaCobreEDeixaLinhaDeTiro() {
        int blocos = cobertura().size();
        int chao = ArcoCatalog.blocosDoChao(RAIO);
        double parte = blocos / (double) chao;
        assertTrue(parte >= 0.03 && parte <= 0.20,
                "a cobertura ocupa " + Math.round(parte * 1000) / 10.0
                        + "% do chão");
        assertTrue(blocos >= MAXIMO, "só " + blocos + " blocos de muro para "
                + MAXIMO + " jogadores: não há abrigo para todos");

        int maior = 0;
        int limpos = 0;
        int[] porQuadrante = new int[4];
        for (int k = 0; k < 4; k++) {
            porQuadrante[k] = Integer.MAX_VALUE;
        }
        for (int k = 0; k < RAIOS; k++) {
            double angulo = 2 * Math.PI * k / RAIOS;
            int quantos = ArcoCatalog.coberturaNoCaminho(angulo, RAIO, ANEIS,
                    MUROS_POR_ANEL, MURO_COMPRIMENTO);
            maior = Math.max(maior, quantos);
            if (quantos == 0) {
                limpos++;
            }
            int quadrante = k * 4 / RAIOS;
            porQuadrante[quadrante] = Math.min(porQuadrante[quadrante],
                    quantos);
        }
        assertTrue(maior <= ANEIS.size() * 2, "há uma direção em que o tiro "
                + "atravessa " + maior + " blocos de muro, e com "
                + ANEIS.size() + " anéis o máximo razoável é "
                + (ANEIS.size() * 2) + ": os anéis estão alinhados e fecharam "
                + "a direção");
        assertTrue(limpos >= RAIOS / 12, "só " + limpos + " de " + RAIOS
                + " direções têm tiro limpo do meio: a arena é um labirinto, "
                + "não um campo de tiro");
        for (int k = 0; k < 4; k++) {
            assertTrue(porQuadrante[k] <= 1, "o quadrante " + k + " não tem "
                    + "nenhuma direção com um muro ou menos no caminho: quem "
                    + "largar daquele lado não tem tiro");
        }
    }

    @Test
    @DisplayName("os muros de um anel deixam passar entre eles")
    void osMurosDeixamPassar() {
        for (int k = 0; k < ANEIS.size(); k++) {
            double arco = 2 * Math.PI * ANEIS.get(k) / MUROS_POR_ANEL.get(k);
            double vao = arco - MURO_COMPRIMENTO;
            assertTrue(vao >= 3, "no anel de raio " + ANEIS.get(k) + " sobram "
                    + Math.round(vao) + " bloco(s) entre dois muros: o anel "
                    + "virou parede fechada");
        }
        int antes = 0;
        for (int anel : ANEIS) {
            assertTrue(anel > antes + 4, "o anel " + anel + " está a "
                    + (anel - antes) + " do anterior: não dá para andar de um "
                    + "muro para o outro");
            antes = anel;
        }
        assertTrue(ANEIS.get(ANEIS.size() - 1) <= RAIO - 4,
                "o anel mais de fora está no raio "
                        + ANEIS.get(ANEIS.size() - 1) + " de " + RAIO);
    }

    @Test
    @DisplayName("o muro de cobertura não vira escada para fora")
    void oMuroNaoViraEscada() {
        assertTrue(MURO_ALTURA + 2 <= PAREDE_ALTURA, "o muro tem "
                + MURO_ALTURA + " fiadas e o muro de fora " + PAREDE_ALTURA
                + ": daria para pular de um para o outro");
        assertTrue(MURO_ALTURA >= 2, "um muro de " + MURO_ALTURA
                + " fiada(s) não cobre quem está de pé");
    }

    @Test
    @DisplayName("a cobertura não tem bloco repetido nem fora do campo")
    void aCoberturaEhLimpa() {
        Set<String> vistos = new HashSet<>();
        for (int[] onde : cobertura()) {
            assertTrue(vistos.add(onde[0] + ":" + onde[1]),
                    "o bloco (" + onde[0] + "," + onde[1] + ") aparece duas "
                            + "vezes na cobertura");
            double longe = Math.hypot(onde[0], onde[1]);
            assertTrue(longe <= RAIO - 2, "o bloco (" + onde[0] + ","
                    + onde[1] + ") está a " + Math.round(longe)
                    + " do meio, encostado no muro de fora");
            assertTrue(longe >= 4, "o bloco (" + onde[0] + "," + onde[1]
                    + ") está a " + Math.round(longe) + " do meio, em cima do "
                    + "painel do campo");
        }
    }

    @Test
    @DisplayName("a conferência pega a arena sem muro e a arena fechada")
    void aConferenciaPegaACobertura() {
        // Sem anel nenhum.
        assertNotNull(ArcoBuilder.conferirCobertura(List.of(), List.of(),
                MURO_COMPRIMENTO, MURO_ALTURA, PAREDE_ALTURA, RAIO));
        // Contagem de muros que não bate com os anéis.
        assertNotNull(ArcoBuilder.conferirCobertura(ANEIS, List.of(6, 9),
                MURO_COMPRIMENTO, MURO_ALTURA, PAREDE_ALTURA, RAIO));
        // Comprimento par: o muro não fica centrado.
        assertNotNull(ArcoBuilder.conferirCobertura(ANEIS, MUROS_POR_ANEL, 6,
                MURO_ALTURA, PAREDE_ALTURA, RAIO));
        // Muro da altura do muro de fora: vira escada.
        assertNotNull(ArcoBuilder.conferirCobertura(ANEIS, MUROS_POR_ANEL,
                MURO_COMPRIMENTO, PAREDE_ALTURA - 1, PAREDE_ALTURA, RAIO));
        // Anéis colados um no outro.
        assertNotNull(ArcoBuilder.conferirCobertura(List.of(11, 14, 28),
                MUROS_POR_ANEL, MURO_COMPRIMENTO, MURO_ALTURA, PAREDE_ALTURA,
                RAIO));
        // Anel encostado no muro de fora.
        assertNotNull(ArcoBuilder.conferirCobertura(List.of(11, 20, RAIO - 1),
                MUROS_POR_ANEL, MURO_COMPRIMENTO, MURO_ALTURA, PAREDE_ALTURA,
                RAIO));
        // Anel fechado: muros demais no anel de dentro.
        assertNotNull(ArcoBuilder.conferirCobertura(ANEIS, List.of(20, 9, 12),
                MURO_COMPRIMENTO, MURO_ALTURA, PAREDE_ALTURA, RAIO));
        // Um muro só: cobertura abaixo do mínimo.
        assertNotNull(ArcoBuilder.conferirCobertura(List.of(20),
                List.of(1), 3, MURO_ALTURA, PAREDE_ALTURA, RAIO));
    }

    // ------------------------------------------------------------------
    //  O relógio e as largadas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a graça dá para correr da largada até um muro")
    void aGracaDaParaCorrerAteOMuro() {
        int maisDeFora = ANEIS.get(ANEIS.size() - 1);
        // Um jogador andando faz uns 4,3 blocos por segundo.
        double correr = (RAIO - 3 - maisDeFora) / 4.3;
        assertTrue(GRACA >= correr, "a graça é de " + GRACA + "s e correr da "
                + "largada até o muro mais de fora leva "
                + Math.ceil(correr) + "s");
        assertTrue(GRACA <= TEMPO_LIMITE / 10, "a graça é de " + GRACA
                + "s num limite de " + TEMPO_LIMITE + "s");
        assertNotNull(ArcoBuilder.conferirRelogio(0, TEMPO_LIMITE, RAIO,
                List.of(5)));
        assertNotNull(ArcoBuilder.conferirRelogio(TEMPO_LIMITE / 2,
                TEMPO_LIMITE, RAIO, ANEIS));
    }

    /**
     * A largada fica entre o muro de fora e o anel de muros mais de fora.
     *
     * Este teste pegou um erro de verdade: a primeira versão passava o raio
     * para {@link ArenaComum#largadas}, que recua cinco blocos do que recebe
     * — certo numa arena vazia, errado aqui, porque punha gente largando
     * dentro do anel de muros do raio 28 e alguns em cima deles.
     */
    @Test
    @DisplayName("as largadas ficam na beira, olhando para dentro")
    void asLargadasFicamNaBeira() {
        List<int[]> onde = ArcoCatalog.largadas(MAXIMO, RAIO - 3);
        assertEquals(MAXIMO, onde.size());
        int maisDeFora = ANEIS.get(ANEIS.size() - 1);
        List<int[]> muros = cobertura();
        for (int[] ponto : onde) {
            double longe = Math.hypot(ponto[0], ponto[1]);
            assertTrue(longe > maisDeFora, "uma largada caiu a "
                    + Math.round(longe) + " do meio, dentro do anel de muros "
                    + "mais de fora (" + maisDeFora + ")");
            assertTrue(longe <= RAIO - 1, "uma largada caiu a "
                    + Math.round(longe) + " do meio, no muro de raio "
                    + RAIO);
            for (int[] muro : muros) {
                assertTrue(muro[0] != ponto[0] || muro[1] != ponto[1],
                        "uma largada caiu dentro de um muro de cobertura");
            }
        }
    }

    @Test
    @DisplayName("os alvos de enfeite ficam no muro de fora")
    void osAlvosFicamNoMuro() {
        List<int[]> alvos = ArcoCatalog.pontosDosAlvos(RAIO, 8);
        assertEquals(8, alvos.size());
        List<String> vistos = new ArrayList<>();
        for (int[] onde : alvos) {
            double longe = Math.hypot(onde[0], onde[1]);
            assertTrue(longe >= RAIO - 3 && longe <= RAIO,
                    "um alvo caiu a " + Math.round(longe) + " do meio, longe "
                            + "do muro de raio " + RAIO);
            assertTrue(vistos.add(onde[0] + ":" + onde[1])
                            || !vistos.contains(onde[0] + ":" + onde[1]),
                    "dois alvos no mesmo lugar");
        }
    }

    @Test
    @DisplayName("o campo é maior que as outras arenas, porque arco pede longe")
    void oCampoEhGrande() {
        assertTrue(RAIO >= 30, "um campo de arco de raio " + RAIO
                + " é curto: a flecha chega antes de o alvo reagir, e o jogo "
                + "deixa de ter mira e passa a ter reflexo");
        assertTrue(ArcoCatalog.blocosDoChao(RAIO) > 3000,
                "o campo tem " + ArcoCatalog.blocosDoChao(RAIO)
                        + " blocos de chão");
    }
}
