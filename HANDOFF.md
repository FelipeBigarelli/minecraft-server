# 🤖 HANDOFF — Servidor Minecraft do Felipe

> **Atualizado em 11/08/2026** (última auditoria: 14/08/2026 — o plugin tem
> **12 classes** e **37 testes**; para o estado corrente da economia veja
> [ECONOMIA.md](ECONOMIA.md)). Este arquivo registra o estado conhecido do
> projeto, decisões que não devem ser revertidas por acidente e as regras para
> continuar o trabalho. O runtime (`~/minecraft`) pode ter mudado fora do Git;
> portanto, **audite antes de assumir que o estado da máquina continua igual**.
>
> **Máquina nova:** antes de desenvolver, conclua [NOVO-PC.md](NOVO-PC.md).

---

## 1. 🎯 O que é este projeto

Servidor Minecraft com plugin próprio em Java (`BigaCore`). O objetivo de longo
prazo não é apenas manter um servidor: é construir um mundo com um **narrador
vivo**, alimentado por IA, que observa eventos reais dos jogadores, acumula uma
memória do mundo e usa essa memória para gerar lore e, mais tarde, manifestações
no jogo.

Horizonte do projeto: **construir algo grande, sem pressa**. Não sacrificar
segurança, mundo ou arquitetura por velocidade.

---

## 2. 📍 Estado conhecido

### Repositório

Estado confirmado no GitHub em 11/08/2026:

- Paper 26.2 build 92 como servidor padrão;
- Java 25;
- `paper-api` fixa em `26.2.build.92-stable`;
- BigaCore 1.0.0 usando Adventure + MiniMessage;
- setup idempotente;
- backup privado completo (`backup.sh`);
- snapshot somente do mundo (`export-world.sh`);
- restauração segura somente de mundo (`restore-world.sh`), inclusive para o backup legado;
- diagnóstico sem boot (`doctor.sh`);
- defaults de RAM/diretório/build persistidos em `scripts/server.env` no runtime;
- GitHub Actions valida sintaxe dos scripts, restauração segura, Java 25, build do BigaCore e helper Python;
- nenhum sistema de IA implementado ainda.

### Runtime

Último runtime documentado e validado em jogo no PC anterior: **04/08/2026**.
Em um PC novo, o runtime precisa ser reconstruído pelo `setup.sh`; o Git não
carrega mundo, jars nem configs reais do servidor.

Última validação em jogo registrada:

```text
Paper 26.2 build 92
BigaCore habilitado
/biga info funcionando
/biga reload funcionando
/biga voar funcionando
MiniMessage funcionando
mundo migrado para a estrutura do Paper
```

### O que ainda não deve ser presumido

- plugins de terceiros podem ter atualizado desde a última pesquisa;
- o runtime pode ter recebido mudanças manuais depois do último handoff;
- suporte de plugins à 26.2 deve ser conferido **no dia da instalação**;
- não assuma que versões anotadas em transcripts históricos continuam atuais;
- em PC novo, não assuma que Java/Maven/Paper/mundo estão corretos até `doctor.sh` passar.

---

## 3. 🗺️ Estrutura

### Repositório

```text
minecraft-server/
├── .gitignore
├── README.md
├── NOVO-PC.md
├── COMO-RODAR.md
├── HANDOFF.md
├── PLANO-EXECUCAO.md
├── .github/workflows/ci.yml
├── docs/
│   └── sessoes/
├── server/
│   ├── scripts/
│   │   ├── setup.sh
│   │   ├── start.sh
│   │   ├── doctor.sh
│   │   ├── restore-world.sh
│   │   ├── backup.sh
│   │   ├── export-world.sh
│   │   └── minecraft.service
│   └── config/
│       ├── server.properties
│       ├── bukkit.yml
│       ├── spigot.yml
│       ├── commands.yml
│       └── paper/
│           ├── paper-global.yml
│           └── paper-world-defaults.yml
└── plugin/
    ├── pom.xml
    └── src/main/
        ├── java/codes/biga/bigacore/
        │   ├── BigaCore.java
        │   ├── BigaCommand.java
        │   └── JogadorListener.java
        └── resources/
            ├── plugin.yml
            └── config.yml
```

### Runtime padrão

