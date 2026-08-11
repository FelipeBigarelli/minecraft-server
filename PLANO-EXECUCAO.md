# 🚀 PLANO DE EXECUÇÃO — Do servidor limpo ao narrador vivo

> **Para o Claude Code.** Leia o `HANDOFF.md` antes deste arquivo — ele tem o estado da máquina, as decisões tomadas e as armadilhas já descobertas.
>
> Este documento é um plano faseado. **Não execute tudo de uma vez.** Cada fase termina num checkpoint onde você para e reporta.

---

## 0. ⚠️ REGRAS QUE VALEM PARA TUDO

### 0.1 Os números deste documento podem estar errados

As versões, builds e URLs aqui vieram de pesquisas pontuais. Plugins atualizam semanalmente. **Antes de baixar qualquer coisa, confirme na página oficial** se há build para Paper 26.2.

Se um plugin só listar uma versão anterior, **não instale e reporte.** Rodar plugin de versão anterior no 26.2 pode funcionar ou pode quebrar de formas silenciosas — não vale o risco num servidor que ainda está sendo construído.

### 0.2 Fontes permitidas

✅ **Modrinth**, **Hangar** (hangar.papermc.io), **GitHub Releases oficiais**, **SpigotMC oficial**, site oficial do projeto.

❌ **NUNCA** baixe de: spigotunlocked, blackspigot, nullforums, ou qualquer site de plugin "nulled"/crackeado. São vetores conhecidos de malware.

### 0.3 JAR de terceiro é código executável — inspecione antes de instalar

Já houve malware distribuído por contas comprometidas de autores. Um scan por
um nome de arquivo conhecido é somente uma **rede de segurança**, nunca prova de
que o plugin é seguro.

O comando antigo usava `grep -R` diretamente nos `.jar`, mas JAR é um ZIP e o
grep comum **não inspeciona corretamente as entradas internas**. Como o JDK 25 já
está instalado, use `jar tf`:

```bash
cd ~/minecraft/plugins
ACHOU=0
for plugin in *.jar; do
  [ -e "$plugin" ] || continue
  if jar tf "$plugin" | grep -q 'plugin-config\.bin'; then
    echo "⚠️ SUSPEITO: $plugin contém plugin-config.bin"
    ACHOU=1
  fi
done
[ "$ACHOU" -eq 0 ] && echo "Nenhum plugin-config.bin encontrado. Isso NÃO substitui verificar origem, versão e hash."
```

Além disso, sempre confirme:

1. fonte oficial;
2. versão explícita compatível com 26.2;
3. data/release oficial;
4. quando possível, SHA256 do arquivo baixado.

### 0.4 Backup antes de cada fase

```bash
cd ~/minecraft && bash scripts/backup.sh
```

Com o servidor **desligado** — senão o arquivo sai marcado `-quente`. Confirme que o `.tar.gz` foi criado antes de prosseguir.

🔒 `backup.sh` é **privado**: inclui mundo, plugins, configs e arquivos
administrativos. Nunca envie `mc-backup-*.tar.gz` para uma GitHub Release pública.
Para compartilhar só o mundo use `scripts/export-world.sh`.

### 0.5 Um plugin por vez

Instale, suba o servidor, confirme que carregou sem erro, **só então** instale o próximo. Se subir cinco de uma vez e o servidor não subir, você não sabe qual foi.

### 0.6 Meça antes e depois

O **spark já vem embutido no Paper**. Antes de instalar qualquer coisa, capture o baseline:

```text
/spark tps
/spark healthreport
```

Anote MSPT com servidor vazio. Se depois de uma fase o MSPT subir muito, você sabe onde procurar.

### 0.7 Pare e pergunte quando

- Um plugin não tem suporte confirmado a 26.2
- Algo exigir pagamento
- Um plugin pedir dependência que não está neste plano
- O servidor não subir após uma instalação
- Você precisar mudar uma decisão do `HANDOFF.md` seção 6

---

## FASE 1 — 🧱 Infraestrutura

**Objetivo:** base sólida de permissões, proteção e diagnóstico. Nada de conteúdo ainda.

### 1.1 Ordem de instalação

