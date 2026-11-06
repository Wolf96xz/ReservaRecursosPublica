# ReservaRecursosPublica

Plataforma de reservas de recursos públicos municipales (pistas deportivas, salas, instalaciones).
Proyecto de portfolio que busca demostrar algo más que dominio de Java: arquitectura por capas,
mensajería, seguridad, testing serio, CI/CD y observabilidad.

## Stack

Java 21 · Spring Boot 4.1 · PostgreSQL + Flyway · Apache Kafka · Keycloak (OAuth2/OIDC) ·
Angular · springdoc-openapi · JUnit 5, Mockito, AssertJ, Testcontainers, ArchUnit · GitHub Actions ·
Docker · Micrometer / Prometheus / Grafana.

## Módulos

| Módulo | Responsabilidad |
|---|---|
| `common` | Contratos compartidos: eventos y constantes. |
| `reservas-service` | Núcleo: recursos, reservas, reglas de negocio y API REST. |
| `notificaciones-service` | Consume eventos de Kafka y envía correos. |
| `api-gateway` | Entrada única: enrutado, validación JWT, CORS y filtros transversales (puerto 8080). |
| `frontend` | Aplicación Angular (no es módulo Maven). Pendiente. |

Cada servicio se organiza en tres capas (`domain`, `application`, `infrastructure`); la regla de
dependencias se comprueba con un test ArchUnit.

## Estado

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Dominio puro | Hecha |
| 2 | Persistencia (Flyway, JPA, restricción anti-solapamiento) | Hecha |
| 3 | Casos de uso y API REST | Hecha (los tests de persistencia e integración solo corren en el CI) |
| 4 | Seguridad: resource server JWT y roles de Keycloak | Hecha (probada con JWT simulados; sin Keycloak real todavía) |
| 5 | Mensajería: Outbox, Kafka, notificaciones (idempotencia, reintentos, DLT) | Hecha (los tests con Kafka real solo corren en el CI) |
| 6 | Gateway: enrutado, JWT, CORS, correlación y registro | Hecha (probada con backend falso y JWT simulados; sin Keycloak ni `reservas-service` reales) |
| 7 | Front-end Angular | Pendiente |
| 8 | Docker, docker-compose y observabilidad | Pendiente |

## Reglas de negocio destacadas

- Una franja horaria es un intervalo semiabierto `[inicio, fin)`: las franjas contiguas no se solapan.
- Las reservas se guardan en UTC; el horario de apertura se interpreta en la zona del recurso
  (`Europe/Madrid` por defecto), respetando los cambios de hora de marzo y octubre.
- Dos reservas confirmadas del mismo recurso no pueden solaparse; lo garantiza una restricción de
  exclusión de PostgreSQL, no solo la lógica de aplicación.
- Una reserva solo puede cancelarse con una antelación mínima (24 h por defecto).
- No se puede reservar en el pasado: la franja no puede empezar antes del instante actual (400).

## Cómo ejecutar los tests

Requisitos: JDK 21 y Docker (los tests de persistencia e integración usan PostgreSQL real con
Testcontainers; sin Docker se omiten).

```bash
./mvnw clean verify                       # compila y ejecuta todos los tests
./mvnw -pl reservas-service -am test      # un módulo y sus dependencias
```

## Decisiones de diseño

Se registran como ADR en [`docs/adr/`](docs/adr/).

## Licencia

[MIT](LICENSE)
