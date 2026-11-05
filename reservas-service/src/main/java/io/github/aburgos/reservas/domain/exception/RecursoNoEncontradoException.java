package io.github.aburgos.reservas.domain.exception;

import java.util.UUID;

public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(UUID id) {
        super("No existe el recurso " + id);
    }
}
