package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.logging.Level;

/**
 * A venda de terreno no survival.
 *
 * Esta classe decide; o {@link WorldGuardBridge} executa; o
 * {@link TerrenoCommand} conversa com o jogador. Nenhuma delas faz o trabalho
 * das outras, e e por isso que a ordem das operacoes numa compra — a parte que
 * da prejuizo de verdade quando esta errada — cabe num metodo que se le de uma
 * vez.
 *
 * <h2>A ordem da compra</h2>
 *
 * Criar a regiao primeiro, cobrar depois. Se a cobranca falhar, a regiao e
 * desfeita. O inverso — cobrar e depois tentar criar — tem um caso em que o
 * jogador paga e nao recebe: id ja existente, colisao que apareceu entre a
 * checagem e a criacao, WorldGuard recusando a regiao. Nesse caminho o
 * conserto depende de um deposito que tambem pode falhar, e o jogador fica sem
 * terreno e sem dinheiro. Mesma logica que o {@link PlotSale} ja usa.
 *
 * <h2>Por que o reembolso nao passa pelo teto do ServerBuyback</h2>
 *
 * Vender terreno credita moeda, e todo deposito e emissao de dinheiro. A
 * diferenca do admin shop e que aqui o valor e limitado pelo que o jogador
 * pagou antes, e sempre menor: o reembolso sai do preco BASE, sem o
 * multiplicador de quantidade. Comprar e vender em sequencia da prejuizo
 * garantido. Nao existe ciclo com lucro para automatizar, entao nao existe o
 * que capear. Vender pedra para o servidor e diferente — pedra e renovavel e
 * o ciclo nunca fecha.
 */
public final class TerrenoVenda {

    private final BigaCore plugin;

    public TerrenoVenda(BigaCore plugin) {
        this.plugin = plugin;
    }

    /** Orcamento de uma compra: tudo checado, nada feito. */
    public record Orcamento(boolean ok, String motivo, Caixa caixa, int tamanho,
                            int slot, long preco, double multiplicador,
                            int jaTem, List<String> colisoes) {

        static Orcamento recusado(String motivo) {
            return new Orcamento(false, motivo, null, 0, 0, 0L, 0.0, 0, List.of());
        }

        static Orcamento recusadoPorColisao(String motivo, List<String> colisoes) {
            return new Orcamento(false, motivo, null, 0, 0, 0L, 0.0, 0, colisoes);
        }
    }

    public record Resultado(boolean ok, String mensagem, long valor, String id) {

        static Resultado nao(String mensagem) {
            return new Resultado(false, mensagem, 0L, null);
        }

        static Resultado sim(String mensagem, long valor, String id) {
            return new Resultado(true, mensagem, valor, id);
        }
    }

    private TerrenoCatalog catalogo() {
        return plugin.terrenoCatalog();
    }

    private WorldGuardBridge wg() {
        return plugin.worldGuard();
    }

    public boolean isento(Player jogador) {
        return jogador.hasPermission("bigacore.terreno.isento");
    }

    public boolean semLimite(Player jogador) {
        return jogador.hasPermission("bigacore.terreno.ilimitado");
    }

    public List<WorldGuardBridge.Terreno> meus(Player jogador) {
        if (!wg().disponivel()) {
            return List.of();
        }
        return wg().terrenosDe(jogador.getWorld(), jogador.getUniqueId(), catalogo().prefixo());
    }

    /** O terreno em que o jogador esta pisando, se for nosso. */
    public Optional<WorldGuardBridge.Terreno> onde(Player jogador) {
        if (!wg().disponivel()) {
            return Optional.empty();
        }
        Location local = jogador.getLocation();
        return wg().em(jogador.getWorld(), local.getBlockX(), local.getBlockY(),
                local.getBlockZ(), catalogo().prefixo());
    }

    // ------------------------------------------------------------------
    // Orcamento
    // ------------------------------------------------------------------

