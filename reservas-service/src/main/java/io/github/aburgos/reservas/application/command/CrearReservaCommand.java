package io.github.aburgos.reservas.application.command;

import java.time.Instant;
import java.util.UUID;

public record CrearReservaCommand(UUID recursoId, String usuarioId, Instant inicio, Instant fin) {
}
