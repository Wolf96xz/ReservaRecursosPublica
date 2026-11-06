# 0005. Una base de datos por servicio, sin claves foráneas entre servicios

**Estado:** aceptada

## Contexto

`reservas-service` y `notificaciones-service` guardan datos propios y se comunican por eventos de Kafka
(ADR 0007). Compartir un esquema o consultarse las tablas entre servicios los acoplaría por datos: un cambio
de columna en uno rompería al otro, y no podrían desplegarse ni evolucionar por separado. Tampoco hay tabla
de usuarios: la identidad viene del claim `sub` del JWT (ADR 0003).

## Decisión

- **Una base de datos por servicio** (`reservas`, `notificaciones`; Keycloak usa la suya), en la misma
  instancia de PostgreSQL. Cada servicio tiene sus propias migraciones Flyway y es el único que accede a su
  base.
- **Sin claves foráneas ni joins entre servicios.** Las referencias entre ellos son lógicas:
  `notificacion.reserva_id` es un `uuid` sin FK, y `usuario_id` guarda el `sub` como `varchar` opaco.
  `outbox_event.aggregate_id` tampoco tiene FK a `reserva`, a propósito: el evento debe poder sobrevivir a
  la fila.
- **Los datos viajan en eventos autocontenidos**, no se consultan al otro servicio (ver ADR 0007).
- **Una instancia, no un servidor por servicio.** Es una simplificación operativa para desarrollo y
  `docker-compose`; el aislamiento es lógico (bases y credenciales distintas), no de recursos.

## Consecuencias

- Cada servicio puede cambiar su esquema, migrarlo y desplegarse sin coordinarse con el otro.
- No hay integridad referencial entre servicios: un `reserva_id` puede apuntar a una reserva que ya no existe.
  Es aceptable porque la notificación es un registro de lo ocurrido y no depende de la fila original.
- Las consultas que cruzan servicios (p. ej. "notificaciones de mis reservas") no se pueden resolver con un
  join; habría que añadir una API o una proyección propia.
- Hay que crear las dos bases al provisionar PostgreSQL (tarea de la fase 8, `docker-compose`).
- Configuración: ambos servicios leen `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD`, con valores
  por defecto de desarrollo local y base `reservas` o `notificaciones` según el servicio. En los tests, Testcontainers
  (`@ServiceConnection`) sustituye esos valores.
- Compartir instancia significa que una caída o saturación de PostgreSQL afecta a ambos servicios. Pasar a
  instancias separadas no exigiría cambios de código, solo de configuración (`DB_HOST`, `DB_NAME`).

## Alternativas descartadas

- **Esquema compartido con tablas de ambos servicios:** más simple, pero acopla los servicios por datos y
  permite FKs y joins que luego impiden separarlos.
- **Un esquema por servicio en una sola base:** aislamiento más débil; las migraciones y los permisos son más
  fáciles de mezclar.
- **Consultar a `reservas-service` desde `notificaciones-service`** para obtener los datos de la reserva: añade
  una dependencia en tiempo de ejecución entre servicios.
