package io.github.aburgos.reservas.integration;

import io.github.aburgos.reservas.application.NotificacionService;
import io.github.aburgos.reservas.application.port.NotificationSender;
import io.github.aburgos.reservas.domain.EstadoNotificacion;
import io.github.aburgos.reservas.domain.Notificacion;
import io.github.aburgos.reservas.event.ReservaCancelada;
import io.github.aburgos.reservas.event.ReservaCreada;
import io.github.aburgos.reservas.event.ReservaEvent;
import io.github.aburgos.reservas.event.Topics;
import io.github.aburgos.reservas.infrastructure.messaging.KafkaConsumerConfig;
import io.github.aburgos.reservas.infrastructure.persistence.EventoProcesadoRepository;
import io.github.aburgos.reservas.infrastructure.persistence.NotificacionRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * El consumidor de extremo a extremo: mensajes reales en Kafka, base de datos real. El envío de correo se
 * sustituye por un registrador (no hay SMTP), pero todo lo demás es el servicio verdadero.
 *
 * <p>Los mensajes que deben llegar en orden comparten clave (la misma reserva), porque Kafka solo garantiza
 * el orden dentro de una partición.
 */
@TestPropertySource(properties = {
        // Los reintentos se disparan a mano en el test; aquí se desactiva el planificador y el margen.
        "notificaciones.reintentos.enabled=false",
        "notificaciones.reintentos.margen-segundos=0"
})
class NotificacionesKafkaIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        RegistradorDeEnvios registrador() {
            return new RegistradorDeEnvios();
        }

        /** En producción lo crea reservas-service; aquí no hay productor, así que se declara. */
        @Bean
        NewTopic topicReservas() {
            return TopicBuilder.name(Topics.RESERVAS).partitions(3).replicas(1).build();
        }
    }

    /** Sustituye al envío por SMTP: anota los eventos y puede simular un servidor caído. */
    static class RegistradorDeEnvios implements NotificationSender {

        final List<ReservaEvent> enviados = new CopyOnWriteArrayList<>();
        final AtomicBoolean fallar = new AtomicBoolean(false);

        @Override
        public void enviar(ReservaEvent evento) {
            if (fallar.get()) {
                throw new IllegalStateException("smtp caído");
            }
            enviados.add(evento);
        }

        long vecesEnviado(UUID eventId) {
            return enviados.stream().filter(e -> e.eventId().equals(eventId)).count();
        }
    }

    private static final Instant INICIO = Instant.parse("2026-01-12T10:00:00Z");
    private static final Instant FIN = Instant.parse("2026-01-12T11:00:00Z");

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    NotificacionRepository notificaciones;

    @Autowired
    EventoProcesadoRepository eventosProcesados;

    @Autowired
    NotificacionService service;

    @Autowired
    RegistradorDeEnvios registrador;

    @Autowired
    JsonMapper json;

    @BeforeEach
    void servidorDeCorreoDisponible() {
        registrador.fallar.set(false);
    }

    private static ReservaCreada creada(UUID reservaId, String email) {
        return new ReservaCreada(UUID.randomUUID(), reservaId, UUID.randomUUID(), "Pista 1", "Europe/Madrid",
                "user-1", email, INICIO, FIN, Instant.parse("2026-01-05T09:00:00Z"));
    }

    private void publicar(ReservaEvent evento) throws Exception {
        publicar(evento.reservaId().toString(), evento.tipo(), json.writeValueAsString(evento));
    }

    private void publicar(String clave, String tipo, String cuerpo) throws Exception {
        ProducerRecord<String, String> registro = new ProducerRecord<>(Topics.RESERVAS, clave, cuerpo);
        registro.headers().add(Topics.HEADER_TIPO_EVENTO, tipo.getBytes(StandardCharsets.UTF_8));
        kafka.send(registro).get();
    }

    private void esperarEstado(UUID eventId, EstadoNotificacion estado) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notificaciones.findByEventId(eventId)).get()
                        .extracting(Notificacion::getEstado).isEqualTo(estado));
    }

    /** Lee del DLT (desde el principio) hasta encontrar un mensaje con esa clave. */
    private ConsumerRecord<String, String> esperarEnElDlt(String clave) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-dlt-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(KafkaConsumerConfig.TOPIC_DLT));
            while (System.nanoTime() < limite) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    if (clave.equals(r.key())) {
                        return r;
                    }
                }
            }
        }
        throw new AssertionError("No llegó al DLT ningún mensaje con la clave " + clave);
    }

    // --- flujo normal ---

    @Test
    void unEventoDeKafkaGeneraUnaNotificacionEnviada() throws Exception {
        ReservaCreada evento = creada(UUID.randomUUID(), "ana@example.org");

        publicar(evento);

        esperarEstado(evento.eventId(), EstadoNotificacion.ENVIADA);
        Notificacion n = notificaciones.findByEventId(evento.eventId()).orElseThrow();
        assertThat(n.getReservaId()).isEqualTo(evento.reservaId());
        assertThat(n.getDestinatario()).isEqualTo("ana@example.org");
        assertThat(n.getIntentos()).isEqualTo(1);
        assertThat(registrador.vecesEnviado(evento.eventId())).isEqualTo(1);
        assertThat(eventosProcesados.existsById(evento.eventId())).isTrue();
    }

    @Test
    void unaCancelacionTambienSeNotifica() throws Exception {
        UUID reservaId = UUID.randomUUID();
        ReservaCancelada evento = new ReservaCancelada(UUID.randomUUID(), reservaId, UUID.randomUUID(), "Pista 1",
                "Europe/Madrid", "user-1", "ana@example.org", INICIO, FIN, Instant.parse("2026-01-06T09:00:00Z"));

        publicar(evento);

        esperarEstado(evento.eventId(), EstadoNotificacion.ENVIADA);
        assertThat(registrador.vecesEnviado(evento.eventId())).isEqualTo(1);
    }

    // --- idempotencia ---

    @Test
    void unEventoEntregadoVariasVecesSoloSeNotificaUnaVez() throws Exception {
        UUID reservaId = UUID.randomUUID();
        ReservaCreada duplicado = creada(reservaId, "ana@example.org");
        ReservaCreada despues = creada(reservaId, "ana@example.org");   // misma clave: misma partición, en orden

        publicar(duplicado);
        publicar(duplicado);
        publicar(duplicado);
        publicar(despues);

        // Cuando se ha procesado el último, los duplicados anteriores (misma partición) ya se consumieron.
        esperarEstado(despues.eventId(), EstadoNotificacion.ENVIADA);
        assertThat(registrador.vecesEnviado(duplicado.eventId())).isEqualTo(1);
        assertThat(notificaciones.findAll().stream()
                .filter(n -> n.getEventId().equals(duplicado.eventId()))).hasSize(1);
    }

    @Test
    void unEventoSinEmailSeDaPorProcesadoSinCrearNotificacion() throws Exception {
        UUID reservaId = UUID.randomUUID();
        ReservaCreada sinEmail = creada(reservaId, null);
        ReservaCreada despues = creada(reservaId, "ana@example.org");

        publicar(sinEmail);
        publicar(despues);

        esperarEstado(despues.eventId(), EstadoNotificacion.ENVIADA);
        assertThat(eventosProcesados.existsById(sinEmail.eventId())).isTrue();
        assertThat(notificaciones.findByEventId(sinEmail.eventId())).isEmpty();
        assertThat(registrador.vecesEnviado(sinEmail.eventId())).isZero();
    }

    // --- mensajes ilegibles: DLT ---

    @Test
    void unJsonRotoVaAlDltYElConsumidorSigueFuncionando() throws Exception {
        UUID reservaId = UUID.randomUUID();
        String clave = reservaId.toString();
        ReservaCreada valido = creada(reservaId, "ana@example.org");

        publicar(clave, "ReservaCreada", "{esto no es json");
        publicar(valido);

        ConsumerRecord<String, String> enDlt = esperarEnElDlt(clave);
        assertThat(enDlt.value()).isEqualTo("{esto no es json");
        assertThat(enDlt.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        // El mensaje venenoso no bloquea la partición: el siguiente, válido, se procesa.
        esperarEstado(valido.eventId(), EstadoNotificacion.ENVIADA);
    }

    @Test
    void unTipoDeEventoDesconocidoVaAlDlt() throws Exception {
        String clave = UUID.randomUUID().toString();

        publicar(clave, "ReservaBorrada", "{}");

        ConsumerRecord<String, String> enDlt = esperarEnElDlt(clave);
        assertThat(enDlt.value()).isEqualTo("{}");
    }

    // --- fallos de envío y reintento ---

    @Test
    void siFallaElEnvioQuedaFallidaYUnReintentoPosteriorLaEnvia() throws Exception {
        registrador.fallar.set(true);
        ReservaCreada evento = creada(UUID.randomUUID(), "ana@example.org");

        publicar(evento);

        esperarEstado(evento.eventId(), EstadoNotificacion.FALLIDA);
        Notificacion fallida = notificaciones.findByEventId(evento.eventId()).orElseThrow();
        assertThat(fallida.getIntentos()).isEqualTo(1);
        assertThat(fallida.getUltimoError()).contains("smtp caído");
        assertThat(registrador.vecesEnviado(evento.eventId())).isZero();

        // El servidor de correo vuelve. El evento ya consta como procesado y Kafka no lo reentregará:
        // el reintento sale de la notificación guardada, no de Kafka.
        registrador.fallar.set(false);
        service.reintentarPendientes();

        Notificacion enviada = notificaciones.findByEventId(evento.eventId()).orElseThrow();
        assertThat(enviada.getEstado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(enviada.getIntentos()).isEqualTo(2);
        assertThat(enviada.getUltimoError()).isNull();
        assertThat(registrador.vecesEnviado(evento.eventId())).isEqualTo(1);
    }
}
