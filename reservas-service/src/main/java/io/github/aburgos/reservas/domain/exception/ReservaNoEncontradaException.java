package io.github.aburgos.reservas.domain.exception;

import java.util.UUID;

public class ReservaNoEncontradaException extends RuntimeException {

    public ReservaNoEncontradaException(UUID id) {
        super("No existe la reserva " + id);
    }
}
