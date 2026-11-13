package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O empurrão do golpe.
 *
 * Ele faltava inteiro: os vinte jogos calculam o dano sozinhos, e sem o dano
 * do Minecraft não vem a repulsa do Minecraft. O golpe piscava vermelho,
 * tirava vida e deixava os dois jogadores exatamente onde estavam — em Sky
 * Wars e Bed Wars, onde derrubar é metade do jogo, isso apagava a metade.
 */
@DisplayName("Empurrão do golpe")
class EmpurraoTest {

    private static final double FORCA = 0.42;
    private static final double ALTO = 0.36;
    private static final double TETO = 0.42;

    private static double[] golpe(double dx, double dz) {
        return ArenaComum.empurrao(dx, dz, 0, 0, 0, FORCA, ALTO, TETO);
    }

    @Test
    @DisplayName("o alvo vai para o lado OPOSTO a quem bateu")
    void vaiParaOLadoOposto() {
        // Alvo ao leste de quem bateu: tem de continuar para leste.
        double[] leste = golpe(3, 0);
        assertTrue(leste[0] > 0, "empurrou para trás de quem bateu");
        assertEquals(0.0, leste[2], 1.0E-9);

        double[] oeste = golpe(-3, 0);
        assertTrue(oeste[0] < 0);

        double[] sul = golpe(0, 5);
        assertTrue(sul[2] > 0);
        assertEquals(0.0, sul[0], 1.0E-9);
    }

    @Test
    @DisplayName("a força é a mesma de qualquer distância")
    void aDistanciaNaoMudaAForca() {
        double perto = Math.hypot(golpe(0.5, 0.5)[0], golpe(0.5, 0.5)[2]);
        double longe = Math.hypot(golpe(40, 40)[0], golpe(40, 40)[2]);
        assertEquals(perto, longe, 1.0E-9,
                "a distância entrou na conta: um golpe de longe empurraria "
                        + "mais que um de perto");
        assertEquals(FORCA, perto, 1.0E-9);
    }

    /**
     * Dois corpos no mesmo ponto não podem dividir por zero nem deixar de
     * empurrar: num amontoado de Bed Wars isso acontece toda partida.
     */
    @Test
    @DisplayName("em cima um do outro ainda empurra, e não dá NaN")
    void emCimaUmDoOutroAindaEmpurra() {
        double[] colado = golpe(0, 0);
        for (double v : colado) {
            assertTrue(Double.isFinite(v), "velocidade não finita: " + v);
        }
        assertTrue(Math.hypot(colado[0], colado[2]) > 0.1,
                "coladinho não empurrou nada");
    }

    /**
     * O teto da subida é o que impede o golpe de virar catapulta: sem ele,
     * dois golpes seguidos somam e jogam o alvo por cima do mapa.
     */
    @Test
    @DisplayName("golpes seguidos não viram catapulta")
    void golpesSeguidosNaoViramCatapulta() {
        double[] um = golpe(1, 0);
        double[] dois = ArenaComum.empurrao(1, 0, um[0], um[1], um[2],
                FORCA, ALTO, TETO);
        double[] tres = ArenaComum.empurrao(1, 0, dois[0], dois[1], dois[2],
                FORCA, ALTO, TETO);
        assertTrue(tres[1] <= TETO + 1.0E-9,
                "a subida chegou a " + tres[1] + ", acima do teto " + TETO);
        assertTrue(tres[0] < FORCA * 2.5,
                "o empurrão horizontal somou sem limite: " + tres[0]);
    }

    /**
     * A velocidade de agora vale METADE. É a conta do Minecraft, e sem ela
     * quem já está voando leva um empurrão somado em cima do que tinha.
     */
    @Test
    @DisplayName("a velocidade que o alvo já tinha vale metade")
    void aVelocidadeDeAgoraValeMetade() {
        double[] parado = ArenaComum.empurrao(1, 0, 0, 0, 0, FORCA, ALTO, TETO);
        double[] correndo = ArenaComum.empurrao(1, 0, 1.0, 0, 0,
                FORCA, ALTO, TETO);
        assertEquals(parado[0] + 0.5, correndo[0], 1.0E-9);
    }

    @Test
    @DisplayName("força zero não move, e é um ajuste legítimo do yml")
    void forcaZeroNaoMove() {
        double[] nada = ArenaComum.empurrao(1, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(0.0, nada[0], 1.0E-9);
        assertEquals(0.0, nada[1], 1.0E-9);
        assertEquals(0.0, nada[2], 1.0E-9);
    }

    @Test
    @DisplayName("o config.yml traz os três números do empurrão")
    void oConfigTrazOsNumeros() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration config;
        try (java.io.InputStream in = EmpurraoTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            config = org.bukkit.configuration.file.YamlConfiguration
                    .loadConfiguration(new java.io.InputStreamReader(in,
                            java.nio.charset.StandardCharsets.UTF_8));
        }
        double forca = config.getDouble("minigames.empurrao", -1);
        double alto = config.getDouble("minigames.empurrao-alto", -1);
        double teto = config.getDouble("minigames.empurrao-teto", -1);
        assertTrue(forca > 0, "sem 'minigames.empurrao' o golpe não empurra");
        assertTrue(alto > 0, "sem subida o alvo é pregado no chão");
        assertTrue(teto >= alto, "o teto (" + teto + ") é menor que a subida ("
                + alto + "): o empurrão nasce cortado");
        assertTrue(forca < 1.5, "empurrão de " + forca
                + " transforma o jogo em arremesso");
    }
}
