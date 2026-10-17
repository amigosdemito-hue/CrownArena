# Comandos y permisos

Comando base: `/evento`

## Para jugadores (permiso `evento.join`, activo por defecto)
| Comando | Qué hace |
|---|---|
| `/evento aceptar` | Acepta la invitación activa (alternativa al diálogo) |
| `/evento top` | Marcador del evento en curso (top 10) |
| `/evento stats [jugador]` | Estadísticas históricas (propias o de otro jugador) |
| `/evento ranking [kills\|wins\|totems\|streak\|events]` | Ranking histórico, top 10 |
| `/evento proximo` | Fecha del próximo evento y tiempo restante |
| `/evento info` | Estado actual, participantes, arena y tiempo |

## Para administradores (permiso `evento.admin`, solo OP por defecto)
| Comando | Qué hace |
|---|---|
| `/evento iniciar` | Lanza el evento ahora (cuenta de 10 s y diálogo) |
| `/evento detener` | Cancela el evento y devuelve todos los inventarios |
| `/evento spawn <arena>` | **Establece** el spawn de esa arena en tu posición (reemplaza los automáticos). Debes estar dentro del mundo de esa arena (el nombre del mundo = nombre de la arena) |
| `/evento spawn <arena> set` | Igual que el anterior: fija el spawn en tu posición (reemplaza) |
| `/evento spawn <arena> add` | **Añade** un punto de aparición más a esa arena (sin borrar los que haya) |
| `/evento spawn <arena> clear` | Borra todos los spawns de esa arena (equivale a `/evento borrar`) |
| `/evento lobby` | Fija tu posición como lobby de espera (debes estar en el mundo del lobby) |
| `/evento borrar <arena>` | Borra los spawns de una arena (el mundo no se borra). Igual que `/evento spawn <arena> clear` |
| `/evento lista` | Lista arenas y número de spawns |
| `/evento tparena <arena>` | Te teletransporta al primer spawn de la arena |
| `/evento mundo` | Te lleva al lobby del evento |
| `/evento expulsar <jugador>` | Saca a un participante y le devuelve su inventario |
| `/evento recargar` | Recarga `config.yml`, `messages.yml`, arenas y kit |
| `/evento instalar` | Vuelve a cargar/copiar el lobby y las arenas (útil tras subir mapas nuevos) |

## Permisos
| Permiso | Defecto | Descripción |
|---|---|---|
| `evento.join` | todos | Participar y recibir invitaciones |
| `evento.admin` | OP | Administrar el evento; los admins no tienen la restricción de comandos durante el evento |

Para impedir que un grupo participe, niega `evento.join` (por ejemplo con LuckPerms: `lp group <grupo> permission set evento.join false`).
