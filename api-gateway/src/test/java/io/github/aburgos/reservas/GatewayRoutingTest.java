package io.github.aburgos.reservas;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Enrutado, seguridad y CORS del gateway contra su cadena real (ver {@link GatewayTestBase}). */
class GatewayRoutingTest extends GatewayTestBase {

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
