package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guarda o inventário de quem entra num minigame e devolve ao sair.
 *
 * <h2>Por que isto existe antes de o primeiro jogo precisar de item</h2>
 *
 * Porque o perigo não é o jogo que dá itens — é o jogo que NÃO dá. Sem isto,
 * nada impede alguém de levar arco, poção e blocos para dentro do TNT Run e
 * ganhar construindo. Então a regra passa a ser uma só, igual nos vinte
 * jogos: ao entrar no mundo da arena o inventário é guardado e esvaziado; ao
 * sair, volta inteiro.
 *
 * <h2>Guardado em disco, e não só na memória</h2>
 *
 * Um mapa em memória resolve o caso normal e perde tudo no caso que importa:
 * servidor caiu com gente dentro da arena. O jogador volta com o inventário
 * vazio e a culpa é do servidor — e num servidor de sobrevivência isso é
 * perder o que custou horas. Por isso cada guarda escreve o {@code
 * mochilas.yml} na hora, e a devolução só apaga depois de devolver.
 *
 * <h2>Guardar nunca sobrescreve</h2>
 *
 * Se já existe mochila guardada, guardar de novo não faz nada. É o caso do
 * jogador que estava na arena quando o servidor caiu: ao voltar, ele entra no
 * mundo da arena com o inventário vazio, e um guardar ingênuo trocaria a
 * mochila cheia pela vazia. Mesma ideia do {@code putIfAbsent} do modo de
 * jogo anterior.
 */
public final class Mochila implements Listener {

    private static final String ARQUIVO = "mochilas.yml";

    private final BigaCore plugin;
    private final Map<UUID, ItemStack[]> guardadas = new HashMap<>();
    private final Map<UUID, Integer> maoDe = new HashMap<>();
    private final File arquivo;

    public Mochila(BigaCore plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), ARQUIVO);
        carregar();
    }

    // ------------------------------------------------------------------
    //  Guardar e devolver
    // ------------------------------------------------------------------

    /**
     * Guarda o que o jogador carrega e esvazia a mochila dele.
     *
     * @return false se já havia mochila guardada — e aí nada foi tocado além
     *         de esvaziar, porque o que está guardado é o que vale.
     */
    public boolean guardar(Player jogador) {
        UUID id = jogador.getUniqueId();
        boolean nova = !guardadas.containsKey(id);
        if (nova) {
            guardadas.put(id, jogador.getInventory().getContents().clone());
            maoDe.put(id, jogador.getInventory().getHeldItemSlot());
            salvar();
        }
        jogador.getInventory().clear();
        jogador.getInventory().setHeldItemSlot(0);
        return nova;
    }

    /** Devolve o que estava guardado. Sem mochila guardada, não faz nada. */
    public boolean devolver(Player jogador) {
        UUID id = jogador.getUniqueId();
        ItemStack[] conteudo = guardadas.get(id);
        if (conteudo == null) {
            return false;
        }
        jogador.getInventory().clear();
        jogador.getInventory().setContents(ajustar(conteudo,
                jogador.getInventory().getSize()));
        Integer mao = maoDe.get(id);
        if (mao != null && mao >= 0 && mao < 9) {
            jogador.getInventory().setHeldItemSlot(mao);
        }
        guardadas.remove(id);
        maoDe.remove(id);
        salvar();
        return true;
    }

    /**
     * Devolve a mochila de quem entrou no servidor fora de arena nenhuma.
     *
     * É o caso do servidor que caiu com gente jogando: o jogador volta no
     * lobby, sem passar por saída de mundo nenhuma, e a mochila dele está
     * esperando no arquivo.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void aoEntrarNoServidor(PlayerJoinEvent evento) {
        Player jogador = evento.getPlayer();
        if (!guardadas.containsKey(jogador.getUniqueId())) {
            return;
        }
        if (plugin.ehMundoDeMinigame(jogador.getWorld().getName())) {
            return;
        }
        devolver(jogador);
        jogador.sendMessage(plugin.mini().deserialize(
                "<gray>devolvemos o que você estava carregando antes do "
                        + "minigame</gray>"));
    }

    /** Devolve tudo o que está guardado. Roda no desligamento do plugin. */
    public int devolverTodos() {
        int devolvidas = 0;
        for (UUID id : new ArrayList<>(guardadas.keySet())) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador != null && devolver(jogador)) {
                devolvidas++;
            }
        }
        return devolvidas;
    }

    public int quantasGuardadas() {
        return guardadas.size();
    }

    // ------------------------------------------------------------------
    //  Disco
    // ------------------------------------------------------------------

    private void carregar() {
        if (!arquivo.exists()) {
            return;
        }
        YamlConfiguration dados = YamlConfiguration.loadConfiguration(arquivo);
        if (dados.getConfigurationSection("mochilas") == null) {
            return;
        }
        for (String chave : dados.getConfigurationSection("mochilas").getKeys(false)) {
            try {
                UUID id = UUID.fromString(chave);
                List<?> lista = dados.getList("mochilas." + chave + ".conteudo");
                if (lista == null) {
                    continue;
                }
                ItemStack[] conteudo = new ItemStack[lista.size()];
                for (int i = 0; i < lista.size(); i++) {
                    conteudo[i] = lista.get(i) instanceof ItemStack item ? item : null;
                }
                guardadas.put(id, conteudo);
                maoDe.put(id, dados.getInt("mochilas." + chave + ".mao", 0));
            } catch (IllegalArgumentException erro) {
                plugin.getLogger().warning("[mochila] chave inválida no "
                        + ARQUIVO + ": " + chave);
            }
        }
        if (!guardadas.isEmpty()) {
            plugin.getLogger().info("[mochila] " + guardadas.size()
                    + " mochila(s) esperando devolução.");
        }
    }

    private void salvar() {
        YamlConfiguration dados = new YamlConfiguration();
        for (Map.Entry<UUID, ItemStack[]> mochila : guardadas.entrySet()) {
            String base = "mochilas." + mochila.getKey() + ".";
            dados.set(base + "conteudo", Arrays.asList(mochila.getValue()));
            dados.set(base + "mao", maoDe.getOrDefault(mochila.getKey(), 0));
        }
        try {
            dados.save(arquivo);
        } catch (IOException erro) {
            plugin.getLogger().severe("[mochila] não deu para salvar o " + ARQUIVO
                    + ": " + erro.getMessage() + " — as mochilas guardadas só "
                    + "existem na memória até o próximo salvamento.");
        }
    }

    /**
     * Encaixa o conteúdo guardado no inventário de hoje.
     *
     * Uma versão do jogo pode mudar quantos espaços um inventário tem. Sem
     * este ajuste, {@code setContents} estoura e o jogador fica sem nada —
     * que é justamente o acidente que esta classe existe para evitar.
     */
    static ItemStack[] ajustar(ItemStack[] conteudo, int tamanho) {
        if (conteudo.length == tamanho) {
            return conteudo;
        }
        ItemStack[] saida = new ItemStack[tamanho];
        System.arraycopy(conteudo, 0, saida, 0, Math.min(conteudo.length, tamanho));
        return saida;
    }
}
