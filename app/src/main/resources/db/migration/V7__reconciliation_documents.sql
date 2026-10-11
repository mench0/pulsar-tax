-- Сверка счетов и платежей: какая часть дохода (поступления) закрывает какой счёт.
-- Один доход может закрывать несколько счетов, один счёт — оплачиваться несколькими платежами.
create table invoice_payments (
    id              bigint generated always as identity primary key,
    business_id     bigint         not null references businesses (id) on delete cascade,
    invoice_id      bigint         not null references invoices (id) on delete cascade,
    income_id       bigint references incomes (id) on delete cascade,
    -- Возврат покупателю по счёту (списание) — отрицательная сумма.
    bank_transaction_id bigint references bank_transactions (id) on delete cascade,
    amount          numeric(14, 2) not null check (amount <> 0),
    source          varchar(20)    not null default 'MANUAL', -- MANUAL, SUGGESTION, INVOICE_PAID
    created_at      timestamptz    not null default now(),
    last_updated_at timestamptz    not null default now(),
    check ((income_id is null) <> (bank_transaction_id is null))
);
create unique index invoice_payments_income_uq on invoice_payments (invoice_id, income_id) where income_id is not null;
create unique index invoice_payments_tx_uq on invoice_payments (invoice_id, bank_transaction_id) where bank_transaction_id is not null;
create index invoice_payments_business_idx on invoice_payments (business_id);

-- Оплаты, записанные раньше через «Счёт оплачен» (incomes.invoice_id), переносятся в сверку.
insert into invoice_payments (business_id, invoice_id, income_id, amount, source)
select i.business_id, i.invoice_id, i.id, i.amount, 'INVOICE_PAID'
from incomes i where i.invoice_id is not null;

-- Документы: счета поставщиков, чеки, акты. Распознанные значения хранятся отдельно от подтверждённых.
create table documents (
    id                  bigint generated always as identity primary key,
    business_id         bigint         not null references businesses (id) on delete cascade,
    file_name           varchar(255)   not null,
    content_type        varchar(100)   not null,
    size_bytes          int            not null,
    sha256              varchar(64)    not null,
    text_content        text           not null default '',
    -- TEXT_LAYER — текст из PDF; OCR — распознан с изображения; OCR_UNAVAILABLE — распознавание не настроено;
    -- NO_TEXT — текста нет; FAILED — файл не удалось прочитать.
    extraction          varchar(20)    not null,
    extracted           text           not null default '{}', -- JSON: поле → значение, уверенность, фрагмент
    status              varchar(20)    not null default 'NEEDS_REVIEW', -- NEEDS_REVIEW, CONFIRMED, REJECTED
    doc_type            varchar(20)    not null default 'OTHER',
    number              varchar(100)   not null default '',
    doc_date            date,
    currency            varchar(3),
    amount              numeric(16, 2),
    counterparty        varchar(200)   not null default '',
    counterparty_tin    varchar(20)    not null default '',
    expense_id          bigint references expenses (id) on delete set null,
    income_id           bigint references incomes (id) on delete set null,
    invoice_id          bigint references invoices (id) on delete set null,
    bank_transaction_id bigint references bank_transactions (id) on delete set null,
    created_at          timestamptz    not null default now(),
    last_updated_at     timestamptz    not null default now()
);
create unique index documents_sha_uq on documents (business_id, sha256);
create index documents_business_idx on documents (business_id, status);

-- Содержимое файлов отдельно: список документов не читает их с диска.
create table document_files (
    document_id bigint primary key references documents (id) on delete cascade,
    content     bytea  not null
);
