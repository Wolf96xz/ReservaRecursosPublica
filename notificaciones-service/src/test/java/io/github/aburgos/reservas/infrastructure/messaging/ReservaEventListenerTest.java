package io.github.aburgos.reservas.infrastructure.messaging;

import io.github.aburgos.reservas.application.NotificacionService;
import io.github.aburgos.reservas.event.ReservaCancelada;
import io.github.aburgos.reservas.event.ReservaCreada;
import io.github.aburgos.reservas.event.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservaEventListenerTest {

    private static final Instant AHORA = Instant.parse("2026-01-05T09:00:00Z");

    @Mock
    NotificacionService service;

    private final JsonMapper json = JsonMapper.builder().build();

    private ReservaEventListener listener() {
        return new ReservaEventListener(service, json);
    }

    private static ConsumerRecord<String, String> registro(String tipo, String cuerpo) {
        ConsumerRecord<String, String> r = new ConsumerRecord<>(Topics.RESERVAS, 0, 0L, "clave", cuerpo);
        if (tipo != null) {
            r.headers().add(Topics.HEADER_TIPO_EVENTO, tipo.getBytes(StandardCharsets.UTF_8));
        }
        return r;
    }

    private static ReservaCreada creada() {
        return new ReservaCreada(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Pista 1",
                "Europe/Madrid", "user-1", "ana@example.org", Instant.parse("2026-01-12T10:00:00Z"),
                Instant.parse("2026-01-12T11:00:00Z"), AHORA);
    }

    @Test
    void unaReservaCreadaSeDeserializaYSeEntregaAlServicio() {
        ReservaCreada evento = creada();

        listener().alRecibir(registro("ReservaCreada", json.writeValueAsString(evento)));

        verify(service).procesar(evento);
    }

    @Test
    void unaReservaCanceladaSeDeserializaPorSuTipo() {
        ReservaCancelada evento = new ReservaCancelada(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Pista 1", "Europe/Madrid", "user-1", "ana@example.org",
                Instant.parse("2026-01-12T10:00:00Z"), Instant.parse("2026-01-12T11:00:00Z"), AHORA);

        listener().alRecibir(registro("ReservaCancelada", json.writeValueAsString(evento)));

        verify(service).procesar(evento);
    }

    @Test
    void unMensajeSinCabeceraDeTipoSeRechazaParaIrAlDlt() {
        assertThatThrownBy(() -> listener().alRecibir(registro(null, json.writeValueAsString(creada()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Topics.HEADER_TIPO_EVENTO);
        verifyNoInteractions(service);
    }

    @Test
    void unTipoDesconocidoSeRechazaParaIrAlDlt() {
        assertThatThrownBy(() -> listener().alRecibir(registro("ReservaBorrada", "{}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ReservaBorrada");
        verifyNoInteractions(service);
    }

    @Test
    void unJsonRotoSeRechazaParaIrAlDlt() {
        assertThatThrownBy(() -> listener().alRecibir(registro("ReservaCreada", "{no es json")))
                .isInstanceOf(JacksonException.class);
        verifyNoInteractions(service);
    }
}
