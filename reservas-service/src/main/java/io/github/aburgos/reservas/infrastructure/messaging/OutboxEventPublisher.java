package io.github.aburgos.reservas.infrastructure.messaging;

import io.github.aburgos.reservas.application.port.EventPublisher;
import io.github.aburgos.reservas.event.ReservaEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Implementa el puerto guardando el evento en la tabla outbox, no enviándolo a Kafka. Exige una transacción
 * ya abierta ({@code MANDATORY}): si alguien lo llamara fuera de una, fallaría en vez de guardar un evento
 * que no está atado a ningún cambio de estado.
 */
@Component
public class OutboxEventPublisher implements EventPublisher {

    static final String AGGREGATE_TYPE = "Reserva";

    private final OutboxRepository outbox;
    private final JsonMapper json;
    private final Clock clock;

    public OutboxEventPublisher(OutboxRepository outbox, JsonMapper json, Clock clock) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publicar(ReservaEvent evento) {
        outbox.insertar(new OutboxEvent(
                evento.eventId(),
                AGGREGATE_TYPE,
                evento.reservaId(),
                evento.tipo(),
                json.writeValueAsString(evento),
                clock.instant()));
    }
}
