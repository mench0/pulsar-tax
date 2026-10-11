-- Общие поля сущностей (BaseEntity): время создания и последнего изменения.
alter table users add column last_updated_at timestamptz not null default now();

alter table businesses
    add column created_at      timestamptz not null default now(),
    add column last_updated_at timestamptz not null default now();

alter table invoices
    add column created_at      timestamptz not null default now(),
    add column last_updated_at timestamptz not null default now();

alter table incomes
    add column created_at      timestamptz not null default now(),
    add column last_updated_at timestamptz not null default now();

alter table expenses
    add column created_at      timestamptz not null default now(),
    add column last_updated_at timestamptz not null default now();

alter table tax_payments
    add column created_at      timestamptz not null default now(),
    add column last_updated_at timestamptz not null default now();
