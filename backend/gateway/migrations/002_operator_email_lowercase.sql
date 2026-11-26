DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM operators GROUP BY lower(btrim(email)) HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'operators: e-mail addresses that differ only in case or spaces; resolve manually before migrating';
  END IF;
END $$;

UPDATE operators SET email = lower(btrim(email)) WHERE email <> lower(btrim(email));

ALTER TABLE operators
  ADD CONSTRAINT operators_email_normalized CHECK (email = lower(btrim(email))),
  ADD CONSTRAINT operators_email_length CHECK (char_length(email) BETWEEN 3 AND 254);

CREATE UNIQUE INDEX operators_email_lower_key ON operators (lower(email));
