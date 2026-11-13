package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A paleta de TODOS os yml do plugin, de uma vez.
 *
 * <h2>Por que este teste existe por cima dos testes de cada jogo</h2>
 *
 * Cada jogo tem o seu {@code paletaEhValida}, e os dez primeiros jogos o
 * têm. O décimo primeiro não teve, porque escrever o teste é a parte do
 * trabalho que se esquece quando o jogo já está de pé — e o custo de
 * esquecer é alto e silencioso: um nome de material errado no yml não
 * quebra nada, {@link CatalogoDeArena#cor} devolve o padrão, e a arena
 * nasce com um bloco cinza no lugar do bloco escolhido. Ninguém vê o
 * problema; vê-se uma arena feia.
 *
 * <p>Então este teste não pede que alguém se lembre: ele LISTA os yml do
 * plugin e confere os que têm paleta. Um jogo novo entra na conferência por
 * existir, e dois erros reais — {@code WAXED_EXPOSED_CUT_COPPER_STAIRS} como
 * placa e {@code WAXED_EXPOSED_CUT_COPPER_WALL}, que não existe — teriam
 * sido pegos aqui antes de virarem obra.
 *
 * <h2>A placa tem de ser de PAREDE</h2>
 *
 * {@link CatalogoDeArena#materialDaPlaca} precisa de um {@code _WALL_SIGN}:
 * uma placa de chão posta na parede cai no chão, e o letreiro do saguão
 * aparece deitado aos pés do jogador. É o erro mais fácil de cometer,
 * porque {@code ACACIA_SIGN} existe e parece certo.
 */
class PaletasTest {

    /** Quantos yml de arena o plugin tem hoje, para a lista não vir vazia. */
    private static final int MINIMO_DE_ARENAS = 17;

    private record Arquivo(String nome, YamlConfiguration yml) { }

    /**
     * Os yml empacotados, lidos do diretório de recursos compilados.
     *
     * Lista o diretório em vez de ter os nomes escritos aqui: uma lista
     * escrita à mão é exatamente o lugar onde o jogo novo não entra.
     */
    private static List<Arquivo> todos() throws Exception {
        URL um = PaletasTest.class.getResource("/minigames.yml");
        assertNotNull(um, "minigames.yml não foi empacotado");
        File pasta = new File(um.toURI()).getParentFile();
        File[] achados = pasta.listFiles(
                (dir, nome) -> nome.toLowerCase(Locale.ROOT).endsWith(".yml"));
        assertNotNull(achados, "não deu para listar " + pasta);
        List<Arquivo> saida = new ArrayList<>();
        Arrays.sort(achados);
        for (File arquivo : achados) {
            try (Reader leitor = new InputStreamReader(
                    Files.newInputStream(arquivo.toPath()),
                    StandardCharsets.UTF_8)) {
                saida.add(new Arquivo(arquivo.getName(),
                        YamlConfiguration.loadConfiguration(leitor)));
            }
        }
        return saida;
    }

    /**
     * Um yml é de ARENA quando tem {@code mundo:}.
     *
     * É o que {@link CatalogoDeArena} lê para saber onde construir, então é
     * a marca exata, e não uma lista de nomes escrita à mão — a lista é o
     * lugar onde o jogo novo não entra.
     */
    private static boolean ehArena(Arquivo arquivo) {
        return !arquivo.yml().getString("mundo", "").isBlank();
    }

    @Test
    @DisplayName("todo material de toda paleta de todo yml existe")
    void todaPaletaEhValida() throws Exception {
        List<Arquivo> arquivos = todos();
        assertTrue(arquivos.size() >= MINIMO_DE_ARENAS,
                "achei só " + arquivos.size() + " yml: a listagem do "
                        + "diretório falhou e o teste não está conferindo "
                        + "nada");
        int comPaleta = 0;
        for (Arquivo arquivo : arquivos) {
            var secao = arquivo.yml().getConfigurationSection("paleta");
            if (secao == null) {
                continue;
            }
            Map<String, Object> paleta = secao.getValues(false);
            assertFalse(paleta.isEmpty(), "a paleta de " + arquivo.nome()
                    + " está vazia");
            for (Map.Entry<String, Object> item : paleta.entrySet()) {
                String nome = String.valueOf(item.getValue())
                        .toUpperCase(Locale.ROOT);
                assertNotNull(Material.matchMaterial(nome),
                        arquivo.nome() + ": paleta." + item.getKey()
                                + " não é um material (" + nome + ")");
            }
            comPaleta++;
        }
        assertTrue(comPaleta >= MINIMO_DE_ARENAS,
                "só " + comPaleta + " yml têm paleta, e já há "
                        + MINIMO_DE_ARENAS + " arenas");
    }

    @Test
    @DisplayName("a placa de todo saguão é de parede")
    void todaPlacaEhDeParede() throws Exception {
        for (Arquivo arquivo : todos()) {
            String nome = arquivo.yml().getString("paleta.placa", "");
            if (nome.isBlank()) {
                continue;
            }
            assertTrue(nome.toUpperCase(Locale.ROOT).endsWith("_WALL_SIGN"),
                    arquivo.nome() + ": paleta.placa é " + nome
                            + " e tem de ser um _WALL_SIGN, senão o letreiro "
                            + "do saguão cai no chão");
            assertNotNull(Material.matchMaterial(
                    nome.toUpperCase(Locale.ROOT)));
        }
    }

    @Test
    @DisplayName("toda arena tem paleta, placa de parede e painel")
    void todaArenaSeApresenta() throws Exception {
        int arenas = 0;
        for (Arquivo arquivo : todos()) {
            if (!ehArena(arquivo)) {
                continue;
            }
            arenas++;
            assertNotNull(arquivo.yml().getConfigurationSection("paleta"),
                    arquivo.nome() + " é arena e não tem paleta");
            assertFalse(arquivo.yml().getString("paleta.placa", "").isBlank(),
                    arquivo.nome() + " é arena e não tem paleta.placa: o "
                            + "saguão não teria onde escrever [ENTRAR]");
            assertFalse(arquivo.yml().getString("textos.titulo", "").isBlank(),
                    arquivo.nome() + " não tem textos.titulo para o painel");
            assertTrue(arquivo.yml().getStringList("textos.regras").size() >= 3,
                    arquivo.nome() + " tem menos de três linhas de regra no "
                            + "painel de instruções");
        }
        assertTrue(arenas >= MINIMO_DE_ARENAS,
                "achei só " + arenas + " arenas e já há " + MINIMO_DE_ARENAS);
    }
}
