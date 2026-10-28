package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estado do pedreiro: cantos marcados, plantas em disco e obras em andamento.
 *
 * Fica separado do comando porque o comando é só a porta de entrada — quem
 * guarda estado precisa sobreviver a ele e ser desligado no onDisable.
 */
public final class Pedreiro {

    /** Uma obra concluída, guardada para o /pedreiro desfazer. */
    private record Volta(Planta antes, World mundo, int x, int y, int z) {
    }

    private final BigaCore plugin;
    private final File pasta;

    private final Map<UUID, Location> canto1 = new HashMap<>();
    private final Map<UUID, Location> canto2 = new HashMap<>();
    private final Map<UUID, Obra> obras = new HashMap<>();
    private final Map<UUID, Volta> voltas = new HashMap<>();

    public Pedreiro(BigaCore plugin) {
        this.plugin = plugin;
        this.pasta = new File(plugin.getDataFolder(), "obras");
        if (!pasta.exists() && !pasta.mkdirs()) {
            plugin.getLogger().warning("Não consegui criar a pasta de obras: " + pasta);
        }
    }

    // ------------------------------------------------------------- cantos

    public void marcar(Player jogador, int qual) {
        Location onde = jogador.getLocation().getBlock().getLocation();
        (qual == 1 ? canto1 : canto2).put(jogador.getUniqueId(), onde);
    }

    public Location canto(Player jogador, int qual) {
        return (qual == 1 ? canto1 : canto2).get(jogador.getUniqueId());
    }

    // ------------------------------------------------------------ plantas

    public File arquivoDe(String nome) {
        return new File(pasta, nome.toLowerCase() + ".yml");
    }

    public List<String> listar() {
        File[] arquivos = pasta.listFiles((dir, nome) -> nome.endsWith(".yml"));
        if (arquivos == null) {
            return Collections.emptyList();
        }
        List<String> nomes = new ArrayList<>();
        for (File arquivo : arquivos) {
            nomes.add(arquivo.getName().substring(0, arquivo.getName().length() - 4));
        }
        Collections.sort(nomes);
        return nomes;
    }

    public void guardar(Planta planta) throws IOException {
        planta.salvar(arquivoDe(planta.nome()));
    }

    public Planta abrir(String nome) {
        File arquivo = arquivoDe(nome);
        return arquivo.exists() ? Planta.carregar(arquivo) : null;
    }

    // -------------------------------------------------------------- obras

    public boolean ocupado(Player jogador) {
        return obras.containsKey(jogador.getUniqueId());
    }

    public Obra obraDe(Player jogador) {
        return obras.get(jogador.getUniqueId());
    }

    /**
     * Começa a levantar a planta com o canto mínimo na posição informada.
     *
     * Antes de colocar o primeiro bloco, fotografa o que já existia ali —
     * essa foto é o /pedreiro desfazer. Sem isso, construir em cima de algo
     * seria irreversível.
     */
    public void iniciar(Player jogador, Planta planta, Location origem, int porCiclo) {
        Location oposto = origem.clone().add(
                planta.largura() - 1.0, planta.altura() - 1.0, planta.comprimento() - 1.0);
        Planta antes = Planta.estudar("antes-" + planta.nome(), origem, oposto);
        voltas.put(jogador.getUniqueId(),
                new Volta(antes, origem.getWorld(),
                        origem.getBlockX(), origem.getBlockY(), origem.getBlockZ()));

        Obra obra = new Obra(plugin, planta, origem, porCiclo, jogador.getUniqueId());
        obras.put(jogador.getUniqueId(), obra);
        obra.runTaskTimer(plugin, 10L, 2L);
    }

    public boolean parar(Player jogador) {
        Obra obra = obras.remove(jogador.getUniqueId());
        if (obra == null) {
            return false;
        }
        obra.cancel();
        return true;
    }

    /** Chamado pela própria Obra quando o último bloco entra. */
    public void aoTerminar(Obra obra) {
        obras.values().remove(obra);
    }

    public boolean temVolta(Player jogador) {
        return voltas.containsKey(jogador.getUniqueId());
    }

    public boolean desfazer(Player jogador) {
        Volta volta = voltas.remove(jogador.getUniqueId());
        if (volta == null) {
            return false;
        }
        parar(jogador);
        volta.antes().aplicar(volta.mundo(), volta.x(), volta.y(), volta.z());
        return true;
    }

    public void desligar() {
        for (Obra obra : new ArrayList<>(obras.values())) {
            obra.cancel();
        }
        obras.clear();
        canto1.clear();
        canto2.clear();
        voltas.clear();
    }
}
