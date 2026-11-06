package io.github.aburgos.reservas.infrastructure.persistence;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de los tests de persistencia: PostgreSQL real (no H2), con el esquema creado por Flyway.
 * Si no hay Docker disponible los tests se OMITEN (no fallan); en CI y en máquinas con Docker se ejecutan.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractPersistenceTest {

    /**
     * Contenedor singleton compartido por todas las clases de test de la JVM. No lleva {@code @Container}:
     * con ese ciclo de vida se pararía al acabar cada clase mientras Spring conserva el contexto en caché
     * apuntando a un contenedor muerto. El bloque estático solo se ejecuta si los tests corren.
     */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
