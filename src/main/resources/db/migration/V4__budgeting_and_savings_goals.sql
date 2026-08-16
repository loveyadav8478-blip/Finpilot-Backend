-- V4__budgeting_and_savings_goals.sql
--
-- Budgeting & Savings Goals Engine schema definitions.

-- ============================================================
-- BUDGETS
-- Monthly spending limit per category or overall account.
-- ============================================================
CREATE TABLE budgets (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id                 UUID REFERENCES categories(id) ON DELETE CASCADE,
    monthly_limit_amount        NUMERIC(19,4) NOT NULL CHECK (monthly_limit_amount > 0),
    period_month                VARCHAR(7) NOT NULL, -- Format: 'YYYY-MM', e.g., '2026-08'
    alert_threshold_percentage NUMERIC(5,2) NOT NULL DEFAULT 80.00 CHECK (alert_threshold_percentage BETWEEN 1.0 AND 100.0),
    currency                    VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Ensure a user can only have one budget definition per category for a given month.
CREATE UNIQUE INDEX uq_budgets_user_category_period
    ON budgets (user_id, COALESCE(category_id, '00000000-0000-0000-0000-000000000000'::uuid), period_month);

CREATE INDEX idx_budgets_user_period ON budgets (user_id, period_month);


-- ============================================================
-- SAVINGS GOALS
-- User savings targets with current progress tracking.
-- ============================================================
CREATE TYPE savings_goal_status AS ENUM ('IN_PROGRESS', 'COMPLETED', 'CANCELLED');

CREATE TABLE savings_goals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    target_amount   NUMERIC(19,4) NOT NULL CHECK (target_amount > 0),
    current_amount  NUMERIC(19,4) NOT NULL DEFAULT 0.00 CHECK (current_amount >= 0),
    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    target_date     DATE,
    status          savings_goal_status NOT NULL DEFAULT 'IN_PROGRESS',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_savings_goals_user_status ON savings_goals (user_id, status);


-- ============================================================
-- SAVINGS CONTRIBUTIONS
-- Audit log of manual or automated funds added to a savings goal.
-- ============================================================
CREATE TABLE savings_contributions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    savings_goal_id     UUID NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount              NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    note                TEXT,
    contribution_date   DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_savings_contributions_goal ON savings_contributions (savings_goal_id);
