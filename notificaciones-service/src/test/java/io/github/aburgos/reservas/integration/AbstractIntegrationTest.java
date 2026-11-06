package io.github.aburgos.reservas.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Servicio completo contra PostgreSQL y Kafka reales. Si no hay Docker los tests se OMITEN (no fallan).
 * Contenedores singleton por JVM (ver la explicación en AbstractPersistenceTest); se arrancan en el bloque
 * estático, que solo se ejecuta si los tests corren.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
