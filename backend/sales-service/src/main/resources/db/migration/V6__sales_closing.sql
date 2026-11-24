-- Dnevni zakljucki prodaje (issue #55). Zakljucek je posnetek vsot prodaj enega UTC dneva za en
-- avtomat ali za vse avtomate (machine_id IS NULL). Obseg: tabeli sales_closing in
-- sales_closing_total ter varovali dokoncnosti.
-- Napake: prekrsitev unikatnosti = 23505 (aplikacija jo prestreze z ON CONFLICT DO NOTHING in
-- vrne CLOSING_EXISTS); prekrsitev varovala = SQLSTATE 23000 (integrity_constraint_violation);
-- poslovna konflikta CLOSING_CONFIRMED in CLOSING_HAS_OPEN_RECONCILIATION preveri aplikacija.
CREATE TABLE sales_closing (
    id                   uuid PRIMARY KEY,
    closing_date         date         NOT NULL,
    machine_id           uuid,
    status               varchar(9)   NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED')),
    note                 varchar(500) NOT NULL DEFAULT '',
    open_reconciliations integer      NOT NULL CHECK (open_reconciliations >= 0),
    created_at           timestamptz  NOT NULL,
    created_by           uuid         NOT NULL,
    updated_at           timestamptz  NOT NULL,
    confirmed_at         timestamptz,
    confirmed_by         uuid,
    -- podatka o potrditvi obstajata natanko takrat, ko je zakljucek CONFIRMED
    CONSTRAINT sales_closing_confirmed_check CHECK (
        (status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL)
        OR (status = 'DRAFT' AND confirmed_at IS NULL AND confirmed_by IS NULL)),
    -- potrditev je mogoca le brez odprtih uskladitev
    CONSTRAINT sales_closing_open_check CHECK (status <> 'CONFIRMED' OR open_reconciliations = 0),
    -- en zakljucek na (datum, avtomat); NULL (vsi avtomati) je tudi vrednost, ne "neznano"
    CONSTRAINT sales_closing_scope_key UNIQUE NULLS NOT DISTINCT (closing_date, machine_id)
);

-- Seznam: najnovejsi datum prvi.
CREATE INDEX sales_closing_date_idx ON sales_closing (closing_date DESC, created_at DESC);

CREATE TABLE sales_closing_total (
    closing_id  uuid          NOT NULL REFERENCES sales_closing (id) ON DELETE CASCADE,
    currency    char(3)       NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    sales_count bigint        NOT NULL CHECK (sales_count >= 0),
    quantity    bigint        NOT NULL CHECK (quantity >= 0),
    -- brez omejitve obsega: vsota najvecjih dovoljenih prodaj ne sme povzrociti prekoracitve
    turnover    numeric       NOT NULL CHECK (turnover >= 0),
    PRIMARY KEY (closing_id, currency)
);

-- Zakljucek nastane kot DRAFT; identiteta osnutka je nespremenljiva; potrjen zakljucek je dokoncen:
-- nobenega UPDATE in nobenega DELETE; DELETE osnutka je dovoljen.
CREATE FUNCTION sales_closing_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'a closing must be created as a draft'
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status = 'CONFIRMED' THEN
        RAISE EXCEPTION 'a confirmed closing is final: % is not allowed', TG_OP
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.closing_date IS DISTINCT FROM OLD.closing_date
        OR NEW.machine_id IS DISTINCT FROM OLD.machine_id
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN
        RAISE EXCEPTION 'the identity of a closing cannot change'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sales_closing_final
    BEFORE INSERT OR UPDATE OR DELETE ON sales_closing
    FOR EACH ROW EXECUTE FUNCTION sales_closing_guard();

-- TRUNCATE bi obšel vrstična varovala.
CREATE FUNCTION sales_closing_no_truncate() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'TRUNCATE of % is not allowed', TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sales_closing_no_truncate
    BEFORE TRUNCATE ON sales_closing
    FOR EACH STATEMENT EXECUTE FUNCTION sales_closing_no_truncate();

CREATE TRIGGER sales_closing_total_no_truncate
    BEFORE TRUNCATE ON sales_closing_total
    FOR EACH STATEMENT EXECUTE FUNCTION sales_closing_no_truncate();

-- Vsote potrjenega zakljucka se ne smejo vstavljati, spreminjati ali brisati. Pri kaskadnem brisanju
-- osnutka starsa ni vec, zato se brisanje dovoli; potrjenega starsa brez DELETE ni mogoce odstraniti.
CREATE FUNCTION sales_closing_total_guard() RETURNS trigger AS $$
DECLARE
    parent_id     uuid;
    parent_status varchar(9);
BEGIN
    -- preveri starsa stare vrstice (UPDATE, DELETE) in starsa nove vrstice (INSERT, UPDATE)
    FOR parent_id IN
        SELECT DISTINCT p FROM (VALUES
            (CASE WHEN TG_OP IN ('UPDATE', 'DELETE') THEN OLD.closing_id END),
            (CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN NEW.closing_id END)) AS v (p)
        WHERE p IS NOT NULL
    LOOP
        SELECT status INTO parent_status FROM sales_closing WHERE id = parent_id;
        IF parent_status = 'CONFIRMED' THEN
            RAISE EXCEPTION 'a confirmed closing is final: totals cannot change (%)', TG_OP
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    END LOOP;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sales_closing_total_final
    BEFORE INSERT OR UPDATE OR DELETE ON sales_closing_total
    FOR EACH ROW EXECUTE FUNCTION sales_closing_total_guard();
