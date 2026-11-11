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
    @DisplayName("mexer um bloco cancela; girar a cabeca no lugar nao")
    void cancelaSoQuandoSaiDoBloco() {
        org.bukkit.Location parado = new org.bukkit.Location(null, 10.2, 64.0, -3.8);

        // Mesmo bloco: mirar para outro lado, pular no lugar, escorregar meio
        // bloco numa escada. Cancelar aqui faria o comando parecer quebrado.
        org.bukkit.Location mesmoBloco = new org.bukkit.Location(null, 10.9, 64.4, -3.1);
        mesmoBloco.setYaw(180f);
        assertFalse(TerrenoViagem.saiuDoLugar(parado, mesmoBloco));

        assertTrue(TerrenoViagem.saiuDoLugar(parado,
                new org.bukkit.Location(null, 11.2, 64.0, -3.8)));
        assertTrue(TerrenoViagem.saiuDoLugar(parado,
                new org.bukkit.Location(null, 10.2, 65.0, -3.8)));
        assertTrue(TerrenoViagem.saiuDoLugar(parado,
                new org.bukkit.Location(null, 10.2, 64.0, -4.8)));
        assertTrue(TerrenoViagem.saiuDoLugar(parado, null));
    }

    @Test
    @DisplayName("a contagem na tela comeca no total e termina em 1, nunca em 0")
    void contagemNaoMostraZero() {
        long total = 5 * 20L;

        // Primeira conferida, 5 ticks depois de comecar: faltam 95 ticks.
        assertEquals(5, TerrenoViagem.segundosRestantes(5, total));
        assertEquals(4, TerrenoViagem.segundosRestantes(20, total));
        assertEquals(1, TerrenoViagem.segundosRestantes(95, total));

        // No fim quem fala e a mensagem de chegada, nao a contagem.
        assertEquals(0, TerrenoViagem.segundosRestantes(100, total));
        assertEquals(0, TerrenoViagem.segundosRestantes(120, total));
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
