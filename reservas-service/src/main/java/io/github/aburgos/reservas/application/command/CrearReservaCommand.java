package io.github.aburgos.reservas.application.command;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code email} es el claim {@code email} del JWT del usuario; viaja en el evento para las notificaciones
 * y no se guarda en la reserva. Es opcional: un token sin ese claim no impide reservar.
 */
public record CrearReservaCommand(UUID recursoId, String usuarioId, Instant inicio, Instant fin, String email) {

    public CrearReservaCommand(UUID recursoId, String usuarioId, Instant inicio, Instant fin) {
        this(recursoId, usuarioId, inicio, fin, null);
    }
}
