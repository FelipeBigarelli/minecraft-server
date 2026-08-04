# 🤖 HANDOFF — Servidor Minecraft do Felipe

> **Para o Claude Code:** este documento é o estado completo do projeto. Leia inteiro antes de rodar qualquer comando. A seção **AUDITORIA** no fim tem o checklist do que verificar primeiro.
>
> **Você tem autonomia para rodar comandos.** O Felipe quer que você execute, não que descreva. Exceções na seção "Regras de operação".

---

## 1. 🎯 O que é este projeto

Servidor de Minecraft rodando na máquina local do Felipe (Ubuntu 24.04), com plugin próprio em Java. O objetivo final não é "ter um servidor" — é construir **algo que não existe**: um mundo com um narrador vivo, alimentado pela API do Claude, que reage ao que os jogadores realmente fazem.

Horizonte declarado: **construir algo grande, sem pressa.** Não otimize para entregar rápido; otimize para uma base que aguente crescer.

---

## 2. 📍 Estado atual — o que já funciona

Tudo abaixo foi executado e verificado com sucesso:

| Item | Status |
|---|---|
| Ubuntu 24.04.3 LTS (noble), kernel 6.17 | ✅ |
| OpenJDK 25.0.3 como default (`java`, `javac`, `JAVA_HOME`) | ✅ |
| Maven 3.8.7 rodando sobre JDK 25 | ✅ |
| **Paper 26.2 build 92** rodando (migrado em 04/08/2026) | ✅ |
| Spigot 26.2 mantido no disco como rollback | ✅ |
| Plugin BigaCore 1.0.0 compilando e carregando | ✅ |
| **BigaCore usando Adventure + MiniMessage** (paper-api) | ✅ validado em jogo |
| Cliente Minecraft instalado (.deb oficial) | ✅ |
| Conta Microsoft original, `online-mode=true` | ✅ |
| `setup.sh` instala tudo do zero em ~1 min, idempotente | ✅ |

**Última linha de log confirmada (Paper):**
```
[bootstrap] Loading Paper 26.2-92-main@0a99345 for Minecraft 26.2
[BigaCore] BigaCore habilitado.
Done (13.703s)! For help, type "help"
```

⚠️ O boot do Paper leva ~13s contra ~0.9s do Spigot. Não é problema: o Paper
inicializa o DataConverter, o spark embutido e mais subsistemas. Comparar com o
número antigo leva a um falso alarme.

### ⏳ O que ainda NÃO foi feito

- ❌ Nenhum plugin de terceiro instalado (o spark **já vem embutido** no Paper)
- ❌ Backup não agendado no cron (há backups manuais em `~/minecraft-backups/`)
- ❌ Acesso remoto para amigos (Radmin não serve — é Windows-only)
- ❌ Nada do narrador com IA

### ✅ Concluído em 03–04/08/2026

- ✅ Git inicializado; commit inicial `5445b08` = estado **pré-Paper**, é a rota de volta
- ✅ Migração para Paper executada (seção 7)
- ✅ Configs do servidor versionados em `server/config/` — antes existiam só no runtime
- ✅ `setup.sh` reescrito: baixa o Paper com verificação de SHA256 em vez de
  compilar o Spigot por 15 minutos

---

## 3. 🗺️ Estrutura — duas pastas, propósitos distintos

