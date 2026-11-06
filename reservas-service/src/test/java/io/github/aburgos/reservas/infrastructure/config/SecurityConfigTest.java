package io.github.aburgos.reservas.infrastructure.config;

import io.github.aburgos.reservas.application.RecursoService;
import io.github.aburgos.reservas.application.ReservaService;
import io.github.aburgos.reservas.domain.model.HorarioApertura;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.domain.model.TipoRecurso;
import io.github.aburgos.reservas.infrastructure.web.GlobalExceptionHandler;
import io.github.aburgos.reservas.infrastructure.web.RecursoController;
import io.github.aburgos.reservas.infrastructure.web.ReservaController;
import io.github.aburgos.reservas.infrastructure.web.mapper.RecursoMapper;
import io.github.aburgos.reservas.infrastructure.web.mapper.ReservaMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static io.github.aburgos.reservas.TestJwt.admin;
import static io.github.aburgos.reservas.TestJwt.ciudadano;
import static io.github.aburgos.reservas.TestJwt.gestor;
import static io.github.aburgos.reservas.TestJwt.sinRoles;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reglas de acceso de la API contra la cadena de seguridad real, con JWT simulados. */
@WebMvcTest({RecursoController.class, ReservaController.class})
@Import({RecursoMapper.class, ReservaMapper.class, GlobalExceptionHandler.class, SecurityConfig.class})
class SecurityConfigTest {

    private static final String ALTA_RECURSO = "{\"nombre\": \"Pista 1\", \"tipo\": \"PISTA\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RecursoService recursos;

    @MockitoBean
    ReservaService reservas;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static MockHttpServletRequestBuilder altaRecurso() {
        return post("/api/recursos").contentType(MediaType.APPLICATION_JSON).content(ALTA_RECURSO);
    }

    @Test
    void sinTokenTodaLaApiDevuelve401() throws Exception {
        mvc.perform(get("/api/recursos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reservas")).andExpect(status().isUnauthorized());
        mvc.perform(altaRecurso()).andExpect(status().isUnauthorized());
        verifyNoInteractions(recursos, reservas);
    }

    @Test
    void unTokenInvalidoDevuelve401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("firma no válida"));

        mvc.perform(get("/api/recursos").header("Authorization", "Bearer no-es-un-jwt-valido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ciudadanoPuedeConsultarRecursosYReservas() throws Exception {
        when(recursos.listar()).thenReturn(List.of());
        when(reservas.listarDelUsuario("c1")).thenReturn(List.of());

        mvc.perform(get("/api/recursos").with(ciudadano("c1"))).andExpect(status().isOk());
        mvc.perform(get("/api/reservas").with(ciudadano("c1"))).andExpect(status().isOk());
    }

    @Test
    void ciudadanoNoPuedeDarDeAltaRecursosYDevuelve403() throws Exception {
        mvc.perform(altaRecurso().with(ciudadano("c1"))).andExpect(status().isForbidden());
        verifyNoInteractions(recursos);
    }

    @Test
    void tokenValidoSinRolesNoPuedeDarDeAltaRecursos() throws Exception {
        mvc.perform(altaRecurso().with(sinRoles("c1"))).andExpect(status().isForbidden());
        verifyNoInteractions(recursos);
    }

    @Test
    void tokenValidoSinRolesSiPuedeConsultar() throws Exception {
        when(recursos.listar()).thenReturn(List.of());

        mvc.perform(get("/api/recursos").with(sinRoles("c1"))).andExpect(status().isOk());
    }

    @Test
    void gestorPuedeDarDeAltaRecursos() throws Exception {
        when(recursos.crear(any())).thenReturn(Recurso.crear("Pista 1", TipoRecurso.PISTA, new HorarioApertura(List.of())));

        mvc.perform(altaRecurso().with(gestor("g1"))).andExpect(status().isCreated());
    }

    @Test
    void adminPuedeDarDeAltaRecursos() throws Exception {
        when(recursos.crear(any())).thenReturn(Recurso.crear("Pista 1", TipoRecurso.PISTA, new HorarioApertura(List.of())));

        mvc.perform(altaRecurso().with(admin("a1"))).andExpect(status().isCreated());
    }

    @Test
    void laPeticionSinCsrfFuncionaPorqueLaApiNoUsaSesion() throws Exception {
        when(recursos.crear(any())).thenReturn(Recurso.crear("Pista 1", TipoRecurso.PISTA, new HorarioApertura(List.of())));

        // altaRecurso() no añade csrf(): con CSRF activo esto sería un 403
        mvc.perform(altaRecurso().with(gestor("g1"))).andExpect(status().isCreated());
    }
}
