-- Подготовленные налоговые отчёты. Цифры фиксируются снимком расчёта; статус FILED_MANUALLY ставит сам пользователь
-- после подачи в системе электронной отчётности КГД — Pulsar не подаёт отчёты и не получает их статус.
create table tax_reports (
    id              bigint generated always as identity primary key,
    business_id     bigint        not null references businesses (id) on delete cascade,
    type            varchar(30)   not null, -- TURNOVER_QUARTERLY, MICRO_ANNUAL
    year            int           not null check (year between 2000 and 2100),
    quarter         int check (quarter between 1 and 4),
    version         int           not null default 1,
    status          varchar(20)   not null default 'PREPARED', -- PREPARED, FILED_MANUALLY, SUPERSEDED
    snapshot_id     bigint        not null references tax_snapshots (id),
    turnover        numeric(16, 2) not null,
    accrued         numeric(16, 2),
    paid            numeric(16, 2) not null,
    due             numeric(16, 2),
    warnings        text          not null default '[]', -- проверки, принятые пользователем при подготовке
    filed_on        date,
    filing_reference varchar(100) not null default '',
    filing_note     varchar(500)  not null default '',
    created_at      timestamptz   not null default now(),
    last_updated_at timestamptz   not null default now()
);
create index tax_reports_business_period_idx on tax_reports (business_id, year, quarter);
