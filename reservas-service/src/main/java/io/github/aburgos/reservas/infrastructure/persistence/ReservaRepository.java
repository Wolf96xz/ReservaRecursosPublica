package io.github.aburgos.reservas.infrastructure.persistence;

import io.github.aburgos.reservas.domain.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    /**
     * Hay alguna reserva CONFIRMADA del recurso que se solape con [inicio, fin).
     * Intervalos semiabiertos: una reserva que termina en {@code inicio} (o empieza en {@code fin}) no cuenta.
     * Es la comprobación "amistosa" de la capa de aplicación; la garantía real es la restricción de la BD.
     */
    @Query("""
            select count(r) > 0 from Reserva r
            where r.recursoId = :recursoId
              and r.estado = io.github.aburgos.reservas.domain.model.EstadoReserva.CONFIRMADA
              and r.franja.inicio < :fin
              and r.franja.fin > :inicio
            """)
    boolean existeSolapada(@Param("recursoId") UUID recursoId,
                           @Param("inicio") Instant inicio,
                           @Param("fin") Instant fin);

    List<Reserva> findByUsuarioIdOrderByFranjaInicioAsc(String usuarioId);
}
