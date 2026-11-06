# 0008. API Gateway: Spring Cloud Gateway MVC, validación de JWT en dos niveles y CORS

**Estado:** aceptada

## Contexto

El front (fase 7) debe hablar siempre con una única entrada, no directamente con `reservas-service`. Esa entrada
debe enrutar, comprobar el token, resolver CORS y añadir lo transversal (correlación y registro de peticiones).
`reservas-service` ya es un resource server JWT (ADR 0006) y no debe quedar desprotegido si alguien lo alcanza
sin pasar por el gateway.

## Decisión

- **Spring Cloud Gateway Server MVC** (`spring-cloud-starter-gateway-server-webmvc`, release train
  `2025.1.2`, importado en el `pom` padre), no la variante WebFlux. Todo el proyecto es servlet; mezclar
  un modelo reactivo solo en el gateway añadiría otro modelo de programación y de tests sin un beneficio
  que este tráfico (peticiones cortas, una sola dependencia de backend) justifique. Las rutas se declaran con
  `RouterFunction` (`RoutesConfig`).
- **Enrutado por prefijo:** `/api/recursos/**` y `/api/reservas/**` van a `reservas-service`. Al enrutar por
  prefijo, los endpoints nuevos de ese servicio (disponibilidad, `PATCH` de recursos) no obligan a tocar el
  gateway.
- **El JWT se valida en dos niveles.** El gateway lo valida (firma, emisor y caducidad) y rechaza con 401 lo
  que no lo cumpla, sin llegar al backend. Después **reenvía el token tal cual** en `Authorization`, y
  `reservas-service` lo vuelve a validar y es quien aplica los roles (ADR 0006). Se descarta confiar solo en el
  gateway: el servicio seguiría protegido únicamente por la red, y los tests y el ADR 0006 asumen un backend
  que se protege por sí mismo. El gateway no reescribe ni inyecta identidad: el usuario sigue saliendo del
  claim `sub` que valida el backend (ADR 0003).
- **Rutas públicas del gateway:** solo `/actuator/health/**` e `/actuator/info`. El resto exige token.
  Las reglas de roles **no** se duplican en el gateway: viven en `reservas-service`.
- **CORS en el gateway**, no en los servicios. El origen permitido es configurable
  (`http://localhost:4200` por defecto en desarrollo) y no se usa `*` con credenciales. El navegador solo
  habla con el gateway, así que los servicios no necesitan CORS.
- **Filtros transversales:**
  - `CorrelationIdFilter`: si la petición no trae `X-Correlation-Id`, genera uno; lo reenvía al backend y lo
    devuelve en la respuesta.
  - `RequestLoggingFilter`: registra método, ruta, estado y duración, con el id de correlación. **No registra**
    cabeceras `Authorization` ni cuerpos (pueden llevar el email, dato personal).
- **Puertos:** gateway en `8080` y `reservas-service` en `8081` (`notificaciones-service` ya usa `8082`).
  El destino de `reservas-service` y el `issuer-uri` de Keycloak son configurables por variable de entorno
  (`KEYCLOAK_ISSUER_URI`, como en `reservas-service`).
- **Estructura:** el gateway no tiene dominio propio: `config/` y `filter/`, sin las tres capas. El test
  ArchUnit de regla de dependencias no se le aplica.

## Consecuencias

- Con la validación doble, el token se comprueba dos veces por petición. Es un coste pequeño a cambio de no
  depender de la red para proteger el backend.
- Ambos validan contra el mismo `issuer-uri`; un cambio de emisor hay que hacerlo en los dos.
- El enrutado por prefijo deja pasar al backend rutas que el gateway no conoce; si se quisiera una lista
  cerrada de rutas, habría que enumerarlas.
- **Sin verificar todavía:** la compatibilidad en ejecución de Spring Cloud Gateway 5.0.2 con Boot 4.1.1
  (hoy solo se ha comprobado que el starter resuelve) y el comportamiento contra un Keycloak real. Los tests del
  gateway usarán JWT simulados y un backend falso; lo demás se comprobará con `docker-compose` (fase 8).
- `reservas-service` escucha en el `8081` (configurable con `SERVER_PORT`), para no chocar con el gateway.
- El `X-Correlation-Id` llega al backend, pero los servicios aún no lo leen ni lo incluyen en sus logs; ese
  aprovechamiento completo llega con la observabilidad (fase 8).

## Alternativas descartadas

- **Gateway WebFlux:** más escalable en conexiones concurrentes, pero introduce el modelo reactivo en un
  proyecto servlet sin necesidad medida. Si hiciera falta, se revisaría con una medición de carga (fase 9).
- **Confiar solo en el gateway** (quitar la validación del backend): menos trabajo, pero deja a los servicios
  expuestos si se alcanzan sin pasar por él.
- **CORS en cada servicio:** repite configuración y obliga al navegador a conocer más de un origen.
