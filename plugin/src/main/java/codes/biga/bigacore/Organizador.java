package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Junta as pilhas soltas de um baú e põe o conteúdo em ordem.
 *
 * <h2>O problema</h2>
 *
 * Baú de mineração vira um mosaico: cobblestone em quatro pilhas de 64 mais
 * uma de 31, deepslate espalhado entre elas, tuff no meio. Nada disso é
 * desperdício de espaço por acaso — cada vez que você joga o inventário
 * inteiro no baú, o Minecraft preenche o primeiro slot livre em vez de
 * procurar uma pilha igual que ainda tenha espaço.
 *
 * <h2>O que "organizar" faz aqui</h2>
 *
 * Duas coisas, nesta ordem:
 * <ol>
 *   <li><b>Junta</b> — pilhas do mesmo item viram pilhas cheias, e sobra no
 *       máximo uma incompleta por tipo.</li>
 *   <li><b>Ordena</b> — bloco antes de item, depois alfabético pelo nome do
 *       material. Alfabético não é escolha estética: é o que mantém a família
 *       junta sozinha, sem tabela que envelhece. {@code COBBLESTONE},
 *       {@code COBBLESTONE_SLAB}, {@code COBBLESTONE_STAIRS} e
 *       {@code COBBLESTONE_WALL} caem lado a lado porque compartilham o
 *       prefixo, não porque alguém listou.</li>
 * </ol>
 *
 * <h2>O que decide se dois itens são "o mesmo"</h2>
 *
 * {@link ItemStack#isSimilar(ItemStack)}, igual ao {@link Amostra}. Compara
 * tipo e componentes ignorando só a quantidade, então espada encantada nunca
 * se funde com espada limpa — fundir seria destruir o encantamento.
 *
 * <h2>Por que nada se perde</h2>
 *
 * A conta de entrada e a de saída são conferidas antes de qualquer escrita.
 * Divergiu, o baú não é tocado. Juntar pilha nunca cria slot novo, mas uma
 * pilha acima do limite (coisa que /give permite) precisa quebrar em duas, e
 * aí o resultado pode ocupar mais slots do que cabe — por isso o teto também
 * é conferido antes.
 */
public final class Organizador {

    /** Teto de recipientes por varredura de região, para não travar o tick. */
    public static final int LIMITE_RECIPIENTES = 200;

    /**
     * A ordem na tela: a ordem de declaração do {@link Material}.
     *
     * O enum é gerado a partir do registro do Minecraft, e o registro vem em
     * dois blocos alfabéticos — itens primeiro, blocos depois. Isso agrupa a
     * família sozinho, de graça: {@code COBBLESTONE}, {@code COBBLESTONE_SLAB},
     * {@code COBBLESTONE_STAIRS} e {@code COBBLESTONE_WALL} ficam em quatro
     * posições seguidas porque compartilham o prefixo, sem tabela de famílias
     * que alguém precise manter quando sair bloco novo.
     *
     * O caminho óbvio seria separar com {@link Material#isBlock()}, e ele não
     * serve: em 26.3 esse método consulta o RegistryAccess do Paper, que só
     * existe com o servidor no ar. Chamar fora dali levanta
     * {@code IllegalStateException}, e o teste desta classe não subiria.
     * {@code ordinal()} é campo do enum, não custa nada e nunca falha.
     */
    public static final Comparator<Material> ORDEM_MATERIAL =
            Comparator.comparingInt(Material::ordinal);

    /** A ordem das pilhas: a de cima, e com a maior quantidade na frente. */
    public static final Comparator<ItemStack> ORDEM_PILHA =
            Comparator.comparing(ItemStack::getType, ORDEM_MATERIAL)
                    .thenComparing(Comparator.comparingInt(ItemStack::getAmount).reversed());

    private Organizador() {
    }

    /**
     * O resultado de organizar um recipiente.
     *
     * @param slotsAntes  pilhas ocupadas antes
     * @param slotsDepois pilhas ocupadas depois
     * @param itens       total de unidades (igual antes e depois, por construção)
     */
    public record Resultado(int slotsAntes, int slotsDepois, int itens) {

        /** Quantos slots o baú ganhou de volta. */
        public int liberados() {
            return slotsAntes - slotsDepois;
        }
    }

    /**
     * Junta as pilhas iguais e devolve a lista já ordenada.
     *
     * Trabalha em cópias: as pilhas que entram são as reais do baú e mexer no
     * amount delas esvaziaria o baú de verdade, do mesmo jeito que no
     * {@link Amostra#coletar}.
     */
    public static List<ItemStack> consolidar(List<ItemStack> entrada) {
        List<ItemStack> saida = new ArrayList<>();

        for (ItemStack item : entrada) {
            if (vazio(item)) {
                continue;
            }
            int restante = item.getAmount();

            // Primeiro completa o que já está aberto; só depois abre pilha nova.
            for (ItemStack alvo : saida) {
                if (restante <= 0) {
                    break;
                }
                if (!alvo.isSimilar(item)) {
                    continue;
                }
                int cabe = alvo.getMaxStackSize() - alvo.getAmount();
                if (cabe <= 0) {
                    continue;
                }
                int move = Math.min(cabe, restante);
                alvo.setAmount(alvo.getAmount() + move);
                restante -= move;
            }

            while (restante > 0) {
                ItemStack nova = item.clone();
                int quanto = Math.min(restante, Math.max(1, nova.getMaxStackSize()));
                nova.setAmount(quanto);
                saida.add(nova);
                restante -= quanto;
            }
        }

        saida.sort(ORDEM_PILHA);
        return saida;
    }

    /**
     * Organiza o recipiente no lugar.
     *
     * @throws IllegalStateException se a conferência de unidades ou de slots
     *                               falhar — e aí nada foi escrito
     */
    public static Resultado aplicar(Inventory inventario) {
        List<ItemStack> antes = new ArrayList<>();
        for (ItemStack item : inventario.getContents()) {
            if (!vazio(item)) {
                antes.add(item.clone());
            }
        }

        List<ItemStack> depois = consolidar(antes);

        int totalAntes = total(antes);
        int totalDepois = total(depois);
        if (totalAntes != totalDepois) {
            throw new IllegalStateException(
                    "conferência falhou: " + totalAntes + " unidades viraram " + totalDepois);
        }
        if (depois.size() > inventario.getSize()) {
            throw new IllegalStateException(
                    "resultado não cabe: " + depois.size() + " pilhas em " + inventario.getSize() + " slots");
        }

        inventario.clear();
        for (int slot = 0; slot < depois.size(); slot++) {
            inventario.setItem(slot, depois.get(slot));
        }

        return new Resultado(antes.size(), depois.size(), totalAntes);
    }

    /** Soma de unidades, para a conferência. */
    public static int total(List<ItemStack> pilhas) {
        int soma = 0;
        for (ItemStack pilha : pilhas) {
            soma += pilha.getAmount();
        }
        return soma;
    }

    private static boolean vazio(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}
