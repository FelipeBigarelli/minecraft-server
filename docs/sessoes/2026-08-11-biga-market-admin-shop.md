# Biga Market vira loja do servidor — 11/08/2026

## Decisão

O dono do servidor pediu que a Biga Market fosse **do servidor**, um Admin Shop,
e não um conjunto de bancas de jogador.

Isso contraria o desenho original registrado em `ECONOMIA.md` e no handoff
("Admin Shop ilimitada: desativada", "os baús da Biga Market NÃO são Admin Shops
infinitas"). A objeção foi apresentada antes, com a alternativa explícita, e a
decisão foi reafirmada. Está implementado.

## O problema que não podia ser ignorado

Um Admin Shop tem dois lados, e eles têm riscos opostos:

```text
servidor VENDE  -> jogador paga -> moeda SAI de circulação -> sink,   seguro
servidor COMPRA -> jogador ganha -> moeda ENTRA            -> faucet, perigoso
```

Em Minecraft quase tudo é automatizável. Uma recompra ilimitada de cobblestone
transforma uma farm AFK em impressora de dinheiro e mata o mercado entre
jogadores — que é a premissa do servidor.

## Solução: os dois lados, implementados de formas diferentes

| Lado | Como | Limite |
|---|---|---|
| Servidor vende | placas ChestShop `Admin Shop` na parede do fundo, estoque infinito | nenhum (é sink) |
| Servidor compra | `/biga eco vender` no BigaCore | **teto diário e semanal por jogador** |

A recompra **não é placa** porque o ChestShop não sabe aplicar teto por jogador,
e adicionar a API do ChestShop como dependência de compilação traria um
repositório Maven novo para o CI. Passando pelo BigaCore, os limites
`admin-buyback-daily-cap: 150` e `weekly-cap: 900` — que já existiam como
política escrita, sem código — finalmente valem.

Vazão máxima de moeda nova: `jogadores × 150 B$/dia`, contra os sinks de 4% de
imposto, 50 B$ por loja criada e a venda no varejo.

### Regras da recompra

- vende só o item **na mão**, nunca o inventário (não é um `/sellall` disfarçado);
- recusa item com encantamento, nome ou dano: preço de tabela não vale para item único;
- pagamento arredonda **para baixo**, sempre;
- debita o item antes de creditar; se o depósito falhar, o item volta.

### O que entra na parede

15 vagas, definidas em `admin-shop.wall`. Só material de construção e consumo
básico. Item de progressão fica fora — e há teste que **falha o build** se
diamante, elytra, netherite, totem ou shulker shell aparecerem lá.

## Bugs encontrados no caminho

### `nether_quartz` era um preço inalcançável

O catálogo é indexado pelo nome do `Material`. A chave `nether_quartz` não casa
com nada: o item do Nether é `Material.QUARTZ`. O preço existia, tinha
`server-buy`, e mesmo assim o servidor responderia "não compro isso".

Renomeado para `quartz`, e `EconomyCatalogTest` agora falha o build se qualquer
chave do catálogo deixar de casar com um Material real.

### `doctor.sh` reprovava um servidor saudável

```text
[ERRO] EternalEconomy: defaultBalance não está em 250.
```

O valor estava certo. Ao gravar o próprio config no primeiro boot, o
EternalEconomy normaliza o número para string e escreve `defaultBalance: '250'`.
O regex do doctor não aceitava aspas, então bloqueava o boot de um servidor
perfeitamente configurado. Corrigido.

## Seis defeitos da loja construída

A leitura dos blocos do mundo depois da primeira construção real revelou:

1. **gable traseiro ausente** — rombo triangular aberto no fundo (Y=74..78);
2. **folhas de azaleia apodreceram** — colocadas com `persistent=false`;
3. **cumeeira era uma calha** — o slab substituía a telha do topo;
4. **degraus do telhado sem face vertical** — dava para ver o sótão de lado;
5. **lanterna apagava um bloco da lona** do toldo;
6. `placeSideWallBlock` era código morto, 100% sobrescrito.

Todos corrigidos. `RoofGeometryTest` prova a continuidade do telhado por
aritmética, sem servidor.

## Verificação

```text
mvn clean verify ......... BUILD SUCCESS, 33 testes
doctor.sh ................ aprovado, 0 avisos
bash -n scripts/*.sh ..... OK
```

## Limitações conhecidas

- ~~Placas geradas por código ainda não validadas em jogo.~~ **Validado em
  12/08/2026:** o balcão responde ao clique, os nomes em português resolvem pelo
  `itemAliases.yml` e os itens giram sobre os barris.
- ~~`ChestShop.shop.limit.<n>`~~ **Premissa errada, corrigida na RODADA 02:**
  esse permission node não existe no ChestShop 3.13-pre-1. `player-shop-first-limit`
  passou a PLANEJADO / NÃO APLICADO, com o caminho futuro (`PreShopCreationEvent`)
  documentado no `economy.yml`.
- `combined-mint-daily-cap` segue só documentado, renomeado para `-PLANEJADO`.

## Validado em runtime (12/08/2026)

Log do boot, sem nenhum `SEVERE`:

```text
[teto] Teto de recompra ligado nas placas do Admin Shop.
[display] 60 item(ns) flutuante(s) removido(s).
Snapshot da Biga Market salvo: 17980 blocos em loja-snapshot.yml.
[loja] 968 bloco(s) de árvore/vegetação removidos da footprint.
[admin-shop] Balcão montado com 60 vaga(s) de 61 disponíveis.
```

A linha do teto é a que mais importa: ela prova que o hook por reflexão
encontrou `PreTransactionEvent`, resolveu os quatro métodos e o enum `SELL`, e
registrou o listener. Como `ativo()` retornou true, as placas nasceram com o
lado de compra — se tivesse falhado, o fail-closed as teria deixado só de venda.

---

## Continuação — ampliação, item flutuante e saldo inicial

### O saldo inicial nunca foi aplicado

O banco tinha uma linha só, e ela estava zerada:

```text
('261ba52f-…', 'zBigaBiga', 0)
```

`defaultBalance: '250'` estava certo no EternalEconomy e mesmo assim a conta
nasceu com 0 — provavelmente criada por um caminho Vault que ignora esse
default. A política é do BigaCore, então agora quem a aplica é o BigaCore
(`StartingBalance.java`), no login, com duas travas independentes: marca o UUID
num arquivo próprio e credita apenas o **complemento até** 250, nunca uma soma.

### O tamanho esbarrou no terreno, não no código

Varredura do mundo real num raio de 140 blocos, para três footprints:

| Footprint | Locais sem árvore |
|---|---|
| 21x17 (antiga) | 2 |
| 25x21 | 0 |
| 27x25 | 0 |
| 31x29 (nova) | 0 |

A região é floresta densa de spruce. Existe terreno perfeitamente plano
(`x=16, z=-32`, desnível 1), mas sempre sob a mata — 931 colunas com árvore.

Daí a opção `loja-spawn.limpar-arvores`, desligada por padrão. Ligada, a
construção derruba folha e tronco **dentro da footprint** e nada mais: líquido,
TileState e estrutura existente continuam bloqueando, e nada é cavado.

### Ampliação

- footprint 21x17 → **31x29** (357 → 899 colunas);
- toda a geometria derivada de quatro constantes no topo da classe, em vez de
  `-8`/`8`/`2`/`12` espalhados por dez métodos;
- balcão de 15 → **60 itens**, em três paredes (fundo + duas laterais).

### Item flutuante

O ChestShop 3.13 não tem essa funcionalidade — a única opção parecida no config
é `SHOWITEM_MESSAGE`, que só afeta o chat. É característica do QuickShop, outro
plugin. Implementado no BigaCore com `ItemDisplay` nativo do Paper
(`ShopDisplays.java`), com tag de propriedade para que o rollback consiga
limpar: snapshot cobre **blocos**, e entidade órfã ficaria boiando sobre o mato.

### Bug encontrado na ampliação

`Layout.block()` tinha um `7` fixo como meio da footprint — correto para 21x17,
mentira para 31x29. O centro reportado por `/biga loja local` ficaria deslocado
do centro real da construção. Agora sai de `CENTER_V`.
