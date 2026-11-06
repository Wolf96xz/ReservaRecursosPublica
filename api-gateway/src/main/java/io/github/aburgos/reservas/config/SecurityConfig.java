package io.github.aburgos.reservas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * El gateway valida el JWT (firma, emisor y caducidad) y rechaza con 401 lo que no lo cumpla, sin llegar al
 * backend. Después reenvía el token tal cual: {@code reservas-service} lo vuelve a validar y es quien aplica
 * los roles (ADR 0006 y 0008), así que aquí no se duplican. Sin sesión y sin CSRF: la autenticación viaja
 * en {@code Authorization: Bearer}, no en una cookie.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // Usa el CorsConfigurationSource de CorsConfig; atiende el preflight antes de pedir token.
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
