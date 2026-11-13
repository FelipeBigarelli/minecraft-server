package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

/**
 * Levanta a montanha do Rei da Montanha.
 *
 * <h2>O que se confere antes de pôr um bloco</h2>
 *
 * Uma montanha tem um jeito só de sair errada e é o pior deles: ficar
 * INESCALÁVEL em alguma direção. Dois degraus de uma vez num ponto e aquele
 * lado da montanha deixa de existir — o jogo continua jogável, só que todo
 * mundo sobe pelo mesmo lugar, e quem chegar primeiro naquele ponto fecha a
 * passagem. Por isso o teste anda os dezesseis rumos e confere degrau por
 * degrau.
 */
public final class ReiBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_rei_painel";

    private final BigaCore plugin;
    private int contador;

    public ReiBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        ReiCatalog planta = plugin.reiCatalog();

        String problema = conferirMontanha(planta.peRaio(), planta.passo(),
                planta.altura(), planta.topoRaio());
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Montanha: chão de raio " + planta.raio() + " na fiada "
                    + planta.topoY() + ", pé em " + planta.peRaio()
                    + ", cume na fiada " + planta.cumeY() + ", topo de raio "
                    + planta.topoRaio() + ". Ganha quem segurar "
                    + planta.segundosParaGanhar() + "s seguidos. Saguão em y "
                    + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga rei desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);

        montanha(mundo, planta);
        pedras(mundo, planta);
        marcos(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Montanha levantada: " + contador
                + " blocos, " + planta.altura() + " fiadas de encosta e "
                + planta.pedras() + " pedras. O spawn do mundo foi posto no "
                + "saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        ReiCatalog planta = plugin.reiCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        contador += ArenaComum.apagarACaixa(mundo, alcance,
                planta.fundoDaObra(), planta.tetoDaObra());
        return new Resultado(true, "Montanha apagada: " + contador + " blocos.",
                contador);
    }

    @Override
    public Resultado textos(World mundo) {
        ReiCatalog planta = plugin.reiCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        return new Resultado(true, "Letreiros refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * A montanha sobe uma fiada de cada vez e tem topo de verdade.
     *
     * A conta de altura é a mesma em toda direção, então basta andar UM raio
     * para conferir todos — o que esta conferência pega é número errado no
     * yml: passo zero (parede), passo maior que o pé (montanha de uma fiada),
     * altura maior do que o raio comporta (cume que vira pilar).
     */
    static String conferirMontanha(int peRaio, int passo, int altura,
                                   int topoRaio) {
        int antes = ReiCatalog.alturaDe(peRaio + 1, peRaio, passo, altura);
        if (antes != 0) {
            return "A montanha não começa no chão: na distância " + (peRaio + 1)
                    + " ela já tem " + antes + " fiadas.";
        }
        for (int r = peRaio; r >= 0; r--) {
            int agora = ReiCatalog.alturaDe(r, peRaio, passo, altura);
            if (agora - antes > 1) {
                return "A montanha sobe " + (agora - antes) + " fiadas de uma "
                        + "vez na distância " + r + ": ninguém escala degrau "
                        + "de dois, e aquele lado da montanha deixa de "
                        + "existir. Suba 'passo' ou baixe 'altura'.";
            }
            if (agora < antes) {
                return "A montanha DESCE na distância " + r + ": a conta de "
                        + "altura não é monótona.";
            }
            antes = agora;
        }
        if (antes < altura) {
            return "A montanha só chega a " + antes + " fiadas das " + altura
                    + " pedidas: o pé de raio " + peRaio + " com passo "
                    + passo + " não dá altura para tanto.";
        }
        int cume = 0;
        for (int r = 0; r <= peRaio; r++) {
            if (ReiCatalog.alturaDe(r, peRaio, passo, altura) >= altura) {
                cume = r;
            }
        }
        if (cume != topoRaio) {
            return "O cume tem raio " + cume + " e a zona do topo tem raio "
                    + topoRaio + ": a zona que conta tem de ser exatamente o "
                    + "chão plano do cume, senão ou sobra chão que não conta "
                    + "ou conta chão que não existe.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    private void montanha(World mundo, ReiCatalog planta) {
        Material chao = planta.material(ReiCatalog.CHAO);
        Material beira = planta.material(ReiCatalog.BEIRA);
        Material encosta = planta.material(ReiCatalog.ENCOSTA);
        Material degrau = planta.material(ReiCatalog.DEGRAU);
        Material topo = planta.material(ReiCatalog.TOPO);
        Material topoBeira = planta.material(ReiCatalog.TOPO_BEIRA);
        int raio = planta.raio();
        int y = planta.topoY();
        int altura = planta.altura();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)) {
                    continue;
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(x, z,
                        raio - planta.beiraAneis());
                por(mundo, x, y, z, naBeira ? beira : chao);

                int r = ReiCatalog.distancia(x, z);
                int alto = planta.alturaDe(r);
                for (int fiada = 1; fiada <= alto; fiada++) {
                    boolean noCume = fiada == altura;
                    boolean superficie = fiada == alto;
                    Material qual;
                    if (noCume) {
                        qual = r >= planta.topoRaio() ? topoBeira : topo;
                    } else {
                        qual = superficie ? degrau : encosta;
                    }
                    por(mundo, x, y + fiada, z, qual);
                }
            }
        }
    }

    /** As pedras da encosta: cobertura, e um degrau a mais para quem souber. */
    private void pedras(World mundo, ReiCatalog planta) {
        Material pedra = planta.material(ReiCatalog.PEDRA);
        Material musgo = planta.material(ReiCatalog.PEDRA_TOPO);
        int y = planta.topoY();

        for (int[] onde : planta.pedrasDaEncosta()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = onde[0] + dx;
                    int z = onde[1] + dz;
                    // A base sai da altura DAQUELA casa, e não da do meio da
                    // pedra: a encosta sobe por baixo dela, e uma pedra toda
                    // na mesma fiada fica meio enterrada de um lado e
                    // flutuando do outro.
                    int base = planta.alturaDe(ReiCatalog.distancia(x, z));
                    for (int alto = 1; alto <= planta.pedraAltura(); alto++) {
                        por(mundo, x, y + base + alto, z,
                                alto == planta.pedraAltura() ? musgo : pedra);
                    }
                }
            }
        }
    }

    /** Quatro marcos em volta, para a montanha ter escala. */
    private void marcos(World mundo, ReiCatalog planta) {
        Material estrutura = planta.cor("estrutura",
                Material.POLISHED_BLACKSTONE_BRICKS);
        Material vidro = planta.cor("vidro", Material.YELLOW_STAINED_GLASS);
        Material luz = planta.cor("luz", Material.SHROOMLIGHT);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int d = planta.marcoDistancia();
        int altura = planta.marcoAltura();
        int y = planta.topoY();

        for (int[] eixo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int cx = eixo[0] * d;
            int cz = eixo[1] * d;
            for (int alto = -4; alto <= altura; alto++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean miolo = dx == 0 && dz == 0;
                        Material qual = miolo && alto % 5 == 0 ? luz
                                : (miolo ? vidro : estrutura);
                        por(mundo, cx + dx, y + alto, cz + dz, qual);
                    }
                }
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, cx, y + altura + alto, cz, rod);
            }
        }
    }

    private int contarOcupados(World mundo, ReiCatalog planta) {
        return ArenaComum.contarOcupados(mundo, planta.alcanceDaObra(),
                planta.fundoDaObra(), planta.tetoDaObra());
    }

    private void por(World mundo, int x, int y, int z, Material material) {
        mundo.getBlockAt(x, y, z).setType(material, false);
        contador++;
    }

    // ------------------------------------------------------------------
    //  Pontos
    // ------------------------------------------------------------------

    @Override
    public Location pontoDeEntrada(World mundo) {
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.reiCatalog());
    }

    /** Onde um jogador nasce: no chão, ao pé da montanha, olhando para ela. */
    public static Location pontoDaLargada(World mundo, ReiCatalog planta,
                                          int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1,
                xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(xz[0], xz[1]));
        pouso.setPitch(0f);
        return pouso;
    }

    /** De onde se assiste, e para onde volta quem cai no vazio. */
    public static Location pontoDaPlateia(World mundo, ReiCatalog planta) {
        Location onde = new Location(mundo, 0.5, planta.cumeY() + 16, 0.5);
        onde.setYaw(180f);
        onde.setPitch(60f);
        return onde;
    }

    /** O meio do topo, para a partícula da coroa. */
    public static Location pontoDoTopo(World mundo, ReiCatalog planta) {
        return new Location(mundo, 0.5, planta.cumeY() + 1, 0.5);
    }

    /**
     * Para onde volta quem caiu: um ponto aleatório do chão, ao pé da
     * montanha.
     *
     * Aleatório de propósito. Devolver sempre no mesmo lugar faria da queda
     * uma rota: o rei derrubado reapareceria sempre do mesmo lado, e quem
     * soubesse disso esperaria ali.
     */
    public static Location pontoDeVolta(World mundo, ReiCatalog planta,
                                        java.util.Random sorte) {
        List<int[]> pontos = planta.largadas(12);
        int[] onde = pontos.get(sorte.nextInt(pontos.size()));
        return pontoDaLargada(mundo, planta, onde);
    }
}
