-- ---------------------------------------------------------------------
-- Installment plans
--
-- An installment plan divides the agreed net fee of ONE fee account
-- into scheduled amounts. The fee account stays the authoritative
-- record of the obligation; the installments only schedule it and
-- never add liability of their own.
--
-- Invariants (enforced by InstallmentService, the only place that can
-- see several rows of an account at once):
--   * SUM(installment.amount) = gross_fee - discount_amount of the
--     account (exact decimal comparison),
--   * the installment numbers of one account are 1..n, unique.
--
-- Amounts are NUMERIC(12,2) like every other monetary column in this
-- schema - never floats. Historical rows are protected with
-- ON DELETE RESTRICT.
-- ---------------------------------------------------------------------

CREATE TABLE fee_installments (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fee_account_id     BIGINT NOT NULL REFERENCES fee_accounts(id) ON DELETE RESTRICT,
    installment_number INTEGER NOT NULL CHECK (installment_number > 0),
    due_date           DATE NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    created_by         BIGINT NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_fee_installments_account_number
        UNIQUE (fee_account_id, installment_number)
);

CREATE INDEX ix_fee_installments_account
    ON fee_installments (fee_account_id, installment_number);
CREATE INDEX ix_fee_installments_due_date
    ON fee_installments (due_date);

-- The composite unique keys below let the allocation table prove in
-- the database that a payment and the installment it pays belong to
-- the same fee account (a rule a single-column CHECK cannot express).
ALTER TABLE payments
    ADD CONSTRAINT ux_payments_id_account UNIQUE (id, fee_account_id);
ALTER TABLE fee_installments
    ADD CONSTRAINT ux_fee_installments_id_account UNIQUE (id, fee_account_id);

-- ---------------------------------------------------------------------
-- Payment allocations
--
-- The link between a received payment and the installments it covers.
-- A payment may cover part of one installment or parts of several.
--
-- Allocations are append-only: a voided payment KEEPS its allocations
-- so the history of what was paid against which installment stays
-- auditable. Only allocations of RECEIVED payments contribute to the
-- effective "paid" amount - the allocations of a voided payment stay
-- visible but count as zero.
-- ---------------------------------------------------------------------

CREATE TABLE payment_allocations (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fee_account_id BIGINT NOT NULL,
    payment_id     BIGINT NOT NULL,
    installment_id BIGINT NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_payment_allocations_payment_installment
        UNIQUE (payment_id, installment_id),
    -- The payment and the installment must belong to the same fee
    -- account; the composite foreign keys enforce it in the database.
    CONSTRAINT fk_payment_allocations_payment
        FOREIGN KEY (payment_id, fee_account_id)
        REFERENCES payments (id, fee_account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_allocations_installment
        FOREIGN KEY (installment_id, fee_account_id)
        REFERENCES fee_installments (id, fee_account_id) ON DELETE RESTRICT
);

CREATE INDEX ix_payment_allocations_installment
    ON payment_allocations (installment_id);
CREATE INDEX ix_payment_allocations_payment
    ON payment_allocations (payment_id);