Instale **nesta ordem** — há dependências entre eles:

| # | Plugin | Função | Fonte | Licença |
|---|---|---|---|---|
| 1 | **LuckPerms** | Permissões | luckperms.net/download, Modrinth, Hangar | MIT |
| 2 | **VaultUnlocked** | Ponte economia/perms/chat | Hangar, Modrinth | — |
| 3 | **EssentialsX** | `/home`, `/tpa`, `/warp`, kits | essentialsx.net/downloads, Hangar | GPLv3 |
| 4 | **CoreProtect** | Log de tudo + rollback | coreprotect.net, Hangar, Modrinth | Artistic-2.0 |
| 5 | **FastAsyncWorldEdit** | Edição em massa (assíncrona) | Modrinth, Hangar, SpigotMC | GPL-3.0 |
| 6 | **WorldGuard** | Proteção de regiões | enginehub.org, Modrinth | LGPL-3.0 |

> A tabela é a ordem desejada, **não uma autorização automática para instalar**.
> Revalide compatibilidade com 26.2 no dia de cada instalação.

### 1.2 Armadilhas específicas desta fase

🔴 **NUNCA instale WorldEdit e FastAsyncWorldEdit juntos.** Conflito de comandos. FAWE já implementa a API do WorldEdit — plugins que dependem de WorldEdit funcionam com o FAWE instalado.

🔴 **LuckPerms deve ser o único plugin de permissões.** Se houver qualquer outro, os checks quebram de forma imprevisível.

🟡 **LuckPerms baixa libs no primeiro start.** Precisa de internet. Se falhar, não improvise baixando dependências de sites aleatórios.

🟡 **Qualquer observação de versão específica neste documento envelhece.** Se uma nota disser que um plugin suporta ou não suporta determinada build, confira de novo na fonte oficial antes de agir.

🟡 **EssentialsX x economia:** comandos como `/pay` e `/balance` podem conflitar com plugins de economia. Quando houver economia, revisar aliases e permissões.

### 1.3 Depois de instalar

```bash
# Confirme que todos carregaram
grep -iE "Enabling (LuckPerms|Essentials|CoreProtect|FastAsyncWorldEdit|WorldGuard|Vault)" ~/minecraft/logs/latest.log

# Procure erros
grep -iE "ERROR|SEVERE|Could not load|incompatible" ~/minecraft/logs/latest.log | head -20
```

E rode `/spark healthreport` de novo, comparando com o baseline.

### 1.4 Configuração mínima

Não configure nada além do necessário agora. Especificamente:

- **LuckPerms:** crie apenas os grupos `default` e `admin`. Nada de hierarquia elaborada ainda.
- **CoreProtect:** default serve. Confirme só que está logando (`/co inspect`).
- **EssentialsX:** desative o que não for usar. Ele traz dezenas de comandos que podem conflitar depois.

### ✋ CHECKPOINT 1

Pare aqui. Reporte:

- [ ] Quais plugins instalou e qual versão exata de cada
- [ ] Quais declararam suporte a 26.2 e quais você teve que verificar de outro jeito
- [ ] MSPT antes × depois
- [ ] Qualquer warning no log
- [ ] Origem e SHA256 de cada JAR instalado, quando disponível
- [ ] Resultado da inspeção das entradas dos JARs

**Não avance para a Fase 2 sem aprovação do Felipe.**

---

## FASE 2 — 🧠 O coletor de memória do mundo

> Esta é a fase mais importante do projeto inteiro. **Ainda sem IA.**

**Objetivo:** o BigaCore passa a observar e lembrar o que acontece no servidor.

### 2.1 Por que sem IA ainda

Um narrador só é tão bom quanto a memória que ele tem. Se o coletor for raso, o Claude vai gerar texto genérico — e aí a culpa parece ser da IA, quando na verdade é da entrada.

Construir o coletor primeiro também permite testar tudo sem gastar um centavo de API.

### 2.2 O que coletar

Eventos que contam uma história. Não logue tudo — logue o que é **narrativamente significativo**:

