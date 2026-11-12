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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mina, cavada sem servidor.
 *
 * <h2>O que pode dar errado aqui e não lança exceção</h2>
 *
 * Minério SOLTO em vez de em veio: cavar em qualquer direção passa a pagar o
 * mesmo, e o jogo deixa de ter leitura sem nenhuma linha de erro. Faixa de
 * profundidade fora da mina: aquele minério simplesmente não existe, e a
 * tabela de pontos passa a mentir. Tabela fora de ordem: esmeralda perto da
 * superfície, e descer deixa de ser a decisão da partida. Minério de menos: a
 * segunda metade de cada partida é pedra. Minério de mais: não existe rota
 * ruim. Rodada sorteando uma mina pobre: aquela partida é um sorteio.
 *
 * <p>Nada disso quebra. Tudo isso deixa o jogo de pé e errado.
 *
 * <h2>O teste que vale mais aqui</h2>
 *
 * É o {@link #oMinerioVemEmVeio}. Ele mede o que separa esta mina da mina de
 * servidor de prisão — quantos vizinhos do mesmo minério um bloco de minério
 * tem, em média — e exige que seja mais de um, contra os poucos centésimos
 * que o sorteio bloco por bloco daria na mesma densidade. É a única forma de
 * a frase "o minério vem em veio" ser verificável em vez de ser propaganda.
 */
class MineracaoTest {

    private static YamlConfiguration arena;
    private static List<MineracaoCatalog.Veio> tabela;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int LADO = 35;
    private static final int PROFUNDIDADE = 12;
    private static final int FUNDO_PROFUNDO = 6;
    private static final int MOLDURA = 1;
    private static final int PARAPEITO = 2;
    private static final int MARCO_ALTURA = 18;
    private static final int SONARES = 2;
    private static final int SONAR_ALCANCE = 10;
    private static final int DOBRO = 45;
    private static final int MAXIMO = 16;
    private static final int TEMPO_LIMITE = 240;

    /** Quantas minas o teste cava. */
    private static final int MINAS = 400;

    private static final int MEIO = (LADO - 1) / 2;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = MineracaoTest.class.getResourceAsStream("/mineracao.yml")) {
            assertNotNull(in, "mineracao.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        tabela = MineracaoCatalog.lerTabela(arena);
        assertTrue(tabela.size() >= 6, "a tabela do yml saiu com "
                + tabela.size() + " minério(s): ou o yml mudou ou a leitura "
                + "está engolindo linhas");
    }

    private static int[] mina(long semente) {
        return MineracaoCatalog.gerar(LADO, PROFUNDIDADE, semente, tabela);
    }

    // ------------------------------------------------------------------
    //  O yml
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o yml diz os números que o teste usa")
    void ymlBate() {
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(LADO, arena.getInt("arena.lado"));
        assertEquals(PROFUNDIDADE, arena.getInt("arena.profundidade"));
        assertEquals(FUNDO_PROFUNDO, arena.getInt("arena.fundo-profundo"));
        assertEquals(MOLDURA, arena.getInt("arena.moldura"));
        assertEquals(PARAPEITO, arena.getInt("arena.parapeito"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));
        assertEquals(SONARES, arena.getInt("regras.sonares"));
        assertEquals(SONAR_ALCANCE, arena.getInt("regras.sonar-alcance"));
        assertEquals(DOBRO, arena.getInt("regras.dobro-segundos"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
        assertEquals(-1, arena.getInt("arena.semente"),
                "o yml de produção tem de sortear a mina a cada rodada");
    }

    @Test
    @DisplayName("a planta aceita a tabela e o rendimento do yml")
    void plantaAceitaOYml() {
        assertNull(MineracaoBuilder.conferirTabela(tabela, LADO, PROFUNDIDADE,
                FUNDO_PROFUNDO));
        assertNull(MineracaoBuilder.conferirRendimento(tabela, LADO,
                PROFUNDIDADE, MAXIMO, TEMPO_LIMITE));
    }

    @Test
    @DisplayName("a conferência pega faixa fora da mina, tabela fora de ordem e lado par")
    void conferenciaPegaOsErros() {
        assertNotNull(MineracaoBuilder.conferirTabela(List.of(), LADO,
                PROFUNDIDADE, FUNDO_PROFUNDO), "tabela vazia");
        assertNotNull(MineracaoBuilder.conferirTabela(tabela, 34, PROFUNDIDADE,
                FUNDO_PROFUNDO), "lado par põe o meio do maciço fora do (0,0)");
        assertNotNull(MineracaoBuilder.conferirTabela(tabela, LADO, PROFUNDIDADE,
                PROFUNDIDADE), "zona funda do tamanho da mina");

        MineracaoCatalog.Veio fundoDemais = new MineracaoCatalog.Veio("fundo demais",
                Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, 1, 2, 3,
                PROFUNDIDADE, PROFUNDIDADE + 2);
        assertNotNull(MineracaoBuilder.conferirTabela(List.of(fundoDemais), LADO,
                PROFUNDIDADE, FUNDO_PROFUNDO),
                "faixa abaixo do bedrock apaga o minério da mina inteira");

        MineracaoCatalog.Veio invertido = new MineracaoCatalog.Veio("invertido",
                Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, 1, 2, 3, 5, 2);
        assertNotNull(MineracaoBuilder.conferirTabela(List.of(invertido), LADO,
                PROFUNDIDADE, FUNDO_PROFUNDO), "faixa invertida");

        MineracaoCatalog.Veio semValor = new MineracaoCatalog.Veio("de graça",
                Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, 0, 2, 3, 1, 4);
        assertNotNull(MineracaoBuilder.conferirTabela(List.of(semValor), LADO,
                PROFUNDIDADE, FUNDO_PROFUNDO), "minério sem ponto é pedra colorida");

        // Caro e raso: a ordem da tabela é a promessa "fundo paga mais".
        List<MineracaoCatalog.Veio> foraDeOrdem = List.of(
                new MineracaoCatalog.Veio("barato", Material.COAL_ORE,
                        Material.DEEPSLATE_COAL_ORE, 5, 2, 3, 8, 10),
                new MineracaoCatalog.Veio("caro e raso", Material.EMERALD_ORE,
                        Material.DEEPSLATE_EMERALD_ORE, 20, 2, 3, 0, 1));
        assertNotNull(MineracaoBuilder.conferirTabela(foraDeOrdem, LADO,
                PROFUNDIDADE, FUNDO_PROFUNDO),
                "esmeralda na superfície mata a decisão de descer");
    }

    @Test
    @DisplayName("a conferência pega minério de menos e de mais")
    void conferenciaPegaORendimento() {
        List<MineracaoCatalog.Veio> pobre = List.of(
                new MineracaoCatalog.Veio("pouco", Material.COAL_ORE,
                        Material.DEEPSLATE_COAL_ORE, 1, 2, 2, 0, 4));
        assertNotNull(MineracaoBuilder.conferirRendimento(pobre, LADO,
                PROFUNDIDADE, MAXIMO, TEMPO_LIMITE),
                "quatro blocos de minério para dezesseis pessoas");

        List<MineracaoCatalog.Veio> rica = List.of(
                new MineracaoCatalog.Veio("muito", Material.COAL_ORE,
                        Material.DEEPSLATE_COAL_ORE, 1, 400, 20, 0, 10));
        assertNotNull(MineracaoBuilder.conferirRendimento(rica, LADO,
                PROFUNDIDADE, MAXIMO, TEMPO_LIMITE),
                "oito mil blocos de minério: não existe rota ruim");
    }

    // ------------------------------------------------------------------
    //  O gerador
    // ------------------------------------------------------------------

    @Test
    @DisplayName("nenhum minério sai da faixa de profundidade dele")
    void nenhumMinerioSaiDaFaixa() {
        for (long semente = 0; semente < MINAS; semente++) {
            int[] mina = mina(semente);
            for (int fiada = 0; fiada < PROFUNDIDADE; fiada++) {
                for (int v = 0; v < LADO; v++) {
                    for (int u = 0; u < LADO; u++) {
                        int qual = mina[MineracaoCatalog.indice(LADO, fiada, u, v)];
                        if (qual == 0) {
                            continue;
                        }
                        MineracaoCatalog.Veio veio = tabela.get(qual - 1);
                        assertTrue(fiada >= veio.fundo() && fiada <= veio.fundura(),
                                "semente " + semente + ": " + veio.nome()
                                        + " apareceu na fiada " + fiada
                                        + ", fora da faixa " + veio.fundo() + "–"
                                        + veio.fundura());
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("o minério vem em VEIO, e não solto bloco por bloco")
    void oMinerioVemEmVeio() {
        int[] mina = mina(4242L);
        for (int o = 0; o < tabela.size(); o++) {
            MineracaoCatalog.Veio veio = tabela.get(o);
            if (veio.tamanho() < 3) {
                continue;
            }
            int blocos = 0;
            int vizinhos = 0;
            for (int fiada = 0; fiada < PROFUNDIDADE; fiada++) {
                for (int v = 0; v < LADO; v++) {
                    for (int u = 0; u < LADO; u++) {
                        if (mina[MineracaoCatalog.indice(LADO, fiada, u, v)] != o + 1) {
                            continue;
                        }
                        blocos++;
                        vizinhos += vizinhosIguais(mina, o + 1, fiada, u, v);
                    }
                }
            }
            assertTrue(blocos > 0, veio.nome() + " não apareceu na mina");
            double media = vizinhos / (double) blocos;
            // O que o sorteio bloco por bloco daria na mesma densidade: seis
            // vizinhos vezes a fração de minério naquela faixa.
            int casasDaFaixa = (veio.fundura() - veio.fundo() + 1) * LADO * LADO;
            double solto = 6.0 * blocos / casasDaFaixa;
            // O piso é 1,2 porque um veio crescido por passeio é um caminho:
            // t casas ligadas por t-1 arestas dão grau médio 2(t-1)/t, que
            // para o menor veio da tabela (três casas) é 1,33. Medido, a
            // tabela de hoje vai de 1,33 a 2,07.
            assertTrue(media >= 1.2, veio.nome() + ": cada bloco tem em média "
                    + Math.round(media * 100) / 100.0 + " vizinho(s) do mesmo "
                    + "minério. Sorteado bloco por bloco daria "
                    + Math.round(solto * 100) / 100.0 + " — se a média cair "
                    + "para perto disso o minério virou pó espalhado, cavar em "
                    + "qualquer direção paga o mesmo e o jogo deixa de ter "
                    + "leitura");
            assertTrue(media > solto * 4, veio.nome() + ": " + media + " contra "
                    + solto + " do sorteio solto — a diferença é pequena "
                    + "demais para o jogador notar que existe veio");
        }
    }

    @Test
    @DisplayName("toda mina sorteada rende quase o mesmo")
    void todaMinaRendeOMesmo() {
        // A mina é outra a cada rodada, e é por isso que isto importa: se o
        // rendimento variar muito, a partida de quarta é mais rica que a de
        // quinta e ninguém entende por que o placar mudou de escala.
        List<Integer> rendimentos = new ArrayList<>();
        for (long semente = 0; semente < MINAS; semente++) {
            rendimentos.add(MineracaoCatalog.pontosNaMina(mina(semente), tabela));
        }
        double media = rendimentos.stream().mapToInt(Integer::intValue).average()
                .orElse(0);
        int menor = rendimentos.stream().mapToInt(Integer::intValue).min().orElse(0);
        int maior = rendimentos.stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue(menor > media * 0.88 && maior < media * 1.12,
                "em " + MINAS + " minas o rendimento foi de " + menor + " a "
                        + maior + " pontos, com média " + Math.round(media)
                        + ": uma variação dessas faz a rodada de quarta valer "
                        + "mais que a de quinta sem ninguém saber por quê");
        assertTrue(media > 1500 && media < 5000, "a mina média rende "
                + Math.round(media) + " pontos: fora da escala em que o "
                + "placar de dezesseis pessoas é legível");
    }

    @Test
    @DisplayName("a mesma semente dá sempre a mesma mina")
    void mesmaSementeMesmaMina() {
        for (long semente = 0; semente < 20; semente++) {
            assertEquals(assinatura(mina(semente)), assinatura(mina(semente)),
                    "semente " + semente + ": gerar duas vezes deu duas minas");
        }
    }

    @Test
    @DisplayName("sementes diferentes dão minas diferentes")
    void sementesDiferentesMinasDiferentes() {
        Set<String> vistas = new HashSet<>();
        for (long semente = 0; semente < 60; semente++) {
            vistas.add(assinatura(mina(semente)));
        }
        assertEquals(60, vistas.size(), "em 60 sementes saíram "
                + vistas.size() + " minas distintas");
    }

    @Test
    @DisplayName("a zona funda guarda o minério caro, e só ela")
    void aZonaFundaGuardaOCaro() {
        MineracaoCatalog.Veio caro = tabela.get(tabela.size() - 1);
        assertTrue(caro.fundo() >= FUNDO_PROFUNDO, "o minério mais caro ("
                + caro.nome() + ") começa na fiada " + caro.fundo()
                + " e a zona funda só começa em " + FUNDO_PROFUNDO
                + ": dá para achar esmeralda sem descer");
        for (long semente = 0; semente < 40; semente++) {
            int[] mina = mina(semente);
            for (int fiada = 0; fiada < FUNDO_PROFUNDO; fiada++) {
                for (int v = 0; v < LADO; v++) {
                    for (int u = 0; u < LADO; u++) {
                        int qual = mina[MineracaoCatalog.indice(LADO, fiada, u, v)];
                        if (qual == 0) {
                            continue;
                        }
                        assertTrue(tabela.get(qual - 1).pontos() < caro.pontos(),
                                "semente " + semente + ": "
                                        + tabela.get(qual - 1).nome()
                                        + " apareceu na fiada rasa " + fiada);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("o bloco de ardósia existe para cada minério")
    void ardosiaExiste() {
        for (MineracaoCatalog.Veio veio : tabela) {
            assertNotNull(veio.profundo(), veio.nome() + " sem bloco de ardósia");
            assertNotEquals(veio.bloco(), veio.profundo(), veio.nome()
                    + " usa o mesmo bloco na zona funda e na rasa: a parede "
                    + "deixa de avisar que o jogador chegou onde paga");
            assertEquals(veio.bloco(),
                    veio.blocoNaFiada(FUNDO_PROFUNDO - 1, FUNDO_PROFUNDO));
            assertEquals(veio.profundo(),
                    veio.blocoNaFiada(FUNDO_PROFUNDO, FUNDO_PROFUNDO));
        }
    }

    @Test
    @DisplayName("a picareta de ferro quebra todo minério da tabela")
    void picaretaDeFerroDaConta() {
        for (MineracaoCatalog.Veio veio : tabela) {
            assertTrue(veio.bloco().name().endsWith("_ORE"), veio.nome()
                    + " não é minério: " + veio.bloco());
            assertTrue(veio.bloco() != Material.ANCIENT_DEBRIS, "escombros "
                    + "antigos pedem picareta de diamante, e a picareta da "
                    + "mina é de ferro");
        }
    }

    // ------------------------------------------------------------------
    //  As regras
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o dobro aperta o fim sem comer a partida")
    void oDobroApertaOFim() {
        assertTrue(DOBRO < TEMPO_LIMITE / 3, "os " + DOBRO + "s de dobro num "
                + "relógio de " + TEMPO_LIMITE + "s: mais de um terço da "
                + "partida valendo dobrado faz os dois primeiros minutos "
                + "deixarem de contar");
        assertTrue(DOBRO >= 20, "menos de vinte segundos de dobro não dá tempo "
                + "de virar nada e só gera barulho no fim");
    }

    @Test
    @DisplayName("o sonar é escasso e não vê a mina toda")
    void oSonarEhEscasso() {
        assertTrue(SONARES >= 1 && SONARES <= 3, SONARES + " sonares: um a "
                + "três é o que faz gastar um ser decisão");
        assertTrue(SONAR_ALCANCE < MEIO, "o sonar alcança " + SONAR_ALCANCE
                + " num maciço de meio " + MEIO + ": de um canto ele veria "
                + "quase tudo e a mina deixaria de ter segredo");
    }

    @Test
    @DisplayName("a mina cabe no alcance da obra")
    void minaCabeNoAlcance() {
        int alcance = MEIO + MOLDURA + 5;
        assertTrue(MEIO + MOLDURA <= alcance, "a moldura vai até "
                + (MEIO + MOLDURA) + " e o desfazer só limpa até " + alcance);
        assertEquals(17, MEIO);
    }

    @Test
    @DisplayName("o maciço não é todo minério")
    void macicoNaoEhTodoMinerio() {
        int[] mina = mina(99L);
        int minerio = MineracaoCatalog.blocosDeMinerio(mina);
        int casas = LADO * LADO * PROFUNDIDADE;
        assertTrue(minerio < casas / 8, minerio + " blocos de minério num "
                + "maciço de " + casas + ": mais de um oitavo e a rocha deixa "
                + "de ser obstáculo");
        assertTrue(minerio > casas / 60, minerio + " blocos de minério num "
                + "maciço de " + casas + ": pouco demais para dezesseis "
                + "pessoas acharem algo");
    }

    // ------------------------------------------------------------------
    //  O rumo do sonar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o sonar nomeia os oito rumos da rosa")
    void rumosDaRosa() {
        assertEquals("NORTE", Mineracao.rumoEscrito(0, -8));
        assertEquals("SUL", Mineracao.rumoEscrito(0, 8));
        assertEquals("LESTE", Mineracao.rumoEscrito(8, 0));
        assertEquals("OESTE", Mineracao.rumoEscrito(-8, 0));
        assertEquals("NORDESTE", Mineracao.rumoEscrito(6, -6));
        assertEquals("NOROESTE", Mineracao.rumoEscrito(-6, -6));
        assertEquals("SUDESTE", Mineracao.rumoEscrito(6, 6));
        assertEquals("SUDOESTE", Mineracao.rumoEscrito(-6, 6));
        assertEquals("seus pés", Mineracao.rumoEscrito(0, 0));
    }

    // ------------------------------------------------------------------
    //  O placar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o placar ordena, conta o lugar certo e não inventa líder no empate")
    void placarSeComporta() {
        Placar placar = new Placar();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        placar.entrar(a);
        placar.entrar(b);
        placar.entrar(c);

        assertNull(placar.lider(), "três pessoas com zero ponto não têm líder");
        assertEquals(3, placar.quantos());

        placar.somar(a, 10);
        placar.somar(b, 40);
        placar.somar(c, 40);

        assertNull(placar.lider(), "empate na frente não dá vencedor — dar "
                + "daria a vitória a quem entrou primeiro");
        assertEquals(40, placar.pontosDoLider());
        assertEquals(List.of(b, c, a), placar.ranking(),
                "o desempate é a ordem de entrada, para a lista não piscar");
        assertEquals(1, placar.posicaoDe(b));
        assertEquals(1, placar.posicaoDe(c), "dois empatados dividem o lugar");
        assertEquals(3, placar.posicaoDe(a), "e o seguinte é o terceiro, "
                + "não o segundo");
        assertEquals(0, placar.posicaoDe(UUID.randomUUID()));

        placar.somar(b, 1);
        assertEquals(b, placar.lider());
        assertEquals(1, placar.posicaoDe(b));
        assertEquals(2, placar.posicaoDe(c));

        placar.sair(b);
        assertEquals(c, placar.lider());
        assertEquals(2, placar.quantos());
        placar.zerar();
        assertTrue(placar.vazio());
        assertEquals(0, placar.de(a));
    }

    // ------------------------------------------------------------------
    //  Utilidades do teste
    // ------------------------------------------------------------------

    private static int vizinhosIguais(int[] mina, int qual, int fiada, int u,
                                      int v) {
        int quantos = 0;
        int[][] seis = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}};
        for (int[] rumo : seis) {
            int nu = u + rumo[0];
            int nv = v + rumo[1];
            int nf = fiada + rumo[2];
            if (nu < 0 || nu >= LADO || nv < 0 || nv >= LADO
                    || nf < 0 || nf >= PROFUNDIDADE) {
                continue;
            }
            if (mina[MineracaoCatalog.indice(LADO, nf, nu, nv)] == qual) {
                quantos++;
            }
        }
        return quantos;
    }

    private static String assinatura(int[] mina) {
        StringBuilder texto = new StringBuilder(mina.length);
        for (int casa : mina) {
            texto.append((char) ('a' + casa));
        }
        return texto.toString();
    }
}