```text
~/minecraft/
├── paper-26.2-92.jar
├── scripts/
│   ├── setup.sh
│   ├── start.sh
│   ├── doctor.sh
│   ├── restore-world.sh
│   ├── backup.sh
│   ├── export-world.sh
│   └── server.env                  # gerado pelo setup
├── plugins/
│   └── bigacore-1.0.0.jar
├── world/                          # só existe após restore ou primeiro boot
├── config/
├── server.properties
├── bukkit.yml
├── spigot.yml
├── commands.yml
├── ops.json                        # se criado
├── whitelist.json                  # se criado
└── logs/                           # após primeiro boot
```

O runtime fica fora do Git de propósito. Mundo, logs, jars, bancos e configs
reais não pertencem ao repositório de código.

---

## 4. 🔢 Versões e compatibilidade

| Componente | Estado do projeto |
|---|---|
| Minecraft / Paper | 26.2 build 92 |
| Java | 25 |
| `api-version` | `26.2` |
| `maven.compiler.release` | 25 |
| `paper-api` | `26.2.build.92-stable` |
| BigaCore | 1.0.0 |

### Regra de atualização

Não atualize apenas o jar.

Quando a build do Paper mudar, revisar no mesmo trabalho:

1. `server/scripts/setup.sh`;
2. fallback do `server/scripts/start.sh`;
3. `plugin/pom.xml`;
4. runtime `scripts/server.env`;
5. backup antes da mudança;
6. build do BigaCore;
7. validação em jogo.

Versão fixa é intencional: evita build não reprodutível e incompatibilidade entre
API usada na compilação e servidor em runtime.

---

## 5. 🌍 Migração Spigot → Paper

Migração realizada e validada em 04/08/2026.

O Paper converteu a estrutura de dimensões para o formato atual:

```text
ANTES
world/
world_nether/
world_the_end/

DEPOIS
world/
└── dimensions/minecraft/
    ├── overworld/
    ├── the_nether/
    └── the_end/
```

### Consequência

`SERVER_FLAVOR=spigot` **não é rollback completo**.

Voltar ao Spigot exige restaurar o backup pré-Paper, porque o Spigot espera a
estrutura antiga de diretórios.

O arquivo histórico foi registrado como:

```text
PRE-PAPER-2026-08-03_195957.tar.gz
```

Nunca faça rollback de mundo sem um backup novo antes.

---

## 6. 💾 Backup privado × snapshot do mundo × restore

Esta separação foi formalizada em **11/08/2026** após auditoria de segurança.

### `backup.sh` — PRIVADO

Inclui, conforme existirem:

- mundos;
- plugins e dados de plugins;
- configs do Paper;
- `server.properties`;
- Bukkit/Spigot configs;
- `ops.json`;
- whitelist;
- bans;
- cache de usuários;
- `scripts/server.env`.

Isso é correto para **disaster recovery**, mas significa que o arquivo poderá
conter futuramente senha RCON, secret de proxy, bancos ou tokens.

> 🔴 **NUNCA publique `mc-backup-*.tar.gz` como Release pública.**

### `export-world.sh` — SOMENTE MUNDOS

Gera `mc-world-*.tar.gz` com apenas as pastas de mundo existentes.

Características:

- recusa executar com o servidor ligado;
- não inclui plugins/configs/ops/whitelist/bancos;
- valida gzip;
- imprime SHA256.

O arquivo é adequado para **transportar o mundo**, mas o próprio mundo pode
conter UUIDs, inventários e posições. Portanto "sem configs/segredos" não é o
mesmo que "sem dados de jogadores".

### `restore-world.sh` — restauração filtrada

Aceita tanto `mc-world-*.tar.gz` quanto o `mc-backup-*.tar.gz` legado, porém só
extrai:

```text
world/
world_nether/
world_the_end/
```

Nunca restaura plugins, configs, ops, whitelist ou credenciais do arquivo.
Também:

- exige servidor desligado;
- valida gzip;
- rejeita caminhos absolutos/`..`;
- não sobrescreve mundo existente sem `FORCE=1`;
- com `FORCE=1`, move o mundo anterior para uma pasta de segurança primeiro.

