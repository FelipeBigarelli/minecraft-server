package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * O caderno que faz a decoração da corrida ser desfazível.
 *
 * <h2>O defeito que deu origem a isto</h2>
 *
 * <p>A largada e a chegada eram desenhadas "só onde há gelo" e desfeitas
 * repintando de {@code PACKED_ICE}. Duas coisas erradas na mesma regra, e as
 * duas apareceram na primeira descida de verdade:
 *
 * <ol>
 *   <li>parte da estrada do Monte Akina já é {@code BLUE_ICE} — a linha
 *       rápida do mapa. O xadrez pintado de azul sobre azul não aparecia, e o
 *       desfazer trocava o azul NATIVO por gelo comum, deixando uma cicatriz
 *       permanente na pista;</li>
 *   <li>e onde havia folha de carvalho encostada na estrada, o pórtico
 *       simplesmente não subia daquele lado — meio pórtico, que foi o "o
 *       final está feio e errado" do relato.</li>
 * </ol>
 *
 * <p>Com o caderno a decoração pode pôr o que quiser onde quiser, porque o
 * que havia antes está anotado. E por isso o formato do caderno é testado:
 * um caderno que não volta a ser lido é um pórtico eterno na montanha, e
 * ninguém ligaria uma coisa à outra meses depois.
 */
class EnfeiteDaPistaTest {

    @Test
    @DisplayName("uma linha do caderno volta com os três números")
    void aLinhaVolta() {
        assertArrayEquals(new long[]{10, 90, -200},
                EnfeiteDaPista.lerLinha("10 90 -200 PACKED_ICE"));
        assertArrayEquals(new long[]{10, 90, -200},
                EnfeiteDaPista.lerLinha("  10   90  -200   OAK_LEAVES  "),
                "espaço a mais numa linha não pode perder uma casa");
    }

    @Test
    @DisplayName("linha estragada é ignorada, não derruba o servidor")
    void aLinhaEstragadaEhIgnorada() {
        // Caderno ilegível é um enfeite que não sai. Caderno que explode no
        // onEnable é um servidor que não sobe.
        assertNull(EnfeiteDaPista.lerLinha(null));
        assertNull(EnfeiteDaPista.lerLinha(""));
        assertNull(EnfeiteDaPista.lerLinha("   "));
        assertNull(EnfeiteDaPista.lerLinha("# um comentário"));
        assertNull(EnfeiteDaPista.lerLinha("10 90 -200"));
        assertNull(EnfeiteDaPista.lerLinha("dez 90 -200 STONE"));
    }

    @Test
    @DisplayName("a casa negativa volta no lugar certo do mundo")
    void aCasaNegativaVolta() {
        // O Akina desce até x = -304: metade do caderno é coordenada
        // negativa. Uma chave que perdesse o sinal devolveria a casa a meio
        // milhão de blocos de onde ela está, e o desfazer não desfaria nada
        // — calado, porque lá não há nada para trocar.
        for (int[] casa : new int[][]{{-304, 87, 1008}, {575, 223, 160},
                {0, 0, 0}, {-1, -1, -1}, {524287, 0, -524288}}) {
            long chave = ArenaComum.chave(casa[0], casa[1], casa[2]);
            assertEquals(casa[0], ArenaComum.xDaChave(chave), "x de " + chave);
            assertEquals(casa[1], ArenaComum.yDaChave(chave), "y de " + chave);
            assertEquals(casa[2], ArenaComum.zDaChave(chave), "z de " + chave);
        }
    }

    @Test
    @DisplayName("os três nomes dizem o mesmo que os três deslocamentos")
    void osNomesBatemComOsDeslocamentos() {
        // O Bed Wars passava 0, 1 e 2 onde o parâmetro é DESLOCAMENTO de
        // bits. Compila, roda, e devolve três números sem sentido: a lã posta
        // na rodada era procurada do outro lado do mundo e nunca apagada.
        long chave = ArenaComum.chave(-7, 64, 300);
        assertEquals(ArenaComum.daChave(chave, 40), ArenaComum.xDaChave(chave));
        assertEquals(ArenaComum.daChave(chave, 20), ArenaComum.yDaChave(chave));
        assertEquals(ArenaComum.daChave(chave, 0), ArenaComum.zDaChave(chave));
        assertEquals(-7, ArenaComum.xDaChave(chave));
        assertEquals(64, ArenaComum.yDaChave(chave));
        assertEquals(300, ArenaComum.zDaChave(chave));
    }
}