    public Orcamento orcar(Player jogador, int tamanho) {
        TerrenoCatalog cfg = catalogo();

        if (!wg().disponivel()) {
            return Orcamento.recusado(
                    "O WorldGuard nao esta respondendo, entao nao da para proteger terreno agora.");
        }

        World mundo = jogador.getWorld();
        if (!cfg.mundoPermitido(mundo.getName())) {
            return Orcamento.recusado("Neste mundo nao se vende terreno. "
                    + "Mundos a venda: " + String.join(", ", cfg.mundos()) + ".");
        }

        int minimo = cfg.tamanhoMinimo();
        int maximo = cfg.tamanhoMaximo();
        if (tamanho < minimo || tamanho > maximo) {
            return Orcamento.recusado("O tamanho vai de " + minimo + " a " + maximo
                    + " blocos de lado. Voce pediu " + tamanho + ".");
        }

        Location local = jogador.getLocation();
        Caixa caixa = Caixa.centradaEm(local.getBlockX(), local.getBlockZ(), tamanho,
                alturaBaixa(mundo, local.getBlockY()), alturaAlta(mundo, local.getBlockY()));

        Location spawn = mundo.getSpawnLocation();
        int folgaSpawn = cfg.folgaDoSpawn();
        int distancia = caixa.distanciaAte(spawn.getBlockX(), spawn.getBlockZ());
        if (distancia < folgaSpawn) {
            return Orcamento.recusado("Perto demais do spawn. O terreno precisa ficar a "
                    + folgaSpawn + " blocos dele, e este fica a " + distancia + ".");
        }

        List<WorldGuardBridge.Terreno> ja = wg().terrenosDe(
                mundo, jogador.getUniqueId(), cfg.prefixo());
        int limite = cfg.limitePorJogador();
        if (!semLimite(jogador) && ja.size() >= limite) {
            return Orcamento.recusado("Voce ja tem " + ja.size() + " terreno(s) neste mundo, "
                    + "que e o limite. Venda um com /terreno vender antes de comprar outro.");
        }

        List<String> colisoes = wg().colisoes(mundo, caixa.inflada(cfg.folgaEntreTerrenos()), null);
        if (!colisoes.isEmpty()) {
            return Orcamento.recusadoPorColisao(
                    "Esse quadrado encosta em area que ja tem dono.", colisoes);
        }

        int slot = proximoSlot(ja, cfg.prefixo(), jogador.getName());
        double multiplicador = TerrenoCatalog.multiplicadorPara(cfg.multiplicadores(), slot - 1);
        long preco = isento(jogador)
                ? 0L
                : TerrenoCatalog.preco(caixa.area(), cfg.precoPorBloco(), multiplicador);

        return new Orcamento(true, null, caixa, tamanho, slot, preco,
                multiplicador, ja.size(), List.of());
    }

    // ------------------------------------------------------------------
    // Compra
    // ------------------------------------------------------------------

    public Resultado comprar(Player jogador, int tamanho) {
        Orcamento orcamento = orcar(jogador, tamanho);
        if (!orcamento.ok()) {
            return Resultado.nao(orcamento.motivo());
        }

        World mundo = jogador.getWorld();
        TerrenoCatalog cfg = catalogo();
        String id = idPara(cfg.prefixo(), jogador.getName(), orcamento.slot());

        if (orcamento.preco() > 0) {
            Resultado saldo = conferirSaldo(jogador, orcamento.preco());
            if (saldo != null) {
                return saldo;
            }
        }

        boolean criou = wg().criar(mundo, id, orcamento.caixa(), jogador.getUniqueId(),
                textoDeFlag(cfg.mensagemEntrada(), jogador.getName()),
                textoDeFlag(cfg.mensagemSaida(), jogador.getName()));

        if (!criou) {
            return Resultado.nao("Nao consegui registrar a regiao " + id
                    + ". Nada foi cobrado. Avise o administrador.");
        }

        if (orcamento.preco() > 0
                && !plugin.economyBridge().withdraw(jogador, orcamento.preco())) {
            // Cobranca falhou depois da regiao existir: desfaz, para nunca
            // entregar terreno de graca por falha de economia.
            wg().apagar(mundo, id);
            return Resultado.nao("A cobranca nao passou. O terreno nao foi criado.");
        }

        wg().salvarDepois(mundo);

        plugin.getLogger().info("[terreno] " + jogador.getName() + " comprou " + id
                + " (" + orcamento.tamanho() + "x" + orcamento.tamanho() + ", "
                + orcamento.caixa().area() + " blocos) por " + orcamento.preco() + ".");

        return Resultado.sim("Terreno comprado.", orcamento.preco(), id);
    }

