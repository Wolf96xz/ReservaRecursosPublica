package io.github.aburgos.reservas.infrastructure.persistence;

import io.github.aburgos.reservas.domain.model.Recurso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecursoRepository extends JpaRepository<Recurso, UUID> {
}
