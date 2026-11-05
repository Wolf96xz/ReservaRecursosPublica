package io.github.aburgos.reservas.domain.policy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaCancelacionTest {

    private static final Instant INICIO = Instant.parse("2026-01-12T10:00:00Z");
    private final PoliticaCancelacion politica = new PoliticaCancelacion(Duration.ofHours(24));

    @Test
    void permiteCancelarConMasAntelacionDeLaMinima() {
        assertThat(politica.permiteCancelar(INICIO, INICIO.minus(Duration.ofHours(25)))).isTrue();
    }

    @Test
    void permiteCancelarConLaAntelacionMinimaExacta() {
        assertThat(politica.permiteCancelar(INICIO, INICIO.minus(Duration.ofHours(24)))).isTrue();
    }

    @Test
    void rechazaCancelarUnSegundoDespuesDelLimite() {
        assertThat(politica.permiteCancelar(INICIO, INICIO.minus(Duration.ofHours(24)).plusSeconds(1))).isFalse();
    }

    @Test
    void rechazaCancelarCuandoLaReservaYaHaEmpezado() {
        assertThat(politica.permiteCancelar(INICIO, INICIO)).isFalse();
        assertThat(politica.permiteCancelar(INICIO, INICIO.plusSeconds(60))).isFalse();
    }

    @Test
    void antelacionCeroPermiteCancelarHastaElInicio() {
        PoliticaCancelacion sinAntelacion = new PoliticaCancelacion(Duration.ZERO);
        assertThat(sinAntelacion.permiteCancelar(INICIO, INICIO)).isTrue();
        assertThat(sinAntelacion.permiteCancelar(INICIO, INICIO.plusSeconds(1))).isFalse();
    }

    @Test
    void porDefectoSon24Horas() {
        assertThat(PoliticaCancelacion.POR_DEFECTO.antelacionMinima()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void rechazaAntelacionNegativaONula() {
        assertThatThrownBy(() -> new PoliticaCancelacion(Duration.ofHours(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaCancelacion(null))
                .isInstanceOf(NullPointerException.class);
    }
}
