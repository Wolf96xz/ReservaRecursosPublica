package io.github.aburgos.reservas;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Enrutado, seguridad y CORS del gateway contra su cadena real, con un backend falso y JWT simulados
 * (el {@code JwtDecoder} se sustituye; no hay Keycloak, ADR 0008).
 */
@SpringBootTest
@AutoConfigureMockMvc
class GatewayRoutingTest {

    private static final String ORIGEN_FRONT = "http://localhost:4200";
    private static final String TOKEN_VALIDO = "token-valido";

    private static final BackendFalso BACKEND = new BackendFalso();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("gateway.reservas.url", BACKEND::url);
        registry.add("gateway.cors.allowed-origins", () -> ORIGEN_FRONT);
    }

    @AfterAll
    static void cerrarBackend() {
        BACKEND.close();
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void preparar() {
        BACKEND.limpiar();
        Instant ahora = Instant.now();
        Jwt jwt = Jwt.withTokenValue(TOKEN_VALIDO).header("alg", "none").subject("user-1")
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(60)).build();
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("token no válido"));
        // doReturn: con when(...) se ejecutaría la llamada y saltaría el stub genérico de arriba.
        doReturn(jwt).when(jwtDecoder).decode(TOKEN_VALIDO);
    }

    private static String bearer() {
        return "Bearer " + TOKEN_VALIDO;
    }

    @Test
    void sinTokenDevuelve401YNoLlegaAlBackend() throws Exception {
        mvc.perform(get("/api/recursos")).andExpect(status().isUnauthorized());

        assertThat(BACKEND.recibidas()).isEmpty();
    }

    @Test
    void conTokenInvalidoDevuelve401YNoLlegaAlBackend() throws Exception {
        mvc.perform(get("/api/recursos").header("Authorization", "Bearer otro-token"))
                .andExpect(status().isUnauthorized());

        assertThat(BACKEND.recibidas()).isEmpty();
    }

    @Test
    void enrutaRecursosConLaRutaLaQueryYElTokenIntactos() throws Exception {
        mvc.perform(get("/api/recursos/abc?tipo=PISTA").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"origen\":\"backend\"}"));

        assertThat(BACKEND.recibidas()).singleElement().satisfies(p -> {
            assertThat(p.metodo()).isEqualTo("GET");
            assertThat(p.rutaYQuery()).isEqualTo("/api/recursos/abc?tipo=PISTA");
            // El gateway reenvía el token tal cual: reservas-service lo vuelve a validar (ADR 0008).
            assertThat(p.authorization()).isEqualTo(bearer());
        });
    }

    @Test
    void enrutaReservasConElMetodoYElCuerpoIntactos() throws Exception {
        String cuerpo = "{\"recursoId\":\"r1\"}";

        mvc.perform(post("/api/reservas").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());
        mvc.perform(post("/api/reservas/x1/cancelar").header("Authorization", bearer()))
                .andExpect(status().isOk());

        assertThat(BACKEND.recibidas()).hasSize(2);
        assertThat(BACKEND.recibidas().get(0).metodo()).isEqualTo("POST");
        assertThat(BACKEND.recibidas().get(0).rutaYQuery()).isEqualTo("/api/reservas");
        assertThat(BACKEND.recibidas().get(0).cuerpo()).isEqualTo(cuerpo);
        assertThat(BACKEND.recibidas().get(1).rutaYQuery()).isEqualTo("/api/reservas/x1/cancelar");
    }

    @Test
    void unaRutaSinEnrutarDevuelve404SinLlegarAlBackend() throws Exception {
        mvc.perform(get("/api/otra-cosa").header("Authorization", bearer()))
                .andExpect(status().isNotFound());

        assertThat(BACKEND.recibidas()).isEmpty();
    }

    @Test
    void elHealthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void otrosEndpointsDeActuatorExigenToken() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void elPreflightDelOrigenPermitidoSeResuelveSinTokenYSinLlegarAlBackend() throws Exception {
        mvc.perform(options("/api/reservas")
                        .header("Origin", ORIGEN_FRONT)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_FRONT));

        assertThat(BACKEND.recibidas()).isEmpty();
    }

    @Test
    void elPreflightDeOtroOrigenSeRechaza() throws Exception {
        mvc.perform(options("/api/reservas")
                        .header("Origin", "https://malicioso.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());

        assertThat(BACKEND.recibidas()).isEmpty();
    }

    @Test
    void laRespuestaAlOrigenPermitidoLlevaLaCabeceraCors() throws Exception {
        mvc.perform(get("/api/recursos").header("Authorization", bearer()).header("Origin", ORIGEN_FRONT))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_FRONT));
    }
}
