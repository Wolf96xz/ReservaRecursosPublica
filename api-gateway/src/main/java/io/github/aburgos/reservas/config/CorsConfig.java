package io.github.aburgos.reservas.config;

import io.github.aburgos.reservas.filter.CorrelationIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS en el gateway, no en los servicios: el navegador solo habla con él (ADR 0008). Los orígenes son
 * configurables y nunca {@code *}. Sin credenciales: el token va en la cabecera, no en una cookie.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${gateway.cors.allowed-origins}") List<String> origenesPermitidos) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origenesPermitidos);
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", CorrelationIdFilter.HEADER));
        // Sin esto el front no puede leer el id de correlación de la respuesta.
        cors.setExposedHeaders(List.of(CorrelationIdFilter.HEADER));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
