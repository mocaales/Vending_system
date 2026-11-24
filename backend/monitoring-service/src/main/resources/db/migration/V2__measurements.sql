-- Meritve naprav (#23). measurement_id je idempotenčni ključ (PK); temperature_c je numeric brez
-- fiksne lestvice, da se zapis ("4.2") vrne natanko tak, kot je bil poslan.
CREATE TABLE measurement (
    measurement_id uuid PRIMARY KEY,
    machine_id     uuid        NOT NULL,
    observed_at    timestamptz NOT NULL,
    temperature_c  numeric     NOT NULL CHECK (temperature_c BETWEEN -1000 AND 1000),
    door_state     text        NOT NULL CHECK (door_state IN ('OPEN', 'CLOSED')),
    received_at    timestamptz NOT NULL
);

CREATE INDEX measurement_machine_observed_idx ON measurement (machine_id, observed_at);
