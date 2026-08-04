# 🚀 PLANO DE EXECUÇÃO — Do servidor limpo ao narrador vivo

> **Para o Claude Code.** Leia o `HANDOFF.md` antes deste arquivo — ele tem o estado da máquina, as decisões tomadas e as armadilhas já descobertas.
>
> Este documento é um plano faseado. **Não execute tudo de uma vez.** Cada fase termina num checkpoint onde você para e reporta.

---

## 0. ⚠️ REGRAS QUE VALEM PARA TUDO

### 0.1 Os números deste documento podem estar errados

As versões, builds e URLs aqui vieram de pesquisa feita em **04/08/2026**. Plugins atualizam semanalmente. **Antes de baixar qualquer coisa, confirme na página oficial** se há build para Paper 26.2.

Se um plugin só listar até 26.1.2, **não instale e reporte.** Rodar plugin de versão anterior no 26.2 pode funcionar ou pode quebrar de formas silenciosas — não vale o risco num servidor que ainda está sendo construído.

### 0.2 Fontes permitidas

✅ **Modrinth**, **Hangar** (hangar.papermc.io), **GitHub Releases oficiais**, **SpigotMC oficial**, site oficial do projeto.

❌ **NUNCA** baixe de: spigotunlocked, blackspigot, nullforums, ou qualquer site de plugin "nulled"/crackeado. São vetores conhecidos de malware.

### 0.3 O incidente de malware do SpigotMC é real

A PaperMC documentou malware distribuído por contas de autores comprometidas. Depois de baixar qualquer `.jar`, rode:

```bash
cd ~/minecraft/plugins && grep -R "plugin-config.bin" . && echo "⚠️ SUSPEITO" || echo "limpo"
```

Se aparecer `plugin-config.bin` dentro de um jar, **pare tudo e reporte imediatamente.**

### 0.4 Backup antes de cada fase

```bash
cd ~/minecraft && bash scripts/backup.sh
```

Com o servidor **desligado** — senão o arquivo sai marcado `-quente`. Confirme que o `.tar.gz` foi criado antes de prosseguir.

### 0.5 Um plugin por vez

Instale, suba o servidor, confirme que carregou sem erro, **só então** instale o próximo. Se subir cinco de uma vez e o servidor não subir, você não sabe qual foi.

### 0.6 Meça antes e depois

O **spark já vem embutido no Paper**. Antes de instalar qualquer coisa, capture o baseline:

```
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

### 1.2 Armadilhas específicas desta fase

🔴 **NUNCA instale WorldEdit e FastAsyncWorldEdit juntos.** Conflito de comandos. FAWE já implementa a API do WorldEdit — plugins que dependem de WorldEdit funcionam com o FAWE instalado.

🔴 **LuckPerms deve ser o único plugin de permissões.** Se houver qualquer outro, os checks quebram de forma imprevisível.

🟡 **LuckPerms baixa libs no primeiro start.** Precisa de internet. Se falhar, a pasta `plugins/LuckPerms/libs/` pode ser copiada manualmente.

🟡 **WorldGuard 7.0.17 roda em 26.2 mas loga warnings conhecidos de listener.** Se aparecerem, documente e siga — não são fatais.

🟡 **WorldEdit para 26.2 estava em BETA** na data da pesquisa. Como estamos usando FAWE, isso não deve nos afetar, mas confirme que a build do FAWE declara 26.2.

🟡 **EssentialsX x economia:** conflito conhecido por `/pay` e `/balance`. Como não vamos instalar plugin de economia agora, não deve aparecer. Se aparecer aviso no startup, reporte.

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
- [ ] Resultado do scan de `plugin-config.bin`

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

**PersistentDataContainer (PDC)** — para dados por-jogador/por-entidade simples: contadores, flags, última posição de morte. É a API nativa do Paper, sobrevive a restart, não precisa de banco. Note que a antiga `Metadatable` API foi deprecada no 1.21.9 justamente por causar memory leak — use PDC.

**SQLite** — para o world state consultável: histórico de eventos, agregações, o que o narrador vai ler. Use **HikariCP** como pool de conexões (padrão de fato).

Adicione ao `pom.xml`:
- `org.xerial:sqlite-jdbc`
- `com.zaxxer:HikariCP`

⚠️ **Agora o `maven-shade-plugin` vai fazer trabalho de verdade.** Ele vai embutir essas libs no jar. **Faça relocation dos pacotes** para evitar conflito de classloader se outro plugin também usar HikariCP:

```xml
<relocations>
  <relocation>
    <pattern>com.zaxxer.hikari</pattern>
    <shadedPattern>codes.biga.bigacore.lib.hikari</shadedPattern>
  </relocation>
</relocations>
```

Sem isso, dois plugins com versões diferentes da mesma lib se sabotam.

⚠️ **Quando migrar para VPS, isso vira Postgres.** O Felipe já domina Postgres via Supabase. Escreva a camada de acesso a dados atrás de uma interface para a troca não ser cirurgia.

### 2.4 Arquitetura

```
Listeners (main thread)
    ↓  só enfileiram, nunca escrevem em disco
