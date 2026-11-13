package codes.biga.bigacore;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * A LISTA LATERAL: quem está ganhando, sempre à vista.
 *
 * <h2>Por que ela existe</h2>
 *
 * O placar morava na barra de ação — uma linha, só a SUA pontuação e a SUA
 * colocação, e ela sumia cada vez que qualquer outra mensagem usava a mesma
 * barra. O jogador sabia que estava em terceiro e não sabia de quem, nem por
 * quanto, nem se o segundo estava escapando ou voltando. Num jogo de pontos
 * isso não é informação de enfeite: é a informação que decide se vale a pena
 * correr atrás ou segurar o que se tem.
 *
 * <h2>Por que com times, e não reescrevendo as linhas</h2>
 *
 * O jeito curto é apagar o objetivo e escrever tudo de novo a cada segundo.
 * Ele funciona e PISCA: a cada atualização o cliente vê a lista sumir e
 * voltar, uma vez por segundo, a partida inteira.
 *
 * <p>Aqui cada posição da lista é um TIME fixo, cuja entrada é um código de
 * cor invisível — nada aparece dela — e cujo prefixo carrega a linha inteira
 * que se lê. Trocar o prefixo de um time é uma atualização em pé, sem apagar
 * nada, e a lista muda sem piscar.
 *
 * <h2>Por que o número da direita é escondido</h2>
 *
 * O Minecraft escreve o valor de cada entrada em vermelho na direita, e esse
 * vermelho não se tinge. Com {@code NumberFormat.blank()} ele some, e a linha
 * inteira passa a ser nossa — é o que deixa a pontuação sair na cor do lugar
 * em vez de num vermelho que diz "perigo" ao lado do primeiro colocado.
 *
 * <h2>O quadro é do JOGO, e não o do servidor</h2>
 *
 * Cada arena tem o seu. Pôr a lateral no quadro principal faria os vinte
 * jogos disputarem a mesma faixa lateral e mostraria o placar da MINERAÇÃO a
 * quem está no spawn. Em troca, o que depende de time — o ESCONDE esconde a
 * etiqueta de nome pelo time — tem de morar neste quadro também, senão deixa
 * de valer para quem está vendo a lateral.
 */
public final class PlacarLateral {

    /** O ouro, a prata e o bronze. */
    static final String OURO = "#FFD700";
    static final String PRATA = "#C7D0D8";
    static final String BRONZE = "#CD7F32";

    /** Quem não está no pódio. */
    static final String TINTA = "#C9D1D9";
    static final String SOMBRA = "#6E7681";

    /**
     * Quantas linhas cabem.
     *
     * Quinze é o teto do Minecraft, e não uma escolha. Com mais gente que
     * isso a lista mostra os primeiros e resume o resto numa linha, porque
     * cortar em silêncio faria o décimo-sexto achar que saiu da partida.
     */
    static final int MAXIMO = 15;

    /**
     * As entradas: um código de cor cada, que não desenha nada.
     *
     * Elas precisam ser diferentes entre si — o Minecraft indexa a lista por
     * elas — e precisam ser invisíveis, porque o que se lê vem do prefixo do
     * time. Dezesseis códigos de cor dão dezesseis entradas distintas e
     * nenhum pixel.
     */
    private static final String CORES = "0123456789abcdef";

    private final BigaCore plugin;
    private final String etiqueta;
    private Scoreboard quadro;
    private Objective coluna;
    private final List<Team> faixas = new ArrayList<>();

    public PlacarLateral(BigaCore plugin, String etiqueta) {
        this.plugin = plugin;
        this.etiqueta = etiqueta;
    }

    /** Uma linha da lista: o nome e o que vai escrito à direita dele. */
    public record Linha(String nome, String valor) {
    }

    /**
     * O quadro desta arena, criado na primeira vez que alguém pergunta.
     *
     * Público porque o ESCONDE precisa dele: o time que apaga a etiqueta de
     * nome tem de viver no mesmo quadro que o jogador está vendo, senão o
     * disfarce vira um caixote com o nome em cima.
     */
    public Scoreboard quadro() {
        if (quadro != null) {
            return quadro;
        }
        if (Bukkit.getScoreboardManager() == null) {
            return null;
        }
        quadro = Bukkit.getScoreboardManager().getNewScoreboard();
        coluna = quadro.registerNewObjective("biga_" + etiqueta,
                Criteria.DUMMY, Component.empty());
        coluna.setDisplaySlot(DisplaySlot.SIDEBAR);
        coluna.numberFormat(NumberFormat.blank());
        for (int k = 0; k < MAXIMO; k++) {
            Team faixa = quadro.registerNewTeam("bigalat" + k);
            faixa.addEntry(entradaDe(k));
            faixas.add(faixa);
        }
        return quadro;
    }

