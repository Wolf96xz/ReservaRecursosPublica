package io.github.aburgos.reservas.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Reloj fijo para los tests de integración: "hoy" es el lunes 2026-01-05, una semana antes de las franjas usadas. */
@TestConfiguration(proxyBeanMethods = false)
class RelojFijoTestConfig {

    static final Instant AHORA = Instant.parse("2026-01-05T09:00:00Z");

    @Bean
    @Primary
    Clock relojFijo() {
        return Clock.fixed(AHORA, ZoneOffset.UTC);
    }
}
