package io.github.aburgos.reservas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de un evento ya tratado: su existencia es lo que hace idempotente al consumidor (Kafka entrega
 * al menos una vez). Se escribe con {@code INSERT ... ON CONFLICT DO NOTHING} (ver
 * {@code EventoProcesadoRepository}): insertar y mirar cuántas filas entraron es atómico, mientras que
 * "comprobar y luego insertar" deja pasar a dos consumidores concurrentes. Es la misma razón por la que el
 * anti-solapamiento vive en la base de datos (ADR 0001).
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {
        // requerido por JPA
    }

    public EventoProcesado(UUID eventId, Instant procesadoEn) {
        this.eventId = eventId;
        this.procesadoEn = procesadoEn;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }
}
