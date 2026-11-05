package io.github.aburgos.reservas.domain.model;

import io.github.aburgos.reservas.domain.exception.FueraDeHorarioException;
import io.github.aburgos.reservas.domain.exception.RecursoNoDisponibleException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecursoTest {

    private static final HorarioApertura LUNES_9_A_21 = new HorarioApertura(
            List.of(new FranjaApertura(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(21, 0))));

    // Lunes 2026-01-12, invierno (UTC+1)
    private static final FranjaHoraria DENTRO = franja("2026-01-12T10:00:00Z", "2026-01-12T11:00:00Z");
    private static final FranjaHoraria FUERA = franja("2026-01-12T06:00:00Z", "2026-01-12T07:00:00Z");

    private static FranjaHoraria franja(String ini, String fin) {
        return new FranjaHoraria(Instant.parse(ini), Instant.parse(fin));
    }

    private static Recurso pista() {
        return Recurso.crear("Pista 1", TipoRecurso.PISTA, LUNES_9_A_21);
    }

    @Test
    void crearDejaElRecursoActivoYEnZonaMadrid() {
        Recurso r = pista();
        assertThat(r.isActivo()).isTrue();
        assertThat(r.getZona()).isEqualTo(ZoneId.of("Europe/Madrid"));
        assertThat(r.getId()).isNotNull();
        assertThat(r.getTipo()).isEqualTo(TipoRecurso.PISTA);
    }

    @Test
    void validarReservaAceptaFranjaDentroDeHorario() {
        assertThatCode(() -> pista().validarReserva(DENTRO)).doesNotThrowAnyException();
    }

    @Test
    void validarReservaRechazaFranjaFueraDeHorario() {
        assertThatThrownBy(() -> pista().validarReserva(FUERA))
                .isInstanceOf(FueraDeHorarioException.class);
    }

    @Test
    void validarReservaRechazaDiaSinHorario() {
        // martes 2026-01-13
        assertThatThrownBy(() -> pista().validarReserva(franja("2026-01-13T10:00:00Z", "2026-01-13T11:00:00Z")))
                .isInstanceOf(FueraDeHorarioException.class);
    }

    @Test
    void validarReservaRechazaRecursoInactivo() {
        Recurso r = pista();
        r.desactivar();
        assertThatThrownBy(() -> r.validarReserva(DENTRO))
                .isInstanceOf(RecursoNoDisponibleException.class);
    }

    @Test
    void recursoInactivoSeReportaComoNoDisponibleAunqueLaFranjaEsteFueraDeHorario() {
        Recurso r = pista();
        r.desactivar();
        assertThatThrownBy(() -> r.validarReserva(FUERA))
                .isInstanceOf(RecursoNoDisponibleException.class);
    }

    @Test
    void reactivarPermiteReservarDeNuevo() {
        Recurso r = pista();
        r.desactivar();
        r.activar();
        assertThatCode(() -> r.validarReserva(DENTRO)).doesNotThrowAnyException();
    }

    @Test
    void validarReservaInterpretaElHorarioEnLaZonaDelRecurso() {
        // La misma franja 06:00Z-07:00Z del lunes 2026-01-12:
        //  - Madrid (UTC+1): 07:00-08:00 -> antes de la apertura (09:00) -> fuera de horario
        //  - Tokio (UTC+9):  15:00-16:00 -> dentro del horario
        Recurso tokio = new Recurso(UUID.randomUUID(), "Sala", TipoRecurso.SALA,
                ZoneId.of("Asia/Tokyo"), LUNES_9_A_21, true);

        assertThatThrownBy(() -> pista().validarReserva(FUERA)).isInstanceOf(FueraDeHorarioException.class);
        assertThatCode(() -> tokio.validarReserva(FUERA)).doesNotThrowAnyException();
    }

    @Test
    void cambiarHorarioAfectaALaValidacion() {
        Recurso r = pista();
        r.cambiarHorario(new HorarioApertura(List.of()));
        assertThatThrownBy(() -> r.validarReserva(DENTRO)).isInstanceOf(FueraDeHorarioException.class);
    }

    @Test
    void rechazaNombreVacioONulo() {
        assertThatThrownBy(() -> Recurso.crear(" ", TipoRecurso.SALA, LUNES_9_A_21))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Recurso.crear(null, TipoRecurso.SALA, LUNES_9_A_21))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> pista().renombrar(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaDatosObligatoriosNulos() {
        assertThatThrownBy(() -> Recurso.crear("X", null, LUNES_9_A_21)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Recurso.crear("X", TipoRecurso.SALA, null)).isInstanceOf(NullPointerException.class);
    }
}
