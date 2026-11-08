-- Staff API (add-admin-web). Forward-only, like V1. The staff UI keeps its session in its own Nitro server, so the
-- backend stores no session and no login state.

-- Every staff write, one row each, in the write's database transaction. transaction_id is set for money writes only.
CREATE TABLE staff_action (
    id             UUID PRIMARY KEY,
    actor_oid      VARCHAR(64) NOT NULL,
    actor_name     VARCHAR(255),
    action         VARCHAR(32) NOT NULL CHECK (action IN (
                       'RESET', 'ADJUST', 'CLIENT_REGISTERED', 'GRANTS_REPLACED',
                       'CURRENCY_CREATED', 'CURRENCY_DEFAULT_SET')),
    target         VARCHAR(255) NOT NULL,
    transaction_id UUID REFERENCES transaction (id),
    detail         JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(detail) = 'object'),
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX staff_action_history ON staff_action (created_at, id);

CREATE TRIGGER staff_action_append_only
    BEFORE UPDATE OR DELETE ON staff_action
    FOR EACH ROW EXECUTE FUNCTION ledger_reject_journal_mutation();

-- Staff money writes are journal transactions of their own kinds, so the log tells them from client traffic.
ALTER TABLE transaction DROP CONSTRAINT transaction_kind_check;
ALTER TABLE transaction ADD CONSTRAINT transaction_kind_check
    CHECK (kind IN ('TRANSFER', 'MINT', 'BURN', 'BATCH', 'RESET', 'ADJUST'));

-- The window aggregates of the money flow scan postings by creation time.
CREATE INDEX posting_created_at ON posting (created_at);

-- Idempotency keys are scoped: 'client:<client_id>' for client writes, 'staff:<oid>' for staff writes. Existing rows
-- keep their stored answers and become client scoped.
ALTER TABLE idempotency_key ADD COLUMN scope VARCHAR(128);
UPDATE idempotency_key SET scope = 'client:' || client_id::text;
ALTER TABLE idempotency_key ALTER COLUMN scope SET NOT NULL;
ALTER TABLE idempotency_key DROP CONSTRAINT idempotency_key_pkey;
ALTER TABLE idempotency_key ALTER COLUMN client_id DROP NOT NULL;
ALTER TABLE idempotency_key ADD PRIMARY KEY (scope, key);
-- The client foreign key stays; a NULL client (staff keys) is not checked by it.
ALTER TABLE idempotency_key ADD CONSTRAINT idempotency_key_scope_owner CHECK (
    CASE WHEN scope LIKE 'client:%'
         THEN client_id IS NOT NULL AND scope = 'client:' || client_id::text
         ELSE client_id IS NULL AND scope LIKE 'staff:%'
    END);
