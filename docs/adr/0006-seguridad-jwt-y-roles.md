# 0006. Seguridad: resource server JWT y roles de realm

**Estado:** aceptada

## Contexto

`reservas-service` debe autenticar con tokens de Keycloak (OAuth2/OIDC) y distinguir tres perfiles:
ciudadano, gestor y administrador. Hasta ahora regía la configuración por defecto de Spring Boot
(HTTP Basic, formulario y CSRF), y los tests la sorteaban con `user()` y `csrf()`.

## Decisión

- **Resource server JWT** sin sesión y con CSRF desactivado. La autenticación viaja en
  `Authorization: Bearer`, no en una cookie, así que no hay petición cross-site que falsificar.
- **Roles de realm** `CIUDADANO`, `GESTOR` y `ADMIN`, leídos de `realm_access.roles` (Keycloak no los
  pone en `scope`, que es lo único que Spring lee por defecto) y convertidos a `ROLE_<MAYÚSCULAS>` por
  `KeycloakRealmRoleConverter`. Un token sin ese claim o con formato inesperado no concede ningún rol.
  Los roles de cliente (`resource_access`) se ignoran a propósito.
- **Reglas en un único sitio** (`SecurityConfig`), no repartidas en `@PreAuthorize`:
  - `POST /api/recursos`: `GESTOR` o `ADMIN` (403 en otro caso).
  - Resto de `/api/**`: cualquier usuario autenticado. Sin token o con token inválido, 401.
  - `/actuator/health/**` e `/info` son públicos; el resto de rutas requiere autenticación.
- **`issuer-uri` configurable** (`KEYCLOAK_ISSUER_URI`). Con `issuer-uri` el decodificador es perezoso:
  la aplicación arranca sin Keycloak y solo contacta con él al validar el primer token.
- **Tests con JWT simulados** (`TestJwt`) con la forma de Keycloak (`sub` y `realm_access.roles`) y el
  conversor real de producción, de modo que también se prueba el mapeo de roles. Los `@WebMvcTest`
  importan `SecurityConfig` y simulan el `JwtDecoder`.

## Consecuencias

- Aún **no se ha probado contra un Keycloak real**: firma, emisor y caducidad los valida el
  `JwtDecoder` de Spring, que los tests no ejercitan. Se hará con `docker-compose` (fase 8) o con
  `testcontainers-keycloak`.
- El usuario sigue saliendo del claim `sub` (ADR 0003); `ADMIN` y `GESTOR` **no** pueden aún ver ni
  cancelar reservas ajenas. Requiere `cancelada_por` en `reserva` y se decidirá en su propio ADR.
- Cualquier rol nuevo se añade en Keycloak y en `SecurityConfig`; no hay que tocar el conversor.
- El front (fase 7) y el gateway (fase 6) deberán enviar el mismo `Bearer`; los guards del front son solo
  UX, la seguridad real está aquí.