| Categoria | Eventos | Por quê |
|---|---|---|
| **Morte** | `PlayerDeathEvent` — quem, onde, causa, o que carregava | Base de profecias e lugares "amaldiçoados" |
| **Descoberta** | Primeira vez que um jogador entra numa bioma/estrutura, primeiro diamante, primeira ida ao Nether | Marcos individuais |
| **Construção** | Blocos colocados agregados por região e por jogador (NÃO cada bloco) | Onde as pessoas se estabelecem |
| **Convivência** | Proximidade entre jogadores ao longo do tempo, mortes por PvP | Relações emergentes |
| **Marcos coletivos** | Boss derrotado, portal construído, primeiro a chegar em X | Eventos que merecem lore |

🔴 **Regra crítica: agregue, não registre linha a linha.** `BlockPlaceEvent` dispara centenas de vezes por minuto. Acumule em memória e persista em lote (a cada N minutos ou N eventos).

### 2.3 Persistência

**Duas camadas, com propósitos diferentes:**

**PersistentDataContainer (PDC)** — para estado pequeno e diretamente ligado a
jogador/entidade/item: flags, contadores simples, IDs e marcadores. É a API nativa
adequada para dados persistentes associados a objetos do jogo.

**SQLite** — para o estado consultável do mundo: histórico de eventos,
agregações e memória que o narrador vai consultar.

A documentação atual do Paper informa que o **driver JDBC do SQLite já vem
embutido no servidor**. Portanto, para o SQLite local do MVP:

- **não** adicione `org.xerial:sqlite-jdbc` ao `pom.xml`;
- **não** faça shade/relocation do driver SQLite;
- inicialize `org.sqlite.JDBC` e abra a conexão via JDBC;
- mantenha toda a persistência fora da main thread.

Referência oficial: `https://docs.papermc.io/paper/dev/using-databases/`.

**Não introduza HikariCP só porque é comum em aplicações web.** SQLite é um
arquivo local e o primeiro desenho deve priorizar simplicidade e ordem de
escrita. Comece com uma camada de repositório e um executor assíncrono dedicado
para serializar/batchear writes. Se a medição mostrar necessidade real de outra
estratégia, mude com evidência.

Quando chegar a hora de PostgreSQL ou outra dependência externa, escolha de forma
consciente entre:

1. `libraries:` no `plugin.yml` — o Paper baixa a dependência do Maven Central e
   adiciona ao classpath, removendo a necessidade de shade/relocation em muitos
   casos; ou
2. shade + relocation — quando houver motivo específico para empacotar a lib.

Referência oficial: `https://docs.papermc.io/paper/dev/plugin-yml/#libraries`.

⚠️ **Quando migrar para VPS, o backend pode virar PostgreSQL.** Mantenha a camada
de acesso a dados atrás de uma interface para a troca não virar cirurgia.

⚠️ O banco SQLite do BigaCore ficará no runtime e deve entrar no **backup
privado**. Ele nunca entra no `export-world.sh` nem em uma release pública.

### 2.4 Arquitetura

```text
Listeners (main thread)
    ↓  transformam evento do Bukkit em dado simples
Fila limitada em memória (thread-safe)
    ↓  agregação + batch
Executor dedicado de persistência
    ↓
SQLite
    ↓
Camada de consulta → agregações prontas para o narrador
```

Regras adicionais:

- a fila deve ter limite definido; não pode crescer sem controle;
- `BlockPlaceEvent` e eventos muito frequentes devem ser agregados antes de persistir;
- eventos de alto valor narrativo podem ter prioridade maior de flush;
- no `onDisable()`, pare de aceitar novos eventos, drene a fila com timeout e feche a conexão;
- falha de banco deve degradar com log claro, não travar o tick.

🔴 **NUNCA faça I/O na thread principal.** Nem SQLite. Um listener que grava em disco a cada evento congela o servidor inteiro.

🔴 **Voltar para a main thread antes de tocar no mundo.** A API do Bukkit não é thread-safe. Padrão: async para buscar/gravar → scheduler da main thread para aplicar mudança no jogo.

### 2.5 Comando de inspeção

Crie `/biga memoria` (permissão `bigacore.admin`) que mostra o que foi coletado. Sem isso você está construindo às cegas.

