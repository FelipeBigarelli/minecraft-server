package codes.biga.bigacore;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tag paga e a lista do TAB.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * A tag é a única coisa do servidor em que um jogador escreve na tela de
 * todos os outros. Regra frouxa aqui não dá erro no console: ela PUBLICA, e
 * só se descobre pela reclamação de quem leu. Cada caso abaixo é um jeito
 * conhecido de furar uma lista de palavras — leet, acento, espaço no meio.
 */
class TagTest {

    private static final int MIN = 3;
    private static final int MAX = 12;
    private static final String CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -_.";
    private static final List<String> PROIBIDAS = List.of("admin", "dono", "staff", "mod");
    private static final List<String> OFENSAS = List.of("merda", "fdp");

    private static YamlConfiguration tags;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = TagTest.class.getResourceAsStream("/tags.yml")) {
            assertNotNull(in, "tags.yml não foi empacotado");
            tags = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static TagCatalog.Veredito conferir(String texto) {
        return TagCatalog.conferir(texto, MIN, MAX, CHARS, PROIBIDAS, OFENSAS);
    }

    @Test
    @DisplayName("tag normal passa")
    void tagNormalPassa() {
        assertEquals(TagCatalog.Veredito.OK, conferir("Rei da Ilha"));
        assertEquals(TagCatalog.Veredito.OK, conferir("Pescador"));
        assertEquals(TagCatalog.Veredito.OK, conferir("zBiga_1"));
        assertEquals(TagCatalog.Veredito.OK, conferir("Dev.Jr"));
    }

    @Test
    @DisplayName("se passar pela equipe nao passa, nem escrito torto")
    void naoDaParaSePassarPelaEquipe() {
        // Cada uma destas já é o jeito clássico de furar lista de palavras.
        for (String tentativa : List.of("admin", "ADMIN", "Admin", "4dm1n", "@dm1n",
                "a-d-m-i-n", "admín", "sou admin", "xADMINx", "D0N0", "st4ff")) {
            assertTrue(conferir(tentativa) == TagCatalog.Veredito.PROIBIDA
                            || conferir(tentativa) == TagCatalog.Veredito.CARACTERE
                            || conferir(tentativa) == TagCatalog.Veredito.LONGA,
                    "passou uma tag de equipe: " + tentativa);
        }
    }

    @Test
    @DisplayName("ofensa escrita com numero tambem e ofensa")
    void ofensaComLeetNaoPassa() {
        assertEquals(TagCatalog.Veredito.OFENSA, conferir("m3rda"));
        assertEquals(TagCatalog.Veredito.OFENSA, conferir("FDP"));
        assertEquals(TagCatalog.Veredito.OFENSA, conferir("f.d.p"));
    }

    @Test
    @DisplayName("nenhum caractere de formatacao entra na tag")
    void formatacaoNaoEntra() {
        // Com < e > liberados, uma tag viraria cor, nova linha ou até um
        // texto clicável na lista de todo mundo.
        for (String tentativa : List.of("<red>eu", "&cEu", "§cEu", "a<b", "eu>eu",
                "eu\neu", "⚔Guerra", "a  b")) {
            assertEquals(TagCatalog.Veredito.CARACTERE, conferir(tentativa),
                    "deixou passar: " + tentativa);
        }
    }

    @Test
    @DisplayName("tamanho: nem apelido de uma letra, nem faixa de protesto")
    void tamanhoEhConferido() {
        assertEquals(TagCatalog.Veredito.VAZIA, conferir(""));
        assertEquals(TagCatalog.Veredito.VAZIA, conferir("   "));
        assertEquals(TagCatalog.Veredito.CURTA, conferir("eu"));
        assertEquals(TagCatalog.Veredito.LONGA, conferir("o maior pescador da ilha"));
    }

    @Test
    @DisplayName("o normalizador achata leet, acento e separador")
    void normalizadorAchata() {
        assertEquals("admin", TagCatalog.normalizar("4dm1n"));
        assertEquals("admin", TagCatalog.normalizar("A-D-M-I-N"));
        assertEquals("admin", TagCatalog.normalizar("ádmín"));
        assertEquals("staff", TagCatalog.normalizar("st4ff"));
    }

    @Test
    @DisplayName("o ping sai com a cor da faixa, e ping 0 nao e erro")
    void pingPintado() {
        // Ping 0 é o do dono, jogando na mesma máquina do servidor. Pintar
        // de vermelho o único ping que ele vê todo dia seria mentir.
        assertTrue(ListaDeJogadores.pingPintado(0, 60, 120, 200).contains("green"));
        assertTrue(ListaDeJogadores.pingPintado(45, 60, 120, 200).contains("green"));
        assertTrue(ListaDeJogadores.pingPintado(90, 60, 120, 200).contains("yellow"));
        assertTrue(ListaDeJogadores.pingPintado(150, 60, 120, 200).contains("gold"));
        assertTrue(ListaDeJogadores.pingPintado(400, 60, 120, 200).contains("red"));
        assertTrue(ListaDeJogadores.pingPintado(-1, 60, 120, 200).contains("--"));
        assertTrue(ListaDeJogadores.pingPintado(45, 60, 120, 200).contains("45ms"));
    }

    @Test
    @DisplayName("o TPS nunca aparece acima de 20")
    void tpsNuncaPassaDe20() {
        // O servidor relata 20.02 em idle. "TPS 20.0" é informação; "20.02"
        // é ruído que faz todo mundo perguntar.
        assertTrue(ListaDeJogadores.tpsPintado(20.04).contains("20.0"));
        assertTrue(ListaDeJogadores.tpsPintado(20.04).contains("green"));
        assertTrue(ListaDeJogadores.tpsPintado(18.2).contains("yellow"));
        assertTrue(ListaDeJogadores.tpsPintado(9.0).contains("red"));
    }

    @Test
    @DisplayName("nenhuma linha do TAB deixa tag de cor aparecendo na tela")
    void miniMessageDoTabEstaFechado() {
        // MiniMessage não reclama de tag malformada: ela vira TEXTO. Uma
        // barra esquecida num </color> apareceria literalmente na lista de
        // todo mundo, e o console não diria nada.
        MiniMessage mini = MiniMessage.miniMessage();
        PlainTextComponentSerializer plano = PlainTextComponentSerializer.plainText();

        List<String> linhas = new java.util.ArrayList<>();
        linhas.addAll(tags.getStringList("lista.cabecalho"));
        linhas.addAll(tags.getStringList("lista.rodape"));
        linhas.add(tags.getString("lista.formato-do-nome", ""));
        linhas.add(tags.getString("lista.formato-da-tag", ""));

        for (String linha : linhas) {
            String pronta = linha
                    .replace("<online>", "3")
                    .replace("<limite>", "20")
                    .replace("<tps>", "<green>20.0</green>")
                    .replace("<dia>", "27")
                    .replace("<simbolo>", "B$")
                    .replace("<saldo>", "1240")
                    .replace("<ping>", "<green>23ms</green>")
                    .replace("<cor>", "<gold>")
                    .replace("</cor>", "</gold>");

            String visivel = plano.serialize(mini.deserialize(pronta,
                    Placeholder.unparsed("nome", "zBigaBiga"),
                    Placeholder.unparsed("texto", "Rei da Ilha"),
                    Placeholder.component("tag", net.kyori.adventure.text.Component.empty()),
                    Placeholder.unparsed("mundo", "world")));

            assertFalse(visivel.contains("<") || visivel.contains(">"),
                    "sobrou tag visível na lista do TAB: " + visivel);
        }
    }

    @Test
    @DisplayName("as cores do tags.yml existem de verdade no Adventure")
    void coresDoArquivoExistem() {
        List<String> cores = tags.getStringList("tag.cores");
        assertFalse(cores.isEmpty(), "tags.yml sem nenhuma cor");

        for (String cor : cores) {
            assertNotNull(net.kyori.adventure.text.format.NamedTextColor.NAMES.value(cor),
                    "cor inexistente no tags.yml: " + cor);
        }

        String padrao = tags.getString("tag.cor-padrao", "");
        assertTrue(cores.contains(padrao),
                "a cor-padrao " + padrao + " não está entre as liberadas");
    }

    @Test
    @DisplayName("a allowlist de caracteres nao deixa entrar formatacao")
    void allowlistDoArquivoEhSegura() {
        String caracteres = tags.getString("tag.caracteres", "");
        for (char proibido : new char[]{'<', '>', '&', '§', '\n', '\\'}) {
            assertFalse(caracteres.indexOf(proibido) >= 0,
                    "tags.yml libera um caractere de formatação: " + proibido);
        }
    }

    @Test
    @DisplayName("preco de troca nao passa o preco cheio")
    void trocaNaoCustaMaisQueComprar() {
        // Se trocar custasse mais que comprar, o caminho barato seria
        // /tag remover + /tag comprar — e a regra viraria pegadinha.
        int cheio = tags.getInt("tag.preco");
        int troca = tags.getInt("tag.preco-troca");
        assertTrue(troca <= cheio, "trocar (" + troca + ") custa mais que comprar (" + cheio + ")");
    }
}
