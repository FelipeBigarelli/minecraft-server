package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A decisão de atualizar, avisar ou não mexer num yml do disco.
 *
 * <h2>O caso que deu origem a tudo isto</h2>
 *
 * {@link #oArquivoIntocadoDeUmaVersaoVelhaEhAtualizado}. O servidor rodou
 * três semanas com os vinte ymls do primeiro dia: o Sky Wars sem as ilhas
 * afuniladas, a mineração sem tocha, o quiz com 48 das 198 perguntas. Nada
 * disso aparecia — nem no console, nem em lugar nenhum —, porque
 * {@code saveResource(nome, false)} escreve uma vez e cala para sempre.
 *
 * <h2>O caso oposto, que é o perigoso</h2>
 *
 * {@link #oArquivoEditadoNuncaEhTrocadoSozinho}. Consertar o primeiro caso
 * sobrescrevendo tudo apagaria a edição de quem mexeu no yml — e um plugin
 * que apaga a configuração do dono na subida é pior do que um que esconde
 * novidade dele.
 */
class ConfigsDoDiscoTest {

    private static final String PADRAO_VELHO = ConfigsDoDisco.digital(
            "ilha-raio: 3".getBytes(StandardCharsets.UTF_8));
    private static final String PADRAO_NOVO = ConfigsDoDisco.digital(
            "ilha-raio: 6".getBytes(StandardCharsets.UTF_8));
    private static final String EDITADO = ConfigsDoDisco.digital(
            "ilha-raio: 3 # mexi aqui".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("arquivo que não existe é escrito")
    void oArquivoQueNaoExisteEhEscrito() {
        assertEquals(ConfigsDoDisco.Acao.ESCREVER,
                ConfigsDoDisco.decidir(PADRAO_NOVO, null, null, null));
    }

    @Test
    @DisplayName("disco igual ao jar não dá trabalho nenhum")
    void oDiscoIgualAoJar() {
        assertEquals(ConfigsDoDisco.Acao.EM_DIA,
                ConfigsDoDisco.decidir(PADRAO_NOVO, PADRAO_NOVO, null, null));
        assertEquals(ConfigsDoDisco.Acao.EM_DIA, ConfigsDoDisco.decidir(
                PADRAO_NOVO, PADRAO_NOVO, PADRAO_VELHO, PADRAO_VELHO));
    }

    @Test
    @DisplayName("arquivo intocado de uma versão velha é atualizado")
    void oArquivoIntocadoDeUmaVersaoVelhaEhAtualizado() {
        // O caso do servidor: escrevemos o padrão velho, ninguém tocou, e o
        // jar agora traz outro. Trocar é a resposta certa — e com cópia.
        assertEquals(ConfigsDoDisco.Acao.ATUALIZAR, ConfigsDoDisco.decidir(
                PADRAO_NOVO, PADRAO_VELHO, PADRAO_VELHO, PADRAO_VELHO));
    }

    @Test
    @DisplayName("arquivo editado nunca é trocado sozinho")
    void oArquivoEditadoNuncaEhTrocadoSozinho() {
        assertEquals(ConfigsDoDisco.Acao.AVISAR, ConfigsDoDisco.decidir(
                PADRAO_NOVO, EDITADO, PADRAO_VELHO, PADRAO_VELHO));
    }

    @Test
    @DisplayName("sem anotação, avisa em vez de arriscar")
    void semAnotacaoAvisa() {
        // Primeira subida com o caderninho e um arquivo diferente do jar:
        // não dá para saber se é edição do dono ou atraso. Avisar é o lado
        // em que o pior resultado é um recado a mais no console.
        assertEquals(ConfigsDoDisco.Acao.AVISAR,
                ConfigsDoDisco.decidir(PADRAO_NOVO, EDITADO, null, null));
        assertEquals(ConfigsDoDisco.Acao.AVISAR, ConfigsDoDisco.decidir(
                PADRAO_NOVO, EDITADO, PADRAO_VELHO, null));
    }

    @Test
    @DisplayName("padrão que não mudou não mexe em nada, nem no editado")
    void oPadraoQueNaoMudou() {
        assertEquals(ConfigsDoDisco.Acao.EM_DIA, ConfigsDoDisco.decidir(
                PADRAO_VELHO, EDITADO, PADRAO_VELHO, PADRAO_VELHO));
    }

    @Test
    @DisplayName("a digital muda com um byte e some sem bytes")
    void aDigital() {
        assertNotEquals(PADRAO_VELHO, PADRAO_NOVO);
        assertEquals(PADRAO_VELHO, ConfigsDoDisco.digital(
                "ilha-raio: 3".getBytes(StandardCharsets.UTF_8)));
        assertEquals(64, PADRAO_VELHO.length(), "SHA-256 em hexa dá 64");
        assertNull(ConfigsDoDisco.digital(null));
    }

    @Test
    @DisplayName("as chaves que faltam saem na ordem do padrão")
    void asChavesQueFaltam() {
        Set<String> padrao = new LinkedHashSet<>(List.of("arena", "arena.y",
                "arena.ilha-raio", "arena.ilha-afunilamento", "mapa-pronto",
                "mapa-pronto.ativo"));
        Set<String> disco = new LinkedHashSet<>(List.of("arena", "arena.y",
                "arena.ilha-raio"));
        assertEquals(List.of("arena.ilha-afunilamento", "mapa-pronto",
                        "mapa-pronto.ativo"),
                ConfigsDoDisco.faltando(padrao, disco));
        assertEquals(List.of(), ConfigsDoDisco.faltando(disco, padrao),
                "chave a mais no disco não é falta");
    }

    @Test
    @DisplayName("o recado diz o arquivo, as chaves e o comando")
    void oRecado() {
        String recado = ConfigsDoDisco.recado("sky-wars.yml",
                List.of("a", "b", "c", "d", "e", "f", "g", "h"));
        assertTrue(recado.contains("sky-wars.yml"), recado);
        assertTrue(recado.contains("8 chave(s)"), recado);
        assertTrue(recado.contains("…"), "a lista longa tinha de ser cortada");
        assertTrue(recado.contains("/biga configs atualizar"), recado);

        String semChave = ConfigsDoDisco.recado("quiz.yml", List.of());
        assertFalse(semChave.contains("Faltam"), "sem chave faltando, não se "
                + "inventa uma lista vazia: " + semChave);
        assertTrue(semChave.contains("quiz.yml"), semChave);
    }

    @Test
    @DisplayName("a cópia leva a data no nome e não apaga a anterior")
    void oNomeDaCopia() {
        Date quando = new Date(1763000000000L);
        String um = ConfigsDoDisco.nomeDaCopia("sky-wars.yml", quando);
        assertTrue(um.startsWith("sky-wars.yml.bak-"), um);
        assertEquals(um, ConfigsDoDisco.nomeDaCopia("sky-wars.yml", quando));
        assertNotEquals(um, ConfigsDoDisco.nomeDaCopia("sky-wars.yml",
                new Date(quando.getTime() + 60_000)));
    }

    @Test
    @DisplayName("todo yml que o plugin entrega está na lista de conferência")
    void aListaCobreOsRecursos() {
        // O defeito que isto pega: alguém acrescenta um jogo, põe o yml em
        // resources e esquece desta lista — e o arquivo novo volta a ser
        // escrito uma vez e nunca mais, que é exatamente o bug de origem.
        for (String arquivo : BigaCore.CONFIGS_DO_JAR) {
            assertTrue(getClass().getResource("/" + arquivo) != null,
                    "a lista cita " + arquivo + " e ele não está empacotado");
        }
        assertFalse(BigaCore.CONFIGS_DO_JAR.contains("config.yml"),
                "o config.yml fica de fora: é o único que todo servidor "
                        + "edita de verdade");
        assertEquals(BigaCore.CONFIGS_DO_JAR.size(),
                Set.copyOf(BigaCore.CONFIGS_DO_JAR).size(),
                "a lista tem arquivo repetido");
    }
}
