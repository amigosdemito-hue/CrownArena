# Guía completa del evento (EventoPvP 1.2)

Este documento explica con detalle **todo** el funcionamiento del plugin: la activación
automática, el flujo del evento, las reglas, los comandos y cada opción de configuración.

---

## 1. Resumen

Evento PvP por rondas: avisa, invita, junta a los jugadores en un lobby, los lanza a una
arena con **kit igual para todos**, hay combate con **rotación de arenas** y **hordas de
mobs**, y al final hay ranking, recompensas y se devuelven inventarios y posiciones
originales. Todo ocurre en **mundos propios** (lobby + una arena por mundo).

- Versión del plugin: **1.2.0**
- Mundos por defecto: lobby `evento_lobby` y arenas `evento_arena_1` … `evento_arena_5`.
- Permisos: `evento.admin` (por defecto solo OP) y `evento.join` (por defecto todos).

---

## 2. Activación automática del evento  ← (lo más importante)

El evento se lanza **solo**, según la sección `schedule` de `config.yml`. También se puede
lanzar a mano con `/evento iniciar`.

### 2.1 Cómo funciona (en palabras simples)

- **`schedule.every-days`** = cada cuántos días cae el evento.
  - `1` = todos los días.
  - `2` = día por medio.
  - `7` = una vez por semana (mismo día).
  - etc.
- **`schedule.times`** = la **hora de cada día de la semana** (`"HH:mm"`). Como el evento
  puede caer en distintos días, según el día en que caiga se usa la hora que le toca.
- **`schedule.first-date`** = la fecha del **primer evento** (`AAAA-MM-DD`). Marca el inicio
  del ciclo; a partir de ahí se suman `every-days` días.
- **`schedule.timezone`** = zona horaria (formato Java, p. ej. `America/Mexico_City`).
- **`schedule.reminders-minutes`** = minutos antes en que se mandan avisos globales.

Regla de cálculo: el plugin toma la última fecha del ciclo que sea ≤ hoy y, si su hora ya
pasó, salta al siguiente múltiplo (`hoy + every-days`). La hora de ese día se saca de
`times` (o de `time` si no configuraste `times`). El planificador revisa cada segundo.

### 2.2 Ejemplo por defecto: **día por medio** con horas por día

```yaml
schedule:
  enabled: true
  timezone: America/Mexico_City
  first-date: "2026-10-19"     # lunes (inicio del ciclo)
  every-days: 2                # 2 = día por medio
  times:
    lunes: "20:21"
    martes: "16:30"
    miercoles: "20:21"
    jueves: "16:30"
    viernes: "20:21"
    sabado: "18:40"
    domingo: "20:21"
  reminders-minutes: [30, 10, 5, 1]
```

Con `every-days: 2` el día va rotando y, a cada día, se le aplica su hora de `times`:

| # | Fecha | Día | Hora |
|---|-------|-----|------|
| 1 | 19/10/2026 | lunes | 20:21 |
| 2 | 21/10/2026 | miércoles | 20:21 |
| 3 | 23/10/2026 | viernes | 20:21 |
| 4 | 25/10/2026 | domingo | 20:21 |
| 5 | 27/10/2026 | martes | 16:30 |
| 6 | 29/10/2026 | jueves | 16:30 |
| 7 | 31/10/2026 | sábado | 18:40 |
| 8 | 02/11/2026 | lunes | 20:21 |
| … | cada 2 días | … | … |

La rotación de días es siempre **lunes → miércoles → viernes → domingo → martes → jueves →
sábado** y vuelve a empezar (ciclo de 14 días / 7 eventos).

### 2.3 Otros horarios útiles

- **Todos los días, con la hora de cada día:** `every-days: 1` y el mismo `times` de arriba.
- **Una sola hora todos los días:** pon solo `times` con la misma hora en cada día, o usa
  la hora única y quita `times`:
  ```yaml
  schedule:
    every-days: 1
    time: "20:00"            # hora única (se usa si no hay 'times')
  ```
