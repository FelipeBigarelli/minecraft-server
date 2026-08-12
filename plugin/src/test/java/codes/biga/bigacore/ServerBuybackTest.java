package codes.biga.bigacore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão da aritmética do teto de recompra.
 *
 * <h2>Por que este teste existe</h2>
 *
 * A recompra do servidor é o ÚNICO caminho que cria moeda no BigaCore. Um erro
 * de arredondamento aqui não derruba nada e não aparece em log: só se percebe
 * semanas depois, quando todo mundo está rico e o mercado entre jogadores
 * morreu. É a conta mais barata de testar e a mais cara de errar.
 *
 * A invariante que importa: <b>o pagamento nunca pode passar do que sobrou do
 * teto diário</b>, para nenhuma combinação de lote e preço.
 */
class ServerBuybackTest {

    @Test
    void pagamentoNuncaPassaDoTetoRestante() {
        int[] restantes = {1, 7, 50, 150, 900};
        int[] lotes = {1, 8, 16, 32, 64};
        int[] precos = {2, 8, 20, 55, 80, 96, 240};

        for (int restante : restantes) {
            for (int lote : lotes) {
                for (int preco : precos) {
                    int cabe = ServerBuyback.quantidadeQueCabeNoTeto(restante, lote, preco);
                    int pago = ServerBuyback.pagamentoPor(cabe, lote, preco);

                    assertTrue(pago <= restante,
                            "estourou o teto: restante=" + restante + " lote=" + lote
                                    + " preco=" + preco + " -> pagou " + pago);
                }
            }
        }
    }

    @Test
    void loteInteiroPagaOPrecoDoLote() {
        // 64 cobblestone por 8 B$ o lote de 64.
        assertEquals(8, ServerBuyback.pagamentoPor(64, 64, 8));
        // 16 ferros por 80 B$ o lote de 16.
        assertEquals(80, ServerBuyback.pagamentoPor(16, 16, 80));
    }

    @Test
    void fracaoDeLoteArredondaParaBaixo() {
        // Metade de um lote de 64 que vale 8 -> 4.
        assertEquals(4, ServerBuyback.pagamentoPor(32, 64, 8));
        // Um único cobblestone não chega a 1 B$: paga 0, e o comando recusa.
        assertEquals(0, ServerBuyback.pagamentoPor(1, 64, 8));
    }

    @Test
    void arredondamentoNuncaFavoreceOJogador() {
        // Varre fracões e confirma que o pagamento nunca ultrapassa o valor
        // proporcional exato — arredondar para cima criaria moeda do nada.
        for (int quantidade = 1; quantidade <= 64; quantidade++) {
            int pago = ServerBuyback.pagamentoPor(quantidade, 64, 20);
            double exato = 20.0 * quantidade / 64.0;

            assertTrue(pago <= exato + 1e-9,
                    "pagou " + pago + " por " + quantidade + ", exato seria " + exato);
        }
    }

    @Test
    void tetoZeradoNaoVendeNada() {
        assertEquals(0, ServerBuyback.quantidadeQueCabeNoTeto(0, 64, 8));
        assertEquals(0, ServerBuyback.pagamentoPor(0, 64, 8));
    }

    @Test
    void entradaInvalidaNaoCriaMoeda() {
        assertEquals(0, ServerBuyback.quantidadeQueCabeNoTeto(100, 0, 8));
        assertEquals(0, ServerBuyback.quantidadeQueCabeNoTeto(100, 64, 0));
        assertEquals(0, ServerBuyback.quantidadeQueCabeNoTeto(-5, 64, 8));
        assertEquals(0, ServerBuyback.pagamentoPor(-1, 64, 8));
    }

    @Test
    void tetoDiarioLimitaFarmDeCobblestone() {
        // Cenário concreto: cobblestone, lote 64, server-buy 8, teto 150/dia.
        // Mesmo com estoque infinito, o jogador não passa de 150 B$ no dia.
        int cabe = ServerBuyback.quantidadeQueCabeNoTeto(150, 64, 8);
        int pago = ServerBuyback.pagamentoPor(cabe, 64, 8);

        assertTrue(pago <= 150, "farm AFK estourou o teto diário");
        assertEquals(1200, cabe, "150 B$ a 8 por 64 dá 1200 blocos");
        assertEquals(150, pago);
    }
}
