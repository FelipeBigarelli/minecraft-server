#!/usr/bin/env bash
# =============================================================
#  doctor.sh — Diagnóstico sem iniciar o servidor.
#
#  Útil depois do setup/restore em um PC novo. Confere Java,
#  Maven, Paper, BigaCore, economia, configs básicas e mundo.
#  Não modifica arquivos e NÃO sobe o Minecraft.
# =============================================================
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/server.env" ]; then
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/server.env"
fi

SERVER_DIR="${SERVER_DIR:-${DEFAULT_SERVER_DIR:-$HOME/minecraft}}"
MC_VERSION="${MC_VERSION:-${DEFAULT_MC_VERSION:-26.3}}"
PAPER_BUILD="${PAPER_BUILD:-${DEFAULT_PAPER_BUILD:-167}}"
EXPECTED_JAR="paper-$MC_VERSION-$PAPER_BUILD.jar"

CHESTSHOP_JAR="ChestShop-3.13-pre-1.jar"
VAULT_JAR="VaultUnlocked-2.20.2.jar"
ETERNAL_JAR="EternalEconomy-v1.0.1.jar"

ERROS=0
AVISOS=0

ok()   { printf '\033[1;32m[ OK ]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[AVISO]\033[0m %s\n' "$*"; AVISOS=$((AVISOS + 1)); }
err()  { printf '\033[1;31m[ERRO]\033[0m %s\n' "$*"; ERROS=$((ERROS + 1)); }

java_major() {
    java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'
}

verificar_jar_plugin() {
    local arquivo="$1" descricao="$2"
    if [ -f "$SERVER_DIR/plugins/$arquivo" ]; then
        ok "$descricao encontrado: $arquivo"
    else
        err "$descricao não encontrado: $SERVER_DIR/plugins/$arquivo"
    fi
}

