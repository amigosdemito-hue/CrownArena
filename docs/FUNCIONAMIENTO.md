# Funcionamiento

## Flujo del evento
1. **Aviso**: mensajes globales a los 30, 10, 5 y 1 minutos de la hora programada (`schedule`). El evento se programa por `every-days` (p. ej. día por medio) con una hora por día de la semana en `schedule.times`; también se puede lanzar a mano con `/evento iniciar`.
2. **Cuenta de 10 s**: título grande con sonido para todos los jugadores.
3. **Invitación**: diálogo "¿Deseas asistir?" + mensaje clicable. Ventana de `accept-window-seconds`.
4. **Lobby**: quien acepta guarda su estado en disco, recibe el kit y espera en el lobby (o en un punto de la arena si no hay lobby), sin daño y con una barra de cuenta.
5. **Cuenta final** de `lobby-countdown-seconds` y despliegue: todos a la arena a la vez, repartidos lo más lejos posible entre sí. Si no llegan al mínimo de jugadores, el evento se cancela y se devuelve todo.
6. **Combate** (`duration-minutes`): marcador lateral y barra de tiempo; anuncios globales; cambio de arena cada `arena-rotation-minutes` con aviso de 5 s (cada arena es un **mundo distinto**, así que el cambio teletransporta de mundo); hordas de mobs progresivas en la arena actual.
7. **Final**: ranking, título del ganador (si hubo kills), fuegos artificiales y recompensas. Tras `ending-seconds` se devuelven los inventarios y posiciones originales.

## Reglas durante el evento
- **Acceso**: a los mundos del evento (el lobby y cada arena) solo entran los participantes y los administradores (`evento.admin`). A cualquier otro jugador se le bloquea el teletransporte y, si aparece dentro al conectarse, se le devuelve al mundo principal.
- Nadie puede salir: al morir se reaparece de inmediato en la arena actual, sin perder nada (drops y XP desactivados) y con el kit completo.
- Daño solo durante el combate; invulnerabilidad corta al reaparecer o cambiar de arena (se pierde al atacar). El vacío siempre mata. Solo hay PvP entre participantes.
- **Mobs**: únicamente aparecen hordas progresivas en la arena durante el combate; el resto del mundo no genera mobs naturales y las explosiones no destruyen el terreno.
- Hambre bloqueada; sin construir, sin tirar ni recoger objetos, sin portales; solo los comandos de `allowed-commands` (los admins quedan exentos). Los comandos ejecutados por menús/GUIs de otros plugins pueden escapar a esta lista.
- Un tótem reventado se atribuye al último atacante dentro de `combat-tag-seconds`.

## Seguridad de inventarios
- Antes de entrar se guarda en `snapshots/<uuid>.yml` (escritura segura: se escribe a un temporal y se mueve). Si el guardado falla, el jugador no entra.
- Se restaura al terminar el evento, al usar `/evento expulsar`, al desactivar el plugin, o **al volver a conectarse** si estaba desconectado o el servidor se cayó.
- Un snapshot nunca se sobrescribe: siempre conserva el inventario original.
- Si un participante se desconecta durante el evento, sigue contando como participante; al volver mientras el evento sigue, regresa a la arena.

## Mundos
- El evento usa un **mundo por arena** (5 por defecto: `evento_arena_1` … `evento_arena_5`) más un **mundo lobby** (`evento_lobby`), definidos en `config.yml` (`worlds.*`).
- El lobby y las arenas **vienen incluidos en el propio plugin** (embebidos en el `.jar`): al arrancar, si el mundo no existe, el plugin lo extrae e instala solo, sin depender de Multiverse ni de subir mapas aparte.
- Puedes **sobreescribir** un mapa dejando su carpeta (o `<nombre>.zip`) en `plugins/EventoPvP/world-templates/`; si no hay y no está incluido, lo **crea vacío** (plano) mientras `create-world-if-missing` sea `true`.
- Si un mapa está en el nether (`DIM-1`) o el end (`DIM1`), el mundo se carga con esa dimensión; las plantillas anidadas (`carpeta/carpeta/level.dat`) se corrigen solas.
- En Paper 26.x el servidor guarda los mundos ya migrados en `<nivel>/dimensions/minecraft/<nombre>` y borra la carpeta antigua. El plugin busca esa carpeta en todos los mundos del servidor; si la encuentra, **carga el mundo por su clave** (`WorldCreator.ofKey`) y no vuelve a extraer el mapa (si quedó una carpeta suelta la mueve a `<nombre>.legacy`). Así puedes cambiar el jar sin borrar `world/dimensions/...` a cada rato. La dimensión de cada mundo se recuerda en `arenas.yml` (`envs`).
- Los puntos de aparición de cada arena se generan automáticamente alrededor del spawn del mundo si la arena no tiene ninguno. Establécete el spawn con `/evento spawn <arena>` (fija tu posición, reemplaza los automáticos), añade más con `/evento spawn <arena> add` o bórralos con `/evento spawn <arena> clear`, estando en el mundo de esa arena.
- Si subes mapas nuevos, aplícalos con `/evento instalar` o reiniciando el servidor.

## Estadísticas
Al terminar cada evento se acumulan en `history.yml`: eventos jugados, victorias (1.er puesto con al menos 1 kill), kills, muertes, tótems reventados y mejor racha. Un jugador expulsado con `/evento expulsar` queda fuera: se descartan sus estadísticas del evento en curso (no cuenta ni opta a recompensas).

## Compatibilidad
- El marcador lateral reemplaza el scoreboard del jugador durante el evento y se restaura al terminar; puede chocar con plugins de scoreboard/tablist. Desactívalo con `scoreboard: false`.
- El diálogo es nativo de Java. Para Bedrock (Geyser) siempre se envía también el mensaje clicable y existe `/evento aceptar`.
