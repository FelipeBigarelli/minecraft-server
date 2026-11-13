package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;

import java.util.List;

/**
 * A obra do BED WARS, e as seis conferências que seguram o desenho.
 *
 * <h2>Por que seis, e não "confere o yml e pronto"</h2>
 *
 * Este jogo tem três respostas a três reclamações do gênero, e cada resposta
 * é um NÚMERO. Um número errado não quebra nada: ele faz o jogo voltar a ter
 * exatamente o defeito que a resposta existia para consertar, e nada no log
 * avisa. As conferências são o que torna as três respostas verificáveis:
 *
 * <ul>
 *   <li>{@link #conferirCama} — a cama tem de custar entre cinco e quinze
 *       segundos de golpe. Menos, e o rush volta a ser instantâneo; mais, e o
 *       invasor fica parado tanto tempo que invadir deixa de ser jogada. E a
 *       cura tem de ser MUITO mais lenta que a quebra: se a cama se curasse
 *       quase na velocidade em que é batida, defender seria automático.</li>
 *   <li>{@link #conferirGraca} — a carência tem de ser o tempo da viagem.
 *       Curta demais é enfeite; longa demais faz o invasor esperar.</li>
 *   <li>{@link #conferirTear} — uma viagem ao meio tem de render lã de sobra
 *       para valer a ida e menos do que uma defesa inteira. As duas pontas
 *       importam: sem a de baixo, ninguém sai de casa; sem a de cima, uma
 *       viagem fecha a cama e o jogo acaba no primeiro minuto.</li>
 *   <li>{@link #conferirVidas} — uma vida só depois da cama é a reclamação
 *       "sua cama foi destruída, você agora é espectador" escrita em
 *       número.</li>
 *   <li>{@link #conferirLuta} — a espada tem de precisar de quatro a oito
 *       golpes, como nas outras arenas daqui.</li>
 *   <li>{@link #conferirArena} — as bases não se encostam, a cama cabe na
 *       base e a passarela é estreita e comprida o bastante para ser um
 *       aperto.</li>
 * </ul>
 */
