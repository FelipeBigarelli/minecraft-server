package codes.biga.bigacore;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/**
 * /pedreiro — estuda uma construção existente e refaz em outro lugar.
 *
 * Não há IA envolvida: copiar uma construção é leitura de região, operação
 * determinística. O que o comando acrescenta sobre um //copy + //paste é a
 * obra subindo aos poucos e o desfazer automático.
 */
public final class PedreiroCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMANDOS = Arrays.asList(
            "p1", "p2", "estudar", "obras", "construir", "parar", "desfazer", "amostra", "organizar");

    private final BigaCore plugin;

    public PedreiroCommand(BigaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender remetente, @NotNull Command comando,
                             @NotNull String rotulo, String[] args) {
        if (!(remetente instanceof Player jogador)) {
            remetente.sendMessage("Só jogador pode usar /pedreiro.");
            return true;
        }
        if (args.length == 0) {
            ajuda(jogador);
            return true;
        }

        Pedreiro pedreiro = plugin.pedreiro();
        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "p1", "p2" -> {
                int qual = sub.equals("p1") ? 1 : 2;
                pedreiro.marcar(jogador, qual);
                Location onde = pedreiro.canto(jogador, qual);
                diz(jogador, "<gray>Canto <white>" + qual + "</white> em "
                        + onde.getBlockX() + " " + onde.getBlockY() + " " + onde.getBlockZ()
                        + "</gray>");
            }
            case "estudar" -> estudar(jogador, pedreiro, args);
            case "amostra" -> amostra(jogador, pedreiro, args);
            case "organizar" -> organizar(jogador, pedreiro, args);
            case "obras" -> {
                List<String> nomes = pedreiro.listar();
                if (nomes.isEmpty()) {
                    diz(jogador, "<gray>Nenhuma planta estudada ainda.</gray>");
                } else {
                    diz(jogador, "<aqua>Plantas:</aqua> <white>" + String.join(", ", nomes) + "</white>");
                }
            }
            case "construir" -> construir(jogador, pedreiro, args);
            case "parar" -> diz(jogador, pedreiro.parar(jogador)
                    ? "<yellow>Obra interrompida. Use /pedreiro desfazer para limpar.</yellow>"
                    : "<gray>Você não tem obra em andamento.</gray>");
            case "desfazer" -> diz(jogador, pedreiro.desfazer(jogador)
                    ? "<green>Terreno devolvido ao estado anterior.</green>"
                    : "<gray>Nada para desfazer.</gray>");
            default -> ajuda(jogador);
        }
        return true;
    }

    private void estudar(Player jogador, Pedreiro pedreiro, String[] args) {
        if (args.length < 2) {
            diz(jogador, "<red>Falta o nome: /pedreiro estudar <nome></red>");
            return;
        }
        Location a = pedreiro.canto(jogador, 1);
        Location b = pedreiro.canto(jogador, 2);
        if (a == null || b == null) {
            diz(jogador, "<red>Marque os dois cantos antes (/pedreiro p1 e /pedreiro p2).</red>");
            return;
        }
        try {
            Planta planta = Planta.estudar(args[1].toLowerCase(Locale.ROOT), a, b);
            pedreiro.guardar(planta);
            diz(jogador, "<green>Estudado <white>" + planta.nome() + "</white> — "
                    + planta.largura() + "x" + planta.altura() + "x" + planta.comprimento()
                    + " (" + planta.total() + " blocos).</green>");
        } catch (IllegalArgumentException erro) {
            diz(jogador, "<red>" + erro.getMessage() + "</red>");
        } catch (IOException erro) {
            diz(jogador, "<red>Não consegui salvar a planta: " + erro.getMessage() + "</red>");
            plugin.getLogger().warning("Falha ao salvar planta: " + erro);
        }
    }

    /**
     * Entrega 1 de cada item de um baú, sem tirar nada de dentro dele.
     *
     * Sem argumento olha o baú mirado; com "tudo" varre todos os containers
     * entre os cantos p1 e p2, que é o caso de montar o filtro de um sistema
     * de organização inteiro de uma vez.
     */
    private void amostra(Player jogador, Pedreiro pedreiro, String[] args) {
        boolean regiao = args.length >= 2 && args[1].equalsIgnoreCase("tudo");
        List<ItemStack> itens = new ArrayList<>();
        int recipientes = 0;

        if (regiao) {
            Location a = pedreiro.canto(jogador, 1);
            Location b = pedreiro.canto(jogador, 2);
            if (a == null || b == null) {
                diz(jogador, "<red>Marque os dois cantos antes (/pedreiro p1 e /pedreiro p2).</red>");
                return;
            }
            List<Container> containers;
            try {
                containers = Amostra.naRegiao(a.getWorld(), a, b);
            } catch (IllegalArgumentException erro) {
                diz(jogador, "<red>" + erro.getMessage() + "</red>");
                return;
            }
            for (Container recipiente : containers) {
                Amostra.coletar(recipiente.getInventory(), itens);
            }
            recipientes = containers.size();
        } else {
            Container recipiente = Amostra.mirado(jogador);
            if (recipiente == null) {
                diz(jogador, "<red>Mire num baú, barril ou funil (até "
                        + Amostra.ALCANCE + " blocos). Use <white>/pedreiro amostra tudo</white>"
                        + " para varrer a região marcada.</red>");
                return;
            }
            Amostra.coletar(recipiente.getInventory(), itens);
            recipientes = 1;
        }

        if (itens.isEmpty()) {
            diz(jogador, "<gray>Nada dentro — nenhum item para amostrar.</gray>");
            return;
        }

        int caiuNoChao = Amostra.entregar(jogador, itens);
        diz(jogador, "<green>" + itens.size() + " item(ns) diferente(s) de "
                + recipientes + " recipiente(s).</green> <gray>O conteúdo original não foi tocado.</gray>");
        if (caiuNoChao > 0) {
            diz(jogador, "<yellow>Inventário cheio: " + caiuNoChao
                    + " caíram no chão aos seus pés.</yellow>");
        }
        if (itens.size() >= Amostra.LIMITE) {
            diz(jogador, "<yellow>Parei no teto de " + Amostra.LIMITE + " itens distintos.</yellow>");
        }
    }

    /**
     * Junta as pilhas soltas de um baú e põe o conteúdo em ordem.
     *
     * Sem argumento organiza o baú mirado; com "tudo" varre todos os
     * containers entre p1 e p2, que é o caso da sala de armazenamento inteira.
     * O alvo é argumento e não comando próprio justamente para caber alvo novo
     * depois sem mexer na gramática.
     *
     * Recusa recipiente com outra pessoa olhando dentro: reescrever o
     * inventário por baixo de quem está com ele aberto deixa o cliente dela
     * vendo o estado antigo, e o clique seguinte cai no slot errado.
     */
    private void organizar(Player jogador, Pedreiro pedreiro, String[] args) {
        String alvo = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "bau";
        List<Container> recipientes = new ArrayList<>();

        switch (alvo) {
            case "bau" -> {
                Container mirado = Amostra.mirado(jogador);
                if (mirado == null) {
                    diz(jogador, "<red>Mire num baú, barril ou funil (até " + Amostra.ALCANCE
                            + " blocos). Use <white>/pedreiro organizar tudo</white>"
                            + " para varrer a região marcada.</red>");
                    return;
                }
                recipientes.add(mirado);
            }
            case "tudo" -> {
                Location a = pedreiro.canto(jogador, 1);
                Location b = pedreiro.canto(jogador, 2);
                if (a == null || b == null) {
                    diz(jogador, "<red>Marque os dois cantos antes (/pedreiro p1 e /pedreiro p2).</red>");
                    return;
                }
                try {
                    recipientes.addAll(Amostra.naRegiao(a.getWorld(), a, b));
                } catch (IllegalArgumentException erro) {
                    diz(jogador, "<red>" + erro.getMessage() + "</red>");
                    return;
                }
                if (recipientes.size() > Organizador.LIMITE_RECIPIENTES) {
                    diz(jogador, "<red>" + recipientes.size() + " recipientes na região (máximo "
                            + Organizador.LIMITE_RECIPIENTES + "). Marque uma área menor.</red>");
                    return;
                }
            }
            default -> {
                diz(jogador, "<red>Alvo desconhecido '" + alvo
                        + "'. Vale <white>bau</white> ou <white>tudo</white>.</red>");
                return;
            }
        }

        // Baú duplo é dois blocos que devolvem o MESMO inventário de 54 slots.
        // Sem a dedupe o segundo passe organizaria de novo e contaria em dobro.
        Set<Location> jaVistos = new HashSet<>();
        int organizados = 0;
        int liberados = 0;
        int itens = 0;
        int ocupados = 0;
        int comGente = 0;
        int falhas = 0;

        for (Container recipiente : recipientes) {
            Inventory inventario = recipiente.getInventory();
            Location onde = inventario.getLocation();
            if (onde == null) {
                onde = recipiente.getLocation();
            }
            if (!jaVistos.add(onde.toBlockLocation())) {
                continue;
            }
            if (outroOlhando(inventario, jogador)) {
                comGente++;
                continue;
            }
            try {
                Organizador.Resultado resultado = Organizador.aplicar(inventario);
                organizados++;
                liberados += resultado.liberados();
                ocupados += resultado.slotsDepois();
                itens += resultado.itens();
            } catch (IllegalStateException erro) {
                falhas++;
                plugin.getLogger().warning("Não organizei o recipiente em "
                        + onde.getBlockX() + " " + onde.getBlockY() + " " + onde.getBlockZ()
                        + ": " + erro.getMessage());
            }
        }

        jogador.updateInventory();

        if (organizados == 0) {
            diz(jogador, falhas > 0
                    ? "<red>Nada organizado — a conferência de itens falhou. Veja o console.</red>"
                    : "<gray>Nada para organizar.</gray>");
        } else if (liberados == 0) {
            diz(jogador, "<green>Já estava organizado.</green> <gray>" + itens
                    + " item(ns) em " + ocupados + " pilha(s).</gray>");
        } else {
            diz(jogador, "<green>" + liberados + " slot(s) liberado(s)</green> <gray>em "
                    + organizados + " recipiente(s) — " + itens + " item(ns) em "
                    + ocupados + " pilha(s).</gray>");
        }
        if (comGente > 0) {
            diz(jogador, "<yellow>Pulei " + comGente
                    + " recipiente(s): tem gente com eles abertos.</yellow>");
        }
        if (falhas > 0 && organizados > 0) {
            diz(jogador, "<yellow>" + falhas
                    + " recipiente(s) não foram tocados (conferência falhou). Veja o console.</yellow>");
        }
    }

    /** Alguém além do próprio jogador está com o inventário aberto? */
    private boolean outroOlhando(Inventory inventario, Player dono) {
        for (HumanEntity quem : inventario.getViewers()) {
            if (!quem.getUniqueId().equals(dono.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private void construir(Player jogador, Pedreiro pedreiro, String[] args) {
        if (args.length < 2) {
            diz(jogador, "<red>Falta o nome: /pedreiro construir <nome> [blocos por ciclo]</red>");
            return;
        }
        if (pedreiro.ocupado(jogador)) {
            diz(jogador, "<red>Você já tem uma obra rodando. /pedreiro parar primeiro.</red>");
            return;
        }
        Planta planta = pedreiro.abrir(args[1]);
        if (planta == null) {
            diz(jogador, "<red>Não achei a planta '" + args[1] + "'. Veja /pedreiro obras.</red>");
            return;
        }

        int porCiclo = 40;
        if (args.length >= 3) {
            try {
                porCiclo = Math.max(1, Math.min(2000, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignorado) {
                diz(jogador, "<gray>Velocidade inválida, usando 40.</gray>");
            }
        }

        Location origem = jogador.getLocation().getBlock().getLocation();
        pedreiro.iniciar(jogador, planta, origem, porCiclo);
        diz(jogador, "<green>Obra iniciada: <white>" + planta.nome() + "</white> ("
                + planta.total() + " blocos, " + porCiclo + " por ciclo).</green>");
        diz(jogador, "<gray>O canto mínimo é onde você está. /pedreiro desfazer volta tudo.</gray>");
    }

    private void ajuda(Player jogador) {
        diz(jogador, "<aqua>/pedreiro</aqua> <gray>— estuda uma construção e refaz em outro lugar</gray>");
        diz(jogador, "<white>p1</white> <gray>/</gray> <white>p2</white> <gray>marca os cantos onde você está</gray>");
        diz(jogador, "<white>estudar <nome></white> <gray>guarda a região marcada</gray>");
        diz(jogador, "<white>obras</white> <gray>lista o que já foi estudado</gray>");
        diz(jogador, "<white>amostra [tudo]</white> <gray>1 de cada item do baú mirado, sem tirar do baú</gray>");
        diz(jogador, "<white>organizar [bau|tudo]</white> <gray>junta as pilhas soltas e poe o bau em ordem</gray>");
        diz(jogador, "<white>construir <nome> [vel]</white> <gray>levanta aqui, aos poucos</gray>");
        diz(jogador, "<white>parar</white> <gray>/</gray> <white>desfazer</white> <gray>interrompe / devolve o terreno</gray>");
    }

    private void diz(Player jogador, String mensagem) {
        jogador.sendMessage(MiniMessage.miniMessage().deserialize(mensagem));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender remetente, @NotNull Command comando,
                                      @NotNull String rotulo, String[] args) {
        if (args.length == 1) {
            List<String> saida = new ArrayList<>();
            for (String sub : SUBCOMANDOS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    saida.add(sub);
                }
            }
            return saida;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("construir")) {
            return plugin.pedreiro().listar();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("amostra")) {
            return Collections.singletonList("tudo");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("organizar")) {
            return Arrays.asList("bau", "tudo");
        }
        return Collections.emptyList();
    }
}