    // ------------------------------------------------------------------
    // Expansao
    // ------------------------------------------------------------------

    /**
     * Aumenta o terreno em que o jogador esta, mantendo o centro.
     *
     * Mantem o centro, nao a posicao do jogador: expandir de onde se esta
     * arrastaria o terreno a cada expansao, e depois de tres o jogador estaria
     * com metade da construcao fora dele.
     *
     * A diferenca de area e cobrada pelo multiplicador DO SLOT do terreno, nao
     * por 1.0. Sem isso, comprar o segundo terreno no tamanho minimo (x2.0 de
     * um quadrado pequeno) e expandir para 64x64 pagaria o preco de primeiro
     * terreno — o multiplicador de quantidade viraria decoracao.
     */
    public Resultado expandir(Player jogador, int novoTamanho) {
        if (!wg().disponivel()) {
            return Resultado.nao("O WorldGuard nao esta respondendo.");
        }

        Optional<WorldGuardBridge.Terreno> talvez = onde(jogador);
        if (talvez.isEmpty()) {
            return Resultado.nao("Fique dentro do seu terreno para expandir.");
        }

        WorldGuardBridge.Terreno terreno = talvez.get();
        if (!terreno.ehDono(jogador.getUniqueId())) {
            return Resultado.nao("Este terreno nao e seu.");
        }

        TerrenoCatalog cfg = catalogo();
        World mundo = jogador.getWorld();
        Caixa atual = terreno.caixa();
        int tamanhoAtual = atual.lado();

        if (novoTamanho <= tamanhoAtual) {
            return Resultado.nao("O terreno ja tem " + tamanhoAtual
                    + " de lado. Para expandir, peca um numero maior.");
        }
        if (novoTamanho > cfg.tamanhoMaximo()) {
            return Resultado.nao("O maximo e " + cfg.tamanhoMaximo() + " blocos de lado.");
        }

        Caixa nova = Caixa.centradaEm(atual.centroX(), atual.centroZ(), novoTamanho,
                        atual.minY(), atual.maxY())
                .comAltura(atual.minY(), atual.maxY());

        Location spawn = mundo.getSpawnLocation();
        int distancia = nova.distanciaAte(spawn.getBlockX(), spawn.getBlockZ());
        if (distancia < cfg.folgaDoSpawn()) {
            return Resultado.nao("Nesse tamanho o terreno chegaria a " + distancia
                    + " blocos do spawn, e o minimo e " + cfg.folgaDoSpawn() + ".");
        }

        List<String> colisoes = wg().colisoes(
                mundo, nova.inflada(cfg.folgaEntreTerrenos()), terreno.id());
        if (!colisoes.isEmpty()) {
            return Resultado.nao("Nesse tamanho encostaria em: " + String.join(", ", colisoes) + ".");
        }

        int slot = slotDoId(terreno.id());
        double multiplicador = TerrenoCatalog.multiplicadorPara(
                cfg.multiplicadores(), Math.max(0, slot - 1));
        int areaExtra = nova.area() - atual.area();
        long preco = isento(jogador)
                ? 0L
                : TerrenoCatalog.preco(areaExtra, cfg.precoPorBloco(), multiplicador);

        if (preco > 0) {
            Resultado saldo = conferirSaldo(jogador, preco);
            if (saldo != null) {
                return saldo;
            }
        }

        if (!wg().redimensionar(mundo, terreno.id(), nova)) {
            return Resultado.nao("Nao consegui redefinir a regiao. Nada foi cobrado.");
        }

        if (preco > 0 && !plugin.economyBridge().withdraw(jogador, preco)) {
            // Volta ao tamanho antigo: a expansao sem pagamento daria area de graca.
            wg().redimensionar(mundo, terreno.id(), atual);
            return Resultado.nao("A cobranca nao passou. O terreno ficou do tamanho que estava.");
        }

        wg().salvarDepois(mundo);
        plugin.getLogger().info("[terreno] " + jogador.getName() + " expandiu " + terreno.id()
                + " de " + tamanhoAtual + " para " + novoTamanho + " por " + preco + ".");

        return Resultado.sim("Terreno expandido para " + novoTamanho + "x" + novoTamanho + ".",
                preco, terreno.id());
    }

