package io.github.aburgos.reservas.infrastructure.web;

import io.github.aburgos.reservas.application.RecursoService;
import io.github.aburgos.reservas.application.command.CrearRecursoCommand;
import io.github.aburgos.reservas.domain.exception.RecursoNoEncontradoException;
import io.github.aburgos.reservas.domain.model.FranjaApertura;
import io.github.aburgos.reservas.domain.model.HorarioApertura;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.domain.model.TipoRecurso;
import io.github.aburgos.reservas.infrastructure.web.mapper.RecursoMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecursoController.class)
@Import({RecursoMapper.class, GlobalExceptionHandler.class})
class RecursoControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RecursoService service;

    private static Recurso recurso() {
        return Recurso.crear("Pista 1", TipoRecurso.PISTA, new HorarioApertura(List.of(
                new FranjaApertura(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(21, 0)))));
    }

    private MockHttpServletRequestBuilder crear(String cuerpo) {
        return post("/api/recursos").with(user("gestor")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test
    void crearConvierteElCuerpoEnComandoYDevuelve201() throws Exception {
        when(service.crear(any(CrearRecursoCommand.class))).thenReturn(recurso());

        mvc.perform(crear("""
                {"nombre": "Pista 1", "tipo": "PISTA", "zona": "Atlantic/Canary",
                 "horario": [{"dia": "MONDAY", "apertura": "09:00", "cierre": "21:00"}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Pista 1"))
                .andExpect(jsonPath("$.zona").value("Europe/Madrid"))
                .andExpect(jsonPath("$.horario[0].dia").value("MONDAY"))
                .andExpect(jsonPath("$.horario[0].apertura").value("09:00:00"));

        ArgumentCaptor<CrearRecursoCommand> captor = ArgumentCaptor.forClass(CrearRecursoCommand.class);
        verify(service).crear(captor.capture());
        assertThat(captor.getValue().zona()).isEqualTo(ZoneId.of("Atlantic/Canary"));
        assertThat(captor.getValue().horario().franjas()).containsExactly(
                new FranjaApertura(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(21, 0)));
    }

    @Test
    void crearSinZonaPasaZonaNulaParaUsarLaPorDefecto() throws Exception {
        when(service.crear(any(CrearRecursoCommand.class))).thenReturn(recurso());

        mvc.perform(crear("{\"nombre\": \"Sala\", \"tipo\": \"SALA\"}")).andExpect(status().isCreated());

        ArgumentCaptor<CrearRecursoCommand> captor = ArgumentCaptor.forClass(CrearRecursoCommand.class);
        verify(service).crear(captor.capture());
        assertThat(captor.getValue().zona()).isNull();
        assertThat(captor.getValue().horario().franjas()).isEmpty();
    }

    @Test
    void crearConNombreVacioDevuelve400() throws Exception {
        mvc.perform(crear("{\"nombre\": \" \", \"tipo\": \"SALA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearConTipoDesconocidoDevuelve400() throws Exception {
        mvc.perform(crear("{\"nombre\": \"X\", \"tipo\": \"PISCINA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearConZonaInexistenteDevuelve400() throws Exception {
        mvc.perform(crear("{\"nombre\": \"X\", \"tipo\": \"SALA\", \"zona\": \"Marte/Olympus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos no válidos"));
    }

    @Test
    void crearConFranjaDeAperturaInvertidaDevuelve400() throws Exception {
        mvc.perform(crear("""
                {"nombre": "X", "tipo": "SALA",
                 "horario": [{"dia": "MONDAY", "apertura": "21:00", "cierre": "09:00"}]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void obtenerInexistenteDevuelve404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.obtener(id)).thenThrow(new RecursoNoEncontradoException(id));

        mvc.perform(get("/api/recursos/" + id).with(user("u")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listarDevuelveLosRecursos() throws Exception {
        when(service.listar()).thenReturn(List.of(recurso(), recurso()));

        mvc.perform(get("/api/recursos").with(user("u")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
