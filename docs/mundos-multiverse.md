# Mundos separados com Multiverse

Instalado em 04/11/2026.

| Plugin | Versão | Para quê |
|---|---|---|
| `multiverse-core-5.8.2.jar` | 5.8.2 | criar e administrar mundos |
| `multiverse-inventories-5.3.6.jar` | 5.3.6 | inventário separado por mundo |

Os dois foram baixados do Modrinth e conferidos por SHA-512. Ambos declaram
suporte a 26.3.

## Por que o Inventories não é opcional

Sem ele o servidor tem **um** inventário por jogador, válido em todos os
mundos. Qualquer coisa pega no criativo caminha para o survival no bolso de
quem vai, e aí não existe mais item escasso — o `economy.yml`, o preço do
ChestShop e a loja do spawn viram enfeite, porque tudo que eles cobram pode
ser gerado de graça num mundo ao lado.

É o único jeito de ter criativo e economia no mesmo servidor.

## O que já estava separado antes do Multiverse

O `plots` do CreativePlots **já é um mundo Bukkit de verdade** — aparece no
boot como `World Settings For [minecraft:plots]` e o WorldGuard já mantém
região separada nele. O Multiverse não precisa criar nada para os plots, e
mexer neles por aqui só arriscaria quebrar o que funciona.

O que o Multiverse acrescenta é inventário e modo de jogo por mundo, além dos
mundos novos.

## Primeiro boot

Depois de reiniciar, no console:

```
/mv list
```

Se o `plots` aparecer na lista, está só sendo acompanhado — sem problema. Se o
console mostrar erro de carregar `plots` duas vezes (CreativePlots e Multiverse
disputando), tire ele do Multiverse com:

```
/mv remove plots
```

`remove` tira do controle do Multiverse. **Não apaga mundo nenhum** — quem apaga
é `/mv delete`, que não deve ser usado aqui.

## Criar os mundos

A sintaxe real da 5.8.2 (lida do jar, não de tutorial velho):

```
/mv create <nome> <environment> [--seed ... --generator ... --world-type ...
           --no-structures --no-adjust-spawn --biome ... --properties k=v,k=v]
```

### Criativo

```
/mv create criativo normal --world-type flat --no-structures
/mv modify criativo set gamemode creative
/mv modify criativo set difficulty peaceful
/mv modify criativo set allow-flight true
/mv modify criativo set alias "&6Criativo"
```

### Minigames

```
/mv create minigames normal --world-type flat --no-structures
/mv modify minigames set gamemode adventure
/mv modify minigames set difficulty easy
/mv modify minigames set alias "&bMinigames"
```

`gamemode` no Multiverse é obrigatório, não sugestão: ele troca o modo de quem
entra, e devolve na saída. Sem isso, um jogador em criativo que atravessasse
para o survival continuaria em criativo.

Para ver a lista inteira de propriedades: digite `/mv modify criativo set ` e
aperte Tab.

### Sobre o terreno dos mundos novos

Datapack é por mundo: fica em `<mundo>/datapacks/`. O FloatingIslands está
dentro de `world/datapacks/`, então **mundo criado pelo Multiverse nasce com
terreno normal**, não com ilhas flutuantes. É o que se quer para criativo e
minigames.

Se um dia quiser outro mundo de ilhas, copie o zip do datapack para
`<mundo>/datapacks/` **antes** de gerar — depois de gerado não converte.

Mundo totalmente vazio (void), do jeito que minigame costuma usar, precisa de um
plugin gerador. O Multiverse sozinho não faz void; `flat --no-structures` é o
mais perto disso sem instalar mais nada.

## Separar os inventários — a parte que protege a economia

O Multiverse-Inventories começa com um grupo padrão em que todos os mundos
compartilham tudo. Ou seja: **instalar não separa nada**. Separar é trabalho de
grupo.

A ideia: mundos no mesmo grupo compartilham; mundos em grupos diferentes ficam
isolados.

```
/mvinv group create survival inventory,armor,ender_chest,health,hunger,experience,level,potions world,world_nether,world_the_end
/mvinv group create criativo inventory,armor,ender_chest,health,hunger,experience,level,potions criativo
/mvinv group create minigames inventory,armor,ender_chest,health,hunger,experience,level,potions minigames
/mvinv group create plots     inventory,armor,ender_chest,health,hunger,experience,level,potions plots
```

Depois confira, e só então confie:

```
/mvinv info world
/mvinv list
```

### Não inclua `economy` nas shares

O Multiverse-Inventories sabe separar saldo (`economy`, `balance`, `money`) e
seria um desastre aqui: o servidor tem **uma** moeda, o B$, e o saldo vive no
EssentialsX. Separar por mundo daria a cada jogador uma carteira por mundo, as
lojas do spawn cobrariam de uma e o criativo pagaria de outra.

Por isso as shares acima são listadas uma por uma em vez de `everything`.
`everything` é cômodo e inclui coisas que a gente não quer.

### Teste antes de abrir para os outros

1. Entre no survival, deixe algo marcante no inventário.
2. `/mv tp criativo` — o inventário tem que estar **vazio**.
3. Pegue um bloco qualquer do criativo.
4. `/mv tp world` — o bloco do criativo **não** pode ter vindo, e o item do
   survival tem que estar de volta.
5. `/balance` nos dois mundos tem que dar o mesmo número.

Se o passo 4 falhar, pare e conserte o grupo antes de deixar alguém entrar —
depois de um jogador trazer uma pilha de diamante do criativo, não tem como
saber o que é legítimo.

## Portais e viagem

Não instalei o Multiverse-Portals: o servidor já usa **AdvancedPortals**, e dois
plugins de portal no mesmo bloco disputam o evento de entrada.

Para os jogadores viajarem sem ser op, o caminho é LuckPerms:

```
/lp group default permission set multiverse.teleport.self.criativo true
/lp group default permission set multiverse.teleport.self.minigames true
```

Ou, melhor para quem está começando: um portal do AdvancedPortals no spawn, que
não exige comando nenhum.

## Interação com o `/terreno`

A venda de terreno no survival usa lista branca de mundos (`mundos:` no
`terrenos.yml`). Mundo novo **não** entra vendendo terreno — é de propósito, para
ninguém comprar 64x64 no meio da arena de minigame.

Se um dia um mundo novo tiver que vender terreno, acrescente o nome lá e dê
`/biga reload`.
