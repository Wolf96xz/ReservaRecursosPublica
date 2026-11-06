package io.github.aburgos.reservas.infrastructure.messaging;

import io.github.aburgos.reservas.application.NotificacionService;
import io.github.aburgos.reservas.event.ReservaCancelada;
import io.github.aburgos.reservas.event.ReservaCreada;
import io.github.aburgos.reservas.event.ReservaEvent;
import io.github.aburgos.reservas.event.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Consume los eventos de reserva. El tipo viaja en una cabecera ({@link Topics#HEADER_TIPO_EVENTO}) y el
 * cuerpo es JSON. Un mensaje que no se puede interpretar (sin cabecera, tipo desconocido, JSON roto) lanza
 * una excepción que el {@code DefaultErrorHandler} envía directamente al topic de mensajes fallidos (DLT),
 * sin reintentos: reintentar no lo arreglaría. Los errores transitorios (p. ej. la base de datos caída) sí
 * se reintentan antes de ir al DLT. Ver {@link KafkaConsumerConfig}.
 */
@Component
public class ReservaEventListener {

    private final NotificacionService service;
    private final JsonMapper json;

    public ReservaEventListener(NotificacionService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    @KafkaListener(topics = Topics.RESERVAS, groupId = "${spring.kafka.consumer.group-id:notificaciones-service}")
    public void alRecibir(ConsumerRecord<String, String> registro) {
        service.procesar(deserializar(registro));
    }

    ReservaEvent deserializar(ConsumerRecord<String, String> registro) {
        Header cabecera = registro.headers().lastHeader(Topics.HEADER_TIPO_EVENTO);
        if (cabecera == null) {
            throw new IllegalArgumentException("Mensaje sin cabecera " + Topics.HEADER_TIPO_EVENTO);
        }
        String tipo = new String(cabecera.value(), StandardCharsets.UTF_8);
        Class<? extends ReservaEvent> clase = switch (tipo) {
            case "ReservaCreada" -> ReservaCreada.class;
            case "ReservaCancelada" -> ReservaCancelada.class;
            default -> throw new IllegalArgumentException("Tipo de evento desconocido: " + tipo);
        };
        return json.readValue(registro.value(), clase);
    }
}
