# ReservaRecursosPublica

Plataforma de reservas de recursos públicos municipales (pistas deportivas, salas, instalaciones). Proyecto de portfolio para GitHub y CV, orientado a demostrar más que dominio de Java: arquitectura por capas, mensajería, seguridad, testing serio, CI/CD y observabilidad.

Autor: Toni, desarrollador backend Java (Spring Boot/MVC, Spring Security, Oracle, Maven, Git, Linux Debian) con experiencia en sector público. Objetivo del proyecto: reforzar el perfil para puestos backend Java remotos y avanzar hacia DevOps.

## Stack y versiones

| Elemento | Valor |
|---|---|
| Java | 21 (instalado en el IDE; no subir a 25 salvo decisión explícita registrada en un ADR) |
| Spring Boot | 4.1.x (parent `spring-boot-starter-parent`) |
| Spring Cloud BOM | 2025.1.2 (compatible con Boot 4.1.x; si falla la comprobación de compatibilidad, ajustar aquí) |
| Build | Apache Maven, multi-módulo |
| Base de datos | PostgreSQL (migraciones con Flyway) |
| Mensajería | Apache Kafka |
| Seguridad | Keycloak (OAuth2/OIDC, JWT, roles) |
| Documentación API | springdoc-openapi 3.x (rama para Boot 4) |
| Tests | JUnit 5, Mockito, AssertJ, Testcontainers 2.x, ArchUnit, JaCoCo |
| CI/CD | GitHub Actions, imágenes Docker multietapa (`eclipse-temurin:21-jre`) |
| Observabilidad | Actuator, Micrometer, Prometheus, Grafana |

IDE: IntelliJ IDEA Ultimate. Se trabaja desde la terminal integrada con Claude Code.

## Módulos Maven

Proyecto padre `reserva-recursos-publica` (packaging `pom`, sin código, sin `src`):

| Módulo | Responsabilidad |
|---|---|
| `common` | Solo contratos compartidos: eventos y constantes. Sin lógica de negocio, sin plugin `spring-boot-maven-plugin`. |
| `reservas-service` | Núcleo: recursos, reservas, reglas de negocio, API REST, outbox. |
| `notificaciones-service` | Consume eventos de Kafka y envía correos; idempotente. |
| `api-gateway` | Entrada única: enrutado, validación JWT, filtros transversales. |

Paquete base: `io.github.<usuario>.reservas.<servicio>`.

## Arquitectura interna de cada servicio

Tres capas: `domain`, `application`, `infrastructure`.
Regla de dependencias: `domain` no importa Spring web, Kafka ni infraestructura (JPA anotado en el dominio está permitido por pragmatismo). Esta regla se verifica con un test ArchUnit en cada servicio.

### common

```
event/   ReservaEvent (sealed interface), ReservaCreada, ReservaCancelada (records), Topics
web/     ErrorCodes (opcional)
```

### reservas-service

```
domain/model/       Recurso, Reserva (aggregate root, @Version), FranjaHoraria (record),
                    HorarioApertura (@Embeddable), FranjaApertura (record @Embeddable),
                    EstadoReserva (CONFIRMADA, CANCELADA), TipoRecurso (PISTA, SALA, INSTALACION)
domain/policy/      PoliticaCancelacion (antelación mínima, p. ej. 24 h)
domain/exception/   ReservaSolapadaException, RecursoNoDisponibleException,
                    CancelacionNoPermitidaException, FueraDeHorarioException
application/        ReservaService, RecursoService, command/ (CrearReservaCommand, CancelarReservaCommand),
                    port/EventPublisher
infrastructure/web/         ReservaController, RecursoController, dto/ (records), mapper/ReservaMapper,
                            GlobalExceptionHandler (@RestControllerAdvice → ProblemDetail)
infrastructure/persistence/ ReservaRepository, RecursoRepository
infrastructure/messaging/   OutboxEvent, OutboxRepository, OutboxEventPublisher, OutboxRelay (@Scheduled)
infrastructure/config/      SecurityConfig, KafkaConfig, OpenApiConfig, ClockConfig
resources/db/migration/     V1__esquema.sql, V2__outbox.sql
```

### notificaciones-service

