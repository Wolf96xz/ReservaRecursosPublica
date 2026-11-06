package io.github.aburgos.reservas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Id de correlación y registro de peticiones del gateway. */
@ExtendWith(OutputCaptureExtension.class)
class GatewayFiltersTest extends GatewayTestBase {

    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Test
    void sinIdEntranteGeneraUnoLoDevuelveYLoReenviaAlBackend() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/recursos").header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn();

        String id = resultado.getResponse().getHeader("X-Correlation-Id");
        assertThat(id).matches(UUID_REGEX);
        assertThat(BACKEND.recibidas()).singleElement()
                .satisfies(p -> assertThat(p.idCorrelacion()).isEqualTo(id));
    }

    @Test
    void conservaUnIdEntranteValido() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/recursos").header("Authorization", bearer())
                .header("X-Correlation-Id", "front-123.abc_DEF")).andReturn();

        assertThat(resultado.getResponse().getHeader("X-Correlation-Id")).isEqualTo("front-123.abc_DEF");
        assertThat(BACKEND.recibidas()).singleElement()
                .satisfies(p -> assertThat(p.idCorrelacion()).isEqualTo("front-123.abc_DEF"));
    }

    @Test
    void sustituyeUnIdEntranteConCaracteresNoSeguros() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/recursos").header("Authorization", bearer())
                .header("X-Correlation-Id", "falso] GET /api/admin -> 200 <script>")).andReturn();

        assertThat(resultado.getResponse().getHeader("X-Correlation-Id")).matches(UUID_REGEX);
        assertThat(BACKEND.recibidas()).singleElement()
                .satisfies(p -> assertThat(p.idCorrelacion()).matches(UUID_REGEX));
    }

    @Test
    void sustituyeUnIdEntranteDemasiadoLargo() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/recursos").header("Authorization", bearer())
                .header("X-Correlation-Id", "a".repeat(65))).andReturn();

        assertThat(resultado.getResponse().getHeader("X-Correlation-Id")).matches(UUID_REGEX);
    }

    @Test
    void elIdTambienVaEnLasRespuestasDeError() throws Exception {
        MvcResult sinToken = mvc.perform(get("/api/recursos")).andExpect(status().isUnauthorized()).andReturn();
        MvcResult noEncontrada = mvc.perform(get("/api/otra-cosa").header("Authorization", bearer()))
                .andExpect(status().isNotFound()).andReturn();

        assertThat(sinToken.getResponse().getHeader("X-Correlation-Id")).matches(UUID_REGEX);
        assertThat(noEncontrada.getResponse().getHeader("X-Correlation-Id")).matches(UUID_REGEX);
    }

    @Test
    void registraMetodoRutaEstadoYElIdDeCorrelacion(CapturedOutput salida) throws Exception {
        mvc.perform(get("/api/recursos/abc").header("Authorization", bearer())
                .header("X-Correlation-Id", "traza-1")).andExpect(status().isOk());

        assertThat(salida.getOut()).contains("[traza-1] GET /api/recursos/abc -> 200");
    }

    @Test
    void registraTambienLasPeticionesRechazadas(CapturedOutput salida) throws Exception {
        mvc.perform(get("/api/recursos").header("X-Correlation-Id", "traza-2"))
                .andExpect(status().isUnauthorized());

        assertThat(salida.getOut()).contains("[traza-2] GET /api/recursos -> 401");
    }

    @Test
    void noRegistraElTokenNiLaQuery(CapturedOutput salida) throws Exception {
        mvc.perform(get("/api/recursos?email=ana@example.org").header("Authorization", bearer())
                .header("X-Correlation-Id", "traza-3")).andExpect(status().isOk());

        assertThat(salida.getOut())
                .contains("[traza-3] GET /api/recursos -> 200")
                .doesNotContain(TOKEN_VALIDO)
                .doesNotContain("ana@example.org");
    }
}
