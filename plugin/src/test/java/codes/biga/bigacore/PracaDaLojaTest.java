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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A praça da Biga Market e as placas de dica da alameda.
 *
 * As duas falhas que este arquivo existe para impedir são caladas em jogo:
 * texto de placa cortado no meio do comando, e bloco construído fora do
 * volume que o /biga loja desfazer sabe devolver.
 */
class PracaDaLojaTest {

    private static YamlConfiguration ajuda;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = PracaDaLojaTest.class.getResourceAsStream("/ajuda.yml")) {
            assertNotNull(in, "ajuda.yml não foi empacotado");
            ajuda = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("nenhuma linha de totem e cortada pela largura da placa")
    void totemCabeNaPlaca() {
        List<Map<?, ?>> totens = ajuda.getMapList("totens");
        assertFalse(totens.isEmpty(), "ajuda.yml não tem nenhum totem");

        List<String> estouradas = new ArrayList<>();
        for (Map<?, ?> totem : totens) {
            for (Object linha : (List<?>) totem.get("linhas")) {
                String texto = String.valueOf(linha);
                if (!PainelAjuda.cabeNaPlaca(texto)) {
                    estouradas.add(texto + " (" + PainelAjuda.larguraDePlaca(texto) + "px)");
                }
            }
        }

        assertTrue(estouradas.isEmpty(),
                "linha não cabe na placa e sai cortada em jogo: " + estouradas);
    }

    @Test
    @DisplayName("todo totem tem quatro linhas e uma cor de tinta valida")
    void totemEstaCompleto() {
        for (Map<?, ?> totem : ajuda.getMapList("totens")) {
            List<?> linhas = (List<?>) totem.get("linhas");
            assertNotNull(linhas, "totem sem linhas");
            assertTrue(linhas.size() <= 4,
                    "placa tem 4 linhas; este totem pediu " + linhas.size());

            String cor = String.valueOf(totem.get("cor"));
            assertTrue(org.bukkit.DyeColor.valueOf(cor) != null, cor);
        }
    }

    @Test
    @DisplayName("a praca nao pavimenta por cima da construcao")
    void pracaNaoInvadeAFootprint() {
        for (int u = -SpawnShopBuilder.ENVELOPE_HALF; u <= SpawnShopBuilder.ENVELOPE_HALF; u++) {
            for (int v = SpawnShopBuilder.ENVELOPE_MIN_V;
                    v <= SpawnShopBuilder.ENVELOPE_MAX_V; v++) {

                if (!SpawnShopBuilder.dentroDaFootprint(u, v)) {
                    continue;
                }
                // Dentro da footprint quem manda é buildFloor(). Se as duas
                // regras valessem na mesma coluna, a última a rodar venceria
                // — e qual é a última é detalhe de ordem de chamada.
                assertTrue(SpawnShopBuilder.pisoDaPraca(u, v) == null
                                || SpawnShopBuilder.pisoDe(u, v) != null,
                        "praça e piso disputam u=" + u + " v=" + v);
            }
        }
    }

    @Test
    @DisplayName("a alameda sai da porta e chega na beirada do lote")
    void alamedaLigaPortaAteARua() {
        // Sem isto, um ajuste em PRACA_FRENTE deixaria a alameda terminando
        // no meio do gramado, e os totens do fim ficariam na grama solta.
        for (int v = SpawnShopBuilder.ENVELOPE_MIN_V; v < SpawnShopBuilder.MIN_V; v++) {
            Material piso = SpawnShopBuilder.pisoDaPraca(0, v);
            assertNotNull(piso, "buraco na alameda em v=" + v);
        }
    }

    @Test
    @DisplayName("o gramado sobrevive: praca nao e lote inteiro de pedra")
    void sobraGramado() {
        int pavimentado = 0;
        int total = 0;

        for (int u = -SpawnShopBuilder.ENVELOPE_HALF; u <= SpawnShopBuilder.ENVELOPE_HALF; u++) {
            for (int v = SpawnShopBuilder.ENVELOPE_MIN_V;
                    v <= SpawnShopBuilder.ENVELOPE_MAX_V; v++) {
                if (SpawnShopBuilder.dentroDaFootprint(u, v)) {
                    continue;
                }
                total++;
                if (SpawnShopBuilder.pisoDaPraca(u, v) != null) {
                    pavimentado++;
                }
            }
        }

        assertTrue(pavimentado * 2 < total,
                "a praça pavimentou " + pavimentado + " de " + total
                        + " colunas: vira pátio de concreto, não jardim");
    }
}
