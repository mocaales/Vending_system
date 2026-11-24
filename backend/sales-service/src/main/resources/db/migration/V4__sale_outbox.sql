-- Outbox za zmanjšanje zaloge (issue #32): vsaka sprejeta prodaja dobi natanko en vnos, ki ga
-- worker dostavi Inventory. Vnos nastane v isti transakciji kot prodaja. Brez tujega ključa na
-- sales, da varovalo pred TRUNCATE (V3) ostane edina ovira.
CREATE TABLE sale_outbox (
    sale_id         uuid PRIMARY KEY,
    trace_id        varchar(64),
    attempts        integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    done_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- Samo odprti vnosi: worker išče zapadle po času naslednjega poskusa.
CREATE INDEX sale_outbox_due_idx ON sale_outbox (next_attempt_at) WHERE done_at IS NULL;

-- Prodaje, sprejete pred to migracijo in še ne usklajene z zalogo, dobijo vnos, zapadel takoj.
-- trace_id je neznan (worker ustvari novega); ON CONFLICT ohrani migracijo ponovljivo varno.
INSERT INTO sale_outbox (sale_id, trace_id, attempts, next_attempt_at)
SELECT sale_id, NULL, 0, now()
FROM sales
WHERE inventory_state = 'PENDING'
ON CONFLICT (sale_id) DO NOTHING;
