package io.github.aburgos.reservas.infrastructure.messaging;

import io.github.aburgos.reservas.event.Topics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant AHORA = Instant.parse("2026-01-20T03:00:00Z");

    @Mock
    OutboxRepository outbox;

    @Mock
    KafkaTemplate<String, String> kafka;

    private OutboxRelay relay() {
        return new OutboxRelay(outbox, kafka, Clock.fixed(AHORA, ZoneOffset.UTC), 50, 1, 7);
    }

    private static OutboxEvent evento(String tipo, String payload) {
        return new OutboxEvent(UUID.randomUUID(), "Reserva", UUID.randomUUID(), tipo, payload,
                Instant.parse("2026-01-05T09:00:00Z"));
    }

    private static CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> envioOk() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> envioFallido() {
        return CompletableFuture.failedFuture(new IllegalStateException("broker caído"));
    }

    @Test
    void publicaCadaEventoConSuClaveYCabeceraYLoMarcaPublicado() {
        OutboxEvent e = evento("ReservaCreada", "{\"a\":1}");
        when(outbox.bloquearPendientes(50)).thenReturn(List.of(e));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(envioOk());

        relay().publicarPendientes();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> enviado = captor.getValue();
        assertThat(enviado.topic()).isEqualTo(Topics.RESERVAS);
        assertThat(enviado.key()).isEqualTo(e.aggregateId().toString());   // orden por reserva
        assertThat(enviado.value()).isEqualTo("{\"a\":1}");
        Header tipo = enviado.headers().lastHeader(Topics.HEADER_TIPO_EVENTO);
        assertThat(new String(tipo.value(), StandardCharsets.UTF_8)).isEqualTo("ReservaCreada");
        verify(outbox).marcarPublicado(e.id(), AHORA);
    }

    @Test
    void publicaEnOrdenYMarcaCadaEventoDespuesDeEnviarlo() {
        OutboxEvent primero = evento("ReservaCreada", "{\"n\":1}");
        OutboxEvent segundo = evento("ReservaCancelada", "{\"n\":2}");
        when(outbox.bloquearPendientes(50)).thenReturn(List.of(primero, segundo));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(envioOk());

        relay().publicarPendientes();

        InOrder orden = inOrder(kafka, outbox);
        orden.verify(kafka).send(any(ProducerRecord.class));
        orden.verify(outbox).marcarPublicado(primero.id(), AHORA);
        orden.verify(kafka).send(any(ProducerRecord.class));
        orden.verify(outbox).marcarPublicado(segundo.id(), AHORA);
    }

    @Test
    void siFallaUnEnvioNoLoMarcaNiAdelantaLosSiguientes() {
        OutboxEvent primero = evento("ReservaCreada", "{\"n\":1}");
        OutboxEvent segundo = evento("ReservaCancelada", "{\"n\":2}");
        when(outbox.bloquearPendientes(50)).thenReturn(List.of(primero, segundo));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(envioFallido());

        relay().publicarPendientes();

        verify(kafka).send(any(ProducerRecord.class));       // solo el primero: se corta el lote
        verify(outbox, never()).marcarPublicado(any(), any());
    }

    @Test
    void conservaLoYaPublicadoSiUnEventoPosteriorFalla() {
        OutboxEvent primero = evento("ReservaCreada", "{\"n\":1}");
        OutboxEvent segundo = evento("ReservaCancelada", "{\"n\":2}");
        when(outbox.bloquearPendientes(50)).thenReturn(List.of(primero, segundo));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(envioOk(), envioFallido());

        relay().publicarPendientes();

        verify(outbox).marcarPublicado(primero.id(), AHORA);
        verify(outbox, never()).marcarPublicado(segundo.id(), AHORA);
    }

    @Test
    void sinPendientesNoEnviaNada() {
        when(outbox.bloquearPendientes(50)).thenReturn(List.of());

        relay().publicarPendientes();

        verify(kafka, never()).send(any(ProducerRecord.class));
    }

    @Test
    void purgaLosPublicadosConMasDeLaRetencion() {
        relay().purgarPublicados();

        verify(outbox).purgarPublicadosAnterioresA(Instant.parse("2026-01-13T03:00:00Z")); // AHORA - 7 días
    }
}
