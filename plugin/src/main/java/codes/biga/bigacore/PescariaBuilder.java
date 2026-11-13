package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

/**
 * Levanta a plataforma e as seis bacias da Pescaria.
 *
 * <h2>O que se confere antes de pôr um bloco</h2>
 *
 * Dois tipos de erro, e o segundo é o que importa.
 *
 * <p>Os GEOMÉTRICOS aparecem olhando, mas só depois de construir: bacia que
 * passa da beira da plataforma, duas bacias que se encostam e viram uma,
 * poste de placa nascido dentro da parede da bacia vizinha.
 *
 * <p>O de EQUILÍBRIO não aparece nunca. Se um poço pagar mais pontos por
 * segundo que os outros, o jogo continua funcionando, bonito, sem erro no
 * log — e os dezesseis jogadores ficam todos em cima daquele poço, e os
 * outros cinco são enfeite caro. É um erro de tabela de pesos, que ninguém
 * confere de cabeça, e por isso {@link #conferirEquilibrio} roda a conta
 * poço por poço e recusa a obra.
 */
public final class PescariaBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_pescaria_painel";

    /** Quanto um poço pode se afastar da média de pontos por segundo. */
    private static final double FOLGA_DO_EQUILIBRIO = 0.12;

    /** Quantas vezes o poço de maior lance tem de pagar mais por peixe. */
    private static final double RITMO_MINIMO = 4.0;

    /** A espessura da parede da bacia. Dois, para não vazar na diagonal. */
    private static final int PAREDE_DA_BACIA = 2;

    private final BigaCore plugin;
    private int contador;

    public PescariaBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        PescariaCatalog planta = plugin.pescariaCatalog();

        String problema = conferirTabelas(planta.pocos(), planta.peixes());
        if (problema == null) {
            problema = conferirBacias(planta.pocos(), planta.raio(),
                    planta.beiraAneis(), planta.pocoRaioMeio(),
                    planta.pocoRaio(), planta.pocoAnel());
        }
        if (problema == null) {
            problema = conferirEquilibrio(planta.pocos(), planta.peixes());
        }
        if (problema == null) {
            problema = conferirLeitos(planta.pocos());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            StringBuilder resumo = new StringBuilder();
            for (PescariaCatalog.Poco poco : planta.pocos()) {
                resumo.append(" · ").append(poco.nome()).append(": ")
                        .append(Math.round(PescariaCatalog.pontosPorSegundo(poco,
                                planta.peixes()) * 100) / 100.0).append(" pts/s");
            }
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Plataforma de raio " + planta.raio() + " na fiada "
                    + planta.topoY() + " com " + planta.pocos().size()
                    + " poços." + resumo + ". Saguão em y " + planta.saguaoY()
                    + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga pescaria desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        deque(mundo, planta);
        for (int k = 0; k < planta.pocos().size(); k++) {
            bacia(mundo, planta, k);
        }
        postes(mundo, planta);
        torres(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);
        placasDosPocos(mundo, planta);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Pescaria levantada: " + contador
                + " blocos e " + planta.pocos().size() + " poços. O spawn do "
                + "mundo foi posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        PescariaCatalog planta = plugin.pescariaCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        contador += ArenaComum.apagarACaixa(mundo, alcance,
                planta.fundoDaObra(), planta.tetoDaObra());
        return new Resultado(true, "Pescaria apagada: " + contador + " blocos.",
                contador);
    }

    @Override
    public Resultado textos(World mundo) {
        PescariaCatalog planta = plugin.pescariaCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        placasDosPocos(mundo, planta);
        return new Resultado(true, "Letreiros e placas dos poços refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    static String conferirTabelas(List<PescariaCatalog.Poco> pocos,
                                  List<PescariaCatalog.Peixe> peixes) {
        if (peixes.isEmpty()) {
            return "A tabela de peixes está vazia: ninguém faria ponto.";
        }
        if (pocos.size() < 2) {
            return "A pescaria tem " + pocos.size() + " poço(s): com menos de "
                    + "dois não há onde escolher, e escolher é o jogo.";
        }
        int antes = 0;
        for (PescariaCatalog.Peixe peixe : peixes) {
            if (peixe.pontos() <= 0) {
                return "O " + peixe.nome() + " vale " + peixe.pontos()
                        + " ponto(s).";
            }
            if (peixe.pontos() < antes) {
                return "A tabela de peixes está fora de ordem no "
                        + peixe.nome() + ": ela é lida do mais barato ao mais "
                        + "caro, e é essa ordem que diz qual é o 'melhor "
                        + "peixe' de um poço na placa dele.";
            }
            antes = peixe.pontos();
        }
        for (PescariaCatalog.Poco poco : pocos) {
            if (poco.pesoTotal() <= 0) {
                return "O poço " + poco.nome() + " não tem peso nenhum: nada "
                        + "morderia lá e a placa dele mentiria.";
            }
            if (poco.esperaMin() <= 0 || poco.esperaMax() < poco.esperaMin()) {
                return "A espera do poço " + poco.nome() + " é de "
                        + poco.esperaMin() + " a " + poco.esperaMax()
                        + " tiques.";
            }
        }
        return null;
    }

    /**
     * Os leitos são blocos cheios.
     *
     * Fica de fora da conferência pura e roda só na obra porque
     * {@code Material#isSolid} consulta o registro de blocos do servidor, que
     * não existe num teste. É o único pedaço da conferência que precisa do
     * servidor de pé — e um leito vazado faz a água escorrer pelo fundo da
     * bacia até o deque, o que vale a chamada.
     */
    private static String conferirLeitos(List<PescariaCatalog.Poco> pocos) {
        for (PescariaCatalog.Poco poco : pocos) {
            if (!poco.leito().isSolid()) {
                return "O leito do poço " + poco.nome() + " é "
                        + poco.leito() + ", que não é bloco cheio: a água "
                        + "vazaria pelo fundo da bacia.";
            }
        }
        return null;
    }

    /** As bacias cabem na plataforma e não se encostam. */
    static String conferirBacias(List<PescariaCatalog.Poco> pocos, int raio,
                                 int beiraAneis, int raioMeio, int pocoRaio,
                                 int anel) {
        int raioLivre = raio - beiraAneis - 1;
        int quantos = pocos.size();
        for (int k = 0; k < quantos; k++) {
            int[] centro = PescariaCatalog.centroDoPoco(k, quantos, anel);
            int borda = PescariaCatalog.raioDoPoco(k, raioMeio, pocoRaio)
                    + PAREDE_DA_BACIA;
            double longe = Math.hypot(centro[0], centro[1]);
            if (longe + borda > raioLivre) {
                return "A bacia do poço " + pocos.get(k).nome()
                        + " chega a " + Math.round(longe + borda) + " do meio e "
                        + "a plataforma só tem " + raioLivre + " de piso: ela "
                        + "ficaria pendurada no vazio.";
            }
            for (int o = k + 1; o < quantos; o++) {
                int[] outro = PescariaCatalog.centroDoPoco(o, quantos, anel);
                double entre = Math.hypot(centro[0] - outro[0], centro[1] - outro[1]);
                int precisa = borda
                        + PescariaCatalog.raioDoPoco(o, raioMeio, pocoRaio)
                        + PAREDE_DA_BACIA + 2;
                if (entre < precisa) {
                    return "As bacias dos poços " + pocos.get(k).nome()
                            + " e " + pocos.get(o).nome() + " estão a "
                            + Math.round(entre) + " uma da outra e precisam de "
                            + precisa + ": elas se encostariam e viravam um "
                            + "poço só, com duas placas e duas esperas.";
                }
            }
            int[] poste = PescariaCatalog.posteDoPoco(k, quantos, anel, raioMeio,
                    pocoRaio);
            if (PescariaCatalog.pocoDoPonto(poste[0], poste[1], quantos, anel,
                    raioMeio, pocoRaio) >= 0) {
                return "O poste da placa do poço " + pocos.get(k).nome()
                        + " nasce dentro de um poço, em (" + poste[0] + ","
                        + poste[1] + ").";
            }
        }
        return null;
    }

    /**
     * Todo poço paga quase o mesmo por segundo, e em lances de tamanhos
     * diferentes.
     *
     * As duas metades são o desenho inteiro do jogo. A primeira recusa o poço
     * estritamente melhor, que faria os outros cinco de enfeite. A segunda
     * recusa seis poços com o mesmo ritmo, que seriam o mesmo poço com seis
     * nomes — e aí escolher onde lançar não decidiria nada.
     */
    static String conferirEquilibrio(List<PescariaCatalog.Poco> pocos,
                                     List<PescariaCatalog.Peixe> peixes) {
        double soma = 0;
        for (PescariaCatalog.Poco poco : pocos) {
            soma += PescariaCatalog.pontosPorSegundo(poco, peixes);
        }
        double media = soma / pocos.size();
        if (media <= 0) {
            return "Nenhum poço rende ponto.";
        }
        for (PescariaCatalog.Poco poco : pocos) {
            double rende = PescariaCatalog.pontosPorSegundo(poco, peixes);
            double desvio = Math.abs(rende - media) / media;
            if (desvio > FOLGA_DO_EQUILIBRIO) {
                return "O poço " + poco.nome() + " rende "
                        + Math.round(rende * 100) / 100.0 + " pontos por "
                        + "segundo e a média dos poços é "
                        + Math.round(media * 100) / 100.0 + " — "
                        + Math.round(desvio * 100) + "% de diferença, e a "
                        + "folga é " + Math.round(FOLGA_DO_EQUILIBRIO * 100)
                        + "%. Com um poço melhor que os outros, os dezesseis "
                        + "jogadores ficam todos nele e os demais são enfeite.";
            }
        }
        double menor = Double.MAX_VALUE;
        double maior = 0;
        for (PescariaCatalog.Poco poco : pocos) {
            double lance = PescariaCatalog.valorEsperado(poco, peixes);
            menor = Math.min(menor, lance);
            maior = Math.max(maior, lance);
        }
        if (menor <= 0 || maior / menor < RITMO_MINIMO) {
            return "O maior lance médio é " + Math.round(maior * 100) / 100.0
                    + " pontos e o menor é " + Math.round(menor * 100) / 100.0
                    + ": só " + Math.round(maior / Math.max(0.01, menor) * 10) / 10.0
                    + " vezes, e o mínimo é " + RITMO_MINIMO + ". Com lances "
                    + "do mesmo tamanho os poços são o mesmo poço com nomes "
                    + "diferentes, e escolher onde lançar deixa de decidir "
                    + "qualquer coisa.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    /** O deque: o disco de piso, menos o que é bacia. */
    private void deque(World mundo, PescariaCatalog planta) {
        Material piso = planta.cor("deque", Material.SMOOTH_STONE);
        Material xadrez = planta.cor("deque-xadrez", Material.POLISHED_DIORITE);
        Material beira = planta.cor("beira", Material.DEEPSLATE_TILES);
        int raio = planta.raio();
        int y = planta.topoY();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)) {
                    continue;
                }
                if (planta.pocoDoPonto(x, z) >= 0) {
                    continue;
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(x, z,
                        raio - planta.beiraAneis());
                Material qual = naBeira ? beira
                        : (((x >> 2) + (z >> 2)) % 2 == 0 ? piso : xadrez);
                por(mundo, x, y, z, qual);
            }
        }
    }

    /**
     * Uma bacia: a parede, o leito e a água.
     *
     * A parede tem dois blocos de espessura por um motivo só: um anel de um
     * bloco desenhado num círculo de inteiros tem furos na diagonal, e furo
     * na parede de bacia é água saindo pelo deque até o servidor inteiro
     * perceber.
     */
    private void bacia(World mundo, PescariaCatalog planta, int indice) {
        PescariaCatalog.Poco poco = planta.pocos().get(indice);
        int[] centro = planta.centroDoPoco(indice);
        int raio = planta.raioDoPoco(indice);
        int y = planta.topoY();
        int fundo = y - poco.fundo();
        Material parede = planta.cor("parede-do-poco", Material.STONE_BRICKS);

        int fora = raio + PAREDE_DA_BACIA;
        for (int dx = -fora; dx <= fora; dx++) {
            for (int dz = -fora; dz <= fora; dz++) {
                if (!ArenaComum.noDiscoMeio(dx, dz, fora)) {
                    continue;
                }
                int x = centro[0] + dx;
                int z = centro[1] + dz;
                boolean naAgua = ArenaComum.noDiscoMeio(dx, dz, raio);
                if (!naAgua) {
                    for (int fiada = fundo; fiada <= y; fiada++) {
                        por(mundo, x, fiada, z, parede);
                    }
                    continue;
                }
                por(mundo, x, fundo, z, poco.leito());
                for (int fiada = fundo + 1; fiada <= y; fiada++) {
                    por(mundo, x, fiada, z, Material.WATER);
                }
            }
        }
    }

    /** Um poste com lanterna ao lado de cada poço, para pendurar a placa. */
    private void postes(World mundo, PescariaCatalog planta) {
        Material poste = planta.cor("poste", Material.SPRUCE_FENCE);
        Material luz = planta.cor("poste-luz", Material.LANTERN);
        int y = planta.topoY();

        for (int k = 0; k < planta.pocos().size(); k++) {
            int[] onde = planta.posteDoPoco(k);
            for (int alto = 1; alto <= SaguaoPadrao.FIADA_DA_PLACA + 1; alto++) {
                por(mundo, onde[0], y + alto, onde[1], poste);
            }
            por(mundo, onde[0], y + SaguaoPadrao.FIADA_DA_PLACA + 2, onde[1], luz);
        }
    }

    /**
     * A placa de cada poço: o nome, a espera e o melhor peixe.
     *
     * É o painel de instrução do jogo, e ele é por POÇO porque é por poço que
     * a decisão existe. Um cartaz único na entrada dizendo "há seis poços com
     * esperas diferentes" não ajuda ninguém a escolher; a placa ao lado da
     * água, com o número da espera e o nome do peixe, ajuda.
     */
    private void placasDosPocos(World mundo, PescariaCatalog planta) {
        for (int k = 0; k < planta.pocos().size(); k++) {
            PescariaCatalog.Poco poco = planta.pocos().get(k);
            int[] onde = planta.posteDoPoco(k);
            PescariaCatalog.Peixe melhor = planta.melhorPeixeDo(poco);
            int[] alvo = {onde[0], planta.topoY() + SaguaoPadrao.FIADA_DA_PLACA,
                    onde[1]};
            // A placa fica pendurada no poste, um bloco à frente dele.
            org.bukkit.block.BlockFace frente =
                    PescariaCatalog.RUMOS_DA_PLACA[onde[2]];
            alvo[0] += frente.getModX();
            alvo[2] += frente.getModZ();
            ArenaComum.escreverPlaca(mundo, alvo, planta.materialDaPlaca(),
                    frente, List.of(
                            plugin.mini().deserialize("<color:#5B8DEF><b>"
                                    + HubBuilder.escapar(poco.nome()) + "</b></color>"),
                            plugin.mini().deserialize("<gray>espera "
                                    + (poco.esperaMin() / 20) + "–"
                                    + (poco.esperaMax() / 20) + "s</gray>"),
                            plugin.mini().deserialize(melhor == null ? "" : "<white>"
                                    + HubBuilder.escapar(melhor.nome()) + "</white>"),
                            plugin.mini().deserialize(melhor == null ? "" : "<gray>"
                                    + melhor.pontos() + " pontos</gray>")));
        }
    }

    /** Quatro torres nas diagonais, longe dos poços, para a arena ter escala. */
    private void torres(World mundo, PescariaCatalog planta) {
        Material torre = planta.cor("torre", Material.PRISMARINE_BRICKS);
        Material luz = planta.cor("torre-luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int y = planta.topoY();
        int raio = planta.raio() - 1;

        for (int k = 0; k < 4; k++) {
            double angulo = Math.PI / 4 + k * Math.PI / 2;
            int x = (int) Math.round(Math.cos(angulo) * raio);
            int z = (int) Math.round(Math.sin(angulo) * raio);
            for (int alto = 1; alto <= planta.marcoAltura(); alto++) {
                por(mundo, x, y + alto, z, alto % 5 == 0 ? luz : torre);
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, x, y + planta.marcoAltura() + alto, z, rod);
            }
        }
    }

    private int contarOcupados(World mundo, PescariaCatalog planta) {
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
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.pescariaCatalog());
    }

    /** Onde um jogador nasce: no deque, entre o poço do meio e o anel. */
    public static Location pontoDaLargada(World mundo, PescariaCatalog planta,
                                          int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1,
                xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(xz[0], xz[1]) + 180f);
        pouso.setPitch(10f);
        return pouso;
    }

    /** De onde se assiste. */
    public static Location pontoDaPlateia(World mundo, PescariaCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.marcoAltura() + 14, 0.5);
        onde.setYaw(180f);
        onde.setPitch(65f);
        return onde;
    }

    /** O meio de um poço, na superfície da água: para o som e a partícula. */
    public static Location pontoDoPoco(World mundo, PescariaCatalog planta,
                                       int indice) {
        int[] centro = planta.centroDoPoco(indice);
        return new Location(mundo, centro[0] + 0.5, planta.topoY() + 1.2,
                centro[1] + 0.5);
    }
}