verificar_duplicatas_plugin() {
    local padrao="$1" descricao="$2"
    local quantidade
    quantidade=$(find "$SERVER_DIR/plugins" -maxdepth 1 -type f -iname "$padrao" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$quantidade" -gt 1 ]; then
        err "$descricao tem $quantidade JARs no diretório plugins/. Remova versões antigas antes de iniciar."
    fi
}

echo "Minecraft Server Doctor"
echo "Servidor esperado: $SERVER_DIR"
echo "Paper esperado   : $EXPECTED_JAR"
echo "Economia         : ChestShop 3.13-pre-1 + VaultUnlocked 2.20.2 + EternalEconomy 1.0.1"
echo

if command -v java >/dev/null 2>&1; then
    MAJOR=$(java_major)
    if [[ "$MAJOR" =~ ^[0-9]+$ ]] && [ "$MAJOR" -ge 25 ]; then
        ok "Java $MAJOR disponível."
    else
        err "Java 25+ é obrigatório; encontrado: ${MAJOR:-desconhecido}."
    fi
else
    err "java não encontrado no PATH."
fi

if command -v mvn >/dev/null 2>&1; then
    MVN_JAVA=$(mvn -version 2>/dev/null | sed -nE 's/.*Java version: ([0-9]+).*/\1/p' | head -1)
    if [ "${MVN_JAVA:-0}" -ge 25 ] 2>/dev/null; then
        ok "Maven disponível e usando Java $MVN_JAVA."
    else
        warn "Maven existe, mas não consegui confirmar Java 25. Rode: mvn -version"
    fi
else
    err "Maven não encontrado."
fi

if [ -d "$SERVER_DIR" ]; then
    ok "Diretório do servidor existe."
else
    err "Diretório do servidor não existe: $SERVER_DIR"
fi

if [ -f "$SERVER_DIR/$EXPECTED_JAR" ]; then
    ok "$EXPECTED_JAR encontrado."
else
    err "$EXPECTED_JAR não encontrado em $SERVER_DIR. Rode setup.sh novamente."
fi

PLUGIN_JAR=$(find "$SERVER_DIR/plugins" -maxdepth 1 -type f -name 'bigacore-*.jar' 2>/dev/null | head -1)
if [ -n "$PLUGIN_JAR" ]; then
    ok "BigaCore encontrado: $(basename "$PLUGIN_JAR")"
    if jar tf "$PLUGIN_JAR" 2>/dev/null | grep -qx 'economy.yml'; then
        ok "BigaCore contém economy.yml."
        # A política de preços mora no economy.yml. Guardar cópia dos números
        # aqui fazia o doctor reprovar o servidor toda vez que a política mudava
        # — foi exatamente o que derrubou o Economy Smoke em 28/10/2026. Agora
        # ele lê do JAR e compara os plugins contra a fonte, não contra memória.
        POLITICA_TMP="$(mktemp -d)"
        if (cd "$POLITICA_TMP" && jar xf "$PLUGIN_JAR" economy.yml >/dev/null 2>&1); then
            POLITICA="$POLITICA_TMP/economy.yml"
        fi
    else
        err "O JAR do BigaCore não contém economy.yml. Recompile pelo setup.sh."
    fi
else
    err "BigaCore não encontrado em $SERVER_DIR/plugins/."
fi

verificar_duplicatas_plugin '*bigacore-*.jar' 'BigaCore'
verificar_jar_plugin "$VAULT_JAR" "VaultUnlocked"

# Quem paga o Vault é o EssentialsX no servidor real e o EternalEconomy numa
# máquina recém-montada pelo setup.sh. Exigir o JAR do EternalEconomy reprovava
# o servidor real, que trocou de provedor de propósito. O que importa é existir
# UM provedor; os dois ao mesmo tempo é que brigariam pelo Vault.
if [ -n "$(find "$SERVER_DIR/plugins" -maxdepth 1 -type f -iname 'EssentialsX-*.jar' 2>/dev/null)" ]; then
    ok "Provedor de economia: EssentialsX."
elif [ -f "$SERVER_DIR/plugins/$ETERNAL_JAR" ]; then
    ok "Provedor de economia: $ETERNAL_JAR"
else
    err "Nenhum provedor de economia em plugins/ (EssentialsX ou $ETERNAL_JAR)."
fi

verificar_jar_plugin "$CHESTSHOP_JAR" "ChestShop"
verificar_duplicatas_plugin '*Vault*.jar' 'Vault/VaultUnlocked'
verificar_duplicatas_plugin '*EternalEconomy*.jar' 'EternalEconomy'
verificar_duplicatas_plugin '*ChestShop*.jar' 'ChestShop'

# Lê um escalar simples do economy.yml. Serve para as chaves de política
# (um nível, sem lista), que é tudo que o doctor precisa comparar.
politica() {
    [ -n "${POLITICA:-}" ] && [ -f "$POLITICA" ] || return 1
    grep -Em1 "^[[:space:]]*$1:[[:space:]]" "$POLITICA" \
        | sed -E "s/^[^:]*:[[:space:]]*//; s/[[:space:]]*#.*$//; s/^['\"]//; s/['\"]$//; s/[[:space:]]*$//"
}

conferir_politica() {
    # $1 chave no economy.yml, $2 arquivo do plugin, $3 chave no plugin, $4 rótulo
    local esperado
    esperado="$(politica "$1")" || { err "Não consegui ler '$1' do economy.yml."; return; }
    if [ -z "$esperado" ]; then
        err "Chave '$1' ausente no economy.yml."
        return
    fi
    if grep -Eq "^$3:[[:space:]]*['\"]?${esperado}(\\.0+)?['\"]?[[:space:]]*(#.*)?$" "$2"; then
        ok "$4 = $esperado (bate com o economy.yml)."
    else
        err "$4 diverge do economy.yml, que pede $esperado."
    fi
}

CHESTSHOP_CONFIG="$SERVER_DIR/plugins/ChestShop/config.yml"
if [ -f "$CHESTSHOP_CONFIG" ]; then
    conferir_politica chestshop-tax-percent "$CHESTSHOP_CONFIG" TAX_AMOUNT \
        "ChestShop: taxa P2P"
    conferir_politica shop-creation-price "$CHESTSHOP_CONFIG" SHOP_CREATION_PRICE \
        "ChestShop: criação de loja"
    conferir_politica shop-refund-price "$CHESTSHOP_CONFIG" SHOP_REFUND_PRICE \
        "ChestShop: reembolso de loja"

    if grep -Eq '^ALLOW_PARTIAL_TRANSACTIONS:[[:space:]]*false([[:space:]]|$)' "$CHESTSHOP_CONFIG"; then
        ok "ChestShop: transações parciais desativadas."
    else
        err "ChestShop: ALLOW_PARTIAL_TRANSACTIONS deve ser false."
    fi
else
    err "Config do ChestShop não encontrado: $CHESTSHOP_CONFIG"
fi

# O provedor de economia mudou de EternalEconomy para EssentialsX no servidor
# real, mas o setup.sh ainda instala o EternalEconomy numa máquina nova. Os dois
# são válidos; o que não pode é o saldo inicial divergir da política. Então o
# doctor confere quem estiver presente, em vez de exigir um plugin específico.
ESSENTIALS_CONFIG="$SERVER_DIR/plugins/Essentials/config.yml"
ETERNAL_CONFIG="$SERVER_DIR/plugins/EternalEconomy/config.yml"
if [ -f "$ESSENTIALS_CONFIG" ]; then
    conferir_politica starting-balance "$ESSENTIALS_CONFIG" starting-balance \
        "EssentialsX: saldo inicial"
elif [ -f "$ETERNAL_CONFIG" ]; then
    # As aspas são obrigatórias no padrão: ao gravar o próprio config no
    # primeiro boot, o EternalEconomy normaliza o número para string e escreve
    # defaultBalance: '250'. Sem aceitar isso, o doctor reprovava um servidor
    # perfeitamente configurado e bloqueava o boot.
    conferir_politica starting-balance "$ETERNAL_CONFIG" defaultBalance \
        "EternalEconomy: saldo inicial"
else
    err "Nenhum provedor de economia encontrado (nem EssentialsX nem EternalEconomy)."
fi

if grep -q '^eula=true$' "$SERVER_DIR/eula.txt" 2>/dev/null; then
    ok "EULA marcada como aceita pelo setup."
else
    err "eula.txt não contém eula=true."
fi

# online-mode=false deixou de ser acidente em 05/11/2026: é o que permite o
# crossplay com Bedrock, para jogar no Nintendo Switch (docs/bedrock-switch.md).
# O risco não desapareceu, mudou de lugar — sem validação na Mojang, quem
# digita o nick É o jogador, inclusive o nick de um operador. A whitelist é o
# que fecha essa porta, e por isso ela tem de vir ANTES de qualquer
# encaminhamento de porta no roteador.
if grep -q '^online-mode=true$' "$SERVER_DIR/server.properties" 2>/dev/null; then
    ok "online-mode=true (contas validadas na Mojang)."
elif grep -q '^online-mode=false$' "$SERVER_DIR/server.properties" 2>/dev/null; then
    if grep -q '^white-list=true$' "$SERVER_DIR/server.properties" 2>/dev/null; then
        ok "online-mode=false com whitelist ligada (crossplay Bedrock)."
    else
        warn "online-mode=false SEM whitelist: qualquer um entra com qualquer nick, inclusive o de um operador. Só é aceitável enquanto o servidor vive na LAN. Ligue white-list=true antes de encaminhar qualquer porta."
    fi
else
    err "online-mode não foi encontrado em server.properties."
fi

if grep -q '^enable-rcon=true$' "$SERVER_DIR/server.properties" 2>/dev/null; then
    warn "RCON está habilitado. Confirme firewall e nunca exponha 25575 diretamente à internet."
else
    ok "RCON não está habilitado."
fi

# O crossplay com Bedrock saiu do projeto em 28/10/2026 e voltou em
# 05/11/2026, para jogar no Nintendo Switch. Então o Geyser agora é esperado,
# e o que merece aviso é ele configurado de um jeito que o console não
# consegue usar. Veja docs/bedrock-switch.md.
if ls "$SERVER_DIR"/plugins/Geyser-Spigot*.jar >/dev/null 2>&1 \
    || [ -d "$SERVER_DIR/plugins/Geyser-Spigot" ]; then
    ok "Crossplay ativo (Geyser)."
    # auth-type fica indentado dentro de "java:", daí o ^[[:space:]].
    if grep -qE '^[[:space:]]+auth-type:[[:space:]]*online[[:space:]]*$' \
        "$SERVER_DIR/plugins/Geyser-Spigot/config.yml" 2>/dev/null; then
        warn "Geyser com auth-type: online. No console não há como digitar um login da Mojang; use offline (gamertag como nick) ou floodgate (/linkaccount)."
    fi
else
    ok "Sem crossplay (servidor somente Java)."
fi

# O paredão de ajuda (/biga painel) e a mensagem de saldo insuficiente do
# ChestShop ensinam /saldo, que não existe em plugin nenhum — é um apelido
# nosso em commands.yml apontando para o /balance do Essentials. Se o arquivo
# for recriado (update do Paper, reinstalação), o apelido some e a parede passa
# a ensinar um comando que o jogador digita e não acontece nada.
if grep -qE '^[[:space:]]+saldo:[[:space:]]*$' "$SERVER_DIR/commands.yml" 2>/dev/null; then
    ok "Apelido /saldo presente em commands.yml."
else
    warn "commands.yml sem o apelido /saldo. O paredão de ajuda ensina esse comando, mas no jogo ele não existe (no Essentials é /balance, /bal ou /money). Copie o bloco de server/config/commands.yml e reinicie."
fi

# Mesma armadilha do /saldo, por outro caminho: o paredão pode ensinar um
# comando que EXISTE no servidor mas que o grupo "default" não tem permissão
# de usar. Quem é OP testa, funciona, e o buraco só aparece quando um jogador
# comum digita. O grupo default do LuckPerms é uma lista explícita — não tem
# essentials.* — então cada comando do Essentials citado na parede precisa
# estar nela, nominalmente.
#
# /back é o caso que motivou esta checagem: ele tem custo em command-costs e
# aparece em dois painéis, mas essentials.back não é default:true no
# plugin.yml do EssentialsX (o padrão do Bukkit é "op").
LP_DEFAULT="$SERVER_DIR/plugins/LuckPerms/yaml-storage/groups/default.yml"
if [ -f "$LP_DEFAULT" ]; then
    FALTANDO=()
    for node in essentials.back essentials.home essentials.sethome essentials.tpa \
                essentials.balance essentials.pay essentials.balancetop essentials.spawn \
                essentials.list essentials.kit essentials.msg essentials.near essentials.seen \
                essentials.mail.send essentials.delhome; do
        grep -qE "^[[:space:]]*-[[:space:]]*${node}\$" "$LP_DEFAULT" || FALTANDO+=("$node")
    done
    if [ ${#FALTANDO[@]} -eq 0 ]; then
        ok "Grupo default tem permissão para todos os comandos do paredão."
    else
        warn "O paredão ensina comandos que o grupo default não pode usar: ${FALTANDO[*]}. Conceda com /lp group default permission set <node> true, ou tire o comando do ajuda.yml."
    fi
else
    ok "LuckPerms não está em yaml-storage; permissões do paredão não conferidas aqui."
fi

MUNDOS=()
for mundo in world world_nether world_the_end; do
    [ -d "$SERVER_DIR/$mundo" ] && MUNDOS+=("$mundo")
done

if [ ${#MUNDOS[@]} -gt 0 ]; then
    ok "Mundo encontrado: ${MUNDOS[*]}"
else
    warn "Nenhum mundo existe ainda. Isso é normal em instalação nova; o primeiro boot gera um mundo se você não restaurar um snapshot."
fi

PROCESSOS=$(ps -eo pid=,comm=,args= 2>/dev/null | awk '$2 == "java" && /(paper|spigot)-[^ ]*\.jar/ {print}')
if [ -n "$PROCESSOS" ]; then
    warn "Há um servidor Minecraft rodando agora. Não inicie uma segunda instância."
    echo "$PROCESSOS"
else
    ok "Nenhum processo Paper/Spigot detectado."
fi

echo
if [ "$ERROS" -eq 0 ]; then
    printf '\033[1;32mDiagnóstico aprovado.\033[0m %d aviso(s).\n' "$AVISOS"
    echo "Para iniciar manualmente: cd \"$SERVER_DIR\" && bash scripts/start.sh"
    exit 0
fi

printf '\033[1;31mDiagnóstico encontrou %d erro(s).\033[0m Corrija antes do primeiro boot.\n' "$ERROS"
exit 1
