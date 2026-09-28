CREATE TABLE IF NOT EXISTS merchants (
    id   BIGINT PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS payments (
    id           BIGINT PRIMARY KEY,
    customer_id  BIGINT NOT NULL,
    merchant_id  BIGINT NOT NULL REFERENCES merchants (id),
    method       TEXT NOT NULL CHECK (method IN ('CARD', 'BANK_TRANSFER')),
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    captured_at  TIMESTAMPTZ NOT NULL
);

-- Every call made to the payment provider, so a test can count them.
CREATE TABLE IF NOT EXISTS provider_calls (
    id         BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    amount_cents BIGINT NOT NULL,
    called_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS refunds (
    id           BIGSERIAL PRIMARY KEY,
    payment_id   BIGINT NOT NULL REFERENCES payments (id),
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    status       TEXT NOT NULL CHECK (status IN ('PENDING', 'REFUNDED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- At most one live refund per payment, even when two requests race.
CREATE UNIQUE INDEX IF NOT EXISTS refunds_one_live_per_payment
    ON refunds (payment_id) WHERE status IN ('PENDING', 'REFUNDED');

CREATE INDEX IF NOT EXISTS payments_by_customer ON payments (customer_id);