    static String entradaDe(int posicao) {
        return "§" + CORES.charAt(posicao % CORES.length()) + "§r";
    }

    public void mostrarPara(Player jogador) {
        Scoreboard meu = quadro();
        if (meu != null) {
            jogador.setScoreboard(meu);
        }
    }

    /** Devolve o jogador ao quadro do servidor: a lateral é só da arena. */
    public void esconderDe(Player jogador) {
        if (Bukkit.getScoreboardManager() != null) {
            jogador.setScoreboard(
                    Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void titulo(String mini) {
        if (quadro() == null) {
            return;
        }
        coluna.displayName(plugin.mini().deserialize(mini));
    }

    /**
     * Redesenha a lista.
     *
     * As linhas que sobram do desenho anterior saem da tela em vez de ficarem
     * penduradas: quando alguém é eliminado numa arena de dezesseis, a última
     * linha tem de sumir, e um prefixo vazio não some sozinho — a entrada
     * dele é que tem de deixar de ter pontuação.
     */
    public void desenhar(List<Linha> linhas) {
        if (quadro() == null) {
            return;
        }
        List<String> escritas = montar(linhas);
        for (int k = 0; k < MAXIMO; k++) {
            String entrada = entradaDe(k);
            if (k < escritas.size()) {
                faixas.get(k).prefix(plugin.mini().deserialize(escritas.get(k)));
                coluna.getScore(entrada).setScore(MAXIMO - k);
            } else {
                faixas.get(k).prefix(Component.empty());
                quadro.resetScores(entrada);
            }
        }
    }

    /** Tira a lista da tela sem desmontar o quadro. */
    public void apagar() {
        desenhar(List.of());
    }

    /**
     * As linhas escritas, do primeiro ao último.
     *
     * Estática e sem servidor para o teste poder ler o que o jogador leria. É
     * aqui que mora a regra do pódio, e ela é uma regra de EMPATE antes de
     * ser de cor: dois com a mesma pontuação dividem o mesmo lugar, senão o
     * segundo e o terceiro aparecem com metais diferentes tendo feito o
     * mesmo — e um dos dois reclama, com razão.
     */
    static List<String> montar(List<Linha> linhas) {
        List<String> saida = new ArrayList<>();
        if (linhas.isEmpty()) {
            return saida;
        }
        boolean corta = linhas.size() > MAXIMO;
        int mostra = corta ? MAXIMO - 1 : linhas.size();

        String valorAnterior = null;
        int lugar = 0;
        for (int k = 0; k < mostra; k++) {
            Linha linha = linhas.get(k);
            if (!linha.valor().equals(valorAnterior)) {
                lugar = k + 1;
                valorAnterior = linha.valor();
            }
            saida.add(escrever(lugar, linha));
        }
        if (corta) {
            saida.add("<color:" + SOMBRA + ">… e mais "
                    + (linhas.size() - mostra) + "</color>");
        }
        return saida;
    }

    /**
     * Uma linha: o lugar, o nome e o número.
     *
     * O pódio ganha um disco na cor do metal em vez do número do lugar. É
     * mais legível de canto de olho — e é de canto de olho que esta lista é
     * lida, no meio de uma partida — do que "1º", "2º", "3º" em cinza igual.
     */
    static String escrever(int lugar, Linha linha) {
        String cor = corDoLugar(lugar);
        String marca = lugar <= 3
                ? "<color:" + cor + ">●</color> "
                : "<color:" + SOMBRA + ">" + lugar + "º</color> ";
        return marca + "<color:" + (lugar <= 3 ? cor : TINTA) + ">"
                + HubBuilder.escapar(linha.nome()) + "</color>"
                + "<color:" + SOMBRA + "> · </color>"
                + "<color:" + (lugar <= 3 ? cor : TINTA) + ">"
                + HubBuilder.escapar(linha.valor()) + "</color>";
    }

    static String corDoLugar(int lugar) {
        return switch (lugar) {
            case 1 -> OURO;
            case 2 -> PRATA;
            case 3 -> BRONZE;
            default -> TINTA;
        };
    }
}