- **Un día fijo a la semana (p. ej. todos los sábados 18:40):**
  ```yaml
  schedule:
    first-date: "2026-10-24" # un sábado
    every-days: 7
    time: "18:40"
  ```

### 2.4 Nombres de días aceptados

Con o sin acento, en español o inglés (mayúsculas/minúsculas da igual):
`lunes`/`monday`, `martes`/`tuesday`, `miércoles`/`miercoles`/`wednesday`,
`jueves`/`thursday`, `viernes`/`friday`, `sábado`/`sabado`/`saturday`, `domingo`/`sunday`,
y abreviaturas (`lun`, `mar`, `mié`, `jue`, `vie`, `sáb`, `dom`, `mon`, `tue`, …). También
números `1`–`7` (1 = lunes).

### 2.5 Avisos previos

Con `reminders-minutes: [30, 10, 5, 1]`, se envían mensajes globales a los 30, 10, 5 y 1
minutos antes de cada evento.

### 2.6 Activación manual

- `/evento iniciar` — arranca el evento ya (admin).
- `/evento detener` — lo cancela si está en curso (admin).
- `/evento proximo` — muestra la fecha/hora del próximo evento y cuánto falta.

### 2.7 Qué impide que arranque

- `schedule.enabled: false` (o un evento ya en curso: solo hay uno a la vez).
- Que no haya arenas configuradas (`/evento lista`).
- Que al terminar la ventana de aceptación haya **menos de `min-players`** (por defecto 2);
  en ese caso se avisa `cancelled-few` y se cancela.
- Si el formato de fecha/hora es inválido, el plugin lo avisa por consola
  (`Programación inválida (schedule.*): ...`).

### 2.8 Aplicar cambios de horario

`config.yml` **no se sobreescribe** si ya existe: edita el tuyo a mano. Tras cambiarlo,
ejecuta `/evento recargar` (o reinicia el servidor). El planificador lee la config en vivo.

---

## 3. Flujo del evento (estados)

Estados internos: `IDLE → COUNTDOWN → INVITING → STARTING → RUNNING → ENDING → IDLE`.

1. **Aviso** (`schedule.reminders-minutes`): mensajes globales antes de la hora.
2. **Cuenta inicial** (`countdown-seconds`, por defecto 10 s): título grande + sonido a todos.
   → *COUNTDOWN*
3. **Invitación** (`accept-window-seconds`, por defecto 30 s): se abre un diálogo
   "¿Deseas asistir?" y un mensaje clicable; quien acepta con `/evento aceptar` entra.
   Cada jugador debe tener el permiso `evento.join`. Se respeta `max-players`
   (`0` = sin límite). Al unirse se guarda su inventario/posición (snapshot), se le da el
   kit y espera en el lobby **sin daño**. Barra de cuenta (amarilla). → *INVITING*
4. **Cuenta final** (`lobby-countdown-seconds`, por defecto 5 s): título y barra roja.
   Si no se llega a `min-players`, se cancela. → *STARTING*
5. **Combate** (`duration-minutes`, por defecto 20 min): todos pasan a la arena a la vez,
   repartidos lo más lejos posible entre sí. Marcador lateral y barra de tiempo; avisos
   globales de marcador cada `announce-interval-seconds`; **cambio de arena** cada
   `arena-rotation-minutes` (aviso 5 s antes); **hordas progresivas** de mobs. → *RUNNING*
6. **Final** (`ending-seconds`, por defecto 8 s): ranking, título del ganador (si hubo
   kills), fuegos artificiales y recompensas. Luego se devuelven inventarios y posiciones
   originales; los desconectados se restauran al reconectar. → *ENDING → IDLE*

---

## 4. Reglas durante el evento

### 4.1 Acceso y mundos
- Solo **participantes y `evento.admin`** pueden estar en el lobby o en las arenas. A
  cualquier otro se le bloquea el teletransporte y, si aparece dentro al conectarse, se le
  devuelve al mundo principal.