    // ------------------------------------------------------------------
    // Venda de volta
    // ------------------------------------------------------------------

    public Resultado vender(Player jogador) {
        if (!wg().disponivel()) {
            return Resultado.nao("O WorldGuard nao esta respondendo.");
        }

        Optional<WorldGuardBridge.Terreno> talvez = onde(jogador);
        if (talvez.isEmpty()) {
            return Resultado.nao("Fique dentro do terreno que voce quer vender.");
        }

        WorldGuardBridge.Terreno terreno = talvez.get();
        if (!terreno.ehDono(jogador.getUniqueId())) {
            return Resultado.nao("Este terreno nao e seu.");
        }

        TerrenoCatalog cfg = catalogo();
        World mundo = jogador.getWorld();
        long volta = TerrenoCatalog.reembolso(
                terreno.caixa().area(), cfg.precoPorBloco(), cfg.reembolsoPercent());

        if (!wg().apagar(mundo, terreno.id())) {
            return Resultado.nao("Nao consegui remover a regiao. Nada mudou.");
        }

        if (volta > 0 && !plugin.economyBridge().deposit(jogador, volta)) {
            // Recria para o jogador nao ficar sem terreno e sem dinheiro.
            // Os membros convidados se perdem neste caminho — e o preco de nao
            // perder o terreno, e por isso o log e SEVERE.
            wg().criar(mundo, terreno.id(), terreno.caixa(), jogador.getUniqueId(),
                    textoDeFlag(cfg.mensagemEntrada(), jogador.getName()),
                    textoDeFlag(cfg.mensagemSaida(), jogador.getName()));
            plugin.getLogger().log(Level.SEVERE, "[terreno] Reembolso de " + volta
                    + " para " + jogador.getName() + " falhou; a regiao " + terreno.id()
                    + " foi recriada SEM os membros convidados.");
            return Resultado.nao("O reembolso nao passou. Seu terreno continua seu, "
                    + "mas os convidados precisam ser adicionados de novo.");
        }

        wg().salvarDepois(mundo);
        plugin.getLogger().info("[terreno] " + jogador.getName() + " vendeu " + terreno.id()
                + " e recebeu " + volta + ".");

        return Resultado.sim("Terreno vendido.", volta, terreno.id());
    }

    // ------------------------------------------------------------------
    // Convidados
    // ------------------------------------------------------------------

