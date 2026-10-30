package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Preenche placas EM BRANCO de uma construção pronta com o balcão do servidor.
 *
 * <h2>Por que existe</h2>
 *
 * O {@link SpawnShopBuilder} resolve o caso em que o BigaCore é o arquiteto:
 * ele levanta a loja e já sabe onde cada barril e cada placa vão. Mas em
 * 30/10/2026 a decisão foi usar uma construção baixada e colada com WorldEdit
 * — 42x34, com 92 placas de carvalho já posicionadas e todas vazias.
 *
 * Ali o BigaCore não é o arquiteto, é o lojista: a construção já existe e o que
 * falta é escrever o preço. Esta classe faz só isso.
 *
 * <h2>Só mexe no que está em branco</h2>
 *
 * A varredura ignora qualquer placa que já tenha texto. Isso é o que torna o
 * comando seguro de rodar duas vezes e o que impede de apagar a decoração da
 * construção — placa de nome de rua, de boas-vindas, de aviso. Sem essa regra,
 * um raio generoso demais reescreveria metade do cenário.
 *
 * <h2>Admin Shop não precisa de baú</h2>
 *
 * Com {@code FORCE_UNLIMITED_ADMIN_SHOP: true} no ChestShop, a placa cujo dono
 * é o {@code ADMIN_SHOP_NAME} tem estoque infinito e dispensa container. Por
 * isso a construção baixada servir sem nenhum barril não é problema: as 92
 * placas soltas são exatamente o formato certo.
 */
public final class ShopSignWriter {

    /**
     * Teto do raio aceito.
     *
     * A varredura é por tile entity de chunk, não bloco a bloco, então o custo
     * cresce com a ÁREA em chunks e não com o volume. Ainda assim 64 já cobre
     * uma construção de 128x128 — bem mais que qualquer terreno — e passar
     * disso só aumentaria a chance de pegar placa de outra construção.
     */
    public static final int RAIO_MAXIMO = 64;

    public static final int RAIO_PADRAO = 32;

    private final BigaCore plugin;

    public ShopSignWriter(BigaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Lista o que seria feito, sem tocar em nada.
     *
     * Separado de {@link #escrever} de propósito: 92 placas escritas por engano
     * dão um trabalho enorme de desfazer na mão, e não existe snapshot aqui
     * como existe para a construção.
     */
    public Relatorio simular(Location centro, int raio) {
        List<Sign> vazias = placasEmBranco(centro, raio);
        List<EconomyCatalog.PriceEntry> vitrine =
                plugin.economyCatalog().adminShopWall(vazias.size());
        return new Relatorio(vazias.size(), vitrine.size(), itensPedidos(), raio);
    }

    /** Escreve de fato. Devolve o mesmo relatório da simulação. */
    public Relatorio escrever(Location centro, int raio) {
        List<Sign> vazias = placasEmBranco(centro, raio);
        List<EconomyCatalog.PriceEntry> vitrine =
                plugin.economyCatalog().adminShopWall(vazias.size());

        String dono = plugin.economyCatalog().adminShopSignOwner();
        int escritas = 0;

        for (int i = 0; i < vitrine.size() && i < vazias.size(); i++) {
            if (escreverUma(vazias.get(i), vitrine.get(i), dono)) {
                escritas++;
            }
        }

        plugin.getLogger().info("[admin-shop] " + escritas + " placa(s) escritas de "
                + vazias.size() + " em branco encontradas.");

        return new Relatorio(vazias.size(), escritas, itensPedidos(), raio);
    }

    // ------------------------------------------------------------------

    private boolean escreverUma(Sign placa, EconomyCatalog.PriceEntry item, String dono) {
        List<String> linhas = AdminShopSign.linhas(plugin, item, dono);
        SignSide frente = placa.getSide(Side.FRONT);
        for (int n = 0; n < linhas.size(); n++) {
            frente.line(n, Component.text(linhas.get(n)));
        }
        // update(force=true) para o ChestShop registrar a loja mesmo sem um
        // jogador ter digitado a placa. applyPhysics=false: mexer em física
        // numa construção recém-colada derruba tocha e placa vizinhas.
        return placa.update(true, false);
    }

    /**
     * Placas vazias dentro do raio, em ordem estável.
     *
     * A ordem (x, z, y) é arbitrária mas DETERMINÍSTICA: rodar o comando duas
     * vezes no mesmo lugar distribui os itens igual. Ordenar pela ordem em que
     * o servidor devolve os chunks daria uma loja embaralhada a cada vez.
     */
    private List<Sign> placasEmBranco(Location centro, int raio) {
        World mundo = centro.getWorld();
        List<Sign> achadas = new ArrayList<>();
        if (mundo == null) {
            return achadas;
        }

        int limite = Math.max(1, Math.min(raio, RAIO_MAXIMO));
        int chunkRaio = (limite >> 4) + 1;
        int cx = centro.getBlockX() >> 4;
        int cz = centro.getBlockZ() >> 4;

        for (int dx = -chunkRaio; dx <= chunkRaio; dx++) {
            for (int dz = -chunkRaio; dz <= chunkRaio; dz++) {
                if (!mundo.isChunkLoaded(cx + dx, cz + dz)) {
                    continue;
                }
                Chunk chunk = mundo.getChunkAt(cx + dx, cz + dz);
                for (BlockState estado : chunk.getTileEntities()) {
                    if (estado instanceof Sign placa
                            && dentroDoRaio(placa.getLocation(), centro, limite)
                            && estaEmBranco(placa)) {
                        achadas.add(placa);
                    }
                }
            }
        }

        achadas.sort(Comparator
                .comparingInt((Sign s) -> s.getLocation().getBlockX())
                .thenComparingInt(s -> s.getLocation().getBlockZ())
                .thenComparingInt(s -> s.getLocation().getBlockY()));
        return achadas;
    }

    private static boolean dentroDoRaio(Location placa, Location centro, int raio) {
        return Math.abs(placa.getBlockX() - centro.getBlockX()) <= raio
                && Math.abs(placa.getBlockZ() - centro.getBlockZ()) <= raio
                && Math.abs(placa.getBlockY() - centro.getBlockY()) <= raio;
    }

    /** Em branco é frente E verso vazios: placa escrita atrás também é decoração. */
    private static boolean estaEmBranco(Sign placa) {
        return ladoVazio(placa.getSide(Side.FRONT)) && ladoVazio(placa.getSide(Side.BACK));
    }

    private static boolean ladoVazio(SignSide lado) {
        for (Component linha : lado.lines()) {
            if (!PlainTextComponentSerializer.plainText().serialize(linha).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private int itensPedidos() {
        return plugin.economyCatalog().adminShopWallSize();
    }

    /**
     * O que foi (ou seria) feito.
     *
     * {@code sobrando} e {@code semVaga} contam os dois desencontros possíveis
     * entre construção e catálogo, e os dois importam: placa vazia sobrando é
     * loja com buraco, item sem vaga é produto que o servidor deixou de vender
     * sem avisar ninguém.
     */
    public record Relatorio(int placasEmBranco, int itensColocados, int itensPedidos, int raio) {

        public int sobrando() {
            return Math.max(0, placasEmBranco - itensColocados);
        }

        public int semVaga() {
            return Math.max(0, itensPedidos - itensColocados);
        }
    }
}
