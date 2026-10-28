package codes.biga.bigacore;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ordem em que o /pedreiro organizar deixa o baú.
 *
 * <h2>Por que este teste existe</h2>
 *
 * Juntar pilha é aritmética e se vê na hora; a <i>ordem</i> é o que se nota
 * depois, quando o baú volta a parecer bagunçado mesmo sem nenhum slot
 * desperdiçado. A regra é "bloco antes de item, depois alfabético", e a
 * aposta por trás dela é que o prefixo do nome já agrupa a família sozinho —
 * {@code COBBLESTONE_SLAB} fica colado em {@code COBBLESTONE} porque começa
 * igual, sem tabela de famílias para alguém manter.
 *
 * Isso é testável aqui porque {@link Material} é enum puro: não precisa de
 * servidor no ar, ao contrário de {@code ItemStack}, que pede o ItemFactory
 * do Bukkit para comparar componentes.
 */
class OrganizadorTest {

    /** O que havia no baú que motivou o comando, mais dois não-blocos. */
    private static final List<Material> BAGUNCA = List.of(
            Material.STONE_BRICKS,
            Material.DIAMOND_PICKAXE,
            Material.COBBLESTONE_WALL,
            Material.DEEPSLATE,
            Material.COBBLESTONE,
            Material.TUFF,
            Material.COBBLESTONE_STAIRS,
            Material.ANDESITE,
            Material.COBBLED_DEEPSLATE,
            Material.TORCH,
            Material.COBBLESTONE_SLAB,
            Material.GRANITE,
            Material.STICK,
            Material.STONE);

    private static List<Material> ordenado() {
        List<Material> copia = new ArrayList<>(BAGUNCA);
        copia.sort(Organizador.ORDEM_MATERIAL);
        return copia;
    }

    @Test
    void familiaDoCobblestoneFicaJunta() {
        List<Material> ordem = ordenado();
        int primeiro = ordem.indexOf(Material.COBBLESTONE);
        int ultimo = ordem.indexOf(Material.COBBLESTONE_WALL);

        assertTrue(primeiro >= 0 && ultimo > primeiro, "ordem inesperada: " + ordem);
        assertEquals(3, ultimo - primeiro,
                "cobblestone, slab, stairs e wall precisam ser 4 posições seguidas: " + ordem);
    }

    /**
     * O baú inteiro, na ordem exata em que o comando vai deixá-lo.
     *
     * Vale mais que asserções soltas porque mostra as duas metades do registro
     * de uma vez: picareta e graveto (itens) na frente, blocos atrás e em
     * ordem alfabética entre si. Se o Mojang acrescentar bloco novo, a ordem
     * relativa destes catorze não muda — o alfabeto não se reordena.
     */
    @Test
    void itensNaFrenteBlocosAtrasEmOrdemAlfabetica() {
        assertEquals(List.of(
                        Material.DIAMOND_PICKAXE,
                        Material.STICK,
                        Material.ANDESITE,
                        Material.COBBLED_DEEPSLATE,
                        Material.COBBLESTONE,
                        Material.COBBLESTONE_SLAB,
                        Material.COBBLESTONE_STAIRS,
                        Material.COBBLESTONE_WALL,
                        Material.DEEPSLATE,
                        Material.GRANITE,
                        Material.STONE,
                        Material.STONE_BRICKS,
                        Material.TORCH,
                        Material.TUFF),
                ordenado());
    }

    @Test
    void ordemNaoDependeDeComoEntrou() {
        List<Material> embaralhado = new ArrayList<>(BAGUNCA);
        Collections.shuffle(embaralhado);
        embaralhado.sort(Organizador.ORDEM_MATERIAL);

        assertEquals(ordenado(), embaralhado,
                "a mesma bagunça em outra ordem precisa terminar igual");
    }

    @Test
    void comparadorEhTotal() {
        for (Material a : BAGUNCA) {
            assertEquals(0, Organizador.ORDEM_MATERIAL.compare(a, a), a + " contra si mesmo");
            for (Material b : BAGUNCA) {
                if (a == b) {
                    continue;
                }
                int ida = Organizador.ORDEM_MATERIAL.compare(a, b);
                int volta = Organizador.ORDEM_MATERIAL.compare(b, a);
                assertTrue(ida != 0, a + " e " + b + " não podem empatar");
                assertEquals(Integer.signum(ida), -Integer.signum(volta),
                        "comparação assimétrica entre " + a + " e " + b);
            }
        }
    }
}
