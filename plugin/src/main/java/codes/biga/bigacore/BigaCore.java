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
    private ShopSignWriter shopSignWriter;
    private ServerBuyback serverBuyback;
    private StartingBalance startingBalance;
    private ShopDisplays shopDisplays;
    private ChestShopCapGuard chestShopCapGuard;
    private PlotApiBridge plotApi;
    private PlotSale plotSale;
    private Pedreiro pedreiro;
    private PainelAjuda painelAjuda;
    private WorldGuardBridge worldGuard;
    private TerrenoCatalog terrenoCatalog;
    private TerrenoVenda terrenoVenda;
    private CercaTerreno cercaTerreno;
    private EncomendaCatalog encomendaCatalog;
    private EncomendaLivro encomendaLivro;

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

        // ajuda.yml guarda o texto do paredão do spawn. Mesmo motivo do
        // economy.yml para ficar fora do config.yml: é conteúdo editorial,
        // reescrito com frequência e sem relação com as chaves de
        // comportamento do plugin.
        File ajudaFile = new File(getDataFolder(), "ajuda.yml");
        if (!ajudaFile.exists()) {
            saveResource("ajuda.yml", false);
        }

        // terrenos.yml: regra de venda de terreno no survival. Fora do
        // economy.yml porque mexer no preco por bloco nao deveria obrigar a
        // reler trezentas linhas de catalogo de item.
        File terrenosFile = new File(getDataFolder(), "terrenos.yml");
        if (!terrenosFile.exists()) {
            saveResource("terrenos.yml", false);
        }

        // encomendas.yml: limites do quadro de pedidos. Os pedidos em si
        // moram em encomendas-abertas.yml, que nasce vazio em tempo de
        // execucao — regra e dado em arquivos diferentes, para um update do
        // plugin nunca sobrescrever pedido de jogador.
        File encomendasFile = new File(getDataFolder(), "encomendas.yml");
        if (!encomendasFile.exists()) {
            saveResource("encomendas.yml", false);
        }

        this.economyCatalog = new EconomyCatalog(this);
        this.economyBridge = new VaultEconomyBridge();
        this.spawnShopBuilder = new SpawnShopBuilder(this);
        this.shopPreview = new ShopPreview(this);
        this.shopSnapshot = new ShopSnapshot(this);
        this.shopSignWriter = new ShopSignWriter(this);
        this.serverBuyback = new ServerBuyback(this);
        this.startingBalance = new StartingBalance(this);
        this.plotApi = new PlotApiBridge();
        this.plotSale = new PlotSale(this);
        this.shopDisplays = new ShopDisplays(this);
        this.shopDisplays.start();
        this.painelAjuda = new PainelAjuda(this);
        // So construir a ponte se o WorldGuard estiver carregado. Nao e zelo
        // excessivo: ligar a classe faz a JVM resolver com/sk89q/..., e num
        // servidor sem WorldGuard isso lanca NoClassDefFoundError aqui dentro
        // do onEnable, derrubando o BigaCore INTEIRO — economia, lojas e
        // painel junto. Foi o que aconteceu: o smoke test do CI nao instala
        // WorldGuard e ficou vermelho de 4e41c89 ate aqui.
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            this.worldGuard = new WorldGuardBridge(this);
        } else {
            this.worldGuard = null;
        }
        this.terrenoCatalog = new TerrenoCatalog(this);
        this.terrenoVenda = new TerrenoVenda(this);
        this.cercaTerreno = new CercaTerreno(this);
        this.encomendaCatalog = new EncomendaCatalog(this);
        this.encomendaLivro = new EncomendaLivro(this);

        // Precisa vir depois do ChestShop no softdepend, senão o plugin ainda
        // não está registrado e o hook do teto não encontra a classe do evento.
        this.chestShopCapGuard = new ChestShopCapGuard(this);
        this.chestShopCapGuard.register();

        // Listeners e comandos ficam em classes próprias para a
        // classe principal não virar um depósito de tudo.
        getServer().getPluginManager().registerEvents(new JogadorListener(this), this);

        // Venda de terreno. O listener sobe sempre; quem decide se cobra é o
        // PlotSale, que fica inerte se policy.plot-prices estiver vazio ou se
        // o CreativePlots nao estiver instalado.
        getServer().getPluginManager().registerEvents(new PlotPurchaseListener(this), this);

        this.pedreiro = new Pedreiro(this);

        var cmdPedreiro = getCommand("pedreiro");
        if (cmdPedreiro != null) {
            var executorPedreiro = new PedreiroCommand(this);
            cmdPedreiro.setExecutor(executorPedreiro);
            cmdPedreiro.setTabCompleter(executorPedreiro);
        } else {
            getLogger().severe("Comando 'pedreiro' não declarado no plugin.yml — verifique o arquivo.");
        }

        var cmdTerreno = getCommand("terreno");
        if (cmdTerreno != null) {
            var executorTerreno = new TerrenoCommand(this);
            cmdTerreno.setExecutor(executorTerreno);
            cmdTerreno.setTabCompleter(executorTerreno);
        } else {
            getLogger().severe("Comando 'terreno' não declarado no plugin.yml — verifique o arquivo.");
        }

        var cmdEncomenda = getCommand("encomenda");
        if (cmdEncomenda != null) {
            var executorEncomenda = new EncomendaCommand(this);
            cmdEncomenda.setExecutor(executorEncomenda);
            cmdEncomenda.setTabCompleter(executorEncomenda);
        } else {
            getLogger().severe("Comando 'encomenda' não declarado no plugin.yml — verifique o arquivo.");
        }

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

        relatarVendaDeTerreno();
        relatarTerrenoSurvival();
        relatarEncomendas();
    }

    /**
     * Diz no console se a venda de terreno está realmente valendo.
     *
     * A cobrança depende de reflexão sobre o CreativePlots, e reflexão falha em
     * silêncio: sem esta linha, um terreno saindo de graça por API renomeada
     * só apareceria quando alguém reparasse que o saldo não caiu. Mesmo motivo
     * da linha [teto] do ChestShopCapGuard.
     */
    private void relatarVendaDeTerreno() {
        var precos = economyCatalog.plotPrices();
        String moeda = economyCatalog.currencySymbol();

        if (precos.isEmpty()) {
            getLogger().info("[terreno] Venda desligada — policy.plot-prices está vazio.");
            return;
        }

        if (!plotApi.available()) {
            getLogger().warning("[terreno] CreativePlots não encontrado pela ponte de reflexão — "
                    + "terreno continua SENDO DE GRAÇA apesar de policy.plot-prices estar preenchido.");
            return;
        }

        if (!economyBridge.available()) {
            getLogger().warning("[terreno] Economia Vault indisponível — terreno continua de graça.");
            return;
        }

        StringBuilder escada = new StringBuilder();
        for (int i = 0; i < precos.size(); i++) {
            escada.append(i == 0 ? "" : " / ").append(precos.get(i));
        }
        getLogger().info("[terreno] Venda ligada — " + escada + " " + moeda
                + " (depois disso, x" + PlotSale.fatorDeEscalada(precos) + " por terreno).");
    }

    /**
     * Diz no console se /terreno esta valendo, e por que nao quando nao esta.
     *
     * Mesmo motivo das linhas [terreno] e [teto]: a venda depende de coisas
     * que falham caladas. Sem WorldGuard o comando responde erro so para quem
     * digitou, e um mundo fora da lista branca nao gera aviso nenhum — o dono
     * descobriria pela reclamacao de um jogador.
     */
    private void relatarTerrenoSurvival() {
        if (worldGuard == null) {
            getLogger().warning("[survival] /terreno desligado — WorldGuard nao esta instalado.");
            return;
        }

        if (!worldGuard.disponivel()) {
            getLogger().warning("[survival] /terreno desligado — WorldGuard instalado mas a API "
                    + "de regioes nao respondeu. Confira a versao do plugin.");
            return;
        }

        if (!economyBridge.available()) {
            getLogger().warning("[survival] /terreno ativo mas SEM economia — "
                    + "terreno sairia de graca. Confira o provider do Vault.");
            return;
        }

        getLogger().info("[survival] /terreno ativo — "
                + terrenoCatalog.precoPorBloco() + " por bloco, de "
                + terrenoCatalog.tamanhoMinimo() + "x" + terrenoCatalog.tamanhoMinimo()
                + " a " + terrenoCatalog.tamanhoMaximo() + "x" + terrenoCatalog.tamanhoMaximo()
                + ", limite " + terrenoCatalog.limitePorJogador()
                + " por pessoa, nos mundos " + String.join(", ", terrenoCatalog.mundos()) + ".");
    }

    /**
     * Diz no console em que estado o quadro de encomendas subiu.
     *
     * Mesmo motivo das linhas [terreno] e [teto]: o pedido guarda dinheiro de
     * gente. Se o quadro voltar vazio depois de um desligamento sujo, ou se a
     * economia nao responder, isso precisa aparecer no boot e nao na
     * reclamacao de quem pagou.
     */
    private void relatarEncomendas() {
        if (!encomendaCatalog.ativo()) {
            getLogger().info("[encomenda] Quadro desligado — ativo: false no encomendas.yml.");
            return;
        }

        if (!economyBridge.available()) {
            getLogger().warning("[encomenda] Economia Vault indisponivel — /encomenda "
                    + "recusa criar e entregar ate o provider aparecer.");
            return;
        }

        int abertas = encomendaLivro.abertas().size();
        long emCustodia = 0;
        for (Encomenda pedido : encomendaLivro.abertas()) {
            emCustodia += pedido.preco();
        }

        getLogger().info("[encomenda] Quadro ativo — " + abertas + " pedido(s) aberto(s), "
                + economyCatalog.money(emCustodia) + " em custodia, limite "
                + encomendaCatalog.limitePorJogador() + " por jogador.");
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
        if (pedreiro != null) {
            pedreiro.desligar();
        }
        pedreiro = null;
        shopPreview = null;
        shopSnapshot = null;
        shopSignWriter = null;
        serverBuyback = null;
        startingBalance = null;
        shopDisplays = null;
        chestShopCapGuard = null;
        plotSale = null;
        plotApi = null;
        painelAjuda = null;
        terrenoVenda = null;
        cercaTerreno = null;
        encomendaLivro = null;
        encomendaCatalog = null;
        terrenoCatalog = null;
        worldGuard = null;
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

    public Pedreiro pedreiro() {
        return pedreiro;
    }

    public SpawnShopBuilder spawnShopBuilder() {
        return spawnShopBuilder;
    }

    public ShopPreview shopPreview() {
        return shopPreview;
    }

    public ShopSignWriter shopSignWriter() {
        return shopSignWriter;
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

    public PlotApiBridge plotApi() {
        return plotApi;
    }

    public PlotSale plotSale() {
        return plotSale;
    }

    public PainelAjuda painelAjuda() {
        return painelAjuda;
    }

    public WorldGuardBridge worldGuard() {
        return worldGuard;
    }

    /**
     * Diz se da para mexer em terreno agora.
     *
     * Passa por aqui, e nao direto no {@code worldGuard.disponivel()}, porque
     * a ponte e nula quando o WorldGuard nao esta instalado. O curto-circuito
     * do && e justamente o que impede a JVM de ligar a classe da ponte nesse
     * caso. Veja o comentario no onEnable.
     */
    public boolean terrenoDisponivel() {
        return worldGuard != null && worldGuard.disponivel();
    }

    public TerrenoCatalog terrenoCatalog() {
        return terrenoCatalog;
    }

    public TerrenoVenda terrenoVenda() {
        return terrenoVenda;
    }

    public CercaTerreno cercaTerreno() {
        return cercaTerreno;
    }

    public EncomendaCatalog encomendaCatalog() {
        return encomendaCatalog;
    }

    public EncomendaLivro encomendaLivro() {
        return encomendaLivro;
    }

    /** Recarrega os dois arquivos que pertencem ao BigaCore. */
    public void reloadBigaConfigs() {
        reloadConfig();
        economyCatalog.reload();
        serverBuyback.reload();
        startingBalance.reload();
        plotSale.reload();
        painelAjuda.reload();
        terrenoCatalog.reload();
        encomendaCatalog.reload();
        encomendaLivro.reload();
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
