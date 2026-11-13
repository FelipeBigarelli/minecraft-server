package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * A Pescaria: paciência vira prêmio.
 *
 * <h2>Onde você lança é a decisão da partida</h2>
 *
 * Os plugins de pescaria mexem no que o jogador PEGA: a espera continua sendo
 * o sorteio de 5 a 30 segundos do jogo-base, e não há nada para decidir —
 * lança-se e espera-se. Aqui a espera sai do POÇO em que o anzol pousou, e os
 * seis poços pagam o mesmo por segundo com ritmos diferentes: a corredeira dá
 * 1,4 ponto a cada três segundos, o poço fundo dá 7 a cada treze. Escolher é
 * escolher variância, e variância é o que faz uma pescaria ter o que contar.
 *
 * <h2>Por que o anzol é seguido tique a tique</h2>
 *
 * No instante do lance o anzol ainda está na mão: ele não pousou, e não há
 * como saber em que poço vai cair. Decidir a espera pelo poço mais perto do
 * JOGADOR seria mais simples e seria explorável — a vara alcança vinte
 * blocos, e aí bastaria ficar na corredeira e lançar no poço fundo para ter a
 * espera de um e o peixe do outro. Então no lance a espera é estacionada num
 * valor em que nada morde, e o laço que já roda a cada tique espera o anzol
 * tocar a água para então dizer quanto ele vai demorar.
 *
 * <h2>O poço cansa</h2>
 *
 * Cada peixe tirado acrescenta espera àquele poço, e o alívio por segundo é
 * menor do que três pescadores juntos gastam. Sem isso, acampar no melhor
 * poço seria estritamente ótimo — e com os poços pagando igual, "o melhor
 * poço" seria só aquele em que os outros já estão, por hábito. O cansaço é o
 * que faz a plataforma inteira ser usada.
 *
 * <h2>O GRANDE sempre vem</h2>
 *
 * De cinquenta em cinquenta segundos ele abre em UM poço, anunciado para
 * todos, por vinte e cinco segundos. É o único momento em que o jogo diz a
 * todo mundo para ir ao mesmo lugar — e quatro vezes por partida dezesseis
 * pessoas largam o que estão fazendo e correm.
 */
public final class Pescaria extends JogoDeArena {

    /** A espera estacionada: enquanto o anzol voa, nada morde. */
    private static final int ESPERA_PARADA = 20 * 600;

    /** Por quantos tiques um recado fica na barra de ação do jogador. */
    private static final long RECADO_TIQUES = 40;

    private final Placar placar = new Placar();
    private final Random sorte = new Random();

    /** Anzóis lançados que ainda não pousaram na água. */
    private final Map<UUID, FishHook> aVoar = new HashMap<>();

    /** O cansaço de cada poço, em tiques de espera a mais. */
    private int[] cansaco = new int[0];

    private final Map<UUID, String> recado = new HashMap<>();
    private final Map<UUID, Long> recadoAte = new HashMap<>();

    private int grandeNoPoco = -1;
    private int grandeAte;
    private int proximoGrande;
    private String quemPegouOGrande;

    public Pescaria(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.pescariaCatalog();
    }

