package codes.biga.bigacore;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O RITMO: pule na batida.
 *
 * <h2>O que o jogo mede</h2>
 *
 * O tique em que o pulo chegou, contra o tique da batida mais perto. Nada
 * mais — não há posição para escolher, não há item, não há mira. É o único
 * dos vinte jogos em que o que se avalia é QUANDO o jogador agiu, e é por
 * isso que ele precisa de uma regra que os outros não precisam: a folga tem
 * de ser fração da batida. A explicação inteira está em {@link MusicaCatalog}.
 *
 * <h2>Duas formas de errar, e as duas custam uma vida</h2>
 *
 * Pular fora da folga cobra na hora: o jogador sabe imediatamente que errou,
 * o que importa num jogo de ritmo — descobrir o erro três batidas depois não
 * ensina nada. E não pular cobra quando a folga da batida fecha, que é o
 * último instante em que ainda daria para acertar.
 *
 * <p>O pulo ruim QUEIMA a batida que ele mirava, e isso não é detalhe: se ele
 * fosse apenas ignorado, segurar a tecla de pulo jogaria sozinho. Quem segura
 * pula a cada doze tiques exatos, sem erro humano, e num andamento em que
 * doze e a batida têm divisor comum esse trilho casa com várias batidas — o
 * jogador aproveitaria essas e não pagaria pelas outras. Com a batida
 * queimada, cada pulo fora da folga custa uma vida, e a tecla presa não
 * atravessa nenhuma faixa. {@link MusicaCatalog#errosDoPuloContinuo} é a
 * conta, e a conferência da obra recusa a escada em que ela falha.
 *
 * <h2>As vidas são o tablado</h2>
 *
 * Cada vida perdida encolhe o tablado um anel, e no fim das vidas ele some e
 * o jogador cai. Um número na tela seria mais simples e só o dono veria;
 * assim, num anel de dezesseis, todo mundo vê quem está por cair — e ver isso
 * é metade da graça de assistir depois de ter caído.
 *
 * <h2>O empate se desfaz pela batida acertada</h2>
 *
 * Se o relógio acabar com vários de pé, ganha quem acertou mais batidas. Não
 * é um critério inventado para o desempate: é a mesma coisa que o jogo pede o
 * tempo todo, só contada.
 */
public final class Musica extends JogoDeArena {

    /** Por quantos tiques o núcleo do metrônomo fica aceso na batida. */
    private static final int PISCA_TIQUES = 2;

    private final Placar placar = new Placar();

    /** Em que tablado cada jogador está. */
    private final Map<UUID, Integer> tabladoDe = new HashMap<>();

    /** Quantas vidas cada um ainda tem. */
    private final Map<UUID, Integer> vidas = new HashMap<>();

    /** A última batida que cada um já resolveu, acertando ou errando. */
    private final Map<UUID, Integer> resolvida = new HashMap<>();

    /** Quantas batidas seguidas cada um acertou, para a tela. */
    private final Map<UUID, Integer> sequencia = new HashMap<>();

    /** A próxima batida a tocar, e o tique em que ela cai. */
    private int proximaBatida;
    private long tiqueDaProxima;

    /** A última batida já cobrada de quem não pulou. */
    private int cobradaAte = -1;

    /** A faixa de andamento que o painel está mostrando. */
    private int faixaNoPainel = -1;

    private long apagarONucleoEm = -1;

    public Musica(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.musicaCatalog();
    }

    private MusicaCatalog planta2() {
        return plugin.musicaCatalog();
    }

    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "NO RITMO!";
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    @Override
    protected void prepararArena(World mundo) {
        placar.zerar();
        tabladoDe.clear();
        vidas.clear();
        resolvida.clear();
        sequencia.clear();
        proximaBatida = 0;
        tiqueDaProxima = 0;
        cobradaAte = -1;
        faixaNoPainel = -1;
        apagarONucleoEm = -1;
        plugin.musicaBuilder().refazerTablados(mundo);
        plugin.musicaBuilder().baterOMetronomo(mundo, false);
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        MusicaCatalog planta = planta2();
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            UUID id = jogador.getUniqueId();
            int tablado = k % planta.tablados();
            tabladoDe.put(id, tablado);
            vidas.put(id, planta.vidas());
            resolvida.put(id, -1);
            sequencia.put(id, 0);
            jogador.teleport(MusicaBuilder.pontoDoTablado(mundo, planta, tablado));
            jogador.setFallDistance(0f);
            jogador.getInventory().clear();
            placar.entrar(id);
        }
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        MusicaCatalog planta = planta2();
        anunciar("<gray>o metrônomo começa em <white>"
                + MusicaCatalog.bpmDe(planta.tempos().get(0))
                + " bpm</white> e sobe até <white>"
                + MusicaCatalog.bpmDe(planta.tempos().get(
                        planta.tempos().size() - 1))
                + " bpm</white>. O primeiro compasso é <white>de graça</white> "
                + "— depois dele, pulo fora da batida tira uma vida</gray>");
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return MusicaBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
        tabladoDe.remove(id);
        vidas.remove(id);
        resolvida.remove(id);
        sequencia.remove(id);
    }

    @Override
    protected void aoTerminarRodada() {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return;
        }
        plugin.musicaBuilder().refazerTablados(mundo);
        plugin.musicaBuilder().baterOMetronomo(mundo, false);
        ArenaComum.limparPaineis(mundo, MusicaBuilder.TAG_RITMO);
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
        UUID lider = placar.lider();
        List<Player> vivos = jogadoresVivos();
        if (vivos.size() == 1) {
            lider = vivos.get(0).getUniqueId();
        }
        if (lider == null) {
            return "empate nas batidas";
        }
        return placar.de(lider) + " batidas certas · até "
                + MusicaCatalog.bpmDe(planta2().tempoDaBatida(
                        Math.max(0, proximaBatida - 1))) + " bpm";
    }

    @Override
    protected float progressoDaBarra() {
        MusicaCatalog planta = planta2();
        int escada = Math.max(1, planta.batidasDaEscada());
        return ArenaComum.limite(Math.min(1f, proximaBatida / (float) escada));
    }

    @Override
    protected String textoDaBarra() {
        MusicaCatalog planta = planta2();
        int batida = Math.max(0, proximaBatida - 1);
        int tempo = planta.tempoDaBatida(batida);
        String aviso = batida < planta.batidasDeAviso()
                ? " <color:#8BE04E>· de graça</color>" : "";
        return "<color:#D89BFF>" + MusicaCatalog.bpmDe(tempo)
                + " bpm</color> <gray>· folga ±" + planta.folgaDaBatida(batida)
                + " tiques · batida " + (batida + 1) + "</gray>" + aviso;
    }

    // ------------------------------------------------------------------
    //  O relógio da música
    // ------------------------------------------------------------------

    @Override
    protected void aCadaTique(World mundo) {
        MusicaCatalog planta = planta2();
        long agora = tiquesDeJogo();

        if (apagarONucleoEm >= 0 && agora >= apagarONucleoEm) {
            plugin.musicaBuilder().baterOMetronomo(mundo, false);
            apagarONucleoEm = -1;
        }
        while (agora >= tiqueDaProxima) {
            bater(mundo, planta, proximaBatida);
            proximaBatida++;
            tiqueDaProxima = planta.tiqueDaBatida(proximaBatida);
        }
        cobrarQuemNaoPulou(planta, agora);
    }

    /** Uma batida: o som, o pisca e a partícula. */
    private void bater(World mundo, MusicaCatalog planta, int batida) {
        int tempo = planta.tempoDaBatida(batida);
        int faixa = planta.faixaDaBatida(batida);
        boolean naCabeca = batida % planta.batidasPorCompasso() == 0;

        plugin.musicaBuilder().baterOMetronomo(mundo, true);
        apagarONucleoEm = tiquesDeJogo() + PISCA_TIQUES;

        List<Double> melodia = planta.melodia();
        float tom = melodia.get(batida % melodia.size()).floatValue();
        Location noMeio = new Location(mundo, 0.5,
                planta.topoY() + planta.metronomoAltura(), 0.5);
        mundo.spawnParticle(Particle.END_ROD, noMeio, naCabeca ? 30 : 10,
                0.4, 0.4, 0.4, 0.04);

        for (Player jogador : jogadoresVivos()) {
            Location ouvido = jogador.getLocation();
            jogador.playSound(ouvido, naCabeca
                            ? Sound.BLOCK_NOTE_BLOCK_BASEDRUM
                            : Sound.BLOCK_NOTE_BLOCK_HAT,
                    naCabeca ? 1.0f : 0.6f, 1.0f);
            jogador.playSound(ouvido, Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, tom);
        }

        if (faixa != faixaNoPainel) {
            faixaNoPainel = faixa;
            mostrarAFaixa(mundo, planta, batida, tempo, faixa);
        }
    }

    /** Avisa que o andamento subiu, e refaz o painel do metrônomo. */
    private void mostrarAFaixa(World mundo, MusicaCatalog planta, int batida,
                               int tempo, int faixa) {
        plugin.musicaBuilder().painelDoRitmo(mundo, planta,
                plugin.mini().deserialize("<color:#D89BFF><b>"
                        + MusicaCatalog.bpmDe(tempo) + " bpm</b></color>\n"
                        + "<gray>folga ±" + planta.folgaDaFaixa(faixa)
                        + " tiques · faixa " + (faixa + 1) + " de "
                        + planta.tempos().size() + "</gray>"));
        if (batida == 0) {
            return;
        }
        tituloEmTodos("<color:#D89BFF>" + MusicaCatalog.bpmDe(tempo)
                + " bpm</color>", "<white>o andamento subiu</white>");
        anunciar("<color:#D89BFF>" + MusicaCatalog.bpmDe(tempo)
                + " bpm</color> <gray>— a folga agora é de <white>±"
                + planta.folgaDaFaixa(faixa) + " tiques</white></gray>");
        for (Player jogador : jogadoresVivos()) {
            jogador.playSound(jogador.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
        }
    }

    /**
     * Cobra de quem deixou a batida passar sem pular.
     *
     * Cobra quando a folga FECHA, e não na batida seguinte: o último instante
     * em que ainda daria para acertar é o fim da folga, e punir antes disso
     * seria punir um pulo que ainda ia chegar na hora.
     */
    private void cobrarQuemNaoPulou(MusicaCatalog planta, long agora) {
        int alvo = cobradaAte + 1;
        while (alvo < proximaBatida) {
            long fechaEm = planta.tiqueDaBatida(alvo)
                    + planta.folgaDaBatida(alvo) + 1;
            if (agora < fechaEm) {
                return;
            }
            if (alvo >= planta.batidasDeAviso()) {
                for (Player jogador : new ArrayList<>(jogadoresVivos())) {
                    UUID id = jogador.getUniqueId();
                    if (resolvida.getOrDefault(id, -1) >= alvo) {
                        continue;
                    }
                    resolvida.put(id, alvo);
                    tirarUmaVida(jogador, "perdeu a batida");
                }
            }
            cobradaAte = alvo;
            alvo++;
        }
    }

    // ------------------------------------------------------------------
    //  O pulo
    // ------------------------------------------------------------------

    @EventHandler
    public void aoPular(PlayerJumpEvent evento) {
        Player jogador = evento.getPlayer();
        if (!jogando() || !estaVivo(jogador)) {
            return;
        }
        MusicaCatalog planta = planta2();
        UUID id = jogador.getUniqueId();
        long agora = tiquesDeJogo();

        int[] perto = MusicaCatalog.batidaMaisPerto(agora, planta.tempos(),
                planta.batidasPorTempo());
        int batida = perto[0];
        int desvio = perto[1];
        int folga = planta.folgaDaBatida(batida);

        if (batida < planta.batidasDeAviso()) {
            // Compasso de aviso: o pulo não conta nem a favor nem contra.
            return;
        }
        if (resolvida.getOrDefault(id, -1) >= batida) {
            // A batida já foi resolvida: o segundo pulo em cima dela não
            // acerta de novo e também não castiga. Isso só é seguro porque a
            // batida ruim QUEIMA a batida que mirava, logo abaixo — se o pulo
            // fora da folga fosse apenas ignorado, quem martela a tecla
            // aproveitaria as batidas em que o trilho de doze tiques casa e
            // não pagaria pelas outras.
            return;
        }
        resolvida.put(id, batida);

        if (Math.abs(desvio) <= folga) {
            placar.somar(id, planta.pontosNaBatida());
            int seguidas = sequencia.merge(id, 1, Integer::sum);
            jogador.playSound(jogador.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f,
                    desvio == 0 ? 2.0f : 1.5f);
            jogador.sendActionBar(plugin.mini().deserialize(
                    (desvio == 0 ? "<color:#8BE04E>NA MOSCA</color>"
                            : "<color:#8BE04E>na batida</color> <gray>"
                            + (desvio < 0 ? "adiantado " : "atrasado ")
                            + Math.abs(desvio) + "</gray>")
                            + " <gray>· " + seguidas + " seguidas · "
                            + vidas.getOrDefault(id, 0) + " vidas</gray>"));
            return;
        }
        sequencia.put(id, 0);
        tirarUmaVida(jogador, desvio < 0 ? "pulou adiantado " + (-desvio)
                : "pulou atrasado " + desvio);
    }

    /** Tira uma vida, encolhe o tablado, e derruba quem acabou. */
    private void tirarUmaVida(Player jogador, String porque) {
        MusicaCatalog planta = planta2();
        UUID id = jogador.getUniqueId();
        int sobrou = Math.max(0, vidas.getOrDefault(id, 0) - 1);
        vidas.put(id, sobrou);
        sequencia.put(id, 0);

        World mundo = jogador.getWorld();
        Integer tablado = tabladoDe.get(id);
        if (tablado != null) {
            if (sobrou <= 0) {
                plugin.musicaBuilder().apagarTablado(mundo, tablado);
            } else {
                plugin.musicaBuilder().encolherTablado(mundo, tablado, sobrou);
            }
        }

        jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS,
                0.8f, 0.5f);
        if (sobrou <= 0) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF6B6B>" + porque + " — acabou o tablado</color>"));
            // Não elimina aqui: o tablado sumiu e a queda faz isso, e a queda
            // é o que os outros quinze veem.
            return;
        }
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:#FF6B6B>" + porque + "</color> <gray>· " + sobrou
                        + (sobrou == 1 ? " vida" : " vidas") + "</gray>"));
    }

    @Override
    protected void aCadaSegundo(World mundo) {
        if (placar.vazio()) {
            return;
        }
        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            if (sequencia.getOrDefault(id, 0) >= 8) {
                mundo.spawnParticle(Particle.NOTE, jogador.getLocation()
                        .add(0, 2.2, 0), 4, 0.4, 0.2, 0.4, 1.0);
            }
        }
    }
}
