package io.github.aburgos.reservas.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila de {@code outbox_event}. No es una entidad JPA: el {@code payload} es {@code jsonb} y se maneja como
 * texto con SQL explícito (ver {@link OutboxRepository}), sin depender del mapeo JSON de Hibernate.
 */
public record OutboxEvent(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant creadoEn) {
}
