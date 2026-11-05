package io.github.aburgos.reservas.infrastructure.web;

import io.github.aburgos.reservas.application.ReservaService;
import io.github.aburgos.reservas.application.command.CancelarReservaCommand;
import io.github.aburgos.reservas.application.command.CrearReservaCommand;
import io.github.aburgos.reservas.domain.exception.CancelacionNoPermitidaException;
import io.github.aburgos.reservas.domain.exception.FueraDeHorarioException;
import io.github.aburgos.reservas.domain.exception.RecursoNoDisponibleException;
import io.github.aburgos.reservas.domain.exception.ReservaNoEncontradaException;
import io.github.aburgos.reservas.domain.exception.ReservaSolapadaException;
import io.github.aburgos.reservas.domain.model.EstadoReserva;
import io.github.aburgos.reservas.domain.model.FranjaHoraria;
import io.github.aburgos.reservas.domain.model.Reserva;
import io.github.aburgos.reservas.infrastructure.web.mapper.ReservaMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservaController.class)
@Import({ReservaMapper.class, GlobalExceptionHandler.class})
class ReservaControllerTest {

    private static final UUID RECURSO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant INICIO = Instant.parse("2026-01-12T10:00:00Z");
    private static final Instant FIN = Instant.parse("2026-01-12T11:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReservaService service;

    private static Reserva reserva(String usuario) {
        return new Reserva(UUID.fromString("22222222-2222-2222-2222-222222222222"), RECURSO, usuario,
                new FranjaHoraria(INICIO, FIN), EstadoReserva.CONFIRMADA,
                Instant.parse("2026-01-05T09:00:00Z"), null);
    }

    private static final String CUERPO_VALIDO = """
            {"recursoId": "11111111-1111-1111-1111-111111111111",
             "inicio": "2026-01-12T10:00:00Z", "fin": "2026-01-12T11:00:00Z"}""";

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder crear(String cuerpo) {
        return post("/api/reservas").with(user("user-1")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test
    void crearDevuelve201ConLocationYCuerpo() throws Exception {
        when(service.crear(any(CrearReservaCommand.class))).thenReturn(reserva("user-1"));

        mvc.perform(crear(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith(
                        "/api/reservas/22222222-2222-2222-2222-222222222222")))
                .andExpect(jsonPath("$.id").value("22222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.inicio").value("2026-01-12T10:00:00Z"));
    }

    @Test
    void crearUsaElUsuarioAutenticadoNoElDelCuerpo() throws Exception {
        when(service.crear(any(CrearReservaCommand.class))).thenReturn(reserva("user-1"));

        mvc.perform(crear("""
                {"recursoId": "11111111-1111-1111-1111-111111111111", "usuarioId": "intruso",
                 "inicio": "2026-01-12T10:00:00Z", "fin": "2026-01-12T11:00:00Z"}"""))
                .andExpect(status().isCreated());

        verify(service).crear(new CrearReservaCommand(RECURSO, "user-1", INICIO, FIN));
    }

    @Test
    void crearSinCamposObligatoriosDevuelve400ProblemDetail() throws Exception {
        mvc.perform(crear("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void crearConJsonMalformadoDevuelve400() throws Exception {
        mvc.perform(crear("{no es json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearConFranjaInvertidaDevuelve400() throws Exception {
        when(service.crear(any(CrearReservaCommand.class)))
                .thenThrow(new IllegalArgumentException("inicio debe ser anterior a fin"));

        mvc.perform(crear(CUERPO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos no válidos"));
    }

    @Test
    void crearConSolapeDevuelve409() throws Exception {
        when(service.crear(any(CrearReservaCommand.class))).thenThrow(new ReservaSolapadaException("ocupado"));

        mvc.perform(crear(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Reserva solapada"))
                .andExpect(jsonPath("$.detail").value("ocupado"));
    }

    @Test
    void crearConRecursoInactivoDevuelve409() throws Exception {
        when(service.crear(any(CrearReservaCommand.class))).thenThrow(new RecursoNoDisponibleException("inactivo"));

        mvc.perform(crear(CUERPO_VALIDO)).andExpect(status().isConflict());
    }

    @Test
    void crearFueraDeHorarioDevuelve422() throws Exception {
        when(service.crear(any(CrearReservaCommand.class))).thenThrow(new FueraDeHorarioException("cerrado"));

        mvc.perform(crear(CUERPO_VALIDO)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void crearSinAutenticarDevuelve401() throws Exception {
        mvc.perform(post("/api/reservas").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void obtenerDevuelveLaReserva() throws Exception {
        when(service.obtener(UUID.fromString("22222222-2222-2222-2222-222222222222"), "user-1"))
                .thenReturn(reserva("user-1"));

        mvc.perform(get("/api/reservas/22222222-2222-2222-2222-222222222222").with(user("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value("user-1"));
    }

    @Test
    void obtenerInexistenteDevuelve404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.obtener(id, "user-1")).thenThrow(new ReservaNoEncontradaException(id));

        mvc.perform(get("/api/reservas/" + id).with(user("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listarDevuelveLasReservasDelUsuario() throws Exception {
        when(service.listarDelUsuario("user-1")).thenReturn(List.of(reserva("user-1")));

        mvc.perform(get("/api/reservas").with(user("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void cancelarDevuelveLaReservaCancelada() throws Exception {
        Reserva cancelada = new Reserva(UUID.fromString("22222222-2222-2222-2222-222222222222"), RECURSO, "user-1",
                new FranjaHoraria(INICIO, FIN), EstadoReserva.CANCELADA,
                Instant.parse("2026-01-05T09:00:00Z"), Instant.parse("2026-01-06T09:00:00Z"));
        when(service.cancelar(any(CancelarReservaCommand.class))).thenReturn(cancelada);

        mvc.perform(post("/api/reservas/22222222-2222-2222-2222-222222222222/cancelar")
                        .with(user("user-1")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void cancelarFueraDePlazoDevuelve409() throws Exception {
        when(service.cancelar(any(CancelarReservaCommand.class)))
                .thenThrow(new CancelacionNoPermitidaException("fuera de plazo"));

        mvc.perform(post("/api/reservas/22222222-2222-2222-2222-222222222222/cancelar")
                        .with(user("user-1")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Cancelación no permitida"));
    }
}
