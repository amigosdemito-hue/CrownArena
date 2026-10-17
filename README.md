# EventoPvP 1.2

Plugin de **evento PvP automático** (horario configurable) para servidores **Paper 26.2 (Java 25)**. A la hora programada abre una invitación a todos los jugadores; quienes aceptan pasan a un **lobby y arenas en mundos propios** con **inventario separado** (como entre servidores de una network), todos con el **mismo kit** y sin equipos, hasta que el evento termina. El servidor general ve anuncios globales de kills, tótems reventados, rachas y marcador, y las arenas rotan automáticamente.

## Características
- **Programación automática configurable** (por defecto **día por medio**, con la hora según el día de la semana; también admite diario, semanal u hora única) con avisos globales previos (30/10/5/1 min) y también inicio manual.
- **Cuenta de 10 s** con títulos y sonido, y después un **diálogo nativo** "¿Deseas asistir?" (más mensaje clicable y `/evento aceptar`).
- **Inventario separado**: al aceptar se guarda todo en disco (inventario, armadura, mano secundaria, XP, vida, hambre, efectos, modo de juego y posición) y se restaura al terminar. Si el jugador se desconecta o el servidor cae, se restaura al volver a entrar.
- **Lobby de espera** opcional, cuenta final de 5 s y despliegue de todos a la arena a la vez.
- **Mismo kit para todos**, configurable (`config.yml`), con reposición completa al reaparecer.
- **Muerte sin pérdida**: sin drops ni pérdida de XP, reaparición inmediata en el spawn más alejado de los rivales, invulnerabilidad corta (se pierde al atacar). Nadie sale del evento.
- **Anuncios globales**: kill con el arma usada, tótem reventado y a quién, primera sangre, rachas, quién corta una racha, y marcador periódico (más kills / más muertes).
- **Arenas rotativas en mundos propios**: un mundo por arena (5 por defecto) con varios puntos de aparición cada una; cambio automático cada X minutos con aviso.
- **Mundos incluidos en el propio plugin**: el lobby (`evento_lobby`) y las 5 arenas (`evento_arena_1` … `evento_arena_5`) ya vienen dentro del `.jar`; el plugin los instala solo al arrancar. Basta con subir el plugin (no necesitas Multiverse-Core ni subir mapas aparte). Si quieres tus propios mapas, deja sus carpetas (o `.zip`) en `plugins/EventoPvP/world-templates/` para sobreescribir los incluidos. Los spawns de cada arena se generan solos alrededor del spawn del mundo, los mapas de nether/end se cargan con su dimensión y las plantillas anidadas se corrigen solas.
- **Hordas de mobs**: durante el combate aparecen hordas progresivas en la arena; el resto del mundo no genera mobs y las explosiones no rompen el terreno.
- **Solo participantes en el mundo**: los jugadores que no participan (ni son admin) no pueden entrar al lobby ni a las arenas.
- **HUD para participantes**: marcador lateral (tiempo, arena, kills, muertes, racha, top 3) y barra con tiempo restante.
- **Final**: ranking, título del ganador, fuegos artificiales, recompensas por comandos de consola y devolución de inventarios.
- **Estadísticas históricas** (`history.yml`): `/evento stats` y `/evento ranking`.
- **Mensajes 100 % editables** en `messages.yml` (MiniMessage), con color, gradientes, títulos llamativos y una versión que se actualiza sola (respaldando la anterior).
- Reglas de evento: sin construir, sin tirar ni recoger objetos, sin portales, comandos limitados a una lista permitida.

## Requisitos
- Servidor **Paper 26.2** (o compatible con la API usada) y **JDK 25** para compilar.
- Internet en el primer `gradlew build` (descarga Gradle 9.7.1 y la API de Paper).

## Compilar
```bash
./gradlew build          # Linux/Mac
gradlew.bat build        # Windows
```
El jar queda en `build/libs/EventoPvP-1.2.0.jar`. Si falla la compilación, mira [docs/SOLUCION-DE-PROBLEMAS.md](docs/SOLUCION-DE-PROBLEMAS.md).

## Puesta en marcha (2 minutos)
1. Copia el jar a `plugins/` y arranca el servidor. El plugin **instala solo** el lobby (`evento_lobby`) y las 5 arenas (`evento_arena_1..5`) que ya vienen dentro del jar, cada una en su mundo. No hace falta Multiverse-Core ni subir mapas aparte. También crea `config.yml` y `messages.yml`.
2. **¿Quieres tus propios mapas?** Deja la carpeta de cada mundo (o un `.zip`) en `plugins/EventoPvP/world-templates/` con el nombre exacto para **sobreescribir** los incluidos, o sube los mundos ya al servidor. En el siguiente arranque (o con `/evento instalar`) el plugin los usa.
3. Los spawns de cada arena se generan automáticamente alrededor del spawn del mundo. Para usar los tuyos: ve al punto y ejecuta `/evento spawn <arena>` (fija tu posición, reemplaza los automáticos); añade más con `/evento spawn <arena> add`. Estando dentro del mundo de la arena. Revisa con `/evento lista`.
4. Opcional: colócate en tu sala de espera y ejecuta `/evento lobby`.
5. Edita `config.yml`: horario (`schedule.first-date`, `schedule.every-days` y `schedule.times`), kit, duración y recompensas. Aplica con `/evento recargar`.
6. Prueba con **2 cuentas**: `/evento iniciar`.

## Documentación
- [docs/GUIA-COMPLETA.md](docs/GUIA-COMPLETA.md): guía completa — activación automática, reglas y funciones paso a paso
- [docs/COMANDOS.md](docs/COMANDOS.md): comandos y permisos
- [docs/CONFIGURACION.md](docs/CONFIGURACION.md): todas las opciones de `config.yml`, kit y `messages.yml`
- [docs/FUNCIONAMIENTO.md](docs/FUNCIONAMIENTO.md): flujo del evento, reglas y archivos de datos
- [docs/SOLUCION-DE-PROBLEMAS.md](docs/SOLUCION-DE-PROBLEMAS.md): errores de compilación, Bedrock, conflictos
- [CHANGELOG.md](CHANGELOG.md)
