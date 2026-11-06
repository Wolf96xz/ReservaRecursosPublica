package io.github.aburgos.reservas.infrastructure.messaging;

import io.github.aburgos.reservas.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * Reintentos y DLT del consumidor. Boot aplica este {@code CommonErrorHandler} a los listeners.
 *
 * <p>Errores transitorios: 3 reintentos con 1 s de espera y, si siguen fallando, el mensaje va al DLT.
 * Errores que reintentar no arregla ({@link IllegalArgumentException} por tipo desconocido o falta de
 * cabecera, {@link JacksonException} por JSON no válido): directos al DLT. Los fallos de envío del correo
 * no pasan por aquí: se anotan en la notificación y se reintentan aparte (ver {@code NotificacionService}).
 */
@Configuration
public class KafkaConsumerConfig {

    public static final String TOPIC_DLT = Topics.RESERVAS + ".DLT";

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafka) {
        // Publica en "<topic>.DLT" y en la misma partición que el original, por eso el DLT tiene las mismas.
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafka), new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class, JacksonException.class);
        return handler;
    }

    @Bean
    NewTopic topicReservasDlt() {
        return TopicBuilder.name(TOPIC_DLT).partitions(3).replicas(1).build();
    }
}
