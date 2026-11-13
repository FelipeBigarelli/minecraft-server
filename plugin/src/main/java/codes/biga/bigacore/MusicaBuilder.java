package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.List;

/**
 * Levanta o metrônomo e o anel de tablados do RITMO.
 *
 * <h2>As três conferências, e a primeira é a que importa</h2>
 *
 * {@link #conferirAndamentos} recusa a tabela de andamentos por dois motivos
 * opostos, e os dois deixam o jogo rodando sem erro nenhum:
 *
 * <ul>
 *   <li><b>Folga larga demais.</b> Passando de metade da batida, as folgas de
 *       duas batidas vizinhas se encostam e TODO pulo cai dentro de alguma —
 *       o jogo toca, pisca, conta pontos e não pode mais ser perdido. É o
 *       defeito exato de converter os milissegundos do osu! para um servidor
 *       a vinte tiques por segundo.</li>
 *   <li><b>Folga de um tique.</b> Aí o que decide não é o jogador, é o
 *       arredondamento do tique em que o pulo chegou.</li>
 *   <li><b>Folga que não aperta escada abaixo.</b> O andamento bate no teto
 *       do pulo em 100 bpm, então a folga é a única coisa que ainda pode
 *       dificultar — uma lista de folgas iguais dá sete faixas com o mesmo
 *       jogo em sete velocidades parecidas.</li>
 *   <li><b>Batida mais rápida que o pulo.</b> Abaixo de
 *       {@value MusicaCatalog#CICLO_DO_PULO} tiques a batida chega antes de o
 *       jogador voltar ao chão. Não é difícil, é impossível, e o sintoma é a
 *       arena inteira morrendo nas três primeiras batidas da faixa.</li>
 * </ul>
 *
 * <p>E a faixa mais lenta tem de dar {@value MusicaCatalog#FOLGA_DO_PING}
 * tiques de folga, que são 250 ms: é ela que absorve o ping, e sem isso as
 * primeiras batidas seriam impossíveis para quem joga de longe — o que não
 * apareceria no teste de ninguém, porque quem testa joga em casa.
 *
 * <h2>O vazio é medido</h2>
 *
 * {@link #conferirNumeros} exige {@value #SALTO_DO_JOGADOR} blocos de vazio
 * entre tablados vizinhos e entre o anel e o metrônomo. Um jogador correndo
 * pula quatro blocos; com quatro de vazio dá para invadir o tablado do
 * vizinho — que tem as vidas DELE — ou se refugiar no metrônomo, que não tem
 * vida nenhuma para perder.
 */
