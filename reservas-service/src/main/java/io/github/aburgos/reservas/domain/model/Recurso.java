package io.github.aburgos.reservas.domain.model;

import io.github.aburgos.reservas.domain.exception.FueraDeHorarioException;
import io.github.aburgos.reservas.domain.exception.RecursoNoDisponibleException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Recurso municipal reservable (pista, sala, instalación).
 * El horario de apertura se interpreta en la zona del recurso.
 * <p>
 * Persistencia: el agregado guarda las franjas de apertura como colección de elementos
 * (tabla recurso_horario); {@link HorarioApertura} sigue siendo un value object puro.
 */
@Entity
@Table(name = "recurso")
public class Recurso {

    public static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("Europe/Madrid");

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoRecurso tipo;

    @Column(nullable = false)
    private ZoneId zona;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recurso_horario", joinColumns = @JoinColumn(name = "recurso_id"))
    private List<FranjaApertura> franjas = new ArrayList<>();

    @Column(nullable = false)
    private boolean activo;

    protected Recurso() {
        // requerido por JPA
    }

    public Recurso(UUID id, String nombre, TipoRecurso tipo, ZoneId zona,
                   HorarioApertura horario, boolean activo) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.nombre = validarNombre(nombre);
        this.tipo = Objects.requireNonNull(tipo, "tipo es obligatorio");
        this.zona = Objects.requireNonNull(zona, "zona es obligatoria");
        Objects.requireNonNull(horario, "horario es obligatorio");
        this.franjas.addAll(horario.franjas());
        this.activo = activo;
    }

    /** Recurso nuevo: activo y en la zona por defecto (Europe/Madrid). */
    public static Recurso crear(String nombre, TipoRecurso tipo, HorarioApertura horario) {
        return new Recurso(UUID.randomUUID(), nombre, tipo, ZONA_POR_DEFECTO, horario, true);
    }

    /**
     * Comprueba que el recurso admite una reserva en la franja dada.
     *
     * @throws RecursoNoDisponibleException si el recurso está inactivo
     * @throws FueraDeHorarioException      si la franja no cae dentro del horario de apertura
     */
    public void validarReserva(FranjaHoraria franja) {
        Objects.requireNonNull(franja, "franja es obligatoria");
        if (!activo) {
            throw new RecursoNoDisponibleException("El recurso " + id + " no está activo");
        }
        if (!getHorario().permite(franja, zona)) {
            throw new FueraDeHorarioException(
                    "La franja " + franja.inicio() + " - " + franja.fin()
                            + " queda fuera del horario del recurso " + id);
        }
    }

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    public void renombrar(String nuevoNombre) {
        this.nombre = validarNombre(nuevoNombre);
    }

    public void cambiarHorario(HorarioApertura nuevoHorario) {
        Objects.requireNonNull(nuevoHorario, "horario es obligatorio");
        this.franjas.clear();
        this.franjas.addAll(nuevoHorario.franjas());
    }

    private static String validarNombre(String nombre) {
        Objects.requireNonNull(nombre, "nombre es obligatorio");
        if (nombre.isBlank()) {
            throw new IllegalArgumentException("nombre no puede estar vacío");
        }
        return nombre;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoRecurso getTipo() {
        return tipo;
    }

    public ZoneId getZona() {
        return zona;
    }

    public HorarioApertura getHorario() {
        return new HorarioApertura(franjas);
    }

    public boolean isActivo() {
        return activo;
    }
}
