package io.github.aburgos.reservas.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificacionTest {

    private static final Instant CREADA = Instant.parse("2026-01-05T09:00:00Z");
    private static final Instant ENVIO = Instant.parse("2026-01-05T09:05:00Z");
    private static final Clock RELOJ_CREACION = Clock.fixed(CREADA, ZoneOffset.UTC);
    private static final Clock RELOJ_ENVIO = Clock.fixed(ENVIO, ZoneOffset.UTC);

    private static Notificacion nueva() {
        return Notificacion.nueva(UUID.randomUUID(), UUID.randomUUID(), TipoNotificacion.RESERVA_CREADA,
                "ana@example.org", "{}", RELOJ_CREACION);
    }

    @Test
    void unaNotificacionNuevaEstaPendienteYSinIntentos() {
        Notificacion n = nueva();

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(n.getIntentos()).isZero();
        assertThat(n.getCreadaEn()).isEqualTo(CREADA);
        assertThat(n.getEnviadaEn()).isNull();
        assertThat(n.getUltimoError()).isNull();
        assertThat(n.getId()).isNotNull();
    }

    @Test
    void marcarEnviadaAnotaElIntentoYLaFecha() {
        Notificacion n = nueva();

        n.marcarEnviada(RELOJ_ENVIO);

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(n.getIntentos()).isEqualTo(1);
        assertThat(n.getEnviadaEn()).isEqualTo(ENVIO);
    }

    @Test
    void registrarFalloAnotaElIntentoYElError() {
        Notificacion n = nueva();

        n.registrarFallo("smtp caído");

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(n.getIntentos()).isEqualTo(1);
        assertThat(n.getUltimoError()).isEqualTo("smtp caído");
        assertThat(n.getEnviadaEn()).isNull();
    }

    @Test
    void unEnvioPosteriorLimpiaElErrorAnterior() {
        Notificacion n = nueva();
        n.registrarFallo("smtp caído");

        n.marcarEnviada(RELOJ_ENVIO);

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(n.getIntentos()).isEqualTo(2);
        assertThat(n.getUltimoError()).isNull();
    }

    @Test
    void unErrorMuyLargoSeRecortaParaNoDesbordarLaColumna() {
        Notificacion n = nueva();

        n.registrarFallo("x".repeat(5000));

        assertThat(n.getUltimoError()).hasSize(2000);
    }

    @Test
    void unErrorNuloSeAnotaComoDesconocido() {
        Notificacion n = nueva();

        n.registrarFallo(null);

        assertThat(n.getUltimoError()).isEqualTo("error desconocido");
    }

    @Test
    void sePuedeReintentarMientrasQuedenIntentos() {
        Notificacion n = nueva();
        assertThat(n.reintentable(3)).isTrue();   // pendiente, 0 intentos

        n.registrarFallo("e1");
        n.registrarFallo("e2");
        assertThat(n.reintentable(3)).isTrue();   // fallida, 2 de 3

        n.registrarFallo("e3");
        assertThat(n.reintentable(3)).isFalse();  // agotada
    }

    @Test
    void unaNotificacionEnviadaNoSeReintenta() {
        Notificacion n = nueva();
        n.marcarEnviada(RELOJ_ENVIO);

        assertThat(n.reintentable(5)).isFalse();
    }

    @Test
    void exigeDestinatario() {
        assertThatThrownBy(() -> Notificacion.nueva(UUID.randomUUID(), UUID.randomUUID(),
                TipoNotificacion.RESERVA_CREADA, " ", "{}", RELOJ_CREACION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notificacion.nueva(UUID.randomUUID(), UUID.randomUUID(),
                TipoNotificacion.RESERVA_CREADA, null, "{}", RELOJ_CREACION))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