A CI possui teste funcional que cria um backup falso com um `server.properties`
contendo segredo e confirma que somente `world/` chega ao destino.

### ⚠️ Release legada de 04/08/2026

A release `mundo-2026-08-04` contém o backup Paper:

```text
mc-backup-2026-08-04_142421.tar.gz
```

Em PC novo, se não houver snapshot mais recente, ele pode servir de fallback
**somente através de `restore-world.sh`**.

Não usar `PRE-PAPER-2026-08-03_195957.tar.gz` para continuar no Paper.

---

## 7. ⚙️ Setup, defaults e diagnóstico

O setup gera:

```text
<runtime>/scripts/server.env
```

com:

```text
DEFAULT_SERVER_DIR
DEFAULT_MC_VERSION
DEFAULT_PAPER_BUILD
DEFAULT_RAM
DEFAULT_SERVER_FLAVOR
```

O arquivo contém defaults operacionais, **não segredos**.

Os scripts operacionais leem os mesmos defaults. Variáveis passadas diretamente
continuam tendo prioridade:

```bash
RAM=2G bash scripts/start.sh
```

`FORCE_CONFIG=1` permite atualizar deliberadamente o `server.env` ao rerodar o
setup.

### `doctor.sh`

Antes do primeiro boot em máquina nova:

```bash
cd ~/minecraft
bash scripts/doctor.sh
```

Ele não modifica arquivos nem inicia o servidor. Confere Java, Maven, Paper,
BigaCore, EULA, `online-mode`, RCON, presença de mundo e processos Paper/Spigot.

Se não houver mundo ainda, é apenas aviso: o primeiro boot gera um novo.

---

## 8. 🪤 Configs existem em dois lugares

O projeto guarda templates; o servidor usa o runtime.

| Template | Runtime real |
|---|---|
| `server/config/server.properties` | `~/minecraft/server.properties` |
| `plugin/src/main/resources/config.yml` | `~/minecraft/plugins/BigaCore/config.yml` |

Editar template não muda automaticamente uma instalação existente.

### Divergência histórica intencional do BigaCore

Em 04/08/2026 o runtime antigo possuía uma mensagem de boas-vindas de teste
diferente do template. Isso foi usado para validar `/biga reload`.

Uma instalação nova recebe o **template limpo** do Git. Não restaure o config
antigo só para reproduzir essa divergência de teste.

---

## 9. 🧩 BigaCore atual

```text
BigaCore.java            ponto de entrada
BigaCommand.java         /biga e subcomandos
JogadorListener.java     join/quit
EconomyCatalog.java      lê economy.yml
VaultEconomyBridge.java  ponte Vault por reflexão (sem dependência de compilação)
StartingBalance.java     aplica o saldo inicial da política
ServerBuyback.java       recompra do servidor com teto diário/semanal
SpawnShopBuilder.java    analisa terreno e constrói a Biga Market
ShopPreview.java         preview por partículas
ShopSnapshot.java        snapshot e rollback da construção
ShopDisplays.java        item flutuante sobre as bancas
```

Cresceu junto com a economia e a Biga Market, mas continua sem framework: cada
classe tem uma responsabilidade e nenhuma depende de plugin de terceiro em
tempo de compilação. Não transformar em arquitetura enterprise antes de haver
problema real para resolver.

### Testes

`mvn verify` roda 37 testes que **não precisam de servidor**. Eles existem
porque cada um corresponde a uma falha que chegou ao runtime:

| Teste | Falha que ele previne |
|---|---|
| `PluginDescriptorTest` | plugin.yml inválido derrubando o plugin no boot |
| `GroundClassificationTest` | grama classificada como construção (Tag.DIRT encolheu na 26.2) |
| `RoofGeometryTest` | telhado com degrau vazado |
| `EconomyCatalogTest` | chave de catálogo que nenhum Material casa |
| `ServerBuybackTest` | aritmética do teto criando moeda a mais |

### Decisões atuais

- Paper API, não Spigot API;
- Adventure Components;
- MiniMessage para texto configurável;
- `Placeholder.unparsed()` para dado externo;
- comandos administrativos protegidos por `bigacore.admin`;
- sem I/O de rede/banco na main thread.

### Regra para IA

Resposta de IA é **dado não confiável**.

Nunca faça:

