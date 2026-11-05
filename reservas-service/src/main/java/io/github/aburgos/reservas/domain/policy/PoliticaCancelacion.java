package io.github.aburgos.reservas.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Regla de cancelación: la reserva solo puede cancelarse con una antelación mínima
 * respecto a su inicio. Cancelar justo con la antelación mínima exacta está permitido.
 */
public record PoliticaCancelacion(Duration antelacionMinima) {

    public static final PoliticaCancelacion POR_DEFECTO = new PoliticaCancelacion(Duration.ofHours(24));

    public PoliticaCancelacion {
        Objects.requireNonNull(antelacionMinima, "antelacionMinima es obligatoria");
        if (antelacionMinima.isNegative()) {
            throw new IllegalArgumentException("antelacionMinima no puede ser negativa: " + antelacionMinima);
        }
    }

    public boolean permiteCancelar(Instant inicioReserva, Instant ahora) {
        Objects.requireNonNull(inicioReserva, "inicioReserva es obligatorio");
        Objects.requireNonNull(ahora, "ahora es obligatorio");
        return !ahora.plus(antelacionMinima).isAfter(inicioReserva);
    }
}
