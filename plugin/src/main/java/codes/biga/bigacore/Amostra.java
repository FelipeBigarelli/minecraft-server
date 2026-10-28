package codes.biga.bigacore;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tira 1 de cada item distinto de um baú — sem encostar no conteúdo dele.
 *
 * <h2>Por que isso existe</h2>
 *
 * Num sistema de organização (golem de cobre, funil com filtro, ChestShop) o
 * baú de destino precisa já conter um exemplar do item que vai receber. Montar
 * isso na mão significa abrir o baú cheio, retirar um de cada e repor — e é aí
 * que se perde item. Aqui nada sai do baú: cada pilha encontrada é clonada e a
 * cópia vira amostra de 1 unidade.
 *
 * <h2>O que conta como "item diferente"</h2>
 *
 * {@link ItemStack#isSimilar(ItemStack)}, que compara tipo e componentes e
 * ignora só a quantidade. Então espada encantada e espada limpa saem as duas,
 * que é o certo: num filtro elas não são a mesma coisa.
 */
public final class Amostra {

    /** Alcance da mira em blocos. 6 é o mesmo do alcance de interação. */
    public static final int ALCANCE = 6;

    /** Teto de itens distintos por chamada, para não entupir o inventário. */
    public static final int LIMITE = 500;

    /** Teto de chunks varridos no modo região. */
    public static final int LIMITE_CHUNKS = 400;

    private Amostra() {
    }

    /** O container que o jogador está olhando, ou null. */
    public static Container mirado(Player jogador) {
        Block bloco = jogador.getTargetBlockExact(ALCANCE);
        if (bloco == null) {
            return null;
        }
        return bloco.getState() instanceof Container recipiente ? recipiente : null;
    }

    /**
     * Acrescenta ao acumulador 1 de cada item ainda não visto.
     *
     * O clone é o ponto todo do método: {@code getContents()} devolve as
     * pilhas reais, e mexer no amount delas esvaziaria o baú de verdade.
     */
    public static void coletar(Inventory inventario, List<ItemStack> acumulador) {
        for (ItemStack item : inventario.getContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (acumulador.size() >= LIMITE) {
                return;
            }
            boolean repetido = false;
            for (ItemStack visto : acumulador) {
                if (visto.isSimilar(item)) {
                    repetido = true;
                    break;
                }
            }
            if (!repetido) {
                ItemStack copia = item.clone();
                copia.setAmount(1);
                acumulador.add(copia);
            }
        }
    }

    /**
     * Todos os containers dentro da caixa, varrendo por block entity.
     *
     * Varrer bloco a bloco custaria largura*altura*comprimento chamadas de
     * getState(); os block entities do chunk já vêm prontos e são poucos.
     * Metade de baú duplo aparece duas vezes, o que é inofensivo: a dedupe
     * de itens acontece depois.
     */
    public static List<Container> naRegiao(World mundo, Location a, Location b) {
        int x1 = Math.min(a.getBlockX(), b.getBlockX());
        int x2 = Math.max(a.getBlockX(), b.getBlockX());
        int y1 = Math.min(a.getBlockY(), b.getBlockY());
        int y2 = Math.max(a.getBlockY(), b.getBlockY());
        int z1 = Math.min(a.getBlockZ(), b.getBlockZ());
        int z2 = Math.max(a.getBlockZ(), b.getBlockZ());

        int chunks = ((x2 >> 4) - (x1 >> 4) + 1) * ((z2 >> 4) - (z1 >> 4) + 1);
        if (chunks > LIMITE_CHUNKS) {
            throw new IllegalArgumentException(
                    "Região grande demais: " + chunks + " chunks (máximo " + LIMITE_CHUNKS + ").");
        }

        List<Container> achados = new ArrayList<>();
        for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) {
            for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) {
                Chunk chunk = mundo.getChunkAt(cx, cz);
                for (BlockState estado : chunk.getTileEntities()) {
                    if (!(estado instanceof Container recipiente)) {
                        continue;
                    }
                    int x = estado.getX();
                    int y = estado.getY();
                    int z = estado.getZ();
                    if (x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2) {
                        achados.add(recipiente);
                    }
                }
            }
        }
        return achados;
    }

    /** Entrega no inventário; o que não couber cai no chão em vez de sumir. */
    public static int entregar(Player jogador, List<ItemStack> itens) {
        Map<Integer, ItemStack> sobra =
                jogador.getInventory().addItem(itens.toArray(new ItemStack[0]));
        for (ItemStack resto : sobra.values()) {
            jogador.getWorld().dropItem(jogador.getLocation(), resto);
        }
        return sobra.size();
    }
}
