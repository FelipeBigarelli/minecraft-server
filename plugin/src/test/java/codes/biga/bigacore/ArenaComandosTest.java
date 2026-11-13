package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O que não se digita dentro de uma arena.
 *
 * O teste lê o config.yml DE VERDADE, o mesmo que o servidor instala, e não
 * uma cópia escrita aqui: uma lista repetida no teste concorda consigo mesma
 * enquanto a de produção diverge.
 */
@DisplayName("Comandos bloqueados nos minigames")
class ArenaComandosTest {

    private static YamlConfiguration config;

    @BeforeAll
    static void lerOArquivo() throws Exception {
        try (InputStream in = ArenaComandosTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------
    //  A raiz do comando
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a barra, o argumento e o prefixo do plugin saem da raiz")
    void aRaizEhSoONome() {
        assertEquals("speed", ComandosDaArena.raiz("/speed"));
        assertEquals("speed", ComandosDaArena.raiz("/speed 10"));
        assertEquals("speed", ComandosDaArena.raiz("speed"));
        assertEquals("speed", ComandosDaArena.raiz("  /SPEED walk 5  "));
        assertEquals("", ComandosDaArena.raiz(null));
        assertEquals("", ComandosDaArena.raiz("/"));
    }

    /**
     * O Essentials registra /essentials:speed além de /speed. Sem cortar no
     * último dois-pontos, bloquear o curto deixa a porta dos fundos aberta.
     */
    @Test
    @DisplayName("o prefixo do plugin não é porta dos fundos")
    void oPrefixoNaoEscapa() {
        assertEquals("speed", ComandosDaArena.raiz("/essentials:speed"));
        assertEquals("speed", ComandosDaArena.raiz("/minecraft:essentials:speed 9"));
        assertEquals("gamemode", ComandosDaArena.raiz("/minecraft:gamemode creative"));
    }

    // ------------------------------------------------------------------
    //  A lista
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o config.yml bloqueia o /speed, que foi o que motivou tudo")
    void oSpeedEstaBloqueado() {
        Set<String> lista = ComandosDaArena.lista(
                config.getStringList(ComandosDaArena.CHAVE));
        assertFalse(lista.isEmpty(), "a lista do config.yml veio vazia");
        assertTrue(lista.contains("speed"), "/speed não está bloqueado");
        assertTrue(lista.contains("fly"), "/fly não está bloqueado");
        assertTrue(lista.contains("gamemode"), "/gamemode não está bloqueado");
        assertTrue(lista.contains("god"), "/god não está bloqueado");
    }

    /**
     * Apagar a lista do yml não libera nada: a de fábrica volta a valer. É o
     * contrário do normal de propósito — uma lista vazia aqui seria o jogo
     * inteiro aberto a /speed sem ninguém perceber.
     */
    @Test
    @DisplayName("lista vazia no yml cai na de fábrica, e não em nada")
    void listaVaziaCaiNaDeFabrica() {
        assertEquals(ComandosDaArena.lista(ComandosDaArena.DE_FABRICA),
                ComandosDaArena.lista(List.of()));
        assertEquals(ComandosDaArena.lista(ComandosDaArena.DE_FABRICA),
                ComandosDaArena.lista((List<String>) null));
        assertTrue(ComandosDaArena.lista(List.of()).contains("speed"));
    }

    /**
     * A trava que importa mais do que a lista: um jogador trancado numa arena
     * sem saída é um defeito pior do que um jogador voando.
     */
    @Test
    @DisplayName("nenhuma saída é bloqueada — ninguém fica preso na arena")
    void asSaidasFicamAbertas() {
        assertNull(ComandosDaArena.saidaFechada(ComandosDaArena.lista(
                config.getStringList(ComandosDaArena.CHAVE))));
        assertNull(ComandosDaArena.saidaFechada(
                ComandosDaArena.lista(ComandosDaArena.DE_FABRICA)));
    }

    @Test
    @DisplayName("a conferência acusa uma lista que tranca o jogador")
    void aListaQueTrancaEhAcusada() {
        assertEquals("spawn", ComandosDaArena.saidaFechada(
                ComandosDaArena.lista(List.of("speed", "spawn"))));
        assertEquals("biga", ComandosDaArena.saidaFechada(
                ComandosDaArena.lista(List.of("/biga"))));
    }

    @Test
    @DisplayName("a lista de fábrica e a do yml dizem a mesma coisa")
    void oYmlEAFabricaNaoDivergem() {
        assertEquals(ComandosDaArena.lista(ComandosDaArena.DE_FABRICA),
                ComandosDaArena.lista(config.getStringList(
                        ComandosDaArena.CHAVE)),
                "o config.yml e a lista de fábrica divergiram: quem editar "
                        + "um dos dois tem de editar o outro");
    }
}
