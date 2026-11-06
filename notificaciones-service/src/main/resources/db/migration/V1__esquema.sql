-- Idempotencia del consumidor: Kafka entrega al menos una vez, así que antes de tratar un evento se intenta
-- insertar su id aquí. La PK rechaza el duplicado de forma atómica (no "comprobar y luego insertar").
CREATE TABLE evento_procesado (
    event_id     UUID        PRIMARY KEY,
    procesado_en TIMESTAMPTZ NOT NULL
);

CREATE TABLE notificacion (
    id           UUID         PRIMARY KEY,
    event_id     UUID         NOT NULL,
    reserva_id   UUID         NOT NULL,       -- referencia lógica: sin FK entre servicios
    tipo         VARCHAR(30)  NOT NULL,
    destinatario VARCHAR(320) NOT NULL,       -- email
    estado       VARCHAR(20)  NOT NULL,
    intentos     INT          NOT NULL DEFAULT 0,
    ultimo_error TEXT,
    -- Evento original en JSON. Hace falta para reintentar el envío: el evento ya consta como procesado y
    -- Kafka no lo volvería a entregar. Es texto (no jsonb): nunca se consulta por su contenido.
    payload      TEXT         NOT NULL,
    creada_en    TIMESTAMPTZ  NOT NULL,
    enviada_en   TIMESTAMPTZ,
    CONSTRAINT notificacion_event_unico UNIQUE (event_id),
    CONSTRAINT notificacion_evento_fk FOREIGN KEY (event_id) REFERENCES evento_procesado (event_id),
    CONSTRAINT notificacion_tipo_valido CHECK (tipo IN ('RESERVA_CREADA', 'RESERVA_CANCELADA')),
    CONSTRAINT notificacion_estado_valido CHECK (estado IN ('PENDIENTE', 'ENVIADA', 'FALLIDA')),
    CONSTRAINT notificacion_intentos_validos CHECK (intentos >= 0)
);

-- Para reintentar las pendientes y las fallidas.
CREATE INDEX idx_notificacion_estado ON notificacion (estado);
