package codes.biga.bigacore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão do tamanho da Biga Market contra o terreno que a abriga.
 *
 * <h2>Por que este teste existe</h2>
 *
 * A loja não mora mais em campo aberto: ela fica dentro de um terreno do
 * CreativePlots. A grade é de 32 blocos com uma fileira de
 * {@code polished_blackstone} de borda em cada lado, o que deixa
 * {@value #INTERIOR} colunas de grama construível — conferido lendo o mundo em
 * 30/10/2026, no terreno #1 (grama em X 7..36 e Z 7..36, borda em 6 e 37).
 *
 * Com {@code HALF_WIDTH = 15} a footprint saía 31 de largura e passava um
 * bloco por cima da borda. Isso falhava de duas formas ao mesmo tempo: a
 * validação reprovava (borda é bloco construído, e
 * {@code max-blocos-construidos} é 0 por padrão) e, se alguém levantasse o
 * limite para forçar, a construção apagaria 30 blocos da borda do terreno.
 *
 * Nada disso aparece em teste de unidade comum: a footprint é aritmética de
 * constantes, e a única forma de descobrir era construir e olhar. Este teste
 * amarra os dois números ao tamanho do terreno — mexer num deles sem mexer no
 * outro passa a quebrar aqui, não no mundo.
 */
class PlotFootprintTest {

    /** Colunas de grama de um terreno: 32 da grade menos as duas bordas. */
    private static final int INTERIOR = 30;

    @Test
    void aLarguraCabeNoTerreno() {
        int largura = SpawnShopBuilder.HALF_WIDTH * 2 + 1;
        assertTrue(largura <= INTERIOR,
                "a loja tem " + largura + " de largura e o terreno só oferece " + INTERIOR);
    }

    @Test
    void aProfundidadeCabeNoTerreno() {
        int profundidade = SpawnShopBuilder.MAX_V - SpawnShopBuilder.MIN_V + 1;
        assertTrue(profundidade <= INTERIOR,
                "a loja tem " + profundidade + " de profundidade e o terreno só oferece "
                        + INTERIOR);
    }

    /**
     * O centro reportado por {@code /biga loja local} precisa ser um ponto do
     * qual a construção inteira ainda caiba — senão o offset calculado a
     * partir dele joga a loja para fora do terreno sem ninguém perceber.
     */
    @Test
    void aConstrucaoCabeAoRedorDoCentroReportado() {
        int paraTras = SpawnShopBuilder.CENTER_V - SpawnShopBuilder.MIN_V;
        int paraFrente = SpawnShopBuilder.MAX_V - SpawnShopBuilder.CENTER_V;

        assertTrue(paraTras + paraFrente + 1 <= INTERIOR,
                "ao redor do centro a loja ocupa " + (paraTras + paraFrente + 1)
                        + " colunas de profundidade");
        assertTrue(SpawnShopBuilder.HALF_WIDTH * 2 + 1 <= INTERIOR,
                "ao redor do centro a loja ocupa " + (SpawnShopBuilder.HALF_WIDTH * 2 + 1)
                        + " colunas de largura");
    }

    /**
     * As vagas do balcão são o motivo de a profundidade ter crescido quando a
     * largura encolheu. Se alguém desfizer só metade da troca, o economy.yml
     * passa a pedir mais itens do que a parede comporta — o
     * {@code EconomyCatalogTest} pega isso, mas só com o arquivo real em mãos.
     */
    @Test
    void oBalcaoNaoEncolheuComARefinada() {
        assertTrue(SpawnShopBuilder.adminShopSlotCount() >= 61,
                "o balcão caiu para " + SpawnShopBuilder.adminShopSlotCount() + " vagas");
    }
}