```
~/Desktop/minecraft-server/     ← código-fonte (versionável)
├── .gitignore
├── COMO-RODAR.md               ← guia operacional do Felipe
├── HANDOFF.md                  ← este arquivo
├── README.md
├── server/
│   ├── scripts/
│   │   ├── setup.sh            # instala TUDO do zero, idempotente (~1 min)
│   │   ├── start.sh            # Aikar's flags + SERVER_FLAVOR paper|spigot
│   │   ├── backup.sh           # backup rotativo, com gzip -t obrigatório
│   │   └── minecraft.service   # unit systemd (não instalado)
│   └── config/                 # templates — o setup.sh instala no runtime
│       ├── server.properties
│       ├── bukkit.yml
│       ├── spigot.yml          # timeout-time: 300, restart-script corrigido
│       ├── commands.yml
│       └── paper/
│           ├── paper-global.yml
│           └── paper-world-defaults.yml
└── plugin/                     # projeto Maven
    ├── pom.xml
    └── src/main/
        ├── java/codes/biga/bigacore/
        │   ├── BigaCore.java         # JavaPlugin, ciclo de vida
        │   ├── BigaCommand.java      # /biga — info|reload|voar + tab complete
        │   └── JogadorListener.java  # PlayerJoinEvent, PlayerQuitEvent
        └── resources/
            ├── plugin.yml            # api-version 26.2
            └── config.yml

~/minecraft/                       ← runtime (NUNCA versionar)
├── paper-26.2-92.jar              # o servidor atual
├── spigot-26.2.jar                # rollback (ver ressalva na seção 5)
├── scripts/                       # cópia dos scripts
├── plugins/
│   ├── bigacore-1.0.0.jar
│   ├── BigaCore/config.yml        # config REAL, gerado no 1º boot
│   └── PluginMetrics/config.yml
├── world/                         # ⚠️ AGORA contém as TRÊS dimensões
│   ├── level.dat  players/  data/  datapacks/
│   └── dimensions/minecraft/
│       ├── overworld/{region,entities,poi,data}
│       ├── the_nether/{region,entities,poi,data}
│       └── the_end/{region,entities,poi,data}
├── config/                        # gerado pelo Paper no 1º boot
│   ├── paper-global.yml
│   └── paper-world-defaults.yml
├── server.properties              # config REAL
├── bukkit.yml  spigot.yml
├── ops.json  whitelist.json  banned-players.json  banned-ips.json
├── commands.yml  help.yml  permissions.yml  usercache.json  eula.txt
├── bundler/                       # 91 MB — ⚠️ NÃO apagar (ver abaixo)
├── buildtools/                    # 639 MB de sobra do build — removível
└── logs/latest.log
```

⚠️ `world_nether/` e `world_the_end/` **não existem mais** — o Paper migrou as
três dimensões para dentro de `world/dimensions/`. Ver seção 5.

### Duas pastas que parecem lixo e não são a mesma coisa

| Pasta | Tamanho | Pode apagar? | O que é |
|---|---|---|---|
| `bundler/` | 91 MB | ❌ **NÃO** | Onde o jar do servidor extrai as bibliotecas e o server jar versionado. É lido em runtime, a cada boot. Apagar quebra o servidor |
| `buildtools/` | 639 MB | ✅ Sim | Sobra do BuildTools depois de compilar o Spigot. Nada em runtime depende dela |

`plugins/PluginMetrics/` também não estava documentado: é telemetria embutida no
próprio Spigot (aponta para `mcstats.org`, domínio morto há anos), criada no
primeiro boot. **Não é plugin de terceiro** — a seção 2 continua correta ao
dizer que nenhum foi instalado.

### ⚠️ Regra que já causou confusão duas vezes

**Arquivos de config existem em duplicata.** O projeto guarda o template; o servidor usa a cópia.

| Editar isto | NÃO tem efeito | Editar isto | TEM efeito |
|---|---|---|---|
| `server/config/server.properties` | ❌ | `~/minecraft/server.properties` | ✅ |
| `plugin/src/main/resources/config.yml` | ❌ | `~/minecraft/plugins/BigaCore/config.yml` | ✅ |

O `saveDefaultConfig()` só copia o template **se o arquivo ainda não existir**. Recompilar não sobrescreve. Para forçar: `rm ~/minecraft/plugins/BigaCore/config.yml` e reiniciar.

**Ao alterar config, altere nos dois lugares** — o do servidor para funcionar agora, o do projeto para não perder a mudança.

### 📌 Divergência INTENCIONAL — não é bug

Os dois `config.yml` do BigaCore estão **de propósito** diferentes hoje:

| Arquivo | `mensagem-boas-vindas` |
|---|---|
| `plugin/src/main/resources/config.yml` (template) | `<aqua>Bem-vindo ao servidor, <white><jogador></white>! <gray>Online agora: <online>` |
| `~/minecraft/plugins/BigaCore/config.yml` (runtime) | `<aqua>Bem-vindo ao servidor, seu baiano! <white><jogador></white>! ...` |

O Felipe editou o runtime em 03/08/2026 para testar o `/biga reload` — o teste
funcionou. O `seu baiano!` é **texto de teste, não a mensagem definitiva**.

Decisão dele: **manter assim.** O template fica com a mensagem limpa (é o que
uma instalação nova deve receber); o runtime fica com o texto de teste até ele
decidir a mensagem final.

⚠️ Não "corrija" essa diferença achando que é a armadilha acima. Não é.