- El mundo del evento **no tiene mobs naturales**: solo las hordas del combate.

### 4.2 Combate
- **PvP solo entre participantes** (un participante no puede dañar a quien no participa, ni
  al revés). El daño de mobs (hordas) sí aplica.
- El daño a un participante se cancela **salvo** el vacío (VOID) y la muerte por comando
  (KILL), y cuando no están en `RUNNING`. → *el vacío siempre mata*.
- **Protección al reaparecer/cambiar de arena** (`spawn-protection-seconds`, por defecto 3 s):
  invulnerable; **se pierde al atacar** a alguien.
- **Muerte**: se conserva inventario y nivel, no hay drops ni mensaje de muerte, y se
  reaparece solo. Se registran las estadísticas.
- **Tótem**: si revives con tótem tras recibir daño dentro de `combat-tag-seconds`
  (por defecto 10 s), se le cuenta al último atacante.
- **Rachas**: se anuncian al alcanzar los valores de `streaks` (por defecto 3, 5, 7, 10).
  Cortar una racha de 3+ se anuncia también.

### 4.3 Hambre y regeneración
- **`hunger: vanilla`** (por defecto): el hambre baja como en el juego normal y la
  regeneración natural depende de la comida (el daño del PvP se nota).
- **`hunger: locked`**: la comida queda siempre llena (sin hambre).
- Al empezar y al reaparecer se llena la comida (y se limpian efectos/fuego).
- La **dificultad** de lobby y arenas se fuerza a `world-difficulty` (por defecto `NORMAL`)
  para que **ningún mapa importado se quede en Peaceful** (en Peaceful la vida se regenera
  sola y el hambre nunca baja).

### 4.4 Construcción e inventario
- **No se puede construir/romper/colocar** ni usar cubos (agua/lava) salvo que
  `allow-building: true`.
- Los participantes **no pueden soltar ni recoger ítems**, ni usar portales.

### 4.5 Explosiones y mobs
- Las **explosiones no destruyen terreno** en el mundo del evento (el daño a entidades sí).
- Los mobs (incluidas las hordas) **no sueltan drops ni XP** en el mundo del evento.
- Las hordas llevan **resistencia al fuego** (para no morir al sol), y desaparecen al
  cambiar de arena o al terminar el evento.

### 4.6 Comandos
- Durante el evento, a los participantes solo se les permiten los comandos de
  `allowed-commands` (por defecto `evento, msg, tell, w, r, reply`). Los `evento.admin`
  no tienen restricción. Aplica también a comandos escritos en carteles.

### 4.7 Salir del evento
- `/evento expulsar <jugador>` (admin) lo saca: **se le devuelve el inventario y se
  descartan sus estadísticas** del evento en curso.
- Si un jugador se desconecta y reconecta, se reintegra al evento (se le protege y se le
  devuelve al lobby/arena). Si el evento ya terminó mientras estaba fuera, se le restaura
  el inventario al volver.

---

## 5. Comandos

### 5.1 Públicos
| Comando | Qué hace |
|---|---|
| `/evento aceptar` | Acepta la invitación y participa (durante la ventana de aceptación) |
| `/evento top` | Top 10 del evento en curso (kills/muertes) |
| `/evento stats [jugador]` | Estadísticas históricas (eventos, victorias, kills, muertes, tótems, mejor racha) |
| `/evento ranking [kills\|wins\|totems\|streak\|events]` | Top 10 histórico (por defecto `kills`) |
| `/evento proximo` | Fecha/hora del próximo evento y cuánto falta |
| `/evento info` | Estado, nº de participantes, arena actual y tiempo restante |

