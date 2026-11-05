package io.github.aburgos.reservas.domain.exception;

public class FueraDeHorarioException extends RuntimeException {

    public FueraDeHorarioException(String message) {
        super(message);
    }
}
