package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * A SORTE: o dado escolhe, e você escolhe onde estar quando ele cair.
 *
 * <h2>Um jogo de sorte que não é moeda ao ar</h2>
 *
 * O dado é justo — toda fatia é 1 em 6, e nenhuma leitura da arena diz o
 * contrário. O que o jogador decide são duas coisas, e nenhuma delas é
 * adivinhar o dado:
 *
 * <ul>
 *   <li><b>Onde os outros não estão.</b> O prêmio da rodada é dividido por
 *       quantos escolheram a mesma fatia, por divisão inteira e sem piso: com
 *       dezesseis numa fatia de prêmio oito, cada um leva ZERO. A fatia vazia
 *       paga tudo e corre o mesmo risco, então amontoar-se não é prudência,
 *       é desperdício — e isso é o jogo inteiro, porque obriga a olhar para
 *       os outros quinze em vez de para o dado.</li>
 *   <li><b>Quando parar.</b> O banco do meio guarda o que está em mão e tira
 *       o jogador da partida. Cair não guarda nada. A conta de quando sair
 *       está em {@link SorteCatalog#evDeSair}, e a tabela de prêmios é
 *       escolhida para que o topo dela caia no MEIO da partida — perto o
 *       bastante do fim para a decisão ser apertada, longe o bastante para
 *       não ser automática.</li>
 * </ul>
 *
 * <h2>Entrar no banco é uma porta só de ida</h2>
 *
 * Quem pisa no banco guarda na hora e sai da rodada. Não é rigidez: se dar
 * para entrar e sair, a jogada certa é ficar na beira do banco e entrar nele
 * no instante do dado, e aí ninguém arrisca nada nunca. O patamar fica um
 * degrau acima do disco justamente para que entrar nele seja um ato — um
 * pulo, e não um passo distraído.
 *
 * <h2>Quem está de pé no fim guarda sozinho</h2>
 *
 * Quando a última rodada passa, ou quando o relógio acaba, todo mundo que
 * ainda está de pé guarda o que tem em mão. Sem isso, o jogador corajoso que
 * sobrou sozinho perderia tudo porque os outros saíram antes dele — seria
 * castigo por bravura, que é o contrário do que este jogo quer pagar.
 */
public final class Sorte extends JogoDeArena {

    /** Em que parte da rodada o jogo está. */
    private enum Fase { ESCOLHA, REVELACAO }

    /** O placar GUARDADO: é ele que decide o vencedor. */
    private final Placar placar = new Placar();

    /** O que cada um tem em mão, e ainda pode perder. */
    private final Map<UUID, Integer> emMao = new HashMap<>();

    private final Random dado = new Random();

    private int rodada;
    private Fase fase = Fase.ESCOLHA;
    private long tiqueDaFase;

    /** A fatia que o dado tirou, ou -1 fora da revelação. */
    private int sorteada = -1;

    public Sorte(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.sorteCatalog();
    }

    private SorteCatalog planta2() {
        return plugin.sorteCatalog();
    }

    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "ESCOLHA!";
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    @Override
    protected void prepararArena(World mundo) {
        placar.zerar();
        emMao.clear();
        rodada = 0;
        fase = Fase.ESCOLHA;
        tiqueDaFase = 0;
        sorteada = -1;
        plugin.sorteBuilder().refazerFatias(mundo);
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        SorteCatalog planta = planta2();
        List<int[]> onde = planta.largadas(Math.max(2, entram.size()));
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            UUID id = jogador.getUniqueId();
            emMao.put(id, 0);
            placar.entrar(id);
            jogador.teleport(SorteBuilder.pontoDaLargada(mundo, planta,
                    onde.get(k % onde.size())));
            jogador.setFallDistance(0f);
            jogador.getInventory().clear();
        }
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        SorteCatalog planta = planta2();
        tiqueDaFase = planta.tempoEscolha() * 20L;
        anunciar("<gray>o dado é <white>justo</white>: toda fatia é <white>1 em "
                + planta.setores() + "</white>. O prêmio da rodada é "
                + "<white>dividido</white> por quantos escolherem a mesma "
                + "fatia — a cheia paga <white>zero</white>. Pular no "
                + "<color:#E8A87C>banco do meio</color> guarda o que você tem "
                + "e te tira da partida; <white>cair não guarda nada</white>."
                + "</gray>");
        anunciarARodada(mundo, planta);
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return SorteBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoEliminarJogador(Player jogador) {
        // Caiu: o que estava em mão se perde. O que já estava guardado fica.
        emMao.put(jogador.getUniqueId(), 0);
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
        emMao.remove(id);
    }

    @Override
    protected void aoTerminarRodada() {
        World mundo = mundoDaArena();
        // Quem está de pé no fim guarda sozinho: sem isto, quem sobrou por
        // último perderia tudo só porque os outros saíram antes.
        for (Player jogador : new ArrayList<>(jogadoresVivos())) {
            guardar(jogador, "o jogo acabou de pé");
        }
        if (mundo != null) {
            plugin.sorteBuilder().refazerFatias(mundo);
            ArenaComum.limparPaineis(mundo, SorteBuilder.TAG_DADO);
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
        UUID lider = placar.lider();
        if (lider == null) {
            return null;
        }
        Player quem = Bukkit.getPlayer(lider);
        return quem == null ? "alguém" : quem.getName();
    }

    @Override
    protected String legendaDaVitoria() {
        return placar.pontosDoLider() + " pontos guardados · aguentou até a "
                + "rodada " + Math.min(rodada + 1, planta2().rodadas())
                + " de " + planta2().rodadas();
    }

    @Override
    protected float progressoDaBarra() {
        SorteCatalog planta = planta2();
        return ArenaComum.limite(rodada / (float) Math.max(1, planta.rodadas()));
    }

    @Override
    protected String textoDaBarra() {
        SorteCatalog planta = planta2();
        if (rodada >= planta.rodadas()) {
            return "<color:#E8A87C>acabaram as rodadas</color>";
        }
        String agora = fase == Fase.ESCOLHA
                ? "<white>escolha</white>"
                : (sorteada < 0 ? "<white>o dado rola</white>"
                        : "<color:#FF6B6B>saiu a face " + (sorteada + 1)
                        + "</color>");
        return "<color:#E8A87C>rodada " + (rodada + 1) + " de "
                + planta.rodadas() + "</color> <gray>· prêmio <white>"
                + planta.premioDaRodada(rodada) + "</white> · </gray>" + agora;
    }

    // ------------------------------------------------------------------
    //  O relógio
    // ------------------------------------------------------------------

    @Override
    protected void aCadaTique(World mundo) {
        if (!jogando()) {
            return;
        }
        SorteCatalog planta = planta2();
        long agora = tiquesDeJogo();

        guardarQuemEntrouNoBanco(planta);
        if (rodada >= planta.rodadas() || agora < tiqueDaFase) {
            return;
        }
        if (fase == Fase.ESCOLHA) {
            rolarODado(mundo, planta, agora);
        } else {
            virarARodada(mundo, planta, agora);
        }
    }

    /**
     * Quem pisou no banco guarda na hora.
     *
     * Por tique, e não só no instante do dado: o jogador tem de poder sair
     * quando quiser, e saber que saiu no momento em que pulou. Fazer a
     * varredura só na hora do dado deixaria o jogador parado no banco sem
     * saber se está dentro ou fora da rodada.
     */
    private void guardarQuemEntrouNoBanco(SorteCatalog planta) {
        for (Player jogador : new ArrayList<>(jogadoresVivos())) {
            Location onde = jogador.getLocation();
            if (planta.setorDoPonto(onde.getX(), onde.getZ())
                    == SorteCatalog.BANCO) {
                guardar(jogador, "guardou no banco");
            }
        }
    }

    /** O dado: sorteia a fatia, apaga ela, e paga as outras. */
    private void rolarODado(World mundo, SorteCatalog planta, long agora) {
        int setores = planta.setores();
        int premio = planta.premioDaRodada(rodada);

        Map<UUID, Integer> onde = new HashMap<>();
        int[] quantos = new int[setores];
        for (Player jogador : jogadoresVivos()) {
            Location pe = jogador.getLocation();
            int setor = planta.setorDoPonto(pe.getX(), pe.getZ());
            onde.put(jogador.getUniqueId(), setor);
            if (setor >= 0) {
                quantos[setor]++;
            }
        }

        sorteada = dado.nextInt(setores);

        // A FASE VIRA ANTES DO TRABALHO, e isto não é estilo.
        //
        // Virar no fim deixava o método inteiro — apagar a fatia, varrer os
        // jogadores, pagar cada um — entre o sorteio e a troca de fase. Uma
        // exceção em qualquer ponto daquele miolo abortaria o método com
        // 'fase' ainda em ESCOLHA e 'tiqueDaFase' no passado: o tique
        // seguinte sortearia OUTRA fatia e a apagaria também. Em seis tiques
        // o disco inteiro sumia e todo mundo caía junto — que é exatamente o
        // defeito relatado. Virando a fase primeiro, o pior caso de um
        // defeito ali dentro é uma rodada mal paga, e não o chão sumindo.
        fase = Fase.REVELACAO;
        tiqueDaFase = agora + planta.tempoRevelacao() * 20L;

        plugin.sorteBuilder().apagarFatia(mundo, sorteada);

        Location noMeio = SorteBuilder.pontoDaFatia(mundo, planta, sorteada);
        mundo.spawnParticle(Particle.FLASH, noMeio, 6, 3.0, 1.0, 3.0, 0.0);
        plugin.sorteBuilder().painelDoDado(mundo, planta,
                plugin.mini().deserialize("<color:#FF6B6B><b>FACE "
                        + (sorteada + 1) + "</b></color>\n<gray>"
                        + quantos[sorteada] + (quantos[sorteada] == 1
                                ? " jogador caiu" : " jogadores caíram")
                        + "</gray>"));
        tituloEmTodos("<color:#FF6B6B>FACE " + (sorteada + 1) + "</color>",
                "<gray>a fatia " + (sorteada + 1) + " sumiu</gray>");

        for (Player jogador : new ArrayList<>(jogadoresVivos())) {
            UUID id = jogador.getUniqueId();
            int setor = onde.getOrDefault(id, SorteCatalog.FORA);
            Location ouvido = jogador.getLocation();
            if (setor == sorteada) {
                emMao.put(id, 0);
                jogador.playSound(ouvido, Sound.ENTITY_GENERIC_EXPLODE,
                        0.7f, 0.7f);
                jogador.sendActionBar(plugin.mini().deserialize(
                        "<color:#FF6B6B>a face " + (sorteada + 1)
                                + " saiu debaixo de você</color> <gray>· "
                                + "perdeu tudo que tinha em mão</gray>"));
                continue;
            }
            if (setor < 0) {
                jogador.sendActionBar(plugin.mini().deserialize(
                        "<gray>você não estava em fatia nenhuma · "
                                + "não ganhou nada</gray>"));
                continue;
            }
            int paga = SorteCatalog.divisao(premio, quantos[setor]);
            emMao.merge(id, paga, Integer::sum);
            jogador.playSound(ouvido, paga > 0
                            ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP
                            : Sound.BLOCK_NOTE_BLOCK_BASS,
                    0.7f, paga > 0 ? 1.4f : 0.6f);
            jogador.sendActionBar(plugin.mini().deserialize(paga > 0
                    ? "<color:#8BE04E>+" + paga + "</color> <gray>· "
                            + premio + " dividido por " + quantos[setor]
                            + " · em mão <white>" + emMao.getOrDefault(id, 0)
                            + "</white></gray>"
                    : "<color:#FFD93D>zero</color> <gray>· " + premio
                            + " dividido por " + quantos[setor]
                            + " não dá um ponto · em mão <white>"
                            + emMao.getOrDefault(id, 0) + "</white></gray>"));
        }
    }

    /** Fecha a revelação: refaz a fatia e abre a rodada seguinte. */
    private void virarARodada(World mundo, SorteCatalog planta, long agora) {
        rodada++;
        sorteada = -1;
        if (rodada >= planta.rodadas()) {
            // A última passou: quem está de pé guarda, e isso esvazia a
            // arena, que é o que termina a rodada.
            for (Player jogador : new ArrayList<>(jogadoresVivos())) {
                guardar(jogador, "aguentou as " + planta.rodadas() + " rodadas");
            }
            return;
        }
        plugin.sorteBuilder().refazerFatias(mundo);
        fase = Fase.ESCOLHA;
        tiqueDaFase = agora + planta.tempoEscolha() * 20L;
        anunciarARodada(mundo, planta);
    }

    /** O painel e o aviso de abertura de rodada. */
    private void anunciarARodada(World mundo, SorteCatalog planta) {
        int premio = planta.premioDaRodada(rodada);
        plugin.sorteBuilder().painelDoDado(mundo, planta,
                plugin.mini().deserialize("<color:#E8A87C><b>RODADA "
                        + (rodada + 1) + " de " + planta.rodadas()
                        + "</b></color>\n<white>" + premio
                        + " pontos</white> <gray>divididos por quem "
                        + "escolher a mesma fatia</gray>"));
        anunciar("<color:#E8A87C>rodada " + (rodada + 1) + "</color> <gray>— "
                + "paga <white>" + premio + "</white>, dividido. "
                + planta.tempoEscolha() + "s para escolher.</gray>");
        for (Player jogador : jogadoresVivos()) {
            jogador.playSound(jogador.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f);
        }
    }

    /**
     * Guarda o que o jogador tem em mão e o tira da partida.
     *
     * Não passa por {@link #eliminar}: quem guarda não caiu, e o título e o
     * anúncio de eliminação diriam a coisa errada para o único jogador do
     * jogo que fez tudo certo.
     */
    private void guardar(Player jogador, String porque) {
        UUID id = jogador.getUniqueId();
        int quanto = emMao.getOrDefault(id, 0);
        emMao.put(id, 0);
        placar.somar(id, quanto);
        vivos().remove(id);

        jogador.setFallDistance(0f);
        jogador.setGameMode(GameMode.SPECTATOR);
        jogador.teleport(pontoDaPlateia(jogador.getWorld()));
        jogador.playSound(jogador.getLocation(),
                Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.2f);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:#E8A87C>" + porque + "</color> <gray>· guardado "
                        + "<white>" + placar.de(id) + "</white></gray>"));

        if (comecouCom() > 1) {
            anunciar("<color:#E8A87C>" + HubBuilder.escapar(jogador.getName())
                    + "</color> <gray>guardou <white>" + placar.de(id)
                    + "</white> e saiu — faltam <white>" + quantosVivos()
                    + "</white></gray>");
        }
    }

    // ------------------------------------------------------------------
    //  O que o jogador vê enquanto escolhe
    // ------------------------------------------------------------------

    /**
     * A conta de quanto a fatia de cada um pagaria AGORA.
     *
     * É a informação que o jogo é feito de: o jogador não precisa saber a
     * chance (é 1 em 6, está escrito nas seis placas), precisa saber quantos
     * estão com ele. Sem isto, decidir com dezesseis pessoas num disco de
     * cinquenta e seis blocos seria contar cabeças de longe.
     */
    @Override
    protected void aCadaSegundo(World mundo) {
        SorteCatalog planta = planta2();
        if (fase != Fase.ESCOLHA || rodada >= planta.rodadas()) {
            return;
        }
        int premio = planta.premioDaRodada(rodada);
        int[] quantos = new int[planta.setores()];
        Map<UUID, Integer> onde = new HashMap<>();
        for (Player jogador : jogadoresVivos()) {
            Location pe = jogador.getLocation();
            int setor = planta.setorDoPonto(pe.getX(), pe.getZ());
            onde.put(jogador.getUniqueId(), setor);
            if (setor >= 0) {
                quantos[setor]++;
            }
        }
        long faltam = Math.max(0, (tiqueDaFase - tiquesDeJogo()) / 20 + 1);
        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            int setor = onde.getOrDefault(id, SorteCatalog.FORA);
            String fatia = setor < 0
                    ? "<color:#FF6B6B>fora de fatia</color>"
                    : "<white>fatia " + (setor + 1) + "</white> <gray>· "
                            + quantos[setor] + " aqui · pagaria <white>"
                            + SorteCatalog.divisao(premio, quantos[setor])
                            + "</white></gray>";
            jogador.sendActionBar(plugin.mini().deserialize(fatia
                    + " <dark_gray>|</dark_gray> <gray>em mão <white>"
                    + emMao.getOrDefault(id, 0) + "</white> · guardado <white>"
                    + placar.de(id) + "</white> · <color:#E8A87C>" + faltam
                    + "s</color></gray>"));
        }
    }
}
