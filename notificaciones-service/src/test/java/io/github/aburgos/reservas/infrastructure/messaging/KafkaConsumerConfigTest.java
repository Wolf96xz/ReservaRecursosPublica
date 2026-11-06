package io.github.aburgos.reservas.infrastructure.messaging;

import io.github.aburgos.reservas.event.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import tools.jackson.core.JacksonException;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    @Test
    void elTopicDeMensajesFallidosEsElDeReservasConSufijoDlt() {
        assertThat(KafkaConsumerConfig.TOPIC_DLT).isEqualTo(Topics.RESERVAS + ".DLT");
    }

    @Test
    void losMensajesFallidosVanAlDltEnLaMismaParticionQueElOriginal() {
        ConsumerRecord<String, String> original = new ConsumerRecord<>(Topics.RESERVAS, 2, 15L, "clave", "valor");

        TopicPartition destino = KafkaConsumerConfig.destinoDlt(original, new IllegalArgumentException("x"));

        // Regresión: el nombre por defecto de Spring Kafka es "<topic>-dlt", distinto del topic que se crea.
        assertThat(destino.topic()).isEqualTo(KafkaConsumerConfig.TOPIC_DLT);
        assertThat(destino.partition()).isEqualTo(2);
    }

    @Test
    void elDltTieneTantasParticionesComoElTopicDeOrigen() {
        var dlt = new KafkaConsumerConfig().topicReservasDlt();

        assertThat(dlt.name()).isEqualTo(KafkaConsumerConfig.TOPIC_DLT);
        assertThat(dlt.numPartitions()).isEqualTo(3);
    }

    @Test
    void losErroresDeInterpretacionNoSeReintentan() {
        DefaultErrorHandler handler = new KafkaConsumerConfig().errorHandler(Mockito.mock(KafkaTemplate.class));

        // removeClassification devuelve la clasificación registrada (false = no reintentar, null = sin
        // clasificar, es decir, se reintenta por defecto). Un mensaje ilegible no mejora por esperar: va
        // directo al DLT. Solo se consulta una vez por excepción porque la llamada la elimina.
        assertThat(handler.removeClassification(IllegalArgumentException.class)).isFalse();
        assertThat(handler.removeClassification(JacksonException.class)).isFalse();
        // Un error transitorio (p. ej. la base de datos caída) no está en la lista: se reintenta antes del DLT.
        assertThat(handler.removeClassification(IllegalStateException.class)).isNull();
    }
}
