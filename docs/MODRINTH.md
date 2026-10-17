# CrownArena (Evento PvP)

Plugin de **evento PvP automático** para servidores **Paper 26.2 (Java 25)**. A la hora
programada abre una invitación a todos los jugadores; quienes aceptan pasan a un **lobby y
arenas en mundos propios** con **inventario separado**, todos con el **mismo kit** y sin
equipos, hasta que el evento termina. El servidor general ve anuncios globales de kills,
tótems reventados, rachas y marcador, y las arenas rotan solas.

> Nota: el jar **incluye** el lobby y las 5 arenas (mapas de terceros) para que funcione
> "recién instalado". Si prefieres un jar ligero, pon tus propios mapas en
> `plugins/CrownArena/world-templates/` o borra `worlds/` del jar.

## Características

- **Programación automática configurable**: por defecto **día por medio** con la hora según
  el día de la semana; también admite diario, semanal u hora única. Avisos globales previos
  (30/10/5/1 min) y arranque manual.
- **Invitación nativa** ("¿Deseas asistir?"), mensaje clicable y `/evento aceptar`.
- **Inventario separado**: al aceptar se guarda inventario, armadura, mano secundaria, XP,
  vida, hambre, efectos, modo de juego y posición, y se restaura al terminar (también si el
  jugador se desconecta o el servidor cae).
- **Lobby de espera** y cuenta final antes de soltar a todos en la arena a la vez.
- **Mismo kit para todos**, configurable, con reposición completa al reaparecer.
- **Muerte sin pérdida**: sin drops ni pérdida de XP, reaparición inmediata, invulnerabilidad
  corta al aparecer (se pierde al atacar).
- **Anuncios globales**: kill con el arma usada, tótem reventado, primera sangre, rachas y
  quién corta una racha, y marcador periódico.
- **Arenas rotativas en mundos propios** (5 por defecto) con varios spawns cada una y cambio
  automático cada X minutos con aviso.
- **Mundos incluidos en el jar**: se instalan solos al arrancar (no necesitas Multiverse ni
  subir mapas aparte). Soporta nether/end y plantillas anidadas.
- **Hordas de mobs progresivas** durante el combate; el mundo no genera mobs ni rompe terreno.
- **Solo participantes en el mundo**: quien no participa (ni es admin) no puede entrar.
- **HUD**: marcador lateral (tiempo, arena, kills, muertes, racha, top 3) y barra de tiempo.
- **Final**: ranking, título del ganador, fuegos artificiales, recompensas por consola y
  devolución de inventarios.
- **Estadísticas históricas** (`history.yml`): `/evento stats` y `/evento ranking`.
- **Mensajes 100 % editables** (`messages.yml`, MiniMessage).
- **Marcador transparente en Bedrock** (resource pack incluido para Geyser).

## Requisitos

- Servidor **Paper 26.2** (o compatible) y **JDK 25**.
- Opcional: **Geyser** si entran jugadores de Bedrock (para el marcador transparente).

## Instalación (2 minutos)

1. Copia `EventoPvP-1.2.0.jar` a `plugins/` y arranca el servidor. El plugin instala solo
   el lobby y las 5 arenas (cada una en su mundo), y crea `config.yml` y `messages.yml`.
   El comando del juego es `/evento` y la carpeta de datos es `plugins/EventoPvP/`.
2. Ajusta el horario en `config.yml` (`schedule.first-date`, `schedule.every-days`,
   `schedule.times`), el kit y las recompensas. Aplica con `/evento recargar`.
3. Comprueba el próximo evento con `/evento proximo` y pruébalo con 2 cuentas con
   `/evento iniciar`.

## Comandos

Públicos: `aceptar, top, stats, ranking, proximo, info`.
Admin (`evento.admin`): `iniciar, detener, spawn, lobby, borrar, lista, tparena, mundo,
expulsar, recargar, instalar`.

## Documentación completa

- Activación automática, reglas y funciones paso a paso: **GUIA-COMPLETA.md** (`docs/`).
- Comandos y permisos: **COMANDOS.md**.
- Todas las opciones de `config.yml`, kit y `messages.yml`: **CONFIGURACION.md**.
- Flujo del evento y archivos de datos: **FUNCIONAMIENTO.md**.
- Solución de problemas: **SOLUCION-DE-PROBLEMAS.md**.

## Enlaces

- Código fuente: https://github.com/amigosdemito-hue/CrownArena

## Licencia y créditos

- Código del plugin: **MIT**.
- Los mapas incluidos (lobby y arenas) son de **terceros**; consulta/atribuye a sus autores
  originales antes de redistribuirlos.
