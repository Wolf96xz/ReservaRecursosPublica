package io.github.aburgos.reservas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Enrutado por prefijo hacia {@code reservas-service} (ADR 0008): los endpoints nuevos de ese servicio
 * no obligan a tocar el gateway. Lo que no encaja con ninguna ruta responde 404 sin llegar al backend.
 */
@Configuration
public class RoutesConfig {

    @Bean
    RouterFunction<ServerResponse> reservasRoutes(@Value("${gateway.reservas.url}") String reservasUrl) {
        return route("reservas-service")
                .route(path("/api/recursos/**").or(path("/api/reservas/**")), http())
                .before(uri(reservasUrl))
                .build();
    }
}
