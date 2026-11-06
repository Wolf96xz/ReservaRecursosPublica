package io.github.aburgos.reservas.application.port;

import io.github.aburgos.reservas.event.ReservaEvent;

/**
 * Salida de eventos de dominio. Debe llamarse dentro de la transacción que modifica la reserva: el evento
 * se guarda en la misma transacción (patrón Outbox) y un proceso aparte lo publica en Kafka. Así nunca hay
 * una reserva sin evento ni un evento de una reserva que no llegó a guardarse.
 */
public interface EventPublisher {

    void publicar(ReservaEvent evento);
}
