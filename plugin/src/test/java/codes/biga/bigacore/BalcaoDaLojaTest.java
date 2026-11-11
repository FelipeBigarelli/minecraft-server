package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O balcão da Biga Market, conferido sem servidor.
 *
 * Tudo aqui existe por causa da mesma classe de falha: a loja é construída uma
 * vez, por comando, e um erro de posição não dá erro nenhum — ele some um item
 * do catálogo, e isso só aparece quando um jogador procura e não acha.
 */
class BalcaoDaLojaTest {

    @Test
    @DisplayName("nenhuma vaga repete posicao — placa em cima de placa apaga item")
    void nenhumaVagaRepetida() {
        Set<String> vistas = new HashSet<>();
        List<String> repetidas = new ArrayList<>();

        for (int[] p : SpawnShopBuilder.posicoesDoBalcao()) {
            String chave = p[0] + "," + p[1] + "," + p[2];
            if (!vistas.add(chave)) {
                repetidas.add(chave);
            }
        }

        assertTrue(repetidas.isEmpty(),
                "posicao repetida no balcao (u,v,altura): " + repetidas);
    }

    @Test
    @DisplayName("as duas prateleiras nao se atropelam: barril, placa, barril, placa")
    void prateleirasNaoColidem() {
        // Cada vaga come DOIS blocos de parede: o barril na altura h e a placa
        // em h+1. Duas prateleiras que comecem a menos de dois blocos uma da
        // outra fazem o barril de cima nascer em cima da placa de baixo, e a
        // placa apagada leva o item junto.
        int[] alturas = SpawnShopBuilder.PRATELEIRAS;
        for (int i = 1; i < alturas.length; i++) {
            assertTrue(alturas[i] - alturas[i - 1] >= 2,
                    "prateleira em " + alturas[i] + " invade a de " + alturas[i - 1]);
        }

        // E a placa mais alta tem de caber na parede, senao ela encosta no
        // beiral e fica ilegivel de dentro do salao.
        int placaMaisAlta = alturas[alturas.length - 1] + 1;
        assertTrue(placaMaisAlta <= SpawnShopBuilder.WALL_HEIGHT - 1,
                "a placa de cima fica em " + placaMaisAlta
                        + " e a parede so vai ate " + (SpawnShopBuilder.WALL_HEIGHT - 1));
    }

    @Test
    @DisplayName("toda vaga encosta numa parede, senao a placa nao tem onde grudar")
    void todaVagaEncostaNaParede() {
        for (int[] p : SpawnShopBuilder.posicoesDoBalcao()) {
            int u = p[0];
            int v = p[1];

            boolean coladaNaLateral = Math.abs(u) == SpawnShopBuilder.WALL_HALF - 1;
            boolean coladaNoFundo = v == SpawnShopBuilder.BACK_V - 1;

            assertTrue(coladaNaLateral || coladaNoFundo,
                    "vaga solta no meio do salao: u=" + u + " v=" + v);
        }
    }

    @Test
    @DisplayName("o balcao comporta o catalogo com folga para as raridades")
    void cabeOCatalogoComFolga() {
        // 62 itens no economy.yml hoje. A folga existe para a prateleira de
        // raridades (ovo de aldeao e companhia) entrar sem obra nova.
        assertTrue(SpawnShopBuilder.adminShopSlotCount() >= 100,
                "o balcao tem so " + SpawnShopBuilder.adminShopSlotCount() + " vagas");
    }

    @Test
    @DisplayName("a prateleira de baixo e preenchida antes da de cima")
    void comecaPelaAlturaDosOlhos() {
        List<int[]> posicoes = SpawnShopBuilder.posicoesDoBalcao();
        int metade = posicoes.size() / 2;

        assertEquals(SpawnShopBuilder.PRATELEIRAS[0], posicoes.get(0)[2]);
        assertEquals(SpawnShopBuilder.PRATELEIRAS[0], posicoes.get(metade - 1)[2]);
        assertEquals(SpawnShopBuilder.PRATELEIRAS[1], posicoes.get(metade)[2]);
    }
}
