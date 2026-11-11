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
            boolean coladaNaFrente = v == SpawnShopBuilder.FRONT_V + 1;

            assertTrue(coladaNaLateral || coladaNoFundo || coladaNaFrente,
                    "vaga solta no meio do salao: u=" + u + " v=" + v);
        }
    }

    @Test
    @DisplayName("o balcao comporta o catalogo com folga para as raridades")
    void cabeOCatalogoComFolga() {
        // 82 itens no economy.yml hoje. A folga sobrevive ao corte das
        // colunas que viraram pilar: a loja perdeu 10 vagas para ganhar
        // ritmo, e ainda sobra espaco para catalogo novo sem obra.
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

    @Test
    @DisplayName("nenhuma PLACA cai no bloco de outra placa nem dentro de um barril")
    void placasNaoDisputamBloco() {
        // Este teste nasceu de um defeito real, achado antes de ir para o
        // jogo: quando a placa saiu de cima do barril e foi para a frente
        // dele, a vaga do fundo em u=+-10 passou a por placa exatamente onde a
        // placa da coluna lateral iria. Duas placas no mesmo bloco significam
        // uma escrita por cima da outra e um item sumido da loja — calado.
        Set<String> placas = new HashSet<>();
        Set<String> barris = new HashSet<>();
        List<String> conflitos = new ArrayList<>();

        for (int[] p : SpawnShopBuilder.posicoesDoBalcao()) {
            barris.add(p[0] + "," + p[1] + "," + p[2]);
        }

        for (int[] p : SpawnShopBuilder.posicoesDoBalcao()) {
            int u = p[0] + SpawnShopBuilder.desvioUDaParede(p[3]);
            int v = p[1] + SpawnShopBuilder.desvioVDaParede(p[3]);
            String onde = u + "," + v + "," + p[2];

            if (!placas.add(onde)) {
                conflitos.add("duas placas em " + onde);
            }
            if (barris.contains(onde)) {
                conflitos.add("placa dentro do barril em " + onde);
            }
        }

        assertTrue(conflitos.isEmpty(), String.join("; ", conflitos));
    }

    @Test
    @DisplayName("a placa fica entre o barril e o corredor, nunca dentro da parede")
    void placaFicaParaDentroDoSalao() {
        for (int[] p : SpawnShopBuilder.posicoesDoBalcao()) {
            int u = p[0] + SpawnShopBuilder.desvioUDaParede(p[3]);
            int v = p[1] + SpawnShopBuilder.desvioVDaParede(p[3]);

            assertTrue(Math.abs(u) < SpawnShopBuilder.WALL_HALF,
                    "placa em u=" + u + " entra na parede lateral");
            assertTrue(v > SpawnShopBuilder.FRONT_V && v < SpawnShopBuilder.BACK_V,
                    "placa em v=" + v + " entra na parede da frente ou do fundo");
        }
    }

    @Test
    @DisplayName("meio passo local vira deslocamento no eixo certo do mundo")
    void passoLocalRespeitaOGiroDaLoja() {
        // O item flutuante e empurrado meio passo na direcao do corredor. Esse
        // passo e local (u, v); somado direto no x/z do mundo ele so acerta
        // nas fachadas em que u por acaso e o eixo x. Nas outras o item
        // aparece deslocado para o lado, fora do barril — foi o que apareceu
        // na foto da parede lateral.
        for (org.bukkit.block.BlockFace fachada : List.of(
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST)) {

            SpawnShopBuilder.Layout layout = new SpawnShopBuilder.Layout(0, 0, fachada);
            double[] emU = layout.passoNoMundo(1, 0);
            double[] emV = layout.passoNoMundo(0, 1);

            assertEquals(0.0, emU[0] * emV[0] + emU[1] * emV[1], 1.0e-9,
                    "em " + fachada + " os eixos u e v deixaram de ser perpendiculares");

            // +v e sempre para o fundo da loja, ou seja, o contrario da fachada.
            assertEquals(fachada.getOppositeFace().getModX(), emV[0], 1.0e-9,
                    "em " + fachada + " o passo em v foi para o eixo errado");
            assertEquals(fachada.getOppositeFace().getModZ(), emV[1], 1.0e-9,
                    "em " + fachada + " o passo em v foi para o eixo errado");

            assertEquals(1.0, Math.abs(emU[0]) + Math.abs(emU[1]), 1.0e-9,
                    "em " + fachada + " o passo em u perdeu tamanho");
        }
    }

    @Test
    @DisplayName("o letreiro olha para a praca, nao para o fundo da loja")
    void letreiroOlhaParaFora() {
        assertEquals(0f, SpawnShopBuilder.yawDe(org.bukkit.block.BlockFace.SOUTH));
        assertEquals(90f, SpawnShopBuilder.yawDe(org.bukkit.block.BlockFace.WEST));
        assertEquals(180f, SpawnShopBuilder.yawDe(org.bukkit.block.BlockFace.NORTH));
        assertEquals(270f, SpawnShopBuilder.yawDe(org.bukkit.block.BlockFace.EAST));
    }
}