Fila em memória (thread-safe)
    ↓  flush periódico assíncrono
SQLite (async)
    ↓
Camada de consulta → agregações prontas para o narrador
```

🔴 **NUNCA faça I/O na thread principal.** Nem SQLite. Todo write vai em `runTaskAsynchronously()`. Um listener que grava em disco a cada evento congela o servidor inteiro.

🔴 **Voltar para a main thread antes de tocar no mundo.** A API do Bukkit não é thread-safe. Padrão: async para buscar/gravar → `runTask()` para aplicar no jogo.

### 2.5 Comando de inspeção

Crie `/biga memoria` (permissão `bigacore.admin`) que mostra o que foi coletado. Sem isso você está construindo às cegas.

Use Adventure/MiniMessage — já está no projeto e funciona.

### ✋ CHECKPOINT 2

- [ ] Esquema do banco (mostre o DDL)
- [ ] Quais eventos estão sendo coletados
- [ ] `/biga memoria` funcionando com dados reais
- [ ] Confirmação de que nenhum write acontece na main thread
- [ ] MSPT antes × depois — o coletor não pode custar performance perceptível

Deixe rodando por alguns dias de jogo antes da Fase 3. Você precisa de memória real acumulada para testar o narrador.

---

## FASE 3 — 🌌 O narrador

**Objetivo:** o Claude lê a memória do mundo e gera lore.

### 3.1 Referências para estudar antes de escrever

Existem plugins que integram LLM em Minecraft, mas **todos são reativos** (jogador fala → NPC responde). Nenhum é observacional/proativo como este projeto. Isso confirma que o conceito é território aberto — mas os existentes servem de referência arquitetural:

- **CraftGPT** (Modrinth, MIT, repo `zizmax/CraftGPT`) — **já suporta Claude nativamente** via `provider: anthropic`. É a referência mais relevante: veja como faz rate-limit, memória e controle de custo.
- **LLMCraft** (SpigotMC, GPL-3.0) — usa **Langchain4J**. Vale avaliar se essa lib faz sentido aqui ou se chamar a API HTTP direto é mais simples.

⚠️ Ambos foram testados em 1.19–1.21, **não em 26.x**. Estude o código, não instale.

### 3.2 A chave da API

🔴 **NUNCA no código. NUNCA no Git. Nem em commit que será "corrigido depois"** — o histórico guarda para sempre.

O `.gitignore` já cobre `.env`, `*.key` e `**/api-key*`. Use variável de ambiente lida no `onEnable()`. Se estiver ausente, o plugin deve **carregar normalmente com o narrador desativado** e logar um aviso — nunca derrubar o servidor.

### 3.3 Controle de custo — obrigatório desde o primeiro commit

A API é paga por token. Um servidor ativo pode gerar milhares de chamadas por dia se você for ingênuo.

| Mecanismo | Como |
|---|---|
| **Agregação** | Nunca uma chamada por evento. Junte eventos numa janela (ex: 30 min) e faça uma chamada |
| **Rate limit** | Teto rígido de chamadas por hora, configurável |
| **Cache** | Se o world state não mudou significativamente, reutilize a última geração |
| **Kill switch** | `/biga narrador off` desliga tudo na hora |
| **Telemetria** | Logue tokens consumidos por chamada. Sem isso você descobre o custo na fatura |

### 3.4 Fluxo

```
Timer assíncrono (ex: a cada 30 min)
    ↓
Lê agregação do SQLite
    ↓
Monta prompt com o contexto do mundo
    ↓
HttpClient (Java 25, async) → API do Claude
    ↓
Resposta → validação e sanitização
    ↓
