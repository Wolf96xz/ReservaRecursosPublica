package io.github.aburgos.reservas.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Aplicación completa contra PostgreSQL real. Si no hay Docker los tests se OMITEN.
 * El reloj está fijado (ver {@link RelojFijoTestConfig}) para que las fechas de los tests no caduquen.
 */
@SpringBootTest
@Import(RelojFijoTestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
}
