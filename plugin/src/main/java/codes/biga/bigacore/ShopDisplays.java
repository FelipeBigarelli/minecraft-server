package codes.biga.bigacore;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
                      int u, int v, int alturaDaPlaca, EconomyCatalog.PriceEntry item) {

        Material material = Material.matchMaterial(item.key());
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("[display] '" + item.key()
                    + "' não é um item válido; vaga fica sem item flutuante.");
            return;
        }

        // Na altura da PLACA, nao do piso: o balcao tem duas prateleiras, e
        // com floorY fixo os itens do andar de cima nasciam todos colados no
        // chao, longe do barril que representam.
        Location alvo = layout.block(world, u, v, alturaDaPlaca)
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

    // ------------------------------------------------------------------
    //  Construção baixada: pendurar a partir das placas já escritas
    // ------------------------------------------------------------------

    /**
     * Pendura um item na frente de cada placa do Admin Shop num raio.
     *
     * <h2>Por que existe um segundo caminho de criação</h2>
     *
     * O {@link #spawn} acima serve quando o BigaCore é o arquiteto: ele recebe
     * o {@link SpawnShopBuilder.Layout} e já sabe onde fica cada barril. Na
     * loja de 30/10/2026 a construção veio pronta, colada com WorldEdit, e
     * não existe Layout nenhum — só 60 placas espalhadas por uma planta que o
     * plugin nunca viu.
     *
     * Aqui a placa É o layout: a posição e a direção dela dizem onde o item
     * deve flutuar, e a linha 4 diz qual item é.
     *
     * <h2>O item vem da linha da placa, não da ordem</h2>
     *
     * Parear por índice (primeira placa = primeiro item do economy.yml) daria
     * a loja inteira errada em silêncio se alguém apagasse uma placa do meio.
     * A linha 4 é traduzida de volta ao material pelo mesmo {@code nomes:} que
     * a escreveu; placa que não casa fica sem item e é CONTADA no relatório.
     */
    public Relatorio pendurar(Location centro, int raio, boolean aplicar) {
        int limite = Math.max(1, Math.min(raio, ShopSignWriter.RAIO_MAXIMO));
        List<Sign> placas = placasDoAdminShop(centro, limite);
        Map<String, Material> indice = indiceDoCatalogo();

        int removidas = aplicar ? removerNoRaio(centro, limite) : 0;
        int penduradas = 0;
        int semMaterial = 0;
        int semEspaco = 0;

        for (Sign placa : placas) {
            Material material = materialDaPlaca(placa, indice);
            if (material == null || material.isAir()) {
                semMaterial++;
                continue;
            }
            Location ponto = pontoDoItem(placa);
            if (ponto == null) {
                semEspaco++;
                continue;
            }
            if (aplicar) {
                criarNoPonto(ponto, material);
            }
            penduradas++;
        }

        if (aplicar) {
            plugin.getLogger().info("[display] " + penduradas + " item(ns) pendurados nas placas, "
                    + removidas + " removido(s) antes.");
        }

        return new Relatorio(placas.size(), penduradas, semMaterial, semEspaco, removidas, limite);
    }

    /**
     * Remove só os itens flutuantes do raio.
     *
     * O {@link #removeAll} varre o mundo inteiro, o que é o certo no rollback
     * da loja gerada. Para a construção colada num terreno isso seria demais:
     * apagaria a vitrine de outra loja no mesmo mundo.
     */
    public int removerNoRaio(Location centro, int raio) {
        World world = centro.getWorld();
        if (world == null) {
            return 0;
        }
        int limite = Math.max(1, Math.min(raio, ShopSignWriter.RAIO_MAXIMO));
        int removidos = 0;

        for (Entity entidade : world.getEntities()) {
            if (entidade instanceof ItemDisplay
                    && entidade.getScoreboardTags().contains(TAG)
                    && dentroDoRaio(entidade.getLocation(), centro, limite)) {
                entidade.remove();
                removidos++;
            }
        }

        girando.removeIf(display -> !display.isValid());
        return removidos;
    }

    private void criarNoPonto(Location alvo, Material material) {
        World world = alvo.getWorld();
        if (world == null) {
            return;
        }
        ItemDisplay display = world.spawn(alvo, ItemDisplay.class, novo -> {
            novo.setItemStack(new ItemStack(material));
            novo.setBillboard(Display.Billboard.FIXED);
            novo.setViewRange(0.6f);
            novo.setPersistent(true);
            // O interior da construção baixada é escuro de propósito. Sem
            // forçar o brilho o ícone some na penumbra e a vitrine não serve.
            novo.setBrightness(new Display.Brightness(15, 15));
            novo.setShadowRadius(0f);
            novo.addScoreboardTag(TAG);
        });

        aplicarAngulo(display, angulo);
        girando.add(display);
    }

    /**
     * O ponto onde o item flutua, ou null se não couber em lugar nenhum.
     *
     * <h2>Por que acima e não à frente</h2>
     *
     * A primeira versão punha o item um bloco à frente da placa, na mesma
     * altura. Ficou errado na prática: o olho do jogador fica em Y+1,6 a partir
     * do piso, ou seja, exatamente na altura da placa — então o ícone pousava
     * na linha de visão e TAPAVA o texto que ele deveria ilustrar.
     *
     * Acima resolve os dois lados: a placa continua na altura do olhar, onde se
     * lê, e o item fica no ar sobre ela, visível de longe no corredor inteiro.
     *
     * <h2>Por que procurar o primeiro espaço livre</h2>
     *
     * Em vez de fixar "uma casa acima", sobe até achar ar. Assim a mesma regra
     * serve para a banca simples (placa, depois ar) e para a banca com
     * prateleira (placa, bloco, depois ar) sem precisar de dois comandos.
     */
    private static final int SUBIDA_MAXIMA = 2;

    private static Location pontoDoItem(Sign placa) {
        Block bloco = placa.getBlock();
        for (int acima = 1; acima <= SUBIDA_MAXIMA; acima++) {
            Block candidato = bloco.getRelative(0, acima, 0);
            if (candidato.isPassable()) {
                return candidato.getLocation().add(0.5, 0.45, 0.5);
            }
        }
        // Teto baixo em cima da placa: cai para a frente, que é melhor que
        // nenhum item. Continua contado como vitrine no relatório.
        if (placa.getBlockData() instanceof Directional direcional) {
            Location frente = placa.getLocation().add(0.5, 0.5, 0.5)
                    .add(direcional.getFacing().getDirection());
            if (frente.getBlock().isPassable()) {
                return frente;
            }
        }
        return null;
    }

    /**
     * Nome escrito na placa -> material.
     *
     * Indexa pelos DOIS nomes — a chave do catálogo e a tradução — porque
     * {@code admin-shop.nomes-em-portugues} pode ser desligado depois, e aí as
     * placas antigas seguem em português enquanto as novas saem em inglês.
     */
    private Map<String, Material> indiceDoCatalogo() {
        Map<String, Material> indice = new HashMap<>();
        EconomyCatalog catalog = plugin.economyCatalog();

        for (EconomyCatalog.PriceEntry item : catalog.adminShopWall(Integer.MAX_VALUE)) {
            Material material = Material.matchMaterial(item.key());
            if (material == null) {
                plugin.getLogger().warning("[display] '" + item.key()
                        + "' não é material deste Minecraft; fica sem item flutuante.");
                continue;
            }
            indice.put(normalizar(item.key()), material);
            indice.put(normalizar(catalog.displayName(item.key())), material);
        }
        return indice;
    }

    private static Material materialDaPlaca(Sign placa, Map<String, Material> indice) {
        String linha = texto(placa, 3);
        if (linha.isBlank()) {
            return null;
        }
        Material traduzido = indice.get(normalizar(linha));
        if (traduzido != null) {
            return traduzido;
        }
        // A placa pode ter sido escrita à mão, fora do catálogo.
        return Material.matchMaterial(linha.trim().replace(' ', '_'));
    }

    /**
     * Placas cuja primeira linha é o dono do Admin Shop.
     *
     * Mesma varredura por tile entity do {@link ShopSignWriter} — o custo
     * cresce com a área em chunks, não com o volume — mas o filtro é o
     * oposto: lá interessa a placa em branco, aqui a já escrita.
     */
    private List<Sign> placasDoAdminShop(Location centro, int limite) {
        World world = centro.getWorld();
        List<Sign> achadas = new ArrayList<>();
        if (world == null) {
            return achadas;
        }

        String dono = normalizar(plugin.economyCatalog().adminShopSignOwner());
        int chunkRaio = (limite >> 4) + 1;
        int cx = centro.getBlockX() >> 4;
        int cz = centro.getBlockZ() >> 4;

        for (int dx = -chunkRaio; dx <= chunkRaio; dx++) {
            for (int dz = -chunkRaio; dz <= chunkRaio; dz++) {
                if (!world.isChunkLoaded(cx + dx, cz + dz)) {
                    continue;
                }
                for (BlockState estado : world.getChunkAt(cx + dx, cz + dz).getTileEntities()) {
                    if (estado instanceof Sign placa
                            && dentroDoRaio(placa.getLocation(), centro, limite)
                            && normalizar(texto(placa, 0)).equals(dono)) {
                        achadas.add(placa);
                    }
                }
            }
        }
        return achadas;
    }

    private static boolean dentroDoRaio(Location alvo, Location centro, int raio) {
        return Math.abs(alvo.getBlockX() - centro.getBlockX()) <= raio
                && Math.abs(alvo.getBlockZ() - centro.getBlockZ()) <= raio
                && Math.abs(alvo.getBlockY() - centro.getBlockY()) <= raio;
    }

    private static String texto(Sign placa, int linha) {
        return PlainTextComponentSerializer.plainText()
                .serialize(placa.getSide(Side.FRONT).line(linha));
    }

    /**
     * Compara nome de placa ignorando acento, caixa e espaço sobrando.
     *
     * O nome vai do economy.yml para a placa e volta. No caminho passa pelo
     * cliente e por arquivos que podem ter sido salvos em outra codificação —
     * exigir igualdade byte a byte faria a vitrine falhar por um acento.
     */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    /**
     * O que foi (ou seria) pendurado.
     *
     * {@code semMaterial} e {@code semEspaco} são os dois jeitos de uma placa
     * ficar sem item, e separá-los importa: o primeiro é erro de catálogo, o
     * segundo é erro de construção, e o conserto de cada um é diferente.
     */
    public record Relatorio(int placas, int penduradas, int semMaterial,
                            int semEspaco, int removidas, int raio) {

        public int semItem() {
            return semMaterial + semEspaco;
        }
    }
}