    public Resultado convidado(Player jogador, OfflinePlayer alvo, boolean adicionar) {
        if (!wg().disponivel()) {
            return Resultado.nao("O WorldGuard nao esta respondendo.");
        }

        Optional<WorldGuardBridge.Terreno> talvez = onde(jogador);
        if (talvez.isEmpty()) {
            return Resultado.nao("Fique dentro do seu terreno.");
        }

        WorldGuardBridge.Terreno terreno = talvez.get();
        if (!terreno.ehDono(jogador.getUniqueId())) {
            return Resultado.nao("Este terreno nao e seu.");
        }

        if (alvo.getUniqueId().equals(jogador.getUniqueId())) {
            return Resultado.nao("Voce ja e o dono.");
        }

        boolean jaEra = terreno.membros().contains(alvo.getUniqueId());
        if (adicionar && jaEra) {
            return Resultado.nao(nomeDe(alvo) + " ja podia construir aqui.");
        }
        if (!adicionar && !jaEra) {
            return Resultado.nao(nomeDe(alvo) + " nao estava na lista.");
        }

        if (!wg().membro(jogador.getWorld(), terreno.id(), alvo.getUniqueId(), adicionar)) {
            return Resultado.nao("Nao consegui mexer na lista de convidados.");
        }

        wg().salvarDepois(jogador.getWorld());
        return Resultado.sim(adicionar
                        ? nomeDe(alvo) + " agora pode construir no seu terreno."
                        : nomeDe(alvo) + " nao pode mais construir no seu terreno.",
                0L, terreno.id());
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /** Devolve null quando o saldo cobre; devolve a recusa pronta quando nao. */
    private Resultado conferirSaldo(Player jogador, long preco) {
        if (!plugin.economyBridge().available()) {
            return Resultado.nao("A economia nao esta disponivel, entao nao da para cobrar.");
        }

        OptionalDouble saldo = plugin.economyBridge().balance(jogador);
        if (saldo.isPresent() && saldo.getAsDouble() < preco) {
            EconomyCatalog eco = plugin.economyCatalog();
            long falta = preco - (long) saldo.getAsDouble();
            return Resultado.nao("Custa " + eco.money(preco) + " e voce tem "
                    + eco.money((long) saldo.getAsDouble()) + ". Faltam " + eco.money(falta) + ".");
        }
        return null;
    }

    /**
     * Altura de baixo da regiao.
     *
     * Com altura-total o terreno pega a coluna inteira. E o padrao porque a
     * alternativa cria o buraco mais explorado em servidor de survival: quem
     * nao pode construir no terreno cava por baixo dele, sai dentro da casa e
     * leva tudo, sem nunca quebrar um bloco protegido.
     */
    private int alturaBaixa(World mundo, int yJogador) {
        if (catalogo().alturaTotal()) {
            return mundo.getMinHeight();
        }
        return Math.max(mundo.getMinHeight(), yJogador - 16);
    }

    private int alturaAlta(World mundo, int yJogador) {
        if (catalogo().alturaTotal()) {
            return mundo.getMaxHeight() - 1;
        }
        return Math.min(mundo.getMaxHeight() - 1, yJogador + 48);
    }

    /**
     * Primeiro numero de slot livre do jogador.
     *
     * O numero fica no id da regiao (t_nome_2) e e dele que sai o
     * multiplicador de preco. Usar o numero do slot em vez da contagem de
     * terrenos deixa o preco estavel: o seu terreno numero 2 custa o preco de
     * segundo terreno hoje e depois de voce vender e recomprar o primeiro.
     */
    static int proximoSlot(List<WorldGuardBridge.Terreno> ja, String prefixo, String nome) {
        List<Integer> usados = new ArrayList<>();
        for (WorldGuardBridge.Terreno terreno : ja) {
            int slot = slotDoId(terreno.id());
            if (slot > 0) {
                usados.add(slot);
            }
        }
        int candidato = 1;
        while (usados.contains(candidato)) {
            candidato++;
        }
        return candidato;
    }

    /** O numero no fim do id, ou 0 se o id nao tiver esse formato. */
    static int slotDoId(String id) {
        if (id == null) {
            return 0;
        }
        int corte = id.lastIndexOf('_');
        if (corte < 0 || corte == id.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(id.substring(corte + 1));
        } catch (NumberFormatException erro) {
            return 0;
        }
    }

    /**
     * Id da regiao.
     *
     * O WorldGuard so aceita letra, numero e alguns sinais no id; nome de
     * jogador do Minecraft e sempre [A-Za-z0-9_], entao a filtragem aqui e
     * cinto de seguranca para nome vindo de plugin de apelido.
     */
    static String idPara(String prefixo, String nome, int slot) {
        String limpo = nome.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (limpo.isBlank()) {
            limpo = "jogador";
        }
        return prefixo + "_" + limpo + "_" + slot;
    }

    /** Troca <dono> pelo nome. O resto do texto vem do terrenos.yml. */
    static String textoDeFlag(String modelo, String dono) {
        if (modelo == null || modelo.isBlank()) {
            return "";
        }
        return modelo.replace("<dono>", dono);
    }

    static String nomeDe(OfflinePlayer alvo) {
        String nome = alvo.getName();
        if (nome != null && !nome.isBlank()) {
            return nome;
        }
        return alvo.getUniqueId().toString().substring(0, 8);
    }
}
