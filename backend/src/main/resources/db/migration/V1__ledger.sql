-- Ledger schema (add-ledger-core, design decisions 1 to 8).
-- Journal tables (transaction, posting) are append-only: triggers reject UPDATE and DELETE for every role.
-- Amounts are NUMERIC(38, 8); the scale of each currency is enforced by the application (no rounding).

-- Currencies. Fraction digits never change after creation (enforced by the service).
CREATE TABLE currency (
    id              UUID PRIMARY KEY,
    code            VARCHAR(8) NOT NULL UNIQUE CHECK (code ~ '^[A-Z]{3,8}$'),
    name_singular   VARCHAR(64) NOT NULL CHECK (btrim(name_singular) <> ''),
    name_plural     VARCHAR(64) NOT NULL CHECK (btrim(name_plural) <> ''),
    symbol          VARCHAR(8) NOT NULL CHECK (btrim(symbol) <> ''),
    fraction_digits SMALLINT NOT NULL CHECK (fraction_digits BETWEEN 0 AND 8),
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- At most one default currency.
CREATE UNIQUE INDEX currency_one_default ON currency (is_default) WHERE is_default;

-- Clients (Entra ID application ids, the token's azp) and their grants per currency.
CREATE TABLE client (
    client_id    UUID PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL CHECK (btrim(display_name) <> ''),
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE client_grant (
    client_id  UUID NOT NULL REFERENCES client (client_id),
    currency   VARCHAR(8) NOT NULL REFERENCES currency (code),
    permission VARCHAR(32) NOT NULL CHECK (permission IN ('MINT', 'BURN', 'TRANSFER_ON_BEHALF', 'READ')),
    PRIMARY KEY (client_id, currency, permission)
);

-- Accounts. One PLAYER account per player UUID, SOURCE and SINK per client, FEE and ADMIN_* per currency.
CREATE TABLE account (
    id         UUID PRIMARY KEY,
    kind       VARCHAR(16) NOT NULL CHECK (kind IN ('PLAYER', 'BANK', 'SOURCE', 'SINK', 'FEE', 'ADMIN_SOURCE', 'ADMIN_SINK')),
    owner_uuid UUID,
    client_id  UUID REFERENCES client (client_id),
    currency   VARCHAR(8) REFERENCES currency (code),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT account_shape CHECK (
        (kind = 'PLAYER' AND owner_uuid IS NOT NULL AND client_id IS NULL AND currency IS NULL)
        OR (kind IN ('SOURCE', 'SINK') AND client_id IS NOT NULL AND owner_uuid IS NULL AND currency IS NULL)
        OR (kind IN ('FEE', 'ADMIN_SOURCE', 'ADMIN_SINK') AND currency IS NOT NULL AND owner_uuid IS NULL AND client_id IS NULL)
        OR (kind = 'BANK' AND owner_uuid IS NULL AND client_id IS NULL AND currency IS NULL)
    )
);

CREATE UNIQUE INDEX account_player_owner ON account (owner_uuid) WHERE kind = 'PLAYER';
CREATE UNIQUE INDEX account_client_system ON account (client_id, kind) WHERE client_id IS NOT NULL;
CREATE UNIQUE INDEX account_currency_system ON account (currency, kind) WHERE currency IS NOT NULL;

-- Balance projection: one row per account and currency, updated in the booking transaction.
CREATE TABLE balance (
    account_id UUID NOT NULL REFERENCES account (id),
    currency   VARCHAR(8) NOT NULL REFERENCES currency (code),
    amount     NUMERIC(38, 8) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (account_id, currency)
);

-- Journal header. Metadata is a JSON object (at most 16 entries, checked by the application).
CREATE TABLE transaction (
    id                 UUID PRIMARY KEY,
    kind               VARCHAR(16) NOT NULL CHECK (kind IN ('TRANSFER', 'MINT', 'BURN', 'BATCH')),
    reason             VARCHAR(255),
    actor              VARCHAR(128),
    external_reference VARCHAR(128),
    client_id          UUID REFERENCES client (client_id),
    metadata           JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(metadata) = 'object'),
    created_at         TIMESTAMPTZ NOT NULL
);

-- Journal lines. The signed amount per account and currency; the postings of a transaction sum to zero per currency.
CREATE TABLE posting (
    id             UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transaction (id),
    account_id     UUID NOT NULL REFERENCES account (id),
    currency       VARCHAR(8) NOT NULL REFERENCES currency (code),
    amount         NUMERIC(38, 8) NOT NULL CHECK (amount <> 0),
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX posting_transaction ON posting (transaction_id);
CREATE INDEX posting_history ON posting (account_id, created_at, id);

-- Idempotency keys, stored in the booking transaction. Expired keys are the only rows ever deleted.
CREATE TABLE idempotency_key (
    client_id      UUID NOT NULL REFERENCES client (client_id),
    key            VARCHAR(255) NOT NULL CHECK (btrim(key) <> ''),
    request_hash   VARCHAR(64) NOT NULL,
    transaction_id UUID REFERENCES transaction (id),
    response       JSONB,
    expires_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (client_id, key)
);

CREATE INDEX idempotency_key_expiry ON idempotency_key (expires_at);

-- Player name register with history. Names are matched case-insensitively.
CREATE TABLE player_name (
    uuid       UUID NOT NULL,
    name       VARCHAR(16) NOT NULL CHECK (name ~ '^[A-Za-z0-9_]{1,16}$'),
    first_seen TIMESTAMPTZ NOT NULL,
    last_seen  TIMESTAMPTZ NOT NULL,
    source     VARCHAR(16) NOT NULL CHECK (source IN ('JOIN', 'MOJANG')),
    PRIMARY KEY (uuid, name)
);

CREATE INDEX player_name_lower ON player_name (lower(name));

-- Append-only journal: UPDATE and DELETE are rejected for every role, including the owner.
CREATE FUNCTION ledger_reject_journal_mutation() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'journal table % is append-only: % is not allowed', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER transaction_append_only
    BEFORE UPDATE OR DELETE ON transaction
    FOR EACH ROW EXECUTE FUNCTION ledger_reject_journal_mutation();

CREATE TRIGGER posting_append_only
    BEFORE UPDATE OR DELETE ON posting
    FOR EACH ROW EXECUTE FUNCTION ledger_reject_journal_mutation();

-- Balanced journal: checked at commit (deferred), so all postings of a transaction are visible.
CREATE FUNCTION ledger_assert_balanced(tx_id UUID) RETURNS void
    LANGUAGE plpgsql AS $$
DECLARE
    unbalanced RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM posting WHERE transaction_id = tx_id) THEN
        RAISE EXCEPTION 'transaction % has no postings', tx_id
            USING ERRCODE = 'check_violation';
    END IF;

    SELECT p.currency, SUM(p.amount) AS total INTO unbalanced
    FROM posting p
    WHERE p.transaction_id = tx_id
    GROUP BY p.currency
    HAVING SUM(p.amount) <> 0
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'postings of transaction % do not sum to zero in %', tx_id, unbalanced.currency
            USING ERRCODE = 'check_violation';
    END IF;
END;
$$;

CREATE FUNCTION ledger_check_transaction_balance() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    PERFORM ledger_assert_balanced(NEW.id);
    RETURN NULL;
END;
$$;

CREATE FUNCTION ledger_check_posting_balance() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    PERFORM ledger_assert_balanced(NEW.transaction_id);
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER transaction_balanced
    AFTER INSERT ON transaction
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger_check_transaction_balance();

CREATE CONSTRAINT TRIGGER posting_balanced
    AFTER INSERT ON posting
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger_check_posting_balance();
