-- Outbox transaccional: el evento se guarda en la misma transacción que la reserva y OutboxRelay lo publica
-- en Kafka después. Sin FK a reserva a propósito: aggregate_id es una referencia lógica y el evento debe
-- poder sobrevivir a la fila.
CREATE TABLE outbox_event (
    id             UUID         PRIMARY KEY,   -- es el eventId que usa la idempotencia del consumidor
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,      -- también es la clave del mensaje Kafka (orden por reserva)
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    creado_en      TIMESTAMPTZ  NOT NULL,
    publicado_en   TIMESTAMPTZ                 -- NULL hasta que OutboxRelay lo publica
);

-- Solo indexa lo pendiente: el relay lee siempre "publicado_en IS NULL ORDER BY creado_en".
CREATE INDEX idx_outbox_event_pendiente ON outbox_event (creado_en) WHERE publicado_en IS NULL;
