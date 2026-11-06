package io.github.aburgos.reservas.infrastructure.persistence;

import io.github.aburgos.reservas.domain.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

    /**
     * Registra el evento como procesado si es la primera vez que se ve. Devuelve 1 si lo insertó y 0 si ya
     * existía. Es atómico: dos consumidores concurrentes con el mismo evento no pueden ganar los dos. Con
     * {@code save()} un duplicado lanzaría una excepción que dejaría la transacción inservible en PostgreSQL.
     */
    @Modifying
    @Query(value = """
            INSERT INTO evento_procesado (event_id, procesado_en)
            VALUES (:eventId, :procesadoEn)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertarSiNoExiste(@Param("eventId") UUID eventId, @Param("procesadoEn") Instant procesadoEn);
}
