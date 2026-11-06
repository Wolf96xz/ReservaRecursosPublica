package io.github.aburgos.reservas.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato de los eventos de reserva que viajan por Kafka. Cada evento es autocontenido: lleva todo lo
 * que necesita el consumidor (nombre y zona del recurso, email del usuario) para no tener que consultar
 * a {@code reservas-service}. Los instantes van en UTC.
 *
 * <p>{@link #eventId()} identifica el evento y es la clave de idempotencia del consumidor (Kafka entrega
 * al menos una vez). {@link #reservaId()} es la clave del mensaje, para conservar el orden por reserva.
 * {@link #email()} puede ser {@code null} si el token del usuario no traía el claim {@code email}.
 */
public sealed interface ReservaEvent permits ReservaCreada, ReservaCancelada {

    UUID eventId();

    UUID reservaId();

    UUID recursoId();

    String recursoNombre();

    /** Identificador IANA de la zona del recurso (p. ej. {@code Europe/Madrid}). */
    String zona();

    String usuarioId();

    String email();

    Instant inicio();

    Instant fin();

    Instant ocurridoEn();

    /** Nombre del tipo de evento (columna {@code event_type} del outbox y cabecera del mensaje Kafka). */
    default String tipo() {
        return getClass().getSimpleName();
    }
}
