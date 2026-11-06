package io.github.aburgos.reservas.event;

import java.time.Instant;
import java.util.UUID;

public record ReservaCreada(
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
