-- Opozorila (#25): tabela alert, rezervoar LOW_STOCK in zadnja sprejeta verzija porocila.
-- Obseg: samo nove tabele; meritve se brisejo loceno in opozoril nikoli ne brisejo (brez tujih
-- kljucev na measurement). Enumi so text + CHECK, ne Postgres enum.
-- Invariant "najvec eno ACTIVE opozorilo na stroj in tip" je delni unikatni indeks.
-- Pricakovane poslovne napake (ALERT_NOT_FOUND, ALERT_NOT_ACTIVE, ALERT_TYPE_NOT_RESOLVABLE)
-- javlja aplikacija z ApiException; baza jih ne javlja z lastno SQLSTATE.
CREATE TABLE alert (
    id                    uuid PRIMARY KEY,
    machine_id            uuid        NOT NULL,
    type                  text        NOT NULL
        CHECK (type IN ('TEMPERATURE_HIGH', 'DOOR_OPEN', 'OFFLINE', 'LOW_STOCK')),
    status                text        NOT NULL CHECK (status IN ('ACTIVE', 'RESOLVED')),
    opened_at             timestamptz NOT NULL,
    acknowledged_at       timestamptz,
    acknowledged_by       uuid,
    resolved_at           timestamptz,
    resolved_by           uuid,
    resolution            text CHECK (resolution IN ('AUTO', 'MANUAL')),
    resolution_note       text CHECK (char_length(resolution_note) <= 500),
    notification_status   text        NOT NULL DEFAULT 'PENDING'
        CHECK (notification_status IN ('PENDING', 'SENT', 'FAILED')),
    notification_attempts integer     NOT NULL DEFAULT 0 CHECK (notification_attempts >= 0)
);

CREATE UNIQUE INDEX alert_one_active_per_machine_type_idx
    ON alert (machine_id, type) WHERE status = 'ACTIVE';

CREATE INDEX alert_machine_opened_idx ON alert (machine_id, opened_at DESC, id DESC);
CREATE INDEX alert_opened_idx ON alert (opened_at DESC, id DESC);

-- Reze LOW_STOCK opozorila; position ohrani vrstni red iz porocila.
CREATE TABLE alert_slot (
    alert_id             uuid    NOT NULL REFERENCES alert (id) ON DELETE CASCADE,
    position             integer NOT NULL,
    slot_code            text    NOT NULL CHECK (char_length(slot_code) BETWEEN 1 AND 8),
    product_id           uuid    NOT NULL,
    quantity             integer NOT NULL CHECK (quantity >= 0),
    low_stock_threshold  integer NOT NULL CHECK (low_stock_threshold >= 0),
    PRIMARY KEY (alert_id, position)
);

-- Zadnja sprejeta verzija porocila na stroj; vrstica je tudi kljuc za serializacijo porocil.
CREATE TABLE alert_report_version (
    machine_id uuid PRIMARY KEY,
    version    bigint NOT NULL
);
