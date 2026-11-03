package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * O relatório da vitrine e o casamento de nome de placa.
 *
 * Nada aqui toca Bukkit: o que está sendo travado é a conta que o dono lê no
 * chat e a regra que decide se uma placa encontra o seu item. As duas falham
 * em silêncio se quebrarem — a loja simplesmente fica com buraco.
 */
class ShopDisplaysReportTest {

    @Test
    @DisplayName("placa sem item soma os dois motivos de falha")
    void semItemSomaOsDoisMotivos() {
        ShopDisplays.Relatorio r = new ShopDisplays.Relatorio(60, 55, 3, 2, 0, 48);
        assertEquals(5, r.semItem());
    }

    @Test
    @DisplayName("loja inteira pendurada não reporta falha")
    void lojaCompletaNaoReportaFalha() {
        ShopDisplays.Relatorio r = new ShopDisplays.Relatorio(60, 60, 0, 0, 0, 48);
        assertEquals(0, r.semItem());
        assertEquals(60, r.penduradas());
    }

    @Test
    @DisplayName("acento, caixa e espaço sobrando não impedem o casamento")
    void normalizaAcentoCaixaEEspaco() {
        assertEquals(ShopDisplays.normalizar("Carvão Vegetal"),
                ShopDisplays.normalizar("  carvao   vegetal "));
    }

    @Test
    @DisplayName("a chave do catálogo casa com ela mesma em caixa alta")
    void normalizaChaveDoCatalogo() {
        assertEquals("GRASS_BLOCK", ShopDisplays.normalizar("grass_block"));
    }

    @Test
    @DisplayName("nome nulo vira vazio em vez de estourar")
    void nomeNuloNaoEstoura() {
        assertEquals("", ShopDisplays.normalizar(null));
    }
}