### 🔤 Mudança de sintaxe em 04/08/2026 — códigos `&` saíram, MiniMessage entrou

Com a migração para Adventure, os dois arquivos passaram de códigos `&` para
MiniMessage. **Placeholders mudaram de `{chave}` para `<chave>`.**

| Antes | Agora |
|---|---|
| `&b` `&f` `&7` `&a` `&c` `&l` | `<aqua>` `<white>` `<gray>` `<green>` `<red>` `<bold>` |
| `{jogador}` `{online}` | `<jogador>` `<online>` |
| — (não existia) | `<color:#ff8800>`, `<gradient:a:b>`, `<hover:...>`, `<click:...>` |

Se a mensagem aparecer no chat com as tags visíveis em vez de colorida, é config
em sintaxe antiga. Converta as tags — não há camada de compatibilidade.

---

## 4. 🔢 Versões — contexto crítico

**A numeração do Minecraft mudou em 2026.** A Mojang abandonou `1.x.y` e adotou `ano.drop.patch`:

- `26.1` = primeiro drop de 2026
- `26.2` = segundo drop, lançado 16/06/2026, codinome *Chaos Cubed*

Consequências:
- 🔴 **Java 25 é obrigatório.** As builds 26.x são compiladas com JDK 25.
- 🔴 Qualquer código que faça parsing de versão assumindo prefixo `"1."` quebra.
- 🟡 Tutoriais e respostas de treino falando em `1.21.x` como "mais recente" estão desatualizados.

| Componente | Versão travada |
|---|---|
| Minecraft | 26.2 |
| Java (servidor + build) | 25.0.3 |
| Maven | 3.8.7 |
| `api-version` no plugin.yml | `'26.2'` |
| `maven.compiler.release` | `25` |

Java 17, 21 e 8 também existem na máquina. O `update-alternatives` e o `JAVA_HOME` no `~/.zshrc` apontam para o 25. **Não mexer nisso** — outros projetos do Felipe podem depender das outras versões.

---

## 5. 🐛 Problemas já resolvidos — não repetir

| Problema | Causa | Solução aplicada |
|---|---|---|
| `java -version` mostrava 17 | JDK 17 preexistente com prioridade | `update-alternatives --config java` e `javac` → opção 0 (auto, 25) |
| `mvn` usava JDK errado | Maven ignora alternatives, lê `JAVA_HOME` | `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64` no `~/.zshrc` |
| Pasta com espaço no nome | `"server mine"` quebra BuildTools e Maven | Renomeada para `minecraft-server` |
| Arquivos todos soltos numa pasta | Estrutura perdida no download | Árvore reconstruída manualmente |
| Dump gigante do Watchdog no `stop` | `sync-chunk-writes=true` → fsync por chunk em disco SATA | `sync-chunk-writes=false` + `timeout-time: 300` no spigot.yml |
| Plugin não compilava (previsto) | Código usava Adventure API e `getPluginMeta()` — **ambos Paper-only** | Reescrito com `ChatColor` e `getDescription()`. ⚠️ **Revertido em 04/08/2026**: com o `paper-api` no pom, as duas APIs voltaram — e são agora as corretas, porque `ChatColor` e `getDescription()` estão deprecados |
| `restart-on-crash` nunca funcionaria | `restart-script: ./start.sh` no spigot.yml, mas o script mora em `scripts/start.sh`. Caminho é relativo a `~/minecraft` | Corrigido para `./scripts/start.sh` (03/08/2026, achado na auditoria) |
| `target/` vazio na raiz do projeto | `mvn` rodado em `~/Desktop/minecraft-server` em vez de `plugin/` | Pasta apagada; `.gitignore` passou de `plugin/target/` para `target/` (pega qualquer nível) |
| `backup.sh` mentia sobre sucesso | `tar ... 2>/dev/null \|\| true` engolia erro; após a migração, `world_nether`/`world_the_end` sumiram e o tar falhava em silêncio imprimindo "OK" | Lista de alvos montada dinamicamente (só o que existe), `\|\| true` removido, `gzip -t` obrigatório |
| Backup pré-Paper seria apagado sozinho | `KEEP_DAYS=7` apaga `mc-backup-*.tar.gz` | Renomeado para `PRE-PAPER-2026-08-03_195957.tar.gz` — fora do padrão da limpeza |

### 🔴 A migração de mundo do Paper — leia antes de mexer em rollback

