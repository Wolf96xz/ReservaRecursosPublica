# 0003. Identidad del usuario y acceso a reservas ajenas

**Estado:** aceptada (la seguridad real se añadió en la fase 4: ver ADR 0006)

## Contexto

No existe tabla de usuarios: el identificador sale del claim `sub` del JWT de Keycloak.

## Decisión

- Los controladores obtienen el usuario del `Principal` (`principal.getName()`), nunca del cuerpo de la
  petición. Con JWT, el nombre del principal es el claim `sub`; la fase 4 solo añade la configuración
  de seguridad.
- Una reserva de otro usuario responde **404**, no 403, para no revelar que existe.
- Cancelar es `POST /api/reservas/{id}/cancelar` y devuelve la reserva con estado `CANCELADA`: es una
  transición de estado, no un borrado.

## Consecuencias

- Los roles existen desde la fase 4 (ADR 0006), pero gestor y administrador **todavía no** pueden ver ni
  cancelar reservas ajenas: queda pendiente (requiere `cancelada_por`).
- Los tests usan JWT simulados (`TestJwt`) en lugar de `user()` y `csrf()`.
