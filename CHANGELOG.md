# Changelog

## 1.2.0
- **Lobby y arenas en mundos propios**: 1 mundo de lobby + 5 mundos de arena (uno por arena), configurables en `worlds.*`.
- **Mundos incluidos en el jar**: el lobby y las 5 arenas ya vienen embebidos en el plugin; al arrancar los extrae e instala solo (no requiere Multiverse-Core ni subir mapas). Se pueden sobreescribir desde `plugins/EventoPvP/world-templates/<nombre>/` (carpeta o `.zip`); si no hay plantilla, los crea vacíos si faltan.
- **Soporte de dimensiones**: si un mapa está construido en el nether (`DIM-1`) o en el end (`DIM1`), el plugin carga el mundo con esa dimensión; también corrige plantillas que vengan anidadas (`carpeta/carpeta/level.dat`).
- **Compatible con Paper 26.x**: los mundos ya migrados al formato nuevo (`<nivel>/dimensions/minecraft/<nombre>`) se detectan (buscando en todas las carpetas del servidor) y se cargan **por su clave** (`WorldCreator.ofKey`), sin volver a extraer el mapa ni recrear la carpeta antigua; ya no hay que borrar `world/dimensions/...` cada vez que cambias el jar. Evita el fallo `Failed to migrate legacy world` / `Refusing to overwrite existing migrated file`. La dimensión de cada mundo se recuerda en `arenas.yml`.
- **Spawns por arena**: `/evento spawn <arena>` ahora **fija** el spawn en tu posición (reemplaza los automáticos); `/evento spawn <arena> add` añade puntos y `/evento spawn <arena> clear` los borra. También hay spawns automáticos y lobby por defecto.
- Nuevo subcomando admin `/evento instalar` para recargar/copiar los mundos sobre la marcha.
- `/evento mundo` ahora lleva al lobby; `/evento tparena <arena>` teletransporta al mundo de esa arena.
- El acceso restringido, las hordas y las reglas de mob/explosión ahora cubren el lobby y todas las arenas.
- **Mensajes renovados**: `messages.yml` reescrito con más color (gradientes, negrita y separadores), títulos de inicio más grandes y textos más claros; nuevas claves `arena-usage` y `arena-no-spawn`. El archivo lleva `messages-version` y, si el plugin trae una versión superior, respalda el antiguo y lo regenera solo.
- **Hambre y regeneración**: nueva opción `hunger` en `config.yml`. Por defecto (`vanilla`) el hambre baja como en el juego normal y la regeneración natural depende de la comida, así que el daño del PvP se nota. Con `locked` la comida queda siempre llena (comportamiento anterior).
- **Dificultad forzada**: nueva opción `world-difficulty` (por defecto `NORMAL`) que se aplica a lobby y arenas al cargarlas. Arregla mapas importados que venían en **Peaceful** (en esa dificultad la vida se regenera sola y el hambre nunca baja, así que parecía que "no hacía daño" en esa arena).
- **Robustez al teletransportar**: si una arena no tiene puntos de spawn, se usa el spawn de su mundo (antes el jugador se quedaba en la arena anterior y parecía que iba a otra); las arenas con su mundo cargado ya no se descartan del giro. La consola avisa de arenas sin spawn o que no cargan.
- **Marcador transparente en Bedrock**: en Bedrock el marcador lateral lleva un cuadro negro de fondo que lo hacía ilegible. El plugin incluye un resource pack (`bedrock-resourcepack/`, empaquetado en el jar bajo `bedrock/`) que lo deja transparente; si Geyser corre en este servidor se instala solo en su carpeta `packs` al arrancar (nueva opción `bedrock.scoreboard-transparent`, por defecto `true`). Si Geyser está en un proxy, se copia el `.zip` a mano en la carpeta `packs` del proxy.
- **Horario fijo editable (por día de la semana)**: nuevo `schedule.times` (hora por día, `"HH:mm"`) combinado con `schedule.every-days` (2 = día por medio, 1 = todos los días). Permite, p. ej., día por medio con horas distintas según el día; `first-date` marca el inicio del ciclo. La config antigua (`time` única) sigue funcionando.

## 1.1.0
- Lobby de espera, cuenta final y despliegue simultáneo; reparto de spawns por distancia.
- Marcador lateral y barra de tiempo; rachas, primera sangre y corte de racha; sonidos.
- Programación quincenal con avisos previos y `/evento proximo`.
- Estadísticas históricas (`/evento stats`, `/evento ranking`).
- Mensajes editables (`messages.yml`, MiniMessage).
- Comandos de admin: `lobby`, `tparena`, `expulsar`, `info`.
- Revisión: snapshots huérfanos se restauran antes de guardar uno nuevo; diálogo tolerante a clientes sin soporte; el vacío siempre mata; kit sin comida (hambre bloqueada); Gradle wrapper incluido.
- Revisión 2: acceso restringido al mundo del evento (solo participantes y admins); hordas de mobs progresivas durante el combate (sin mobs naturales, sin drops/XP, con resistencia al fuego); las explosiones ya no destruyen el terreno; PvP solo entre participantes.
- Revisión 2 (correcciones): se conserva la ranura en mano al guardar/restaurar el inventario; los ítems se leen antes de vaciar el inventario; quien es expulsado pierde sus estadísticas del evento en curso; las recompensas validan el nombre del jugador.
- Revisión 2 (compilación): `paper-api` fijado a `26.2.build.133-stable`; pruebas unitarias de la programación y del formato de coordenadas (`gradlew test`).

## 1.0.0
- Primera versión: evento PvP con diálogo, mundo aparte, inventarios separados, kit igual, anuncios globales y rotación de arenas.
