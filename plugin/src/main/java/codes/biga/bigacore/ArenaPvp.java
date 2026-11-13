package codes.biga.bigacore;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A ARENA PVP: só você e a espada.
 *
 * <h2>Todos têm a mesma espada, então o que decide é o tempo do golpe</h2>
 *
 * Sem kit, sem armadura, sem comida, sem poção: o portal promete "sem kit,
 * sem desculpa" e é isso que o jogo entrega. Tirado o inventário, o que sobra
 * para decidir a luta é ONDE você está e QUANDO você bate — e o quando é a
 * parte que o Minecraft já resolve bem e que quase todo servidor de PvP
 * apaga.
 *
 * <p>A espada do Minecraft tem recarga, e o dano do golpe é
 * {@code 0,2 + p²·0,8}: quem martela o botão bate com a espada descarregada e
 * faz um quinto do dano. {@link ArenaPvpCatalog#forcaDoGolpe} é essa conta, e
 * aqui ela é a conta do jogo. O jogador vê a sua prontidão em número na tela
 * a cada golpe, que é a única maneira de a regra ser aprendível.
 *
 * <h2>Por que a aritmética e não a recusa</h2>
 *
 * O ARCO deste servidor RECUSA o tiro sem puxar, e pensei em fazer igual
 * aqui. Não dá, e o motivo é de interface, não de balanço: o arco tem uma
 * puxada visível e um gesto único, e a espada é um clique reflexo no meio da
 * pancadaria. Clique recusado parece lag — e pior, parece lag do servidor, do
 * tipo que o jogador culpa a internet e não a própria mão. A aritmética
 * castiga mais fundo (um terço do dano) e ensina, porque o número aparece.
 *
 * <h2>A carência é igual ao período, e isso conserta o PvP de vanilla</h2>
 *
 * O Minecraft deixa o alvo invulnerável por 10 tiques depois de um golpe, e a
 * espada recarrega em 12,5. Nessa folga de dois tiques e meio mora o defeito:
 * bater a cada 10 tiques rende 87% do dano de bater no tempo certo, então
 * "clicar rápido" é quase tão bom quanto saber lutar. Aqui a carência é o
 * período inteiro, e bater adiantado cai para 67%.
 * {@link ArenaPvpBuilder#conferirGolpe} recusa qualquer arranjo em que o
 * atalho volte a ser bom.
 *
 * <p>E há uma parte da conta que não é óbvia e que o jogo precisa: TODO golpe
 * reinicia a recarga, inclusive o golpe que a carência comeu. Então quem
 * martela nunca acumula prontidão — não é que ele perca alguns golpes, é que
 * ele bate sempre descarregado. {@link #avisarDaCarencia} diz isso com
 * palavras na tela, porque é a regra que o jogador mais erra e a que ele não
 * tem como descobrir sozinho.
 *
 * <h2>A vida é do plugin, e aqui isso é admissível</h2>
 *
 * Em todas as outras arenas deste servidor o dano de vanilla é cancelado e
 * quem resolve é um acerto, um empurrão ou o relógio — porque vida em jogo
 * com equipamento é decidida por quem tem mais coração e não por quem joga
 * melhor. Nesta arena não há equipamento NENHUM, e aí a vida deixa de ser
 * vantagem e passa a ser um relógio: todos começam iguais, e a vida que
 * sobrou mede o que já aconteceu. É o único jogo daqui onde ela cabe, e
 * cancelar o dano continua valendo — os corações na tela são o mostrador de
 * {@link #vida}, não a vida do Minecraft.
 *
 * <h2>Derrubar devolve vida</h2>
 *
 * Num mata-mata de dezesseis, quem ganha uma briga sai dela com dois
 * corações e é comida de quem assistiu. A jogada certa passa a ser nunca
 * brigar — e um jogo de espada em que não se briga é uma sala de espera.
 * {@code regras.cura-por-eliminacao} devolve um terço, que é o bastante para
 * valer a briga e pouco para o primeiro a matar não limpar a arena sozinho.
 */
public final class ArenaPvp extends JogoDeArena {

    /** Quantos tiques depois de um golpe o aviso de cada segundo volta. */
    private static final long CALMA = 30;

    private final Placar placar = new Placar();

    /** A vida de cada um, em pontos do yml. Os corações são o mostrador. */
    private final Map<UUID, Integer> vida = new HashMap<>();

    /** Até que tique cada um está invulnerável. */
    private final Map<UUID, Long> carencia = new HashMap<>();

    private final Map<UUID, Integer> golpes = new HashMap<>();
    private final Map<UUID, Integer> perdidos = new HashMap<>();
    private final Map<UUID, Integer> danoFeito = new HashMap<>();
    private final Map<UUID, Integer> derrubados = new HashMap<>();
    private final Map<UUID, Double> somaProntidao = new HashMap<>();

    /** Quando cada um deu ou levou o último golpe, para o aviso não piscar. */
    private final Map<UUID, Long> ultimoGolpe = new HashMap<>();

    private boolean gracaAcabou;
    private boolean conferiuOPeriodo;

    public ArenaPvp(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.arenaPvpCatalog();
    }

    private ArenaPvpCatalog planta2() {
        return plugin.arenaPvpCatalog();
    }

    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "LUTE!";
    }

    /**
     * PvP ligado, regeneração desligada.
     *
     * O PvP ligado é o que faz o golpe gerar evento — o dano continua
     * cancelado e quem resolve é {@link #aoBater}. A regeneração desligada é
     * o que impede o Minecraft de curar os corações que este jogo usa como
     * mostrador: com comida cheia, vanilla regenera sozinho e o mostrador
     * passaria a discordar da vida de verdade.
     */
    @Override
    protected void ajustesDoMundo(World mundo) {
        mundo.setPVP(true);
        mundo.setGameRule(GameRule.NATURAL_REGENERATION, false);
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    @Override
    protected void prepararArena(World mundo) {
        placar.zerar();
        vida.clear();
        carencia.clear();
        golpes.clear();
        perdidos.clear();
        danoFeito.clear();
        derrubados.clear();
        somaProntidao.clear();
        ultimoGolpe.clear();
        gracaAcabou = false;
        conferiuOPeriodo = false;
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        ArenaPvpCatalog planta = planta2();
        List<int[]> pontos = planta.largadas(Math.max(2, entram.size()));
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            UUID id = jogador.getUniqueId();
            jogador.teleport(ArenaPvpBuilder.pontoDaLargada(mundo, planta,
                    pontos.get(k % pontos.size())));
            jogador.setFallDistance(0f);
            darAEspada(jogador, planta);
            vida.put(id, planta.vidaCheia());
            golpes.put(id, 0);
            perdidos.put(id, 0);
            danoFeito.put(id, 0);
            derrubados.put(id, 0);
            somaProntidao.put(id, 0.0);
            placar.entrar(id);
            mostrarVida(jogador);
        }
    }

    /**
     * A espada, igual para todos e nada mais.
     *
     * Sem encantamento de propósito: Afiação mudaria o dano e quebraria a
     * conta do yml, e Golpe em Área faria o golpe acertar quem está ao lado —
     * num mata-mata isso premiaria bater no meio da multidão sem mirar em
     * ninguém, que é o oposto do que este jogo pede.
     */
    private void darAEspada(Player jogador, ArenaPvpCatalog planta) {
        jogador.getInventory().clear();
        ItemStack espada = new ItemStack(planta.cor("espada",
                Material.IRON_SWORD));
        ItemMeta marca = espada.getItemMeta();
        if (marca != null) {
            marca.displayName(plugin.mini().deserialize("<color:"
                    + planta.corDoTexto() + ">Espada da Arena</color>"));
            espada.setItemMeta(marca);
        }
        jogador.getInventory().setItem(0, espada);
        jogador.getInventory().setHeldItemSlot(0);
        jogador.setFoodLevel(20);
        jogador.setSaturation(20f);
        jogador.updateInventory();
    }

    @Override
    protected void instrucoes(Player jogador) {
        ArenaPvpCatalog planta = planta2();
        dizer(jogador, "<gray>todos têm a <white>mesma espada</white> e nada mais. "
                + "O golpe vale pela <white>prontidão</white> da espada: "
                + "martelar o botão faz <white>um terço</white> do dano. "
                + "Espere a espada carregar — são <white>"
                + planta.periodoDoGolpe() + " tiques</white> entre "
                + "golpes.</gray>");        dizer(jogador, "<gray>derrubar alguém devolve <white>"
                + planta.curaPorEliminacao() + "</white> de vida, e os "
                + "<white>" + planta.vaos() + " vãos</white> da beira dão no "
                + "vazio.</gray>");
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        ArenaPvpCatalog planta = planta2();
        anunciar("<color:" + planta.corDoTexto() + ">os primeiros "
                + planta.gracaSegundos() + "s a espada não corta</color>");
        conferirOPeriodo(planta);
        mostrarOPainel(mundo, planta);
    }

    /**
     * A espada entregue tem mesmo o período que o yml diz?
     *
     * O número do yml é o eixo de todo o balanço deste jogo, e ele é um
     * número do Minecraft — a velocidade de ataque da espada. Se uma versão
     * do jogo mexer nela, o yml fica velho, a carência deixa de casar com o
     * período e o atalho de "clicar rápido" volta sem nenhum teste falhar.
     * Então a rodada confere o que a espada na mão do jogador diz, e avisa no
     * log. Avisa e não corrige: corrigir em silêncio esconderia a mudança.
     */
    private void conferirOPeriodo(ArenaPvpCatalog planta) {
        if (conferiuOPeriodo) {
            return;
        }
        for (Player jogador : jogadoresVivos()) {
            float real = jogador.getCooldownPeriod();
            conferiuOPeriodo = true;
            if (real <= 0) {
                return;
            }
            if (Math.abs(real - planta.periodoDoGolpe()) > 1.0f) {
                plugin.getLogger().warning("ARENA PVP: a espada entregue "
                        + "recarrega em " + real + " tiques e "
                        + "regras.periodo-do-golpe diz "
                        + planta.periodoDoGolpe() + ". Com os dois números "
                        + "diferentes, a carência deixa de casar com o "
                        + "período e bater adiantado volta a valer. Ajuste o "
                        + "yml para " + Math.ceil(real) + ".");
            }
            return;
        }
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return ArenaPvpBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoEntrarNaArena(Player jogador) {
        devolverAVidaDoMinecraft(jogador);
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
        vida.remove(id);
        carencia.remove(id);
        golpes.remove(id);
        perdidos.remove(id);
        danoFeito.remove(id);
        derrubados.remove(id);
        somaProntidao.remove(id);
        ultimoGolpe.remove(id);
    }

    /**
     * Quem saiu da rodada devolve a espada e volta a ter vida do Minecraft.
     *
     * As contas de golpe, dano e prontidão ficam: são elas que escrevem a
     * legenda da vitória, e quem foi derrubado no primeiro minuto continua
     * tendo feito o dano que fez.
     */
    @Override
    protected void aoEliminarJogador(Player jogador) {
        UUID id = jogador.getUniqueId();
        vida.remove(id);
        carencia.remove(id);
        jogador.getInventory().clear();
        devolverAVidaDoMinecraft(jogador);
    }

    @Override
    protected void aoTerminarRodada() {
        World mundo = mundoDaArena();
        for (Player jogador : jogadoresDaArena()) {
            jogador.getInventory().clear();
            devolverAVidaDoMinecraft(jogador);
        }
        if (mundo != null) {
            ArenaComum.limparPaineis(mundo, ArenaPvpBuilder.TAG_LUTA);
        }
    }

    @Override
    protected boolean rodadaAcabou() {
        if (segundosDeJogo() >= planta2().tempoLimite()) {
            return true;
        }
        return ArenaComum.rodadaAcabou(quantosVivos(), comecouCom(),
                planta2().treinoSozinho());
    }

    @Override
    protected String vencedor() {
        List<Player> vivos = jogadoresVivos();
        if (vivos.size() == 1) {
            return vivos.get(0).getName();
        }
        if (vivos.isEmpty()) {
            return null;
        }
        UUID lider = placar.lider();
        if (lider == null) {
            return null;
        }
        Player quem = Bukkit.getPlayer(lider);
        return quem == null ? "alguém" : quem.getName();
    }

    @Override
    protected String legendaDaVitoria() {
        UUID quem = placar.lider();
        List<Player> vivos = jogadoresVivos();
        if (vivos.size() == 1) {
            quem = vivos.get(0).getUniqueId();
        }
        if (quem == null) {
            return "empate no dano";
        }
        return ficha(quem);
    }

    /** "2 derrubados · 240 de dano · 71% de prontidão" */
    private String ficha(UUID id) {
        int quantos = derrubados.getOrDefault(id, 0);
        int dano = danoFeito.getOrDefault(id, 0);
        return quantos + (quantos == 1 ? " derrubado" : " derrubados") + " · "
                + dano + " de dano · " + Math.round(prontidaoMedia(id) * 100)
                + "% de prontidão";
    }

    /**
     * A prontidão média dos golpes dados, contando os perdidos.
     *
     * Conta os perdidos de propósito: um golpe que a carência comeu é
     * exatamente um golpe fora do tempo, e tirá-lo da média faria o número
     * premiar quem martela — que é quem mais perde golpe.
     */
    private double prontidaoMedia(UUID id) {
        int quantos = golpes.getOrDefault(id, 0) + perdidos.getOrDefault(id, 0);
        if (quantos <= 0) {
            return 0;
        }
        return somaProntidao.getOrDefault(id, 0.0) / quantos;
    }

    @Override
    protected float progressoDaBarra() {
        return ArenaComum.limite(segundosDeJogo()
                / (float) Math.max(1, planta2().tempoLimite()));
    }

    @Override
    protected String textoDaBarra() {
        ArenaPvpCatalog planta = planta2();
        if (!gracaAcabou) {
            return "<color:" + planta.corDoTexto() + ">a espada ainda não "
                    + "corta — " + Math.max(0, planta.gracaSegundos()
                            - segundosDeJogo()) + "s</color>";
        }
        return "<white>" + quantosVivos() + "</white> <gray>de pé · "
                + ArenaComum.tempo(tiquesDeJogo()) + "</gray>";
    }

    // ------------------------------------------------------------------
    //  O golpe
    // ------------------------------------------------------------------

    /**
     * O golpe: a prontidão decide o dano, e a carência decide se ele vale.
     *
     * Prioridade mais baixa e {@code ignoreCancelled = false} porque o dano
     * desta arena está cancelado desde o começo: o evento é o gatilho, não a
     * conta. Mesmo arranjo do bastão do Rei da Montanha e da captura do
     * Esconde.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void aoBater(EntityDamageByEntityEvent evento) {
        if (!(evento.getEntity() instanceof Player alvo)
                || !(evento.getDamager() instanceof Player quemBateu)) {
            return;
        }
        if (!jogando() || !naArena(alvo) || !naArena(quemBateu)) {
            return;
        }
        if (!estaVivo(alvo) || !estaVivo(quemBateu) || alvo.equals(quemBateu)) {
            return;
        }
        ArenaPvpCatalog planta = planta2();
        UUID dono = quemBateu.getUniqueId();
        UUID id = alvo.getUniqueId();

        if (!gracaAcabou) {
            avisarDaGraca(quemBateu, alvo, planta);
            return;
        }

        double prontidao = quemBateu.getAttackCooldown();
        somaProntidao.merge(dono, prontidao, Double::sum);
        ultimoGolpe.put(dono, tiquesDeJogo());
        ultimoGolpe.put(id, tiquesDeJogo());

        if (carencia.getOrDefault(id, 0L) > tiquesDeJogo()) {
            perdidos.merge(dono, 1, Integer::sum);
            avisarDaCarencia(quemBateu, alvo, prontidao);
            return;
        }
        carencia.put(id, tiquesDeJogo() + planta.periodoDoGolpe());
        golpes.merge(dono, 1, Integer::sum);

        int dano = ArenaPvpCatalog.danoDoGolpe(prontidao, planta.danoPorGolpe());
        int sobrou = Math.max(0,
                vida.getOrDefault(id, planta.vidaCheia()) - dano);
        vida.put(id, sobrou);
        danoFeito.merge(dono, dano, Integer::sum);
        placar.somar(dono, dano);

        empurrar(quemBateu, alvo, planta, prontidao);
        avisarDoGolpe(quemBateu, alvo, prontidao, dano, sobrou);
        mostrarVida(alvo);

        if (sobrou <= 0) {
            derrubar(alvo, quemBateu, planta);
        }
    }

    /**
     * O empurrão, dado à mão e multiplicado pela prontidão.
     *
     * À mão porque dano cancelado leva junto o empurrão do Minecraft. Pela
     * prontidão porque, se o golpe fraco empurrasse igual, martelar o botão
     * voltaria a ser jogada — não para fazer dano, mas para jogar alguém num
     * vão. O empurrão é parte do golpe e segue a mesma regra que ele.
     */
    private void empurrar(Player quemBateu, Player alvo,
                          ArenaPvpCatalog planta, double prontidao) {
        Vector direcao = alvo.getLocation().toVector()
                .subtract(quemBateu.getLocation().toVector()).setY(0);
        if (direcao.lengthSquared() < 1.0E-6) {
            direcao = quemBateu.getLocation().getDirection().setY(0);
        }
        if (direcao.lengthSquared() < 1.0E-6) {
            direcao = new Vector(1, 0, 0);
        }
        direcao.normalize().multiply(ArenaPvpCatalog.empurraoDoGolpe(prontidao,
                planta.empurrao()));
        direcao.setY(ArenaPvpCatalog.empurraoDoGolpe(prontidao,
                planta.empurraoAltura()));
        alvo.setVelocity(alvo.getVelocity().multiply(0.4).add(direcao));
        alvo.setFallDistance(0f);
    }

    private void avisarDoGolpe(Player dono, Player alvo, double prontidao,
                               int dano, int sobrou) {
        int parte = (int) Math.round(prontidao * 100);
        String cor = prontidao >= 0.9 ? "#8BE04E"
                : prontidao >= 0.6 ? "#FFD93D" : "#FF8A8A";
        String recado = prontidao >= 0.9 ? "golpe cheio"
                : prontidao >= 0.6 ? "quase no tempo" : "espada descarregada";

        dono.sendActionBar(plugin.mini().deserialize("<color:" + cor + ">"
                + parte + "% · " + recado + "</color> <gray>— tirou <white>"
                + dano + "</white> de " + HubBuilder.escapar(alvo.getName())
                + "</gray>"));
        alvo.sendActionBar(plugin.mini().deserialize("<color:#FF8A8A>-" + dano
                + "</color> <gray>de " + HubBuilder.escapar(dono.getName())
                + " · restam <white>" + sobrou + "</white></gray>"));

        Sound som = prontidao >= 0.9 ? Sound.ENTITY_PLAYER_ATTACK_CRIT
                : prontidao >= 0.6 ? Sound.ENTITY_PLAYER_ATTACK_STRONG
                : Sound.ENTITY_PLAYER_ATTACK_WEAK;
        dono.playSound(dono.getLocation(), som, 1.0f,
                (float) (0.9 + prontidao * 0.3));
        animarGolpe(alvo, dono);
        alvo.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                alvo.getLocation().add(0, 1, 0),
                Math.max(1, (int) Math.round(prontidao * 6)),
                0.3, 0.4, 0.3, 0.05);
        if (prontidao >= 0.9) {
            alvo.getWorld().spawnParticle(Particle.CRIT,
                    alvo.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.2);
        }
    }

    /**
     * O golpe que a carência comeu, e a frase que explica o jogo.
     *
     * Esta é a regra que o jogador mais erra e a que ele não tem como
     * descobrir jogando: todo golpe reinicia a recarga, inclusive este, que
     * não causou dano nenhum. Quem martela não perde alguns golpes — ele bate
     * sempre descarregado, para sempre. Dizer isso em palavras custa uma
     * linha de texto e é a diferença entre um jogo que ensina e um que
     * parece quebrado.
     */
    private void avisarDaCarencia(Player dono, Player alvo, double prontidao) {
        dono.sendActionBar(plugin.mini().deserialize(
                "<color:#FF8A8A>golpe perdido a "
                        + (int) Math.round(prontidao * 100)
                        + "%</color> <gray>— "
                        + HubBuilder.escapar(alvo.getName()) + " ainda está "
                        + "invulnerável, e este golpe <white>reiniciou a sua "
                        + "recarga</white></gray>"));
        dono.playSound(dono.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE,
                0.8f, 0.8f);
    }

    private void avisarDaGraca(Player dono, Player alvo,
                               ArenaPvpCatalog planta) {
        dono.sendActionBar(plugin.mini().deserialize(
                "<color:#FFD93D>a espada ainda não corta</color> <gray>— "
                        + Math.max(0, planta.gracaSegundos()
                                - segundosDeJogo()) + "s</gray>"));
        dono.playSound(dono.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE,
                0.6f, 1.2f);
    }

    /**
     * Derrubado pela espada, e não caído no vazio.
     *
     * Não passa por {@code eliminar} de propósito: ele diz "você caiu" e
     * anuncia "caiu", que é a verdade de quem foi para o vão e a coisa errada
     * de quem perdeu uma luta. Quem morre de espada tem o nome de quem o
     * derrubou na tela — é o que ele quer saber, e é o crédito de quem
     * ganhou.
     */
    private void derrubar(Player alvo, Player dono, ArenaPvpCatalog planta) {
        UUID quem = dono.getUniqueId();
        int quantos = derrubados.merge(quem, 1, Integer::sum);
        placar.somar(quem, planta.pontosPorEliminacao());

        int antes = vida.getOrDefault(quem, planta.vidaCheia());
        int depois = Math.min(planta.vidaCheia(),
                antes + planta.curaPorEliminacao());
        vida.put(quem, depois);
        mostrarVida(dono);

        vivos().remove(alvo.getUniqueId());
        alvo.setFallDistance(0f);
        alvo.setGameMode(GameMode.SPECTATOR);
        alvo.teleport(pontoDaPlateia(alvo.getWorld()));
        aoEliminarJogador(alvo);

        alvo.showTitle(Title.title(
                plugin.mini().deserialize("<color:#FF8A8A>DERRUBADO</color>"),
                plugin.mini().deserialize("<gray>por <white>"
                        + HubBuilder.escapar(dono.getName()) + "</white> · "
                        + ficha(alvo.getUniqueId()) + "</gray>")));
        alvo.playSound(alvo.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);

        dono.sendActionBar(plugin.mini().deserialize("<color:#8BE04E>derrubou "
                + HubBuilder.escapar(alvo.getName()) + "</color> <gray>· "
                + "+<white>" + (depois - antes) + "</white> de vida · "
                + ficha(quem) + "</gray>"));
        dono.playSound(dono.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.7f, 1.5f);
        dono.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                dono.getLocation().add(0, 1, 0), 18, 0.4, 0.6, 0.4, 0.2);

        if (comecouCom() > 1) {
            anunciar("<color:" + planta.corDoTexto() + ">"
                    + HubBuilder.escapar(dono.getName()) + "</color> <gray>"
                    + "derrubou <white>" + HubBuilder.escapar(alvo.getName())
                    + "</white> — faltam <white>" + quantosVivos()
                    + "</white></gray>");
        }
        if (quantos >= 3) {
            anunciar("<color:" + planta.corDoTexto() + ">"
                    + HubBuilder.escapar(dono.getName()) + "</color> <gray>já "
                    + "derrubou <white>" + quantos + "</white></gray>");
        }
        mostrarOPainel(alvo.getWorld(), planta);
    }

    // ------------------------------------------------------------------
    //  Os corações como mostrador
    // ------------------------------------------------------------------

    /**
     * Põe os corações na tela proporcionais à vida do plugin.
     *
     * O jogador não precisa aprender um medidor novo: o que ele já sabe ler é
     * a fileira de corações. Nunca chega a zero — o jogo elimina antes, e
     * coração zerado com dano cancelado seria um jogador de pé que o
     * Minecraft considera morto.
     */
    private void mostrarVida(Player jogador) {
        int cheia = Math.max(1, planta2().vidaCheia());
        int agora = Math.max(0, vida.getOrDefault(jogador.getUniqueId(), cheia));
        double maximo = vidaMaxima(jogador);
        double quanto = maximo * Math.min(cheia, agora) / (double) cheia;
        jogador.setHealth(Math.min(maximo, Math.max(0.5, quanto)));
    }

    private void devolverAVidaDoMinecraft(Player jogador) {
        jogador.setHealth(vidaMaxima(jogador));
        jogador.setFoodLevel(20);
        jogador.setSaturation(20f);
    }

    private double vidaMaxima(Player jogador) {
        AttributeInstance atributo = jogador.getAttribute(Attribute.MAX_HEALTH);
        return atributo == null ? 20.0 : atributo.getValue();
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    @Override
    protected void aCadaTique(World mundo) {
        if (!jogando() || gracaAcabou) {
            return;
        }
        if (segundosDeJogo() >= planta2().gracaSegundos()) {
            gracaAcabou = true;
            tituloEmTodos("<color:#FF8A8A>A ESPADA CORTA</color>",
                    "<gray>espere a recarga entre os golpes</gray>");
            for (Player jogador : jogadoresDaArena()) {
                jogador.playSound(jogador.getLocation(),
                        Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
            }
            mostrarOPainel(mundo, planta2());
        }
    }

    @Override
    protected void aCadaSegundo(World mundo) {
        ArenaPvpCatalog planta = planta2();
        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            // Comida cheia para ninguém perder corrida por fome numa arena
            // em que a fome não é regra nenhuma.
            jogador.setFoodLevel(20);
            jogador.setSaturation(20f);
            if (tiquesDeJogo() - ultimoGolpe.getOrDefault(id, -CALMA) < CALMA) {
                continue;
            }
            int sua = vida.getOrDefault(id, planta.vidaCheia());
            String aviso = gracaAcabou ? ""
                    : " <color:" + planta.corDoTexto() + ">· graça "
                            + Math.max(0, planta.gracaSegundos()
                                    - segundosDeJogo()) + "s</color>";
            jogador.sendActionBar(plugin.mini().deserialize("<gray>vida "
                    + "<white>" + sua + "</white>/" + planta.vidaCheia()
                    + " · " + ficha(id) + " · <white>" + quantosVivos()
                    + "</white> de pé</gray>" + aviso));
        }
    }

    private void mostrarOPainel(World mundo, ArenaPvpCatalog planta) {
        String linha = gracaAcabou
                ? "<gray>espere a espada carregar</gray>"
                : "<color:" + planta.corDoTexto() + ">a espada ainda não "
                        + "corta</color>";
        plugin.arenaPvpBuilder().painelDaLuta(mundo, planta,
                plugin.mini().deserialize("<color:" + planta.corDoTexto()
                        + "><b>" + quantosVivos() + " DE PÉ</b></color>\n"
                        + linha + "\n<gray>martelar faz <white>um terço"
                        + "</white> do dano</gray>"));
    }

    // ------------------------------------------------------------------
    //  Para quem pergunta de fora
    // ------------------------------------------------------------------

    public int vidaDe(UUID id) {
        return vida.getOrDefault(id, 0);
    }

    public int golpesDe(UUID id) {
        return golpes.getOrDefault(id, 0);
    }

    public int golpesPerdidosDe(UUID id) {
        return perdidos.getOrDefault(id, 0);
    }

    public int danoFeitoPor(UUID id) {
        return danoFeito.getOrDefault(id, 0);
    }

    public int derrubadosPor(UUID id) {
        return derrubados.getOrDefault(id, 0);
    }
}
