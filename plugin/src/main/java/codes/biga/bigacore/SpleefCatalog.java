package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A planta e as regras do Spleef, lidas do spleef.yml.
 *
 * <h2>Um piso só, e por quê</h2>
 *
 * O TNT Run tem três pisos porque lá o chão some sozinho: cair é o estado
 * normal do jogo, e três pisos são três chances contra um relógio que não
 * perdoa. Aqui o chão só some se alguém cavar — trabalho, mira e risco de
 * cavar o próprio apoio. Um buraco que custou isso tudo tem de valer a
 * partida, senão cavar não compensa e todo mundo fica andando em círculo.
 *
 * <h2>O muro</h2>
 *
 * Fica um bloco FORA do disco e não tem beiral. As duas coisas importam: sem
 * muro, perder é andar distraído para fora da beira, o que não é o jogo; com
 * beiral, existe um lugar onde ninguém pode cavar o seu chão, que é pior
 * ainda — vira o único lugar racional para ficar.
 *
 * <h2>O aperto</h2>
 *
 * Passados dois minutos, a arena perde um anel de dez em dez segundos. Sem
 * isso, duas pessoas paradas em pontas opostas empatam para sempre: no TNT
 * Run o chão as elimina sozinho, aqui não há nada que as obrigue a se
 * encontrar.
 */
public final class SpleefCatalog extends CatalogoDeArena {

    public SpleefCatalog(BigaCore plugin) {
        super(plugin, "spleef.yml", "spleef", "spleef");
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    @Override
    public int topoY() {
        return config.getInt("arena.y", 100);
    }

    public int raio() {
        return Math.max(8, config.getInt("arena.raio", 20));
    }

    public int beiraAneis() {
        return Math.max(0, config.getInt("arena.beira-aneis", 2));
    }

    public int muroAltura() {
        return Math.max(3, config.getInt("arena.muro-altura", 6));
    }

    /** Quantas fiadas de queda até o limite de eliminação. */
    public int poco() {
        return Math.max(6, config.getInt("arena.poco", 24));
    }

    /**
     * Abaixo desta altura o jogador está eliminado.
     *
     * Longe do chão de propósito: a queda é metade do prazer do jogo, e um
     * limite colado no piso eliminaria antes de o jogador ter visto o buraco
     * em que caiu.
     */
    @Override
    public int quedaY() {
        return topoY() - poco();
    }

    /** A fiada do lago congelado do fundo. É cenário, abaixo da eliminação. */
    public int lagoY() {
        return quedaY() - Math.max(2, config.getInt("arena.lago-fundo", 6));
    }

    @Override
    public int saguaoY() {
        return topoY() + saguaoAltura();
    }

    public int marcoRecuo() {
        return Math.max(6, config.getInt("arena.marco-recuo", 8));
    }

    public int marcoAltura() {
        return Math.max(10, config.getInt("arena.marco-altura", 26));
    }

    public int marcoDistancia() {
        return raio() + marcoRecuo();
    }

    /** O raio da área que a obra ocupa — é o que o desfazer apaga. */
    @Override
    public int alcanceDaObra() {
        return marcoDistancia() + 6;
    }

    @Override
    public int fundoDaObra() {
        return lagoY() - 3;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4, topoY() + marcoAltura() + 6);
    }

    // ------------------------------------------------------------------
    //  Paleta
    // ------------------------------------------------------------------

    /**
     * Os dois materiais que podem ser cavados, e SÓ eles.
     *
     * A trava mais importante do jogo. Sem ela, "o jogador cavou o bloco que
     * estava mirando" passa a valer para o vidro do muro, para o lago do fundo
     * e para a placa do saguão — e a arena vai sendo desmontada por dentro
     * sem erro nenhum aparecer em lugar nenhum. Com ela, o pior caso é um
     * buraco a mais no chão, que a rodada seguinte refaz.
     */
    public Set<Material> materiaisDoPiso() {
        Set<Material> saida = EnumSet.noneOf(Material.class);
        saida.add(cor("piso", Material.SNOW_BLOCK));
        saida.add(cor("beira", Material.PACKED_ICE));
        return saida;
    }

    // ------------------------------------------------------------------
    //  O kit
    // ------------------------------------------------------------------

    /** A ferramenta que cava — e só ela cava. */
    public Material ferramenta() {
        return cor("ferramenta", Material.IRON_SHOVEL, "kit");
    }

    public int bolasDeNeve() {
        return Math.max(0, Math.min(64, config.getInt("kit.bolas-de-neve", 16)));
    }

    public double empurrao() {
        return Math.max(0.0, Math.min(2.0, config.getDouble("kit.empurrao", 0.55)));
    }

    public double empurraoAlto() {
        return Math.max(0.0, Math.min(1.0,
                config.getDouble("kit.empurrao-alto", 0.24)));
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int paradoAviso() {
        return Math.max(2, config.getInt("regras.parado-aviso", 5));
    }

    /** Depois de tantos segundos parado, o bloco sob os pés cede. */
    public int paradoRacha() {
        return Math.max(paradoAviso() + 1, config.getInt("regras.parado-racha", 9));
    }

    public int apertoSegundos() {
        return Math.max(20, config.getInt("regras.aperto-segundos", 120));
    }

    public int apertoIntervalo() {
        return Math.max(2, config.getInt("regras.aperto-intervalo", 10));
    }

    // ------------------------------------------------------------------
    //  Textos
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    //  Conferências — estáticas e puras, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * O raio que a arena tem depois de tantos segundos de partida.
     *
     * Antes do aperto, o raio inteiro. Depois, um anel a menos a cada
     * intervalo — e nunca menos de um, porque uma arena de raio zero não
     * tem onde pôr o último jogador e a partida acabaria por acidente de
     * conta em vez de por queda.
     */
    public static int raioDoAperto(int segundos, int raio, int apertoSegundos,
                                   int apertoIntervalo) {
        return ArenaComum.raioDoAperto(segundos, raio, apertoSegundos, apertoIntervalo);
    }

    /**
     * Se a coluna (x, z) está fora do disco que ainda existe.
     *
     * É o que o aperto apaga, e é também o que impede o aperto de comer o
     * muro: o muro está em raio+1 e nunca entra na conta.
     */
    public static boolean foraDoApertoAtual(int x, int z, int raioAtual, int raio) {
        return ArenaComum.foraDoApertoAtual(x, z, raioAtual, raio);
    }
}
