package io.github.aburgos.reservas.domain.exception;

public class CancelacionNoPermitidaException extends RuntimeException {

    public CancelacionNoPermitidaException(String message) {
        super(message);
    }
}
