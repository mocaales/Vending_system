-- Prodaje (issue #26). Prodaja je nespremenljiva; po sprejemu se spreminja le stanje usklajevanja
-- (inventory_state, inventory_reason), ki ga bo posodabljal outbox (#32).
CREATE TABLE sales (
    sale_id          uuid PRIMARY KEY,
    machine_id       uuid          NOT NULL,
    slot_code        varchar(8)    NOT NULL CHECK (char_length(slot_code) >= 1),
    product_id       uuid          NOT NULL,
    quantity         integer       NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    unit_price_paid  numeric(12,2) NOT NULL CHECK (unit_price_paid >= 0),
    currency         char(3)       NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    payment_method   varchar(8)    NOT NULL CHECK (payment_method IN ('CASH', 'CARD', 'OTHER')),
    occurred_at      timestamptz   NOT NULL,
    received_at      timestamptz   NOT NULL DEFAULT now(),
    inventory_state  varchar(24)   NOT NULL DEFAULT 'PENDING'
        CHECK (inventory_state IN ('PENDING', 'APPLIED', 'NEEDS_RECONCILIATION', 'RESOLVED')),
    inventory_reason varchar(24)
        CHECK (inventory_reason IN ('PRODUCT_MISMATCH', 'INSUFFICIENT_STOCK'))
);

-- Filtri seznama (#27): avtomat, izdelek, čas, stanje usklajevanja; novejše prve.
CREATE INDEX sales_machine_occurred_idx ON sales (machine_id, occurred_at DESC);
CREATE INDEX sales_product_occurred_idx ON sales (product_id, occurred_at DESC);
CREATE INDEX sales_occurred_idx ON sales (occurred_at DESC);
CREATE INDEX sales_state_idx ON sales (inventory_state, occurred_at DESC);

-- Nespremenljivost: brisanje je prepovedano, pri posodobitvi se smeta spremeniti le stanje usklajevanja.
CREATE FUNCTION sales_guard() RETURNS trigger AS $$
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
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sales_immutable
    BEFORE UPDATE OR DELETE ON sales
    FOR EACH ROW EXECUTE FUNCTION sales_guard();
