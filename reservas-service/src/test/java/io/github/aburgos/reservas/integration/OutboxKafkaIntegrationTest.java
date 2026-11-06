package io.github.aburgos.reservas.integration;

import io.github.aburgos.reservas.application.RecursoService;
import io.github.aburgos.reservas.application.ReservaService;
import io.github.aburgos.reservas.application.command.CancelarReservaCommand;
import io.github.aburgos.reservas.application.command.CrearRecursoCommand;
import io.github.aburgos.reservas.application.command.CrearReservaCommand;
import io.github.aburgos.reservas.domain.exception.ReservaSolapadaException;
import io.github.aburgos.reservas.domain.model.FranjaApertura;
import io.github.aburgos.reservas.domain.model.HorarioApertura;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.domain.model.Reserva;
import io.github.aburgos.reservas.domain.model.TipoRecurso;
import io.github.aburgos.reservas.event.ReservaCancelada;
import io.github.aburgos.reservas.event.ReservaCreada;
import io.github.aburgos.reservas.event.Topics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Outbox de extremo a extremo con PostgreSQL y Kafka reales: la reserva y su evento se confirman juntos,
 * el relay lo publica en el topic y un fallo posterior a la escritura no deja ni reserva ni evento.
 *
 * <p>No se comprueba el orden entre eventos de la misma reserva: el reloj de los tests es fijo, así que
 * comparten {@code creado_en}. Con el reloj real un evento posterior siempre es más reciente.
 */
@TestPropertySource(properties = {
        "reservas.outbox.relay.enabled=true",
        "spring.kafka.admin.auto-create=true",
        "reservas.outbox.relay.intervalo-ms=200"
})
class OutboxKafkaIntegrationTest extends AbstractIntegrationTest {

    // Singleton por JVM, igual que PostgreSQL (ver AbstractPersistenceTest).
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private static final Instant INICIO = Instant.parse("2026-01-12T10:00:00Z"); // lunes, 11:00 locales
    private static final Instant FIN = Instant.parse("2026-01-12T11:00:00Z");

    @Autowired
    ReservaService reservas;

    @Autowired
    RecursoService recursos;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transacciones;

    @Autowired
    JsonMapper json;

    private Recurso nuevoRecurso(String nombre) {
        return recursos.crear(new CrearRecursoCommand(nombre, TipoRecurso.PISTA, null, new HorarioApertura(List.of(
                new FranjaApertura(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(21, 0))))));
    }

    private long filasDeOutbox() {
        return jdbc.sql("SELECT count(*) FROM outbox_event").query(Long.class).single();
    }

    /** Lee del topic (desde el principio) hasta reunir {@code esperados} mensajes con esa clave. */
    private List<ConsumerRecord<String, String>> consumir(String clave, int esperados) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> encontrados = new ArrayList<>();
        long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(Topics.RESERVAS));
            while (encontrados.size() < esperados && System.nanoTime() < limite) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (clave.equals(r.key())) {
                        encontrados.add(r);
                    }
                });
            }
        }
        return encontrados;
    }

    private static String cabeceraTipo(ConsumerRecord<String, String> registro) {
        return new String(registro.headers().lastHeader(Topics.HEADER_TIPO_EVENTO).value(), StandardCharsets.UTF_8);
    }

    @Test
    void crearUnaReservaPublicaReservaCreadaEnKafka() {
        Recurso recurso = nuevoRecurso("Pista outbox 1");

        Reserva reserva = reservas.crear(new CrearReservaCommand(recurso.getId(), "user-1", INICIO, FIN,
                "ana@example.org"));

        List<ConsumerRecord<String, String>> recibidos = consumir(reserva.getId().toString(), 1);
        assertThat(recibidos).hasSize(1);
        ConsumerRecord<String, String> mensaje = recibidos.get(0);
        assertThat(cabeceraTipo(mensaje)).isEqualTo("ReservaCreada");
        ReservaCreada evento = json.readValue(mensaje.value(), ReservaCreada.class);
        assertThat(evento.reservaId()).isEqualTo(reserva.getId());
        assertThat(evento.recursoId()).isEqualTo(recurso.getId());
        assertThat(evento.recursoNombre()).isEqualTo("Pista outbox 1");
        assertThat(evento.zona()).isEqualTo("Europe/Madrid");
        assertThat(evento.usuarioId()).isEqualTo("user-1");
        assertThat(evento.email()).isEqualTo("ana@example.org");
        assertThat(evento.inicio()).isEqualTo(INICIO);
        assertThat(evento.fin()).isEqualTo(FIN);

        // El relay marca la fila como publicada después de que Kafka confirme el envío.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = :id AND publicado_en IS NOT NULL")
                        .param("id", reserva.getId()).query(Long.class).single()).isEqualTo(1L));
    }

    @Test
    void cancelarUnaReservaPublicaReservaCanceladaEnKafka() {
        Recurso recurso = nuevoRecurso("Pista outbox 2");
        Reserva reserva = reservas.crear(new CrearReservaCommand(recurso.getId(), "user-2", INICIO, FIN,
                "bea@example.org"));

        reservas.cancelar(new CancelarReservaCommand(reserva.getId(), "user-2", "bea@example.org"));

        List<ConsumerRecord<String, String>> recibidos = consumir(reserva.getId().toString(), 2);
        assertThat(recibidos).extracting(OutboxKafkaIntegrationTest::cabeceraTipo)
                .containsExactlyInAnyOrder("ReservaCreada", "ReservaCancelada");
        ConsumerRecord<String, String> cancelacion = recibidos.stream()
                .filter(r -> "ReservaCancelada".equals(cabeceraTipo(r))).findFirst().orElseThrow();
        ReservaCancelada evento = json.readValue(cancelacion.value(), ReservaCancelada.class);
        assertThat(evento.reservaId()).isEqualTo(reserva.getId());
        assertThat(evento.email()).isEqualTo("bea@example.org");
        assertThat(evento.recursoNombre()).isEqualTo("Pista outbox 2");
    }

    @Test
    void unaReservaRechazadaPorSolapeNoDejaEventoEnElOutbox() {
        Recurso recurso = nuevoRecurso("Pista outbox 3");
        reservas.crear(new CrearReservaCommand(recurso.getId(), "user-3", INICIO, FIN, "c@example.org"));
        long antes = filasDeOutbox();

        assertThatThrownBy(() -> reservas.crear(
                new CrearReservaCommand(recurso.getId(), "user-4", INICIO, FIN, "d@example.org")))
                .isInstanceOf(ReservaSolapadaException.class);

        assertThat(filasDeOutbox()).isEqualTo(antes);
    }

    @Test
    void siLaTransaccionHaceRollbackNoQuedaNiLaReservaNiElEvento() {
        Recurso recurso = nuevoRecurso("Pista outbox 4");
        long antes = filasDeOutbox();

        // crear() se une a esta transacción: guarda la reserva y su evento, y luego todo se deshace.
        // Demuestra que el INSERT del outbox (JdbcClient) y el de la reserva (JPA) comparten transacción.
        assertThatThrownBy(() -> new TransactionTemplate(transacciones).executeWithoutResult(status -> {
            reservas.crear(new CrearReservaCommand(recurso.getId(), "user-5", INICIO, FIN, "e@example.org"));
            throw new IllegalStateException("fallo después de guardar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(filasDeOutbox()).isEqualTo(antes);
        assertThat(reservas.listarDelUsuario("user-5")).isEmpty();
    }
}
