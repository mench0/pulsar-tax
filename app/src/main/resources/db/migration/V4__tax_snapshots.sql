-- Снимки налогового расчёта: по ним объясняется, почему сумма изменилась после правки операций или правил.
create table tax_snapshots (
    id              bigint generated always as identity primary key,
    business_id     bigint         not null references businesses (id) on delete cascade,
    year            int            not null check (year between 2000 and 2100),
    quarter         int check (quarter between 1 and 4), -- null — расчёт за год
    reason          varchar(200)   not null default '',
    accrued         numeric(14, 2),
    payload         text           not null, -- JSON расчёта целиком (TaxEngine.Calculation)
    created_at      timestamptz    not null default now(),
    last_updated_at timestamptz    not null default now()
);
create index tax_snapshots_business_year_idx on tax_snapshots (business_id, year);
