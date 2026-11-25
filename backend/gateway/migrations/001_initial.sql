CREATE TYPE operator_status AS ENUM ('ACTIVE', 'INACTIVE');

CREATE TABLE operators (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email           TEXT NOT NULL UNIQUE,
  name            TEXT NOT NULL,
  password_hash   TEXT NOT NULL,
  status          operator_status NOT NULL DEFAULT 'ACTIVE',
  token_version   INTEGER NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  deactivated_at  TIMESTAMPTZ
);
