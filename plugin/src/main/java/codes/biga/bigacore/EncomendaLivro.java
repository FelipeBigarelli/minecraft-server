package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * O registro dos pedidos abertos e das entregas a retirar.
 *
 * Grava em encomendas-abertas.yml, que e DADO (nasce vazio, muda a cada
 * pedido) e por isso nao e o mesmo arquivo do encomendas.yml, que e REGRA
 * (vem no jar, editado a mao). Confundir os dois faria um update do plugin
 * sobrescrever pedido de jogador.
 *
 * Cada gravacao e imediata. Pedido guarda dinheiro de gente: perder a lista
 * num desligamento sujo significaria dinheiro sacado e encomenda nenhuma.
 */
public final class EncomendaLivro {

    static final String ARQUIVO = "encomendas-abertas.yml";

    private final BigaCore plugin;
    private YamlConfiguration dados;
    private final Map<Integer, Encomenda> abertas = new LinkedHashMap<>();
    private int proximoId = 1;

    public EncomendaLivro(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    private File arquivo() {
        return new File(plugin.getDataFolder(), ARQUIVO);
    }

    public void reload() {
        this.dados = YamlConfiguration.loadConfiguration(arquivo());
        this.abertas.clear();
        this.proximoId = Math.max(1, dados.getInt("proximo-id", 1));

        ConfigurationSection secao = dados.getConfigurationSection("abertas");
        if (secao == null) {
            return;
        }

        for (String chave : secao.getKeys(false)) {
            ConfigurationSection linha = secao.getConfigurationSection(chave);
            if (linha == null) {
                continue;
            }

            Encomenda lida = ler(chave, linha);
            if (lida == null) {
                continue;
            }
            abertas.put(lida.id(), lida);
            proximoId = Math.max(proximoId, lida.id() + 1);
        }
    }

    /**
     * Converte uma linha do YAML em {@link Encomenda}.
     *
     * Devolve null — e avisa no console — em vez de lancar, porque uma linha
     * corrompida nao pode impedir o resto do quadro de carregar. O item e
     * resolvido pelo nome: se uma versao futura do Minecraft aposentar o
     * material, o pedido some do quadro mas o aviso diz qual era.
     */
    private Encomenda ler(String chave, ConfigurationSection linha) {
        try {
            int id = Integer.parseInt(chave);
            String nomeItem = linha.getString("item", "");
            Material item = nomeItem == null ? null : Material.matchMaterial(nomeItem);
            if (item == null) {
                plugin.getLogger().warning("[encomenda] Pedido " + chave
                        + " ignorado: item desconhecido '" + nomeItem + "'.");
                return null;
            }

            String uuid = linha.getString("dono", "");
            if (uuid == null || uuid.isBlank()) {
                return null;
            }

            return new Encomenda(
                    id,
                    UUID.fromString(uuid),
                    linha.getString("dono-nome", "?"),
                    item,
                    linha.getInt("quantidade"),
                    linha.getLong("preco"),
                    linha.getLong("criada-em"));
        } catch (IllegalArgumentException erro) {
            plugin.getLogger().warning("[encomenda] Pedido " + chave
                    + " ignorado: " + erro.getMessage());
            return null;
        }
    }

    public List<Encomenda> abertas() {
        return new ArrayList<>(abertas.values());
    }

    public Optional<Encomenda> buscar(int id) {
        return Optional.ofNullable(abertas.get(id));
    }

    public int abertasDe(UUID jogador) {
        int total = 0;
        for (Encomenda pedido : abertas.values()) {
            if (pedido.ehDono(jogador)) {
                total++;
            }
        }
        return total;
    }

    public List<Encomenda> de(UUID jogador) {
        List<Encomenda> lista = new ArrayList<>();
        for (Encomenda pedido : abertas.values()) {
            if (pedido.ehDono(jogador)) {
                lista.add(pedido);
            }
        }
        return lista;
    }

    /**
     * Cria o pedido ja pago.
     *
     * Devolve null se a gravacao falhar, e NAO deixa o pedido na memoria nesse
     * caso. Quem chama tem de devolver o dinheiro: a alternativa seria um
     * pedido que existe nesta sessao e desaparece no proximo boot, com o saque
     * ja feito.
     */
    public Encomenda criar(UUID dono, String donoNome, Material item,
                           int quantidade, long preco) {

        int id = proximoId;
        Encomenda pedido = new Encomenda(
                id, dono, donoNome, item, quantidade, preco, System.currentTimeMillis());

        dados.set("abertas." + id + ".dono", dono.toString());
        dados.set("abertas." + id + ".dono-nome", donoNome);
        dados.set("abertas." + id + ".item", item.name());
        dados.set("abertas." + id + ".quantidade", quantidade);
        dados.set("abertas." + id + ".preco", preco);
        dados.set("abertas." + id + ".criada-em", pedido.criadaEm());
        dados.set("proximo-id", id + 1);

        if (!salvar()) {
            dados.set("abertas." + id, null);
            dados.set("proximo-id", id);
            return null;
        }

        abertas.put(id, pedido);
        proximoId = id + 1;
        return pedido;
    }

    /** Tira o pedido do quadro. Devolve false se a gravacao falhar. */
    public boolean remover(int id) {
        Encomenda guardado = abertas.get(id);
        if (guardado == null) {
            return false;
        }

        dados.set("abertas." + id, null);
        if (!salvar()) {
            return false;
        }
        abertas.remove(id);
        return true;
    }

    // ------------------------------------------------------------------
    // Caixa de entrega
    //
    // O item entregue nao vai direto para a mochila de quem pediu: na hora da
    // entrega essa pessoa costuma estar offline, e mesmo online pode estar com
    // o inventario cheio. Fica guardado aqui ate /encomenda retirar.
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<ItemStack> entregasDe(UUID dono) {
        List<ItemStack> lista = new ArrayList<>();
        List<?> bruto = dados.getList("entregas." + dono, List.of());
        if (bruto == null) {
            return lista;
        }
        for (Object elemento : bruto) {
            if (elemento instanceof ItemStack pilha) {
                lista.add(pilha);
            }
        }
        return lista;
    }

    public boolean guardarEntrega(UUID dono, List<ItemStack> novos) {
        List<ItemStack> atual = entregasDe(dono);
        atual.addAll(novos);
        return definirEntregas(dono, atual);
    }

    public boolean definirEntregas(UUID dono, List<ItemStack> pilhas) {
        if (pilhas.isEmpty()) {
            dados.set("entregas." + dono, null);
        } else {
            dados.set("entregas." + dono, pilhas);
        }
        return salvar();
    }

    private boolean salvar() {
        try {
            File destino = arquivo();
            File pasta = destino.getParentFile();
            if (pasta != null && !pasta.isDirectory() && !pasta.mkdirs()) {
                plugin.getLogger().severe("[encomenda] Nao foi possivel criar a pasta de "
                        + ARQUIVO + ".");
                return false;
            }
            dados.save(destino);
            return true;
        } catch (IOException erro) {
            plugin.getLogger().severe("[encomenda] Falha ao gravar " + ARQUIVO
                    + ": " + erro.getMessage());
            return false;
        }
    }
}
