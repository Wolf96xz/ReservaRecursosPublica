-- Necesaria para combinar igualdad de uuid (=) con solapamiento de rangos (&&) en un índice GiST.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE recurso (
    id      UUID         PRIMARY KEY,
    nombre  VARCHAR(255) NOT NULL,
    tipo    VARCHAR(20)  NOT NULL,
    zona    VARCHAR(64)  NOT NULL,
    activo  BOOLEAN      NOT NULL,
    CONSTRAINT recurso_tipo_valido CHECK (tipo IN ('PISTA', 'SALA', 'INSTALACION'))
);

-- Horario semanal fijo: una fila por tramo de apertura, en hora local del recurso.
CREATE TABLE recurso_horario (
    recurso_id UUID        NOT NULL REFERENCES recurso (id) ON DELETE CASCADE,
    dia        VARCHAR(10) NOT NULL,
    apertura   TIME        NOT NULL,
    cierre     TIME        NOT NULL,
    CONSTRAINT recurso_horario_dia_valido CHECK (dia IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT recurso_horario_orden CHECK (apertura < cierre)
);

CREATE INDEX idx_recurso_horario_recurso ON recurso_horario (recurso_id);

CREATE TABLE reserva (
    id          UUID         PRIMARY KEY,
    recurso_id  UUID         NOT NULL REFERENCES recurso (id),
    usuario_id  VARCHAR(255) NOT NULL,
    inicio      TIMESTAMPTZ  NOT NULL,
    fin         TIMESTAMPTZ  NOT NULL,
    estado      VARCHAR(20)  NOT NULL,
    creada_en   TIMESTAMPTZ  NOT NULL,
    cancelada_en TIMESTAMPTZ,
    version     BIGINT       NOT NULL,
    CONSTRAINT reserva_estado_valido CHECK (estado IN ('CONFIRMADA', 'CANCELADA')),
    CONSTRAINT reserva_franja_valida CHECK (inicio < fin),
    -- Anti-solapamiento: dos reservas CONFIRMADAS del mismo recurso no pueden compartir tiempo.
    -- El rango es semiabierto [inicio, fin), así que franjas contiguas son válidas.
    -- Las canceladas quedan fuera de la restricción y liberan la franja.
    CONSTRAINT reserva_sin_solape EXCLUDE USING gist (
        recurso_id WITH =,
        tstzrange(inicio, fin, '[)') WITH &&
    ) WHERE (estado = 'CONFIRMADA')
);

CREATE INDEX idx_reserva_recurso_inicio ON reserva (recurso_id, inicio);
CREATE INDEX idx_reserva_usuario ON reserva (usuario_id);
