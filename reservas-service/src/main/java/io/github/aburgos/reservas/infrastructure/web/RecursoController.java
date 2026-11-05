package io.github.aburgos.reservas.infrastructure.web;

import io.github.aburgos.reservas.application.RecursoService;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.infrastructure.web.dto.RecursoRequest;
import io.github.aburgos.reservas.infrastructure.web.dto.RecursoResponse;
import io.github.aburgos.reservas.infrastructure.web.mapper.RecursoMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recursos")
public class RecursoController {

    private final RecursoService service;
    private final RecursoMapper mapper;

    public RecursoController(RecursoService service, RecursoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<RecursoResponse> crear(@Valid @RequestBody RecursoRequest request) {
        Recurso recurso = service.crear(mapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(recurso.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(recurso));
    }

    @GetMapping
    public List<RecursoResponse> listar() {
        return service.listar().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public RecursoResponse obtener(@PathVariable UUID id) {
        return mapper.toResponse(service.obtener(id));
    }
}
