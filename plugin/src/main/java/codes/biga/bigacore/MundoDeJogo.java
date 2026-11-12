package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * Garante que o mundo de um minigame existe, criando-o vazio se preciso.
 *
 * <h2>Por que não deixar isso para o /mv create</h2>
 *
 * O hub e o lobby foram criados à mão, uma vez cada, e isso foi razoável. Com
 * vinte jogos deixa de ser: seriam vinte comandos a digitar na ordem certa,
 * com a flag {@code -g BigaCore} que, esquecida, gera um mundo de PEDRA por
 * baixo da arena — e o construtor então se recusa a levantar nada, dizendo
 * que "há blocos no caminho", o que não explica nada a quem errou a flag.
 *
 * <p>Aqui o mundo nasce certo ou não nasce. Se a criação falhar, o aviso diz
 * o comando exato para fazer à mão, e o jogo simplesmente não roda até lá, em
 * vez de rodar errado.
 *
 * <h2>O Multiverse continua opcional</h2>
 *
 * Um mundo criado assim funciona para tudo o que o plugin faz — o portal usa
 * {@code Bukkit.getWorld(nome)} e acha. O que ele NÃO ganha é /mvtp e as
 * configurações por mundo do Multiverse; para isso basta um
 * {@code /mv import <nome> normal -g BigaCore} depois, uma vez.
 */
public final class MundoDeJogo {

    private MundoDeJogo() {
    }

    /**
     * O mundo de nome {@code nome}, criado vazio se ainda não existir.
     *
     * @return o mundo, ou null se não existia e não deu para criar.
     */
    public static World garantir(BigaCore plugin, String nome) {
        World achado = Bukkit.getWorld(nome);
        if (achado != null) {
            return achado;
        }

        plugin.getLogger().info("[mundos] Criando o mundo vazio '" + nome + "'.");
        try {
            World novo = new WorldCreator(nome)
                    .environment(World.Environment.NORMAL)
                    .generator(new VazioGenerator())
                    .generateStructures(false)
                    .createWorld();
            if (novo == null) {
                plugin.getLogger().warning("[mundos] O servidor recusou criar '" + nome
                        + "'. Crie à mão: /mv create " + nome + " normal -g BigaCore");
                return null;
            }
            novo.setAutoSave(true);
            return novo;
        } catch (RuntimeException erro) {
            // Criar mundo mexe em disco e no registro de dimensões do servidor.
            // Se falhar, o que NÃO pode acontecer é derrubar o onEnable e levar
            // economia, lojas e painel junto — foi o que já aconteceu com a
            // ponte do WorldGuard.
            plugin.getLogger().warning("[mundos] Falhou criar '" + nome + "': "
                    + erro.getMessage() + ". Crie à mão: /mv create " + nome
                    + " normal -g BigaCore");
            return null;
        }
    }
}
