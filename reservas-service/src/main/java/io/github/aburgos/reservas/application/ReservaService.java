package io.github.aburgos.reservas.application;

import io.github.aburgos.reservas.application.command.CancelarReservaCommand;
import io.github.aburgos.reservas.application.command.CrearReservaCommand;
import io.github.aburgos.reservas.application.port.EventPublisher;
import io.github.aburgos.reservas.domain.exception.RecursoNoEncontradoException;
import io.github.aburgos.reservas.domain.exception.ReservaNoEncontradaException;
import io.github.aburgos.reservas.domain.exception.ReservaSolapadaException;
import io.github.aburgos.reservas.domain.model.FranjaHoraria;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.domain.model.Reserva;
import io.github.aburgos.reservas.domain.policy.PoliticaCancelacion;
import io.github.aburgos.reservas.event.ReservaCancelada;
import io.github.aburgos.reservas.event.ReservaCreada;
import io.github.aburgos.reservas.infrastructure.persistence.RecursoRepository;
import io.github.aburgos.reservas.infrastructure.persistence.ReservaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ReservaService {

    private final ReservaRepository reservas;
    private final RecursoRepository recursos;
    private final EventPublisher eventos;
    private final Clock clock;

    public ReservaService(ReservaRepository reservas, RecursoRepository recursos,
                          EventPublisher eventos, Clock clock) {
        this.reservas = reservas;
        this.recursos = recursos;
        this.eventos = eventos;
        this.clock = clock;
    }

    /**
     * Anti-solapamiento en dos capas: la consulta da un error de negocio claro en el caso normal;
     * la restricción de exclusión de la base de datos es la garantía real cuando dos peticiones
     * concurrentes pasan la consulta a la vez (la perdedora falla al insertar).
     */
    @Transactional
    public Reserva crear(CrearReservaCommand cmd) {
        Recurso recurso = recursos.findById(cmd.recursoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(cmd.recursoId()));
        FranjaHoraria franja = new FranjaHoraria(cmd.inicio(), cmd.fin());
        Reserva reserva = Reserva.crear(recurso, cmd.usuarioId(), franja, clock);

        if (reservas.existeSolapada(recurso.getId(), franja.inicio(), franja.fin())) {
            throw solapada(recurso.getId(), franja);
        }
        Reserva guardada;
        try {
            guardada = reservas.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            throw solapada(recurso.getId(), franja);
        }
        // Mismo @Transactional que el guardado: o se confirman la reserva y su evento, o ninguno de los dos.
        eventos.publicar(new ReservaCreada(UUID.randomUUID(), guardada.getId(), recurso.getId(),
                recurso.getNombre(), recurso.getZona().getId(), guardada.getUsuarioId(), cmd.email(),
                franja.inicio(), franja.fin(), clock.instant()));
        return guardada;
    }

    @Transactional
    public Reserva cancelar(CancelarReservaCommand cmd) {
        Reserva reserva = buscarDelUsuario(cmd.reservaId(), cmd.usuarioId());
        reserva.cancelar(clock, PoliticaCancelacion.POR_DEFECTO);
        Reserva guardada = reservas.saveAndFlush(reserva);
        Recurso recurso = recursos.findById(guardada.getRecursoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(guardada.getRecursoId()));
        eventos.publicar(new ReservaCancelada(UUID.randomUUID(), guardada.getId(), recurso.getId(),
                recurso.getNombre(), recurso.getZona().getId(), guardada.getUsuarioId(), cmd.email(),
                guardada.getFranja().inicio(), guardada.getFranja().fin(), guardada.getCanceladaEn()));
        return guardada;
    }

    @Transactional(readOnly = true)
    public Reserva obtener(UUID reservaId, String usuarioId) {
        return buscarDelUsuario(reservaId, usuarioId);
    }

    @Transactional(readOnly = true)
    public List<Reserva> listarDelUsuario(String usuarioId) {
        return reservas.findByUsuarioIdOrderByFranjaInicioAsc(usuarioId);
    }

    /** La reserva de otro usuario se trata como inexistente para no revelar que existe. */
    private Reserva buscarDelUsuario(UUID reservaId, String usuarioId) {
        return reservas.findById(reservaId)
                .filter(r -> r.getUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new ReservaNoEncontradaException(reservaId));
    }

    private static ReservaSolapadaException solapada(UUID recursoId, FranjaHoraria franja) {
        return new ReservaSolapadaException("El recurso " + recursoId + " ya está reservado entre "
                + franja.inicio() + " y " + franja.fin());
    }
}
