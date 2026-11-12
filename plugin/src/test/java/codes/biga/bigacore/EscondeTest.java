package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As contas do ESCONDE, conferidas sem servidor.
 *
 * <h2>Os dois testes que valem mais aqui</h2>
 *
 * {@link #osDoisPapeisValemOMesmo} é o teste de um defeito que não aparece
 * jogando: se caçar pagar melhor que esconder-se, o SORTEIO DO PAPEL decide a
 * partida antes de ela começar, e ninguém reclama porque ninguém sabe. Ele
 * confere a parida de dois em dois jogadores até dezesseis, porque o número
 * de escondidos por caçador muda de um para quatro ao longo dessa faixa — um
 * {@code pontos-por-captura} fixo no yml estaria certo para um tamanho de
 * partida e errado para todos os outros.
 *
 * <p>{@link #aCoberturaComecaSobrandoETerminaApertada} confere a outra ponta:
 * a arena tem de oferecer esconderijo de sobra no começo e quase nenhum no
 * fim. Com cobertura constante, toda partida termina igual — no relógio, com
 * os bons escondidos parados onde entraram, o que não é um jogo, é uma espera.
 *
 * <h2>E o que prova que o disfarce é disfarce</h2>
 *
 * {@link #soSeEncaixaNaGrade}: só se vira caixote onde um caixote caberia. Um
 * caixote fora da grade de quatro em quatro está denunciado pelo alinhamento,
 * e sem essa regra quem jogasse duas vezes acharia todo mundo sem olhar para
 * nada além disso.
 */
class EscondeTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int RAIO = 30;
    private static final int PAREDE_ALTURA = 7;
    private static final int PILAR_PASSO = 10;
    private static final int PILAR_RAIO = 1;
    private static final int PILAR_ALTURA = 4;
    private static final int CAIXOTE_PASSO = 4;
    private static final int CABINE_RAIO = 2;
    private static final int CABINE_ALTURA = 4;
    private static final int MARCO_ALTURA = 18;
    private static final double FRACAO = 0.2;
    private static final int SEGREDO = 25;
    private static final int ENCAIXE_TIQUES = 20;
    private static final int POR_ESCONDIDO = 7;
    private static final int NO_FIM = 1;
    private static final int ONDAS = 4;
    private static final int PONTOS_POR_SEGUNDO = 1;
    private static final int MINIMO = 2;
    private static final int MAXIMO = 16;
    private static final int TEMPO_LIMITE = 240;

    private static final int PREMIO = TEMPO_LIMITE * PONTOS_POR_SEGUNDO;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = EscondeTest.class.getResourceAsStream("/esconde.yml")) {
            assertNotNull(in, "esconde.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static List<int[]> grade() {
        return EscondeCatalog.pontosDosCaixotes(CAIXOTE_PASSO, RAIO,
                PILAR_PASSO, PILAR_RAIO, CABINE_RAIO);
    }

    private static List<int[]> pilares() {
        return EscondeCatalog.pontosDosPilares(PILAR_PASSO, RAIO, CABINE_RAIO);
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
        assertEquals(PILAR_PASSO, arena.getInt("arena.pilar-passo"));
        assertEquals(PILAR_RAIO, arena.getInt("arena.pilar-raio"));
        assertEquals(PILAR_ALTURA, arena.getInt("arena.pilar-altura"));
        assertEquals(CAIXOTE_PASSO, arena.getInt("arena.caixote-passo"));
        assertEquals(CABINE_RAIO, arena.getInt("arena.cabine-raio"));
        assertEquals(CABINE_ALTURA, arena.getInt("arena.cabine-altura"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));
        assertEquals(FRACAO, arena.getDouble("regras.fracao-cacadores"), 1e-9);
        assertEquals(SEGREDO, arena.getInt("regras.segredo-segundos"));
        assertEquals(ENCAIXE_TIQUES, arena.getInt("regras.encaixe-tiques"));
        assertEquals(POR_ESCONDIDO,
                arena.getInt("regras.caixotes-por-escondido"));
        assertEquals(NO_FIM,
                arena.getInt("regras.caixotes-no-fim-por-escondido"));
        assertEquals(ONDAS, arena.getInt("regras.ondas"));
        assertEquals(PONTOS_POR_SEGUNDO,
                arena.getInt("regras.pontos-por-segundo"));
        assertEquals(MINIMO, arena.getInt("regras.minimo"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
    }

    @Test
    @DisplayName("os números do yml passam nas três conferências")
    void aPlantaPassaNaConferencia() {
        assertNull(EscondeBuilder.conferirNumeros(RAIO, PAREDE_ALTURA,
                PILAR_PASSO, PILAR_RAIO, PILAR_ALTURA, CAIXOTE_PASSO,
                CABINE_RAIO, CABINE_ALTURA));
        assertNull(EscondeBuilder.conferirCobertura(grade().size(), MAXIMO,
                FRACAO, POR_ESCONDIDO, NO_FIM, ONDAS));
        assertNull(EscondeBuilder.conferirRelogio(SEGREDO, ONDAS,
                TEMPO_LIMITE, RAIO));
    }

    @Test
    @DisplayName("o painel explica o encaixe, que é a regra que ninguém adivinha")
    void oPainelExplicaOEncaixe() {
        List<String> regras = arena.getStringList("textos.regras");
        assertTrue(regras.size() >= 5, "o painel tem só " + regras.size()
                + " linha(s)");
        assertTrue(regras.stream().anyMatch(
                        linha -> linha.toUpperCase().contains("ENCAIXA")),
                "nenhuma linha fala do encaixe: " + regras);
        assertTrue(regras.stream().anyMatch(
                        linha -> linha.contains("caixote")),
                "nenhuma linha diz que o jogador vira um caixote: " + regras);
        assertNotNull(arena.getString("paleta.caixote"),
                "falta paleta.caixote, que é o enfeite E o disfarce");
    }

    // ------------------------------------------------------------------
    //  Os papéis
    // ------------------------------------------------------------------

    /**
     * Quem caça e quem se esconde têm de terminar com o mesmo, em qualquer
     * tamanho de partida.
     *
     * A parte de um caçador é {@code escondidos / caçadores} capturas, e ela
     * tem de valer o mesmo que atravessar a partida inteira escondido. Entre
     * dois e dezesseis jogadores essa razão vai de um a mais de quatro, então
     * um número fixo de pontos por captura acertaria um tamanho de partida e
     * erraria todos os outros — e o erro é um sorteio de papel que decide a
     * partida.
     */
    @Test
    @DisplayName("os dois papéis valem o mesmo em qualquer número de jogadores")
    void osDoisPapeisValemOMesmo() {
        for (int n = MINIMO; n <= MAXIMO; n++) {
            int caca = EscondeCatalog.cacadores(n, FRACAO);
            int esconde = EscondeCatalog.escondidos(n, FRACAO);
            int valor = EscondeCatalog.pontosPorCaptura(caca, esconde, PREMIO);
            double parte = esconde / (double) caca;
            double doCacador = valor * parte;
            assertTrue(Math.abs(doCacador - PREMIO) <= PREMIO * 0.05,
                    "com " + n + " jogadores (" + caca + " caçando, "
                            + esconde + " escondidos) a parte de um caçador "
                            + "vale " + Math.round(doCacador) + " e sobreviver "
                            + "vale " + PREMIO + ": o sorteio do papel "
                            + "decidiria a partida");
            assertTrue(valor >= 1, "com " + n + " jogadores a captura vale "
                    + valor);
            assertTrue(valor <= PREMIO, "com " + n + " jogadores a captura "
                    + "vale " + valor + ", mais que a partida inteira");
        }
    }

    @Test
    @DisplayName("o sorteio nunca deixa um lado vazio")
    void oSorteioNuncaDeixaUmLadoVazio() {
        for (int n = MINIMO; n <= MAXIMO; n++) {
            int caca = EscondeCatalog.cacadores(n, FRACAO);
            assertTrue(caca >= 1, n + " jogadores e nenhum caçador: a partida "
                    + "seria uma espera de " + TEMPO_LIMITE + "s");
            assertTrue(caca < n, n + " jogadores e " + caca + " caçadores: "
                    + "não haveria o que achar");
        }
        // Fração mal escrita no yml não pode esvaziar nenhum lado.
        assertEquals(1, EscondeCatalog.cacadores(10, 0.0));
        assertEquals(9, EscondeCatalog.cacadores(10, 1.0));
        assertEquals(1, EscondeCatalog.cacadores(1, 0.2));
    }

    @Test
    @DisplayName("com dezesseis dá três caçadores e treze escondidos")
    void aArenaCheiaTemTresCacadores() {
        assertEquals(3, EscondeCatalog.cacadores(MAXIMO, FRACAO));
        assertEquals(13, EscondeCatalog.escondidos(MAXIMO, FRACAO));
        assertEquals(55, EscondeCatalog.pontosPorCaptura(3, 13, PREMIO));
    }

    // ------------------------------------------------------------------
    //  A cobertura
    // ------------------------------------------------------------------

    /**
     * A cobertura começa sobrando e termina apertada, em qualquer tamanho de
     * partida — e por isso ela é contada POR ESCONDIDO.
     */
    @Test
    @DisplayName("a cobertura começa sobrando e termina apertada")
    void aCoberturaComecaSobrandoETerminaApertada() {
        for (int n = MINIMO; n <= MAXIMO; n++) {
            int esconde = Math.max(1, EscondeCatalog.escondidos(n, FRACAO));
            int comeco = EscondeCatalog.caixotesDaOnda(0, esconde, ONDAS,
                    POR_ESCONDIDO, NO_FIM);
            int fim = EscondeCatalog.caixotesDaOnda(ONDAS, esconde, ONDAS,
                    POR_ESCONDIDO, NO_FIM);
            assertTrue(comeco >= esconde * 3,
                    "com " + esconde + " escondidos a partida começa com "
                            + comeco + " caixotes: não há onde não estar");
            assertTrue(fim <= esconde * 2,
                    "com " + esconde + " escondidos a última onda deixa "
                            + fim + " caixotes: dá para ficar parado até o "
                            + "apito");
            int antes = comeco;
            for (int onda = 1; onda <= ONDAS; onda++) {
                int agora = EscondeCatalog.caixotesDaOnda(onda, esconde,
                        ONDAS, POR_ESCONDIDO, NO_FIM);
                assertTrue(agora <= antes, "a onda " + onda + " com "
                        + esconde + " escondidos AUMENTOU a cobertura, de "
                        + antes + " para " + agora);
                antes = agora;
            }
        }
    }

    @Test
    @DisplayName("a grade do chão aguenta a arena cheia")
    void aGradeAguentaAArenaCheia() {
        int lugares = grade().size();
        int precisa = EscondeCatalog.escondidos(MAXIMO, FRACAO) * POR_ESCONDIDO;
        assertTrue(lugares >= precisa, "a arena cheia quer " + precisa
                + " caixotes e a grade só tem " + lugares + " lugares");
        // E tem de sobrar lugar VAZIO para os escondidos encaixarem.
        assertTrue(lugares - precisa >= EscondeCatalog.escondidos(MAXIMO, FRACAO),
                "sobram só " + (lugares - precisa) + " lugares vazios para "
                        + EscondeCatalog.escondidos(MAXIMO, FRACAO)
                        + " escondidos encaixarem");
    }

    @Test
    @DisplayName("as ondas caem dentro da caça, e a última antes do apito")
    void asOndasCaemDentroDaCaca() {
        int antes = SEGREDO;
        for (int onda = 1; onda <= ONDAS; onda++) {
            int quando = EscondeCatalog.segundoDaOnda(onda, ONDAS, SEGREDO,
                    TEMPO_LIMITE);
            assertTrue(quando > antes, "a onda " + onda + " cai no segundo "
                    + quando + ", junto com a anterior");
            assertTrue(quando < TEMPO_LIMITE - 15, "a onda " + onda
                    + " cai no segundo " + quando + " de " + TEMPO_LIMITE
                    + ": tarde para mudar alguma coisa");
            antes = quando;
        }
    }

    @Test
    @DisplayName("a conferência pega a cobertura que não aperta")
    void aConferenciaPegaACobertura() {
        int lugares = grade().size();
        // Não diminui.
        assertNotNull(EscondeBuilder.conferirCobertura(lugares, MAXIMO, FRACAO,
                7, 7, ONDAS));
        // Diminui, mas termina folgada.
        assertNotNull(EscondeBuilder.conferirCobertura(lugares, MAXIMO, FRACAO,
                7, 3, ONDAS));
        // Começa sem cobertura nenhuma.
        assertNotNull(EscondeBuilder.conferirCobertura(lugares, MAXIMO, FRACAO,
                2, 1, ONDAS));
        // A grade não cabe o que o yml promete.
        assertNotNull(EscondeBuilder.conferirCobertura(40, MAXIMO, FRACAO,
                POR_ESCONDIDO, NO_FIM, ONDAS));
        // Uma onda só.
        assertNotNull(EscondeBuilder.conferirCobertura(lugares, MAXIMO, FRACAO,
                POR_ESCONDIDO, NO_FIM, 1));
    }

    // ------------------------------------------------------------------
    //  O chão
    // ------------------------------------------------------------------

    /**
     * Só se vira caixote onde um caixote caberia.
     *
     * Fora da grade de quatro em quatro, dentro de pilar, dentro da cabine ou
     * encostado no muro: em nenhum desses lugares o disfarce passa, e o
     * motivo é sempre o mesmo — um caixote onde nunca houve caixote está
     * denunciado sem ninguém precisar olhar duas vezes.
     */
    @Test
    @DisplayName("só se encaixa onde cabe um caixote")
    void soSeEncaixaNaGrade() {
        assertTrue(EscondeCatalog.naGrade(16, 0, CAIXOTE_PASSO, RAIO,
                PILAR_PASSO, PILAR_RAIO, CABINE_RAIO));
        // Fora da grade: um bloco ao lado.
        assertFalse(EscondeCatalog.naGrade(17, 0, CAIXOTE_PASSO, RAIO,
                PILAR_PASSO, PILAR_RAIO, CABINE_RAIO));
        // Dentro de um pilar.
        assertFalse(EscondeCatalog.naGrade(20, 0, CAIXOTE_PASSO, RAIO,
                PILAR_PASSO, PILAR_RAIO, CABINE_RAIO));
        // Dentro da cabine.
        assertFalse(EscondeCatalog.naGrade(0, 0, CAIXOTE_PASSO, RAIO,
                PILAR_PASSO, PILAR_RAIO, CABINE_RAIO));
        // Encostado no muro.
        assertFalse(EscondeCatalog.naGrade(0, RAIO, CAIXOTE_PASSO, RAIO,
                PILAR_PASSO, PILAR_RAIO, CABINE_RAIO));
        // E a grade e a conferência dizem a mesma coisa.
        Set<String> daLista = new HashSet<>();
        for (int[] onde : grade()) {
            daLista.add(onde[0] + ":" + onde[1]);
            assertTrue(EscondeCatalog.naGrade(onde[0], onde[1], CAIXOTE_PASSO,
                            RAIO, PILAR_PASSO, PILAR_RAIO, CABINE_RAIO),
                    "a grade tem (" + onde[0] + "," + onde[1] + ") e a "
                            + "conferência de encaixe diz que não");
        }
        assertEquals(grade().size(), daLista.size(),
                "a grade tem lugares repetidos");
    }

    @Test
    @DisplayName("nenhum lugar de caixote cai em pilar, cabine ou muro")
    void aGradeNaoEncostaEmNada() {
        for (int[] onde : grade()) {
            assertFalse(EscondeCatalog.noPilar(onde[0], onde[1], PILAR_PASSO,
                            PILAR_RAIO, RAIO, CABINE_RAIO),
                    "o lugar (" + onde[0] + "," + onde[1] + ") está num pilar");
            assertFalse(EscondeCatalog.naCabine(onde[0], onde[1], CABINE_RAIO),
                    "o lugar (" + onde[0] + "," + onde[1] + ") está na cabine");
            assertTrue(Math.hypot(onde[0], onde[1]) <= RAIO - 2,
                    "o lugar (" + onde[0] + "," + onde[1] + ") está a "
                            + Math.round(Math.hypot(onde[0], onde[1]))
                            + " do meio, encostado no muro de raio " + RAIO);
        }
    }

    @Test
    @DisplayName("os pilares não se encostam e deixam passar entre eles")
    void osPilaresDeixamPassar() {
        List<int[]> todos = pilares();
        assertTrue(todos.size() >= 12, "só " + todos.size() + " pilares num "
                + "galpão de raio " + RAIO + ": não cortam vista nenhuma");
        for (int[] um : todos) {
            assertTrue(Math.hypot(um[0], um[1]) <= RAIO - 2,
                    "o pilar (" + um[0] + "," + um[1] + ") está no muro");
            assertFalse(EscondeCatalog.naCabine(um[0], um[1], CABINE_RAIO),
                    "o pilar (" + um[0] + "," + um[1] + ") está na cabine");
            for (int[] outro : todos) {
                if (um == outro) {
                    continue;
                }
                int vao = Math.max(Math.abs(um[0] - outro[0]),
                        Math.abs(um[1] - outro[1])) - (PILAR_RAIO * 2 + 1);
                assertTrue(vao >= 2, "entre os pilares (" + um[0] + ","
                        + um[1] + ") e (" + outro[0] + "," + outro[1]
                        + ") sobram " + vao + " blocos: não passa gente");
            }
        }
    }

    @Test
    @DisplayName("o centro do bloco é o centro do bloco")
    void oCentroDoBlocoEhOCentro() {
        double[] meio = EscondeCatalog.centroDoBloco(16.9, -3.1);
        assertEquals(16.5, meio[0], 1e-9);
        assertEquals(-4 + 0.5, meio[1], 1e-9);
        double[] negativo = EscondeCatalog.centroDoBloco(-0.2, -0.8);
        assertEquals(-0.5, negativo[0], 1e-9);
        assertEquals(-0.5, negativo[1], 1e-9);
    }

    @Test
    @DisplayName("as largadas nascem no galpão, longe da cabine e do muro")
    void asLargadasNascemNoGalpao() {
        List<int[]> onde = ArenaComum.largadas(MAXIMO, RAIO - 6);
        assertEquals(MAXIMO, onde.size());
        for (int[] ponto : onde) {
            double longe = Math.hypot(ponto[0], ponto[1]);
            assertTrue(longe > CABINE_RAIO + 4, "uma largada caiu a " + longe
                    + " do meio, dentro ou colada na cabine");
            assertTrue(longe <= RAIO - 3, "uma largada caiu a " + longe
                    + " do meio, colada no muro de raio " + RAIO);
        }
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a conferência pega pilar que vira parede e pilar que vira escada")
    void aConferenciaPegaOsPilares() {
        // Pilares colados: o galpão viraria um labirinto.
        assertNotNull(EscondeBuilder.conferirNumeros(RAIO, PAREDE_ALTURA, 4, 1,
                PILAR_ALTURA, CAIXOTE_PASSO, CABINE_RAIO, CABINE_ALTURA));
        // Pilar baixo: não corta a vista de quem está de pé.
        assertNotNull(EscondeBuilder.conferirNumeros(RAIO, PAREDE_ALTURA,
                PILAR_PASSO, PILAR_RAIO, 2, CAIXOTE_PASSO, CABINE_RAIO,
                CABINE_ALTURA));
        // Pilar da altura do muro: vira escada para sair.
        assertNotNull(EscondeBuilder.conferirNumeros(RAIO, PAREDE_ALTURA,
                PILAR_PASSO, PILAR_RAIO, PAREDE_ALTURA, CAIXOTE_PASSO,
                CABINE_RAIO, CABINE_ALTURA));
    }

    @Test
    @DisplayName("a conferência pega a cabine que vira mirante")
    void aConferenciaPegaACabine() {
        assertNotNull(EscondeBuilder.conferirNumeros(RAIO, PAREDE_ALTURA,
                PILAR_PASSO, PILAR_RAIO, PILAR_ALTURA, CAIXOTE_PASSO,
                CABINE_RAIO, PAREDE_ALTURA + 1));
        assertNotNull(EscondeBuilder.conferirNumeros(RAIO, PAREDE_ALTURA,
                PILAR_PASSO, PILAR_RAIO, PILAR_ALTURA, CAIXOTE_PASSO,
                CABINE_RAIO, 2));
        // Cabine do tamanho do galpão.
        assertNotNull(EscondeBuilder.conferirNumeros(RAIO, PAREDE_ALTURA,
                PILAR_PASSO, PILAR_RAIO, PILAR_ALTURA, CAIXOTE_PASSO,
                RAIO / 2, CABINE_ALTURA));
    }

    @Test
    @DisplayName("a conferência pega o tempo de esconder curto e longo demais")
    void aConferenciaPegaOTempoDeEsconder() {
        String curto = EscondeBuilder.conferirRelogio(5, ONDAS, TEMPO_LIMITE,
                RAIO);
        assertNotNull(curto);
        assertTrue(curto.contains("atravessar"), curto);
        String longo = EscondeBuilder.conferirRelogio(TEMPO_LIMITE / 2, ONDAS,
                TEMPO_LIMITE, RAIO);
        assertNotNull(longo);
        assertTrue(longo.contains("metade"), longo);
    }

    @Test
    @DisplayName("o tempo de esconder dá para atravessar o galpão")
    void oTempoDeEsconderDaParaAtravessar() {
        // Um jogador andando faz uns 4,3 blocos por segundo.
        double atravessar = RAIO * 2 / 4.3;
        assertTrue(SEGREDO >= atravessar, "o tempo de esconder é de " + SEGREDO
                + "s e atravessar o galpão leva "
                + Math.round(atravessar) + "s");
        assertTrue(SEGREDO < TEMPO_LIMITE / 2, "o tempo de esconder é "
                + SEGREDO + "s de " + TEMPO_LIMITE);
    }

    @Test
    @DisplayName("o encaixe custa um segundo parado, nem mais nem menos")
    void oEncaixeCustaUmSegundo() {
        assertTrue(ENCAIXE_TIQUES >= 10, "encaixar em " + ENCAIXE_TIQUES
                + " tiques: o escondido encaixa ao passar e nunca fica torto");
        assertTrue(ENCAIXE_TIQUES <= 60, "encaixar em " + ENCAIXE_TIQUES
                + " tiques é mais de três segundos parado: quem se mexeu uma "
                + "vez não volta a se esconder dentro da partida");
    }
}
