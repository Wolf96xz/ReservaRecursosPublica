package io.github.aburgos.reservas.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Aplicación completa contra PostgreSQL real. Si no hay Docker los tests se OMITEN.
 * El reloj está fijado (ver {@link RelojFijoTestConfig}) para que las fechas de los tests no caduquen.
 */
@SpringBootTest
@Import(RelojFijoTestConfig.class)
// Sin broker de Kafka en estos tests: ni el relay (reintentaría enviar cada segundo) ni la creación del
// topic al arrancar. Los tests que sí usan Kafka lo reactivan (ver OutboxKafkaIntegrationTest).
@TestPropertySource(properties = {
        "reservas.outbox.relay.enabled=false",
        "spring.kafka.admin.auto-create=false"
})
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractIntegrationTest {

    /** Contenedor singleton por JVM; ver la explicación en AbstractPersistenceTest. */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
