package codes.biga.bigacore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão da geometria do telhado da Biga Market.
 *
 * <h2>Por que este teste existe</h2>
 *
 * A primeira construção real revelou, na leitura dos blocos do mundo, que o
 * telhado era uma escada de blocos soltos: cada coluna recebia UMA telha e a
 * face vertical do degrau ficava aberta. Dava para ver o sótão de lado.
 *
 * A geometria é aritmética pura, sem Bukkit no caminho, então dá para provar
 * aqui que o telhado fecha — em vez de descobrir de novo olhando o mundo
 * depois de construir.
 */
class RoofGeometryTest {

    private static final int FLOOR = 68;
    private static final int SPAN = SpawnShopBuilder.ROOF_HALF_SPAN;

    @Test
    void telhadoSobeDaBeiraAteACumeeira() {
        int beira = FLOOR + SpawnShopBuilder.WALL_HEIGHT;
        assertEquals(beira, SpawnShopBuilder.roofHeight(-SPAN, FLOOR), "beira esquerda");
        assertEquals(beira, SpawnShopBuilder.roofHeight(SPAN, FLOOR), "beira direita");
        assertTrue(SpawnShopBuilder.roofHeight(0, FLOOR) > beira, "cumeeira precisa subir");
    }

    @Test
    void telhadoESimetrico() {
        for (int u = 0; u <= SPAN; u++) {
            assertEquals(SpawnShopBuilder.roofHeight(u, FLOOR),
                    SpawnShopBuilder.roofHeight(-u, FLOOR),
                    "telhado torto em u=" + u);
        }
    }

    @Test
    void degrauNuncaPulaMaisQueUmBloco() {
        for (int u = -SPAN; u < SPAN; u++) {
            int diferenca = Math.abs(SpawnShopBuilder.roofHeight(u + 1, FLOOR)
                    - SpawnShopBuilder.roofHeight(u, FLOOR));
            assertTrue(diferenca <= 1,
                    "degrau de " + diferenca + " blocos entre u=" + u + " e u=" + (u + 1));
        }
    }

    @Test
    void cadaColunaFechaAFaceVerticalDoDegrau() {
        // É esta a regra que o telhado quebrado violava: a coluna precisa subir
        // até a altura da vizinha de dentro, senão o degrau fica vazado.
        for (int u = -SPAN; u <= SPAN; u++) {
            int base = SpawnShopBuilder.roofHeight(u, FLOOR);
            int topo = SpawnShopBuilder.roofHeight(SpawnShopBuilder.towardRidge(u), FLOOR);

            assertTrue(topo >= base, "vizinha de dentro mais baixa em u=" + u);
            assertTrue(topo - base <= 1, "riser alto demais em u=" + u);
        }
    }

    @Test
    void faixasDeColunasVizinhasSeTocam() {
        // O invariante real do telhado: a faixa de Y preenchida por uma coluna
        // e a da coluna seguinte precisam se INTERSECTAR. Se não se tocarem,
        // sobra um Y descoberto e a chuva entra pela lateral do degrau.
        for (int u = -SPAN; u < SPAN; u++) {
            int baixoA = SpawnShopBuilder.roofHeight(u, FLOOR);
            int altoA = SpawnShopBuilder.roofHeight(SpawnShopBuilder.towardRidge(u), FLOOR);
            int baixoB = SpawnShopBuilder.roofHeight(u + 1, FLOOR);
            int altoB = SpawnShopBuilder.roofHeight(SpawnShopBuilder.towardRidge(u + 1), FLOOR);

            int inicio = Math.max(baixoA, baixoB);
            int fim = Math.min(altoA, altoB);

            assertTrue(inicio <= fim,
                    "faixas [" + baixoA + "," + altoA + "] e [" + baixoB + "," + altoB
                            + "] não se tocam entre u=" + u + " e u=" + (u + 1));
        }
    }

    @Test
    void towardRidgeConvergeParaOCentro() {
        assertEquals(0, SpawnShopBuilder.towardRidge(0));
        assertEquals(0, SpawnShopBuilder.towardRidge(1));
        assertEquals(0, SpawnShopBuilder.towardRidge(-1));
        assertEquals(SPAN - 1, SpawnShopBuilder.towardRidge(SPAN));
        assertEquals(-(SPAN - 1), SpawnShopBuilder.towardRidge(-SPAN));
    }

    @Test
    void telhadoCabeNoEnvelopeDoSnapshot() {
        // A cumeeira fica um bloco acima da telha mais alta, e a chaminé sobe
        // até floorY+11. Tudo precisa caber em BUILD_HEIGHT, senão o snapshot
        // de rollback não cobriria o que a construção alterou.
        int cumeeira = SpawnShopBuilder.roofHeight(0, FLOOR) + 1;
        int chamine = FLOOR + 11;
        int envelope = FLOOR + SpawnShopBuilder.BUILD_HEIGHT;

        assertTrue(cumeeira <= envelope, "cumeeira acima do envelope");
        assertTrue(chamine <= envelope, "chaminé acima do envelope");
    }
}