No primeiro boot, o Paper detectou que a estrutura de pastas do mundo era do
CraftBukkit e **migrou para o formato vanilla**. Ele avisa e dá **30 segundos**
para abortar. É irreversível.

| Antes (Spigot/CraftBukkit) | Agora (Paper/vanilla) |
|---|---|
| `world/region/` | `world/dimensions/minecraft/overworld/region/` |
| `world_nether/` | `world/dimensions/minecraft/the_nether/` |
| `world_the_end/` | `world/dimensions/minecraft/the_end/` |

`world_nether/` e `world_the_end/` **não existem mais**. Nada foi perdido —
14 arquivos `.mca`, `level.dat`, `players/` e advancements todos preservados.

**Consequência prática:** `SERVER_FLAVOR=spigot bash scripts/start.sh` **não
basta** para voltar ao Spigot. O Spigot procura as pastas antigas. Voltar exige
restaurar `~/minecraft-backups/PRE-PAPER-2026-08-03_195957.tar.gz` por cima.

⚠️ Ao vigiar um boot do Paper procurando por erro, **inclua `ALERT` e
`migration` no filtro**. O aviso não é `ERROR` nem `Done (` — é um `WARN` com
contagem regressiva, e um grep que só procura sucesso ou erro passa por cima
dele em silêncio.

⚠️ **Sobre o último item:** o BigaCore foi escrito deliberadamente **sem APIs Paper-only** para rodar nos dois. Isso torna a migração trivial. Após migrar, você **pode** usar Adventure — mas leia a seção 7.4 antes.

---

## 6. 🎨 Decisões tomadas e por quê

| Decisão | Motivo |
|---|---|
| Servidor em `~/minecraft`, fora do projeto | Mundo/logs/jars não devem ficar perto de código nem de Git |
| `scope=provided` na API no pom | A API já existe no runtime; embutir causa conflito de classloader |
| Aikar's flags no `start.sh` | Configuram o G1 para pausas curtas — em Minecraft, GC pause = lag spike |
| `sync-chunk-writes=false` | Save rápido; trade-off é risco em queda de energia, mitigado por backup |
| `maven-shade-plugin` mantido | Hoje não faz nada (sem deps), mas já estará pronto quando entrar HttpClient/driver |
| Não usar `/reload confirm` | Deixa classes antigas na memória → bugs fantasma. Reiniciar leva 1s |
| `online-mode=true` | Com `false`, qualquer um entra com qualquer nick |

---

## 7. ✅ CONCLUÍDO — Migração para Paper (04/08/2026)

**Decidido pelo Felipe.** Motivo: em 2026 a maioria dos plugins é testada contra Paper, e alguns exigem APIs Paper-only e não rodam em Spigot. Manter Spigot puro fecha portas — e o projeto vai precisar de ferramentas de conteúdo custom (ver seção 8).

### O que de fato foi feito

| Etapa | Resultado |
|---|---|
| 7.1 Backup | `PRE-PAPER-2026-08-03_195957.tar.gz`, verificado com `gzip -t`, fora da rotação |
| 7.2 Download | `paper-26.2-92.jar`, **SHA256 conferido** contra a API |
| 7.3 `start.sh` | `SERVER_FLAVOR` (paper\|spigot) + `PAPER_BUILD` explícito no nome do jar |
| 7.4 `pom.xml` | **Opção C**: `paper-api` fixo em `26.2.build.92-stable` **e** código migrado para Adventure |
| 7.5 Validação | ✅ **Completa.** Build OK, servidor sobe, plugin carrega, e o Felipe confirmou em jogo em 04/08/2026: `/biga info` com hover e click, help clicável, tab complete, `/biga voar`, mensagem de boas-vindas em MiniMessage, e o mundo migrado (nether e end incluídos) |
| — | Migração automática da estrutura do mundo pelo Paper (ver seção 5) |

**Sobre o 7.4:** a recomendação original deste documento era a opção B (trocar o
pom e deixar o código como estava). O Felipe escolheu ir além: trocar o pom **e**
reescrever os três arquivos de `ChatColor` para `Component`/MiniMessage, porque
`ChatColor` está deprecado e o narrador com IA vai precisar de hover, click e RGB.

