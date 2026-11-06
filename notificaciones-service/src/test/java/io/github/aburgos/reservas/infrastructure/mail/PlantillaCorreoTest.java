package io.github.aburgos.reservas.infrastructure.mail;

import io.github.aburgos.reservas.event.ReservaCancelada;
import io.github.aburgos.reservas.event.ReservaCreada;
import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlantillaCorreoTest {

    private static final Instant OCURRIDO = Instant.parse("2026-01-05T09:00:00Z");

    private static ReservaCreada creada(String zona, String inicio) {
        Instant i = Instant.parse(inicio);
        return new ReservaCreada(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Pista 1", zona,
                "user-1", "ana@example.org", i, i.plusSeconds(3600), OCURRIDO);
    }

    @Test
    void laReservaCreadaMuestraLaHoraEnLaZonaDelRecurso() {
        // 10:00Z en enero, Madrid es UTC+1: 11:00 locales
        PlantillaCorreo.Correo correo = PlantillaCorreo.renderizar(creada("Europe/Madrid", "2026-01-12T10:00:00Z"));

        assertThat(correo.asunto()).isEqualTo("Reserva confirmada: Pista 1");
        assertThat(correo.cuerpo())
                .contains("Tu reserva ha quedado confirmada.")
                .contains("Recurso: Pista 1")
                .contains("Fecha: lunes 12 de enero de 2026")
                .contains("Horario: 11:00 - 12:00 (Europe/Madrid)");
    }

    @Test
    void laCancelacionTieneSuPropioAsuntoYTexto() {
        Instant inicio = Instant.parse("2026-01-12T10:00:00Z");
        ReservaCancelada cancelada = new ReservaCancelada(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Sala B", "Europe/Madrid", "user-1", "ana@example.org", inicio, inicio.plusSeconds(3600), OCURRIDO);

        PlantillaCorreo.Correo correo = PlantillaCorreo.renderizar(cancelada);

        assertThat(correo.asunto()).isEqualTo("Reserva cancelada: Sala B");
        assertThat(correo.cuerpo())
                .contains("Tu reserva ha sido cancelada.")
                .contains("Recurso: Sala B")
                .contains("Horario: 11:00 - 12:00 (Europe/Madrid)");
    }

    @Test
    void despuesDelCambioDeHoraDeMarzoMadridVaA_UTCmas2() {
        // El 29 de marzo de 2026 a las 02:00 locales se pasa a horario de verano (CEST, UTC+2).
        PlantillaCorreo.Correo antes = PlantillaCorreo.renderizar(creada("Europe/Madrid", "2026-03-28T10:00:00Z"));
        PlantillaCorreo.Correo despues = PlantillaCorreo.renderizar(creada("Europe/Madrid", "2026-03-29T10:00:00Z"));

        assertThat(antes.cuerpo()).contains("sábado 28 de marzo de 2026").contains("Horario: 11:00 - 12:00");
        assertThat(despues.cuerpo()).contains("domingo 29 de marzo de 2026").contains("Horario: 12:00 - 13:00");
    }

    @Test
    void despuesDelCambioDeHoraDeOctubreMadridVuelveAUTCmas1() {
        // El 25 de octubre de 2026 a las 03:00 locales se vuelve a horario de invierno (CET, UTC+1).
        PlantillaCorreo.Correo antes = PlantillaCorreo.renderizar(creada("Europe/Madrid", "2026-10-24T10:00:00Z"));
        PlantillaCorreo.Correo despues = PlantillaCorreo.renderizar(creada("Europe/Madrid", "2026-10-25T10:00:00Z"));

        assertThat(antes.cuerpo()).contains("sábado 24 de octubre de 2026").contains("Horario: 12:00 - 13:00");
        assertThat(despues.cuerpo()).contains("domingo 25 de octubre de 2026").contains("Horario: 11:00 - 12:00");
    }

    @Test
    void cadaRecursoUsaSuPropiaZonaNoLaDelServidor() {
        PlantillaCorreo.Correo canarias = PlantillaCorreo.renderizar(creada("Atlantic/Canary", "2026-01-12T10:00:00Z"));

        assertThat(canarias.cuerpo()).contains("Horario: 10:00 - 11:00 (Atlantic/Canary)");
    }

    @Test
    void unaZonaInvalidaFallaEnVezDeMostrarUnaHoraIncorrecta() {
        assertThatThrownBy(() -> PlantillaCorreo.renderizar(creada("Marte/Olympus", "2026-01-12T10:00:00Z")))
                .isInstanceOf(DateTimeException.class);
    }
}
