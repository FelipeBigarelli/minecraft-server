package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * O Quiz: quem sabe, responde.
 *
 * <h2>Três regras contra copiar o vizinho</h2>
 *
 * É o defeito do gênero, e nenhum plugin de quiz o resolve: num jogo em que a
 * resposta é um lugar no chão, o décimo sexto jogador não precisa saber nada —
 * basta olhar onde o primeiro pisou. As três regras daqui existem só por
 * causa disso, e juntas elas fecham o buraco:
 *
 * <ul>
 *   <li><b>Não responder é errar.</b> Sem isso o átrio seria o lugar seguro e
 *       dava para esperar a revelação parado no meio.</li>
 *   <li><b>A resposta tranca no primeiro piso.</b> Sem isso a disputa seria
 *       sobre quem pisa por último, e o jogo viraria quinze pessoas olhando os
 *       pés de uma.</li>
 *   <li><b>Rapidez vale ponto.</b> A eliminação decide quem FICA; o ponto
 *       decide quem GANHA quando mais de um chega ao fim. Quem espera para
 *       copiar sobrevive e perde no ponto, que é exatamente o preço justo —
 *       não se proíbe copiar, se cobra por isso.</li>
 * </ul>
 *
 * <h2>A pergunta que ninguém acerta não vale</h2>
 *
 * Com dezesseis vivos e uma pergunta que ninguém sabe, a regra crua elimina
 * os dezesseis de uma vez e a partida acaba sem vencedor em quinze segundos.
 * Então quando a resposta certa fica vazia a pergunta é anulada: nenhum
 * tablado some, ninguém cai, e a rodada segue. É a válvula que permite ter
 * perguntas difíceis no banco sem elas poderem acabar com a partida.
 *
 * <h2>Quem acertou não cai</h2>
 *
 * A resposta tranca no primeiro tablado pisado, e por isso dá para acertar e
 * depois sair andando por um tablado errado. Quando os tablados errados somem,
 * esse jogador cai — tendo acertado. {@link #caiuFora} o resgata para o átrio
 * em vez de eliminá-lo: a queda é o espetáculo da resposta errada, não a
 * regra, e a regra é a resposta trancada.
 */
public final class Quiz extends JogoDeArena {

    private final Placar placar = new Placar();
    private final Random sorte = new Random();

    /** Quantas perguntas cada um acertou, para a legenda da vitória. */
    private final Map<UUID, Integer> acertos = new HashMap<>();

    /** Em que tablado cada um trancou a resposta desta pergunta. */
    private final Map<UUID, Integer> resposta = new HashMap<>();

    /** Em que tique cada um trancou, para a conta da rapidez. */
    private final Map<UUID, Long> quandoTrancou = new HashMap<>();

    /** Quem errou a pergunta de agora e sai quando a queda terminar. */
    private final Set<UUID> condenados = new HashSet<>();

    /**
     * O baralho, que atravessa as rodadas de propósito.
     *
     * Reembaralhar a cada rodada daria a mesma pergunta na rodada seguinte com
     * frequência alta, e a graça de um quiz é justamente ele não repetir
     * enquanto houver banco.
     */
    private QuizCatalog.Baralho baralho;
    private int bancoDoBaralho = -1;

    private List<QuizCatalog.Pergunta> rodada = List.of();
    private int atual = -1;
    private long tiqueDaPergunta;

    private boolean revelando;
    private long tiqueDaRevelacao;
    private boolean quedaFeita;
    private boolean anulada;

    public Quiz(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.quizCatalog();
    }

    private QuizCatalog planta2() {
        return plugin.quizCatalog();
    }

    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    /** A pergunta aberta agora, ou nula fora de uma. */
    private QuizCatalog.Pergunta perguntaDeAgora() {
        return atual >= 0 && atual < rodada.size() ? rodada.get(atual) : null;
    }

    @Override
    protected String verboDaLargada() {
        return "RESPONDA!";
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    /** A lista lateral mostra o placar deste jogo. */
    @Override
    protected Placar placarDaLateral() {
        return placar;
    }

    @Override
    protected void prepararArena(World mundo) {
        QuizCatalog planta = planta2();
        placar.zerar();
        acertos.clear();
        resposta.clear();
        quandoTrancou.clear();
        condenados.clear();
        revelando = false;
        quedaFeita = false;
        anulada = false;
        atual = -1;

        if (baralho == null || bancoDoBaralho != planta.banco().size()) {
            baralho = new QuizCatalog.Baralho(planta.banco(), sorte);
            bancoDoBaralho = planta.banco().size();
        }
        rodada = baralho.rodada(planta.porNivel());
        plugin.quizBuilder().refazerTablados(mundo);
        plugin.quizBuilder().placasDosTablados(mundo, planta, null);
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        QuizCatalog planta = planta2();
        List<int[]> pontos = planta.largadas(entram.size());
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            jogador.teleport(QuizBuilder.pontoDaLargada(mundo, planta,
                    pontos.get(k % pontos.size())));
            jogador.setFallDistance(0f);
            jogador.getInventory().clear();
            placar.entrar(jogador.getUniqueId());
        }
    }

    @Override
    protected void instrucoes(Player jogador) {
        QuizCatalog planta = planta2();
        dizer(jogador, "<gray>são <white>" + planta.perguntasPorRodada() + " perguntas</white>, do "
                + "fácil ao difícil. <white>" + planta.tempoResposta()
                + " segundos</white> para pisar na resposta — e ficar no átrio "
                + "conta como <white>errar</white></gray>");
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        QuizCatalog planta = planta2();
        proximaPergunta(mundo);
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return QuizBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
        acertos.remove(id);
        resposta.remove(id);
        quandoTrancou.remove(id);
        condenados.remove(id);
    }

    @Override
    protected void aoTerminarRodada() {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return;
        }
        plugin.quizBuilder().refazerTablados(mundo);
        plugin.quizBuilder().placasDosTablados(mundo, planta2(), null);
        ArenaComum.limparPaineis(mundo, QuizBuilder.TAG_PERGUNTA);
    }

    @Override
    protected boolean rodadaAcabou() {
        if (segundosDeJogo() >= planta2().tempoLimite()) {
            return true;
        }
        if (atual >= rodada.size()) {
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
        // Mais de um chegou ao fim das perguntas: decide o ponto, e é aqui
        // que a rapidez cobra de quem esperou para copiar.
        UUID lider = placar.lider();
        if (lider == null) {
            return null;
        }
        Player quem = Bukkit.getPlayer(lider);
        return quem == null ? "alguém" : quem.getName();
    }

    @Override
    protected String legendaDaVitoria() {
        List<Player> vivos = jogadoresVivos();
        if (vivos.size() == 1) {
            UUID id = vivos.get(0).getUniqueId();
            return acertos.getOrDefault(id, 0) + " de " + Math.max(1, atual)
                    + " acertos · último de pé";
        }
        UUID lider = placar.lider();
        if (lider == null) {
            return "empate";
        }
        return acertos.getOrDefault(lider, 0) + " acertos · "
                + placar.de(lider) + " pontos";
    }

    @Override
    protected float progressoDaBarra() {
        QuizCatalog planta = planta2();
        if (revelando) {
            return 0f;
        }
        long janela = planta.tempoResposta() * 20L;
        long usado = tiquesDeJogo() - tiqueDaPergunta;
        return ArenaComum.limite(1f - usado / (float) Math.max(1, janela));
    }

    @Override
    protected String textoDaBarra() {
        QuizCatalog.Pergunta pergunta = perguntaDeAgora();
        if (pergunta == null) {
            return "<white>preparando</white>";
        }
        String conta = "<gray>" + (atual + 1) + "/" + rodada.size() + " · </gray>";
        if (revelando) {
            if (anulada) {
                return conta + "<color:#FFD93D>ninguém acertou — a pergunta "
                        + "não vale</color>";
            }
            return conta + "<white>" + QuizCatalog.LETRAS[pergunta.certa()]
                    + "</white> <gray>·</gray> <color:"
                    + QuizCatalog.corDaLetra(pergunta.certa()) + ">"
                    + HubBuilder.escapar(pergunta.resposta()) + "</color>";
        }
        long resta = Math.max(0, planta2().tempoResposta() * 20L
                - (tiquesDeJogo() - tiqueDaPergunta));
        return conta + "<white>" + HubBuilder.escapar(pergunta.texto())
                + "</white> <gray>· " + (resta / 20 + 1) + "s</gray>";
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    @Override
    protected void aCadaTique(World mundo) {
        QuizCatalog planta = planta2();
        if (perguntaDeAgora() == null) {
            return;
        }
        if (revelando) {
            long desde = tiquesDeJogo() - tiqueDaRevelacao;
            if (!quedaFeita && desde >= planta.quedaTiques()) {
                quedaFeita = true;
                levarOsCondenados();
            }
            if (desde >= planta.tempoRevelacao() * 20L) {
                proximaPergunta(mundo);
            }
            return;
        }
        trancarRespostas(planta);
        if (tiquesDeJogo() - tiqueDaPergunta >= planta.tempoResposta() * 20L) {
            revelar(mundo, planta);
        }
    }

    /**
     * Tranca a resposta de quem pisou num tablado.
     *
     * A primeira pisada vale, e as seguintes não: é o que torna copiar caro em
     * vez de impossível. Quem corre para o tablado errado e se arrepende já
     * respondeu, e quem espera para ver o vizinho responde tarde e perde no
     * ponto da rapidez.
     */
    private void trancarRespostas(QuizCatalog planta) {
        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            if (resposta.containsKey(id)) {
                continue;
            }
            Location onde = jogador.getLocation();
            int tablado = planta.tabladoDoPonto(onde.getX(), onde.getZ());
            if (tablado < 0) {
                continue;
            }
            resposta.put(id, tablado);
            quandoTrancou.put(id, tiquesDeJogo());
            jogador.playSound(onde, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.6f);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:" + QuizCatalog.corDaLetra(tablado) + ">"
                            + planta.letraDo(tablado) + "</color> <gray>— "
                            + "trancado</gray>"));
        }
    }

    /** O fim da janela: diz a resposta, apaga os tablados errados. */
    private void revelar(World mundo, QuizCatalog planta) {
        QuizCatalog.Pergunta pergunta = perguntaDeAgora();
        if (pergunta == null) {
            return;
        }
        revelando = true;
        tiqueDaRevelacao = tiquesDeJogo();
        quedaFeita = false;
        condenados.clear();

        List<Player> certos = new ArrayList<>();
        List<Player> errados = new ArrayList<>();
        for (Player jogador : jogadoresVivos()) {
            Integer escolha = resposta.get(jogador.getUniqueId());
            if (escolha != null && escolha == pergunta.certa()) {
                certos.add(jogador);
            } else {
                errados.add(jogador);
            }
        }

        anulada = certos.isEmpty() && !errados.isEmpty();
        if (anulada) {
            tituloEmTodos("<color:#FFD93D>NINGUÉM ACERTOU</color>",
                    "<white>" + planta.letraDo(pergunta.certa()) + " — "
                            + HubBuilder.escapar(pergunta.resposta())
                            + " · a pergunta não vale</white>");
            anunciar("<color:#FFD93D>ninguém acertou</color> <gray>— era "
                    + "<white>" + HubBuilder.escapar(pergunta.resposta())
                    + "</white>. A pergunta <white>não vale</white> e todos "
                    + "seguem na rodada</gray>");
            painelDaRevelacao(mundo, planta, pergunta);
            return;
        }

        for (Player jogador : certos) {
            UUID id = jogador.getUniqueId();
            placar.somar(id, pontosDe(planta, id));
            acertos.merge(id, 1, Integer::sum);
            jogador.playSound(jogador.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        }
        for (Player jogador : errados) {
            condenados.add(jogador.getUniqueId());
            jogador.playSound(jogador.getLocation(),
                    Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.7f);
        }

        for (int k = 0; k < planta.tablados(); k++) {
            if (k == pergunta.certa()) {
                mundo.spawnParticle(Particle.HAPPY_VILLAGER,
                        QuizBuilder.pontoDoTablado(mundo, planta, k), 40,
                        3.0, 0.6, 3.0, 0.01);
                continue;
            }
            plugin.quizBuilder().apagarTablado(mundo, k);
        }

        tituloEmTodos("<color:" + QuizCatalog.corDaLetra(pergunta.certa()) + ">"
                        + planta.letraDo(pergunta.certa()) + "</color>",
                "<white>" + HubBuilder.escapar(pergunta.resposta()) + "</white>");
        painelDaRevelacao(mundo, planta, pergunta);
        if (!errados.isEmpty()) {
            anunciar("<gray>era <white>"
                    + HubBuilder.escapar(pergunta.resposta()) + "</white> — "
                    + "<white>" + certos.size() + "</white> acertaram e "
                    + "<color:#FF6B6B>" + errados.size() + "</color> caem</gray>");
        }
    }

    /**
     * Quantos pontos um acerto vale: o fixo mais a rapidez.
     *
     * A parte da rapidez é o que cobra de quem esperou para ver onde o vizinho
     * pisou. Ela não elimina ninguém — quem copia sobrevive à pergunta — mas é
     * ela que decide a partida quando mais de um chega ao fim.
     */
    private int pontosDe(QuizCatalog planta, UUID id) {
        long janela = planta.tempoResposta() * 20L;
        long gasto = Math.max(0, Math.min(janela,
                quandoTrancou.getOrDefault(id, tiqueDaPergunta + janela)
                        - tiqueDaPergunta));
        double sobrou = 1.0 - gasto / (double) Math.max(1, janela);
        return planta.pontosAcerto()
                + (int) Math.round(planta.pontosRapidez() * sobrou);
    }

    /** Tira da rodada quem errou, caído ou não. */
    private void levarOsCondenados() {
        for (UUID id : new ArrayList<>(condenados)) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador != null && estaVivo(jogador)) {
                eliminar(jogador);
            }
        }
        condenados.clear();
    }

    /** Abre a pergunta seguinte, ou deixa a rodada acabar. */
    private void proximaPergunta(World mundo) {
        QuizCatalog planta = planta2();
        revelando = false;
        anulada = false;
        quedaFeita = false;
        resposta.clear();
        quandoTrancou.clear();
        condenados.clear();
        plugin.quizBuilder().refazerTablados(mundo);

        atual++;
        QuizCatalog.Pergunta pergunta = perguntaDeAgora();
        if (pergunta == null) {
            return;
        }
        tiqueDaPergunta = tiquesDeJogo();

        plugin.quizBuilder().placasDosTablados(mundo, planta, pergunta);
        List<Component> nosTablados = new ArrayList<>();
        for (int k = 0; k < planta.tablados(); k++) {
            String opcao = k < pergunta.opcoes().size()
                    ? pergunta.opcoes().get(k) : "";
            nosTablados.add(plugin.mini().deserialize(
                    "<color:" + QuizCatalog.corDaLetra(k) + "><b>"
                            + planta.letraDo(k) + "</b></color>\n<white>"
                            + HubBuilder.escapar(opcao) + "</white>"));
        }
        plugin.quizBuilder().painelDaPergunta(mundo, planta,
                plugin.mini().deserialize("<gray>pergunta " + (atual + 1) + "/"
                        + rodada.size() + " · nível " + pergunta.nivel()
                        + "</gray>\n<white>"
                        + HubBuilder.escapar(pergunta.texto()) + "</white>"),
                nosTablados);

        tituloEmTodos("", "<white>" + HubBuilder.escapar(pergunta.texto())
                + "</white>");
        for (Player jogador : jogadoresVivos()) {
            jogador.playSound(jogador.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.2f);
        }
    }

    /** Troca o painel do átrio pela resposta, deixando as opções no lugar. */
    private void painelDaRevelacao(World mundo, QuizCatalog planta,
                                   QuizCatalog.Pergunta pergunta) {
        List<Component> nosTablados = new ArrayList<>();
        for (int k = 0; k < planta.tablados(); k++) {
            String opcao = k < pergunta.opcoes().size()
                    ? pergunta.opcoes().get(k) : "";
            boolean certa = k == pergunta.certa();
            nosTablados.add(plugin.mini().deserialize(
                    (certa ? "<color:#8BE04E><b>✔ " : "<color:#8A8A8A>")
                            + planta.letraDo(k)
                            + (certa ? "</b></color>" : "</color>")
                            + "\n" + (certa ? "<white>" : "<gray>")
                            + HubBuilder.escapar(opcao)
                            + (certa ? "</white>" : "</gray>")));
        }
        plugin.quizBuilder().painelDaPergunta(mundo, planta,
                plugin.mini().deserialize((anulada
                        ? "<color:#FFD93D>a pergunta não vale</color>"
                        : "<gray>a resposta era</gray>") + "\n<color:"
                        + QuizCatalog.corDaLetra(pergunta.certa()) + ">"
                        + planta.letraDo(pergunta.certa()) + " — "
                        + HubBuilder.escapar(pergunta.resposta()) + "</color>"),
                nosTablados);
    }

    @Override
    protected void aCadaSegundo(World mundo) {
        if (revelando) {
            return;
        }
        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            Integer escolha = resposta.get(id);
            String trancado = escolha == null
                    ? "<color:#FF6B6B>sem resposta</color>"
                    : "<color:" + QuizCatalog.corDaLetra(escolha) + ">"
                    + planta2().letraDo(escolha) + " trancado</color>";
            jogador.sendActionBar(plugin.mini().deserialize(trancado
                    + " <gray>· " + placar.de(id) + " pts · "
                    + placar.posicaoDe(id) + "º de " + placar.quantos()
                    + "</gray>"));
        }
    }

    /**
     * Quem acertou não cai.
     *
     * A resposta tranca no primeiro tablado pisado, então dá para acertar e
     * sair andando por cima de um tablado errado — e quando ele some, quem
     * acertou desce junto. Em vez de eliminar, devolve ao átrio: o tablado
     * sumindo é o espetáculo da resposta errada, e a regra é a resposta
     * trancada.
     */
    @Override
    protected boolean caiuFora(Player jogador) {
        if (jogador.getLocation().getY() >= planta2().quedaY()) {
            return false;
        }
        if (revelando && !condenados.contains(jogador.getUniqueId())) {
            World mundo = jogador.getWorld();
            jogador.teleport(new Location(mundo, 0.5, planta2().topoY() + 1, 0.5,
                    jogador.getLocation().getYaw(), 0f));
            jogador.setFallDistance(0f);
            return false;
        }
        return true;
    }
}
