-- V1__baseline_placeholder_tables.sql
--
-- Phase 1 discipline: we're building the Normalized Transaction Contract
-- ONLY. Auth, full user profiles, and category taxonomy are later phases.
-- These are intentionally minimal stub tables that exist purely to give
-- the transactions table valid, real foreign keys instead of orphaned
-- UUID columns. They will be expanded (not replaced) in Phase: Auth and
-- Phase: Categorization.

CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- for gen_random_uuid()

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name    VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL UNIQUE, -- e.g. 'FOOD', 'TRAVEL' — matches the AI service's TransactionCategory enum
    display_name    VARCHAR(100) NOT NULL
);

-- Seed the category taxonomy so it matches the AI service's schema exactly.
-- Keeping this in sync is a Phase 1 responsibility: the AI service's
-- TransactionCategory enum and this table are the same contract expressed
-- in two languages, and they must never drift apart silently.
INSERT INTO categories (code, display_name) VALUES
    ('FOOD', 'Food'),
    ('TRAVEL', 'Travel'),
    ('SHOPPING', 'Shopping'),
    ('ENTERTAINMENT', 'Entertainment'),
    ('UTILITIES', 'Utilities'),
    ('RENT', 'Rent'),
    ('HEALTHCARE', 'Healthcare'),
    ('GROCERIES', 'Groceries'),
    ('SUBSCRIPTIONS', 'Subscriptions'),
    ('EDUCATION', 'Education'),
    ('INVESTMENT', 'Investment'),
    ('TRANSFER', 'Transfer'),
    ('SALARY', 'Salary'),
    ('OTHER', 'Other');
