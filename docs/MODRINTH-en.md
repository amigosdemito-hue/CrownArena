# CrownArena (Evento PvP)

Automatic PvP event plugin for **Paper 26.2 (Java 25)**. At the scheduled time it opens an
invitation to everyone; those who accept join a **private lobby and arena worlds** with a
**separate inventory**, all with the **same kit** and no teams, until the event ends. The main
server sees global announcements of kills, popped totems, streaks and a scoreboard, and arenas
rotate automatically.

> Note: the jar **includes** the lobby and 5 arenas (third-party maps) so it works out of the
> box. Prefer a light jar? Put your own maps in `plugins/CrownArena/world-templates/` or remove
> `worlds/` from the jar.

## Features

- **Configurable automatic schedule**: by default **every other day** with a per-weekday time;
  also daily, weekly or a single time. Global reminders (30/10/5/1 min) and manual start.
- **Native invitation dialog** ("Do you want to join?"), clickable message and `/evento aceptar`.
- **Separate inventory**: on accept it saves inventory, armor, offhand, XP, health, hunger,
  effects, gamemode and position, and restores everything at the end (also if the player
  disconnects or the server crashes).
- **Waiting lobby** and final countdown before dropping everyone into the arena at once.
- **Same kit for everyone**, configurable, fully refilled on respawn.
- **Lossless death**: no drops, no XP loss, instant respawn, short spawn protection (lost on
  attack).
- **Global announcements**: kill with the weapon used, popped totem, first blood, streaks and
  streak-breaker, plus a periodic scoreboard.
- **Rotating arenas in dedicated worlds** (5 by default) with several spawns each and automatic
  rotation every X minutes with a warning.
- **Worlds embedded in the jar**: installed automatically on startup (no Multiverse or separate
  map uploads needed). Supports nether/end and nested templates.
- **Progressive mob waves** during combat; the world spawns no natural mobs and explosions don't
  break terrain.
- **Participants-only world**: non-participants (and non-admins) can't enter.
- **HUD**: sidebar (time, arena, kills, deaths, streak, top 3) and a time boss bar.
- **End**: ranking, winner title, fireworks, console rewards and inventory restore.
- **Historical stats** (`history.yml`): `/evento stats` and `/evento ranking`.
- **Fully editable messages** (`messages.yml`, MiniMessage).
- **Transparent sidebar on Bedrock** (resource pack included for Geyser).

## Requirements

- **Paper 26.2** server (or compatible) and **JDK 25**.
- Optional: **Geyser** for Bedrock players (transparent sidebar).

## Installation (2 minutes)

1. Copy `EventoPvP-1.2.0.jar` to `plugins/` and start the server. The plugin installs the lobby
   and 5 arenas (each in its own world) and creates `config.yml` and `messages.yml`. The in-game
   command is `/evento` and the data folder is `plugins/EventoPvP/`.
2. Set the schedule in `config.yml` (`schedule.first-date`, `schedule.every-days`,
   `schedule.times`), the kit and rewards. Apply with `/evento recargar`.
3. Check the next event with `/evento proximo` and test with 2 accounts using `/evento iniciar`.

## Commands

Public: `aceptar, top, stats, ranking, proximo, info`.
Admin (`evento.admin`): `iniciar, detener, spawn, lobby, borrar, lista, tparena, mundo,
expulsar, recargar, instalar`.

## Full documentation

- Automatic activation, rules and features step by step: **GUIA-COMPLETA.md** (`docs/`).
- Commands and permissions: **COMANDOS.md**.
- All `config.yml`, kit and `messages.yml` options: **CONFIGURACION.md**.
- Event flow and data files: **FUNCIONAMIENTO.md**.
- Troubleshooting: **SOLUCION-DE-PROBLEMAS.md**.

## Links

- Source code: https://github.com/amigosdemito-hue/CrownArena

## License and credits

- Plugin code: **MIT**.
- The included maps (lobby and arenas) are **third-party**; check/credit their original authors
  before redistributing.
