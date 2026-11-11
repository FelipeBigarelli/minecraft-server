package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Volta para um terreno comprado, como o /home faz com a casa.
 *
 * <h2>Por que nao bastava o /home</h2>
 *
 * O /home do Essentials marca um PONTO que o jogador escolheu. O terreno e
 * uma AREA que ele pagou. Sao coisas diferentes: da para ter terreno e nunca
 * ter dado /sethome dentro dele, e e exatamente o que acontece com quem compra
 * o terreno e sai para minerar antes de construir qualquer coisa.
 *
 * <h2>Procura em todos os mundos, nao so no de agora</h2>
 *
 * {@code TerrenoVenda.meus} olha so o mundo em que o jogador esta, porque e
 * disso que o limite por jogador precisa. Aqui seria o defeito principal: o
 * jogador esta no bairro de lotes, pede para voltar para o terreno dele, que
 * fica no {@code world}, e ouviria "voce nao tem terreno". Entao a busca passa
 * por todos os mundos da lista branca do terrenos.yml.
 *
 * <h2>Onde ele aterrissa</h2>
 *
 * No centro da caixa — que e exatamente onde ele estava de pe quando comprou,
 * porque a compra desenha o quadrado em volta do jogador. So o Y se perde, e
 * esse e procurado na hora: superficie primeiro, e se o centro estiver dentro
 * de pedra, em lava ou sem teto, anda em aneis para fora ate achar chao.
 *
 * Nunca chuta uma coordenada: se nenhum ponto servir, o comando recusa com o
 * motivo. Teleportar para dentro de rocha sufoca o jogador e ele perde o que
 * esta carregando — o mesmo prejuizo que ja aconteceu uma vez por inventario.
 */
public final class TerrenoTeleporte {

    /** Um terreno e o mundo onde ele esta — o {@link Terreno} sozinho nao sabe. */
    public record Destino(World mundo, Terreno terreno) {
    }

    /**
     * Distancia entre dois pontos de prova do anel.
     *
     * Quatro blocos: fino o bastante para nao pular uma clareira dentro de uma
     * floresta, grosso o bastante para um terreno de 64x64 custar dezenas de
     * sondagens, e nao milhares.
     */
    static final int PASSO = 4;

    private TerrenoTeleporte() {
    }

    // ------------------------------------------------------------------
    // Quais terrenos sao dele
    // ------------------------------------------------------------------

    public static List<Destino> meusTerrenos(BigaCore plugin, Player jogador) {
        if (!plugin.terrenoDisponivel()) {
            return List.of();
        }

        String prefixo = plugin.terrenoCatalog().prefixo();
        List<Destino> achados = new ArrayList<>();
        for (String nome : plugin.terrenoCatalog().mundos()) {
            World mundo = Bukkit.getWorld(nome);
            if (mundo == null) {
                continue;
            }
            for (Terreno terreno : plugin.worldGuard()
                    .terrenosDe(mundo, jogador.getUniqueId(), prefixo)) {
                achados.add(new Destino(mundo, terreno));
            }
        }
        return achados;
    }

    // ------------------------------------------------------------------
    // Qual deles o jogador pediu
    // ------------------------------------------------------------------

    /**
     * Casa o que o jogador digitou com um id de terreno.
     *
     * Tres formas, da mais exata para a mais solta:
     *
     * <ol>
     *   <li>o id inteiro, {@code t_zbiga_2};</li>
     *   <li>so o numero, {@code 2} — e o que aparece no /terreno listar e o
     *       que qualquer um vai tentar primeiro;</li>
     *   <li>um pedaco do fim do id, aceito so quando casa com um unico
     *       terreno.</li>
     * </ol>
     *
     * A terceira e a que precisa do cuidado: com dois terrenos casando, voltar
     * para o errado e pior do que nao voltar, porque o jogador so descobre
     * depois de ja estar em outro lugar. Em duvida, devolve vazio e quem chama
     * lista as opcoes.
     */
    static Optional<String> idEscolhido(List<String> ids, String pedido) {
        if (ids == null || ids.isEmpty() || pedido == null || pedido.isBlank()) {
            return Optional.empty();
        }
        String alvo = pedido.trim().toLowerCase(Locale.ROOT);

        for (String id : ids) {
            if (id.toLowerCase(Locale.ROOT).equals(alvo)) {
                return Optional.of(id);
            }
        }

        if (alvo.chars().allMatch(Character::isDigit)) {
            int numero;
            try {
                numero = Integer.parseInt(alvo);
            } catch (NumberFormatException grande) {
                return Optional.empty();
            }
            String unico = null;
            for (String id : ids) {
                if (TerrenoVenda.slotDoId(id) == numero) {
                    if (unico != null) {
                        return Optional.empty();
                    }
                    unico = id;
                }
            }
            return Optional.ofNullable(unico);
        }

        String unico = null;
        for (String id : ids) {
            if (id.toLowerCase(Locale.ROOT).endsWith(alvo)) {
                if (unico != null) {
                    return Optional.empty();
                }
                unico = id;
            }
        }
        return Optional.ofNullable(unico);
    }