runTask() → entrega no jogo via MiniMessage
```

### 3.5 Sanitização — não pule isso

🔴 **O texto que volta da API é dado externo.** O `JogadorListener` já usa `Placeholder.unparsed()` pelo motivo certo — o comentário no código antecipou exatamente este momento.

Texto gerado por IA passando direto por `MiniMessage.deserialize()` permite que a saída contenha `<click:run_command:'/op alguem'>`. **Sempre trate a resposta da API como não-confiável:** insira como conteúdo literal, ou use um MiniMessage com resolver restrito só às tags de cor que você permite.

### 3.6 Degradação com elegância

- API fora do ar → timeout curto, log, silêncio no jogo. **Jamais travar o jogador.**
- Resposta malformada → descarta, não entrega texto quebrado.
- Rate limit estourado → pula o ciclo.

O servidor tem que funcionar perfeitamente com o narrador desligado. Ele é um adorno, não uma dependência.

### 3.7 Fases do narrador

Implemente em ordem. Cada uma é entregável sozinha:

1. **`/biga pergunta <texto>`** — consulta manual com contexto do mundo. Valida o pipeline inteiro com custo controlado.
2. **Narrador passivo** — mensagens ambientes periódicas geradas da memória.
3. **Narrador reativo** — dispara em marcos (boss derrotado, primeira morte de alguém num lugar onde outro já morreu).
4. **Narrador ativo** — provoca eventos no mundo: clima, invoca mob, cria estrutura.

### ✋ CHECKPOINT 3

- [ ] `/biga pergunta` funcionando
- [ ] Custo por chamada medido e logado
- [ ] Kill switch testado
- [ ] Sanitização testada (force uma resposta com tag maliciosa e confirme que não executa)
- [ ] Servidor funciona normalmente com a chave ausente

---

## FASE 4 — 🎨 Conteúdo custom

> Só depois que o narrador estiver de pé. Isso aqui é o corpo; o narrador é a alma.

### 4.1 Blocos e itens — CraftEngine

**Recomendação: CraftEngine (versão Free, open-source)** por Xiao-MoMi.

Por que ele e não Oraxen/Nexo/ItemsAdder:

Os plugins tradicionais reaproveitam block states não usados — tipicamente o **note block** (o mecanismo "REAL_NOTE"). Funciona visualmente, mas quebra em coisas sutis: uma árvore custom feita de note blocks faz as folhas conectadas apodrecerem, e transformações de datapack (rotação, espelhamento) não funcionam.

O CraftEngine registra **blocos reais** injetando tipos nos registries do servidor. A doc oficial é direta: seus troncos custom são troncos de verdade e mantêm as folhas vivas. **Para um servidor que quer worldgen e estruturas originais, essa diferença é decisiva.**

Além disso: é server-side, gera o resource pack automaticamente, e blocos tipo porta/laje/cerca são core (no Nexo são addon pago).

⚠️ Confirme suporte a 26.2 antes de instalar.

**Alternativas, se o CraftEngine não servir:** Nexo (pago, sucessor do Oraxen), ItemsAdder (US$ 24,99, mas estava só em 26.1.2 e tem histórico de updates lentos). **Oraxen está em declínio — não comece projeto novo nele.**

🔴 **Escolha UM.** Empilhar dois plugins de conteúdo custom gera conflito de resource pack.

### 4.2 Mobs — MythicMobs + ModelEngine

- **MythicMobs** tem versão Free e suporta 26.2 (Hangar). Comportamento de mob por YAML: fases, skills, bossbar.
- **ModelEngine** (pago) dá modelo 3D animado. Suporta 26.2.
- **BetterModel** aparece como alternativa open-source — vale investigar antes de pagar.

Comece com o MythicMobs Free. Só compre ModelEngine quando tiver um boss que justifique.

### 4.3 NPC — quando o narrador ganhar corpo

| Opção | Quando escolher |
|---|---|
| **Citizens** (2.0.43, 26.2 confirmado) | Maior API e ecossistema. Melhor para controlar programaticamente do BigaCore |
| **FancyNpcs** (MIT, leve, packet-based) | Se você quer leveza e vai escrever toda a lógica você mesmo. Confirmado até 26.1.2 |

Para este projeto, **Citizens** provavelmente vence pela API — o narrador precisa ser controlado por código.

### 4.4 Se precisar mexer em packets

Use **PacketEvents**, não ProtocolLib. PacketEvents é assíncrono, multi-plataforma e suporta 26.1 diretamente. O ProtocolLib ainda funciona mas está em declínio — só instale se um plugin de terceiro exigir.

---

## 5. 🚫 O que NÃO instalar

| Plugin | Motivo |
|---|---|
| **Dynmap** | Notoriamente pesado. Use **BlueMap**, **squaremap** ou **Pl3xMap** — relatos de render em ~10 min contra ~2 dias do Dynmap |
| **WorldEdit** (junto com FAWE) | Conflito de comandos |
| Qualquer plugin de permissões além do LuckPerms | Quebra os checks |
| Anticheat, por enquanto | Pesados e desnecessários num servidor entre amigos |
| Plugins parados há mais de 1 ano | Cheque a data do último commit antes de adotar |

---

## 6. 📅 Ordem sugerida de trabalho

```
Fase 1 (infra)          →  1 sessão
Fase 2 (coletor)        →  várias sessões — é a base de tudo
   ↓ deixar rodando e acumular memória real
Fase 3 (narrador)       →  incremental, 4 sub-fases
Fase 4 (conteúdo)       →  só quando houver o que vestir
```

⚠️ **Não pule a Fase 2.** A tentação de ir direto pro narrador é grande, mas um narrador sem memória boa gera texto genérico — e aí o projeto inteiro parece uma ideia que não funcionou, quando o problema era a fundação.

---

## 7. ✅ Regra final

Se em qualquer momento você não tiver certeza se um plugin suporta 26.2, **não instale e pergunte.** O custo de esperar é zero. O custo de um servidor que não sobe, ou pior, de um mundo corrompido, é alto — e o Felipe declarou horizonte de "construir algo grande sem pressa".

Sem pressa é uma decisão de projeto, não uma desculpa. Respeite ela.
