# -*- coding: utf-8 -*-
"""Traducao pt-BR do WorldGuard 7.0.19 — parte A.

Regras:
  - \x01 e marcador de argumento do StringConcatFactory. Precisa sobreviver
    na mesma QUANTIDADE e na mesma ORDEM, senao os valores saem trocados.
  - %s, %id%, %what%, %page% sao placeholders de formatacao. Idem.
  - Nome de comando, flag e permissao NAO se traduz.
"""

TRAD = {
# ---------- WorldGuardPlugin ----------
"\x01You don't have permission.": "\x01Você não tem permissão.",
"Default configuration file written: \x01": "Arquivo de configuração padrão criado: \x01",
"Failed to check if server is running Folia": "Falha ao verificar se o servidor roda Folia",
"Failed to write default config file": "Falha ao escrever o arquivo de configuração padrão",
"Unable to read default configuration: \x01": "Não foi possível ler a configuração padrão: \x01",
"Unknown actor type. Please report": "Tipo de ator desconhecido. Por favor, reporte",
"WorldEdit detection failed (report error).": "A detecção do WorldEdit falhou (reporte o erro).",
"WorldEdit does not appear to be enabled.": "O WorldEdit não parece estar ativado.",
"WorldEdit does not appear to be installed.": "O WorldEdit não parece estar instalado.",

# ---------- BlockedPotionsListener ----------
"\x01Sorry, arrows with \x01 are presently disabled.": "\x01Desculpe, flechas com \x01 estão desativadas no momento.",
"\x01Sorry, potions with \x01 are presently disabled.": "\x01Desculpe, poções com \x01 estão desativadas no momento.",
"\x01Sorry, potions with \x01 can't be thrown, even if you have a permission to bypass it, due to limitations (and because overly-reliable potion blocking is on).": "\x01Desculpe, poções com \x01 não podem ser arremessadas, mesmo que você tenha permissão para ignorar isso, por limitações técnicas (e porque o bloqueio reforçado de poções está ligado).",

# ---------- ChestProtectionListener ----------
"\x01A chest or double chest above is now protected.": "\x01Um baú ou baú duplo acima agora está protegido.",
"\x01The [Lock] sign must be a sign post, not a wall sign.": "\x01A placa [Lock] precisa ser uma placa de chão, não de parede.",
"\x01The first owner line must be your name.": "\x01A primeira linha de dono precisa ser o seu nome.",
"\x01This chest is protected.": "\x01Este baú está protegido.",
"\x01WorldGuard's sign chest protection is disabled.": "\x01A proteção de baú por placa do WorldGuard está desativada.",
"\x01You do not own the adjacent chest.": "\x01Você não é dono do baú ao lado.",

# ---------- PlayerModesListener ----------
"Enabled auto-god mode for \x01": "Modo deus automático ativado para \x01",
"Enabled water breathing mode for \x01 (player is in group 'wg-amphibious')": "Respiração aquática ativada para \x01 (o jogador está no grupo 'wg-amphibious')",

# ---------- RegionProtectionListener: acoes ("you don't have permission to ___ here") ----------
"break that block": "quebrar esse bloco",
"break things": "quebrar coisas",
"break vehicles": "quebrar veículos",
"change that": "mexer nisso",
"damage that": "danificar isso",
"drop XP": "soltar XP",
"drop items": "soltar itens",
"harm that": "machucar isso",
"hit that": "bater nisso",
"open that": "abrir isso",
"pick up items": "pegar itens",
"place fire": "colocar fogo",
"place that block": "colocar esse bloco",
"place things": "colocar coisas",
"place vehicles": "colocar veículos",
"ride that": "montar nisso",
"take that": "pegar isso",
"use anchors": "usar âncoras",
"use dynamite": "usar dinamite",
"use explosives": "usar explosivos",
"use frostwalker": "usar passos gelados",
"use lingering potions": "usar poções persistentes",
"use that": "usar isso",
"create portals": "criar portais",
"use \x01": "usar \x01",

# ---------- WorldGuardPlayerListener / WorldListener ----------
"\x01/op and /deop can only be used in console (as set by a WG setting).": "\x01/op e /deop só podem ser usados no console (definido numa opção do WG).",
"\x01Can you build? \x01": "\x01Você pode construir? \x01",
"\x01Infinite stack in slot #\x01 removed.": "\x01Pilha infinita no slot #\x01 removida.",
"\x01Infinite stack removed.": "\x01Pilha infinita removida.",
"\x01Intensive server activity has been HALTED.": "\x01A atividade intensa do servidor foi INTERROMPIDA.",
"Applicable regions: \x01": "Regiões aplicáveis: \x01",
"Game mode change on \x01 has been blocked due to the region GAMEMODE flag": "A mudança de modo de jogo em \x01 foi bloqueada pela flag GAMEMODE da região",
"Halt-Act: \x01 entities (>10) auto-removed from \x01": "Halt-Act: \x01 entidades (>10) removidas automaticamente de \x01",
"Halt-Act: \x01 entities (>50) auto-removed from \x01": "Halt-Act: \x01 entidades (>50) removidas automaticamente de \x01",
"WorldGuard host key check: \x01 joined with '\x01' but '\x01' was expected. Kicked!": "Verificação de host key do WorldGuard: \x01 entrou com '\x01' mas era esperado '\x01'. Expulso!",
"WorldGuard: No defined regions here!": "WorldGuard: nenhuma região definida aqui!",
"You did not join with the valid host key!": "Você não entrou com a host key válida!",
"Can build": "Pode construir",
"Current Regions": "Regiões atuais",

# ---------- GeneralCommands ----------
"Compass repointed.": "Bússola reapontada.",
"Compass reset to spawn.": "Bússola voltada para o spawn.",
"God disabled by \x01.": "Modo deus desativado por \x01.",
"God enabled by \x01.": "Modo deus ativado por \x01.",
"God mode disabled!": "Modo deus desativado!",
"God mode enabled! Use /ungod to disable.": "Modo deus ativado! Use /ungod para desativar.",
"Healed by \x01.": "Curado por \x01.",
"Healed!": "Curado!",
"Items compacted into stacks!": "Itens agrupados em pilhas!",
"Players healed.": "Jogadores curados.",
"Players no longer have god mode.": "Os jogadores não estão mais com modo deus.",
"Players now have god mode.": "Os jogadores agora estão com modo deus.",
"Players slain.": "Jogadores mortos.",
"Slain by \x01.": "Morto por \x01.",
"Slain!": "Morto!",

# ---------- ToggleCommands (frases montadas por pedacos) ----------
"This command will ": "Este comando vai ",
" erase ALL animals in ALL loaded chunks in ALL loaded worlds. ": " apagar TODOS os animais em TODOS os chunks carregados de TODOS os mundos. ",
" or type ": " ou digite ",
" to confirm.": " para confirmar.",
"Click to confirm /\x01": "Clique para confirmar /\x01",
"\x01 entities (>10) auto-removed from \x01": "\x01 entidades (>10) removidas automaticamente de \x01",
"ALL intensive server activity halted by \x01.": "TODA a atividade intensa do servidor foi interrompida por \x01.",
"ALL intensive server activity halted.": "TODA a atividade intensa do servidor foi interrompida.",
"ALL intensive server activity is allowed.": "TODA a atividade intensa do servidor está permitida.",
"ALL intensive server activity is not allowed.": "TODA a atividade intensa do servidor está bloqueada.",
"ALL intensive server activity is now allowed.": "TODA a atividade intensa do servidor agora está permitida.",
"Fire spread was already globally disabled.": "O alastramento de fogo já estava desativado globalmente.",
"Fire spread was already globally enabled.": "O alastramento de fogo já estava ativado globalmente.",
"\x01Fire spread is currently globally disabled for this world.": "\x01O alastramento de fogo está desativado globalmente neste mundo.",
}
