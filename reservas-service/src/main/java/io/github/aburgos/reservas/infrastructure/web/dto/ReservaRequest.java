package io.github.aburgos.reservas.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** Instantes en ISO-8601 con zona o 'Z' (p. ej. 2026-01-12T10:00:00Z). */
public record ReservaRequest(@NotNull UUID recursoId, @NotNull Instant inicio, @NotNull Instant fin) {
}
