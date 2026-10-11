-- Загруженные выписки: файл, счёт, итоги проверки. Связь операций с выпиской нужна для проверки и повторной обработки.
create table statement_imports (
    id              bigint generated always as identity primary key,
    business_id     bigint       not null references businesses (id) on delete cascade,
    file_name       varchar(255) not null default '',
    file_sha256     varchar(64)  not null default '',
    account         varchar(100) not null default '',
    mapping         text         not null default '{}',
    rows_total      int          not null default 0,
    rows_added      int          not null default 0,
    rows_duplicate  int          not null default 0,
    rows_error      int          not null default 0,
    rows_skipped    int          not null default 0,
    created_at      timestamptz  not null default now(),
    last_updated_at timestamptz  not null default now()
);
create index statement_imports_business_idx on statement_imports (business_id);

-- Банковские операции. amount со знаком (плюс — поступление). В налог попадают только через подтверждённый доход.
create table bank_transactions (
    id                    bigint generated always as identity primary key,
    business_id           bigint         not null references businesses (id) on delete cascade,
    import_id             bigint references statement_imports (id) on delete set null,
    row_number            int,
    date                  date           not null,
    amount                numeric(16, 2) not null check (amount <> 0),
    currency              varchar(3)     not null default 'AMD',
    rate                  numeric(16, 6),
    amount_amd            numeric(16, 2),
    counterparty          varchar(200)   not null default '',
    description           varchar(500)   not null default '',
    reference             varchar(100)   not null default '',
    account               varchar(100)   not null default '',
    -- Отпечаток строки: повторная загрузка той же строки не создаёт дубль.
    fingerprint           varchar(64)    not null,
    -- Отпечаток в формате прежнего импорта доходов (incomes.import_key) — для защиты от дублей между старым и новым импортом.
    legacy_key            varchar(100),
    suggested_kind        varchar(20)    not null,
    confidence            numeric(4, 3)  not null,
    reasons               varchar(500)   not null default '',
    kind                  varchar(20),
    status                varchar(20)    not null default 'NEEDS_REVIEW',
    income_id             bigint references incomes (id) on delete set null,
    expense_id            bigint references expenses (id) on delete set null,
    tax_payment_id        bigint references tax_payments (id) on delete set null,
    linked_transaction_id bigint references bank_transactions (id) on delete set null,
    raw                   text           not null default '[]',
    created_at            timestamptz    not null default now(),
    last_updated_at       timestamptz    not null default now()
);
create unique index bank_transactions_fingerprint_uq on bank_transactions (business_id, fingerprint);
create index bank_transactions_business_date_idx on bank_transactions (business_id, date);
create index bank_transactions_status_idx on bank_transactions (business_id, status);
