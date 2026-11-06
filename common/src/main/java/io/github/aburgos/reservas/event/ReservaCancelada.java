package io.github.aburgos.reservas.event;

import java.time.Instant;
import java.util.UUID;

/** {@code inicio} y {@code fin} son la franja de la reserva cancelada; {@code ocurridoEn}, la cancelación. */
public record ReservaCancelada(
        UUID eventId,
        UUID reservaId,
        UUID recursoId,
        String recursoNombre,
        String zona,
        String usuarioId,
        String email,
        Instant inicio,
        Instant fin,
        Instant ocurridoEn) implements ReservaEvent {
}