⚠️ **O snippet de pom que este documento sugeria tinha um bug.** Ele propunha
`<version>[26.2.build,)</version>` — um range aberto do Maven. Três defeitos:
build não reprodutível, pega pré-release (`26.2.build.93-beta` ordena acima de
`26.2.build.92-stable`), e quando sair a 26.3 o range sobe sozinho para a API de
outra versão do Minecraft, contra um servidor ainda em 26.2. Compila liso,
quebra em runtime. **Use versão fixa, casada com o jar do servidor.**

### Registro do que foi tentado (para não repetir)

<details>
<summary>Passo a passo original — mantido como referência</summary>

### 7.1 Antes de tudo: backup

```bash
cd ~/minecraft && bash scripts/backup.sh
```

Confirme que o `.tar.gz` foi criado em `~/minecraft-backups/` antes de prosseguir.

### 7.2 Baixar o Paper

⚠️ **A API mudou.** O endpoint antigo `api.papermc.io/v2` parou de receber builds em 31/12/2025. O atual é `https://fill.papermc.io/v3/projects/paper`.

Build conhecida no momento desta escrita: `paper-26.2-92.jar`. Verifique se há mais recente:

```bash
curl -s -H "User-Agent: biga-mc-server/1.0" \
  https://fill.papermc.io/v3/projects/paper/versions/26.2/builds | jq '.[0]'
```

Baixe a build STABLE mais recente para `~/minecraft/paper-26.2.jar`.

### 7.3 Ajustar o start.sh

Trocar a variável do jar. As Aikar's flags continuam válidas — foram feitas pensando em Paper, inclusive.

Manter o `spigot-26.2.jar` no disco por enquanto, como rollback.

### 7.4 Decisão sobre o pom.xml

Aqui tem uma escolha real, **converse com o Felipe antes de decidir**:

**Opção A — manter `spigot-api` no pom.** O plugin continua rodando em Spigot e Paper. Perde acesso a Adventure, ao scheduler melhorado e às APIs novas do Paper.

**Opção B — trocar para `paper-api`:**
```xml
<repository>
  <id>papermc</id>
  <url>https://repo.papermc.io/repository/maven-public/</url>
</repository>

<dependency>
  <groupId>io.papermc.paper</groupId>
  <artifactId>paper-api</artifactId>
  <version>[26.2.build,)</version>
  <scope>provided</scope>
</dependency>
```
Ganha Adventure (texto com hover, click, cores RGB — que o narrador com IA vai querer) e APIs melhores. Perde compatibilidade com Spigot puro.

**Minha recomendação:** opção B, dado que a decisão de migrar já foi tomada e o projeto é para uso próprio, não para distribuir. Mas é decisão do Felipe.

### 7.5 Validar

1. `mvn clean package` → BUILD SUCCESS
2. Copiar jar para `~/minecraft/plugins/`
3. Subir e confirmar `[BigaCore] BigaCore habilitado.`
4. No jogo: `/biga info`, `/biga` + Tab, `/biga voar`, sair e entrar (mensagem de boas-vindas)

⚠️ O Paper gera `config/paper-global.yml` e `config/paper-world-defaults.yml` no primeiro boot. O `spigot.yml` continua existindo e sendo lido. Não apagar nada.

</details>

### O que o documento original NÃO previu

1. **O Paper reescreve o `spigot.yml` inteiro** no primeiro boot (cabeçalho vira
   "This is the Spigot configuration file for Paper", `config-version: 13`). As
   correções de `timeout-time` e `restart-script` **sobreviveram** — mas confira
   depois de cada atualização de build.
2. **A migração de estrutura do mundo** — ver o bloco vermelho na seção 5.
3. **O spark já vem embutido** no Paper (`[spark] Starting background
   profiler...`). Não precisa instalar.
4. Os configs do servidor **só existiam no runtime**. Agora estão versionados em
   `server/config/`, e o `setup.sh` os instala numa máquina nova.

---

## 8. 🌌 O objetivo maior — o narrador vivo

Este é o diferencial do projeto. Vale entender antes de escrever código.

### O conceito

Uma entidade que **observa** o servidor — mortes, descobertas, construções, padrões de convivência — e **gera** lore, profecias e eventos que referenciam o que os jogadores de fato fizeram. Não texto genérico: "a profecia menciona o nome de quem morreu ontem naquele mesmo lugar".

Isso exige a interseção que o Felipe tem: dev Java + acesso à API do Claude + capacidade de montar web. É por isso que ninguém tem.

### Arquitetura proposta (a discutir, não decidida)