Use Adventure/MiniMessage — já está no projeto e funciona.

### ✋ CHECKPOINT 2

- [ ] Esquema do banco (mostre o DDL)
- [ ] Quais eventos estão sendo coletados
- [ ] `/biga memoria` funcionando com dados reais
- [ ] Fila limitada e política de overflow documentadas
- [ ] Confirmação de que nenhum write acontece na main thread
- [ ] Shutdown drena/fecha persistência de forma controlada
- [ ] Backup privado contém o banco; export público não contém
- [ ] MSPT antes × depois — o coletor não pode custar performance perceptível

Deixe rodando por alguns dias de jogo antes da Fase 3. Você precisa de memória real acumulada para testar o narrador.

---

## FASE 3 — 🌌 O narrador

**Objetivo:** o Claude lê a memória do mundo e gera lore.

### 3.1 Referências para estudar antes de escrever

Existem plugins que integram LLM em Minecraft, mas os projetos externos servem
apenas como referência arquitetural. **Revalide o estado deles quando esta fase
começar e não instale um plugin antigo só para testar a ideia.**

Pontos que vale estudar em implementações existentes:

- controle de custo;
- memória e seleção de contexto;
- rate limit;
- timeout/retry;
- degradação quando o provedor está fora do ar.

### 3.2 A chave da API

🔴 **NUNCA no código. NUNCA no Git. Nem em commit que será "corrigido depois"** — o histórico guarda para sempre.

O `.gitignore` já cobre `.env`, `*.key` e `**/api-key*`. Use variável de ambiente lida no `onEnable()`. Se estiver ausente, o plugin deve **carregar normalmente com o narrador desativado** e logar um aviso — nunca derrubar o servidor.

O backup privado também deve ser tratado como sensível: se uma chave for
armazenada em qualquer arquivo do runtime, ela pode acabar dentro dele.

### 3.3 Controle de custo — obrigatório desde o primeiro commit

A API é paga por token. Um servidor ativo pode gerar milhares de chamadas por dia se você for ingênuo.

| Mecanismo | Como |
|---|---|
| **Agregação** | Nunca uma chamada por evento. Junte eventos numa janela e faça uma chamada |
| **Rate limit** | Teto rígido de chamadas por hora, configurável |
| **Cache** | Se o world state não mudou significativamente, reutilize a última geração |
| **Kill switch** | `/biga narrador off` desliga tudo na hora |
| **Telemetria** | Logue tokens consumidos por chamada. Sem isso você descobre o custo na fatura |

### 3.4 Fluxo

```text
Timer assíncrono
    ↓
Lê agregação do SQLite
    ↓
Monta contexto mínimo necessário
    ↓
HttpClient (async) → API do Claude
    ↓
Resposta → validação e sanitização
    ↓
main thread → manifestação no jogo
```

### 3.5 Sanitização — não pule isso

🔴 **O texto que volta da API é dado externo.** O `JogadorListener` já usa `Placeholder.unparsed()` pelo motivo certo — o comentário no código antecipou exatamente este momento.

Texto gerado por IA passando direto por `MiniMessage.deserialize()` permite que a saída contenha `<click:run_command:'/op alguem'>`. **Sempre trate a resposta da API como não-confiável:** insira como conteúdo literal, ou use um MiniMessage/renderer com conjunto explícito e restrito de tags permitidas.

Por padrão, não permita da IA:

- `click:run_command`;
- `click:suggest_command`;
- `insertion`;
- qualquer ação que execute ou sugira comando administrativo.

### 3.6 Degradação com elegância

- API fora do ar → timeout curto, log, silêncio no jogo. **Jamais travar o jogador.**
- Resposta malformada → descarta, não entrega texto quebrado.
- Rate limit estourado → pula o ciclo.
- Erro do narrador nunca pode impedir o servidor de iniciar ou desligar.

O servidor tem que funcionar perfeitamente com o narrador desligado. Ele é um adorno, não uma dependência.

### 3.7 Fases do narrador

Implemente em ordem. Cada uma é entregável sozinha:

