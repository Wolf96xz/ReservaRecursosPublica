package io.github.aburgos.reservas.infrastructure.persistence;

import io.github.aburgos.reservas.domain.Notificacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    Optional<Notificacion> findByEventId(UUID eventId);

    /**
     * Notificaciones por reintentar, de la más antigua a la más reciente: pendientes o fallidas con intentos
     * por agotar, creadas antes de {@code creadasAntesDe}. El margen evita pisar a un envío que el propio
     * consumidor tiene en marcha en ese momento.
     */
    @Query("""
            select n.id from Notificacion n
            where n.estado <> io.github.aburgos.reservas.domain.EstadoNotificacion.ENVIADA
              and n.intentos < :maxIntentos
              and n.creadaEn < :creadasAntesDe
            order by n.creadaEn
            """)
    List<UUID> idsReintentables(@Param("maxIntentos") int maxIntentos,
                                @Param("creadasAntesDe") Instant creadasAntesDe,
                                Pageable limite);
}
