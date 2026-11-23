-- Machines, their fixed slots and the keys a machine uses to authenticate (issue #17).
-- Times are UTC (timestamptz). Machines are never deleted: RETIRED is a terminal status, and the
-- unique code stays taken after retirement.

CREATE TABLE machines (
    id         UUID             PRIMARY KEY,
    code       VARCHAR(32)      NOT NULL,
    name       VARCHAR(100)     NOT NULL,
    address    VARCHAR(200)     NOT NULL,
    latitude   DOUBLE PRECISION NOT NULL,
    longitude  DOUBLE PRECISION NOT NULL,
    status     VARCHAR(16)      NOT NULL,
    created_at TIMESTAMPTZ      NOT NULL,
    updated_at TIMESTAMPTZ      NOT NULL,
    CONSTRAINT machines_code_key UNIQUE (code),
    CONSTRAINT machines_status_check CHECK (status IN ('ACTIVE', 'SERVICE', 'RETIRED')),
    CONSTRAINT machines_latitude_check CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT machines_longitude_check CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX machines_status_idx ON machines (status);

CREATE TABLE machine_slots (
    machine_id UUID        NOT NULL REFERENCES machines (id),
    position   INTEGER     NOT NULL,
    slot_code  VARCHAR(8)  NOT NULL,
    label      VARCHAR(40),
    PRIMARY KEY (machine_id, position),
    CONSTRAINT machine_slots_code_key UNIQUE (machine_id, slot_code)
);

-- Only a hash of the key is stored; the secret is shown once, when the key is created.
CREATE TABLE machine_keys (
    id         UUID        PRIMARY KEY,
    machine_id UUID        NOT NULL REFERENCES machines (id),
    key_hash   CHAR(64)    NOT NULL,
    hint       CHAR(4)     NOT NULL,
    label      VARCHAR(60),
    status     VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT machine_keys_hash_key UNIQUE (key_hash),
    CONSTRAINT machine_keys_status_check CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT machine_keys_revoked_check CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))
);

CREATE INDEX machine_keys_machine_idx ON machine_keys (machine_id, status);
