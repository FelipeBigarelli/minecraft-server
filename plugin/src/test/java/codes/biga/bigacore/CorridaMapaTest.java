package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A corrida sobre um mapa pronto: percurso aberto, de barco, montanha abaixo.
 *
 * <h2>Por que estes testes existem</h2>
 *
 * <p>Um percurso do Monte Akina tem mil e cem blocos de descida. Descobrir em
 * jogo que o sexto portão é incruzável custa dois minutos de barco por
 * tentativa, e descobrir que a pole largou em último custa uma rodada inteira
 * com dezesseis pessoas dentro. Tudo que dá para perguntar sem servidor é
 * perguntado aqui.
 *
 * <h2>O caso que deu origem ao filtro de picos</h2>
 *
 * {@link #oLagoDebaixoDaEstradaNaoViraPortao}. Dois dos quatrocentos pontos do
 * primeiro trecho saíram em y 100 entre vizinhos em y 221: o traçado seguiu o
 * gelo, e havia gelo num lago congelado cento e vinte fiadas ABAIXO da
 * estrada, na mesma coluna. O x e o z continuavam certos. O estrago não era
 * visual — o percurso passava a medir mil e quinhentos blocos em vez de mil e
 * cem, e um dos dez portões nascia no fundo do lago.
 */
class CorridaMapaTest {

    // ------------------------------------------------------------------
    //  A leitura e a limpeza do arquivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o lago debaixo da estrada não vira portão")
    void oLagoDebaixoDaEstradaNaoViraPortao() {
        List<int[]> bruto = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            bruto.add(new int[]{580 - i, 221, 200 + i * 3});
        }
        // O pico de verdade: x e z continuam, só a fiada despenca e volta.
        bruto.get(7)[1] = 100;
        bruto.get(12)[1] = 100;

        List<int[]> limpo = PistaAberta.tirarPicos(bruto, 8);
        assertEquals(bruto.size(), limpo.size(), "limpar não é encurtar");
        for (int[] ponto : limpo) {
            assertEquals(221, ponto[1], "sobrou pico em "
                    + ponto[0] + "," + ponto[2]);
        }
        assertEquals(580 - 7, limpo.get(7)[0], "o x do ponto não se mexe");
        assertEquals(200 + 7 * 3, limpo.get(7)[2], "o z do ponto não se mexe");
    }

    @Test
    @DisplayName("dois picos seguidos não servem de álibi um para o outro")
    void doisPicosSeguidos() {
        // Se o filtro olhasse o vizinho de trás CRU, o segundo pico acharia
        // um vizinho em y 100 atrás dele e passaria por estrada.
        List<int[]> bruto = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            bruto.add(new int[]{i, 200, 0});
        }
        bruto.get(4)[1] = 100;
        bruto.get(5)[1] = 100;

        List<int[]> limpo = PistaAberta.tirarPicos(bruto, 8);
        assertEquals(200, limpo.get(4)[1]);
        assertEquals(200, limpo.get(5)[1]);
    }

    @Test
    @DisplayName("uma rampa de verdade não é pico")
    void aRampaNaoEhPico() {
        // O contrário do caso acima, e o perigoso: o Akina cai cento e trinta
        // e quatro fiadas, e um filtro ganancioso aplainaria a montanha.
        List<int[]> bruto = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            bruto.add(new int[]{i, 220 - i * 4, 0});
        }
        List<int[]> limpo = PistaAberta.tirarPicos(bruto, 8);
        for (int i = 0; i < bruto.size(); i++) {
            assertEquals(220 - i * 4, limpo.get(i)[1],
                    "a rampa foi aplainada no ponto " + i);
        }
    }

    @Test
    @DisplayName("linha torta é pulada e o resto do arquivo vale")
    void aLinhaTorta() {
        List<int[]> lidos = PistaAberta.ler(List.of(
                "# o alto do monte", "", "575 223 160", "nao sou numero",
                "576 223", "576 222 162", "   577 222 164   "));
        assertEquals(3, lidos.size(), "pulou linha boa ou aceitou linha ruim");
        assertEquals(575, lidos.get(0)[0]);
        assertEquals(164, lidos.get(2)[2]);
    }

    // ------------------------------------------------------------------
    //  O eixo e os portões
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a reamostragem mede a descida em três dimensões")
    void aReamostragemMedeAsTres() {
        // Vinte blocos no plano e quinze de queda são vinte e cinco de
        // distância real. Medida só no plano, a rampa levaria portão a menos
        // — e é a rampa que é o jogo.
        List<double[]> eixo = PistaAberta.reamostrar(
                List.of(new int[]{0, 100, 0}, new int[]{20, 85, 0}), 5.0);
        assertEquals(6, eixo.size(), "25 blocos de 5 em 5 dão 6 pontos");
        assertEquals(25.0, PistaAberta.comprimento(eixo), 0.001);
    }

    @Test
    @DisplayName("a largada não é portão e o último portão é a chegada")
    void aLargadaNaoEhPortao() {
        int[] ps = PistaAberta.portoes(10, 500);
        assertEquals(10, ps.length);
        assertTrue(ps[0] > 0, "portão no ponto zero é portão que o jogador "
                + "já está do lado de lá: nunca cruzaria");
        assertEquals(495, ps[9], "a chegada tem de ficar ANTES da ponta do "
                + "eixo: quem para em cima do plano não o atravessou, e numa "
                + "estrada sem continuação a corrida nunca acabaria");
        assertTrue(ps[9] < 499 && ps[9] > 480, "recuo demais ou de menos");
        for (int k = 1; k < ps.length; k++) {
            assertTrue(ps[k] > ps[k - 1], "portões fora de ordem: " + ps[k - 1]
                    + " depois de " + ps[k]);
        }
    }

    @Test
    @DisplayName("descer o percurso inteiro cruza todos os portões, na ordem")
    void aOrdemDosPortoes() {
        // É a conferência que vale por uma descida de barco: anda o eixo
        // ponto a ponto, como o jogador andaria, e exige que cada plano seja
        // cruzado — e cruzado na vez dele.
        List<double[]> eixo = PistaAberta.reamostrar(descidaComCurva(), 2.0);
        int[] ps = PistaAberta.portoes(10, eixo.size());
        List<List<PistaFechada.Portao>> planos =
                PistaAberta.planos(eixo, ps, 6.0, 6);
        assertEquals(-1, PistaAberta.conferirAOrdem(eixo, planos),
                "algum portão do percurso é incruzável descendo");
    }

    @Test
    @DisplayName("a altura de cada portão é a da estrada ali, não uma só")
    void aAlturaDeCadaPortao() {
        List<double[]> eixo = PistaAberta.reamostrar(descidaComCurva(), 2.0);
        int[] ps = PistaAberta.portoes(10, eixo.size());
        int[] alturas = PistaAberta.alturas(eixo, ps);
        assertEquals(10, alturas.length);
        for (int k = 1; k < alturas.length; k++) {
            assertTrue(alturas[k] < alturas[k - 1], "a descida subiu entre o "
                    + "portão " + (k - 1) + " e o " + k);
        }
        assertTrue(PistaAberta.maiorDegrau(alturas) < 40, "um degrau desses "
                + "entre dois portões é queda, não estrada");
    }

    @Test
    @DisplayName("a tangente das pontas olha para dentro, nunca dá a volta")
    void aTangenteDasPontas() {
        // Num anel a janela dá a volta; aqui ela encolhe. É nas pontas que a
        // direção precisa estar certa, porque é por ela que a grade e o
        // pórtico se orientam.
        List<double[]> eixo = PistaAberta.reamostrar(List.of(
                new int[]{0, 100, 0}, new int[]{100, 90, 0}), 2.0);
        double[] inicio = PistaAberta.tangente(eixo, 0, 6);
        double[] fim = PistaAberta.tangente(eixo, eixo.size() - 1, 6);
        assertEquals(1.0, inicio[0], 0.001, "a largada aponta para a descida");
        assertEquals(1.0, fim[0], 0.001, "a chegada também, e não de volta "
                + "para a largada");
    }

    // ------------------------------------------------------------------
    //  A grade e a decoração
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a pole larga na frente de todo mundo, e atrás da linha")
    void aPoleLargaNaFrente() {
        // O defeito que isto pega: contar a fila de frente para trás põe quem
        // entrou por último montanha abaixo na frente da pole — e a corrida
        // já começaria decidida ao contrário.
        int base = 7;
        double passo = 2.0;
        int linha = CorridaBuilder.indiceDaLinha(base, passo);
        int antes = Integer.MAX_VALUE;
        for (int k = 0; k < CorridaBuilder.FILAS_DA_GRADE * 2; k++) {
            int onde = CorridaBuilder.indiceDaFila(k, passo, base);
            assertTrue(onde >= base, "o lugar " + k + " nasce ANTES da base "
                    + "sondada: fora do pedaço de estrada que coube na grade");
            assertTrue(onde < linha, "o lugar " + k + " nasce em cima da linha "
                    + "pintada, ou adiante dela: nunca cruzaria a largada");
            if (k % 2 == 0 && k > 0) {
                assertTrue(onde < antes, "a fila " + (k / 2) + " largou na "
                        + "frente da anterior");
            }
            if (k % 2 == 0) {
                antes = onde;
            }
        }
        assertEquals(CorridaBuilder.indiceDaFila(0, passo, base),
                CorridaBuilder.indiceDaFila(1, passo, base),
                "os dois da mesma fila largam lado a lado");
        assertTrue(CorridaBuilder.ladoDaFila(0) < 0
                        && CorridaBuilder.ladoDaFila(1) > 0,
                "os dois da mesma fila largam no mesmo lado da pista");
    }

    @Test
    @DisplayName("o box é um contorno, e o barco pousa dentro dele")
    void oBoxEhUmContorno() {
        // Contorno e não mancha: o miolo tem de continuar sendo a estrada,
        // que é o que o barco precisa embaixo. E o ponto de largada sai da
        // MESMA conta do desenho — se saísse de outra, o piloto nasceria ao
        // lado do próprio box.
        double[] p = {100.0, 90.0, 200.0};
        double[] t = {1.0, 0.0};
        double off = CorridaBuilder.ladoDaFila(1);
        List<int[]> casas = CorridaBuilder.casasDoBox(p, t, off);
        assertFalse(casas.isEmpty());

        Set<Long> vistas = new HashSet<>();
        int[] meio = CorridaBuilder.casa(p, t, off, 0);
        for (int[] c : casas) {
            assertTrue(vistas.add(CorridaBuilder.chave(c[0], c[1])),
                    "casa repetida no contorno: " + c[0] + "," + c[1]);
        }
        assertFalse(vistas.contains(CorridaBuilder.chave(meio[0], meio[1])),
                "o contorno pintou o próprio meio: o barco pousaria em cima "
                        + "da tinta em vez da estrada");
    }

    @Test
    @DisplayName("a linha pintada é meio tabuleiro: a outra metade é o gelo")
    void aLinhaEhMeioTabuleiro() {
        // É isto que faz a largada não poder estragar o mapa: metade das
        // casas não é posta, é descoberta. E nenhuma casa sai repetida, senão
        // o contador de blocos mentiria no recado do /biga corrida.
        double[] p = {100.0, 90.0, 200.0};
        double[] t = {1.0, 0.0};
        List<int[]> casas = CorridaBuilder.casasDaLinha(p, t, 4, 3);
        assertFalse(casas.isEmpty());
        Set<Long> vistas = new HashSet<>();
        int claras = 0;
        for (int[] c : casas) {
            assertEquals(Math.floorMod(c[0] + c[1], 2), c[2],
                    "a cor da casa não é a do tabuleiro: " + c[0] + "," + c[1]);
            assertTrue(vistas.add(CorridaBuilder.chave(c[0], c[1])),
                    "casa repetida: " + c[0] + "," + c[1]);
            if (c[2] == 0) {
                claras++;
            }
        }
        assertTrue(claras >= 9, "linha de 9 de largura por 3 de fundura "
                + "tem mais casas claras do que " + claras);
        assertTrue(claras < casas.size(), "todas as casas são claras: isso "
                + "não é um tabuleiro, é uma faixa pintada");
    }

    @Test
    @DisplayName("o pórtico tem cinco luzes e deixa o barco passar")
    void oPorticoDeixaPassar() {
        double[] p = {100.0, 90.0, 200.0};
        double[] t = {1.0, 0.0};
        List<int[]> casas = CorridaBuilder.casasDoPortico(p, t, 4,
                CorridaBuilder.ALTURA_DO_PORTICO, false);

        int luzes = 0;
        Set<Integer> ordens = new HashSet<>();
        int maisBaixo = Integer.MAX_VALUE;
        for (int[] c : casas) {
            if (c[3] == CorridaBuilder.LUZ) {
                luzes++;
                ordens.add(c[4]);
            }
            if (c[3] != CorridaBuilder.PILAR) {
                maisBaixo = Math.min(maisBaixo, c[1]);
            }
        }
        assertEquals(CorridaBuilder.LUZES_DA_LARGADA, luzes);
        assertEquals(CorridaBuilder.LUZES_DA_LARGADA, ordens.size(),
                "duas luzes com a mesma ordem acendem juntas: a contagem "
                        + "pularia um segundo");
        assertTrue(maisBaixo >= 4, "o pórtico desce até a fiada " + maisBaixo
                + ": um barco com gente dentro tem dois blocos, e pórtico "
                + "que raspa na cabeça é pórtico que derruba");
    }

    @Test
    @DisplayName("a bandeirada da chegada é xadrez e não tem luz")
    void aBandeiradaDaChegada() {
        double[] p = {0.0, 90.0, 0.0};
        double[] t = {0.0, 1.0};
        List<int[]> casas = CorridaBuilder.casasDoPortico(p, t, 4,
                CorridaBuilder.ALTURA_DO_PORTICO, true);
        Set<Integer> cores = new HashSet<>();
        for (int[] c : casas) {
            assertFalse(c[3] == CorridaBuilder.LUZ, "a chegada não conta "
                    + "segundos: luz ali seria largada no lugar errado");
            if (c[3] == CorridaBuilder.BANDEIRADA) {
                cores.add(c[4]);
            }
        }
        assertEquals(Set.of(0, 1), cores, "xadrez de uma cor só não é xadrez");
    }

    @Test
    @DisplayName("os pilares ficam FORA da estrada")
    void osPilaresForaDaEstrada() {
        // Pilar dentro da pista é um muro no meio da descida.
        double[] p = {0.0, 90.0, 0.0};
        double[] t = {1.0, 0.0};
        int meia = 4;
        for (int[] c : CorridaBuilder.casasDoPortico(p, t, meia,
                CorridaBuilder.ALTURA_DO_PORTICO, false)) {
            if (c[3] == CorridaBuilder.PILAR) {
                assertTrue(Math.abs(c[2]) > meia, "pilar em z " + c[2]
                        + " está dentro da estrada de meia-largura " + meia);
            }
        }
    }

    @Test
    @DisplayName("só gelo conta como estrada")
    void soGeloEhEstrada() {
        // É por este teste que o pórtico acha a borda da pista. Se
        // WHITE_TERRACOTTA — que é a faixa pintada do Akina — contasse, a
        // medida iria até o acostamento e o pórtico nasceria no barranco.
        assertTrue(CorridaBuilder.ehEstrada(Material.PACKED_ICE));
        assertTrue(CorridaBuilder.ehEstrada(Material.BLUE_ICE));
        assertFalse(CorridaBuilder.ehEstrada(Material.WHITE_TERRACOTTA));
        assertFalse(CorridaBuilder.ehEstrada(Material.GRAY_CONCRETE));
        assertFalse(CorridaBuilder.ehEstrada(Material.AIR));
    }

    // ------------------------------------------------------------------
    //  O yml
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o corrida.yml aponta para o mapa e lista os trechos")
    void oYmlDoMapaPronto() throws Exception {
        YamlConfiguration yml = ler("/corrida.yml");
        assertTrue(yml.getBoolean("mapa-pronto.ativo"),
                "o mapa pronto está desligado: a Corrida voltaria ao "
                        + "autódromo gerado sem ninguém pedir");
        assertEquals("corrida_akina", yml.getString("mundo"));
        assertTrue(yml.getBoolean("mapa-pronto.rodizio"),
                "sem rodízio, as cinco rodadas seguintes são o mesmo trecho");

        List<Map<?, ?>> percursos = yml.getMapList("mapa-pronto.percursos");
        assertEquals(5, percursos.size(), "os cinco trechos da descida");
        Set<String> arquivos = new HashSet<>();
        Set<String> nomes = new HashSet<>();
        for (Map<?, ?> entrada : percursos) {
            Object arquivo = entrada.get("arquivo");
            Object nome = entrada.get("nome");
            assertNotNull(arquivo, "trecho sem arquivo: " + entrada);
            assertNotNull(nome, "trecho sem nome: " + entrada);
            assertTrue(arquivos.add(arquivo.toString()),
                    "arquivo repetido no rodízio: " + arquivo);
            assertTrue(nomes.add(nome.toString()),
                    "dois trechos com o mesmo nome: " + nome);
        }
    }

    @Test
    @DisplayName("a linha pintada e o chão do mapa são os dois de gelo")
    void aLinhaEhDeGelo() {
        // Linha de concreto numa pista de gelo é um freio a oitenta por hora
        // no meio da descida — e é o tipo de coisa que, posta na paleta por
        // engano, ninguém liga ao barco que parou sozinho na largada.
        YamlConfiguration yml = ler("/corrida.yml");
        for (String chave : List.of("paleta.mapa-piso", "paleta.mapa-linha")) {
            String nome = yml.getString(chave);
            assertNotNull(nome, chave + " sumiu do yml");
            Material qual = Material.matchMaterial(nome);
            assertNotNull(qual, chave + " = " + nome + ": material que não "
                    + "existe");
            assertTrue(CorridaBuilder.ehEstrada(qual), chave + " = " + nome
                    + " não é gelo");
        }
    }

    @Test
    @DisplayName("o saguão flutua acima do terreno da origem do mapa")
    void oSaguaoFlutua() {
        // Na origem do mapa do Akina o terreno sobe até y 122. O saguão é
        // construído em volta de (0,0): se ele nascesse dentro da montanha,
        // a obra se recusaria a levantar dizendo "há blocos no caminho" — e
        // isso não explicaria nada a quem lesse.
        YamlConfiguration yml = ler("/corrida.yml");
        int y = yml.getInt("arena.y");
        int saguao = y + yml.getInt("arena.saguao-altura", 16);
        assertTrue(saguao > 122 + 20, "o saguão em y " + saguao + " encosta no "
                + "terreno da origem do mapa, que chega a y 122");
    }

    // ------------------------------------------------------------------
    //  Apoio
    // ------------------------------------------------------------------

    private static YamlConfiguration ler(String recurso) {
        try (InputStream in = CorridaMapaTest.class
                .getResourceAsStream(recurso)) {
            assertNotNull(in, recurso + " não foi empacotado");
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException naoAbriu) {
            throw new IllegalStateException(naoAbriu);
        }
    }

    /**
     * Uma descida com curva, no feitio da estrada de montanha.
     *
     * <p>Curva de propósito: numa reta qualquer portão serve, e é na curva
     * que um plano mal orientado passa a ser cruzado de lado.
     */
    private static List<int[]> descidaComCurva() {
        List<int[]> pontos = new ArrayList<>();
        for (int i = 0; i <= 300; i++) {
            double t = i / 300.0;
            double ang = t * Math.PI * 0.75;
            double raio = 260 - t * 60;
            pontos.add(new int[]{
                    (int) Math.round(Math.cos(ang) * raio),
                    (int) Math.round(221 - t * 58),
                    (int) Math.round(Math.sin(ang) * raio)});
        }
        return pontos;
    }
}
