package io.github.aburgos.reservas.infrastructure.messaging;

import io.github.aburgos.reservas.event.ReservaCancelada;
import io.github.aburgos.reservas.event.ReservaCreada;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    private static final Instant AHORA = Instant.parse("2026-01-05T09:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RESERVA_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID RECURSO_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    OutboxRepository outbox;

    private final JsonMapper json = JsonMapper.builder().build();

    private OutboxEventPublisher publisher() {
        return new OutboxEventPublisher(outbox, json, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static ReservaCreada creada(String email) {
        return new ReservaCreada(EVENT_ID, RESERVA_ID, RECURSO_ID, "Pista 1", "Europe/Madrid", "user-1", email,
                Instant.parse("2026-01-12T10:00:00Z"), Instant.parse("2026-01-12T11:00:00Z"), AHORA);
    }

    private OutboxEvent guardado() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).insertar(captor.capture());
        return captor.getValue();
    }

    @Test
    void guardaElEventoConSusMetadatos() {
        publisher().publicar(creada("ana@example.org"));

        OutboxEvent fila = guardado();
        assertThat(fila.id()).isEqualTo(EVENT_ID);               // el eventId es la clave de idempotencia
        assertThat(fila.aggregateType()).isEqualTo("Reserva");
        assertThat(fila.aggregateId()).isEqualTo(RESERVA_ID);    // clave del mensaje Kafka
        assertThat(fila.eventType()).isEqualTo("ReservaCreada");
        assertThat(fila.creadoEn()).isEqualTo(AHORA);
    }

    @Test
    void elTipoDeEventoDeUnaCancelacionEsReservaCancelada() {
        publisher().publicar(new ReservaCancelada(EVENT_ID, RESERVA_ID, RECURSO_ID, "Pista 1", "Europe/Madrid",
                "user-1", null, Instant.parse("2026-01-12T10:00:00Z"), Instant.parse("2026-01-12T11:00:00Z"), AHORA));

        assertThat(guardado().eventType()).isEqualTo("ReservaCancelada");
    }

    @Test
    void elPayloadEsJsonConInstantesIso8601() {
        publisher().publicar(creada("ana@example.org"));

        JsonNode payload = json.readTree(guardado().payload());
        assertThat(payload.get("eventId").asString()).isEqualTo(EVENT_ID.toString());
        assertThat(payload.get("recursoNombre").asString()).isEqualTo("Pista 1");
        assertThat(payload.get("zona").asString()).isEqualTo("Europe/Madrid");
        assertThat(payload.get("email").asString()).isEqualTo("ana@example.org");
        assertThat(payload.get("inicio").asString()).isEqualTo("2026-01-12T10:00:00Z");
        assertThat(payload.get("fin").asString()).isEqualTo("2026-01-12T11:00:00Z");
    }

    @Test
    void elPayloadSePuedeLeerDeVueltaComoElMismoEvento() {
        ReservaCreada original = creada("ana@example.org");

        publisher().publicar(original);

        assertThat(json.readValue(guardado().payload(), ReservaCreada.class)).isEqualTo(original);
    }

    @Test
    void elPayloadConservaElEmailNuloSinFallar() {
        publisher().publicar(creada(null));

        ReservaCreada leido = json.readValue(guardado().payload(), ReservaCreada.class);
        assertThat(leido.email()).isNull();
    }

    @Test
    void exigeUnaTransaccionEnCurso() throws NoSuchMethodException {
        Transactional anotacion = OutboxEventPublisher.class
                .getMethod("publicar", io.github.aburgos.reservas.event.ReservaEvent.class)
                .getAnnotation(Transactional.class);

        assertThat(anotacion).isNotNull();
        assertThat(anotacion.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
