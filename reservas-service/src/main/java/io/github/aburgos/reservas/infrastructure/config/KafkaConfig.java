package io.github.aburgos.reservas.infrastructure.config;

import io.github.aburgos.reservas.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Topic de eventos de reserva y planificación del {@code OutboxRelay}. */
@Configuration
@EnableScheduling
public class KafkaConfig {

    /**
     * Varias particiones para poder escalar consumidores; el orden se conserva por reserva porque la clave
     * del mensaje es el id de la reserva. Una sola réplica: entorno local.
     */
    @Bean
    NewTopic topicReservas() {
        return TopicBuilder.name(Topics.RESERVAS).partitions(3).replicas(1).build();
    }
}
