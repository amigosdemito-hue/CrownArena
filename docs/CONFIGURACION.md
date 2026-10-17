# Configuración

Archivos en `plugins/EventoPvP/`: `config.yml`, `messages.yml`, `arenas.yml` (lo gestiona el plugin), `history.yml` (estadísticas), `snapshots/` (inventarios guardados; **no borrar mientras haya un evento activo**), `world-templates/` (mundos base opcionales).

## config.yml
| Clave | Defecto | Descripción |
|---|---|---|
| `worlds.lobby` | `evento_lobby` | Mundo del lobby de espera |
| `worlds.arenas` | `[evento_arena_1..5]` | Mundos de las arenas (uno por arena) |
| `worlds.auto-spawns` | `true` | Crea spawns automáticos si una arena no tiene ninguno |
| `worlds.spawns-per-arena` | `8` | Cuántos spawns automáticos por arena |
| `worlds.spawn-radius` | `12` | Radio (bloques) de esos spawns alrededor del spawn del mundo |
| `create-world-if-missing` | `true` | Si falta el mundo y no hay plantilla, lo crea vacío |
| `world-difficulty` | `NORMAL` | Dificultad forzada en lobby y arenas (`PEACEFUL`/`EASY`/`NORMAL`/`HARD`) |
| `schedule.enabled` | `true` | Activa la programación automática |
| `schedule.timezone` | `America/Mexico_City` | Zona horaria (formato Java, ej. `Europe/Madrid`) |
| `schedule.first-date` | `2026-10-19` | Fecha del primer evento (AAAA-MM-DD). Marca el inicio del ciclo |
| `schedule.every-days` | `2` | Cada cuántos días se repite (`2` = día por medio, `1` = todos los días) |
| `schedule.times` | por día | Hora (`"HH:mm"`, local) para cada día de la semana (ver abajo) |
| `schedule.time` | `"20:00"` | Hora única para todos los días (solo si no usas `times`) |
| `schedule.reminders-minutes` | `[30,10,5,1]` | Avisos globales previos |
| `countdown-seconds` | `10` | Cuenta antes de mostrar el diálogo |
| `accept-window-seconds` | `30` | Tiempo para aceptar (esperan en el lobby, sin daño) |
| `lobby-countdown-seconds` | `5` | Cuenta final antes de pasar a la arena |
| `duration-minutes` | `20` | Duración del combate |
| `arena-rotation-minutes` | `4` | Cambio automático de arena (necesita 2+ arenas) |
| `ending-seconds` | `8` | Tiempo de resultados antes de devolver inventarios |
| `announce-interval-seconds` | `60` | Marcador global periódico |
| `min-players` | `2` | Mínimo para que el evento arranque |
| `max-players` | `0` | Máximo de participantes (0 = sin límite) |
| `spawn-protection-seconds` | `3` | Invulnerabilidad al reaparecer / cambiar de arena |
| `hunger` | `vanilla` | Hambre/regeneración: `vanilla` (normal) o `locked` (comida siempre llena) |
| `combat-tag-seconds` | `10` | Ventana para atribuir un tótem reventado |
| `streaks` | `[3,5,7,10]` | Rachas que se anuncian |
| `reset-kit-on-respawn` | `true` | Reaparecer entrega el kit completo |
| `allow-building` | `false` | Permite romper/colocar bloques |
| `mobs.enabled` | `true` | Hordas de mobs durante el combate |
| `mobs.first-wave-seconds` | `45` | Cuándo aparece la primera horda |
| `mobs.interval-seconds` | `60` | Cada cuánto aparece una horda nueva |
| `mobs.base-count` | `4` | Mobs de la primera horda |
| `mobs.count-per-wave` | `3` | Mobs extra por cada horda posterior |
| `mobs.max-count` | `16` | Máximo de mobs de horda vivos a la vez |
| `mobs.types` | `[ZOMBIE,SKELETON,SPIDER]` | Tipos de mob de las hordas (`EntityType`) |
| `scoreboard` / `bossbar` / `sounds` / `fireworks` | `true` | Extras visuales |
| `bedrock.scoreboard-transparent` | `true` | Instala en Geyser (si está en este servidor) el resource pack que deja transparente el fondo del marcador lateral en jugadores de Bedrock |
| `allowed-commands` | `[evento,msg,tell,w,r,reply]` | Comandos permitidos a participantes (sin `/`) |
| `rewards.first/second/third` | `[]` | Comandos de consola para el top 3 (solo si tuvo kills); usa `{player}` |

Ejemplo de recompensas:
```yaml
rewards:
  first:
    - "give {player} diamond 10"
    - "lp user {player} permission settemp evento.campeon true 14d"
  second: ["give {player} diamond 5"]
  third: ["give {player} diamond 2"]
```

### Programación del evento
El evento se lanza solo según `schedule`, o a mano con `/evento iniciar`. `schedule.every-days` es **cada cuántos días** cae el evento y `schedule.times` da la **hora de cada día de la semana** (así, según en qué día caiga, la hora cambia). `first-date` marca el inicio del ciclo. Todo es editable.

Ejemplo (por defecto): **día por medio** con estas horas:
```yaml
schedule:
  enabled: true
  timezone: America/Mexico_City
  first-date: "2026-10-19"     # un lunes
  every-days: 2                # 2 = día por medio (1 = todos los días)
  times:
    lunes: "20:21"
    martes: "16:30"
    miercoles: "20:21"
    jueves: "16:30"
    viernes: "20:21"
    sabado: "18:40"
    domingo: "20:21"
```
- Con `every-days: 2` y `first-date` en lunes, la rotación es **lunes, miércoles, viernes, domingo, martes, jueves, sábado** y vuelve a empezar; a cada día se le aplica su hora de `times`. Para que sea **todos los días**, pon `every-days: 1`.
- Los nombres de día se aceptan en español o inglés (`lunes`/`monday`, `miércoles`/`wednesday`, `sáb`/`sat`…), con o sin acento.
- Si no defines `times`, se usa una única `time` (p. ej. `"20:00"`) para todos los días.
- `/evento proximo` muestra la fecha/hora que calcula el plugin.


