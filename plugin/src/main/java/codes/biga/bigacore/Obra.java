package codes.biga.bigacore;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Levanta uma {@link Planta} aos poucos, para dar de assistir.
 *
 * Coloca os blocos de baixo para cima na ordem YZX, que é a mesma em que a
 * planta foi lida. Subir camada por camada é o que faz parecer obra; se
 * colocasse tudo de uma vez seria indistinguível de um //paste.
 *
 * applyPhysics fica desligado: com ele ligado a água escorre e a tocha cai
 * enquanto a parede que as segura ainda não existe.
 */
public final class Obra extends BukkitRunnable {

    private final BigaCore plugin;
    private final Planta planta;
    private final World mundo;
    private final int ox;
    private final int oy;
    private final int oz;
    private final int porCiclo;
    private final UUID dono;

    private int cursor;
    private int colocados;

    public Obra(BigaCore plugin, Planta planta, Location origem, int porCiclo, UUID dono) {
        this.plugin = plugin;
        this.planta = planta;
        this.mundo = origem.getWorld();
        this.ox = origem.getBlockX();
        this.oy = origem.getBlockY();
        this.oz = origem.getBlockZ();
        this.porCiclo = Math.max(1, porCiclo);
        this.dono = dono;
    }

    public Planta planta() {
        return planta;
    }

    public int progresso() {
        return cursor;
    }

    @Override
    public void run() {
        int limite = Math.min(cursor + porCiclo, planta.total());
        Location ultimo = null;

        while (cursor < limite) {
            int x = ox + planta.xDe(cursor);
            int y = oy + planta.yDe(cursor);
            int z = oz + planta.zDe(cursor);

            // Fora do mundo (abaixo do fundo ou acima do teto) é ignorado em
            // vez de estourar: a obra continua e o aviso vem no fim.
            if (y >= mundo.getMinHeight() && y < mundo.getMaxHeight()) {
                mundo.getBlockAt(x, y, z).setBlockData(planta.dadosEm(cursor), false);
                ultimo = new Location(mundo, x, y, z);
                colocados++;
            }
            cursor++;
        }

        if (ultimo != null) {
            mundo.playSound(ultimo, Sound.BLOCK_STONE_PLACE, 0.4f, 1.0f);
        }

        if (cursor >= planta.total()) {
            cancel();
            plugin.pedreiro().aoTerminar(this);
            avisar("<green>Obra <white>" + planta.nome()
                    + "</white> concluída — " + colocados + " blocos.</green>");
        }
    }

    private void avisar(String mensagem) {
        Player jogador = plugin.getServer().getPlayer(dono);
        if (jogador != null) {
            jogador.sendMessage(MiniMessage.miniMessage().deserialize(mensagem));
        }
    }
}
