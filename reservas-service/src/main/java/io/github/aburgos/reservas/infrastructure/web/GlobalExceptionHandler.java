package io.github.aburgos.reservas.infrastructure.web;

import io.github.aburgos.reservas.domain.exception.CancelacionNoPermitidaException;
import io.github.aburgos.reservas.domain.exception.FueraDeHorarioException;
import io.github.aburgos.reservas.domain.exception.RecursoNoDisponibleException;
import io.github.aburgos.reservas.domain.exception.RecursoNoEncontradoException;
import io.github.aburgos.reservas.domain.exception.ReservaNoEncontradaException;
import io.github.aburgos.reservas.domain.exception.ReservaSolapadaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.DateTimeException;

/**
 * Errores en formato ProblemDetail (RFC 9457). Hereda de {@link ResponseEntityExceptionHandler}
 * para que también los errores propios de Spring MVC (validación, JSON malformado...) usen ese formato.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({RecursoNoEncontradoException.class, ReservaNoEncontradaException.class})
    ProblemDetail noEncontrado(RuntimeException e) {
        return problema(HttpStatus.NOT_FOUND, "No encontrado", e);
    }

    @ExceptionHandler(ReservaSolapadaException.class)
    ProblemDetail solapada(ReservaSolapadaException e) {
        return problema(HttpStatus.CONFLICT, "Reserva solapada", e);
    }

    @ExceptionHandler(RecursoNoDisponibleException.class)
    ProblemDetail noDisponible(RecursoNoDisponibleException e) {
        return problema(HttpStatus.CONFLICT, "Recurso no disponible", e);
    }

    @ExceptionHandler(CancelacionNoPermitidaException.class)
    ProblemDetail cancelacionNoPermitida(CancelacionNoPermitidaException e) {
        return problema(HttpStatus.CONFLICT, "Cancelación no permitida", e);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail modificacionConcurrente(ObjectOptimisticLockingFailureException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "La reserva fue modificada por otra petición; vuelve a intentarlo");
        pd.setTitle("Modificación concurrente");
        return pd;
    }

    @ExceptionHandler(FueraDeHorarioException.class)
    ProblemDetail fueraDeHorario(FueraDeHorarioException e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Fuera de horario", e);
    }

    /** Valores que el dominio rechaza al construirse (franja invertida, zona inexistente...). */
    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
    ProblemDetail datosInvalidos(RuntimeException e) {
        return problema(HttpStatus.BAD_REQUEST, "Datos no válidos", e);
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, RuntimeException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle(titulo);
        return pd;
    }
}
