package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.List;

/**
 * Levanta o disco de seis fatias e o banco do meio da SORTE.
 *
 * <h2>A conferência que não é geométrica</h2>
 *
 * {@link #conferirPremios} roda a conta de parada ótima da tabela de prêmios
 * e recusa a obra se o topo cair na ponta. É a única conferência do jogo que
 * não se vê olhando a arena, e é a que decide se existe jogo:
 *
 * <ul>
 *   <li><b>Topo na primeira rodada.</b> O certo é guardar na primeira e sair,
 *       e a partida inteira é dezesseis pessoas pulando no banco aos quinze
 *       segundos.</li>
 *   <li><b>Topo na última.</b> O certo é nunca sair, o banco do meio nunca é
 *       usado, e o jogo volta a ser moeda ao ar — sobrevive quem o dado não
 *       escolher dez vezes seguidas.</li>
 * </ul>
 *
 * <p>Com os números de hoje o topo fica na rodada 8 de 10 e a curva é quase
 * plana da 7 à 10, o que é o alvo: a conta não decide por ninguém, e quem
 * decide é quanto os outros já guardaram.
 */
public final class SorteBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_sorte_painel";

    /** O painel do dado, refeito a cada rodada. */
    public static final String TAG_DADO = "bigacore_sorte_dado";

    /** Quanto a coroa precisa de largura para caber pingo e corrida. */
    private static final int COROA_MINIMA = 10;

    /** Quantas vezes o topo da curva tem de valer o de sair na primeira. */
    private static final double GANHO_DE_JOGAR = 2.0;

    private final BigaCore plugin;
    private int contador;

    public SorteBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        SorteCatalog planta = plugin.sorteCatalog();

        String problema = conferirNumeros(planta.raio(), planta.bancoRaio(),
                planta.setores(), planta.pingoRaio(), planta.pingoPasso());
        if (problema == null) {
            problema = conferirPremios(planta.premios(), planta.setores());
        }
        if (problema == null) {
            problema = conferirRelogio(planta.rodadas(), planta.tempoEscolha(),
                    planta.tempoRevelacao(), planta.quedaTiques(),
                    planta.tempoLimite());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            int sair = SorteCatalog.melhorHoraDeSair(planta.premios(),
                    planta.setores());
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Disco de raio " + planta.raio() + " com "
                    + planta.setores() + " fatias e banco de raio "
                    + planta.bancoRaio() + ", na fiada " + planta.topoY()
                    + ". " + planta.rodadas() + " rodadas, prêmios "
                    + planta.premios() + ", melhor hora de sair: rodada "
                    + sair + ". Saguão em y " + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga sorte desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_DADO);
        banco(mundo, planta);
        for (int k = 0; k < planta.setores(); k++) {
            fatia(mundo, planta, k, true);
        }
        postes(mundo, planta);
        torres(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);
        placasDasFatias(mundo, planta);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "SORTE levantada: " + contador + " blocos e "
                + planta.setores() + " fatias. O spawn do mundo foi posto no "
                + "saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        SorteCatalog planta = plugin.sorteCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_DADO);
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
        return new Resultado(true, "SORTE apagada: " + contador + " blocos.",
                contador);
    }

    @Override
    public Resultado textos(World mundo) {
        SorteCatalog planta = plugin.sorteCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_DADO);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        placasDasFatias(mundo, planta);
        return new Resultado(true, "Letreiros e placas das fatias refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /** A geometria fecha: coroa larga, pingos dentro dela, banco no meio. */
    static String conferirNumeros(int raio, int bancoRaio, int setores,
                                  int pingoRaio, int pingoPasso) {
        if (setores < 2) {
            return "O dado tem " + setores + " face(s): com menos de duas não "
                    + "há o que escolher.";
        }
        if (setores > SorteCatalog.PINGOS.length) {
            return "O dado tem " + setores + " faces e só há desenho de pingo "
                    + "para " + SorteCatalog.PINGOS.length + ".";
        }
        int coroa = raio - bancoRaio;
        if (coroa < COROA_MINIMA) {
            return "A coroa das fatias tem " + coroa + " blocos de largura e "
                    + "precisa de " + COROA_MINIMA + ": é nela que cabem os "
                    + "pingos da face e a corrida de quem troca de fatia nos "
                    + "últimos segundos.";
        }
        int meiaGrade = pingoPasso;
        if (pingoRaio - meiaGrade <= bancoRaio
                || pingoRaio + meiaGrade >= raio) {
            return "Os pingos da face ficam no raio " + pingoRaio + " e a "
                    + "grade deles ocupa " + (meiaGrade * 2 + 1)
                    + " blocos: ela sairia da coroa, que vai de "
                    + (bancoRaio + 1) + " a " + raio + ".";
        }
        // O pingo de uma fatia não pode cair na fatia vizinha: a largura do
        // arco no raio do pingo tem de caber a grade com folga.
        double arco = 2 * Math.PI * pingoRaio / setores;
        if (arco < (meiaGrade * 2 + 1) + 2) {
            return "No raio " + pingoRaio + " cada fatia tem "
                    + Math.round(arco) + " blocos de arco e a grade de pingos "
                    + "pede " + ((meiaGrade * 2 + 1) + 2) + ": os pingos de "
                    + "uma face invadiriam a face vizinha.";
        }
        for (int face = 1; face <= setores; face++) {
            int quantos = 0;
            for (int coluna = 0; coluna < 3; coluna++) {
                for (int fiada = 0; fiada < 3; fiada++) {
                    if (SorteCatalog.temPingo(face, coluna, fiada)) {
                        quantos++;
                    }
                }
            }
            if (quantos != face) {
                return "A face " + face + " está desenhada com " + quantos
                        + " pingo(s).";
            }
        }
        return null;
    }

    /**
     * A tabela de prêmios deixa a melhor hora de sair no meio da partida.
     *
     * É a conferência que decide se o banco do meio é jogo ou enfeite, e ela
     * não se vê na arena: a curva do valor esperado sobe enquanto o prêmio
     * crescente compensa o risco acumulado e desce quando não compensa mais.
     * Se o topo cai na primeira rodada, o certo é guardar e sair na hora; se
     * cai na última, o certo é nunca sair.
     */
    static String conferirPremios(List<Integer> premios, int setores) {
        if (premios.isEmpty()) {
            return "A tabela de prêmios está vazia.";
        }
        for (int premio : premios) {
            if (premio <= 0) {
                return "Há uma rodada que paga " + premio + " ponto(s).";
            }
        }
        int topo = SorteCatalog.melhorHoraDeSair(premios, setores);
        if (topo <= 1) {
            return "A melhor hora de sair é a rodada " + topo + " de "
                    + premios.size() + ": com o prêmio subindo tão devagar, o "
                    + "certo é guardar na primeira e sair, e a partida vira "
                    + "dezesseis pessoas pulando no banco aos quinze segundos. "
                    + "Aumente regras.premio-passo.";
        }
        if (topo >= premios.size()) {
            return "A melhor hora de sair é a última rodada (" + topo + " de "
                    + premios.size() + "): com o prêmio subindo tão rápido, o "
                    + "certo é nunca sair, o banco do meio nunca é usado e o "
                    + "jogo volta a ser moeda ao ar. Diminua "
                    + "regras.premio-passo ou acrescente rodadas.";
        }
        double noTopo = SorteCatalog.evDeSair(topo, premios, setores);
        double naPrimeira = SorteCatalog.evDeSair(1, premios, setores);
        if (naPrimeira > 0 && noTopo / naPrimeira < GANHO_DE_JOGAR) {
            return "Sair no topo (rodada " + topo + ") vale "
                    + Math.round(noTopo * 10) / 10.0 + " e sair na primeira "
                    + "vale " + Math.round(naPrimeira * 10) / 10.0 + " — só "
                    + Math.round(noTopo / naPrimeira * 10) / 10.0 + " vezes, e "
                    + "o mínimo é " + GANHO_DE_JOGAR + ". Sem ganho em "
                    + "continuar, arriscar é burrice e o jogo não tem tensão.";
        }
        return null;
    }

    /** As rodadas cabem no tempo limite, e a queda cabe na revelação. */
    static String conferirRelogio(int rodadas, int tempoEscolha,
                                  int tempoRevelacao, int quedaTiques,
                                  int tempoLimite) {
        if (quedaTiques >= tempoRevelacao * 20) {
            return "A queda leva " + quedaTiques + " tiques e a revelação dura "
                    + (tempoRevelacao * 20) + ": quem caiu seria tirado da "
                    + "rodada depois de a seguinte já ter começado.";
        }
        int precisa = rodadas * (tempoEscolha + tempoRevelacao);
        if (precisa > tempoLimite) {
            return "As " + rodadas + " rodadas levam " + precisa
                    + "s e o tempo limite é " + tempoLimite + "s: as últimas "
                    + "nunca aconteceriam, e é justamente nelas que o prêmio "
                    + "fica grande e sair fica difícil.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    /** O banco: o patamar do meio, um degrau acima do disco. */
    private void banco(World mundo, SorteCatalog planta) {
        Material piso = planta.cor("banco", Material.WAXED_EXPOSED_CUT_COPPER);
        Material beira = planta.cor("banco-beira", Material.WAXED_CUT_COPPER);
        Material nucleo = planta.cor("banco-nucleo", Material.GOLD_BLOCK);
        int raio = planta.bancoRaio();
        int y = planta.topoY() + planta.bancoDegrau();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)) {
                    continue;
                }
                for (int fiada = planta.topoY(); fiada < y; fiada++) {
                    por(mundo, x, fiada, z, beira);
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(x, z, raio - 1);
                boolean noMeio = ArenaComum.noDiscoMeio(x, z, 1);
                por(mundo, x, y, z, noMeio ? nucleo : (naBeira ? beira : piso));
            }
        }
    }

    /**
     * Uma fatia: o piso, o risco que a separa da vizinha e os pingos da face.
     *
     * O risco sai de comparar o setor de cada bloco com o do vizinho, em vez
     * de um ângulo arredondado: assim a linha é exatamente onde
     * {@link SorteCatalog#setorDoPonto} muda de fatia, e o jogador que pisa
     * no risco está do lado que o jogo vai contar.
     */
    private void fatia(World mundo, SorteCatalog planta, int setor,
                       boolean comPingos) {
        Material piso = planta.cor("fatia", Material.SMOOTH_SANDSTONE);
        Material xadrez = planta.cor("fatia-xadrez", Material.SANDSTONE);
        Material risco = planta.cor("risco", Material.WAXED_EXPOSED_CUT_COPPER);
        Material beira = planta.cor("beira", Material.WAXED_WEATHERED_CUT_COPPER);
        Material pingo = planta.cor("pingo", Material.POLISHED_BLACKSTONE);
        int raio = planta.raio();
        int y = planta.topoY();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                // O MEIO do bloco, e não o canto dele.
                //
                // O jogo julga o jogador pela posição exata dos pés, que
                // fica perto do meio do bloco; o construtor desenhava pelo
                // canto. Perto de uma divisa isso faz o bloco ser PINTADO
                // como de uma fatia e CONTADO como da outra — o jogador cai
                // estando na fatia que ele via no chão.
                if (setorDoBloco(planta, x, z) != setor) {
                    continue;
                }
                Material qual;
                if (!ArenaComum.noDiscoMeio(x, z, raio - 1)) {
                    qual = beira;
                } else if (setorDoBloco(planta, x + 1, z) != setor
                        || setorDoBloco(planta, x, z + 1) != setor
                        || setorDoBloco(planta, x - 1, z) != setor
                        || setorDoBloco(planta, x, z - 1) != setor) {
                    qual = risco;
                } else {
                    qual = ((x >> 2) + (z >> 2)) % 2 == 0 ? piso : xadrez;
                }
                por(mundo, x, y, z, qual);
            }
        }
        if (!comPingos) {
            return;
        }
        int[] meio = planta.pontoDaFatia(setor, planta.pingoRaio());
        int passo = planta.pingoPasso();
        for (int coluna = 0; coluna < 3; coluna++) {
            for (int fiada = 0; fiada < 3; fiada++) {
                if (!SorteCatalog.temPingo(setor + 1, coluna, fiada)) {
                    continue;
                }
                int x = meio[0] + (coluna - 1) * passo;
                int z = meio[1] + (fiada - 1) * passo;
                if (setorDoBloco(planta, x, z) == setor) {
                    por(mundo, x, y, z, pingo);
                }
            }
        }
    }

    /**
     * A fatia a que um BLOCO pertence.
     *
     * Pelo meio do bloco, que é onde o jogador pisa. Ver a nota em
     * {@link #fatia}: canto e meio discordam numa faixa de meio bloco de
     * cada divisa, e essa faixa é chão pintado de uma cor que some com a
     * outra.
     */
    static int setorDoBloco(SorteCatalog planta, int x, int z) {
        return planta.setorDoPonto(x + 0.5, z + 0.5);
    }

    /** Refaz as fatias que sumiram na rodada anterior. */
    public int refazerFatias(World mundo) {
        SorteCatalog planta = plugin.sorteCatalog();
        contador = 0;
        for (int k = 0; k < planta.setores(); k++) {
            fatia(mundo, planta, k, true);
        }
        return contador;
    }

    /** Apaga uma fatia: quem estava nela cai. */
    public int apagarFatia(World mundo, int setor) {
        SorteCatalog planta = plugin.sorteCatalog();
        int raio = planta.raio();
        int y = planta.topoY();
        contador = 0;
        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (setorDoBloco(planta, x, z) == setor) {
                    por(mundo, x, y, z, Material.AIR);
                }
            }
        }
        return contador;
    }

    /** Um poste com lanterna do lado de fora de cada fatia. */
    private void postes(World mundo, SorteCatalog planta) {
        Material poste = planta.cor("poste", Material.ACACIA_FENCE);
        Material luz = planta.cor("poste-luz", Material.LANTERN);
        Material base = planta.cor("escuro", Material.POLISHED_BLACKSTONE);
        int y = planta.topoY();

        for (int k = 0; k < planta.setores(); k++) {
            int[] onde = planta.posteDaFatia(k);
            por(mundo, onde[0], y, onde[1], base);
            for (int alto = 1; alto <= SaguaoPadrao.FIADA_DA_PLACA + 1; alto++) {
                por(mundo, onde[0], y + alto, onde[1], poste);
            }
            por(mundo, onde[0], y + SaguaoPadrao.FIADA_DA_PLACA + 2, onde[1], luz);
        }
    }

    /**
     * A placa de cada fatia: a face, a chance e como o prêmio se divide.
     *
     * A chance está escrita em todas as seis porque ela é IGUAL nas seis, e
     * dizer isso é o painel de instrução deste jogo: quem chega procurando a
     * fatia mais segura tem de ler, nas seis placas, que não existe fatia
     * mais segura — o que existe é fatia mais vazia.
     */
    public void placasDasFatias(World mundo, SorteCatalog planta) {
        for (int k = 0; k < planta.setores(); k++) {
            int[] onde = planta.posteDaFatia(k);
            BlockFace frente = SorteCatalog.frenteDaPlaca(onde[0], onde[1]);
            int[] alvo = {onde[0] + frente.getModX(),
                    planta.topoY() + SaguaoPadrao.FIADA_DA_PLACA,
                    onde[1] + frente.getModZ()};
            ArenaComum.escreverPlaca(mundo, alvo, planta.materialDaPlaca(),
                    frente, List.of(
                            plugin.mini().deserialize("<color:#E8A87C><b>FACE "
                                    + (k + 1) + "</b></color>"),
                            plugin.mini().deserialize("<white>1 em "
                                    + planta.setores() + "</white>"),
                            plugin.mini().deserialize("<gray>como todas</gray>"),
                            plugin.mini().deserialize(
                                    "<gray>prêmio dividido</gray>")));
        }
    }

    /** Quatro torres fora do disco, para a arena ter escala. */
    private void torres(World mundo, SorteCatalog planta) {
        Material torre = planta.cor("torre", Material.WAXED_EXPOSED_CUT_COPPER);
        Material luz = planta.cor("torre-luz", Material.LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int y = planta.topoY();
        int raio = planta.raio() + 6;

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

    /** O painel do dado, acima do banco, virado para quem olha. */
    public void painelDoDado(World mundo, SorteCatalog planta, Component texto) {
        ArenaComum.limparPaineis(mundo, TAG_DADO);
        Location alvo = new Location(mundo, 0.5,
                planta.topoY() + planta.bancoDegrau() + planta.painelAltura(),
                0.5);
        ArenaComum.painelVirado(mundo, alvo, texto,
                (float) planta.escalaTitulo(), planta.fundoDoTexto(), TAG_DADO);
    }

    private int contarOcupados(World mundo, SorteCatalog planta) {
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
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.sorteCatalog());
    }

    /** Onde um jogador nasce: na coroa, olhando para o banco. */
    public static Location pontoDaLargada(World mundo, SorteCatalog planta,
                                          int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1,
                xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(xz[0], xz[1]));
        pouso.setPitch(5f);
        return pouso;
    }

    /** De onde se assiste: em cima do banco. */
    public static Location pontoDaPlateia(World mundo, SorteCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.marcoAltura() + 12, 0.5);
        onde.setYaw(180f);
        onde.setPitch(60f);
        return onde;
    }

    /** O meio de uma fatia, para o som e a partícula. */
    public static Location pontoDaFatia(World mundo, SorteCatalog planta,
                                        int setor) {
        int[] onde = planta.pontoDaFatia(setor, planta.pingoRaio());
        return new Location(mundo, onde[0] + 0.5, planta.topoY() + 1.2,
                onde[1] + 0.5);
    }
}