public final class BedWarsBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_bed_painel";

    /** O painel da guerra, refeito quando uma cama cai. */
    public static final String TAG_GUERRA = "bigacore_bed_guerra";

    /** Quanto tempo de golpe a cama pode custar, no mínimo e no máximo. */
    private static final double CAMA_MINIMA = 5.0;
    private static final double CAMA_MAXIMA = 15.0;

    /** Quantas vezes a cura da cama tem de ser mais lenta que a quebra. */
    private static final double CURA_MINIMA = 3.0;

    /** Quanto a carência pode diferir do tempo da viagem. */
    private static final double GRACA_MINIMA = 0.8;
    private static final double GRACA_MAXIMA = 1.5;

    /** Quanta lã uma viagem ao meio tem de render, no mínimo. */
    private static final int LA_MINIMA_DA_VIAGEM = 4;

    /** A faixa do período do golpe, em tiques: a mesma das outras arenas. */
    private static final int PERIODO_MINIMO = 8;
    private static final int PERIODO_MAXIMO = 20;

    private final BigaCore plugin;
    private int contador;

    public BedWarsBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        BedWarsCatalog planta = plugin.bedWarsCatalog();

        String problema = conferirCama(planta.golpesDaCama(),
                planta.periodoDaCama(), planta.curaDaCama(),
                planta.tempoLimite());
        if (problema == null) {
            problema = conferirGraca(planta.gracaSegundos(),
                    planta.baseAnel(), planta.camaRecuo());
        }
        if (problema == null) {
            problema = conferirTear(planta.laDoTear(), planta.tearSegundos(),
                    planta.baseAnel(), planta.meioRaio(), planta.laDaDefesa());
        }
        if (problema == null) {
            problema = conferirVidas(planta.vidasDoTime(),
                    planta.respawnSegundos());
        }
        if (problema == null) {
            problema = conferirLuta(planta.periodoDoGolpe(),
                    planta.vidaCheia(), planta.danoDoGolpe());
        }
        if (problema == null) {
            problema = conferirArena(planta.times(), planta.baseAnel(),
                    planta.baseRaio(), planta.meioRaio(), planta.camaRecuo(),
                    planta.passarelaLargura());
        }
        if (problema == null && planta.quantosTimesDescritos() < planta.times()) {
            problema = "O yml pede " + planta.times() + " times e descreve "
                    + planta.quantosTimesDescritos() + " na lista `times:`: os "
                    + "que faltassem jogariam de branco, com a cama branca, e "
                    + "dois times teriam a mesma cor — que neste jogo é a "
                    + "mesma coisa que não ter time.";
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            int travessia = planta.travessiaEntreCamas();
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". " + planta.times() + " bases de raio "
                    + planta.baseRaio() + " no anel " + planta.baseAnel()
                    + ", com a cama recuada " + planta.camaRecuo()
                    + " para fora. Meio de raio " + planta.meioRaio()
                    + " com o tear, ligado por " + planta.times()
                    + " passarelas de " + planta.passarelaLargura()
                    + " de largura e " + planta.comprimentoDaPassarela()
                    + " de comprimento. A cama custa "
                    + planta.golpesDaCama() + " golpes ("
                    + Math.round(BedWarsCatalog.tempoDeQuebrarACama(
                            planta.golpesDaCama(), planta.periodoDaCama()) * 10) / 10.0
                    + "s) e se cura em "
                    + BedWarsCatalog.tempoDeCurarACama(planta.golpesDaCama(),
                            planta.curaDaCama()) + "s. Travessia de cama a "
                    + "cama: " + travessia + " blocos ("
                    + BedWarsCatalog.segundosAndando(travessia)
                    + "s a pé), e a carência é de " + planta.gracaSegundos()
                    + "s. Uma viagem ao meio rende "
                    + BedWarsCatalog.laDeUmaViagem(planta.baseAnel(),
                            planta.meioRaio(), planta.laDoTear(),
                            planta.tearSegundos()) + " de lã e a defesa pede "
                    + planta.laDaDefesa() + ". Saguão em y "
                    + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga bed desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_GUERRA);
        meio(mundo, planta);
        tear(mundo, planta);
        for (int k = 0; k < planta.times(); k++) {
            passarela(mundo, planta, k);
            base(mundo, planta, k);
            cama(mundo, planta, k);
        }
        torres(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);
        placasDoMeio(mundo, planta);
        for (int k = 0; k < planta.times(); k++) {
            placaDaBase(mundo, planta, k);
        }

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "BED WARS levantado: " + contador
                + " blocos em " + planta.times() + " bases, o meio e as "
                + "passarelas. O spawn do mundo foi posto no saguão.",
                contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        BedWarsCatalog planta = plugin.bedWarsCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_GUERRA);
        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                for (int y = planta.fundoDaObra(); y <= planta.tetoDaObra(); y++) {
                    Block bloco = mundo.getBlockAt(x, y, z);
                    if (!bloco.getType().isAir()) {
                        bloco.setType(Material.AIR, false);
                        contador++;
                    }
                }
            }
        }
        return new Resultado(true, "BED WARS apagado: " + contador
                + " blocos.", contador);
    }

    @Override
    public Resultado textos(World mundo) {
        BedWarsCatalog planta = plugin.bedWarsCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_GUERRA);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        placasDoMeio(mundo, planta);
        for (int k = 0; k < planta.times(); k++) {
            placaDaBase(mundo, planta, k);
        }
        return new Resultado(true, "Letreiros, placas do meio e placas das "
                + "bases refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * A cama custa tempo, e a cura é muito mais lenta que a quebra.
     *
     * A reclamação do gênero é que não dá tempo de proteger a cama, e a
     * proposta do fórum — um minuto de carência — não conserta, porque
     * carência só adia. O que dá tempo é a cama CUSTAR tempo, e com aviso.
     * Entre cinco e quinze segundos: abaixo de cinco o rush volta a ser
     * instantâneo, acima de quinze o invasor fica tanto tempo parado em
     * terreno inimigo que invadir deixa de ser jogada e vira suicídio.
     */
    static String conferirCama(int golpes, int periodo, int cura,
                               int tempoLimite) {
        if (golpes < 4) {
            return "A cama cai em " + golpes + " golpe(s): com menos de "
                    + "quatro, cada golpe vale um quarto da cama e uma "
                    + "distração de dois segundos custa a partida — e o aviso "
                    + "ao time dono chegaria junto com o fim.";
        }
        double quebra = BedWarsCatalog.tempoDeQuebrarACama(golpes, periodo);
        if (quebra < CAMA_MINIMA) {
            return "Quebrar a cama leva " + Math.round(quebra * 10) / 10.0
                    + "s e o mínimo é " + CAMA_MINIMA + "s: o rush voltaria a "
                    + "ser instantâneo, que é a reclamação mais antiga do "
                    + "gênero.";
        }
        if (quebra > CAMA_MAXIMA) {
            return "Quebrar a cama leva " + Math.round(quebra * 10) / 10.0
                    + "s e o máximo é " + CAMA_MAXIMA + "s: o invasor ficaria "
                    + "parado tanto tempo em terreno inimigo que invadir "
                    + "deixa de ser jogada.";
        }
        int curaTotal = BedWarsCatalog.tempoDeCurarACama(golpes, cura);
        if (curaTotal < quebra * CURA_MINIMA) {
            return "A cama se cura inteira em " + curaTotal + "s e quebrar "
                    + "leva " + Math.round(quebra * 10) / 10.0 + "s: curando "
                    + "quase na velocidade em que é batida, defender seria "
                    + "automático e atacar seria perda de tempo. O mínimo é "
                    + CURA_MINIMA + " vezes a quebra.";
        }
        if (curaTotal > tempoLimite / 3) {
            return "A cama se cura inteira em " + curaTotal + "s e a partida "
                    + "dura " + tempoLimite + "s: a cura nunca terminaria "
                    + "dentro de uma partida e seria enfeite — expulsar o "
                    + "invasor tem de devolver terreno de verdade.";
        }
        return null;
    }

    /**
     * A carência é o tempo da viagem, medido na arena.
     *
     * Nos dois sentidos. Uma carência curta é enfeite com aparência de regra:
     * a cama já pode ser batida quando o primeiro invasor chega, e o número no
     * yml não mudou nada. Uma carência longa é pior: o invasor chega, não pode
     * bater, e ou espera em cima da cama — o que troca a jogada por uma fila —
     * ou volta, e aí a arena inteira passa o primeiro minuto vazia.
     */
    static String conferirGraca(int graca, int anel, int recuo) {
        int travessia = BedWarsCatalog.travessiaEntreCamas(anel, recuo);
        int andando = BedWarsCatalog.segundosAndando(travessia);
        if (andando <= 0) {
            return "A arena tem travessia zero: não há de onde invadir.";
        }
        double razao = graca / (double) andando;
        if (razao < GRACA_MINIMA) {
            return "A carência é de " + graca + "s e ir de uma cama até a de "
                    + "outro time leva " + andando + "s andando ("
                    + travessia + " blocos): a carência acabaria antes de o "
                    + "primeiro invasor chegar e não teria mudado nada.";
        }
        if (razao > GRACA_MAXIMA) {
            return "A carência é de " + graca + "s e a travessia leva "
                    + andando + "s: o invasor chegaria e teria de ESPERAR em "
                    + "cima da cama, o que troca a jogada por uma fila. O "
                    + "máximo é " + GRACA_MAXIMA + " vezes a travessia.";
        }
        return null;
    }

    /**
     * Uma viagem ao meio vale a ida, e não fecha a cama sozinha.
     *
     * É a resposta a "o colega fica parado em cima do gerador": com o recurso
     * no meio, não existe lugar seguro onde ficar parado dê lucro. Mas o
     * número tem de ficar entre duas pontas, e as duas são reais — pouca lã e
     * ninguém sai de casa, lã demais e a primeira viagem fecha a cama, o que
     * acaba a partida no primeiro minuto e por outro motivo.
     */
    static String conferirTear(int laDoTear, int tearSegundos, int anel,
                               int meioRaio, int laDaDefesa) {
        int viagem = BedWarsCatalog.laDeUmaViagem(anel, meioRaio, laDoTear,
                tearSegundos);
        if (viagem < LA_MINIMA_DA_VIAGEM) {
            return "Uma viagem ao meio rende " + viagem + " de lã: não paga "
                    + "nem o risco de ir, e aí todo mundo fica em casa — que "
                    + "é o defeito que o tear no meio existe para consertar.";
        }
        if (viagem >= laDaDefesa) {
            return "Uma viagem ao meio rende " + viagem + " de lã e fechar "
                    + "uma cama pede " + laDaDefesa + ": uma viagem só "
                    + "fecharia a cama, e aí a partida acaba no primeiro "
                    + "minuto com todas as camas de pé e ninguém jogando.";
        }
        return null;
    }

    /**
     * Perder a cama não é virar espectador.
     *
     * "Sua cama foi destruída, você agora é espectador" é o título de uma
     * reclamação de fórum, e com uma vida só é literalmente o que acontece: a
     * morte seguinte tira o jogador da partida. Duas vidas no mínimo, para o
     * time ter o que fazer depois da cama; seis no máximo, senão a cama deixa
     * de ser o que se protege.
     */
    static String conferirVidas(int vidas, int respawn) {
        if (vidas < 2) {
            return "Depois da cama o time fica com " + vidas + " vida(s): com "
                    + "uma, a morte seguinte tira o jogador da partida e "
                    + "voltamos a 'sua cama foi destruída, você agora é "
                    + "espectador' — que é a reclamação que este jogo existe "
                    + "para não repetir.";
        }
        if (vidas > 6) {
            return "Depois da cama o time fica com " + vidas + " vidas: tanto "
                    + "assim e a cama deixa de ser o que se protege, porque "
                    + "perdê-la quase não muda a partida.";
        }
        if (respawn < 3) {
            return "Voltar leva " + respawn + "s: rápido demais e morrer não "
                    + "custa nada, nem com cama nem sem ela.";
        }
        if (respawn > 10) {
            return "Voltar leva " + respawn + "s: devagar demais, e quem "
                    + "morre passa a partida olhando a tela de espera, que é "
                    + "o espectador de volta por outro caminho.";
        }
        return null;
    }

    /**
     * A espada precisa de quatro a oito golpes, e do tempo das outras arenas.
     *
     * A carência do alvo é o próprio período, posta assim pelo jogo — então
     * não há o que conferir entre os dois, e conferir seria código que nunca
     * roda. O que há para conferir é a FAIXA: o tempo do golpe é uma coisa
     * que o jogador aprende uma vez e leva para as três arenas, e um período
     * fora da faixa das outras o obriga a reaprender.
     */
    static String conferirLuta(int periodo, int vidaCheia, int dano) {
        double golpes = BedWarsCatalog.golpesParaDerrubar(vidaCheia, dano);
        if (golpes < 4) {
            return "A espada derruba alguém em "
                    + Math.round(golpes * 10) / 10.0 + " golpes: menos de "
                    + "quatro e quem chega primeiro ganha a briga antes de o "
                    + "outro virar.";
        }
        if (golpes > 8) {
            return "A espada derruba alguém em "
                    + Math.round(golpes * 10) / 10.0 + " golpes: mais de oito "
                    + "e nenhuma briga acaba, então ninguém defende nem "
                    + "ataca — só se empurra.";
        }
        if (periodo < PERIODO_MINIMO || periodo > PERIODO_MAXIMO) {
            return "O período do golpe é de " + periodo + " tiques e a faixa "
                    + "é de " + PERIODO_MINIMO + " a " + PERIODO_MAXIMO + ": "
                    + "o tempo da espada é uma coisa que o jogador APRENDE, e "
                    + "ele aprende na ARENA PVP, no SKY WARS e aqui. Um "
                    + "período fora da faixa das outras arenas obriga a "
                    + "reaprender num jogo em que não dá tempo de reaprender "
                    + "nada.";
        }
        return null;
    }

    /** As bases não se encostam, a cama cabe, e a passarela é um aperto. */
    static String conferirArena(int times, int anel, int raio, int meioRaio,
                                int recuo, int largura) {
        if (times < 2) {
            return "Há " + times + " time(s): não há guerra de um lado só.";
        }
        double corda = 2.0 * anel * Math.sin(Math.PI / times);
        double vao = corda - 2.0 * (raio + recuo);
        if (vao < 8) {
            return "Entre duas bases vizinhas sobram "
                    + Math.round(vao * 10) / 10.0 + " blocos: as bases se "
                    + "encostam e dá para bater na cama do vizinho de dentro "
                    + "da própria base, sem nunca passar pelo meio.";
        }
        int passarela = anel - raio - meioRaio;
        if (passarela < 8) {
            return "A passarela tem " + passarela + " blocos: curta assim, a "
                    + "base encosta no meio e a viagem ao tear deixa de ser "
                    + "viagem.";
        }
        if (largura % 2 == 0) {
            return "A passarela tem largura " + largura + ", que é par: sem "
                    + "bloco do meio ela nasce torta em relação ao raio da "
                    + "base, e o erro de meio bloco cai justamente na boca da "
                    + "base.";
        }
        if (largura > meioRaio) {
            return "A passarela tem largura " + largura + " e o meio tem raio "
                    + meioRaio + ": larga assim, ela deixa de ser o aperto "
                    + "onde as brigas acontecem.";
        }
        if (recuo + 2 > raio) {
            return "A cama fica recuada " + recuo + " do centro de uma base "
                    + "de raio " + raio + ": a cabeceira cairia fora do chão "
                    + "da base, no vazio.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    /**
     * A base de um time: um disco na cor dele, com a língua da cama.
     *
     * A língua é o pedaço de chão que sai do disco para trás, onde a cama
     * fica. Ela é estreita porque é o último aperto: o invasor que chegou até
     * ali não pode atacar a cama de três lados ao mesmo tempo, e o defensor
     * tem UM lugar para ficar.
     */
    private void base(World mundo, BedWarsCatalog planta, int k) {
        BedWarsCatalog.Time time = planta.time(k);
        Material piso = planta.cor("base-piso", Material.POLISHED_DEEPSLATE);
        Material beira = planta.cor("base-beira", Material.DEEPSLATE_TILES);
        Material corpo = planta.cor("base-corpo", Material.DEEPSLATE_BRICKS);
        Material marco = planta.cor("base-marco",
                Material.POLISHED_BLACKSTONE_BRICKS);
        int[] centro = planta.centroDaBase(k);
        if (centro == null) {
            return;
        }
        int y = planta.topoY();
        int raio = planta.baseRaio();

        for (int dx = -raio; dx <= raio; dx++) {
            for (int dz = -raio; dz <= raio; dz++) {
                if (!ArenaComum.noDiscoMeio(dx, dz, raio)) {
                    continue;
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(dx, dz, raio - 1);
                por(mundo, centro[0] + dx, y, centro[1] + dz,
                        naBeira ? beira : piso);
                // Uma cruz na cor do time, para a base ser lida de longe.
                if (!naBeira && (dx == 0 || dz == 0)) {
                    por(mundo, centro[0] + dx, y, centro[1] + dz,
                            time.terracota());
                }
                for (int fundo = 1; fundo < planta.baseAltura(); fundo++) {
                    por(mundo, centro[0] + dx, y - fundo, centro[1] + dz,
                            corpo);
                }
            }
        }

        // A língua da cama: três blocos de largura, saindo para fora.
        BlockFace rumo = BedWarsCatalog.rumoDaCama(k, planta.times());
        int[] pe = planta.pontoDaCama(k);
        for (int passo = 0; passo <= planta.camaRecuo() + 2; passo++) {
            int x = centro[0] + rumo.getModX() * passo;
            int z = centro[1] + rumo.getModZ() * passo;
            for (int lado = -1; lado <= 1; lado++) {
                int lx = x + Math.abs(rumo.getModZ()) * lado;
                int lz = z + Math.abs(rumo.getModX()) * lado;
                por(mundo, lx, y, lz, lado == 0 ? time.terracota() : beira);
                for (int fundo = 1; fundo < planta.baseAltura(); fundo++) {
                    por(mundo, lx, y - fundo, lz, corpo);
                }
            }
        }
        // Dois marcos de pé ao lado da cama, para ela ser achada no escuro.
        for (int lado = -1; lado <= 1; lado += 2) {
            int mx = pe[0] + Math.abs(rumo.getModZ()) * lado;
            int mz = pe[1] + Math.abs(rumo.getModX()) * lado;
            por(mundo, mx, y + 1, mz, marco);
            por(mundo, mx, y + 2, mz, time.concreto());
            por(mundo, mx, y + 3, mz, planta.cor("poste-luz",
                    Material.LANTERN));
        }
    }

    /**
     * A cama do time: dois blocos, na cor dele, de costas para o meio.
     *
     * De costas para o meio e não de lado: quem defende fica entre a cama e o
     * inimigo sem ter de escolher um lado, e quem ataca chega sempre pela
     * mesma direção. Numa arena de quatro bases iguais, isso é o que permite
     * ao jogador saber onde se pôr sem ter decorado o mapa.
     */
    public void cama(World mundo, BedWarsCatalog planta, int k) {
        BedWarsCatalog.Time time = planta.time(k);
        int[] pe = planta.pontoDaCama(k);
        int[] cabeca = BedWarsCatalog.cabecaDaCama(k, planta.times(),
                planta.baseAnel(), planta.camaRecuo());
        BlockFace rumo = BedWarsCatalog.rumoDaCama(k, planta.times());
        int y = planta.topoY() + 1;

        Block blocoDoPe = mundo.getBlockAt(pe[0], y, pe[1]);
        Block blocoDaCabeca = mundo.getBlockAt(cabeca[0], y, cabeca[1]);
        blocoDoPe.setType(time.cama(), false);
        blocoDaCabeca.setType(time.cama(), false);
        contador += 2;

        if (blocoDoPe.getBlockData() instanceof Bed dados) {
            dados.setPart(Bed.Part.FOOT);
            dados.setFacing(rumo);
            blocoDoPe.setBlockData(dados, false);
        }
        if (blocoDaCabeca.getBlockData() instanceof Bed dados) {
            dados.setPart(Bed.Part.HEAD);
            dados.setFacing(rumo);
            blocoDaCabeca.setBlockData(dados, false);
        }
    }

    /** Apaga a cama de um time: é isso que o invasor conquista. */
    public int derrubarACama(World mundo, BedWarsCatalog planta, int k) {
        int[] pe = planta.pontoDaCama(k);
        int[] cabeca = BedWarsCatalog.cabecaDaCama(k, planta.times(),
                planta.baseAnel(), planta.camaRecuo());
        int y = planta.topoY() + 1;
        int quantos = 0;
        for (int[] onde : List.of(pe, cabeca)) {
            Block bloco = mundo.getBlockAt(onde[0], y, onde[1]);
            if (!bloco.getType().isAir()) {
                bloco.setType(Material.AIR, false);
                quantos++;
            }
        }
        return quantos;
    }

    /** Põe a cama de volta, para a rodada seguinte. */
    public int refazerACama(World mundo, BedWarsCatalog planta, int k) {
        contador = 0;
        cama(mundo, planta, k);
        return contador;
    }

    /** Se os dois blocos da cama do time {@code k} estão de pé. */
    public static boolean camaDePe(World mundo, BedWarsCatalog planta, int k) {
        int[] pe = planta.pontoDaCama(k);
        int y = planta.topoY() + 1;
        return mundo.getBlockAt(pe[0], y, pe[1]).getType()
                == planta.time(k).cama();
    }

    /** Se o bloco é parte da cama de algum time, e de qual. */
    public static int camaDoBloco(Block bloco, BedWarsCatalog planta) {
        if (bloco.getY() != planta.topoY() + 1) {
            return BedWarsCatalog.FORA;
        }
        for (int k = 0; k < planta.times(); k++) {
            int[] pe = planta.pontoDaCama(k);
            int[] cabeca = BedWarsCatalog.cabecaDaCama(k, planta.times(),
                    planta.baseAnel(), planta.camaRecuo());
            if ((bloco.getX() == pe[0] && bloco.getZ() == pe[1])
                    || (bloco.getX() == cabeca[0]
                            && bloco.getZ() == cabeca[1])) {
                return k;
            }
        }
        return BedWarsCatalog.FORA;
    }

    private void meio(World mundo, BedWarsCatalog planta) {
        Material topo = planta.cor("meio-topo", Material.POLISHED_BLACKSTONE);
        Material corpo = planta.cor("meio-corpo", Material.BLACKSTONE);
        Material beira = planta.cor("meio-beira",
                Material.CHISELED_POLISHED_BLACKSTONE);
        int y = planta.topoY();
        int raio = planta.meioRaio();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)) {
                    continue;
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(x, z, raio - 1);
                por(mundo, x, y, z, naBeira ? beira : topo);
                for (int fundo = 1; fundo < planta.meioAltura(); fundo++) {
                    por(mundo, x, y - fundo, z, corpo);
                }
            }
        }
    }

    /**
     * O tear, no meio: o único lugar de onde sai lã.
     *
     * Em cima de um pedestal e com luz, porque ele tem de ser visível da base
     * de todo mundo: a resposta a "o colega fica parado no gerador" só
     * funciona se o jogador VIR para onde tem de ir.
     */
    private void tear(World mundo, BedWarsCatalog planta) {
        Material pedestal = planta.cor("tear-base",
                Material.CHISELED_POLISHED_BLACKSTONE);
        Material luz = planta.cor("tear-luz", Material.LANTERN);
        Material qual = planta.cor("tear", Material.LOOM);
        int y = planta.topoY();
        int alto = planta.tearAltura();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                por(mundo, dx, y + 1, dz, pedestal);
            }
        }
        for (int fiada = 2; fiada <= alto; fiada++) {
            por(mundo, 0, y + fiada, 0, fiada == alto ? qual : pedestal);
        }
        for (int lado = 0; lado < 4; lado++) {
            double angulo = lado * Math.PI / 2;
            int x = (int) Math.round(Math.cos(angulo) * 3);
            int z = (int) Math.round(Math.sin(angulo) * 3);
            por(mundo, x, y + 1, z, pedestal);
            por(mundo, x, y + 2, z, luz);
        }
    }

    /**
     * A passarela da base até o meio.
     *
     * Dada de graça, de propósito: sem ela a primeira lã viria de lugar
     * nenhum, porque a lã está no meio e chegar ao meio pede lã. O caminho
     * para a base do VIZINHO é que não é dado — e é essa a decisão do jogo.
     */
    private void passarela(World mundo, BedWarsCatalog planta, int k) {
        Material piso = planta.cor("passarela", Material.SMOOTH_STONE);
        Material beira = planta.cor("passarela-beira", Material.STONE_BRICKS);
        double angulo = BedWarsCatalog.anguloDoTime(k, planta.times());
        double dx = Math.cos(angulo);
        double dz = Math.sin(angulo);
        int y = planta.topoY();
        int meia = planta.passarelaLargura() / 2;

        for (int passo = planta.meioRaio() - 1;
                passo <= planta.baseAnel() - planta.baseRaio() + 1; passo++) {
            for (int lado = -meia; lado <= meia; lado++) {
                int x = (int) Math.round(dx * passo - dz * lado);
                int z = (int) Math.round(dz * passo + dx * lado);
                por(mundo, x, y, z,
                        Math.abs(lado) == meia && meia > 0 ? beira : piso);
            }
        }
    }

    /** As placas do meio: as regras que o jogador lê ao chegar no tear. */
    public void placasDoMeio(World mundo, BedWarsCatalog planta) {
        String cor = planta.corDoTexto();
        int y = planta.topoY() + 2;
        int longe = Math.max(3, planta.meioRaio() - 2);
        List<List<Component>> textos = List.of(
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>O TEAR</b></color>"),
                        plugin.mini().deserialize("<white>a lã só sai</white>"),
                        plugin.mini().deserialize("<white>aqui no meio</white>"),
                        plugin.mini().deserialize("<gray>não há gerador</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>A CAMA</b></color>"),
                        plugin.mini().deserialize("<white>custa "
                                + planta.golpesDaCama() + " golpes</white>"),
                        plugin.mini().deserialize("<white>e avisa o dono</white>"),
                        plugin.mini().deserialize("<gray>a cada um</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>SEM CAMA</b></color>"),
                        plugin.mini().deserialize("<white>o time conta</white>"),
                        plugin.mini().deserialize("<white>"
                                + planta.vidasDoTime() + " vidas</white>"),
                        plugin.mini().deserialize("<gray>e segue jogando</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>A LÃ</b></color>"),
                        plugin.mini().deserialize("<white>é da sua cor</white>"),
                        plugin.mini().deserialize("<white>e só se quebra</white>"),
                        plugin.mini().deserialize("<gray>o que foi posto</gray>")));

        int[][] lados = {{0, -longe}, {longe, 0}, {0, longe}, {-longe, 0}};
        for (int k = 0; k < lados.length; k++) {
            int x = lados[k][0];
            int z = lados[k][1];
            por(mundo, x, planta.topoY() + 1, z,
                    planta.cor("poste", Material.POLISHED_BLACKSTONE_BRICKS));
            BlockFace frente = ArcoCatalog.frenteDaPlaca(x, z);
            ArenaComum.escreverPlaca(mundo,
                    new int[]{x + frente.getModX(), y, z + frente.getModZ()},
                    planta.materialDaPlaca(), frente, textos.get(k));
        }
    }

    /** A placa da base: o nome do time, de frente para quem chega. */
    public void placaDaBase(World mundo, BedWarsCatalog planta, int k) {
        BedWarsCatalog.Time time = planta.time(k);
        int[] centro = planta.centroDaBase(k);
        if (centro == null) {
            return;
        }
        BlockFace frente = BedWarsCatalog.frenteDaPlacaDaBase(k,
                planta.times());
        int poste = planta.baseRaio() - 1;
        int px = centro[0] + frente.getModX() * poste;
        int pz = centro[1] + frente.getModZ() * poste;
        int y = planta.topoY() + 2;

        por(mundo, px, planta.topoY() + 1, pz, time.concreto());
        por(mundo, px, y, pz, time.concreto());
        ArenaComum.escreverPlaca(mundo,
                new int[]{px + frente.getModX(), y, pz + frente.getModZ()},
                planta.materialDaPlaca(), frente,
                List.of(plugin.mini().deserialize("<color:" + time.cor()
                                + "><b>" + time.nome() + "</b></color>"),
                        plugin.mini().deserialize("<white>a cama é atrás</white>"),
                        plugin.mini().deserialize("<white>de você</white>"),
                        plugin.mini().deserialize("<gray>defenda-a</gray>")));
    }

    private void torres(World mundo, BedWarsCatalog planta) {
        Material torre = planta.cor("torre", Material.POLISHED_DEEPSLATE);
        Material luz = planta.cor("torre-luz", Material.SHROOMLIGHT);
        Material rod = planta.cor("luz-fria", Material.LANTERN);
        int y = planta.topoY();
        int raio = planta.baseAnel() + planta.camaRecuo() + 8;

        for (int k = 0; k < planta.times(); k++) {
            double angulo = BedWarsCatalog.anguloDoTime(k, planta.times())
                    + Math.PI / planta.times();
            int x = (int) Math.round(Math.cos(angulo) * raio);
            int z = (int) Math.round(Math.sin(angulo) * raio);
            for (int alto = 0; alto <= planta.marcoAltura(); alto++) {
                por(mundo, x, y + alto, z,
                        alto % 4 == 0 && alto > 0 ? luz : torre);
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, x, y + planta.marcoAltura() + alto, z, rod);
            }
        }
    }

    /** O painel da guerra, no alto do meio. */
    public void painelDaGuerra(World mundo, BedWarsCatalog planta,
                               Component texto) {
        ArenaComum.limparPaineis(mundo, TAG_GUERRA);
        Location alvo = new Location(mundo, 0.5,
                planta.topoY() + planta.painelAltura(), 0.5);
        ArenaComum.painelVirado(mundo, alvo, texto,
                (float) planta.escalaTitulo(), planta.fundoDoTexto(),
                TAG_GUERRA);
    }

    private int contarOcupados(World mundo, BedWarsCatalog planta) {
        int alcance = planta.alcanceDaObra();
        int ocupados = 0;
        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                for (int y = planta.fundoDaObra(); y <= planta.tetoDaObra(); y++) {
                    if (!mundo.getBlockAt(x, y, z).getType().isAir()) {
                        ocupados++;
                    }
                }
            }
        }
        return ocupados;
    }

    private void por(World mundo, int x, int y, int z, Material material) {
        Block bloco = mundo.getBlockAt(x, y, z);
        if (bloco.getType() != material) {
            bloco.setType(material, false);
            contador++;
        }
    }

    // ------------------------------------------------------------------
    //  Pontos
    // ------------------------------------------------------------------

    @Override
    public Location pontoDeEntrada(World mundo) {
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.bedWarsCatalog());
    }

    /** Onde o time nasce: no meio da base, olhando para o tear. */
    public static Location pontoDaBase(World mundo, BedWarsCatalog planta,
                                       int k) {
        int[] centro = planta.centroDaBase(k);
        if (centro == null) {
            return SaguaoPadrao.pontoDoSaguao(mundo, planta);
        }
        Location pouso = new Location(mundo, centro[0] + 0.5,
                planta.topoY() + 1, centro[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(centro[0], centro[1]));
        pouso.setPitch(0f);
        return pouso;
    }

    public static Location pontoDaPlateia(World mundo,
                                          BedWarsCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.marcoAltura() + 14, 0.5);
        onde.setYaw(180f);
        onde.setPitch(70f);
        return onde;
    }
}
