package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A geometria do terreno.
 *
 * Erro de um bloco aqui nao da excecao: entrega 19x19 cobrando 20x20, ou
 * cobra 21x21 e protege 20x20. O jogador descobriria contando bloco a bloco.
 */
class CaixaTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 8, 9, 16, 20, 31, 64})
    @DisplayName("a caixa tem exatamente o lado pedido, par ou impar")
    void ladoPedidoEOLadoEntregue(int tamanho) {
        Caixa caixa = Caixa.centradaEm(100, -200, tamanho, -64, 319);

        assertEquals(tamanho, caixa.larguraX(),
                "largura em X diferente do tamanho pedido");
        assertEquals(tamanho, caixa.larguraZ(),
                "largura em Z diferente do tamanho pedido");
        assertEquals(tamanho * tamanho, caixa.area(),
                "a area e o que o jogador paga: precisa ser lado x lado");
    }

    @Test
    @DisplayName("tamanho impar deixa o jogador no centro exato")
    void imparCentralizaDeVerdade() {
        Caixa caixa = Caixa.centradaEm(0, 0, 9, 0, 10);

        assertEquals(-4, caixa.minX());
        assertEquals(4, caixa.maxX());
        assertEquals(0, caixa.centroX());
        assertEquals(0, caixa.centroZ());
    }

    @Test
    @DisplayName("tamanho par sobra um bloco para o lado positivo")
    void parNaoTemCentro() {
        // 20 blocos nao tem bloco do meio. Isto documenta para que lado a
        // sobra vai, para ninguem "consertar" sem perceber que mudou o
        // comportamento de todo terreno de tamanho par.
        Caixa caixa = Caixa.centradaEm(0, 0, 20, 0, 10);

        assertEquals(-9, caixa.minX(), "9 blocos para oeste");
        assertEquals(10, caixa.maxX(), "10 blocos para leste");
        assertEquals(20, caixa.larguraX());
    }

    @Test
    @DisplayName("o centro de uma caixa criada por centradaEm volta no mesmo ponto")
    void centroFechaOCiclo() {
        // Importa para o /terreno expandir: ele recria a caixa a partir do
        // centro da antiga. Se o centro voltasse deslocado, cada expansao
        // arrastaria o terreno um bloco para o lado.
        for (int tamanho = 1; tamanho <= 64; tamanho++) {
            Caixa caixa = Caixa.centradaEm(37, -91, tamanho, 0, 10);
            Caixa refeita = Caixa.centradaEm(caixa.centroX(), caixa.centroZ(),
                    tamanho, 0, 10);

            assertEquals(caixa.minX(), refeita.minX(),
                    "expandir de " + tamanho + " arrastaria o terreno em X");
            assertEquals(caixa.minZ(), refeita.minZ(),
                    "expandir de " + tamanho + " arrastaria o terreno em Z");
        }
    }

    @Test
    @DisplayName("inflar abre so no plano horizontal")
    void inflarNaoMexeNaAltura() {
        Caixa caixa = new Caixa(0, -64, 0, 9, 319, 9);
        Caixa aberta = caixa.inflada(3);

        assertEquals(-3, aberta.minX());
        assertEquals(12, aberta.maxX());
        assertEquals(-3, aberta.minZ());
        assertEquals(12, aberta.maxZ());
        assertEquals(-64, aberta.minY(), "a altura ja e a coluna inteira");
        assertEquals(319, aberta.maxY());
    }

    @Test
    @DisplayName("inflar zero devolve a mesma caixa")
    void inflarZeroNaoFazNada() {
        Caixa caixa = new Caixa(1, 2, 3, 4, 5, 6);

        assertEquals(caixa, caixa.inflada(0));
    }

    @Test
    @DisplayName("a caixa aceita os cantos em qualquer ordem")
    void cantosTrocadosNaoQuebram() {
        // O WorldGuard normaliza os pontos, e a Caixa tambem precisa: um
        // chamador que montar a caixa do canto maior para o menor nao deve
        // receber area negativa.
        Caixa normal = new Caixa(0, 0, 0, 9, 10, 9);
        Caixa invertida = new Caixa(9, 10, 9, 0, 0, 0);

        assertEquals(normal.area(), invertida.area());
        assertEquals(normal.minX(), invertida.minX());
        assertEquals(normal.maxZ(), invertida.maxZ());
    }

    @Test
    @DisplayName("distancia ate um ponto dentro da caixa e zero")
    void pontoDentroDaZero() {
        Caixa caixa = Caixa.centradaEm(0, 0, 21, 0, 10);

        assertEquals(0, caixa.distanciaAte(0, 0));
        assertEquals(0, caixa.distanciaAte(10, 10), "a borda conta como dentro");
        assertTrue(caixa.contemHorizontal(-10, -10));
        assertFalse(caixa.contemHorizontal(11, 0));
    }

    @Test
    @DisplayName("a distancia e medida em quadrado, nao em circulo")
    void distanciaEChebyshev() {
        // Esta e a regra do distancia-minima-do-spawn. Com distancia
        // euclidiana, o canto do quadrado em volta do spawn ficaria mais
        // "longe" do que a lateral, e o canto do spawn seria compravel.
        Caixa caixa = new Caixa(100, 0, 100, 109, 10, 109);

        assertEquals(10, caixa.distanciaAte(90, 100), "10 blocos a oeste");
        assertEquals(10, caixa.distanciaAte(90, 90),
                "no diagonal, o quadrado mede os mesmos 10 — nao 14");
        assertEquals(1, caixa.distanciaAte(110, 105));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 8, 16, 20, 64})
    @DisplayName("o perimetro tem 4n-4 pontos, sem repetir canto")
    void perimetroNaoRepeteCanto(int lado) {
        // Cada ponto do perimetro vira um poste de cerca. Canto contado duas
        // vezes nao da erro: poe a cerca duas vezes no mesmo bloco e relata
        // mais postes do que existem.
        var volta = Caixa.centradaEm(0, 0, lado, 0, 10).perimetro();

        assertEquals(4 * lado - 4, volta.size(),
                "a volta de um quadrado de lado " + lado);
        assertEquals(volta.size(), new java.util.HashSet<>(volta).size(),
                "o perimetro repetiu algum ponto");
    }

    @Test
    @DisplayName("caixa de um bloco tem um ponto de perimetro, nao quatro")
    void perimetroDeUmBlocoSo() {
        // Os quatro cantos sao o mesmo bloco. A formula 4n-4 daria zero aqui,
        // e o caso precisa de tratamento proprio.
        Caixa unica = Caixa.centradaEm(5, 5, 1, 0, 10);

        assertEquals(1, unica.perimetro().size());
        assertEquals(1, unica.cantos().size());
        assertEquals(new Caixa.Ponto(5, 5), unica.perimetro().get(0));
    }

    @Test
    @DisplayName("todo ponto do perimetro encosta na borda")
    void perimetroSoTemBorda() {
        Caixa caixa = Caixa.centradaEm(0, 0, 10, 0, 10);

        for (Caixa.Ponto ponto : caixa.perimetro()) {
            boolean naBorda = ponto.x() == caixa.minX() || ponto.x() == caixa.maxX()
                    || ponto.z() == caixa.minZ() || ponto.z() == caixa.maxZ();
            assertTrue(naBorda, "o ponto " + ponto + " está no meio do terreno");
            assertTrue(caixa.contemHorizontal(ponto.x(), ponto.z()),
                    "o ponto " + ponto + " caiu fora do terreno");
        }
    }

    @Test
    @DisplayName("os quatro cantos são os quatro extremos")
    void cantosSaoOsExtremos() {
        Caixa caixa = Caixa.centradaEm(0, 0, 16, 0, 10);
        var cantos = caixa.cantos();

        assertEquals(4, cantos.size());
        assertTrue(cantos.contains(new Caixa.Ponto(caixa.minX(), caixa.minZ())));
        assertTrue(cantos.contains(new Caixa.Ponto(caixa.maxX(), caixa.maxZ())));
        for (Caixa.Ponto canto : cantos) {
            assertTrue(caixa.perimetro().contains(canto),
                    "o canto " + canto + " não está no perímetro");
        }
    }

    @Test
    @DisplayName("comAltura troca so a altura")
    void comAlturaPreservaOChao() {
        Caixa caixa = Caixa.centradaEm(5, 5, 16, 60, 70);
        Caixa coluna = caixa.comAltura(-64, 319);

        assertEquals(caixa.area(), coluna.area());
        assertEquals(caixa.minX(), coluna.minX());
        assertEquals(-64, coluna.minY());
        assertEquals(319, coluna.maxY());
    }
}
