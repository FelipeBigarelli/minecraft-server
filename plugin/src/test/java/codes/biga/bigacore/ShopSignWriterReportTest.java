package codes.biga.bigacore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regressão da conta do relatório de placas.
 *
 * <h2>Por que este teste existe</h2>
 *
 * O relatório é a única coisa que o dono vê antes de escrever 92 placas de uma
 * vez, e não existe snapshot de rollback para placa. Os dois números que ele
 * precisa enxergar são desencontros opostos:
 *
 * <ul>
 *   <li>{@code sobrando}: a construção tem mais vagas do que o catálogo tem
 *       itens — a loja fica com buraco visível.</li>
 *   <li>{@code semVaga}: o economy.yml pede mais itens do que cabe — o
 *       servidor deixa de vender coisas sem avisar ninguém.</li>
 * </ul>
 *
 * Trocar um pelo outro, ou deixar qualquer um ficar negativo, transformaria o
 * aviso em ruído e o dono aprenderia a ignorar os dois.
 */
class ShopSignWriterReportTest {

    @Test
    void construcaoMaiorQueOCatalogoDeixaPlacaVazia() {
        // O caso real de 30/10/2026: 92 placas na construção baixada, 62 itens
        // com server-sell no catálogo.
        var r = new ShopSignWriter.Relatorio(92, 62, 62, 32);
        assertEquals(30, r.sobrando());
        assertEquals(0, r.semVaga());
    }

    @Test
    void catalogoMaiorQueAConstrucaoDeixaItemDeFora() {
        var r = new ShopSignWriter.Relatorio(40, 40, 62, 32);
        assertEquals(0, r.sobrando());
        assertEquals(22, r.semVaga());
    }

    @Test
    void encaixePerfeitoNaoAvisaNada() {
        var r = new ShopSignWriter.Relatorio(62, 62, 62, 32);
        assertEquals(0, r.sobrando());
        assertEquals(0, r.semVaga());
    }

    @Test
    void nenhumDosDoisFicaNegativo() {
        // Números incoerentes não podem virar "-7 placas sobrando" na tela.
        var r = new ShopSignWriter.Relatorio(10, 99, 3, 32);
        assertEquals(0, r.sobrando());
        assertEquals(0, r.semVaga());
    }
}
