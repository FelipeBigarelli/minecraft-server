package codes.biga.bigacore;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A medição de um mapa pronto: o que o jogo pergunta e o mapa responde.
 *
 * <h2>O teste que vale mais aqui</h2>
 *
 * É o {@link #asIlhasSaemNaOrdemDoNorte}. Numa arena gerada, a ilha 0 é a do
 * norte porque a conta a põe lá. Num mapa feito por outra pessoa, as jaulas
 * vêm na ordem em que foram salvas — que é a ordem em que o autor as colocou,
 * ou seja, nenhuma. Se essa ordem vazasse para o jogo, a ordem de queda do
 * cerco, o número da ilha nas mensagens e o lugar em que cada um nasce
 * passariam a depender de um detalhe do arquivo. Ordenar pelo ângulo é o que
 * faz um mapa de fora se comportar como uma arena nossa.
 *
 * <h2>O irmão dele</h2>
 *
 * {@link #oRecorteNaoComeOMeio}: sem teto de distância, o bloco do meio seria
 * de alguma ilha — a mais perto, qualquer que fosse — e a primeira ilha
 * derrubada pelo cerco levaria junto um pedaço do meio.
 */
class MapaDaArenaTest {

    private static final int JAULAS = 8;
    private static final int ANEL = 30;
    private static final int CENTRO_X = 22;
    private static final int CENTRO_Y = 41;
    private static final int CENTRO_Z = 23;
    private static final int Y_DA_ARENA = 104;

    /** Onde a jaula k fica no arquivo, antes de o mapa ser centralizado. */
    private static int[] jaulaCrua(int k) {
        double angulo = -Math.PI / 2 + 2 * Math.PI * k / JAULAS;
        return new int[]{
                CENTRO_X + (int) Math.round(Math.cos(angulo) * ANEL),
                CENTRO_Y,
                CENTRO_Z + (int) Math.round(Math.sin(angulo) * ANEL)};
    }

    /**
     * Um mapa de mentira com a forma de um de verdade: oito jaulas num anel,
     * um disco de pedra embaixo de cada uma, dois baús em cada ilha e um
     * bolo de blocos no meio com quatro baús.
     *
     * <p>As jaulas entram FORA de ordem de propósito — é assim que elas vêm
     * de um arquivo real.
     */
    private static EstruturaDeMapa mapaDeMentira() {
        List<EstruturaDeMapa.Bloco> blocos = new ArrayList<>();
        List<EstruturaDeMapa.Marco> marcos = new ArrayList<>();

        int[] ordemTorta = {3, 7, 0, 5, 1, 6, 2, 4};
        for (int k : ordemTorta) {
            int[] jaula = jaulaCrua(k);
            marcos.add(new EstruturaDeMapa.Marco(jaula[0] + 0.5, jaula[1],
                    jaula[2] + 0.5, Set.of("cage", "explosion1")));
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx * dx + dz * dz > 16) {
                        continue;
                    }
                    blocos.add(new EstruturaDeMapa.Bloco(jaula[0] + dx,
                            jaula[1] - 2, jaula[2] + dz, "minecraft:stone"));
                }
            }
            marcos.add(new EstruturaDeMapa.Marco(jaula[0] + 2.5, jaula[1] - 1,
                    jaula[2] + 0.5, Set.of("normalChest")));
            marcos.add(new EstruturaDeMapa.Marco(jaula[0] - 2.5, jaula[1] - 1,
                    jaula[2] + 0.5, Set.of("normalChest")));
        }

        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx * dx + dz * dz > 64) {
                    continue;
                }
                blocos.add(new EstruturaDeMapa.Bloco(CENTRO_X + dx,
                        CENTRO_Y - 3, CENTRO_Z + dz, "minecraft:quartz_block"));
            }
        }
        for (int k = 0; k < 4; k++) {
            marcos.add(new EstruturaDeMapa.Marco(CENTRO_X + k - 1.5,
                    CENTRO_Y - 2, CENTRO_Z + 0.5, Set.of("centralChest")));
        }
        return new EstruturaDeMapa(blocos, marcos);
    }

    private static MapaDaArena montar() {
        return new MapaDaArena("mentira", mapaDeMentira(), Y_DA_ARENA,
                "cage", List.of("normalChest"),
                List.of("middleChest", "centralChest"));
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("as ilhas saem na ordem do norte, e não na ordem do arquivo")
    void asIlhasSaemNaOrdemDoNorte() {
        MapaDaArena mapa = montar();
        assertEquals(JAULAS, mapa.ilhas());
        assertTrue(mapa.serve());

        // A ilha 0 é a do norte: z bem negativo, x perto de zero.
        int[] norte = mapa.jaula(0);
        assertNotNull(norte);
        assertEquals(0, norte[0], 1, "a ilha 0 tinha de estar no eixo norte");
        assertTrue(norte[2] < -ANEL + 3, "a ilha 0 tinha de estar ao norte, "
                + "e veio em z=" + norte[2]);

        // E daí em diante o ângulo só cresce, dando no máximo UMA volta:
        // nenhuma ilha fura a fila.
        int voltas = 0;
        double anterior = -1;
        for (int k = 0; k < mapa.ilhas(); k++) {
            int[] onde = mapa.jaula(k);
            double angulo = ArenaComum.anguloDoNorte(onde[0], onde[2]);
            if (angulo < anterior) {
                voltas++;
            }
            anterior = angulo;
        }
        assertTrue(voltas <= 1, "a volta do anel quebrou em " + voltas
                + " pontos: as ilhas não estão em ordem");
    }

    @Test
    @DisplayName("a jaula quase no norte ainda abre a fila")
    void aJaulaQuaseNoNorte() {
        // É o caso do mapa baixado: a jaula do norte está um décimo de bloco
        // a OESTE do eixo, o que dá ângulo 6,28 em vez de 0,00. Uma
        // ordenação crua a jogaria para o fim e giraria a arena inteira.
        List<int[]> jaulas = new ArrayList<>(List.of(
                new int[]{30, 100, 0},
                new int[]{0, 100, 30},
                new int[]{-30, 100, 0},
                new int[]{-1, 100, -30}));
        MapaDaArena.emOrdemDoNorte(jaulas);
        assertEquals(-1, jaulas.get(0)[0], "a quase-norte tinha de abrir");
        assertEquals(-30, jaulas.get(0)[2]);
        assertEquals(30, jaulas.get(1)[0], "depois do norte vem o leste");
        assertEquals(30, jaulas.get(2)[2], "e depois o sul");
        assertEquals(-30, jaulas.get(3)[0], "e por último o oeste");
    }

    @Test
    @DisplayName("o mapa é centralizado e a jaula cai na fiada pedida")
    void oMapaEncaixaNaArena() {
        MapaDaArena mapa = montar();
        for (int k = 0; k < mapa.ilhas(); k++) {
            assertEquals(Y_DA_ARENA, mapa.jaula(k)[1], "a jaula " + k
                    + " não caiu na fiada da arena");
        }
        assertEquals(Y_DA_ARENA, mapa.yDaJaula());
        assertEquals(ANEL, mapa.anel(), 1);

        // O centro das jaulas foi para a origem: a soma dos x e dos z dá zero.
        int somaX = 0;
        int somaZ = 0;
        for (int k = 0; k < mapa.ilhas(); k++) {
            somaX += mapa.jaula(k)[0];
            somaZ += mapa.jaula(k)[2];
        }
        assertEquals(0, somaX, 2);
        assertEquals(0, somaZ, 2);
    }

    @Test
    @DisplayName("cada ilha fica com os baús dela, e o do meio não vira de ilha")
    void osBausVaoParaODono() {
        MapaDaArena mapa = montar();
        for (int k = 0; k < mapa.ilhas(); k++) {
            List<int[]> baus = mapa.bausDaIlha(k);
            assertEquals(2, baus.size(), "a ilha " + k + " ficou com "
                    + baus.size() + " baús");
            for (int[] bau : baus) {
                assertTrue(Math.hypot(bau[0] - mapa.jaula(k)[0],
                                bau[2] - mapa.jaula(k)[2]) <= 4,
                        "um baú da ilha " + k + " ficou longe dela");
                assertEquals(Y_DA_ARENA - 1, bau[1], "o baú perdeu a altura "
                        + "que tinha no mapa");
            }
        }
        assertEquals(4, mapa.bausDoMeio().size());
        for (int[] bau : mapa.bausDoMeio()) {
            assertTrue(Math.hypot(bau[0], bau[2]) < 5, "um baú do meio foi "
                    + "parar longe do meio");
        }
    }

    @Test
    @DisplayName("o recorte não come o meio")
    void oRecorteNaoComeOMeio() {
        MapaDaArena mapa = montar();
        int recorte = MapaDaArena.raioDeRecorte(jaulasCentradas());

        // A distância entre vizinhas num anel de 30 com oito ilhas é ~23;
        // metade disso é ~11, e o disco do meio tem raio 8 — fora do teto.
        assertTrue(recorte >= 8 && recorte <= 14, "o recorte deu " + recorte);
        assertEquals(-1, MapaDaArena.ilhaMaisPerto(0, 0, jaulasCentradas(),
                recorte), "o centro não é de ilha nenhuma");
        assertEquals(0, MapaDaArena.ilhaMaisPerto(0, -ANEL, jaulasCentradas(),
                recorte), "um ponto em cima da jaula do norte é da ilha 0");

        // O meio não entrou em ilha nenhuma: derrubar todas as ilhas deixa o
        // disco do meio de pé, que é o que o cerco promete.
        int blocosDeIlha = 0;
        for (int k = 0; k < mapa.ilhas(); k++) {
            blocosDeIlha += contarBlocosDaIlha(mapa, k);
        }
        assertTrue(blocosDeIlha < mapa.quantosBlocos(), "as ilhas levaram o "
                + "mapa inteiro: sobrou " + (mapa.quantosBlocos() - blocosDeIlha));
        assertTrue(mapa.raioDoMeio() >= 8 && mapa.raioDoMeio() <= 12,
                "o meio mediu " + mapa.raioDoMeio());
    }

    @Test
    @DisplayName("o raio da ilha e o alcance saem do que foi medido")
    void asMedidasSaemDoMapa() {
        MapaDaArena mapa = montar();
        assertEquals(4, mapa.raioDaIlha(), 1);
        assertTrue(mapa.alcance() >= ANEL && mapa.alcance() <= ANEL + 6,
                "o alcance deu " + mapa.alcance());
        int[] alturas = mapa.alturas();
        assertEquals(Y_DA_ARENA - 3, alturas[0]);
        assertEquals(Y_DA_ARENA - 2, alturas[1]);
    }

    @Test
    @DisplayName("mapa sem jaula nenhuma não serve, e não explode")
    void oMapaSemJaula() {
        MapaDaArena vazio = new MapaDaArena("nada",
                new EstruturaDeMapa(List.of(), List.of()), 100, "cage",
                List.of("normalChest"), List.of("centralChest"));
        assertTrue(!vazio.serve());
        assertEquals(0, vazio.ilhas());
        assertNull(vazio.jaula(0));
        assertEquals(List.of(), vazio.bausDaIlha(0));
        assertEquals(0, vazio.anel());
        assertEquals(0, MapaDaArena.raioDeRecorte(List.of()));
    }

    // ------------------------------------------------------------------
    //  O mapa baixado de verdade, quando ele estiver no lugar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o mapa baixado vira arena com oito ilhas medidas")
    void oMapaDeVerdadeViraArena() throws IOException {
        File pasta = new File(System.getProperty("user.home"),
                "minecraft/plugins/BigaCore/mapas/sky-wars");
        assumeTrue(new File(pasta, "map1-1.nbt").isFile(),
                "os mapas baixados não estão instalados nesta máquina");

        MapaDaArena mapa = MapaDaArena.abrir(pasta, "map1", 3, 47, 104,
                "cage", List.of("normalChest"),
                List.of("middleChest", "centralChest"));
        assertTrue(mapa.serve());
        assertEquals(8, mapa.ilhas());
        assertEquals(104, mapa.yDaJaula());

        // Toda ilha tem pelo menos um baú, senão quem nasce nela perdeu a
        // partida no sorteio da jaula.
        for (int k = 0; k < mapa.ilhas(); k++) {
            assertTrue(!mapa.bausDaIlha(k).isEmpty(), "a ilha " + k
                    + " do mapa baixado ficou sem baú");
            assertTrue(contarBlocosDaIlha(mapa, k) > 50, "a ilha " + k
                    + " ficou com quase nenhum bloco");
        }
        assertTrue(!mapa.bausDoMeio().isEmpty(), "o meio ficou sem baú");

        // O anel medido tem de ser grande o bastante para haver travessia, e
        // o mapa inteiro tem de caber em volta da origem.
        assertTrue(mapa.anel() > 20, "anel medido: " + mapa.anel());
        assertTrue(mapa.alcance() < 80, "alcance medido: " + mapa.alcance());
    }

    // ------------------------------------------------------------------

    private static List<int[]> jaulasCentradas() {
        List<int[]> saida = new ArrayList<>();
        for (int k = 0; k < JAULAS; k++) {
            double angulo = -Math.PI / 2 + 2 * Math.PI * k / JAULAS;
            saida.add(new int[]{(int) Math.round(Math.cos(angulo) * ANEL),
                    Y_DA_ARENA, (int) Math.round(Math.sin(angulo) * ANEL)});
        }
        return saida;
    }

    /** Quantos blocos o recorte deu para a ilha k. */
    private static int contarBlocosDaIlha(MapaDaArena mapa, int k) {
        return mapa.blocosDaIlhaParaTeste(k);
    }

    /**
     * Um mapa de duas jaulas em que UM baú cai em cima de um bloco.
     *
     * @param altoDoBau a altura do marcador de baú da ilha do norte: igual à
     *                  da laje, ele divide casa com um bloco do mapa
     */
    private static MapaDaArena doisBaus(int altoDoBau) {
        List<EstruturaDeMapa.Bloco> blocos = new ArrayList<>();
        List<EstruturaDeMapa.Marco> marcos = new ArrayList<>();
        int[][] jaulas = {{0, 40, -10}, {0, 40, 10}};
        for (int[] jaula : jaulas) {
            marcos.add(new EstruturaDeMapa.Marco(jaula[0] + 0.5, jaula[1],
                    jaula[2] + 0.5, Set.of("cage")));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    blocos.add(new EstruturaDeMapa.Bloco(jaula[0] + dx, 39,
                            jaula[2] + dz, "minecraft:stone"));
                }
            }
        }
        // o baú da ilha do norte na altura da laje: mesma casa de um bloco
        marcos.add(new EstruturaDeMapa.Marco(1.5, altoDoBau, -9.5,
                Set.of("normalChest")));
        // o da ilha do sul no ar, um palmo acima: casa livre
        marcos.add(new EstruturaDeMapa.Marco(1.5, 40, 10.5,
                Set.of("normalChest")));
        return new MapaDaArena("baus", new EstruturaDeMapa(blocos, marcos),
                Y_DA_ARENA, "cage", List.of("normalChest"), List.of());
    }

    private static int ilhaDoNorte(MapaDaArena mapa) {
        return mapa.jaula(0)[2] < mapa.jaula(1)[2] ? 0 : 1;
    }

    @Test
    @DisplayName("a casa do baú sai do mapa, e só ela")
    void aCasaDoBauSaiDoMapa() {
        // O RELATO: "itens estão espalhados no chão". Quatro dos 34
        // marcadores de baú do mapa baixado caem EM CIMA de um bloco. O baú
        // era posto por cima dele e, no refazer da rodada seguinte, o mapa
        // repunha o bloco na casa do baú. Trocar um baú cheio por outro
        // bloco faz o Minecraft DERRUBAR o conteúdo no chão — e era isso
        // que o jogador via espalhado pela arena.
        MapaDaArena comChoque = doisBaus(39);
        MapaDaArena semChoque = doisBaus(40);

        int norte = ilhaDoNorte(comChoque);
        assertEquals(norte, ilhaDoNorte(semChoque), "as duas montagens "
                + "tinham de numerar as ilhas igual");

        assertEquals(9, semChoque.blocosDaIlhaParaTeste(norte),
                "sem choque a laje de 3x3 fica inteira");
        assertEquals(8, comChoque.blocosDaIlhaParaTeste(norte),
                "a casa do baú tinha de sair da laje");
        assertEquals(semChoque.blocosDaIlhaParaTeste(1 - norte),
                comChoque.blocosDaIlhaParaTeste(1 - norte),
                "a outra ilha não tinha nada com isso");

        // E o baú continua onde o mapa mandou: tirar o bloco não é mover
        // o baú para o lado.
        int[] bau = comChoque.bausDaIlha(norte).get(0);
        assertEquals(1, bau[0]);
        assertEquals(-10, bau[2]);
    }

    @Test
    @DisplayName("a pergunta barata sabe quem guarda coisa dentro")
    void aPerguntaBarataSabeQuemGuarda() {
        // Desfazer a arena passa por centenas de milhares de blocos, e
        // getState() monta um objeto a cada chamada: o material é a peneira
        // que faz a conta cara só nos poucos blocos que podem ter saque.
        assertTrue(MapaDaArena.guardaCoisa(Material.CHEST));
        assertTrue(MapaDaArena.guardaCoisa(Material.TRAPPED_CHEST));
        assertTrue(MapaDaArena.guardaCoisa(Material.BARREL));
        assertTrue(MapaDaArena.guardaCoisa(Material.RED_SHULKER_BOX));
        assertTrue(MapaDaArena.guardaCoisa(Material.FURNACE));
        assertFalse(MapaDaArena.guardaCoisa(Material.STONE));
        assertFalse(MapaDaArena.guardaCoisa(Material.CHERRY_LEAVES));
        assertFalse(MapaDaArena.guardaCoisa(Material.AIR));
    }
}