```
Eventos do jogo  →  Coletor  →  Memória do mundo  →  Gerador (Claude API)  →  Manifestação
(listeners)         (async)     (SQLite/JSON)         (async, com cache)      (chat, mob, evento)
```

**Fases sugeridas, incrementais:**

1. **Memória** — persistir eventos significativos. Sem IA ainda. Só coletar bem.
2. **`/pergunta`** — comando que consulta a API do Claude com contexto do mundo. Valida o pipeline inteiro.
3. **Narrador passivo** — mensagens ambientes geradas a partir da memória, em intervalos.
4. **Narrador ativo** — dispara eventos de mundo (invoca mob, muda clima, cria estrutura) baseado no que gerou.
5. **NPC corpóreo** — com ModelEngine, o narrador ganha corpo e animação.

### 🔴 Regras técnicas inegociáveis

- **NUNCA fazer I/O na thread principal.** Chamada HTTP, arquivo, banco — tudo em `Bukkit.getScheduler().runTaskAsynchronously()`. Bloquear o tick congela o servidor inteiro para todos os jogadores. Este é o erro nº1 em plugin que chama API externa.
- **Voltar para a thread principal antes de tocar no mundo.** A API do Bukkit não é thread-safe. Padrão: async para buscar → `runTask()` para aplicar.
- **API key nunca no código nem no Git.** Variável de ambiente ou arquivo fora do repo, no `.gitignore`.
- **Rate limit e cache.** Um evento de morte não pode virar uma chamada de API por morte. Agrupe, debounce, cacheie.
- **Degradar com elegância.** API fora do ar não pode derrubar o servidor nem travar o jogador. Timeout curto, fallback silencioso.

### Ferramentas de conteúdo custom (para as fases 4-5)

| Ferramenta | Para quê |
|---|---|
| **Oraxen** ou **ItemsAdder** | Itens/blocos/móveis com textura e modelo próprios; gera e distribui o resource pack automaticamente |
| **ModelEngine** | Mobs com modelo 3D animado de verdade |
| **MythicMobs** | Comportamento de mob por config: fases, skills, bossbar |

Oraxen e ItemsAdder são equivalentes na prática; ItemsAdder só ganha em servidores com 500+ blocos custom.

### Infraestrutura a instalar quando fizer sentido

`LuckPerms` (permissões), `CoreProtect` (log + rollback), `EssentialsX` (comandos básicos), `Vault` (ponte economia/perms), `WorldEdit` (construção).

✅ **`spark` NÃO precisa ser instalado** — desde a migração para Paper ele vem
embutido. O log mostra `[spark] Starting background profiler...` no boot. Use
direto: `/spark profiler start`, `/spark tps`, `/spark health`.

⚠️ **Conflito conhecido:** EssentialsX e plugins de economia brigam por `/pay` e `/balance`. Verificar avisos no startup.

⚠️ **Segurança:** já houve malware distribuído via contas comprometidas de autores no SpigotMC. Baixar só de SpigotMC, Modrinth ou Hangar oficiais. Preferir open-source.

---

## 9. ⚙️ Regras de operação — para você, Claude Code

### 🔴 NUNCA subir o servidor — o Felipe roda ele mesmo

Adicionado em 04/08/2026, depois de dar errado duas vezes na mesma sessão.

Não rodar `start.sh`, `screen -dmS`, systemd nem qualquer variante. O Felipe
sobe o servidor no terminal dele, em foreground, e quer o console na mão.

**O que deu errado:** subi o servidor num `screen` para validar a migração.
Resultado: (1) o meu processo segurou o `world/session.lock` e o `start.sh` dele
falhou com `DirectoryLock$LockException`; (2) ao tentar limpar, li o PID errado
e mandei SIGTERM no servidor que **ele** tinha subido e que estava funcionando.
Duas instâncias no mesmo mundo é risco real de corrupção.

**Como trabalhar:** preparar tudo (compilar, copiar jar, ajustar config) e então
**pedir para ele subir**. Para inspecionar estado sem servidor de pé, basta ler
`~/minecraft/logs/latest.log`, os `.log.gz` rotacionados e os arquivos do mundo.

**Antes de encostar em qualquer processo Java:** conferir o PPID. Se o pai for o
`zsh` dele (sob `gnome-terminal-server`), é o servidor dele.

### Pode fazer sem perguntar

