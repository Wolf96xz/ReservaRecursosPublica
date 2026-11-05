package io.github.aburgos.reservas.application.command;

import java.util.UUID;

public record CancelarReservaCommand(UUID reservaId, String usuarioId) {
}