```
domain/             Notificacion, EstadoNotificacion (PENDIENTE, ENVIADA, FALLIDA), EventoProcesado (eventId)
application/        NotificacionService, port/NotificationSender
infrastructure/messaging/   ReservaEventListener (@KafkaListener), KafkaConsumerConfig (reintentos + DLT)
infrastructure/mail/        MailNotificationSender, PlantillaCorreo
infrastructure/persistence/ NotificacionRepository, EventoProcesadoRepository
```

### api-gateway

```
config/   RoutesConfig (RouterFunction), SecurityConfig, CorsConfig
filter/   CorrelationIdFilter (X-Correlation-Id), RequestLoggingFilter
```

## Reglas de dominio clave

- `FranjaHoraria(inicio, fin)`: `inicio < fin`; método `solapaCon(otra)`. Franjas contiguas NO se solapan.
- `Reserva` guarda `Instant` (UTC). El horario de apertura se interpreta en la zona del recurso (`Europe/Madrid` por defecto).
- `HorarioApertura.permite(franja, zona)`: la reserva no cruza medianoche y cae dentro de una `FranjaApertura` del día correspondiente.
- `Recurso.validarReserva(franja)`: comprueba que está activo y que el horario lo permite.
- `Reserva.cancelar(Clock, PoliticaCancelacion)`: la lógica vive en la entidad (dominio no anémico). Siempre con `Clock` inyectado para tests deterministas.
- Anti-solapamiento entre reservas distintas del mismo recurso: `@Version` NO basta (son filas distintas). Estrategia pendiente de decidir (ver "Decisiones pendientes").
- No existe tabla de usuarios: el `usuarioId` sale del claim `sub` del JWT.
- Escritura de eventos: patrón Outbox. `ReservaService` guarda reserva y `OutboxEvent` en la misma transacción; `OutboxRelay` publica a Kafka con `FOR UPDATE SKIP LOCKED`.
- Notificaciones: antes de procesar un evento se comprueba `EventoProcesado` (Kafka entrega al menos una vez).

## Convenciones de código

- DTOs, comandos y eventos como `record`; estados como `enum` o `sealed interface`.
- Inyección por constructor; nada de `@Autowired` en campos.
- Errores HTTP con `ProblemDetail` (RFC 9457).
- Mapeo manual entre DTO y dominio (sin MapStruct, salvo decisión posterior).
- Sin Lombok en código nuevo (se usan records).
- Commits convencionales: `feat(domain): ...`, `fix(...)`, `test(...)`, `chore(...)`, `docs(...)`.

## Dependencias (nombres Spring Boot 4)

Los starters cambiaron respecto a Boot 3.x:

- `spring-boot-starter-webmvc` (antes `-web`) y `spring-boot-starter-webmvc-test` (aparte).
- `spring-boot-starter-flyway`: sin él, Flyway no se ejecuta y no avisa. Más `org.flywaydb:flyway-database-postgresql`.
- `spring-boot-starter-data-jpa`, `-validation`, `-security`, `-actuator`, `-kafka`, `-mail`.
- `spring-boot-starter-security-oauth2-resource-server` (nombre según convención de Boot 4; verificar).
- `spring-cloud-starter-gateway-server-webmvc` (verificar con `mvn dependency:tree` si no resuelve).
- Tests: `spring-boot-starter-test`, `-webmvc-test`, `-security-test`, `-kafka-test`, `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`, `testcontainers-kafka`, `testcontainers-keycloak` (dasniko), `archunit-junit5`.

Cambios que rompen respecto a Boot 3: Jackson 3 (imports `tools.jackson`, `JsonMapper` inmutable), `@MockBean` → `@MockitoBean`, anotaciones de slice test en paquetes nuevos (p. ej. `org.springframework.boot.webmvc.test.autoconfigure`), Testcontainers 1.x → 2.x, Hibernate 7.1.

## Estructura de tests

```
domain/            tests unitarios puros (sin Spring): FranjaHorariaTest, HorarioAperturaTest, ReservaTest
application/       servicios con Mockito
infrastructure/
  web/             @WebMvcTest + @MockitoBean
  persistence/     @DataJpaTest + Testcontainers (PostgreSQL real, no H2)
  messaging/       Testcontainers Kafka
integration/       @SpringBootTest end-to-end, incl. test de concurrencia (varios hilos, misma franja, solo uno gana)
architecture/      ArchitectureTest (ArchUnit)
```

