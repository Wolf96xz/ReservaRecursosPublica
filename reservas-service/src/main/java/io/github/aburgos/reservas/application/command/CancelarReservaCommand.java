package io.github.aburgos.reservas.application.command;

import java.util.UUID;

/** {@code email}: claim {@code email} del JWT de quien cancela; opcional (ver {@link CrearReservaCommand}). */
public record CancelarReservaCommand(UUID reservaId, String usuarioId, String email) {

    public CancelarReservaCommand(UUID reservaId, String usuarioId) {
        this(reservaId, usuarioId, null);
    }
}
