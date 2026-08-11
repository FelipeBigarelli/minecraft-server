# Auditoria e correções no GitHub — 11/08/2026

## Objetivo

Revisar o repositório usando os próprios documentos do projeto como contexto,
confirmar quais achados da auditoria eram reais e aplicar diretamente na `main`
as correções de maior prioridade sem iniciar o servidor nem tocar no mundo do
runtime.

## Fonte de verdade usada

Foram confrontados:

1. código atual do repositório;
2. `HANDOFF.md`;
3. `PLANO-EXECUCAO.md`;
4. `README.md`;
5. `COMO-RODAR.md`;
6. histórico de commits/releases.

Quando documentação antiga e código divergiam, o estado real do repositório foi
priorizado e os documentos correntes foram atualizados.

---

## Correções aplicadas

### 1. Backup privado separado do snapshot compartilhável

Problema: `backup.sh` inclui mundo, plugins, configs e arquivos administrativos,
mas o README antigo ensinava publicar esse `.tar.gz` em uma GitHub Release
pública.

Correção:

- `backup.sh` agora se identifica explicitamente como **privado**;
- criado `export-world.sh`, que inclui somente os mundos;
- o export exige servidor desligado;
- o export valida gzip e imprime SHA256;
- README/HANDOFF/guia operacional foram atualizados;
- a release `mundo-2026-08-04` ficou documentada como **legada** e não deve ser
  usada como modelo para releases futuras.

Observação: o mundo ainda pode conter dados de jogo/jogadores. A separação evita
vazar configs/plugins/credenciais do runtime, mas não transforma o conteúdo do
mundo em dado anônimo.

### 2. `RAM` e `SERVER_DIR` agora persistem

Problema: o setup aceitava `RAM=8G` e `SERVER_DIR=~/mc`, mas o `start.sh` voltava
posteriormente para seus próprios defaults.

Correção:

- setup gera `<runtime>/scripts/server.env`;
- o arquivo contém defaults operacionais, não segredos;
- `start.sh`, `backup.sh` e `export-world.sh` leem os mesmos defaults;
- variável passada diretamente na execução continua tendo prioridade;
- `FORCE_CONFIG=1` pode atualizar o `server.env` de forma deliberada;
- `.gitignore` ignora `server.env`.

### 3. Scanner de JAR corrigido

Problema: o plano antigo usava `grep -R` em `.jar`, mas JAR é um ZIP e isso não
inspeciona de forma confiável suas entradas internas.

Correção:

- o plano passou a usar `jar tf`;
- ficou explícito que esse teste é só uma rede de segurança;
- origem, versão e hash continuam sendo verificações obrigatórias.

### 4. Persistência SQLite simplificada

Problema: o plano mandava adicionar/shadear `sqlite-jdbc` e introduzir HikariCP
automaticamente.

Correção baseada na documentação atual do Paper:

- Paper já fornece o driver JDBC SQLite;
- não é necessário adicionar/shadear `sqlite-jdbc` no MVP;
- HikariCP não entra automaticamente no SQLite local;
- o plano agora prioriza repository + fila limitada + batch + executor dedicado;
- PostgreSQL e outras libs externas serão decididos quando houver necessidade.

### 5. CI mínimo criado

Criado `.github/workflows/ci.yml` com:

- `bash -n server/scripts/*.sh`;
- Java 25;
- `mvn verify` do BigaCore;
- `py_compile` do exportador de sessões;
- execução manual (`workflow_dispatch`).

---

## Decisões mantidas

Não foram alteradas:

- Paper 26.2 build 92;
- Java 25;
- Adventure/MiniMessage;
- operação manual do servidor pelo Felipe;
- regra de agentes não iniciarem o servidor;
- estrutura pequena atual do BigaCore;
- systemd continua opcional/não instalado;
- desenvolvimento faseado do narrador;
- Fase 2 (memória) antes da IA.

---

## Ação fora do código ainda pendente

A release pública `mundo-2026-08-04` foi criada antes da separação e contém
assets `mc-backup-*.tar.gz` com configs e arquivos administrativos. O repositório
agora impede que esse padrão continue, mas os assets antigos devem ser revisados
e, de preferência, removidos/substituídos por um `mc-world-*.tar.gz` sanitizado
para o objetivo de compartilhamento.

---

## Próximo passo recomendado

Depois de aplicar os scripts novos no runtime (sem iniciar o servidor por agente),
retomar a Fase 1 do `PLANO-EXECUCAO.md`, sempre confirmando versões atuais dos
plugins antes de instalar qualquer JAR.
