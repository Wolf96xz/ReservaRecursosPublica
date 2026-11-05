# 0002. Horario de apertura: comparación en instantes y persistencia

**Estado:** aceptada

## Contexto

Las reservas se guardan en UTC; el horario de apertura es hora local del recurso (`Europe/Madrid` por
defecto). Los cambios de hora de marzo (el día salta de 02:00 a 03:00) y octubre (la hora 02:xx se repite)
rompen las comparaciones basadas solo en `LocalTime`.

## Decisión

- `HorarioApertura.permite(franja, zona)` convierte la apertura y el cierre del día local a `Instant` y
  compara instantes. Una reserva debe caber entera en una franja de apertura del día local de su inicio.
- Ninguna franja cruza la medianoche (`apertura < cierre`), de modo que una reserva que la cruce nunca
  cabe. El cierre máximo es 23:59 porque `LocalTime` no representa las 24:00.
- Horario semanal fijo. Las franjas contiguas del mismo día se fusionan; las solapadas se rechazan.
- Persistencia: `FranjaApertura` es `@Embeddable`, pero `HorarioApertura` queda como value object puro sin
  anotaciones JPA. `Recurso` guarda las franjas como `@ElementCollection` (tabla `recurso_horario`) y
  reconstruye el horario en `getHorario()`.

## Consecuencias

- Los cambios de hora funcionan sin código especial y están cubiertos por tests.
- Se desvía del diseño inicial (`HorarioApertura` como `@Embeddable`): un record con una lista normalizada
  en su constructor es frágil con Hibernate.
- Quedan fuera las vigencias por temporada y los cierres a las 24:00.
