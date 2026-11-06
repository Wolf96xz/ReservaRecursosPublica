package io.github.aburgos.reservas.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del servicio como resource server JWT (Keycloak). Sin sesión y sin CSRF: la autenticación
 * viaja en la cabecera {@code Authorization: Bearer}, no en una cookie, así que no hay CSRF que explotar.
 *
 * <p>Roles de realm: {@code CIUDADANO}, {@code GESTOR}, {@code ADMIN}. Las reglas viven aquí, en un único
 * sitio, y no repartidas en anotaciones por los controllers. El usuario de cada reserva sale del claim
 * {@code sub} (el nombre del principal), nunca del cuerpo de la petición (ADR 0003).
 */
@Configuration
public class SecurityConfig {

    public static final String ROL_GESTOR = "GESTOR";
    public static final String ROL_ADMIN = "ADMIN";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/recursos").hasAnyRole(ROL_GESTOR, ROL_ADMIN)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
