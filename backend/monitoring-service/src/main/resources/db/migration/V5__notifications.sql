-- E-postni outbox (#38): ena vrstica na odprt alarm, zapisana v isti transakciji kot alarm.
-- Delavec jo poslje prek SMTP; napaka SMTP alarma nikoli ne spremeni (alert.status ostane), samo
-- notification.status gre PENDING -> SENT | FAILED. Zadnje stanje se kopira v alert.notification_*
-- (to vraca API). Najem (lease_until) + FOR UPDATE SKIP LOCKED prepreci dvojno posiljanje z dveh
-- instanc. Pri napaki se attempts poveca, next_attempt_at gre naprej (narascajoc zamik).
CREATE TABLE notification (
    id              uuid PRIMARY KEY,
    alert_id        uuid        NOT NULL UNIQUE REFERENCES alert (id) ON DELETE CASCADE,
    status          text        NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts        integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    lease_until     timestamptz,
    last_error      text CHECK (char_length(last_error) <= 100),
    created_at      timestamptz NOT NULL,
    sent_at         timestamptz
);

CREATE INDEX notification_due_idx ON notification (next_attempt_at, id) WHERE status = 'PENDING';