```text
resposta do Claude → MiniMessage.deserialize() irrestrito → jogador
```

Use texto literal ou renderer com lista explícita de tags permitidas.

---

## 10. 🧠 Persistência da futura memória do mundo

O plano foi corrigido em 11/08/2026.

### SQLite

A documentação atual do Paper informa que o driver JDBC SQLite já vem incluído.
No MVP:

- não adicionar `sqlite-jdbc` ao jar;
- não fazer shade do driver;
- não adicionar HikariCP automaticamente;
- usar camada de repositório;
- executor dedicado para writes/batches fora da main thread;
- fila limitada;
- drenar fila e fechar conexão no shutdown.

Quando houver Postgres ou dependência externa, decidir entre `libraries:` do
`plugin.yml` e shade/relocation conforme necessidade concreta.

O banco do BigaCore pertence ao runtime e ao **backup privado**, nunca ao
snapshot do mundo.

---

## 11. 🌌 Roadmap

A ordem continua:

```text
FASE 1 — infraestrutura
    ↓
FASE 2 — memória do mundo (sem IA)
    ↓
acumular dados reais
    ↓
FASE 3 — narrador
    ↓
FASE 4 — conteúdo custom / corpo do narrador
```

Não pule a memória do mundo.

Detalhes: [PLANO-EXECUCAO.md](PLANO-EXECUCAO.md).

---

## 12. 🔒 Segurança

Regras fixas:

- `online-mode=true`;
- API key nunca no Git;
- RCON desligado até existir necessidade real;
- nunca expor RCON diretamente à internet;
- plugin de terceiro apenas de fonte oficial/confiável;
- verificar versão e, quando possível, hash do JAR;
- um plugin por vez;
- backup antes de mudança de versão/mundo/plugin crítico;
- `mc-backup-*.tar.gz` é privado;
- `mc-world-*.tar.gz` transporta somente mundo, mas ainda pode ter dados de jogadores;
- nunca confiar em um único scanner como prova de que um JAR está limpo.

---

## 13. 🛠️ Regras de operação para agentes/Claude Code

### 🔴 NUNCA subir o servidor por conta própria

O Felipe opera o servidor no terminal dele e quer o console sob controle.

Já houve incidente em que outra sessão subiu uma instância, segurou
`world/session.lock` e depois quase matou o processo errado. Duas instâncias no
mesmo mundo são risco real de corrupção.

Agentes podem:

- ler código/configs;
- compilar BigaCore;
- preparar jar;
- editar arquivos do projeto;
- inspecionar logs;
- criar commits;
- preparar mudanças;
- rodar `doctor.sh`.

Mas **não devem iniciar o servidor Minecraft sem pedido explícito do Felipe**.

### Sempre

- desligar com `stop`;
- não usar `kill -9`;
- não usar `/reload confirm` para código;
- backup antes de alteração destrutiva;
- confirmar `[BigaCore] BigaCore habilitado.` depois do deploy feito pelo Felipe;
- não instalar vários plugins de uma vez.

### systemd

`minecraft.service` é apenas um template opcional e **não faz parte da operação
atual**.

---

## 14. ✅ Auditoria antes de trabalho novo

### Em máquina nova

Primeiro siga:

```text
NOVO-PC.md
```

Depois:

```bash
cd ~/minecraft
bash scripts/doctor.sh
```

### Projeto

A pasta do clone pode variar. A partir da raiz do repositório:

```bash
git status
git log -5 --oneline
find server/scripts -maxdepth 1 -type f -print
```

### Ambiente

```bash
java -version
javac -version
mvn -version
```

### Runtime, sem iniciar nada

```bash
ls -la ~/minecraft
ls -la ~/minecraft/scripts
ls -la ~/minecraft/plugins
bash ~/minecraft/scripts/doctor.sh
```

### Build do plugin

A partir da raiz do clone:

```bash
cd plugin
mvn clean package
```

Se algo divergir deste arquivo, **o estado real vence o documento**. Atualize o
HANDOFF depois que a diferença estiver entendida.

---

## 15. 📚 Histórico

Consulte:

```text
docs/sessoes/
```

Este HANDOFF deve permanecer focado no **estado corrente, decisões e armadilhas**.
