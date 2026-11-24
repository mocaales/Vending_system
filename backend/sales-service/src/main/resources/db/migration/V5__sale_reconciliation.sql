-- Zaprtje neusklajene prodaje (issue #35). Obseg: štirje stolpci zapisa usklajevanja na sales,
-- dosledni CHECK-i in nova različica varovala sales_guard().
-- Napake: prekršitev varovala = SQLSTATE 23000 (integrity_constraint_violation); poslovni konflikt
-- INVALID_STATE_TRANSITION preveri aplikacija z pogojnim UPDATE (WHERE inventory_state = ...).
ALTER TABLE sales
    ADD COLUMN resolution  varchar(16),
    ADD COLUMN note        varchar(500),
    ADD COLUMN resolved_at timestamptz,
    ADD COLUMN resolved_by uuid,
    ADD CONSTRAINT sales_resolution_check CHECK (resolution IN ('ADJUSTED', 'WRITTEN_OFF')),
    ADD CONSTRAINT sales_note_check CHECK (char_length(note) BETWEEN 1 AND 500),
    -- zapis usklajevanja obstaja natanko takrat, ko je stanje RESOLVED (vsi štirje stolpci ali noben)
    ADD CONSTRAINT sales_reconciliation_complete CHECK (
        (inventory_state = 'RESOLVED'
            AND resolution IS NOT NULL AND note IS NOT NULL
            AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL)
        OR (inventory_state <> 'RESOLVED'
            AND resolution IS NULL AND note IS NULL
            AND resolved_at IS NULL AND resolved_by IS NULL));

-- Pri posodobitvi se smejo spremeniti le stanje usklajevanja, razlog in zapis usklajevanja;
-- ko je zapis enkrat nastavljen, se ne more nikoli več spremeniti (dokončnost na ravni baze).
CREATE OR REPLACE FUNCTION sales_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'sales are immutable: delete is not allowed'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF (NEW.sale_id, NEW.machine_id, NEW.slot_code, NEW.product_id, NEW.quantity,
        NEW.unit_price_paid, NEW.currency, NEW.payment_method, NEW.occurred_at, NEW.received_at)
       IS DISTINCT FROM
       (OLD.sale_id, OLD.machine_id, OLD.slot_code, OLD.product_id, OLD.quantity,
        OLD.unit_price_paid, OLD.currency, OLD.payment_method, OLD.occurred_at, OLD.received_at) THEN
        RAISE EXCEPTION 'sales are immutable: only the reconciliation state may change'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    -- RESOLVED se doseže samo iz NEEDS_RECONCILIATION
    IF NEW.inventory_state = 'RESOLVED' AND OLD.inventory_state <> 'RESOLVED'
       AND OLD.inventory_state <> 'NEEDS_RECONCILIATION' THEN
        RAISE EXCEPTION 'sales are immutable: RESOLVED is reachable only from NEEDS_RECONCILIATION'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF OLD.inventory_state = 'RESOLVED'
       AND (NEW.resolution, NEW.note, NEW.resolved_at, NEW.resolved_by,
            NEW.inventory_state, NEW.inventory_reason)
           IS DISTINCT FROM
           (OLD.resolution, OLD.note, OLD.resolved_at, OLD.resolved_by,
            OLD.inventory_state, OLD.inventory_reason) THEN
        RAISE EXCEPTION 'sales are immutable: a resolved reconciliation is final'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
