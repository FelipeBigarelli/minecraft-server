package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A obra grande feita aos pedaços.
 *
 * <h2>O caso que deu origem a tudo isto</h2>
 *
 * {@link #aCaixaNaoCabeNumTique}. O vigia do Paper despejou o rastro de pilha
 * três vezes num {@code /biga refazer CONFIRMAR}: {@code Math.hypot} ←
 * {@code ParkourCatalog.percurso} ← {@code cumeY} ← {@code saguaoY} ←
 * {@code tetoDaObra} ← {@code ParkourBuilder.contarOcupados}. Duas coisas
 * erradas no mesmo rastro, e os dois consertos estão testados aqui e em
 * {@link ParkourTest}:
 *
 * <ol>
 *   <li>o limite do laço era uma CHAMADA, reavaliada a cada uma das meio
 *       milhão de voltas — hoje os limites entram como inteiros, em
 *       {@link ArenaComum#contarOcupados};</li>
 *   <li>e mesmo barata, a varredura das vinte arenas não cabe num tique —
 *       hoje ela para onde o orçamento acaba e continua no tique seguinte.</li>
 * </ol>
 */
class ObraEmFatiasTest {

    @Test
    @DisplayName("a caixa de uma arena não cabe num tique")
    void aCaixaNaoCabeNumTique() {
        // Alcance 50 é a ordem de grandeza real das arenas: dez mil e
        // duzentas colunas, cada uma com algumas dezenas de casas. Vinte
        // dessas num tique de 50 ms é o travamento relatado.
        assertEquals(101 * 101, ObraEmFatias.colunasDaCaixa(50));
        assertEquals(1, ObraEmFatias.colunasDaCaixa(0));
        assertEquals(1, ObraEmFatias.colunasDaCaixa(-7),
                "alcance negativo é uma caixa de nada, não um estouro");
        assertTrue(ObraEmFatias.colunasDaCaixa(50) > 10_000,
                "se isto couber num tique, a obra em fatias perdeu o motivo");
    }

    @Test
    @DisplayName("a fila é drenada na ordem, e até o fim")
    void aFilaVaiAteOFim() {
        // Sem servidor por perto dá para exercitar o protocolo da fatia, que
        // é o miolo: passo() devolve false quando aquele pedaço acabou, e a
        // obra só termina quando a fila esvazia.
        List<String> feitos = new ArrayList<>();
        ObraEmFatias.Fatia tres = contar("a", 3, feitos);
        ObraEmFatias.Fatia uma = contar("b", 1, feitos);

        while (tres.passo()) {
            // roda
        }
        while (uma.passo()) {
            // roda
        }
        assertEquals(List.of("a1", "a2", "a3", "b1"), feitos);
    }

    @Test
    @DisplayName("uma fatia que acabou não trabalha de novo")
    void aFatiaQueAcabouNaoVolta() {
        // Importa porque o drenador chama peek() antes de poll(): se uma
        // fatia esgotada voltasse a trabalhar no mesmo tique, a última
        // coluna da caixa seria varrida duas vezes.
        List<String> feitos = new ArrayList<>();
        ObraEmFatias.Fatia uma = contar("c", 1, feitos);
        assertFalse(uma.passo(), "uma volta só: já acabou nesta");
        assertEquals(1, feitos.size());
        assertFalse(uma.passo());
        assertEquals(1, feitos.size(), "trabalhou depois de acabar");
    }

    /** Uma fatia de mentira: anota n passos e diz que acabou no último. */
    private static ObraEmFatias.Fatia contar(String nome, int quantos,
                                             List<String> feitos) {
        int[] falta = {quantos};
        return () -> {
            if (falta[0] <= 0) {
                return false;
            }
            feitos.add(nome + (quantos - falta[0] + 1));
            falta[0]--;
            return falta[0] > 0;
        };
    }

    @Test
    @DisplayName("toda etiqueta de painel começa pelo prefixo combinado")
    void asEtiquetasTemOPrefixo() throws Exception {
        // limparTodosOsPaineis é o que apaga painel no refazer, e ela acha o
        // que apagar pelo PREFIXO, não pela etiqueta exata — é o que a faz
        // valer para os vinte jogos sem conhecer nenhum. Uma etiqueta nova
        // escrita fora da convenção vira painel que nunca mais sai do mundo,
        // e ninguém ligaria uma coisa à outra. Este teste varre as classes
        // compiladas e confere uma por uma.
        List<String> fora = new ArrayList<>();
        int vistas = 0;
        for (Class<?> classe : classesDoPlugin()) {
            for (java.lang.reflect.Field campo : classe.getDeclaredFields()) {
                if (!campo.getName().startsWith("TAG_")
                        || campo.getType() != String.class
                        || !java.lang.reflect.Modifier
                                .isStatic(campo.getModifiers())) {
                    continue;
                }
                campo.setAccessible(true);
                String valor = (String) campo.get(null);
                vistas++;
                if (valor == null || !valor.startsWith(
                        ArenaComum.PREFIXO_DAS_ETIQUETAS)) {
                    fora.add(classe.getSimpleName() + "." + campo.getName()
                            + " = " + valor);
                }
            }
        }
        assertTrue(vistas >= 20, "só " + vistas + " etiqueta(s) encontradas: "
                + "a varredura das classes não achou o que devia");
        assertEquals(List.of(), fora, "etiqueta fora da convenção '"
                + ArenaComum.PREFIXO_DAS_ETIQUETAS + "': " + fora);
    }

    /** As classes compiladas do plugin, lidas da pasta do próprio teste. */
    private static List<Class<?>> classesDoPlugin() throws Exception {
        // A partir de uma classe do PLUGIN, não do teste: o pacote tem o
        // mesmo nome nos dois lados e target/test-classes vem antes no
        // classpath — procurar pelo pacote acha a pasta dos testes, que não
        // tem etiqueta nenhuma, e o teste passaria sem olhar nada.
        java.net.URL raiz = ArenaComum.class.getResource("ArenaComum.class");
        List<Class<?>> achadas = new ArrayList<>();
        if (raiz == null || !"file".equals(raiz.getProtocol())) {
            return achadas;
        }
        java.io.File pasta = new java.io.File(raiz.toURI()).getParentFile();
        String[] arquivos = pasta.list();
        if (arquivos == null) {
            return achadas;
        }
        java.util.Arrays.sort(arquivos);
        for (String arquivo : arquivos) {
            if (!arquivo.endsWith(".class") || arquivo.contains("$")) {
                continue;
            }
            String nome = "codes.biga.bigacore."
                    + arquivo.substring(0, arquivo.length() - ".class".length());
            achadas.add(Class.forName(nome));
        }
        return achadas;
    }
}
