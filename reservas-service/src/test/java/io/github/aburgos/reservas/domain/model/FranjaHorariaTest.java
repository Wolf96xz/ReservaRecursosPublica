package io.github.aburgos.reservas.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FranjaHorariaTest {

    private static Instant t(int hora) {
        return Instant.parse("2026-03-10T00:00:00Z").plusSeconds(hora * 3600L);
    }

    private static FranjaHoraria f(int ini, int fin) {
        return new FranjaHoraria(t(ini), t(fin));
    }

    @Test
    void rechazaInicioPosteriorOIgualAFin() {
        assertThatThrownBy(() -> f(10, 9)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f(10, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaNulos() {
        assertThatThrownBy(() -> new FranjaHoraria(null, t(1))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FranjaHoraria(t(1), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void franjasContiguasNoSeSolapan() {
        assertThat(f(10, 11).solapaCon(f(11, 12))).isFalse();
        assertThat(f(11, 12).solapaCon(f(10, 11))).isFalse();
    }

    @Test
    void franjasDisjuntasNoSeSolapan() {
        assertThat(f(8, 9).solapaCon(f(11, 12))).isFalse();
    }

    @Test
    void franjasParcialmenteSolapadasSeSolapan() {
        assertThat(f(10, 12).solapaCon(f(11, 13))).isTrue();
        assertThat(f(11, 13).solapaCon(f(10, 12))).isTrue();
    }

    @Test
    void franjaContenidaSeSolapa() {
        assertThat(f(10, 14).solapaCon(f(11, 12))).isTrue();
        assertThat(f(11, 12).solapaCon(f(10, 14))).isTrue();
    }

    @Test
    void franjasIdenticasSeSolapan() {
        assertThat(f(10, 12).solapaCon(f(10, 12))).isTrue();
    }
}
