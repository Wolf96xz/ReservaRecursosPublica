package io.github.aburgos.reservas.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo completo por HTTP: recurso -> reserva -> solape -> cancelación, con PostgreSQL real. */
@AutoConfigureMockMvc
class ReservaFlujoIntegrationTest extends AbstractIntegrationTest {

    // Lunes 2026-01-12 (UTC+1): 10:00Z-11:00Z son las 11:00-12:00 locales
    private static final String LUNES_DE_10_A_11 = "\"inicio\": \"2026-01-12T10:00:00Z\", \"fin\": \"2026-01-12T11:00:00Z\"";

    @Autowired
    MockMvc mvc;

    private final JsonMapper json = JsonMapper.builder().build();

    private MockHttpServletRequestBuilder postJson(String url, String usuario, String cuerpo) {
        return post(url).with(user(usuario)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private String crearRecurso() throws Exception {
        MvcResult res = mvc.perform(postJson("/api/recursos", "gestor", """
                        {"nombre": "Pista 1", "tipo": "PISTA",
                         "horario": [{"dia": "MONDAY", "apertura": "09:00", "cierre": "21:00"}]}"""))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        return body.get("id").asString();
    }

    private String cuerpoReserva(String recursoId, String franja) {
        return "{\"recursoId\": \"" + recursoId + "\", " + franja + "}";
    }

    @Test
    void flujoCompletoDeReservaYCancelacion() throws Exception {
        String recursoId = crearRecurso();

        MvcResult creada = mvc.perform(postJson("/api/reservas", "user-1", cuerpoReserva(recursoId, LUNES_DE_10_A_11)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.usuarioId").value("user-1"))
                .andReturn();
        String reservaId = json.readTree(creada.getResponse().getContentAsString()).get("id").asString();

        // El mismo hueco, aunque sea otro usuario: 409
        mvc.perform(postJson("/api/reservas", "user-2", cuerpoReserva(recursoId, LUNES_DE_10_A_11)))
                .andExpect(status().isConflict());

        // Franja contigua: permitida
        mvc.perform(postJson("/api/reservas", "user-2", cuerpoReserva(recursoId,
                        "\"inicio\": \"2026-01-12T11:00:00Z\", \"fin\": \"2026-01-12T12:00:00Z\"")))
                .andExpect(status().isCreated());

        // Otro usuario no ve ni cancela la reserva ajena
        mvc.perform(get("/api/reservas/" + reservaId).with(user("user-2"))).andExpect(status().isNotFound());
        mvc.perform(postJson("/api/reservas/" + reservaId + "/cancelar", "user-2", "")).andExpect(status().isNotFound());

        // El dueño la cancela (la reserva es de 2026-01-12: el reloj real ya está pasado, por eso se prueba aparte
        // la regla de plazo en los tests unitarios; aquí solo se comprueba que el endpoint responde con un estado de negocio)
        mvc.perform(postJson("/api/reservas/" + reservaId + "/cancelar", "user-1", ""))
                .andExpect(status().isConflict());
    }

    @Test
    void reservaFueraDeHorarioDevuelve422() throws Exception {
        String recursoId = crearRecurso();

        mvc.perform(postJson("/api/reservas", "user-1", cuerpoReserva(recursoId,
                        "\"inicio\": \"2026-01-12T05:00:00Z\", \"fin\": \"2026-01-12T06:00:00Z\"")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reservaDeUnRecursoInexistenteDevuelve404() throws Exception {
        mvc.perform(postJson("/api/reservas", "user-1",
                        cuerpoReserva("00000000-0000-0000-0000-000000000000", LUNES_DE_10_A_11)))
                .andExpect(status().isNotFound());
    }
}
