package io.github.aburgos.reservas.application;

import io.github.aburgos.reservas.application.port.NotificationSender;
import io.github.aburgos.reservas.domain.EstadoNotificacion;
import io.github.aburgos.reservas.domain.Notificacion;
import io.github.aburgos.reservas.domain.TipoNotificacion;
import io.github.aburgos.reservas.event.ReservaCancelada;
import io.github.aburgos.reservas.event.ReservaCreada;
import io.github.aburgos.reservas.event.ReservaEvent;
import io.github.aburgos.reservas.infrastructure.persistence.EventoProcesadoRepository;
import io.github.aburgos.reservas.infrastructure.persistence.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-01-05T09:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final int MAX_INTENTOS = 5;

    @Mock
    NotificacionRepository notificaciones;

    @Mock
    EventoProcesadoRepository eventosProcesados;

    @Mock
    NotificationSender sender;

    @Mock
    PlatformTransactionManager transacciones;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void transaccionesSimuladas() {
        when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private NotificacionService service() {
        return new NotificacionService(notificaciones, eventosProcesados, sender, json,
                new TransactionTemplate(transacciones), RELOJ, MAX_INTENTOS, 30, 50);
    }

    private static ReservaCreada creada(String email) {
        return new ReservaCreada(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Pista 1",
                "Europe/Madrid", "user-1", email, Instant.parse("2026-01-12T10:00:00Z"),
                Instant.parse("2026-01-12T11:00:00Z"), AHORA);
    }

    /** Simula la base de datos: lo que se guarda es lo que luego devuelve findById. */
    private AtomicReference<Notificacion> baseDeDatosConUnaNotificacion() {
        AtomicReference<Notificacion> guardada = new AtomicReference<>();
        when(notificaciones.save(any(Notificacion.class))).thenAnswer(i -> {
            guardada.set(i.getArgument(0));
            return i.getArgument(0);
        });
        when(notificaciones.findById(any())).thenAnswer(i -> Optional.ofNullable(guardada.get()));
        return guardada;
    }

    // --- idempotencia ---

    @Test
    void unEventoNuevoSeRegistraSeEnviaYQuedaEnviado() {
        ReservaCreada evento = creada("ana@example.org");
        when(eventosProcesados.insertarSiNoExiste(evento.eventId(), AHORA)).thenReturn(1);
        AtomicReference<Notificacion> guardada = baseDeDatosConUnaNotificacion();

        service().procesar(evento);

        verify(sender).enviar(evento);
        Notificacion n = guardada.get();
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(n.getIntentos()).isEqualTo(1);
        assertThat(n.getEnviadaEn()).isEqualTo(AHORA);
        assertThat(n.getEventId()).isEqualTo(evento.eventId());
        assertThat(n.getReservaId()).isEqualTo(evento.reservaId());
        assertThat(n.getTipo()).isEqualTo(TipoNotificacion.RESERVA_CREADA);
        assertThat(n.getDestinatario()).isEqualTo("ana@example.org");
    }

    @Test
    void unEventoDuplicadoNoCreaNotificacionNiEnviaNada() {
        ReservaCreada evento = creada("ana@example.org");
        when(eventosProcesados.insertarSiNoExiste(evento.eventId(), AHORA)).thenReturn(0); // ya existía

        service().procesar(evento);

        verify(notificaciones, never()).save(any());
        verify(sender, never()).enviar(any());
    }

    @Test
    void elMismoEventoEntregadoDosVecesSoloSeEnviaUnaVez() {
        ReservaCreada evento = creada("ana@example.org");
        // La primera entrega inserta (1); la segunda ve que ya existe (0): es lo que haría la PK.
        when(eventosProcesados.insertarSiNoExiste(evento.eventId(), AHORA)).thenReturn(1, 0);
        baseDeDatosConUnaNotificacion();
        NotificacionService service = service();

        service.procesar(evento);
        service.procesar(evento);

        verify(sender).enviar(evento);   // exactamente una vez
    }

    @Test
    void unEventoSinEmailSeMarcaProcesadoPeroNoGeneraNotificacion() {
        ReservaCreada evento = creada(null);
        when(eventosProcesados.insertarSiNoExiste(evento.eventId(), AHORA)).thenReturn(1);

        service().procesar(evento);

        verify(eventosProcesados).insertarSiNoExiste(evento.eventId(), AHORA);   // consta como visto
        verify(notificaciones, never()).save(any());
        verify(sender, never()).enviar(any());
    }

    @Test
    void unEmailEnBlancoSeTrataComoAusente() {
        ReservaCreada evento = creada("   ");
        when(eventosProcesados.insertarSiNoExiste(evento.eventId(), AHORA)).thenReturn(1);

        service().procesar(evento);

        verify(notificaciones, never()).save(any());
        verify(sender, never()).enviar(any());
    }

    @Test
    void unaCancelacionGeneraUnaNotificacionDeTipoCancelada() {
        ReservaCancelada evento = new ReservaCancelada(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Pista 1", "Europe/Madrid", "user-1", "ana@example.org",
                Instant.parse("2026-01-12T10:00:00Z"), Instant.parse("2026-01-12T11:00:00Z"), AHORA);
        when(eventosProcesados.insertarSiNoExiste(evento.eventId(), AHORA)).thenReturn(1);
        AtomicReference<Notificacion> guardada = baseDeDatosConUnaNotificacion();

        service().procesar(evento);

        assertThat(guardada.get().getTipo()).isEqualTo(TipoNotificacion.RESERVA_CANCELADA);
        verify(sender).enviar(evento);
    }

    // --- fallos de envío ---

    @Test
    void siFallaElEnvioSeAnotaElFalloYNoSePropagaAlConsumidor() {
        ReservaCreada evento = creada("ana@example.org");
        when(eventosProcesados.insertarSiNoExiste(evento.eventId(), AHORA)).thenReturn(1);
        AtomicReference<Notificacion> guardada = baseDeDatosConUnaNotificacion();
        doThrow(new IllegalStateException("smtp caído")).when(sender).enviar(any());

        assertThatCode(() -> service().procesar(evento)).doesNotThrowAnyException();

        Notificacion n = guardada.get();
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(n.getIntentos()).isEqualTo(1);
        assertThat(n.getUltimoError()).contains("smtp caído");
        assertThat(n.getEnviadaEn()).isNull();
    }

    // --- reintentos ---

    private Notificacion fallida(ReservaEvent evento, int fallos) {
        Notificacion n = Notificacion.nueva(evento.eventId(), evento.reservaId(), TipoNotificacion.RESERVA_CREADA,
                evento.email(), json.writeValueAsString(evento), RELOJ);
        for (int i = 0; i < fallos; i++) {
            n.registrarFallo("smtp caído");
        }
        return n;
    }

    @Test
    void reintentarEnviaLasFallidasRehaciendoElEventoDesdeElPayloadGuardado() {
        ReservaCreada evento = creada("ana@example.org");
        Notificacion fallida = fallida(evento, 1);
        when(notificaciones.idsReintentables(MAX_INTENTOS, AHORA.minusSeconds(30), PageRequest.of(0, 50)))
                .thenReturn(List.of(fallida.getId()));
        when(notificaciones.findById(fallida.getId())).thenReturn(Optional.of(fallida));

        int tocadas = service().reintentarPendientes();

        assertThat(tocadas).isEqualTo(1);
        verify(sender).enviar(evento);   // el mismo evento, reconstruido del JSON guardado
        assertThat(fallida.getEstado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(fallida.getIntentos()).isEqualTo(2);
        assertThat(fallida.getUltimoError()).isNull();
    }

    @Test
    void reintentarSinPendientesNoHaceNada() {
        when(notificaciones.idsReintentables(MAX_INTENTOS, AHORA.minusSeconds(30), PageRequest.of(0, 50)))
                .thenReturn(List.of());

        assertThat(service().reintentarPendientes()).isZero();

        verify(sender, never()).enviar(any());
    }

    @Test
    void siElReintentoVuelveAFallarSumaOtroIntento() {
        ReservaCreada evento = creada("ana@example.org");
        Notificacion fallida = fallida(evento, 2);
        when(notificaciones.idsReintentables(MAX_INTENTOS, AHORA.minusSeconds(30), PageRequest.of(0, 50)))
                .thenReturn(List.of(fallida.getId()));
        when(notificaciones.findById(fallida.getId())).thenReturn(Optional.of(fallida));
        doThrow(new IllegalStateException("sigue caído")).when(sender).enviar(any());

        service().reintentarPendientes();

        assertThat(fallida.getEstado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(fallida.getIntentos()).isEqualTo(3);
        assertThat(fallida.getUltimoError()).contains("sigue caído");
    }

    @Test
    void unaNotificacionConLosIntentosAgotadosNoSeReintenta() {
        ReservaCreada evento = creada("ana@example.org");
        Notificacion agotada = fallida(evento, MAX_INTENTOS);
        when(notificaciones.idsReintentables(MAX_INTENTOS, AHORA.minusSeconds(30), PageRequest.of(0, 50)))
                .thenReturn(List.of(agotada.getId()));
        when(notificaciones.findById(agotada.getId())).thenReturn(Optional.of(agotada));

        service().reintentarPendientes();

        verify(sender, never()).enviar(any());
        assertThat(agotada.getIntentos()).isEqualTo(MAX_INTENTOS);
    }

    @Test
    void unPayloadIlegibleSeAnotaComoFalloSinRomperElReintento() {
        ReservaCreada evento = creada("ana@example.org");
        Notificacion corrupta = Notificacion.nueva(evento.eventId(), evento.reservaId(),
                TipoNotificacion.RESERVA_CREADA, "ana@example.org", "{esto no es json", RELOJ);
        when(notificaciones.idsReintentables(MAX_INTENTOS, AHORA.minusSeconds(30), PageRequest.of(0, 50)))
                .thenReturn(List.of(corrupta.getId()));
        when(notificaciones.findById(corrupta.getId())).thenReturn(Optional.of(corrupta));

        assertThatCode(() -> service().reintentarPendientes()).doesNotThrowAnyException();

        assertThat(corrupta.getEstado()).isEqualTo(EstadoNotificacion.FALLIDA);
        verify(sender, never()).enviar(any());
    }
}