### 5.2 Administrador (`evento.admin`)
| Comando | Qué hace |
|---|---|
| `/evento iniciar` | Arranca el evento ya |
| `/evento detener` | Cancela el evento en curso |
| `/evento spawn <arena> [set\|add\|clear]` | Fija/añade/borra spawns (debes estar en el mundo de esa arena) |
| `/evento lobby` | Fija el spawn del lobby (debes estar en el mundo del lobby) |
| `/evento borrar <arena>` | Borra los spawns de una arena |
| `/evento lista` | Lista arenas y nº de spawns |
| `/evento tparena <arena>` | Te teletransporta al mundo de esa arena |
| `/evento mundo` | Te lleva al lobby |
| `/evento expulsar <jugador>` | Saca a un participante y le devuelve su inventario |
| `/evento recargar` | Recarga `config.yml`/`messages.yml`/arenas/kit |
| `/evento instalar` | Recarga/copia los mundos del evento sobre la marcha |

---

## 6. Configuración de referencia (`config.yml`)

### 6.1 Mundos
| Clave | Defecto | Descripción |
|---|---|---|
| `worlds.lobby` | `evento_lobby` | Mundo del lobby |
| `worlds.arenas` | `[evento_arena_1..5]` | Mundos de las arenas (uno por arena) |
| `worlds.auto-spawns` | `true` | Crea spawns automáticos si una arena no tiene |
| `worlds.spawns-per-arena` | `8` | Cuántos spawns automáticos por arena |
| `worlds.spawn-radius` | `12` | Radio de esos spawns alrededor del spawn del mundo |
| `create-world-if-missing` | `true` | Crea el mundo vacío si falta y no hay plantilla |
| `world-difficulty` | `NORMAL` | Dificultad forzada en lobby y arenas |

El lobby y las arenas **vienen incluidos en el plugin** (se extraen e instalan solos si
faltan). Se pueden sobreescribir dejando la carpeta o `<nombre>.zip` en
`plugins/EventoPvP/world-templates/`.

### 6.2 Programación (`schedule`)
| Clave | Defecto | Descripción |
|---|---|---|
| `enabled` | `true` | Activa la programación automática |
| `timezone` | `America/Mexico_City` | Zona horaria (formato Java) |
| `first-date` | `2026-10-19` | Primer evento (`AAAA-MM-DD`), inicio del ciclo |
| `every-days` | `2` | Cada cuántos días cae (`2` = día por medio) |
| `times` | por día | Hora `"HH:mm"` de cada día de la semana |
| `time` | `"20:00"` | Hora única (se usa si no hay `times`) |
| `reminders-minutes` | `[30,10,5,1]` | Avisos globales previos |

### 6.3 Ritmo del evento
| Clave | Defecto | Descripción |
|---|---|---|
| `countdown-seconds` | `10` | Cuenta antes de mostrar la invitación |
| `accept-window-seconds` | `30` | Ventana para aceptar (esperan en el lobby, sin daño) |
| `lobby-countdown-seconds` | `5` | Cuenta final antes de pasar a la arena |
| `duration-minutes` | `20` | Duración del combate |
| `arena-rotation-minutes` | `4` | Cambio automático de arena |
| `ending-seconds` | `8` | Tiempo de resultados antes de devolver inventarios |
| `announce-interval-seconds` | `60` | Marcador global periódico |
| `min-players` | `2` | Mínimo de participantes para arrancar |
| `max-players` | `0` | Máximo de participantes (`0` = sin límite) |
| `spawn-protection-seconds` | `3` | Invulnerabilidad al reaparecer/cambiar de arena |
| `hunger` | `vanilla` | `vanilla` o `locked` |
| `combat-tag-seconds` | `10` | Ventana para atribuir un tótem reventado |
| `streaks` | `[3,5,7,10]` | Rachas que se anuncian |
| `reset-kit-on-respawn` | `true` | Reaparecer = kit completo otra vez |
| `allow-building` | `false` | Permitir construir/romper durante el evento |

