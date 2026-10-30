package codes.biga.bigacore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão do preço escalonado de terreno.
 *
 * <h2>Por que este teste existe</h2>
 *
 * A venda de terreno é o primeiro ralo recorrente de moeda do servidor, e o
 * preço cresce por multiplicação. Multiplicação repetida em {@code int} estoura
 * em silêncio: o valor vira negativo, e um preço negativo passaria pelo
 * {@code saldo < preco} e viraria DEPÓSITO no Vault — o ralo funcionando ao
 * contrário, imprimindo dinheiro em vez de drenar.
 *
 * Nada disso aparece em log. Só se descobre quando alguém já ficou rico
 * comprando terreno.
 */
class PlotSaleTest {

    private static final List<Integer> TABELA = List.of(1200, 3600, 10800);

    @Test
    void osTresPrimeirosVemDaTabela() {
        assertEquals(1200, PlotSale.precoDoProximo(TABELA, 0));
        assertEquals(3600, PlotSale.precoDoProximo(TABELA, 1));
        assertEquals(10800, PlotSale.precoDoProximo(TABELA, 2));
    }

    @Test
    void alemDaTabelaContinuaNoMesmoFator() {
        // O último salto é 10800/3600 = 3, então a escada segue triplicando.
        assertEquals(32400, PlotSale.precoDoProximo(TABELA, 3));
        assertEquals(97200, PlotSale.precoDoProximo(TABELA, 4));
    }

    @Test
    void tabelaVaziaSignificaTerrenoDeGraca() {
        assertEquals(0, PlotSale.precoDoProximo(List.of(), 0));
        assertEquals(0, PlotSale.precoDoProximo(List.of(), 7));
        assertEquals(0, PlotSale.precoDoProximo(null, 3));
    }

    /**
     * A invariante que mais importa: preço nunca negativo, nunca estourado.
     *
     * Sem o teto do {@link PlotSale#PRECO_MAXIMO} esta varredura passa a dar
     * negativo por volta do 19º terreno.
     */
    @Test
    void precoNuncaEstouraNemFicaNegativo() {
        for (int pagos = 0; pagos < 200; pagos++) {
            int preco = PlotSale.precoDoProximo(TABELA, pagos);
            assertTrue(preco >= 0, "preco negativo com pagos=" + pagos + ": " + preco);
            assertTrue(preco <= PlotSale.PRECO_MAXIMO,
                    "preco acima do teto com pagos=" + pagos + ": " + preco);
        }
    }

    @Test
    void precoNuncaDiminuiAoLongoDaEscada() {
        int anterior = -1;
        for (int pagos = 0; pagos < 50; pagos++) {
            int preco = PlotSale.precoDoProximo(TABELA, pagos);
            assertTrue(preco >= anterior,
                    "preco caiu em pagos=" + pagos + ": " + anterior + " -> " + preco);
            anterior = preco;
        }
    }

    @Test
    void contagemNegativaCaiNoPrimeiroDegrau() {
        // Arquivo corrompido ou editado à mão não pode virar terreno de graça.
        assertEquals(1200, PlotSale.precoDoProximo(TABELA, -5));
    }

    @Test
    void fatorSaiDoUltimoSaltoEscritoPeloDono() {
        assertEquals(3, PlotSale.fatorDeEscalada(TABELA));
        assertEquals(2, PlotSale.fatorDeEscalada(List.of(100, 200)));
        assertEquals(10, PlotSale.fatorDeEscalada(List.of(50, 500)));
    }

    @Test
    void tabelaDegenerada_naoTravaNemEncolhe() {
        // Um degrau só: não dá para inferir salto, usa o padrão.
        assertEquals(PlotSale.FATOR_PADRAO, PlotSale.fatorDeEscalada(List.of(1200)));
        assertEquals(3600, PlotSale.precoDoProximo(List.of(1200), 1));

        // Escada plana ou descendente também cai no padrão, em vez de dividir
        // por zero ou fazer o preço encolher para sempre.
        assertEquals(PlotSale.FATOR_PADRAO, PlotSale.fatorDeEscalada(List.of(500, 500)));
        assertEquals(PlotSale.FATOR_PADRAO, PlotSale.fatorDeEscalada(List.of(900, 300)));
    }
}
