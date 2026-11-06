package io.github.aburgos.reservas.infrastructure.messaging;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Acceso a {@code outbox_event} con SQL explícito. Participa en la transacción en curso: {@code insertar}
 * se ejecuta en la misma transacción que guarda la reserva (JpaTransactionManager comparte la conexión
 * JDBC con JdbcClient), que es lo que da la atomicidad del patrón Outbox.
 *
 * <p>El driver de PostgreSQL no acepta {@link Instant} como parámetro: se convierte a
 * {@link OffsetDateTime} en UTC.
 */
@Repository
public class OutboxRepository {

    private final JdbcClient jdbc;

    public OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(OutboxEvent evento) {
        jdbc.sql("""
                        INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, creado_en)
                        VALUES (:id, :aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb), :creadoEn)
                        """)
                .param("id", evento.id())
                .param("aggregateType", evento.aggregateType())
                .param("aggregateId", evento.aggregateId())
                .param("eventType", evento.eventType())
                .param("payload", evento.payload())
                .param("creadoEn", utc(evento.creadoEn()))
                .update();
    }

    /**
     * Lee y bloquea hasta {@code lote} eventos pendientes, del más antiguo al más reciente. Con
     * {@code FOR UPDATE SKIP LOCKED}, si hay varias instancias del relay cada una toma filas distintas y
     * ninguna espera a la otra. Debe llamarse dentro de una transacción: el bloqueo dura hasta su fin.
     */
    public List<OutboxEvent> bloquearPendientes(int lote) {
        return jdbc.sql("""
                        SELECT id, aggregate_type, aggregate_id, event_type,
                               CAST(payload AS text) AS payload, creado_en
                        FROM outbox_event
                        WHERE publicado_en IS NULL
                        ORDER BY creado_en, id
                        LIMIT :lote
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("lote", lote)
                .query((rs, n) -> new OutboxEvent(
                        rs.getObject("id", UUID.class),
                        rs.getString("aggregate_type"),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getObject("creado_en", OffsetDateTime.class).toInstant()))
                .list();
    }

    public void marcarPublicado(UUID id, Instant cuando) {
        jdbc.sql("UPDATE outbox_event SET publicado_en = :cuando WHERE id = :id")
                .param("cuando", utc(cuando))
                .param("id", id)
                .update();
    }

    /** Borra los eventos ya publicados antes de {@code limite} (el payload puede contener un email). */
    public int purgarPublicadosAnterioresA(Instant limite) {
        return jdbc.sql("DELETE FROM outbox_event WHERE publicado_en IS NOT NULL AND publicado_en < :limite")
                .param("limite", utc(limite))
                .update();
    }

    public long contarPendientes() {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE publicado_en IS NULL")
                .query(Long.class).single();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
