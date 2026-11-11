package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As duas decisoes do /terreno teleportar que da para errar em silencio:
 * QUAL terreno o jogador pediu e EM QUE ponto dele ele aterrissa.
 *
 * As duas levam o jogador para longe antes de ele perceber o engano, e no
 * survival isso custa o que ele estiver carregando.
 */
class TerrenoTeleporteTest {

    private static final List<String> MEUS = List.of("t_biga_1", "t_biga_2", "t_biga_3");

    @Test
    @DisplayName("o numero do /terreno listar e o que o jogador digita")
    void aceitaONumero() {
        assertEquals(Optional.of("t_biga_2"), TerrenoTeleporte.idEscolhido(MEUS, "2"));
        assertEquals(Optional.of("t_biga_1"), TerrenoTeleporte.idEscolhido(MEUS, "1"));
    }

    @Test
    @DisplayName("o id inteiro tambem serve, em qualquer caixa")
    void aceitaOIdInteiro() {
        assertEquals(Optional.of("t_biga_3"), TerrenoTeleporte.idEscolhido(MEUS, "t_biga_3"));
        assertEquals(Optional.of("t_biga_3"), TerrenoTeleporte.idEscolhido(MEUS, "T_BIGA_3"));
    }

    @Test
    @DisplayName("numero que ele nao tem nao vira o terreno mais parecido")
    void numeroInexistenteNaoCasa() {
        assertTrue(TerrenoTeleporte.idEscolhido(MEUS, "9").isEmpty());
        assertTrue(TerrenoTeleporte.idEscolhido(MEUS, "").isEmpty());
        assertTrue(TerrenoTeleporte.idEscolhido(MEUS, null).isEmpty());
        assertTrue(TerrenoTeleporte.idEscolhido(List.of(), "1").isEmpty());
    }

    @Test
    @DisplayName("pedaco do nome so vale quando casa com um terreno so")
    void pedacoAmbiguoRecusa() {
        // "biga_1" termina so o primeiro: pode ir.
        assertEquals(Optional.of("t_biga_1"), TerrenoTeleporte.idEscolhido(MEUS, "biga_1"));

        // Mandar para o primeiro da lista aqui seria escolher pelo jogador.
        List<String> parecidos = List.of("t_biga_1", "t_outro_biga_1");
        assertTrue(TerrenoTeleporte.idEscolhido(parecidos, "biga_1").isEmpty());
    }

    @Test
    @DisplayName("o primeiro ponto sondado e o centro — onde ele estava ao comprar")
    void comecaPeloCentro() {
        Caixa caixa = Caixa.centradaEm(100, 200, 16, 0, 255);
        List<Caixa.Ponto> pontos = TerrenoTeleporte.candidatos(caixa);

        assertFalse(pontos.isEmpty());
        Caixa dentro = caixa.inflada(-1);
        assertEquals(new Caixa.Ponto(dentro.centroX(), dentro.centroZ()), pontos.get(0));
    }

    @Test
    @DisplayName("nenhum ponto de pouso cai fora do terreno nem em cima da cerca")
    void todoCandidatoEhDentro() {
        for (int lado : new int[] {8, 16, 33, 64}) {
            Caixa caixa = Caixa.centradaEm(0, 0, lado, 0, 255);
            Caixa dentro = caixa.inflada(-1);
            for (Caixa.Ponto ponto : TerrenoTeleporte.candidatos(caixa)) {
                assertTrue(dentro.contemHorizontal(ponto.x(), ponto.z()),
                        "lado " + lado + ": " + ponto + " cai na borda ou fora");
            }
        }
    }

    @Test
    @DisplayName("terreno minusculo ainda devolve ao menos um ponto")
    void caixaDeUmBlocoNaoFicaSemPonto() {
        // inflada(-1) num 1x1 daria uma caixa invertida; o codigo tem de
        // desistir da folga em vez de devolver lista vazia e o comando morrer
        // dizendo que nao ha onde pousar.
        assertFalse(TerrenoTeleporte.candidatos(Caixa.centradaEm(0, 0, 1, 0, 255)).isEmpty());
        assertFalse(TerrenoTeleporte.candidatos(Caixa.centradaEm(0, 0, 2, 0, 255)).isEmpty());
    }

    @Test
    @DisplayName("o jogador nao aterrissa de pe na lava nem no fogo")
    void pisoPerigosoCobreOQueMata() {
        assertTrue(TerrenoTeleporte.pisoPerigoso(org.bukkit.Material.LAVA));
        assertTrue(TerrenoTeleporte.pisoPerigoso(org.bukkit.Material.MAGMA_BLOCK));
        assertTrue(TerrenoTeleporte.pisoPerigoso(org.bukkit.Material.CACTUS));
        assertFalse(TerrenoTeleporte.pisoPerigoso(org.bukkit.Material.WATER));
        assertFalse(TerrenoTeleporte.pisoPerigoso(org.bukkit.Material.GRASS_BLOCK));
    }
}
