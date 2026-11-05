package io.github.aburgos.reservas.domain.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HorarioAperturaTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static FranjaApertura ap(DayOfWeek d, String abre, String cierra) {
        return new FranjaApertura(d, LocalTime.parse(abre), LocalTime.parse(cierra));
    }

    private static FranjaHoraria utc(String inicio, String fin) {
        return new FranjaHoraria(Instant.parse(inicio), Instant.parse(fin));
    }

    private static HorarioApertura todosLosDias(String abre, String cierra) {
        return new HorarioApertura(java.util.Arrays.stream(DayOfWeek.values())
                .map(d -> ap(d, abre, cierra)).toList());
    }

    // --- FranjaApertura ---

    @Test
    void franjaAperturaRechazaAperturaNoAnteriorACierre() {
        assertThatThrownBy(() -> ap(DayOfWeek.MONDAY, "10:00", "10:00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ap(DayOfWeek.MONDAY, "22:00", "02:00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void franjaAperturaRechazaNulos() {
        assertThatThrownBy(() -> new FranjaApertura(null, LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .isInstanceOf(NullPointerException.class);
    }

    // --- Bordes de apertura y cierre (invierno, UTC+1; lunes 2026-01-12) ---

    @Test
    void permiteReservaDentroDelHorario() {
        assertThat(todosLosDias("09:00", "21:00")
                .permite(utc("2026-01-12T10:00:00Z", "2026-01-12T11:00:00Z"), MADRID)).isTrue();
    }

    @Test
    void permiteReservaQueEmpiezaEnLaAperturaYTerminaEnElCierre() {
        HorarioApertura h = todosLosDias("09:00", "21:00");
        assertThat(h.permite(utc("2026-01-12T08:00:00Z", "2026-01-12T09:00:00Z"), MADRID)).isTrue();  // 09-10
        assertThat(h.permite(utc("2026-01-12T19:00:00Z", "2026-01-12T20:00:00Z"), MADRID)).isTrue();  // 20-21
    }

    @Test
    void rechazaReservaQueEmpiezaAntesDeLaApertura() {
        assertThat(todosLosDias("09:00", "21:00")
                .permite(utc("2026-01-12T07:59:00Z", "2026-01-12T09:00:00Z"), MADRID)).isFalse();
    }

    @Test
    void rechazaReservaQueTerminaDespuesDelCierre() {
        assertThat(todosLosDias("09:00", "21:00")
                .permite(utc("2026-01-12T19:30:00Z", "2026-01-12T20:30:00Z"), MADRID)).isFalse();
    }

    // --- Zona horaria y cambio de hora ---

    @Test
    void mismoInstanteUtcSeInterpretaSegunLaZonaEnInviernoYVerano() {
        HorarioApertura h = todosLosDias("09:00", "21:00");
        // Invierno (UTC+1): 07:00Z = 08:00 local -> cerrado
        assertThat(h.permite(utc("2026-01-12T07:00:00Z", "2026-01-12T08:00:00Z"), MADRID)).isFalse();
        // Verano (UTC+2): 07:00Z = 09:00 local -> abierto
        assertThat(h.permite(utc("2026-07-13T07:00:00Z", "2026-07-13T08:00:00Z"), MADRID)).isTrue();
        // Verano: 06:00Z = 08:00 local -> cerrado
        assertThat(h.permite(utc("2026-07-13T06:00:00Z", "2026-07-13T07:00:00Z"), MADRID)).isFalse();
    }

    @Test
    void cambioDeHoraDeMarzoSaltoDeLas2A3() {
        // Domingo 2026-03-29: a las 01:00Z son las 02:00 CET -> 03:00 CEST
        HorarioApertura h = new HorarioApertura(List.of(ap(DayOfWeek.SUNDAY, "03:00", "05:00")));
        // 03:00 local = 01:00Z
        assertThat(h.permite(utc("2026-03-29T01:00:00Z", "2026-03-29T02:00:00Z"), MADRID)).isTrue();
        // 00:59Z = 01:59 local -> antes de la apertura
        assertThat(h.permite(utc("2026-03-29T00:59:00Z", "2026-03-29T01:59:00Z"), MADRID)).isFalse();
    }

    @Test
    void reservaQueAtraviesaElSaltoDeMarzoEstaDentroSiLaFranjaLoAbarca() {
        HorarioApertura h = new HorarioApertura(List.of(ap(DayOfWeek.SUNDAY, "01:00", "05:00")));
        // 00:30Z-01:30Z = 01:30 CET - 03:30 CEST
        assertThat(h.permite(utc("2026-03-29T00:30:00Z", "2026-03-29T01:30:00Z"), MADRID)).isTrue();
    }

    @Test
    void cambioDeHoraDeOctubreHoraRepetida() {
        // Domingo 2026-10-25: a las 01:00Z son las 03:00 CEST -> 02:00 CET (la hora 02:xx se repite)
        HorarioApertura h = new HorarioApertura(List.of(ap(DayOfWeek.SUNDAY, "01:00", "04:00")));
        // 00:30Z-01:30Z = 02:30 CEST - 02:30 CET: dentro; el día tiene 25 h
        assertThat(h.permite(utc("2026-10-25T00:30:00Z", "2026-10-25T01:30:00Z"), MADRID)).isTrue();
        // 04:00 CET = 03:00Z: una reserva hasta 03:30Z ya excede el cierre
        assertThat(h.permite(utc("2026-10-25T02:30:00Z", "2026-10-25T03:30:00Z"), MADRID)).isFalse();
        assertThat(h.permite(utc("2026-10-25T02:00:00Z", "2026-10-25T03:00:00Z"), MADRID)).isTrue();
    }

    // --- Días sin horario, medianoche ---

    @Test
    void diaSinHorarioConfiguradoEstaCerrado() {
        HorarioApertura soloLunes = new HorarioApertura(List.of(ap(DayOfWeek.MONDAY, "09:00", "21:00")));
        // martes 2026-01-13
        assertThat(soloLunes.permite(utc("2026-01-13T10:00:00Z", "2026-01-13T11:00:00Z"), MADRID)).isFalse();
        assertThat(new HorarioApertura(List.of())
                .permite(utc("2026-01-12T10:00:00Z", "2026-01-12T11:00:00Z"), MADRID)).isFalse();
    }

    @Test
    void rechazaReservaQueCruzaLaMedianoche() {
        HorarioApertura h = new HorarioApertura(List.of(
                ap(DayOfWeek.MONDAY, "20:00", "23:59"),
                ap(DayOfWeek.TUESDAY, "00:00", "02:00")));
        // lunes 23:30 - martes 00:30 locales
        assertThat(h.permite(utc("2026-01-12T22:30:00Z", "2026-01-12T23:30:00Z"), MADRID)).isFalse();
    }

    // --- Normalización de franjas ---

    @Test
    void franjasContiguasDelMismoDiaSeFusionan() {
        HorarioApertura h = new HorarioApertura(List.of(
                ap(DayOfWeek.MONDAY, "14:00", "21:00"),
                ap(DayOfWeek.MONDAY, "09:00", "14:00")));
        assertThat(h.franjas()).containsExactly(ap(DayOfWeek.MONDAY, "09:00", "21:00"));
        // 13:00-15:00 local (UTC+1) cruza el punto de unión
        assertThat(h.permite(utc("2026-01-12T12:00:00Z", "2026-01-12T14:00:00Z"), MADRID)).isTrue();
    }

    @Test
    void franjasConHuecoNoSeFusionan() {
        HorarioApertura h = new HorarioApertura(List.of(
                ap(DayOfWeek.MONDAY, "09:00", "13:00"),
                ap(DayOfWeek.MONDAY, "16:00", "20:00")));
        assertThat(h.franjas()).hasSize(2);
        assertThat(h.permite(utc("2026-01-12T12:00:00Z", "2026-01-12T14:00:00Z"), MADRID)).isFalse();
    }

    @Test
    void rechazaFranjasSolapadasDelMismoDia() {
        assertThatThrownBy(() -> new HorarioApertura(List.of(
                ap(DayOfWeek.MONDAY, "09:00", "14:00"),
                ap(DayOfWeek.MONDAY, "13:00", "21:00"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mismaHoraEnDiasDistintosNoEsSolape() {
        assertThat(new HorarioApertura(List.of(
                ap(DayOfWeek.MONDAY, "09:00", "14:00"),
                ap(DayOfWeek.TUESDAY, "09:00", "14:00"))).franjas()).hasSize(2);
    }

    @Test
    void laListaDeFranjasEsInmutable() {
        HorarioApertura h = todosLosDias("09:00", "21:00");
        assertThatThrownBy(() -> h.franjas().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
