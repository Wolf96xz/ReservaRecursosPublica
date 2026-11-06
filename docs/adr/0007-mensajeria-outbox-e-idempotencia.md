# 0007. Mensajería: Outbox transaccional, consumidor idempotente y reintentos

**Estado:** aceptada

## Contexto

Al crear o cancelar una reserva hay que avisar por correo. Escribir en la base de datos y publicar en Kafka
son dos sistemas sin transacción común: si se publica y luego falla el commit, se notifica una reserva que
no existe; si se hace commit y luego falla la publicación, la reserva existe y nadie se entera. Además,
Kafka entrega **al menos una vez**, así que el consumidor verá duplicados.

## Decisión

### Productor (`reservas-service`)

- **Outbox transaccional.** `ReservaService` guarda la reserva y, en la misma transacción, el evento en
  `outbox_event` (puerto `EventPublisher`, implementado por `OutboxEventPublisher` con
  `Propagation.MANDATORY`: fuera de una transacción falla en vez de guardar un evento suelto).
- **`OutboxRelay`** lee los pendientes con `FOR UPDATE SKIP LOCKED` (varias instancias no se pisan), los
  publica **de forma síncrona y en orden** y marca `publicado_en`. Ante el primer fallo corta el lote y
  reintenta en la siguiente pasada, para no adelantar eventos posteriores de la misma reserva. Entrega al
  menos una vez: si cae entre el envío y el commit, el evento se reenvía.
- **El outbox no es una entidad JPA.** `payload` es `jsonb` y mapear un `String` a `jsonb` con Hibernate 7
  depende del `JsonFormatMapper` (con Jackson 3 en Boot 4). Se usa `JdbcClient` con SQL explícito
  (`CAST(:payload AS jsonb)`). `JpaTransactionManager` comparte la conexión JDBC, así que el `INSERT` entra
  en la misma transacción que la reserva (hay un test que lo demuestra con rollback). El driver de
  PostgreSQL no admite `Instant` como parámetro: se convierte a `OffsetDateTime` en UTC.
- **Un solo topic** (`reservas.eventos`, 3 particiones), **clave del mensaje = id de la reserva** (orden por
  reserva) y **tipo en la cabecera `eventType`**. El cuerpo es JSON.
- **Eventos autocontenidos** (`common`): llevan nombre y zona del recurso y el email, para que el consumidor
  no consulte a `reservas-service`. Los instantes van en UTC; la hora local se calcula con la zona del recurso.
- **Retención:** el relay purga cada día lo publicado con más de **7 días**, porque el `payload` puede
  contener el email (dato personal).

### Origen del email

El claim `email` del JWT viaja **dentro del evento**; no se guarda en `reserva` y no se consulta a Keycloak
(acoplaría los servicios). Es **opcional**: si el token no lo trae, la reserva se hace igual, el evento
viaja con `email` nulo y el consumidor lo da por procesado sin enviar nada. Bloquear una reserva porque a
un usuario le falta un dato de perfil sería peor que no avisarle.

### Consumidor (`notificaciones-service`)

- **Idempotencia por inserción, no por comprobación.** Antes de tratar un evento se ejecuta
  `INSERT INTO evento_procesado ... ON CONFLICT DO NOTHING` y se mira cuántas filas entraron. Es atómico:
  con "comprobar y luego insertar" dos consumidores concurrentes podrían pasar ambos. Es la misma razón
  por la que el anti-solapamiento vive en la base de datos (ADR 0001). Se usa `ON CONFLICT` y no `save()`
  porque en PostgreSQL una violación de la PK deja la transacción inservible.
- **Dos fases separadas.** (1) En una transacción corta se registra el evento y se crea la notificación
  `PENDIENTE`; o entra todo o nada. (2) Fuera de esa transacción se envía el correo y se anota el resultado
  (un servidor SMTP lento no debe retener una conexión a la base de datos).
- **Un fallo de envío no se propaga a Kafka.** El evento ya consta como procesado, así que reentregarlo no
  serviría. Se anota `FALLIDA` con el error y `NotificacionReintentos` reintenta cada minuto, hasta 5
  intentos, desde la tabla `notificacion`. Para poder rehacer el correo, la notificación guarda el evento
  original (`payload`, `text`): es una columna que no estaba en el diseño inicial del modelo.
- **DLT solo para lo que reintentar no arregla.** Un mensaje sin cabecera, de tipo desconocido o con JSON
  roto lanza `IllegalArgumentException` o `JacksonException`, marcadas como no reintentables: van directas
  a `reservas.eventos.DLT` sin esperar. Los errores transitorios (p. ej. la base de datos caída) se
  reintentan 3 veces con 1 s de espera y después van al DLT. Un mensaje venenoso no bloquea la partición.

## Consecuencias

- La entrega es **al menos una vez** de extremo a extremo; la idempotencia la pone el consumidor, no Kafka.
  No se usan transacciones de Kafka ni *exactly-once*: añadirían complejidad sin que haga falta.
- **Límite conocido:** `NotificacionReintentos` no usa bloqueo distribuido. Con varias instancias podría
  repetirse algún correo. Se acepta frente a perderlo; si molestara, se resolvería reclamando las filas con
  `FOR UPDATE SKIP LOCKED`, como el outbox. El margen de 30 s evita que el reintento pise un envío en curso.
- **El DLT no se vigila ni se reprocesa** todavía: queda para la fase de observabilidad (fase 8).
- El orden entre eventos de la misma reserva se conserva por clave de partición y por `creado_en`. En
  producción un evento posterior siempre es más reciente; en los tests el reloj es fijo y no se comprueba.
- **No probado contra Kafka y PostgreSQL reales en local:** los tests con Testcontainers necesitan Docker y
  solo se ejecutan en el CI. Los de lógica (servicio, relay, listener, plantilla) sí corren sin Docker.
- Esta decisión añade a `common` los contratos de los eventos: cualquier cambio de campos debe ser
  compatible hacia atrás mientras convivan productor y consumidor desplegados por separado.