public final class MusicaBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_musica_painel";

    /** O painel do metrônomo, refeito a cada faixa de andamento. */
    public static final String TAG_RITMO = "bigacore_musica_ritmo";

    /** Quantos blocos um jogador correndo pula. Mais um, de margem. */
    public static final int SALTO_DO_JOGADOR = 5;

    private final BigaCore plugin;
    private int contador;

    public MusicaBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        MusicaCatalog planta = plugin.musicaCatalog();

        String problema = conferirAndamentos(planta.tempos(), planta.folgas(),
                planta.folgaMinima(), planta.vidas(),
                planta.batidasPorTempo());
        if (problema == null) {
            problema = conferirNumeros(planta.tablados(), planta.tabladoRaio(),
                    planta.tabladoAnel(), planta.metronomoRaio(),
                    planta.maximo(), planta.vidas());
        }
        if (problema == null) {
            problema = conferirRelogio(planta.tempos(), planta.batidasPorTempo(),
                    planta.batidasPorCompasso(), planta.batidasDeAviso(),
                    planta.tempoLimite());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            StringBuilder escada = new StringBuilder();
            for (int k = 0; k < planta.tempos().size(); k++) {
                escada.append(" · ")
                        .append(MusicaCatalog.bpmDe(planta.tempos().get(k)))
                        .append("bpm folga ±").append(planta.folgaDaFaixa(k));
            }
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". " + planta.tablados() + " tablados de raio "
                    + planta.tabladoRaio() + " no anel " + planta.tabladoAnel()
                    + ", na fiada " + planta.topoY() + ". Escada:" + escada
                    + ". A escada dura "
                    + (planta.tiqueDaBatida(planta.batidasDaEscada()) / 20)
                    + "s. Saguão em y " + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga musica desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_RITMO);
        metronomo(mundo, planta);
        for (int k = 0; k < planta.tablados(); k++) {
            tablado(mundo, planta, k, planta.tabladoRaio());
        }
        postes(mundo, planta);
        torres(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);
        placasDosTablados(mundo, planta);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "RITMO levantado: " + contador
                + " blocos, " + planta.tablados() + " tablados e "
                + planta.tempos().size() + " faixas de andamento. O spawn do "
                + "mundo foi posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        MusicaCatalog planta = plugin.musicaCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_RITMO);
        contador += ArenaComum.apagarACaixa(mundo, alcance,
                planta.fundoDaObra(), planta.tetoDaObra());
        return new Resultado(true, "RITMO apagado: " + contador + " blocos.",
                contador);
    }

    @Override
    public Resultado textos(World mundo) {
        MusicaCatalog planta = plugin.musicaCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_RITMO);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        placasDosTablados(mundo, planta);
        return new Resultado(true, "Letreiros e placas dos tablados refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /** A tabela de andamentos dá folgas com sentido em todas as faixas. */
    static String conferirAndamentos(List<Integer> tempos,
                                     List<Integer> folgas, int folgaMinima,
                                     int vidas, int batidasPorTempo) {
        if (tempos.isEmpty()) {
            return "A tabela de andamentos está vazia: sem batida não há ritmo.";
        }
        if (folgaMinima < MusicaCatalog.FOLGA_HONESTA) {
            return "A folga mínima é de " + folgaMinima + " tique(s) e o "
                    + "honesto é " + MusicaCatalog.FOLGA_HONESTA + ": com um "
                    + "tique de folga o que decide não é o jogador, é em qual "
                    + "tique o pulo dele chegou.";
        }
        if (folgas.isEmpty()) {
            return "A lista de folgas está vazia: é ela que diz quanto o "
                    + "jogador pode errar em cada faixa.";
        }
        if (folgas.size() < tempos.size()) {
            return "São " + tempos.size() + " faixas de andamento e só "
                    + folgas.size() + " folga(s): a última se repetiria para "
                    + "as faixas que faltam, e aí a escada pararia de ficar "
                    + "mais difícil justamente onde ela devia apertar.";
        }
        int antes = Integer.MAX_VALUE;
        int folgaAntes = Integer.MAX_VALUE;
        for (int faixa = 0; faixa < tempos.size(); faixa++) {
            int tempo = tempos.get(faixa);
            if (tempo < 2) {
                return "Há uma faixa com batida de " + tempo + " tique(s).";
            }
            if (tempo < MusicaCatalog.BATIDA_MAIS_RAPIDA) {
                return "A faixa de " + MusicaCatalog.bpmDe(tempo) + " bpm tem "
                        + "batida de " + tempo + " tiques, e o pulo do jogador "
                        + "leva " + MusicaCatalog.CICLO_DO_PULO
                        + " tiques ENTRE um e o outro. Com ±"
                        + MusicaCatalog.JITTER_DO_RITMISTA + " tique de erro "
                        + "humano, quem acertasse uma batida ficaria sem pulo "
                        + "para a seguinte — não seria difícil, seria "
                        + "arbitrário. A batida mais rápida possível é de "
                        + MusicaCatalog.BATIDA_MAIS_RAPIDA + " tiques, ou "
                        + MusicaCatalog.bpmDe(MusicaCatalog.BATIDA_MAIS_RAPIDA)
                        + " bpm.";
            }
            if (tempo > antes) {
                return "A faixa de " + MusicaCatalog.bpmDe(tempo) + " bpm vem "
                        + "depois de uma de " + MusicaCatalog.bpmDe(antes)
                        + " bpm: a escada tem de subir, e um andamento que "
                        + "desacelera no meio da partida confunde quem já "
                        + "pegou o ritmo.";
            }
            antes = tempo;

            int folga = MusicaCatalog.folgaDe(folgas, faixa, folgaMinima);
            if (folga > folgaAntes) {
                return "A faixa de " + MusicaCatalog.bpmDe(tempo) + " bpm dá "
                        + "folga de ±" + folga + " tiques e a faixa anterior "
                        + "dava ±" + folgaAntes + ": a folga tem de apertar "
                        + "escada abaixo, porque é só ela que faz o jogo ficar "
                        + "mais difícil depois que o andamento bate no teto do "
                        + "pulo.";
            }
            folgaAntes = folga;

            if (2 * folga + 1 > tempo) {
                return "A " + MusicaCatalog.bpmDe(tempo) + " bpm a batida dura "
                        + tempo + " tiques e a folga é de ±" + folga
                        + ": as folgas de duas batidas vizinhas se encostam, "
                        + "e aí TODO pulo cai dentro de alguma — o jogo toca, "
                        + "conta ponto e não pode mais ser perdido. Aperte "
                        + "regras.folgas nesta faixa.";
            }
            if (folga < MusicaCatalog.FOLGA_HONESTA) {
                return "A " + MusicaCatalog.bpmDe(tempo) + " bpm a folga cai "
                        + "para ±" + folga + " tique: isso mede a grade de "
                        + "tiques do servidor, e não a pessoa.";
            }

            // A tecla presa vem por último das três: ela é a mais sutil, e um
            // erro grosseiro de folga tem de ser apontado pelo nome dele em
            // vez de sair como "segurar a tecla sobrevive".
            int errosDaTecla = MusicaCatalog.errosDoPuloContinuo(tempo, folga,
                    batidasPorTempo, MusicaCatalog.CICLO_DO_PULO);
            if (errosDaTecla < vidas) {
                return "Na faixa de " + MusicaCatalog.bpmDe(tempo) + " bpm, "
                        + "quem SEGURA a tecla de pulo erra só " + errosDaTecla
                        + " das " + batidasPorTempo + " batidas, e tem "
                        + vidas + " vidas: a tecla presa atravessa a faixa "
                        + "sem ritmo nenhum. Quem segura pula a cada "
                        + MusicaCatalog.CICLO_DO_PULO + " tiques exatos, e "
                        + "numa batida de " + tempo + " com folga de ±" + folga
                        + " esse trilho casa com quase toda batida. Aperte a "
                        + "folga desta faixa.";
            }
        }
        int maisLento = tempos.get(0);
        int folgaLenta = MusicaCatalog.folgaDe(folgas, 0, folgaMinima);
        if (folgaLenta < MusicaCatalog.FOLGA_DO_PING) {
            return "A faixa mais lenta é de " + MusicaCatalog.bpmDe(maisLento)
                    + " bpm e dá só ±" + folgaLenta + " tiques ("
                    + (folgaLenta * 50) + " ms) de folga. A primeira faixa é a "
                    + "que absorve o ping, e precisa de ao menos ±"
                    + MusicaCatalog.FOLGA_DO_PING + " tiques — senão as "
                    + "primeiras batidas são impossíveis para quem joga de "
                    + "longe, o que não aparece para quem testa em casa.";
        }
        return null;
    }

    /** A geometria fecha: um tablado por jogador, e vazio de verdade entre eles. */
    static String conferirNumeros(int tablados, int raio, int anel,
                                  int metronomoRaio, int maximo, int vidas) {
        if (tablados < maximo) {
            return "São " + tablados + " tablados para " + maximo
                    + " jogadores: o tablado É o jogador, com as vidas dele, e "
                    + "dois jogadores no mesmo tablado seriam duas pessoas "
                    + "dividindo " + vidas + " vidas.";
        }
        if (raio < vidas) {
            return "O tablado tem raio " + raio + " e são " + vidas
                    + " vidas: cada vida perdida encolhe o tablado um anel, e "
                    + "com raio menor que as vidas ele acabaria antes delas.";
        }
        double entre = MusicaCatalog.entreVizinhos(tablados, anel);
        double vazio = entre - 2.0 * raio;
        if (vazio < SALTO_DO_JOGADOR) {
            return "Entre dois tablados vizinhos sobram "
                    + Math.round(vazio * 10) / 10.0 + " blocos de vazio e "
                    + "precisam de " + SALTO_DO_JOGADOR + ": um jogador "
                    + "correndo pula quatro, e invadir o tablado do vizinho "
                    + "seria entrar nas vidas dele. Aumente arena.tablado-anel.";
        }
        double atéOMeio = anel - raio - metronomoRaio;
        if (atéOMeio < SALTO_DO_JOGADOR) {
            return "Entre o anel e o metrônomo sobram "
                    + Math.round(atéOMeio) + " blocos de vazio e precisam de "
                    + SALTO_DO_JOGADOR + ": o metrônomo é relógio e painel, "
                    + "não tem vida para perder, e quem conseguisse pular nele "
                    + "ficaria imune.";
        }
        for (int k = 0; k < tablados; k++) {
            int[] poste = MusicaCatalog.posteDoTablado(k, tablados, anel, raio);
            if (MusicaCatalog.tabladoDoPonto(poste[0], poste[1], tablados, anel,
                    raio) >= 0) {
                return "O poste da placa do tablado " + (k + 1) + " nasce "
                        + "dentro de um tablado, em (" + poste[0] + ","
                        + poste[1] + ") — e ele tem de ficar de fora, porque o "
                        + "tablado encolhe.";
            }
        }
        return null;
    }

    /** O compasso de aviso e a escada cabem na partida. */
    static String conferirRelogio(List<Integer> tempos, int batidasPorTempo,
                                  int batidasPorCompasso, int batidasDeAviso,
                                  int tempoLimite) {
        if (batidasDeAviso < batidasPorCompasso) {
            return "O aviso dura " + batidasDeAviso + " batida(s) e o compasso "
                    + "tem " + batidasPorCompasso + ": sem um compasso inteiro "
                    + "tocando de graça, a primeira batida cobra de quem ainda "
                    + "está entendendo de onde vem o som.";
        }
        if (batidasDeAviso >= batidasPorTempo) {
            return "O aviso dura " + batidasDeAviso + " batidas e a faixa de "
                    + "andamento dura " + batidasPorTempo + ": o aviso comeria "
                    + "a faixa mais lenta inteira, que é justamente a que "
                    + "ensina o ritmo.";
        }
        long escada = MusicaCatalog.tiqueDaBatida(
                tempos.size() * batidasPorTempo, tempos, batidasPorTempo);
        long segundos = escada / 20;
        if (segundos > tempoLimite * 2L / 3) {
            return "A escada de andamentos dura " + segundos + "s e o limite "
                    + "da partida é " + tempoLimite + "s: sobraria menos de um "
                    + "terço no andamento mais rápido, que é onde a partida "
                    + "costuma se decidir.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    /** O metrônomo: a ilha do meio, com a coluna que pisca na batida. */
    private void metronomo(World mundo, MusicaCatalog planta) {
        Material piso = planta.cor("metronomo", Material.PURPUR_BLOCK);
        Material beira = planta.cor("metronomo-beira", Material.PURPUR_PILLAR);
        Material coluna = planta.cor("coluna", Material.PURPUR_PILLAR);
        int raio = planta.metronomoRaio();
        int y = planta.topoY();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)) {
                    continue;
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(x, z, raio - 1);
                por(mundo, x, y, z, naBeira ? beira : piso);
            }
        }
        for (int alto = 1; alto < planta.metronomoAltura(); alto++) {
            por(mundo, 0, y + alto, 0, coluna);
        }
        por(mundo, 0, y + planta.metronomoAltura(), 0,
                planta.cor("nucleo", Material.AMETHYST_BLOCK));
    }

    /** Um tablado, no raio pedido. */
    private void tablado(World mundo, MusicaCatalog planta, int indice,
                         int raio) {
        int[] centro = planta.centroDoTablado(indice);
        int y = planta.topoY();
        Material chao = planta.cor("tablado", Material.PURPUR_BLOCK);
        Material beira = planta.cor("tablado-beira", Material.PURPUR_PILLAR);
        Material nucleo = planta.cor("tablado-nucleo", Material.AMETHYST_BLOCK);
        int inteiro = planta.tabladoRaio();

        for (int dx = -inteiro; dx <= inteiro; dx++) {
            for (int dz = -inteiro; dz <= inteiro; dz++) {
                boolean dentro = raio >= 0
                        && ArenaComum.noDiscoMeio(dx, dz, raio);
                if (!dentro) {
                    // Fora do raio de agora: é vazio, e é como o tablado
                    // mostra de longe quantas vidas sobraram.
                    por(mundo, centro[0] + dx, y, centro[1] + dz, Material.AIR);
                    continue;
                }
                Material qual = dx == 0 && dz == 0 ? nucleo
                        : (ArenaComum.noDiscoMeio(dx, dz, raio - 1) ? chao : beira);
                por(mundo, centro[0] + dx, y, centro[1] + dz, qual);
            }
        }
    }

    /** Refaz todos os tablados inteiros. */
    public int refazerTablados(World mundo) {
        MusicaCatalog planta = plugin.musicaCatalog();
        contador = 0;
        for (int k = 0; k < planta.tablados(); k++) {
            tablado(mundo, planta, k, planta.tabladoRaio());
        }
        return contador;
    }

    /**
     * Põe o tablado no raio que as vidas de agora pedem.
     *
     * O encolhimento é o contador de vidas, e ele é de bloco em vez de número
     * na tela de propósito: num anel de dezesseis, saber quem está por cair é
     * metade da graça de assistir, e um número só aparece para quem o tem.
     */
    public int encolherTablado(World mundo, int indice, int vidasAgora) {
        MusicaCatalog planta = plugin.musicaCatalog();
        contador = 0;
        int raio = planta.tabladoRaio() - (planta.vidas() - vidasAgora);
        tablado(mundo, planta, indice, Math.max(-1, raio));
        return contador;
    }

    /** Apaga o tablado: quem está nele cai. */
    public int apagarTablado(World mundo, int indice) {
        MusicaCatalog planta = plugin.musicaCatalog();
        contador = 0;
        tablado(mundo, planta, indice, -1);
        return contador;
    }

    /** Acende ou apaga o núcleo do metrônomo, que é o pisca da batida. */
    public void baterOMetronomo(World mundo, boolean aceso) {
        MusicaCatalog planta = plugin.musicaCatalog();
        Block alvo = mundo.getBlockAt(0, planta.topoY() + planta.metronomoAltura(),
                0);
        Material qual = aceso
                ? planta.cor("nucleo-aceso", Material.SEA_LANTERN)
                : planta.cor("nucleo", Material.AMETHYST_BLOCK);
        if (alvo.getType() != qual) {
            alvo.setType(qual, false);
        }
    }

    /** Um poste com luz do lado de fora de cada tablado. */
    private void postes(World mundo, MusicaCatalog planta) {
        Material poste = planta.cor("poste", Material.WARPED_FENCE);
        Material luz = planta.cor("poste-luz", Material.SHROOMLIGHT);
        Material base = planta.cor("escuro", Material.POLISHED_BLACKSTONE);
        int y = planta.topoY();

        for (int k = 0; k < planta.tablados(); k++) {
            int[] onde = planta.posteDoTablado(k);
            por(mundo, onde[0], y, onde[1], base);
            for (int alto = 1; alto <= SaguaoPadrao.FIADA_DA_PLACA + 1; alto++) {
                por(mundo, onde[0], y + alto, onde[1], poste);
            }
            por(mundo, onde[0], y + SaguaoPadrao.FIADA_DA_PLACA + 2, onde[1], luz);
        }
    }

    /** A placa de cada tablado: o número dele e o que se faz nele. */
    public void placasDosTablados(World mundo, MusicaCatalog planta) {
        for (int k = 0; k < planta.tablados(); k++) {
            int[] onde = planta.posteDoTablado(k);
            BlockFace frente = MusicaCatalog.frenteDaPlaca(onde[0], onde[1]);
            int[] alvo = {onde[0] + frente.getModX(),
                    planta.topoY() + SaguaoPadrao.FIADA_DA_PLACA,
                    onde[1] + frente.getModZ()};
            ArenaComum.escreverPlaca(mundo, alvo, planta.materialDaPlaca(),
                    frente, List.of(
                            plugin.mini().deserialize("<color:#D89BFF><b>"
                                    + (k + 1) + "</b></color>"),
                            plugin.mini().deserialize("<white>PULE</white>"),
                            plugin.mini().deserialize("<gray>na batida</gray>"),
                            plugin.mini().deserialize("<gray>" + planta.vidas()
                                    + " vidas</gray>")));
        }
    }

    /** Quatro torres nas diagonais, fora do anel, para a arena ter escala. */
    private void torres(World mundo, MusicaCatalog planta) {
        Material torre = planta.cor("torre", Material.PURPUR_PILLAR);
        Material luz = planta.cor("torre-luz", Material.SHROOMLIGHT);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int y = planta.topoY();
        int raio = planta.tabladoAnel() + planta.tabladoRaio() + 4;

        for (int k = 0; k < 4; k++) {
            double angulo = Math.PI / 4 + k * Math.PI / 2;
            int x = (int) Math.round(Math.cos(angulo) * raio);
            int z = (int) Math.round(Math.sin(angulo) * raio);
            for (int alto = 0; alto <= planta.marcoAltura(); alto++) {
                por(mundo, x, y + alto, z, alto % 4 == 0 && alto > 0 ? luz : torre);
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, x, y + planta.marcoAltura() + alto, z, rod);
            }
        }
    }

    /**
     * O painel do metrônomo: o andamento de agora, visto de qualquer tablado.
     *
     * Acompanha quem olha ({@link ArenaComum#painelVirado}) porque há
     * dezesseis pessoas em volta dele, em dezesseis direções. Um painel fixo
     * seria legível para uma.
     */
    public void painelDoRitmo(World mundo, MusicaCatalog planta,
                              Component texto) {
        ArenaComum.limparPaineis(mundo, TAG_RITMO);
        Location alvo = new Location(mundo, 0.5,
                planta.topoY() + planta.metronomoAltura() + planta.painelAltura(),
                0.5);
        ArenaComum.painelVirado(mundo, alvo, texto,
                (float) planta.escalaTitulo(), planta.fundoDoTexto(), TAG_RITMO);
    }

    private int contarOcupados(World mundo, MusicaCatalog planta) {
        return ArenaComum.contarOcupados(mundo, planta.alcanceDaObra(),
                planta.fundoDaObra(), planta.tetoDaObra());
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
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.musicaCatalog());
    }

    /** Onde o jogador do tablado nasce: no meio dele, olhando o metrônomo. */
    public static Location pontoDoTablado(World mundo, MusicaCatalog planta,
                                          int indice) {
        int[] centro = planta.centroDoTablado(indice);
        Location pouso = new Location(mundo, centro[0] + 0.5,
                planta.topoY() + 1, centro[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(centro[0], centro[1]));
        pouso.setPitch(0f);
        return pouso;
    }

    /** De onde se assiste: em cima do metrônomo. */
    public static Location pontoDaPlateia(World mundo, MusicaCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.metronomoAltura() + 10, 0.5);
        onde.setYaw(180f);
        onde.setPitch(35f);
        return onde;
    }
}
