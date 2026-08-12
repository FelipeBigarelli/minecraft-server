package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Item flutuante e girando sobre cada banca do balcão.
 *
 * <h2>Por que é código nosso e não config do ChestShop</h2>
 *
 * O ChestShop 3.13 não tem essa funcionalidade. A única opção parecida no
 * config é {@code SHOWITEM_MESSAGE}, que apenas deixa o nome do item clicável
 * no chat. Item girando sobre o baú é característica do QuickShop, um plugin
 * diferente — e trocar o plugin de loja significaria refazer a stack econômica
 * inteira, que já está validada.
 *
 * Como o Paper 26.2 tem {@link ItemDisplay} nativo, dá para fazer isso sem
 * plugin nenhum e com controle total do ciclo de vida.
 *
 * <h2>Como a rotação funciona sem pesar</h2>
 *
 * A primeira versão usava {@code Billboard.VERTICAL}, que faz o item sempre
 * encarar o jogador — e por isso ele nunca girava: o billboard sobrescreve a
 * rotação. Agora o billboard é {@code FIXED} e o giro é nosso.
 *
 * O truque para não custar caro é a <b>interpolação do cliente</b>: o servidor
 * manda um ângulo novo a cada {@value #PERIODO_TICKS} ticks e diz ao cliente
 * para interpolar durante esse mesmo intervalo. O cliente desenha o movimento
 * suave sozinho, e o servidor envia 5 pacotes por segundo por entidade em vez
 * de 20.
 *
 * <h2>Entidade é lixo se ninguém limpar</h2>
 *
 * O snapshot de rollback guarda <b>blocos</b>. Se o /biga loja desfazer
 * restaurasse o terreno e deixasse as entidades, a loja sumiria e os itens
 * continuariam flutuando no ar sobre o mato. Por isso toda entidade criada aqui
 * leva a tag {@value #TAG}, e a remoção varre por tag em vez de por posição.
 */
public final class ShopDisplays {

    /** Marca de propriedade. Sem isto não há como limpar com segurança depois. */
    public static final String TAG = "bigacore_market_display";

    /** Item pequeno o suficiente para flutuar sem cobrir a placa. */
    private static final float ESCALA = 0.45f;

    /** Intervalo entre atualizações de ângulo. */
    private static final long PERIODO_TICKS = 4L;

    /** Quanto o item gira a cada atualização, em radianos. */
    private static final float PASSO = (float) (Math.PI * 2.0 / 40.0);

    private final BigaCore plugin;
    private final List<ItemDisplay> girando = new ArrayList<>();

    private BukkitTask tarefa;
    private float angulo;
    private boolean varreuMundo;

    public ShopDisplays(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Ciclo de vida
    // ------------------------------------------------------------------

    /** Liga o giro. Chamado no onEnable. */
    public void start() {
        if (tarefa != null) {
            return;
        }
        tarefa = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::girar, PERIODO_TICKS, PERIODO_TICKS);
    }

    /** Desliga o giro sem apagar nada do mundo. Chamado no onDisable. */
    public void stop() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        girando.clear();
        varreuMundo = false;
    }

    // ------------------------------------------------------------------
    //  Rotação
    // ------------------------------------------------------------------

    private void girar() {
        // Na primeira passada depois de um restart, recolhe as entidades que
        // já existiam no mundo — senão a loja da sessão anterior ficaria parada.
        if (!varreuMundo) {
            varreuMundo = true;
            for (World world : plugin.getServer().getWorlds()) {
                coletar(world);
            }
        }

        if (girando.isEmpty()) {
            return;
        }

        angulo += PASSO;
        if (angulo > (float) (Math.PI * 2.0)) {
            angulo -= (float) (Math.PI * 2.0);
        }

        Iterator<ItemDisplay> it = girando.iterator();
        while (it.hasNext()) {
            ItemDisplay display = it.next();
            if (!display.isValid()) {
                it.remove();
                continue;
            }
            aplicarAngulo(display, angulo);
        }
    }

    private void aplicarAngulo(ItemDisplay display, float radianos) {
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(radianos, 0f, 1f, 0f),
                new Vector3f(ESCALA, ESCALA, ESCALA),
                new AxisAngle4f(0f, 0f, 0f, 1f)
        ));
        // O cliente desenha o caminho entre o ângulo antigo e o novo.
        display.setInterpolationDelay(0);
        display.setInterpolationDuration((int) PERIODO_TICKS);
    }

    private void coletar(World world) {
        for (Entity entidade : world.getEntities()) {
            if (entidade instanceof ItemDisplay display
                    && entidade.getScoreboardTags().contains(TAG)) {
                girando.add(display);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Criação e limpeza
    // ------------------------------------------------------------------

    /**
     * Cria o item flutuante acima do barril da vaga.
     *
     * Fica um pouco acima do balcão, centralizado no bloco.
     */
    public void spawn(World world, SpawnShopBuilder.Layout layout,
                      int u, int v, int floorY, EconomyCatalog.PriceEntry item) {

        Material material = Material.matchMaterial(item.key());
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("[display] '" + item.key()
                    + "' não é um item válido; vaga fica sem item flutuante.");
            return;
        }

        Location alvo = layout.block(world, u, v, floorY + 2)
                .getLocation()
                .add(0.5, 0.25, 0.5);

        ItemDisplay display = world.spawn(alvo, ItemDisplay.class, novo -> {
            novo.setItemStack(new ItemStack(material));
            // FIXED, não VERTICAL: billboard sobrescreveria a rotação.
            novo.setBillboard(Display.Billboard.FIXED);
            novo.setViewRange(0.6f);
            novo.setPersistent(true);
            novo.addScoreboardTag(TAG);
        });

        aplicarAngulo(display, angulo);
        girando.add(display);
    }

    /**
     * Remove todos os itens flutuantes da loja no mundo indicado.
     *
     * Chamado antes de construir (para não empilhar duas gerações) e no
     * rollback. Idempotente: pode ser chamado quantas vezes precisar.
     */
    public int removeAll(World world) {
        int removidos = 0;

        for (Entity entidade : world.getEntities()) {
            if (!(entidade instanceof ItemDisplay)) {
                continue;
            }
            if (!entidade.getScoreboardTags().contains(TAG)) {
                continue;
            }
            entidade.remove();
            removidos++;
        }

        girando.removeIf(display -> !display.isValid());

        if (removidos > 0) {
            plugin.getLogger().info("[display] " + removidos
                    + " item(ns) flutuante(s) removido(s).");
        }
        return removidos;
    }
}