### 6.4 Hordas de mobs (`mobs`)
| Clave | Defecto | Descripción |
|---|---|---|
| `enabled` | `true` | Activa las hordas durante el combate |
| `first-wave-seconds` | `45` | Cuándo aparece la primera horda |
| `interval-seconds` | `60` | Cada cuánto aparece una horda nueva |
| `base-count` | `4` | Mobs de la primera horda |
| `count-per-wave` | `3` | Mobs extra por cada horda posterior |
| `max-count` | `16` | Máximo de mobs de horda vivos |
| `types` | `[ZOMBIE,SKELETON,SPIDER]` | Tipos de mob de las hordas |

### 6.5 Extras visuales y otros
| Clave | Defecto | Descripción |
|---|---|---|
| `scoreboard` | `true` | Marcador lateral con stats y top |
| `bossbar` | `true` | Barra con el tiempo restante |
| `sounds` | `true` | Sonidos del evento |
| `fireworks` | `true` | Fuegos artificiales sobre el ganador |
| `allowed-commands` | `[evento,msg,tell,w,r,reply]` | Comandos permitidos a participantes |
| `bedrock.scoreboard-transparent` | `true` | Resource pack de Geyser para el marcador transparente en Bedrock |

---

## 7. Kit

El kit es **igual para todos** y se da al entrar y al reaparecer. Formato por línea:
`MATERIAL[:cantidad] encantamiento=nivel ... [unbreakable]`. Encantamientos en minúsculas
con el nombre de Minecraft (`sharpness`, `protection`, `unbreaking`, `feather_falling`,
`power`, `infinity`…). Ranuras: `helmet`, `chestplate`, `leggings`, `boots`, `offhand` y la
lista `items` (se colocan en orden en el inventario). Los errores del kit se avisan en
consola al cargar.

---

## 8. Estadísticas y recompensas

- Al terminar cada evento se acumulan en `history.yml`: eventos jugados, victorias (1.ª
  posición con al menos 1 kill), kills, muertes, tótems reventados y mejor racha.
- Consulta con `/evento stats [jugador]` y `/evento ranking [stat]`.
- **Recompensas** (`rewards.first/second/third`): comandos de consola para el top 3, **solo
  si tuvo kills** y el nombre es válido. Usa `{player}`:
  ```yaml
  rewards:
    first:
      - "give {player} diamond 10"
      - "lp user {player} permission settemp evento.campeon true 14d"
    second: ["give {player} diamond 5"]
    third: ["give {player} diamond 2"]
  ```

---

## 9. Instalación rápida

1. Copia `EventoPvP-1.2.0.jar` a `plugins/` y arranca (se crean lobby y arenas solos).
2. Revisa `plugins/EventoPvP/config.yml`: sobre todo `schedule` (horario) y `kit`.
3. Ajusta spawns si quieres: entra a una arena y usa
   `/evento spawn <arena>` (fija) o `/evento spawn <arena> add` (añade).
4. Comprueba el horario con `/evento proximo`.
5. (Opcional, Bedrock) copia `EventoPvP-ScoreboardTransparente.zip` a la carpeta `packs`
   de Geyser y reinícialo.

---

## 10. Problemas frecuentes

- **No arranca a la hora**: revisa `schedule.timezone`, `enabled: true`, `first-date`
  válido y que las horas de `times` estén entre comillas (`"HH:mm"`). `/evento proximo`
  muestra lo que calcula el plugin.
- **En una arena no baja la vida ni el hambre**: ese mapa estaba en **Peaceful**; el plugin
  fuerza `world-difficulty` (por defecto `NORMAL`) al cargar. Comprueba en consola
  `Mundo 'X': dificultad ajustada a NORMAL`. También puedes usar `/difficulty normal` dentro
  del mundo.
- **En Bedrock el marcador tiene fondo negro**: se quita con el resource pack incluido
  (`bedrock.scoreboard-transparent`); ver la sección de Bedrock en `CONFIGURACION.md`.
- **Sin mapa / arena vacía**: deja tu mundo en `plugins/EventoPvP/world-templates/<arena>/`
  (o `<arena>.zip`) y usa `/evento instalar`.
