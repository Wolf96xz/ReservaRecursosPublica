package io.github.aburgos.reservas.application;

import io.github.aburgos.reservas.application.command.CrearRecursoCommand;
import io.github.aburgos.reservas.domain.exception.RecursoNoEncontradoException;
import io.github.aburgos.reservas.domain.model.Recurso;
import io.github.aburgos.reservas.infrastructure.persistence.RecursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RecursoService {

    private final RecursoRepository recursos;

    public RecursoService(RecursoRepository recursos) {
        this.recursos = recursos;
    }

    @Transactional
    public Recurso crear(CrearRecursoCommand cmd) {
        Recurso recurso = cmd.zona() == null
                ? Recurso.crear(cmd.nombre(), cmd.tipo(), cmd.horario())
                : new Recurso(UUID.randomUUID(), cmd.nombre(), cmd.tipo(), cmd.zona(), cmd.horario(), true);
        return recursos.save(recurso);
    }

    @Transactional(readOnly = true)
    public Recurso obtener(UUID id) {
        return recursos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(id));
    }

    @Transactional(readOnly = true)
    public List<Recurso> listar() {
        return recursos.findAll();
    }
}
