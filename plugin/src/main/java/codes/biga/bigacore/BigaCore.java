package codes.biga.bigacore;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Ponto de entrada do plugin.
 *
 * O Paper instancia esta classe uma única vez e chama onEnable()
 * quando carrega o plugin e onDisable() quando desliga. Não use
 * construtor para lógica de inicialização — nessa hora o servidor
 * ainda não está pronto e várias APIs retornam null.
 */
public final class BigaCore extends JavaPlugin {

    private static BigaCore instance;

    @Override
    public void onEnable() {
        instance = this;

        // Cria o config.yml no disco a partir do resource embutido,
        // apenas se ele ainda não existir. Não sobrescreve o que o
        // admin editou.
        saveDefaultConfig();

        // Listeners e comandos ficam em classes próprias para a
        // classe principal não virar um depósito de tudo.
        getServer().getPluginManager().registerEvents(new JogadorListener(this), this);

        var cmd = getCommand("biga");
        if (cmd != null) {
            var executor = new BigaCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().severe("Comando 'biga' não declarado no plugin.yml — verifique o arquivo.");
        }

        getLogger().info("BigaCore habilitado.");
    }

    @Override
    public void onDisable() {
        // Cancele tarefas agendadas, feche conexões de banco e salve
        // estado aqui. O servidor espera este método terminar antes
        // de prosseguir com o shutdown.
        getLogger().info("BigaCore desabilitado.");
        instance = null;
    }

    /** Acesso à instância ativa. Retorna null se o plugin estiver desligado. */
    public static BigaCore getInstance() {
        return instance;
    }

    /**
     * O parser de MiniMessage.
     *
     * MiniMessage.miniMessage() já devolve uma instância única e
     * compartilhada, criada uma vez pela própria biblioteca. É
     * thread-safe e barata de chamar, então não precisa ser
     * guardada em campo — mas centralizar aqui deixa um ponto único
     * para, no futuro, trocar por uma instância customizada (com
     * tags próprias do narrador, por exemplo).
     */
    public MiniMessage mini() {
        return MiniMessage.miniMessage();
    }

    /**
     * Template da mensagem de boas-vindas, em sintaxe MiniMessage.
     * Ainda é String aqui de propósito: só vira Component depois de
     * os placeholders serem resolvidos, no JogadorListener.
     */
    public String getMensagemBoasVindas() {
        return getConfig().getString("mensagem-boas-vindas",
                "<aqua>Bem-vindo, <white><jogador></white>!");
    }

    public boolean isBoasVindasAtivo() {
        return getConfig().getBoolean("boas-vindas-ativo", true);
    }
}