- Al arrancar, el plugin carga el lobby y las arenas. Cada arena es un **mundo propio** y su nombre de mundo es el nombre de la arena (por defecto `evento_arena_1` … `evento_arena_5`).
- El lobby y las arenas **vienen incluidos en el propio plugin**; al arrancar, si el mundo no existe, el plugin lo extrae e instala solo (no necesita Multiverse-Core).
- Para usar tus propios mapas, deja la carpeta (o `<nombre>.zip`) en `plugins/EventoPvP/world-templates/` con el nombre exacto: **sobreescribe** el incluido. Si no hay mapa y no está incluido, lo crea vacío (plano) mientras `create-world-if-missing` sea `true`.
- Si el mapa está en el nether (`DIM-1`) o el end (`DIM1`), el plugin lo carga con esa dimensión; las plantillas anidadas (`carpeta/carpeta/level.dat`) se corrigen solas.
- Los spawns de cada arena se generan automáticamente alrededor del spawn del mundo si la arena no tiene ninguno; establécete el spawn con `/evento spawn <arena>`, añade más con `/evento spawn <arena> add` o bórralos con `/evento spawn <arena> clear` (estando en ese mundo).
- Vuelve a aplicar todo con `/evento instalar` (tras subir mapas nuevos) o al reiniciar. Ver también `plugins/EventoPvP/world-templates/LEEME.txt`.
- `world-difficulty` fuerza la dificultad de lobby y arenas (por defecto `NORMAL`). Sirve para que un mapa importado **no se quede en Peaceful**: en Peaceful la vida se regenera sola y el hambre nunca baja, así que en esa arena parecería que "no hace daño".

### Bedrock (jugadores que entran por Geyser)
- En Bedrock el marcador lateral lleva un **cuadro negro** de fondo que en Java no existe; no se puede quitar desde el plugin porque lo dibuja el cliente. El plugin incluye un **resource pack** que lo deja transparente (`bedrock.scoreboard-transparent`, por defecto `true`).
- Si **Geyser corre en este mismo servidor**, el plugin escribe el pack en la carpeta `packs` de Geyser al arrancar; solo hay que **reiniciar Geyser** una vez.
- Si **Geyser está en un proxy** (Velocity/BungeeCord), copia a mano `EventoPvP-ScoreboardTransparente.zip` (está también dentro del jar, en `bedrock/`) a la carpeta `packs` del proxy y reinicia Geyser.
- Ponlo en `false` para no instalar nada (el pack ya instalado, si lo hubiera, no se borra).

### Kit
Formato por línea: `MATERIAL[:cantidad] encantamiento=nivel ... [unbreakable]`. Los encantamientos van en minúsculas con su nombre de Minecraft (`sharpness`, `protection`, `unbreaking`, `feather_falling`, `power`, `infinity`...). Ranuras: `helmet`, `chestplate`, `leggings`, `boots`, `offhand` y la lista `items` (se colocan en orden en el inventario). Los errores del kit se avisan en la consola al cargar.

Nota: con `hunger: vanilla` (por defecto) el hambre y la regeneración funcionan como en el juego normal: la comida baja al correr/saltar y la regeneración natural depende de ella, así que el daño del PvP se nota. Al empezar y al reaparecer se llena la comida con la saturación normal de vanilla. Si pones `hunger: locked` la comida nunca baja (siempre llenos) y la regeneración natural queda al máximo; en ese caso conviene no incluir comida en el kit.

### Hordas de mobs y mundo del evento
- Durante el combate aparecen **hordas progresivas** en la arena actual (cada una con más mobs que la anterior, hasta `mobs.max-count` vivos). Los mobs llevan resistencia al fuego para que no mueran al sol y no sueltan drops ni XP.
- El mundo del evento **nunca tiene mobs naturales**: cualquier spawn que no sea de una horda se cancela.
- Las **explosiones no destruyen terreno** dentro del mundo del evento (el daño a jugadores/mobs sí se aplica), así las arenas no se arruinan.
- Al cambiar de arena o terminar el evento, los mobs de la horda se retiran.

## messages.yml
Todos los textos usan [MiniMessage](https://docs.advntr.dev/minimessage/format.html) con placeholders entre `< >` (por ejemplo `<killer>`, `<victim>`, `<kills>`). Traen gradientes, negrita y separadores para verse claros. Si borras una clave, el plugin usa el valor por defecto del jar y avisa en consola. En textos literales evita `<` y `>` fuera de etiquetas MiniMessage (un `<algo>` desconocido se elimina del mensaje): para ejemplos usa mayúsculas, p. ej. `/evento spawn ARENA`.

El archivo empieza con `messages-version`. Si lo actualizas a mano **no cambies ese número**; cuando el plugin trae una versión superior, respalda el tuyo como `messages-vN.bak` y regenera `messages.yml` con los textos nuevos. Si has personalizado mucho y no quieres perderlos, sube el número de `messages-version` a la versión del plugin para que no lo recree.

## arenas.yml
Lo escriben los comandos `/evento spawn`, `/evento lobby` y `/evento borrar`. Guarda el lobby y los spawns **por arena** (cada arena es un mundo distinto, pero el formato de cada punto es `x,y,z,yaw,pitch`). Puedes editarlo a mano y aplicar con `/evento recargar`.
