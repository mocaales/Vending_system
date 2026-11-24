-- Nespremenljivost: tudi TRUNCATE tabele prodaj je prepovedan (V2 varuje le posamezne vrstice).
CREATE FUNCTION sales_truncate_guard() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'sales are immutable: truncate is not allowed'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sales_no_truncate
    BEFORE TRUNCATE ON sales
    FOR EACH STATEMENT EXECUTE FUNCTION sales_truncate_guard();
