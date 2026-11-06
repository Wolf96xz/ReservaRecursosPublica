package io.github.aburgos.reservas;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Preparación común de los tests del gateway: cadena real de seguridad y de filtros, un backend falso en
 * lugar de {@code reservas-service} y un {@code JwtDecoder} simulado (no hay Keycloak, ADR 0008).
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class GatewayTestBase {

    protected static final String ORIGEN_FRONT = "http://localhost:4200";
    protected static final String TOKEN_VALIDO = "token-valido";

    /**
     * Compartido por todas las clases de test: Spring reutiliza el contexto entre ellas y este conserva la
     * URL del primer backend, así que no se puede cerrar al terminar una clase, solo al acabar la JVM.
     */
    protected static final BackendFalso BACKEND = new BackendFalso();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(BACKEND::close));
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("gateway.reservas.url", BACKEND::url);
        registry.add("gateway.cors.allowed-origins", () -> ORIGEN_FRONT);
    }

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void prepararBackendYToken() {
        BACKEND.limpiar();
        Instant ahora = Instant.now();
        Jwt jwt = Jwt.withTokenValue(TOKEN_VALIDO).header("alg", "none").subject("user-1")
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(60)).build();
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("token no válido"));
        // doReturn: con when(...) se ejecutaría la llamada y saltaría el stub genérico de arriba.
        doReturn(jwt).when(jwtDecoder).decode(TOKEN_VALIDO);
    }

    protected static String bearer() {
        return "Bearer " + TOKEN_VALIDO;
    }
}