1. **`/biga pergunta <texto>`** — consulta manual com contexto do mundo. Valida o pipeline inteiro com custo controlado.
2. **Narrador passivo** — mensagens ambientes periódicas geradas da memória.
3. **Narrador reativo** — dispara em marcos.
4. **Narrador ativo** — provoca eventos no mundo: clima, invoca mob, cria estrutura.

### ✋ CHECKPOINT 3

- [ ] `/biga pergunta` funcionando
- [ ] Custo por chamada medido e logado
- [ ] Kill switch testado
- [ ] Sanitização testada com tags proibidas
- [ ] Servidor funciona normalmente com a chave ausente

---

## FASE 4 — 🎨 Conteúdo custom

> Só depois que o narrador estiver de pé. Isso aqui é o corpo; o narrador é a alma.

As recomendações de plugins desta fase envelhecem rápido. Quando chegar aqui,
faça uma pesquisa nova nas fontes oficiais e escolha **um** stack de conteúdo,
sem empilhar plugins equivalentes que disputem resource pack, registries ou
comandos.

### 4.1 Blocos e itens

Critérios de escolha:

- suporte explícito ao Paper/Minecraft em uso naquele momento;
- projeto ativo;
- documentação e API utilizável pelo BigaCore;
- modelo de licença/custo aceitável;
- estratégia de resource pack compatível com o que o narrador precisará criar.

Candidates históricos do plano: CraftEngine, Nexo/ItemsAdder e equivalentes.
**Não instale com base apenas no nome desta lista; reavalie quando chegar à fase.**

🔴 **Escolha UM sistema principal de conteúdo custom.** Empilhar dois costuma gerar conflito de resource pack e comportamento.

### 4.2 Mobs e modelos

A mesma regra vale para MythicMobs, ModelEngine, BetterModel e alternativas:
confirme versão, licença e compatibilidade no momento da implementação.

Comece com a menor combinação que prove a mecânica de um boss. Só adicione
componente pago ou complexo quando existir uma necessidade concreta.

### 4.3 NPC — quando o narrador ganhar corpo

Critérios:

- API estável para controle pelo BigaCore;
- suporte à versão atual;
- custo e licença;
- integração com o sistema de modelos escolhido.

Citizens e alternativas packet-based podem ser comparados quando esta fase
existir de verdade.

### 4.4 Se precisar mexer em packets

Não escolha biblioteca por reputação histórica. Reavalie PacketEvents,
ProtocolLib e alternativas na versão atual do servidor e adote somente se uma
funcionalidade realmente exigir acesso a packets.

---

## 5. 🚫 O que NÃO instalar por impulso

| Categoria | Motivo |
|---|---|
| WorldEdit junto com FAWE | conflito/duplicação de função |
| Outro plugin de permissões junto com LuckPerms | múltiplas fontes de verdade para permissões |
| Anticheat sem necessidade | custo e complexidade antes de existir problema real |
| Plugin abandonado ou sem suporte à versão | risco de falha silenciosa |
| Dois sistemas de conteúdo custom equivalentes | conflito de resource pack/registries |

Para mapas web, anticheat e qualquer outra categoria grande: **pesquise de novo
quando houver requisito real**. Não fixe uma escolha anos antes da necessidade.

---

## 6. 📅 Ordem sugerida de trabalho

```text
Fase 1 (infra)          →  incremental, um plugin por vez
Fase 2 (coletor)        →  várias sessões — é a base de tudo
   ↓ deixar rodando e acumular memória real
Fase 3 (narrador)       →  incremental, 4 sub-fases
Fase 4 (conteúdo)       →  só quando houver o que vestir
```

⚠️ **Não pule a Fase 2.** A tentação de ir direto pro narrador é grande, mas um narrador sem memória boa gera texto genérico — e aí o projeto inteiro parece uma ideia que não funcionou, quando o problema era a fundação.

---

## 7. ✅ Regra final

Se em qualquer momento você não tiver certeza se um plugin suporta a versão
atual do servidor, **não instale e confirme na fonte oficial**. O custo de esperar
é zero. O custo de um servidor que não sobe, ou pior, de um mundo corrompido, é alto.

Sem pressa é uma decisão de projeto, não uma desculpa. Respeite ela.