- Rodar `mvn clean package`, copiar jar para `~/minecraft/plugins/`
- Ler logs, inspecionar configs, rodar diagnósticos
- Editar código do plugin e configs
- Criar branches, commits

### Pergunte antes

- Apagar ou regenerar mundo
- Mudar `online-mode`
- Instalar plugin de terceiro
- Mudar decisão registrada na seção 6
- Trocar a dependência do pom (seção 7.4)

### Sempre

- ✅ `stop` no console para desligar. **Nunca** `kill -9` — corrompe chunk no meio de escrita
- ✅ Backup antes de mexer em mundo ou versão
- ✅ Após editar config, aplicar nos dois lugares (projeto + servidor)
- ✅ Confirmar `[BigaCore] BigaCore habilitado.` após cada deploy
- ❌ Nunca `/reload confirm`

### Ciclo de desenvolvimento

```bash
# editar código
cd ~/Desktop/minecraft-server/plugin
mvn clean package && cp target/bigacore-1.0.0.jar ~/minecraft/plugins/
# console: stop
cd ~/minecraft && bash scripts/start.sh
```

### Comandos úteis

```bash
tail -f ~/minecraft/logs/latest.log      # log ao vivo
ps aux | grep "[p]aper-26.2.jar"         # servidor está rodando?
java -version && mvn -version            # confirmar JDK 25
bash ~/minecraft/scripts/backup.sh       # backup manual
```

---

## 10. ✅ AUDITORIA — rode isto primeiro

Antes de qualquer trabalho novo, verifique se o estado descrito aqui bate com a realidade. Este documento foi escrito por outra instância do Claude e **pode estar desatualizado ou errado**.

```bash
# 1. Ambiente
lsb_release -a
java -version 2>&1 | head -1        # esperado: 25.0.3
javac -version                      # esperado: 25.0.3
mvn -version | grep "Java version"  # esperado: 25.0.3
echo $JAVA_HOME

# 2. Estrutura do projeto
cd ~/Desktop/minecraft-server && find . -type f -not -path './plugin/target/*' | sort

# 3. Estado do servidor
ls -la ~/minecraft/
ls -la ~/minecraft/plugins/
ps aux | grep "[s]pigot\|[p]aper"

# 4. Configs reais em uso
grep -E "online-mode|sync-chunk-writes|view-distance|simulation-distance" ~/minecraft/server.properties
grep -A2 "^settings:" ~/minecraft/spigot.yml | grep timeout-time

# 5. O plugin compila?
cd ~/Desktop/minecraft-server/plugin && mvn clean package -q && echo "BUILD OK"

# 6. Git
cd ~/Desktop/minecraft-server && git status 2>&1 | head -3
```

### Checklist de auditoria

- [ ] Java 25 nos três comandos
- [ ] Estrutura de pastas bate com a seção 3
- [ ] `sync-chunk-writes=false` e `timeout-time: 300` aplicados
- [ ] `online-mode=true`
- [ ] `mvn clean package` passa
- [ ] Nenhuma API Paper-only no código (`grep -rn "net.kyori\|getPluginMeta\|io.papermc" plugin/src/`) — **antes** da migração
- [ ] Git inicializado (feito em 03/08/2026 — o commit inicial é o estado **pré-Paper**, use-o para voltar)
- [ ] `~/minecraft/bundler/` existe e **não pode ser apagada** (ver seção 3)
- [ ] `~/minecraft/buildtools/` ocupa **639 MB** (não ~2 GB, como esta seção afirmava antes de 03/08/2026). Removível, mas o Felipe decidiu **manter**: é a rota de volta para o Spigot se o Paper der problema, e há 662 GB livres no disco

### Divergências

Se algo não bater, **reporte ao Felipe em vez de assumir**. Ele acompanhou cada passo e vai saber dizer o que aconteceu.

---

## 11. 👤 Sobre o Felipe

- Dev full-stack, fundador da biga.codes. React, TypeScript, Next.js, Node, Supabase, API do Claude
- Formado em Engenharia de Software (UTFPR)
- Trabalha no VS Code com a extensão do Claude Code + terminal
- Comunica em **português brasileiro**
- Prefere: estrutura visual clara, emojis, quebras de seção, **arquivos completos em vez de diffs**
- Quer validação antes da entrega — se você não testou algo, diga que não testou
- Java não é a linguagem principal dele. Vale explicar o *porquê* das escolhas de API, não só o *como*
