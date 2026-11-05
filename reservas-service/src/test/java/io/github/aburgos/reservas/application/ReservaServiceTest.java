package io.github.aburgos.reservas.application;

import io.github.aburgos.reservas.application.command.CancelarReservaCommand;
import io.github.aburgos.reservas.application.command.CrearReservaCommand;
import io.github.aburgos.reservas.domain.exception.CancelacionNoPermitidaException;
import io.github.aburgos.reservas.domain.exception.FueraDeHorarioException;
import io.github.aburgos.reservas.domain.exception.RecursoNoEncontradoException;
import io.github.aburgos.reservas.domain.exception.ReservaNoEncontradaException;
import io.github.aburgos.reservas.domain.exception.ReservaSolapadaException;
import io.github.aburgos.reservas.domain.model.EstadoReserva;
import io.github.aburgos.reservas.domain.model.FranjaApertura;
import io.github.aburgos.reservas.domain.model.FranjaHoraria;
import io.github.aburgos.reservas.domain.model.HorarioApertura;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.domain.model.Reserva;
import io.github.aburgos.reservas.domain.model.TipoRecurso;
import io.github.aburgos.reservas.infrastructure.persistence.RecursoRepository;
import io.github.aburgos.reservas.infrastructure.persistence.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    private static final Instant INICIO = Instant.parse("2026-01-12T10:00:00Z"); // lunes, 11:00 locales
    private static final Instant FIN = Instant.parse("2026-01-12T11:00:00Z");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-01-05T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    ReservaRepository reservas;

    @Mock
    RecursoRepository recursos;

    private ReservaService service() {
        return new ReservaService(reservas, recursos, RELOJ);
    }

    private static Recurso recurso() {
        return Recurso.crear("Pista 1", TipoRecurso.PISTA, new HorarioApertura(List.of(
                new FranjaApertura(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(21, 0)))));
    }

    private static Reserva reservaDe(String usuario, Instant inicio) {
        return new Reserva(UUID.randomUUID(), UUID.randomUUID(), usuario,
                new FranjaHoraria(inicio, inicio.plusSeconds(3600)),
                EstadoReserva.CONFIRMADA, Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    // --- crear ---

    @Test
    void crearGuardaLaReservaConfirmada() {
        Recurso r = recurso();
        when(recursos.findById(r.getId())).thenReturn(Optional.of(r));
        when(reservas.existeSolapada(r.getId(), INICIO, FIN)).thenReturn(false);
        when(reservas.saveAndFlush(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));

        Reserva creada = service().crear(new CrearReservaCommand(r.getId(), "user-1", INICIO, FIN));

        assertThat(creada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(creada.getUsuarioId()).isEqualTo("user-1");
        assertThat(creada.getRecursoId()).isEqualTo(r.getId());
        assertThat(creada.getCreadaEn()).isEqualTo(RELOJ.instant());
    }

    @Test
    void crearFallaSiElRecursoNoExiste() {
        UUID id = UUID.randomUUID();
        when(recursos.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().crear(new CrearReservaCommand(id, "u", INICIO, FIN)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(reservas, never()).saveAndFlush(any());
    }

    @Test
    void crearFallaFueraDeHorarioSinConsultarSolapamientos() {
        Recurso r = recurso();
        when(recursos.findById(r.getId())).thenReturn(Optional.of(r));
        Instant tempranoInicio = Instant.parse("2026-01-12T05:00:00Z"); // 06:00 locales

        assertThatThrownBy(() -> service().crear(
                new CrearReservaCommand(r.getId(), "u", tempranoInicio, tempranoInicio.plusSeconds(3600))))
                .isInstanceOf(FueraDeHorarioException.class);
        verify(reservas, never()).existeSolapada(any(), any(), any());
        verify(reservas, never()).saveAndFlush(any());
    }

    @Test
    void crearFallaSiYaHayUnaReservaSolapada() {
        Recurso r = recurso();
        when(recursos.findById(r.getId())).thenReturn(Optional.of(r));
        when(reservas.existeSolapada(r.getId(), INICIO, FIN)).thenReturn(true);

        assertThatThrownBy(() -> service().crear(new CrearReservaCommand(r.getId(), "u", INICIO, FIN)))
                .isInstanceOf(ReservaSolapadaException.class);
        verify(reservas, never()).saveAndFlush(any());
    }

    @Test
    void crearTraduceLaViolacionDeLaRestriccionDeLaBaseDeDatosEnSolape() {
        // Dos peticiones concurrentes pasan la consulta; la perdedora falla al insertar.
        Recurso r = recurso();
        when(recursos.findById(r.getId())).thenReturn(Optional.of(r));
        when(reservas.existeSolapada(r.getId(), INICIO, FIN)).thenReturn(false);
        when(reservas.saveAndFlush(any(Reserva.class))).thenThrow(new DataIntegrityViolationException("reserva_sin_solape"));

        assertThatThrownBy(() -> service().crear(new CrearReservaCommand(r.getId(), "u", INICIO, FIN)))
                .isInstanceOf(ReservaSolapadaException.class);
    }

    @Test
    void crearRechazaUnaFranjaInvertida() {
        Recurso r = recurso();
        when(recursos.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service().crear(new CrearReservaCommand(r.getId(), "u", FIN, INICIO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- cancelar ---

    @Test
    void cancelarMarcaLaReservaComoCancelada() {
        Reserva reserva = reservaDe("user-1", Instant.parse("2026-01-12T10:00:00Z")); // 7 días después del reloj
        when(reservas.findById(reserva.getId())).thenReturn(Optional.of(reserva));
        when(reservas.saveAndFlush(reserva)).thenReturn(reserva);

        Reserva cancelada = service().cancelar(new CancelarReservaCommand(reserva.getId(), "user-1"));

        assertThat(cancelada.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(cancelada.getCanceladaEn()).isEqualTo(RELOJ.instant());
    }

    @Test
    void cancelarFueraDelPlazoFalla() {
        Reserva reserva = reservaDe("user-1", Instant.parse("2026-01-05T20:00:00Z")); // 11 h después del reloj
        when(reservas.findById(reserva.getId())).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> service().cancelar(new CancelarReservaCommand(reserva.getId(), "user-1")))
                .isInstanceOf(CancelacionNoPermitidaException.class);
        verify(reservas, never()).saveAndFlush(any());
    }

    @Test
    void cancelarLaReservaDeOtroUsuarioSeTrataComoInexistente() {
        Reserva reserva = reservaDe("user-1", Instant.parse("2026-01-12T10:00:00Z"));
        when(reservas.findById(reserva.getId())).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> service().cancelar(new CancelarReservaCommand(reserva.getId(), "otro")))
                .isInstanceOf(ReservaNoEncontradaException.class);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    void cancelarUnaReservaInexistenteFalla() {
        UUID id = UUID.randomUUID();
        when(reservas.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().cancelar(new CancelarReservaCommand(id, "u")))
                .isInstanceOf(ReservaNoEncontradaException.class);
    }

    // --- consultas ---

    @Test
    void obtenerDevuelveLaReservaDelUsuario() {
        Reserva reserva = reservaDe("user-1", INICIO);
        when(reservas.findById(reserva.getId())).thenReturn(Optional.of(reserva));

        assertThat(service().obtener(reserva.getId(), "user-1")).isSameAs(reserva);
        assertThatThrownBy(() -> service().obtener(reserva.getId(), "otro"))
                .isInstanceOf(ReservaNoEncontradaException.class);
    }

    @Test
    void listarDelUsuarioDelegaEnElRepositorio() {
        Reserva reserva = reservaDe("user-1", INICIO);
        when(reservas.findByUsuarioIdOrderByFranjaInicioAsc("user-1")).thenReturn(List.of(reserva));

        assertThat(service().listarDelUsuario("user-1")).containsExactly(reserva);
    }
}
