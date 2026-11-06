package io.github.aburgos.reservas.domain.model;

import io.github.aburgos.reservas.domain.exception.CancelacionNoPermitidaException;
import io.github.aburgos.reservas.domain.exception.FueraDeHorarioException;
import io.github.aburgos.reservas.domain.exception.RecursoNoDisponibleException;
import io.github.aburgos.reservas.domain.policy.PoliticaCancelacion;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservaTest {

    private static final PoliticaCancelacion POLITICA = PoliticaCancelacion.POR_DEFECTO; // 24 h

    // Lunes 2026-01-12, invierno (UTC+1): reserva 10:00Z-11:00Z = 11:00-12:00 locales
    private static final FranjaHoraria FRANJA = franja("2026-01-12T10:00:00Z", "2026-01-12T11:00:00Z");

    private static FranjaHoraria franja(String ini, String fin) {
        return new FranjaHoraria(Instant.parse(ini), Instant.parse(fin));
    }

    private static Clock reloj(String instante) {
        return Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
    }

    private static Recurso recurso() {
        return Recurso.crear("Pista 1", TipoRecurso.PISTA, new HorarioApertura(List.of(
                new FranjaApertura(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(21, 0)))));
    }

    private static Reserva reserva() {
        return Reserva.crear(recurso(), "user-1", FRANJA, reloj("2026-01-05T09:00:00Z"));
    }

    // --- crear ---

    @Test
    void crearDejaLaReservaConfirmadaConLosDatosDelRecursoYElReloj() {
        Recurso r = recurso();
        Reserva res = Reserva.crear(r, "user-1", FRANJA, reloj("2026-01-05T09:00:00Z"));

        assertThat(res.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(res.getRecursoId()).isEqualTo(r.getId());
        assertThat(res.getUsuarioId()).isEqualTo("user-1");
        assertThat(res.getFranja()).isEqualTo(FRANJA);
        assertThat(res.getCreadaEn()).isEqualTo(Instant.parse("2026-01-05T09:00:00Z"));
        assertThat(res.getCanceladaEn()).isNull();
        assertThat(res.getId()).isNotNull();
    }

    @Test
    void crearFallaSiLaFranjaEstaFueraDeHorario() {
        assertThatThrownBy(() -> Reserva.crear(recurso(), "u",
                franja("2026-01-12T06:00:00Z", "2026-01-12T07:00:00Z"), reloj("2026-01-05T09:00:00Z")))
                .isInstanceOf(FueraDeHorarioException.class);
    }

    @Test
    void crearFallaSiElRecursoEstaInactivo() {
        Recurso r = recurso();
        r.desactivar();
        assertThatThrownBy(() -> Reserva.crear(r, "u", FRANJA, reloj("2026-01-05T09:00:00Z")))
                .isInstanceOf(RecursoNoDisponibleException.class);
    }

    @Test
    void crearFallaSiLaFranjaEmpiezaEnElPasado() {
        assertThatThrownBy(() -> Reserva.crear(recurso(), "u", FRANJA, reloj("2026-01-12T10:00:01Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pasado");
    }

    @Test
    void crearPermiteUnaFranjaQueEmpiezaExactamenteAhora() {
        Reserva res = Reserva.crear(recurso(), "u", FRANJA, reloj("2026-01-12T10:00:00Z"));
        assertThat(res.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    void crearRechazaUsuarioVacioONulo() {
        assertThatThrownBy(() -> Reserva.crear(recurso(), " ", FRANJA, reloj("2026-01-05T09:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reserva.crear(recurso(), null, FRANJA, reloj("2026-01-05T09:00:00Z")))
                .isInstanceOf(NullPointerException.class);
    }

    // --- cancelar ---

    @Test
    void cancelarDentroDelPlazoCambiaElEstadoYRegistraElInstante() {
        Reserva res = reserva();
        res.cancelar(reloj("2026-01-10T09:00:00Z"), POLITICA); // 49 h antes

        assertThat(res.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(res.getCanceladaEn()).isEqualTo(Instant.parse("2026-01-10T09:00:00Z"));
    }

    @Test
    void cancelarConLaAntelacionExactaEstaPermitido() {
        Reserva res = reserva();
        res.cancelar(reloj("2026-01-11T10:00:00Z"), POLITICA); // exactamente 24 h antes
        assertThat(res.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void cancelarFueraDelPlazoLanzaExcepcionYNoCambiaElEstado() {
        Reserva res = reserva();
        assertThatThrownBy(() -> res.cancelar(reloj("2026-01-11T10:00:01Z"), POLITICA)) // 23 h 59 m 59 s
                .isInstanceOf(CancelacionNoPermitidaException.class);

        assertThat(res.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(res.getCanceladaEn()).isNull();
    }

    @Test
    void cancelarUnaReservaYaIniciadaLanzaExcepcion() {
        Reserva res = reserva();
        assertThatThrownBy(() -> res.cancelar(reloj("2026-01-12T10:30:00Z"), POLITICA))
                .isInstanceOf(CancelacionNoPermitidaException.class);
    }

    @Test
    void cancelarDosVecesLanzaExcepcionYConservaElPrimerInstante() {
        Reserva res = reserva();
        res.cancelar(reloj("2026-01-10T09:00:00Z"), POLITICA);

        assertThatThrownBy(() -> res.cancelar(reloj("2026-01-10T12:00:00Z"), POLITICA))
                .isInstanceOf(CancelacionNoPermitidaException.class);
        assertThat(res.getCanceladaEn()).isEqualTo(Instant.parse("2026-01-10T09:00:00Z"));
    }

    // --- bloquea ---

    @Test
    void reservaConfirmadaBloqueaFranjasSolapadasDelMismoRecurso() {
        Recurso r = recurso();
        Reserva res = Reserva.crear(r, "u", FRANJA, reloj("2026-01-05T09:00:00Z"));

        assertThat(res.bloquea(r.getId(), franja("2026-01-12T10:30:00Z", "2026-01-12T11:30:00Z"))).isTrue();
        assertThat(res.bloquea(r.getId(), FRANJA)).isTrue();
    }

    @Test
    void reservaNoBloqueaFranjasContiguas() {
        Recurso r = recurso();
        Reserva res = Reserva.crear(r, "u", FRANJA, reloj("2026-01-05T09:00:00Z"));

        assertThat(res.bloquea(r.getId(), franja("2026-01-12T11:00:00Z", "2026-01-12T12:00:00Z"))).isFalse();
        assertThat(res.bloquea(r.getId(), franja("2026-01-12T09:00:00Z", "2026-01-12T10:00:00Z"))).isFalse();
    }

    @Test
    void reservaNoBloqueaOtroRecurso() {
        assertThat(reserva().bloquea(UUID.randomUUID(), FRANJA)).isFalse();
    }

    @Test
    void reservaCanceladaYaNoBloquea() {
        Recurso r = recurso();
        Reserva res = Reserva.crear(r, "u", FRANJA, reloj("2026-01-05T09:00:00Z"));
        res.cancelar(reloj("2026-01-10T09:00:00Z"), POLITICA);

        assertThat(res.bloquea(r.getId(), FRANJA)).isFalse();
    }
}
