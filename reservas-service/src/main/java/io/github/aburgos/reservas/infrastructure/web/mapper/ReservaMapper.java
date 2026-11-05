package io.github.aburgos.reservas.infrastructure.web.mapper;

import io.github.aburgos.reservas.application.command.CrearReservaCommand;
import io.github.aburgos.reservas.domain.model.Reserva;
import io.github.aburgos.reservas.infrastructure.web.dto.ReservaRequest;
import io.github.aburgos.reservas.infrastructure.web.dto.ReservaResponse;
import org.springframework.stereotype.Component;

@Component
public class ReservaMapper {

    public CrearReservaCommand toCommand(ReservaRequest request, String usuarioId) {
        return new CrearReservaCommand(request.recursoId(), usuarioId, request.inicio(), request.fin());
    }

    public ReservaResponse toResponse(Reserva r) {
        return new ReservaResponse(r.getId(), r.getRecursoId(), r.getUsuarioId(),
                r.getFranja().inicio(), r.getFranja().fin(), r.getEstado(), r.getCreadaEn(), r.getCanceladaEn());
    }
}
