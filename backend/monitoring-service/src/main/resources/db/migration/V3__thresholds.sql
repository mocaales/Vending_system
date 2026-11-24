-- Pragovi opozoril po napravi (#24). Vrstica obstaja samo, če jih je operater nastavil; brez nje
-- veljajo privzete vrednosti (docs/03). machine_id je referenca na Machine servis (brez tujega
-- ključa). Meje se ujemajo z openapi.yaml; reset_temp_c < max_temp_c je omejitev, ne preverjanje
-- v aplikaciji. Novih poslovnih SQLSTATE ni.
CREATE TABLE threshold (
    machine_id            uuid PRIMARY KEY,
    max_temp_c            numeric NOT NULL CHECK (max_temp_c BETWEEN -1000 AND 1000),
    reset_temp_c          numeric NOT NULL CHECK (reset_temp_c BETWEEN -1000 AND 1000),
    consecutive_readings  integer NOT NULL CHECK (consecutive_readings BETWEEN 1 AND 100),
    door_open_max_seconds integer NOT NULL CHECK (door_open_max_seconds BETWEEN 1 AND 86400),
    no_data_max_seconds   integer NOT NULL CHECK (no_data_max_seconds BETWEEN 1 AND 86400),
    updated_at            timestamptz NOT NULL,
    CONSTRAINT threshold_reset_below_max CHECK (reset_temp_c < max_temp_c)
);
