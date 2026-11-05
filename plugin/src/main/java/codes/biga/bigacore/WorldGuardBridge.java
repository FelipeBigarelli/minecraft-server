package codes.biga.bigacore;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.RemovalStrategy;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * A unica porta do BigaCore para o WorldGuard.
 *
 * Toda classe do com.sk89q.* mora aqui dentro. Quem chama trabalha com
 * {@link Caixa}, UUID e String — tipos do Java e do Bukkit. Isso vale por tres
 * motivos:
 *
 *  1. Se o WorldGuard sair do servidor, so esta classe deixa de carregar. O
 *     resto do plugin continua subindo, e {@link #disponivel()} desliga a
 *     venda de terreno em vez de derrubar o BigaCore.
 *  2. Quando a API do WorldGuard mudar, o conserto fica num arquivo.
 *  3. A conta de geometria fica testavel sem servidor, porque a Caixa nao
 *     conhece BlockVector3.
 *
 * O caminho inverso — escrever nosso proprio motor de protecao — foi recusado
 * de proposito. O servidor ja tem WorldGuard instalado, com as mensagens
 * traduzidas e com flags que o jogador ja conhece. Dois sistemas de protecao
 * lado a lado dariam duas respostas para "quem pode construir aqui", e a que
 * valesse seria a que rodasse primeiro no listener.
 */
public final class WorldGuardBridge {

    /** Prioridade das regioes de terreno. Acima de 0 para vencer regiao de mundo inteiro. */
    private static final int PRIORIDADE = 1;

    private final BigaCore plugin;
    private boolean confirmado;

    public WorldGuardBridge(BigaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Diz se da para mexer em regiao agora.
     *
     * So guarda o resultado quando e positivo. Guardar o negativo seria
     * economizar uma chamada e pagar com um bug de ordem de carga: se algo
     * perguntar antes do WorldGuard habilitar, a venda de terreno ficaria
     * desligada ate o proximo restart, sem nada no log explicando.
     */
    public boolean disponivel() {
        if (confirmado) {
            return true;
        }

        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) {
            return false;
        }

        try {
            if (WorldGuard.getInstance().getPlatform().getRegionContainer() == null) {
                return false;
            }
            confirmado = true;
            return true;
        } catch (Throwable erro) {
            // Throwable, nao Exception: WorldGuard ausente da NoClassDefFoundError,
            // que e Error. Catch de Exception deixaria passar e derrubaria o comando.
            plugin.getLogger().log(Level.WARNING,
                    "[terreno] WorldGuard presente mas a API nao respondeu — venda desligada.", erro);
            return false;
        }
    }

    private RegionManager gerente(World mundo) {
        if (!disponivel()) {
            return null;
        }
        return WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(mundo));
    }

    /**
     * Ids das regioes que encostam na caixa, tirando as que o chamador manda ignorar.
     *
     * O ignorar existe para o /terreno expandir: a caixa nova sempre colide com
     * a regiao antiga do proprio terreno, e barrar por causa disso tornaria
     * expandir impossivel.
     */
    public List<String> colisoes(World mundo, Caixa caixa, String ignorar) {
        RegionManager gerente = gerente(mundo);
        if (gerente == null) {
            return List.of();
        }

        ProtectedCuboidRegion sonda = new ProtectedCuboidRegion(
                "__bigacore_sonda__", minimo(caixa), maximo(caixa));

        ApplicableRegionSet encontradas = gerente.getApplicableRegions(sonda);
        List<String> ids = new ArrayList<>();
        for (ProtectedRegion regiao : encontradas) {
            if (ignorar != null && regiao.getId().equalsIgnoreCase(ignorar)) {
                continue;
            }
            ids.add(regiao.getId());
        }
        return ids;
    }

    /** Cria a regiao com o jogador como dono. Devolve false se o id ja existir. */
    public boolean criar(World mundo, String id, Caixa caixa, UUID dono,
                         String aviso, String despedida) {

        RegionManager gerente = gerente(mundo);
        if (gerente == null || gerente.hasRegion(id)) {
            return false;
        }

        ProtectedCuboidRegion regiao =
                new ProtectedCuboidRegion(id, minimo(caixa), maximo(caixa));
        regiao.getOwners().addPlayer(dono);
        regiao.setPriority(PRIORIDADE);

        if (aviso != null && !aviso.isBlank()) {
            regiao.setFlag(Flags.GREET_MESSAGE, aviso);
        }
        if (despedida != null && !despedida.isBlank()) {
            regiao.setFlag(Flags.FAREWELL_MESSAGE, despedida);
        }

        gerente.addRegion(regiao);
        return true;
    }

    /**
     * Troca o tamanho de um terreno mantendo dono, membros, flags e prioridade.
     *
     * Nao usa setMinimumPoint/setMaximumPoint apesar de existirem. Mexer nos
     * pontos de uma regiao que ja esta no gerente deixa o indice espacial
     * apontando para a area velha: a regiao protege um lugar e aparece em
     * outro. O proprio /rg redefine do WorldGuard faz do jeito que esta aqui —
     * monta uma regiao nova, copia o conteudo com copyFrom e regrava pelo id,
     * o que reindexa.
     *
     * copyFrom nao traz a geometria, so dono, membros, flags, prioridade e
     * pai. E exatamente o que se quer: a geometria e o que esta mudando.
     */
    public boolean redimensionar(World mundo, String id, Caixa nova) {
        RegionManager gerente = gerente(mundo);
        if (gerente == null) {
            return false;
        }

        ProtectedRegion antiga = gerente.getRegion(id);
        if (antiga == null) {
            return false;
        }

        ProtectedCuboidRegion trocada =
                new ProtectedCuboidRegion(id, minimo(nova), maximo(nova));
        trocada.copyFrom(antiga);
        gerente.addRegion(trocada);
        return true;
    }

    public boolean apagar(World mundo, String id) {
        RegionManager gerente = gerente(mundo);
        if (gerente == null) {
            return false;
        }
        // UNSET_PARENT_IN_CHILDREN e o padrao do /rg remove: nenhum terreno tem
        // filho hoje, mas apagar um pai com REMOVE_CHILDREN levaria regioes de
        // outra pessoa junto se alguem usar heranca mais tarde.
        Set<ProtectedRegion> removidas =
                gerente.removeRegion(id, RemovalStrategy.UNSET_PARENT_IN_CHILDREN);
        return removidas != null && !removidas.isEmpty();
    }

    public Optional<Terreno> porId(World mundo, String id) {
        RegionManager gerente = gerente(mundo);
        if (gerente == null) {
            return Optional.empty();
        }
        ProtectedRegion regiao = gerente.getRegion(id);
        return regiao == null ? Optional.empty() : Optional.of(converter(regiao));
    }

    /**
     * Terrenos nossos em que o jogador e dono, ordenados pelo id.
     *
     * O filtro por prefixo existe para o limite por jogador contar so terreno
     * comprado. Sem ele, dar a alguem a posse de uma regiao administrativa
     * (um evento, uma area de staff) consumiria a cota de terreno dele.
     */
    public List<Terreno> terrenosDe(World mundo, UUID dono, String prefixo) {
        RegionManager gerente = gerente(mundo);
        if (gerente == null) {
            return List.of();
        }

        String marca = (prefixo + "_").toLowerCase(Locale.ROOT);
        List<Terreno> achados = new ArrayList<>();
        for (ProtectedRegion regiao : gerente.getRegions().values()) {
            if (!regiao.getId().toLowerCase(Locale.ROOT).startsWith(marca)) {
                continue;
            }
            if (!regiao.getOwners().contains(dono)) {
                continue;
            }
            achados.add(converter(regiao));
        }
        achados.sort(Comparator.comparing(Terreno::id));
        return achados;
    }

    /**
     * O terreno no ponto. Quando ha mais de um, devolve o de dentro.
     *
     * "O de dentro" e o de menor area: terrenos nao se sobrepoem pelas regras
     * da venda, mas uma regiao administrativa maior pode envolver um terreno,
     * e nesse caso quem manda na pergunta "de quem e este chao" e o menor.
     */
    public Optional<Terreno> em(World mundo, int x, int y, int z, String prefixo) {
        RegionManager gerente = gerente(mundo);
        if (gerente == null) {
            return Optional.empty();
        }

        String marca = (prefixo + "_").toLowerCase(Locale.ROOT);
        ProtectedRegion menor = null;
        for (ProtectedRegion regiao : gerente.getApplicableRegions(BlockVector3.at(x, y, z))) {
            if (!regiao.getId().toLowerCase(Locale.ROOT).startsWith(marca)) {
                continue;
            }
            if (menor == null || regiao.volume() < menor.volume()) {
                menor = regiao;
            }
        }
        return menor == null ? Optional.empty() : Optional.of(converter(menor));
    }

    /** Adiciona ou tira um membro (quem pode construir sem ser dono). */
    public boolean membro(World mundo, String id, UUID jogador, boolean adicionar) {
        RegionManager gerente = gerente(mundo);
        if (gerente == null) {
            return false;
        }

        ProtectedRegion regiao = gerente.getRegion(id);
        if (regiao == null) {
            return false;
        }

        if (adicionar) {
            regiao.getMembers().addPlayer(jogador);
        } else {
            regiao.getMembers().removePlayer(jogador);
        }
        return true;
    }

    /**
     * Grava as regioes no disco, fora da thread principal.
     *
     * saveChanges() escreve arquivo. Chamar na thread do servidor para cada
     * compra significa travar o tique inteiro esperando o disco — o proprio
     * WorldGuard grava assincrono pelo mesmo motivo.
     *
     * Falha de gravacao vai para o log em nivel SEVERE e nao e silenciada: o
     * terreno ja esta valendo em memoria, entao o sintoma de um erro aqui e
     * especifico e cruel — o jogador paga, constroi, e perde a protecao no
     * proximo restart.
     */
    public void salvarDepois(World mundo) {
        RegionManager gerente = gerente(mundo);
        if (gerente == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                gerente.saveChanges();
            } catch (Exception erro) {
                plugin.getLogger().log(Level.SEVERE,
                        "[terreno] Falha ao gravar as regioes do mundo "
                                + mundo.getName() + ". O terreno vale em memoria mas "
                                + "pode nao sobreviver ao restart.", erro);
            }
        });
    }

    private static Terreno converter(ProtectedRegion regiao) {
        BlockVector3 min = regiao.getMinimumPoint();
        BlockVector3 max = regiao.getMaximumPoint();
        return new Terreno(
                regiao.getId(),
                new Caixa(min.x(), min.y(), min.z(), max.x(), max.y(), max.z()),
                Set.copyOf(regiao.getOwners().getUniqueIds()),
                Set.copyOf(regiao.getMembers().getUniqueIds()));
    }

    private static BlockVector3 minimo(Caixa caixa) {
        return BlockVector3.at(caixa.minX(), caixa.minY(), caixa.minZ());
    }

    private static BlockVector3 maximo(Caixa caixa) {
        return BlockVector3.at(caixa.maxX(), caixa.maxY(), caixa.maxZ());
    }
}
