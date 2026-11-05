package io.github.aburgos.reservas.infrastructure.web.mapper;

import io.github.aburgos.reservas.application.command.CrearRecursoCommand;
import io.github.aburgos.reservas.domain.model.FranjaApertura;
import io.github.aburgos.reservas.domain.model.HorarioApertura;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.infrastructure.web.dto.FranjaAperturaDto;
import io.github.aburgos.reservas.infrastructure.web.dto.RecursoRequest;
import io.github.aburgos.reservas.infrastructure.web.dto.RecursoResponse;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

@Component
public class RecursoMapper {

    /**
     * Una zona inválida lanza {@code DateTimeException} y una franja incoherente
     * {@code IllegalArgumentException}; el manejador global convierte ambas en 400.
     */
    public CrearRecursoCommand toCommand(RecursoRequest request) {
        List<FranjaAperturaDto> dtos = request.horario() == null ? List.of() : request.horario();
        HorarioApertura horario = new HorarioApertura(dtos.stream()
                .map(d -> new FranjaApertura(d.dia(), d.apertura(), d.cierre()))
                .toList());
        ZoneId zona = request.zona() == null || request.zona().isBlank() ? null : ZoneId.of(request.zona());
        return new CrearRecursoCommand(request.nombre(), request.tipo(), zona, horario);
    }

    public RecursoResponse toResponse(Recurso r) {
        List<FranjaAperturaDto> horario = r.getHorario().franjas().stream()
                .map(f -> new FranjaAperturaDto(f.dia(), f.apertura(), f.cierre()))
                .toList();
        return new RecursoResponse(r.getId(), r.getNombre(), r.getTipo(), r.getZona().getId(), r.isActivo(), horario);
    }
}
