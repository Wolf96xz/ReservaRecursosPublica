package io.github.aburgos.reservas.infrastructure.web.dto;

import io.github.aburgos.reservas.domain.model.EstadoReserva;

import java.time.Instant;
import java.util.UUID;

public record ReservaResponse(UUID id, UUID recursoId, String usuarioId, Instant inicio, Instant fin,
                              EstadoReserva estado, Instant creadaEn, Instant canceladaEn) {
}
