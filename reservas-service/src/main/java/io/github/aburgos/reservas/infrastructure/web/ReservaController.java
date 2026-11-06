package io.github.aburgos.reservas.infrastructure.web;

import io.github.aburgos.reservas.application.ReservaService;
import io.github.aburgos.reservas.application.command.CancelarReservaCommand;
import io.github.aburgos.reservas.domain.model.Reserva;
import io.github.aburgos.reservas.infrastructure.web.dto.ReservaRequest;
import io.github.aburgos.reservas.infrastructure.web.dto.ReservaResponse;
import io.github.aburgos.reservas.infrastructure.web.mapper.ReservaMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * El usuario se toma del {@code Principal} (con JWT, el claim {@code sub}); no existe tabla de usuarios.
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService service;
    private final ReservaMapper mapper;

    public ReservaController(ReservaService service, ReservaMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody ReservaRequest request,
                                                 Principal principal, @AuthenticationPrincipal Jwt jwt) {
        Reserva reserva = service.crear(mapper.toCommand(request, principal.getName(), emailDe(jwt)));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(reserva.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(reserva));
    }

    @GetMapping
    public List<ReservaResponse> listarMias(Principal principal) {
        return service.listarDelUsuario(principal.getName()).stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ReservaResponse obtener(@PathVariable UUID id, Principal principal) {
        return mapper.toResponse(service.obtener(id, principal.getName()));
    }

    /** La cancelación es una transición de estado (la reserva se conserva), no un borrado. */
    @PostMapping("/{id}/cancelar")
    public ReservaResponse cancelar(@PathVariable UUID id, Principal principal, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(service.cancelar(
                new CancelarReservaCommand(id, principal.getName(), emailDe(jwt))));
    }

    /** Claim {@code email} del token; {@code null} si no viene (la reserva se hace igual, sin notificación). */
    private static String emailDe(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }
}
