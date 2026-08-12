package codes.biga.bigacore;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

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

    private EconomyCatalog economyCatalog;
    private VaultEconomyBridge economyBridge;
    private SpawnShopBuilder spawnShopBuilder;
    private ShopPreview shopPreview;
    private ShopSnapshot shopSnapshot;
    private ServerBuyback serverBuyback;
    private StartingBalance startingBalance;
    private ShopDisplays shopDisplays;
    private ChestShopCapGuard chestShopCapGuard;

    @Override
    public void onEnable() {
        instance = this;

        // Cria o config.yml no disco a partir do resource embutido,
        // apenas se ele ainda não existir. Não sobrescreve o que o
        // admin editou.
        saveDefaultConfig();

        // economy.yml fica separado do config de mensagens porque ele
        // possui dezenas de preços e regras com ciclo de ajuste próprio.
        File economyFile = new File(getDataFolder(), "economy.yml");
        if (!economyFile.exists()) {
            saveResource("economy.yml", false);
        }

        this.economyCatalog = new EconomyCatalog(this);
        this.economyBridge = new VaultEconomyBridge();
        this.spawnShopBuilder = new SpawnShopBuilder(this);
        this.shopPreview = new ShopPreview(this);
        this.shopSnapshot = new ShopSnapshot(this);
        this.serverBuyback = new ServerBuyback(this);
        this.startingBalance = new StartingBalance(this);
        this.shopDisplays = new ShopDisplays(this);
        this.shopDisplays.start();

        // Precisa vir depois do ChestShop no softdepend, senão o plugin ainda
        // não está registrado e o hook do teto não encontra a classe do evento.
        this.chestShopCapGuard = new ChestShopCapGuard(this);
        this.chestShopCapGuard.register();

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

        String economyState = "economia Vault indisponível";
        if (economyBridge.available()) {
            economyState = "economia Vault conectada";
        }
        getLogger().info("BigaCore habilitado — " + economyState + ".");
    }

    @Override
    public void onDisable() {
        // Cancele tarefas agendadas, feche conexões de banco e salve
        // estado aqui. O servidor espera este método terminar antes
        // de prosseguir com o shutdown.
        getLogger().info("BigaCore desabilitado.");
        if (shopPreview != null) {
            shopPreview.stopAll();
        }
        if (shopDisplays != null) {
            shopDisplays.stop();
        }
        shopPreview = null;
        shopSnapshot = null;
        serverBuyback = null;
        startingBalance = null;
        shopDisplays = null;
        chestShopCapGuard = null;
        spawnShopBuilder = null;
        economyCatalog = null;
        economyBridge = null;
        instance = null;
    }

    /** Acesso à instância ativa. Retorna null se o plugin estiver desligado. */
    public static BigaCore getInstance() {
        return instance;
    }

    public EconomyCatalog economyCatalog() {
        return economyCatalog;
    }

    public VaultEconomyBridge economyBridge() {
        return economyBridge;
    }

    public SpawnShopBuilder spawnShopBuilder() {
        return spawnShopBuilder;
    }

    public ShopPreview shopPreview() {
        return shopPreview;
    }

    public ShopSnapshot shopSnapshot() {
        return shopSnapshot;
    }

    public ServerBuyback serverBuyback() {
        return serverBuyback;
    }

    public StartingBalance startingBalance() {
        return startingBalance;
    }

    public ShopDisplays shopDisplays() {
        return shopDisplays;
    }

    public ChestShopCapGuard chestShopCapGuard() {
        return chestShopCapGuard;
    }

    /** Recarrega os dois arquivos que pertencem ao BigaCore. */
    public void reloadBigaConfigs() {
        reloadConfig();
        economyCatalog.reload();
        serverBuyback.reload();
        startingBalance.reload();
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
