package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Ponte pequena e isolada para o CreativePlots.
 *
 * Mesma decisão do {@link VaultEconomyBridge} e do {@link ChestShopCapGuard}:
 * o BigaCore NÃO compila contra o plugin de terceiro. Adicionar o CreativePlots
 * ao build amarraria o projeto a uma versão específica de um jar que não está
 * em nenhum repositório Maven público, e quebraria o CI junto.
 *
 * <h2>Por que só leitura</h2>
 *
 * O {@code PlotAPI} do CreativePlots 5.1 expõe apenas consultas — não existe
 * {@code claimPlot()} nem evento de claim cancelável. Por isso a cobrança não
 * acontece "dentro" do claim: o {@link PlotPurchaseListener} deixa o comando do
 * CreativePlots rodar e depois pergunta a esta ponte quem é o dono agora. É a
 * única forma de saber se o claim realmente deu certo sem reimplementar a
 * grade de terrenos.
 *
 * Se o CreativePlots não estiver instalado, ou mudar a API numa versão futura,
 * {@link #available()} passa a devolver false e a venda de terreno se desliga
 * sozinha — o servidor sobe igual, só sem cobrar.
 */
public final class PlotApiBridge {

    private static final String CLASSE_API = "me.sosparkly.utils.PlotAPI";
    private static final String PLUGIN = "CreativePlots";

    private Object api;
    private Method isPlotWorldLocation;
    private Method getPlotAtLocation;
    private Method getPlotOwnerUuid;

    // Opcionais: usados só pelas mensagens do PlotAvisoListener. Ficam fora do
    // contrato de available() de propósito. Se uma versão futura do
    // CreativePlots renomear um destes, a venda de terreno — que é dinheiro —
    // continua funcionando, e o que se perde é a mensagem bonita.
    private Method canBuildLocation;
    private Method getPlotOwnerNome;

    /**
     * Sem cache de falha de propósito.
     *
     * Guardar "já tentei e não achei" deixaria a ponte morta para sempre caso o
     * CreativePlots subisse depois do BigaCore ou fosse recarregado em runtime.
     * O softdepend torna isso improvável — mas improvável somado a falha
     * silenciosa é o par que não se descobre até alguém reclamar que o terreno
     * saiu de graça. A redescoberta custa um lookup em mapa e um
     * {@code Class.forName} já cacheado pelo classloader, e só roda em comando
     * de terreno.
     */
    public boolean available() {
        return api != null || discover();
    }

    /** O jogador está num mundo de terrenos? Fora dele a cobrança não se aplica. */
    public boolean mundoDeTerrenos(Location local) {
        if (local == null || !available()) {
            return false;
        }
        try {
            Object resposta = isPlotWorldLocation.invoke(api, local);
            return resposta instanceof Boolean marcado && marcado;
        } catch (IllegalAccessException | InvocationTargetException erro) {
            return false;
        }
    }

    /**
     * Identificador do terreno nessa posição, no formato do CreativePlots
     * (ex.: {@code "0;0"}). Vazio quando a posição é rua ou fora da grade.
     */
    public Optional<String> terrenoEm(Location local) {
        if (local == null || !available()) {
            return Optional.empty();
        }
        try {
            Object resposta = getPlotAtLocation.invoke(api, local);
            if (resposta instanceof String id && !id.isBlank()) {
                return Optional.of(id);
            }
            return Optional.empty();
        } catch (IllegalAccessException | InvocationTargetException erro) {
            return Optional.empty();
        }
    }

    /** Dono atual do terreno. Vazio quando ninguém reivindicou. */
    public Optional<UUID> donoDe(String mundo, String terreno) {
        if (mundo == null || terreno == null || !available()) {
            return Optional.empty();
        }
        try {
            Object resposta = getPlotOwnerUuid.invoke(api, mundo, terreno);
            if (resposta instanceof UUID dono) {
                return Optional.of(dono);
            }
            return Optional.empty();
        } catch (IllegalAccessException | InvocationTargetException erro) {
            return Optional.empty();
        }
    }

    /**
     * O CreativePlots deixaria este jogador construir aqui?
     *
     * Vazio significa "não sei" — API ausente, posição fora de mundo de
     * terrenos, ou erro de reflexão. Quem chama tem de tratar o vazio como
     * "não faça nada", nunca como "pode" ou "não pode": é a diferença entre
     * uma mensagem que não aparece e um jogador impedido de construir no
     * próprio terreno.
     *
     * A resposta vem do mesmo {@code canBuild} que o listener de proteção do
     * CreativePlots consulta. Reimplementar a regra aqui — dono, convidado,
     * flags, permissão de bypass — garantiria divergência na primeira
     * atualização dele.
     */
    public Optional<Boolean> podeConstruir(Player jogador, Location local) {
        if (jogador == null || local == null || !available() || canBuildLocation == null) {
            return Optional.empty();
        }
        try {
            Object resposta = canBuildLocation.invoke(api, jogador, local);
            return resposta instanceof Boolean pode ? Optional.of(pode) : Optional.empty();
        } catch (IllegalAccessException | InvocationTargetException erro) {
            return Optional.empty();
        }
    }

    /** Nome de quem é dono do terreno, para mostrar na mensagem. */
    public Optional<String> donoNomeDe(String mundo, String terreno) {
        if (mundo == null || terreno == null || !available() || getPlotOwnerNome == null) {
            return Optional.empty();
        }
        try {
            Object resposta = getPlotOwnerNome.invoke(api, mundo, terreno);
            if (resposta instanceof String nome && !nome.isBlank() && !"None".equals(nome)) {
                return Optional.of(nome);
            }
            return Optional.empty();
        } catch (IllegalAccessException | InvocationTargetException erro) {
            return Optional.empty();
        }
    }

    private boolean discover() {
        try {
            Plugin creativePlots = Bukkit.getPluginManager().getPlugin(PLUGIN);
            if (creativePlots == null || !creativePlots.isEnabled()) {
                return false;
            }

            Class<?> tipoApi = Class.forName(CLASSE_API);

            // O construtor público pede a instância do plugin principal (CP).
            // Procuramos por aridade em vez de pelo tipo exato para não
            // precisar carregar a classe me.sosparkly.CP aqui.
            Constructor<?> construtor = null;
            for (Constructor<?> candidato : tipoApi.getConstructors()) {
                if (candidato.getParameterCount() == 1
                        && candidato.getParameterTypes()[0].isInstance(creativePlots)) {
                    construtor = candidato;
                    break;
                }
            }
            if (construtor == null) {
                return false;
            }

            this.api = construtor.newInstance(creativePlots);
            this.isPlotWorldLocation = tipoApi.getMethod("isPlotWorld", Location.class);
            this.getPlotAtLocation = tipoApi.getMethod("getPlotAt", Location.class);
            this.getPlotOwnerUuid = tipoApi.getMethod(
                    "getPlotOwnerUUID", String.class, String.class);

            // Opcionais: ausência aqui não derruba a ponte.
            this.canBuildLocation = metodoOpcional(tipoApi, "canBuild", Player.class, Location.class);
            this.getPlotOwnerNome = metodoOpcional(tipoApi, "getPlotOwnerName", String.class, String.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException erro) {
            this.api = null;
            return false;
        }
    }

    private static Method metodoOpcional(Class<?> tipo, String nome, Class<?>... parametros) {
        try {
            return tipo.getMethod(nome, parametros);
        } catch (NoSuchMethodException ausente) {
            return null;
        }
    }
}
