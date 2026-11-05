# 0001. Anti-solapamiento con restricción de exclusión en PostgreSQL

**Estado:** aceptada

## Contexto

Dos reservas confirmadas del mismo recurso no pueden compartir tiempo. `@Version` no sirve: protege
modificaciones concurrentes de una misma fila, y dos reservas distintas son filas distintas. Una
comprobación "consultar y luego insertar" tiene una condición de carrera: dos peticiones pueden pasarla
a la vez.

## Decisión

- Restricción `EXCLUDE USING gist (recurso_id WITH =, tstzrange(inicio, fin, '[)') WITH &&) WHERE (estado = 'CONFIRMADA')`
  (requiere la extensión `btree_gist`).
- Además, `ReservaService` consulta antes (`existeSolapada`) para devolver un error de negocio claro en el
  caso normal. Si la restricción salta (carrera), se traduce a `ReservaSolapadaException`.
- Rango semiabierto `[)`: franjas contiguas son válidas. Las reservas canceladas quedan fuera y liberan la franja.

## Alternativa descartada

`@Lock(PESSIMISTIC_WRITE)` sobre `Recurso`: serializa todas las reservas de un recurso y deja la garantía
en el código de aplicación, no en los datos.

## Consecuencias

- La garantía es de la base de datos, válida aunque otro proceso escriba en ella.
- Acopla el esquema a PostgreSQL.
- Los tests de persistencia y concurrencia necesitan PostgreSQL real (Testcontainers), no H2.
