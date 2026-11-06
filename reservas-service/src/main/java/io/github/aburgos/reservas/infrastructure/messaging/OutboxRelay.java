package io.github.aburgos.reservas.infrastructure.messaging;

import io.github.aburgos.reservas.event.Topics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publica en Kafka los eventos pendientes del outbox. Entrega <b>al menos una vez</b>: si el proceso cae
 * entre el envío y el commit, el evento se reenvía (el consumidor es idempotente por {@code eventId}).
 *
 * <p>Se publica en orden y de forma síncrona: ante el primer fallo se corta el lote y se reintenta en la
 * siguiente pasada, para no adelantar eventos posteriores de la misma reserva. Lo ya publicado en esa pasada
 * se confirma. Se puede desactivar con {@code reservas.outbox.relay.enabled=false} (p. ej. en tests sin Kafka).
 */
@Component
@ConditionalOnProperty(name = "reservas.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final int lote;
    private final long timeoutEnvioSegundos;
    private final Duration retencion;

    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, String> kafka,
                       Clock clock,
                       @Value("${reservas.outbox.relay.lote:50}") int lote,
                       @Value("${reservas.outbox.relay.timeout-envio-segundos:10}") long timeoutEnvioSegundos,
                       @Value("${reservas.outbox.retencion-dias:7}") long retencionDias) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.clock = clock;
        this.lote = lote;
        this.timeoutEnvioSegundos = timeoutEnvioSegundos;
        this.retencion = Duration.ofDays(retencionDias);
    }

    @Scheduled(fixedDelayString = "${reservas.outbox.relay.intervalo-ms:1000}")
    @Transactional
    public void publicarPendientes() {
        List<OutboxEvent> pendientes = outbox.bloquearPendientes(lote);
        for (OutboxEvent evento : pendientes) {
            if (!enviar(evento)) {
                return;
            }
            outbox.marcarPublicado(evento.id(), clock.instant());
        }
    }

    /** Purga diaria de lo ya publicado: el payload puede contener el email, que es dato personal. */
    @Scheduled(cron = "${reservas.outbox.purga.cron:0 0 3 * * *}")
    @Transactional
    public void purgarPublicados() {
        int borrados = outbox.purgarPublicadosAnterioresA(clock.instant().minus(retencion));
        if (borrados > 0) {
            log.info("Outbox: purgados {} eventos publicados con más de {} días", borrados, retencion.toDays());
        }
    }

    private boolean enviar(OutboxEvent evento) {
        ProducerRecord<String, String> registro =
                new ProducerRecord<>(Topics.RESERVAS, evento.aggregateId().toString(), evento.payload());
        registro.headers().add(Topics.HEADER_TIPO_EVENTO, evento.eventType().getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(registro).get(timeoutEnvioSegundos, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Outbox: no se pudo publicar el evento {} ({}); se reintentará: {}",
                    evento.id(), evento.eventType(), e.toString());
            return false;
        }
    }
}
