# 🤖 HANDOFF — Servidor Minecraft do Felipe

> **Atualizado em 11/08/2026.** Este arquivo registra o estado conhecido do
> projeto, decisões que não devem ser revertidas por acidente e as regras para
> continuar o trabalho. O runtime (`~/minecraft`) pode ter mudado fora do Git;
> portanto, **audite antes de assumir que o estado da máquina continua igual**.

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
- snapshot público somente do mundo (`export-world.sh`);
- defaults de RAM/diretório/build persistidos em `scripts/server.env` no runtime;
- nenhuma CI obrigatória neste estágio;
- nenhum sistema de IA implementado ainda.

### Runtime

Último runtime documentado e validado em jogo: **04/08/2026**. Antes de qualquer
trabalho dependente da máquina, confirme o estado real.

Última validação registrada:

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
- não assuma que versões anotadas em transcripts históricos continuam atuais.

---

## 3. 🗺️ Estrutura

### Repositório

```text
minecraft-server/
├── .gitignore
├── README.md
├── COMO-RODAR.md
├── HANDOFF.md
├── PLANO-EXECUCAO.md
├── docs/
│   └── sessoes/
├── server/
│   ├── scripts/
│   │   ├── setup.sh
│   │   ├── start.sh
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
├── spigot-26.2.jar                 # somente como rota histórica de rollback
├── scripts/
│   ├── setup.sh
│   ├── start.sh
│   ├── backup.sh
│   ├── export-world.sh
│   └── server.env                  # gerado pelo setup
├── plugins/
├── world/
├── config/
├── server.properties
├── bukkit.yml
├── spigot.yml
├── commands.yml
├── ops.json
├── whitelist.json
└── logs/
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

## 6. 💾 Backup privado × snapshot público

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
- cache de usuários.

Isso é correto para **disaster recovery**, mas significa que o arquivo poderá
conter futuramente:

- senha RCON;
- secret de proxy;
- API keys salvas incorretamente em runtime;
- bancos SQLite de plugins;
- tokens ou credenciais de plugins de terceiro.

Portanto:

> 🔴 **NUNCA publique `mc-backup-*.tar.gz` em GitHub Release pública.**

O `.gitignore` não protege conteúdo que foi empacotado dentro de um `.tar.gz` e
enviado como asset de release.

### `export-world.sh` — PUBLICÁVEL

Gera somente:

```text
world/
```

ou as pastas de mundo equivalentes existentes.

Características:

- recusa executar com o servidor ligado;
- não inclui plugins;
- não inclui configs;
- não inclui ops/whitelist;
- não inclui bancos;
- não inclui credenciais;
- valida o gzip;
- imprime SHA256.

É o único artefato criado pelos scripts do projeto que deve ser usado para uma
release pública de mundo.

### ⚠️ Release legada de 04/08/2026

A release `mundo-2026-08-04` foi criada **antes** dessa separação e contém
`mc-backup-*.tar.gz`, inclusive configs e arquivos administrativos.

Não use esse formato para nenhuma release nova. Os assets antigos devem ser
considerados legado potencialmente sensível e revisados/removidos no GitHub.

---

## 7. ⚙️ `setup.sh`, `start.sh` e defaults persistentes

### Problema corrigido em 11/08/2026

Antes, o README prometia:

```bash
RAM=8G bash server/scripts/setup.sh
SERVER_DIR=~/mc bash server/scripts/setup.sh
```

mas `start.sh` voltava depois para os defaults próprios (`4G` e `~/minecraft`).

### Solução atual

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

O arquivo contém apenas defaults operacionais, **não segredos**.

`start.sh` lê esses defaults, mas variáveis passadas diretamente continuam tendo
prioridade:

```bash
RAM=2G bash scripts/start.sh
```

`FORCE_CONFIG=1` permite atualizar deliberadamente o `server.env` ao rerodar o
setup.

---

## 8. 🪤 Configs existem em dois lugares

O projeto guarda templates; o servidor usa o runtime.

| Template | Runtime real |
|---|---|
| `server/config/server.properties` | `~/minecraft/server.properties` |
| `plugin/src/main/resources/config.yml` | `~/minecraft/plugins/BigaCore/config.yml` |

Editar template não muda automaticamente uma instalação existente.

### Divergência histórica intencional do BigaCore

Em 04/08/2026 o runtime possuía uma mensagem de boas-vindas de teste diferente
do template. Isso foi usado para validar `/biga reload` e não era, por si só, um
bug.

Se ainda existir, não "corrija" sem primeiro conferir o runtime e decidir qual
mensagem deve ser definitiva.

---

## 9. 🧩 BigaCore atual

Estrutura pequena de propósito:

```text
BigaCore.java
BigaCommand.java
JogadorListener.java
```

Não transformar o plugin em uma arquitetura enterprise antes de haver problema
real para resolver.

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

porque tags interativas podem carregar ações. Use texto literal ou renderer com
lista explícita de tags permitidas.

---

## 10. 🧠 Persistência da futura memória do mundo

O plano foi corrigido em 11/08/2026.

### SQLite

A documentação atual do Paper informa que o driver JDBC SQLite já vem incluído.
Portanto, no MVP:

- não adicionar `sqlite-jdbc` ao jar;
- não fazer shade do driver;
- não adicionar HikariCP automaticamente;
- usar uma camada de repositório;
- usar executor dedicado para writes/batches fora da main thread;
- usar fila limitada;
- drenar a fila e fechar a conexão no shutdown.

Quando houver Postgres ou dependência externa, decidir entre `libraries:` do
`plugin.yml` e shade/relocation conforme a necessidade concreta.

O banco do BigaCore pertence ao runtime e ao **backup privado**, nunca ao
snapshot público do mundo.

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

Um narrador sem contexto real vira apenas um gerador de frases genéricas.

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
- `mc-world-*.tar.gz` é o formato de snapshot público;
- nunca confiar em um único scanner como prova de que um JAR está limpo.

O scanner do plano usa `jar tf` porque `grep -R` não inspeciona corretamente o
conteúdo interno de um JAR.

---

## 13. 🛠️ Regras de operação para agentes/Claude Code

### 🔴 NUNCA subir o servidor por conta própria

O Felipe opera o servidor no terminal dele e quer o console sob controle.

Já houve incidente em que outra sessão subiu uma instância, segurou
`world/session.lock` e depois quase matou o processo errado. Duas instâncias no
mesmo mundo são risco real de corrupção.

Portanto agentes podem:

- ler código/configs;
- compilar BigaCore;
- preparar jar;
- editar arquivos do projeto;
- inspecionar logs;
- criar commits;
- preparar mudanças.

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
atual**. Não tratar bugs ou decisões desse arquivo como problema de produção até
o projeto decidir realmente usar systemd.

---

## 14. ✅ Auditoria antes de trabalho novo

Antes de confiar neste HANDOFF, confira o que for relevante para a tarefa.

### Projeto

```bash
cd ~/Desktop/minecraft-server
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
tail -100 ~/minecraft/logs/latest.log
```

### Build do plugin

```bash
cd ~/Desktop/minecraft-server/plugin
mvn clean package
```

Se algo divergir deste arquivo, **o estado real vence o documento**. Atualize o
HANDOFF depois que a diferença estiver entendida.

---

## 15. 📚 Histórico

O detalhe da migração e das sessões anteriores não precisa ficar duplicado aqui.
Consulte:

```text
docs/sessoes/
```

Este HANDOFF deve permanecer focado no **estado corrente, decisões e armadilhas**.
O transcript histórico serve para responder "como chegamos aqui?".