    private PescariaCatalog planta2() {
        return plugin.pescariaCatalog();
    }

    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "LANCE!";
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    @Override
    protected void prepararArena(World mundo) {
        placar.zerar();
        aVoar.clear();
        recado.clear();
        recadoAte.clear();
        cansaco = new int[planta2().pocos().size()];
        grandeNoPoco = -1;
        grandeAte = 0;
        proximoGrande = planta2().grandeIntervalo();
        quemPegouOGrande = null;
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        PescariaCatalog planta = planta2();
        List<int[]> pontos = planta.largadas(entram.size());
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            jogador.teleport(PescariaBuilder.pontoDaLargada(mundo, planta,
                    pontos.get(k % pontos.size())));
            jogador.setFallDistance(0f);
            darAVara(jogador);
            placar.entrar(jogador.getUniqueId());
        }
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        PescariaCatalog planta = planta2();
        anunciar("<gray>são <white>" + planta.pocos().size() + " poços</white> "
                + "e todos pagam o mesmo por segundo — o que muda é o ritmo. "
                + "Poço que é muito pescado <white>cansa</white>, e <white>"
                + HubBuilder.escapar(planta.oGrande().nome())
                + "</white> vale <white>" + planta.oGrande().pontos()
                + "</white></gray>");
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return PescariaBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
        aVoar.remove(id);
        recado.remove(id);
        recadoAte.remove(id);
    }

    @Override
    protected boolean rodadaAcabou() {
        return quantosVivos() == 0 || segundosDeJogo() >= planta2().tempoLimite();
    }

    @Override
    protected String vencedor() {
        UUID lider = placar.lider();
        if (lider == null || placar.pontosDoLider() <= 0) {
            return null;
        }
        Player quem = Bukkit.getPlayer(lider);
        return quem == null ? "alguém" : quem.getName();
    }

    @Override
    protected String legendaDaVitoria() {
        String grande = quemPegouOGrande == null ? ""
                : " · " + HubBuilder.escapar(quemPegouOGrande) + " tirou "
                + planta2().oGrande().nome();
        return placar.pontosDoLider() + " pontos de peixe" + grande;
    }

    @Override
    protected float progressoDaBarra() {
        int limite = Math.max(1, planta2().tempoLimite());
        return 1f - segundosDeJogo() / (float) limite;
    }

    @Override
    protected String textoDaBarra() {
        PescariaCatalog planta = planta2();
        int resta = Math.max(0, planta.tempoLimite() - segundosDeJogo());
        String aviso = "";
        if (grandeNoPoco >= 0 && grandeNoPoco < planta.pocos().size()) {
            aviso = " <color:#FFD700>· " + HubBuilder.escapar(
                    planta.oGrande().nome()) + " no "
                    + HubBuilder.escapar(planta.pocos().get(grandeNoPoco).nome())
                    + "</color>";
        }
        if (placar.pontosDoLider() <= 0) {
            return "<white>ninguém tirou peixe ainda</white> <gray>· "
                    + ArenaComum.tempo(resta * 20L) + "</gray>" + aviso;
        }
        return "<white>" + placar.nomeDoLider("empate na frente")
                + "</white> <gray>com <white>" + placar.pontosDoLider()
                + "</white> pts · " + ArenaComum.tempo(resta * 20L) + "</gray>"
                + aviso;
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    /**
     * Espera o anzol pousar para dizer quanto ele vai demorar.
     *
     * Usa o laço que o framework já roda a cada tique em vez de agendar uma
     * tarefa por lance: dezesseis jogadores lançando a cada três segundos
     * dariam cinco tarefas novas por segundo, e cinco tarefas por segundo que
     * existem para esperar um bloco de água é agendador usado de enfeite.
     */
    @Override
    protected void aCadaTique(World mundo) {
        if (aVoar.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, FishHook>> passo = aVoar.entrySet().iterator();
        while (passo.hasNext()) {
            Map.Entry<UUID, FishHook> entrada = passo.next();
            FishHook anzol = entrada.getValue();
            Player jogador = Bukkit.getPlayer(entrada.getKey());
            if (!anzol.isValid() || jogador == null || !estaVivo(jogador)) {
                passo.remove();
                continue;
            }
            if (!anzol.isInWater()) {
                continue;
            }
            passo.remove();
            pousou(jogador, anzol);
        }
    }

    /** O anzol tocou a água: agora o poço decide a espera. */
    private void pousou(Player jogador, FishHook anzol) {
        PescariaCatalog planta = planta2();
        Location onde = anzol.getLocation();
        int indice = planta.pocoDoPonto(onde.getX(), onde.getZ());
        if (indice < 0 || indice >= planta.pocos().size()) {
            // Água fora de poço não existe nesta arena, mas se existir um dia
            // é melhor nada morder do que morder com a tabela de outro poço.
            return;
        }
        PescariaCatalog.Poco poco = planta.pocos().get(indice);
        int extra = cansaco[indice];
        int espera = poco.esperaMin() + extra
                + sorte.nextInt(Math.max(1, poco.esperaMax() - poco.esperaMin() + 1));

        anzol.setApplyLure(false);
        anzol.setMinWaitTime(poco.esperaMin() + extra);
        anzol.setMaxWaitTime(poco.esperaMax() + extra);
        anzol.setWaitTime(espera);

        recado(jogador, "<color:#5B8DEF>" + HubBuilder.escapar(poco.nome())
                + "</color>" + (extra > 0 ? " <color:#FF8A8A>cansado +"
                + (extra / 20) + "s</color>" : ""));
    }

    @Override
    protected void aCadaSegundo(World mundo) {
        PescariaCatalog planta = planta2();
        for (int k = 0; k < cansaco.length; k++) {
            cansaco[k] = Math.max(0, cansaco[k] - planta.cansacoAlivio());
        }
        cuidarDoGrande(mundo, planta);

        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            String nota = recadoAte.getOrDefault(id, 0L) > tiquesDeJogo()
                    ? recado.getOrDefault(id, "") : "";
            jogador.sendActionBar(plugin.mini().deserialize(
                    (nota.isEmpty() ? "" : nota + " <gray>·</gray> ")
                            + "<color:#5B8DEF>" + placar.de(id) + " pts</color>"
                            + " <gray>· " + placar.posicaoDe(id) + "º de "
                            + placar.quantos() + "</gray>"));
        }
    }

    /** Abre e fecha a janela de O GRANDE. */
    private void cuidarDoGrande(World mundo, PescariaCatalog planta) {
        int agora = segundosDeJogo();
        if (grandeNoPoco >= 0 && agora >= grandeAte) {
            anunciar("<gray>" + HubBuilder.escapar(planta.oGrande().nome())
                    + " <white>desistiu</white> e foi para o fundo</gray>");
            grandeNoPoco = -1;
        }
        if (grandeNoPoco >= 0) {
            mundo.spawnParticle(Particle.FISHING, PescariaBuilder.pontoDoPoco(
                    mundo, planta, grandeNoPoco), 30, 2.5, 0.3, 2.5, 0.01);
            return;
        }
        if (agora < proximoGrande
                || agora + planta.grandeJanela() / 2 >= planta.tempoLimite()) {
            return;
        }
        grandeNoPoco = sorte.nextInt(planta.pocos().size());
        grandeAte = agora + planta.grandeJanela();
        proximoGrande = agora + planta.grandeIntervalo();
        String nome = HubBuilder.escapar(planta.oGrande().nome());
        String poco = HubBuilder.escapar(planta.pocos().get(grandeNoPoco).nome());
        tituloEmTodos("<color:#FFD700>" + nome + "</color>",
                "<white>está mordendo no " + poco + "</white>");
        anunciar("<color:#FFD700>" + nome + "</color> <gray>abriu no <white>"
                + poco + "</white> por <white>" + planta.grandeJanela()
                + "s</white> — vale <white>" + planta.oGrande().pontos()
                + "</white> pontos</gray>");
        for (Player jogador : jogadoresVivos()) {
            jogador.playSound(jogador.getLocation(),
                    Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 1.4f);
        }
    }

    // ------------------------------------------------------------------
    //  Pescar
    // ------------------------------------------------------------------

    @EventHandler
    public void aoPescar(PlayerFishEvent evento) {
        Player jogador = evento.getPlayer();
        if (!jogando() || !estaVivo(jogador)) {
            return;
        }
        switch (evento.getState()) {
            case FISHING -> aoLancar(jogador, evento.getHook());
            case CAUGHT_FISH -> aoFisgar(jogador, evento);
            // Fisgar jogador não dá ponto e atrapalha quem está pescando.
            case CAUGHT_ENTITY -> evento.setCancelled(true);
            default -> {
            }
        }
    }

    private void aoLancar(Player jogador, FishHook anzol) {
        anzol.setApplyLure(false);
        anzol.setMinWaitTime(ESPERA_PARADA);
        anzol.setMaxWaitTime(ESPERA_PARADA);
        anzol.setWaitTime(ESPERA_PARADA);
        aVoar.put(jogador.getUniqueId(), anzol);
    }

    private void aoFisgar(Player jogador, PlayerFishEvent evento) {
        PescariaCatalog planta = planta2();
        // Nada de item no chão: o peixe é ponto, e não peixe.
        if (evento.getCaught() != null) {
            evento.getCaught().remove();
        }
        evento.setExpToDrop(0);

        Location onde = evento.getHook().getLocation();
        int indice = planta.pocoDoPonto(onde.getX(), onde.getZ());
        if (indice < 0 || indice >= planta.pocos().size()) {
            return;
        }
        PescariaCatalog.Poco poco = planta.pocos().get(indice);

        List<Integer> pesos = new ArrayList<>(poco.pesos());
        boolean comGrande = grandeNoPoco == indice;
        if (comGrande) {
            pesos.add(planta.grandePeso());
        }
        int qual = PescariaCatalog.sortear(pesos, sorte);
        if (qual < 0) {
            return;
        }
        boolean eOGrande = comGrande && qual == pesos.size() - 1;
        PescariaCatalog.Peixe peixe = eOGrande ? planta.oGrande()
                : planta.peixes().get(Math.min(qual, planta.peixes().size() - 1));

        placar.somar(jogador.getUniqueId(), peixe.pontos());
        cansaco[indice] = Math.min(planta.cansacoMaximo(),
                cansaco[indice] + planta.cansacoPorPeixe());

        if (eOGrande) {
            grandeNoPoco = -1;
            quemPegouOGrande = jogador.getName();
            tituloEmTodos("<color:#FFD700>" + HubBuilder.escapar(peixe.nome())
                    + "</color>", "<white>" + HubBuilder.escapar(jogador.getName())
                    + " tirou do " + HubBuilder.escapar(poco.nome()) + "</white>");
            anunciar("<color:#FFD700>" + HubBuilder.escapar(jogador.getName())
                    + "</color> <gray>tirou <white>"
                    + HubBuilder.escapar(peixe.nome()) + "</white> do "
                    + HubBuilder.escapar(poco.nome()) + " — <white>+"
                    + peixe.pontos() + "</white></gray>");
            jogador.getWorld().playSound(jogador.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        } else {
            jogador.playSound(jogador.getLocation(),
                    Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f,
                    peixe.pontos() >= 8 ? 1.6f : 1.1f);
        }
        recado(jogador, "<white>" + HubBuilder.escapar(peixe.nome())
                + "</white> <color:#5B8DEF>+" + peixe.pontos() + "</color>");
    }

    private void darAVara(Player jogador) {
        jogador.getInventory().clear();
        ItemStack vara = new ItemStack(Material.FISHING_ROD);
        ItemMeta nome = vara.getItemMeta();
        if (nome != null) {
            nome.displayName(plugin.mini().deserialize(
                    "<color:#5B8DEF>Vara</color>").decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            vara.setItemMeta(nome);
        }
        jogador.getInventory().setItem(0, vara);
        jogador.getInventory().setHeldItemSlot(0);
    }

    /** Guarda um recado para a barra de ação do jogador. */
    private void recado(Player jogador, String mini) {
        UUID id = jogador.getUniqueId();
        recado.put(id, mini);
        recadoAte.put(id, tiquesDeJogo() + RECADO_TIQUES);
        jogador.sendActionBar(plugin.mini().deserialize(mini
                + " <gray>·</gray> <color:#5B8DEF>" + placar.de(id)
                + " pts</color>"));
    }
}