Casos que deben tener test sí o sí: franjas contiguas/contenidas/idénticas, cambio de hora de marzo y octubre, bordes de apertura y cierre, día sin horario configurado, cancelación dentro y fuera del plazo, concurrencia de reservas.

## Hoja de ruta (por fases, cada una con tests en verde antes de avanzar)

1. **Dominio puro** (`reservas-service`): `FranjaHoraria` → enums → `FranjaApertura`/`HorarioApertura` → `Recurso` → `PoliticaCancelacion`/`Reserva` → excepciones.
2. **Persistencia**: migración Flyway V1, repositorios, consulta de solapamiento, `@DataJpaTest` con Testcontainers.
3. **Casos de uso y API**: comandos, `ReservaService`, DTOs, controller, `GlobalExceptionHandler`, tests web e integración, test de concurrencia. **Primer hito subible a GitHub.** CI con GitHub Actions desde aquí.
4. **Seguridad**: `SecurityConfig` con Keycloak (roles ciudadano / gestor / admin).
5. **Mensajería**: Outbox, Kafka, `notificaciones-service` (idempotencia + DLT).
6. **Gateway**: `api-gateway`.
7. **Operación**: Docker multietapa, `docker-compose.yml` (PostgreSQL, Kafka, Keycloak, Prometheus, Grafana), observabilidad, README con diagrama, ADRs en `docs/adr/`.
8. **Opcional**: Kubernetes local (kind/k3s), Helm, hilos virtuales (`spring.threads.virtual.enabled=true`) con medición de carga (k6/Gatling) documentada.

## Decisiones pendientes (valor por defecto entre paréntesis)

- Anti-solapamiento: restricción de exclusión de PostgreSQL (`EXCLUDE USING gist` sobre `tstzrange`) frente a `@Lock(PESSIMISTIC_WRITE)` sobre `Recurso` (por defecto: exclusión en BD + comprobación en dominio).
- Gateway MVC frente a WebFlux (por defecto: MVC).
- JPA anotado en `domain` frente a modelo de dominio separado (por defecto: anotado).
- Horario semanal fijo frente a vigencias por temporada (por defecto: semanal fijo).
- Reservas que cruzan medianoche (por defecto: prohibidas).
- Spring Boot 4.1.x frente a 3.5.x (por defecto: 4.1.x; bajar a 3.5.x solo si hay fricción seria de dependencias).

Cada decisión relevante se registra como ADR en `docs/adr/`.

## Comandos habituales

```bash
mvn clean verify                          # compila y ejecuta todos los tests
mvn -pl reservas-service -am test         # tests de un módulo y sus dependencias
mvn -pl reservas-service spring-boot:run  # arrancar un servicio
mvn dependency:tree -pl reservas-service  # comprobar resolución de dependencias
docker compose up -d                      # infraestructura local (cuando exista)
```

## Instrucciones para Claude Code

- Responder en español; el código, los identificadores y los mensajes de commit pueden seguir las convenciones indicadas arriba.
- Cambios mínimos y dirigidos: no refactorizar fuera del alcance de la tarea ni reorganizar paquetes sin pedirlo.
- No introducir frameworks, librerías ni infraestructura nuevos si lo existente basta; si hace falta algo nuevo, proponerlo y justificarlo antes.
- Respetar la regla de dependencias: `domain` no depende de `infrastructure`. Si un cambio la rompe, avisar.
- Escribir o actualizar los tests junto con cada cambio de código; no dar una tarea por terminada sin ejecutar `mvn verify` del módulo afectado.
- Usar `Clock` inyectado y nunca `Instant.now()`/`LocalDateTime.now()` directamente en el dominio.
- Antes de añadir una dependencia, comprobar el nombre del starter para Spring Boot 4 y que resuelve (`mvn dependency:tree`).
- No tocar el `pom` padre (versiones de Boot, Cloud, springdoc) sin avisar.
- Explicar brevemente el porqué de las decisiones de diseño relevantes, de forma que puedan documentarse en un ADR.