    // ------------------------------------------------------------------
    // Onde pisar
    // ------------------------------------------------------------------

    /**
     * Pontos de prova: o centro primeiro, depois aneis para fora.
     *
     * O centro vem antes de tudo porque e o lugar onde o jogador estava quando
     * comprou — quase sempre e a casa dele. Os aneis sao a saida para quando
     * esse ponto virou mina, piscina ou parede.
     *
     * Um bloco de folga para dentro deixa o jogador longe da cerca: aterrissar
     * em cima do poste da uma queda boba, e aterrissar colado na borda com
     * folga-entre-terrenos: 0 poria ele dentro do terreno do vizinho ao dar um
     * passo.
     */
    static List<Caixa.Ponto> candidatos(Caixa caixa) {
        Caixa dentro = caixa.larguraX() > 2 && caixa.larguraZ() > 2
                ? caixa.inflada(-1) : caixa;

        List<Caixa.Ponto> saida = new ArrayList<>();
        saida.add(new Caixa.Ponto(dentro.centroX(), dentro.centroZ()));

        int alcance = Math.max(dentro.larguraX(), dentro.larguraZ()) / 2;
        for (int raio = PASSO; raio <= alcance; raio += PASSO) {
            for (int dx = -raio; dx <= raio; dx += PASSO) {
                for (int dz = -raio; dz <= raio; dz += PASSO) {
                    // So a casca do anel: o miolo ja foi sondado na volta anterior.
                    if (Math.abs(dx) != raio && Math.abs(dz) != raio) {
                        continue;
                    }
                    int x = dentro.centroX() + dx;
                    int z = dentro.centroZ() + dz;
                    if (dentro.contemHorizontal(x, z)) {
                        saida.add(new Caixa.Ponto(x, z));
                    }
                }
            }
        }
        return saida;
    }

    /**
     * Bloco em que ninguem quer cair de pe.
     *
     * Lista curta de proposito: o que mata ou machuca so de pisar. Nao entra
     * aqui bloco que da dano por contato lateral (cacto ja entra, folha nao),
     * nem agua — cair na propria piscina e so molhado.
     */
    static boolean pisoPerigoso(Material material) {
        return switch (material) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, CAMPFIRE,
                 SOUL_CAMPFIRE, SWEET_BERRY_BUSH, POWDER_SNOW, WITHER_ROSE -> true;
            default -> false;
        };
    }

    /** Da para ficar de pe com os pes em y? Chao solido embaixo e dois vaos livres. */
    static boolean cabeDePe(World mundo, int x, int y, int z) {
        if (y - 1 < mundo.getMinHeight() || y + 1 >= mundo.getMaxHeight()) {
            return false;
        }
        Block chao = mundo.getBlockAt(x, y - 1, z);
        if (!chao.getType().isSolid() || pisoPerigoso(chao.getType())) {
            return false;
        }
        Block pes = mundo.getBlockAt(x, y, z);
        Block cabeca = mundo.getBlockAt(x, y + 1, z);
        return !pes.getType().isSolid() && !pisoPerigoso(pes.getType())
                && !cabeca.getType().isSolid() && !pisoPerigoso(cabeca.getType());
    }

    /**
     * Procura chao numa coluna.
     *
     * Comeca na superficie e desce. Descer importa por causa de quem cobriu o
     * terreno com telhado: o ponto mais alto e a telha, e o chao de casa esta
     * uns blocos abaixo. Dezesseis blocos de busca pegam a casa sem atravessar
     * o mundo inteiro ate uma caverna.
     *
     * No Nether o topo e a tampa de bedrock, e parar la seria entalar o jogador
     * no teto. Por isso a busca comeca abaixo dela.
     */
    static Optional<Integer> alturaDePouso(World mundo, Caixa caixa, int x, int z) {
        int teto = mundo.getEnvironment() == World.Environment.NETHER
                ? Math.min(120, mundo.getMaxHeight() - 2)
                : mundo.getHighestBlockYAt(x, z) + 1;

        int topo = Math.min(teto, caixa.maxY());
        int fundo = Math.max(caixa.minY() + 1, topo - 16);

        for (int y = topo; y >= fundo; y--) {
            if (cabeDePe(mundo, x, y, z)) {
                return Optional.of(y);
            }
        }
        return Optional.empty();
    }

    /** O lugar de chegada, ou vazio se o terreno inteiro estiver intransitavel. */
    public static Optional<Location> pouso(World mundo, Caixa caixa, Location olhar) {
        for (Caixa.Ponto ponto : candidatos(caixa)) {
            Optional<Integer> y = alturaDePouso(mundo, caixa, ponto.x(), ponto.z());
            if (y.isPresent()) {
                // +0.5 centraliza no bloco. Sem isso o jogador nasce na quina e
                // escorrega para o bloco vizinho, que pode ser o buraco de onde
                // fugimos.
                Location destino = new Location(mundo, ponto.x() + 0.5,
                        y.get(), ponto.z() + 0.5);
                if (olhar != null) {
                    destino.setYaw(olhar.getYaw());
                    destino.setPitch(olhar.getPitch());
                }
                return Optional.of(destino);
            }
        }
        return Optional.empty();
    }
}
