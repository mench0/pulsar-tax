-- Pulsar Tax: начальная схема.
-- Деньги — numeric(14,2) (до триллиона драмов с копейками), курсы и ставки — numeric с запасом знаков.

create table users (
    id              bigint generated always as identity primary key,
    email           varchar(254) not null unique,
    password_salt   varchar(64)  not null,
    password_hash   varchar(128) not null,
    calendar_token  varchar(64)  not null unique,
    telegram_chat_id bigint,
    lang            varchar(2)   not null default 'ru' check (lang in ('ru', 'en', 'hy')),
    last_reminder   date,
    created_at      timestamptz  not null default now()
);

-- Токен сессии хранится только как SHA-256: украденная база не даёт войти.
create table sessions (
    token_hash  varchar(64) primary key,
    user_id     bigint      not null references users (id) on delete cascade,
    expires_at  timestamptz not null,
    created_at  timestamptz not null default now()
);
create index sessions_user_idx on sessions (user_id);

-- Предприниматель (профиль ИП). Пока один на пользователя; отдельная таблица — чтобы позже
-- бухгалтер мог вести несколько ИП.
create table businesses (
    id           bigint generated always as identity primary key,
    owner_id     bigint       not null unique references users (id) on delete cascade,
    name         varchar(200) not null default '',
    tin          varchar(20)  not null default '',
    address      varchar(300) not null default '',
    bank_account varchar(50)  not null default '',
    regime       varchar(20)  not null default 'MICRO',
    activity     varchar(20)  not null default 'SERVICES',
    custom_rate  numeric(7, 4)
);

create table invoices (
    id               bigint generated always as identity primary key,
    business_id      bigint       not null references businesses (id) on delete cascade,
    number           varchar(50)  not null,
    date             date         not null,
    due_date         date,
    client           varchar(200) not null,
    client_tin       varchar(20)  not null default '',
    status           varchar(10)  not null default 'SENT',
    e_invoice_number varchar(50)  not null default ''
);
-- Номера счетов уникальны в пределах ИП без учёта регистра.
create unique index invoices_number_uq on invoices (business_id, lower(number));

create table invoice_items (
    invoice_id bigint         not null references invoices (id) on delete cascade,
    position   int            not null,
    name       varchar(300)   not null,
    quantity   numeric(16, 4) not null,
    price      numeric(16, 4) not null,
    primary key (invoice_id, position)
);

create table incomes (
    id              bigint generated always as identity primary key,
    business_id     bigint        not null references businesses (id) on delete cascade,
    date            date          not null,
    amount          numeric(14, 2) not null check (amount > 0),
    currency        varchar(3)    not null default 'AMD',
    original_amount numeric(16, 4),
    rate            numeric(16, 6),
    client          varchar(200)  not null default '',
    description     varchar(500)  not null default '',
    -- Доход, записанный при оплате счёта, удаляется вместе со счётом.
    invoice_id      bigint references invoices (id) on delete cascade,
    -- Отпечаток строки банковской выписки: повторная загрузка не создаёт дублей.
    import_key      varchar(100)
);
create index incomes_business_date_idx on incomes (business_id, date);
create unique index incomes_import_key_uq on incomes (business_id, import_key) where import_key is not null;

create table expenses (
    id          bigint generated always as identity primary key,
    business_id bigint         not null references businesses (id) on delete cascade,
    date        date           not null,
    amount      numeric(14, 2) not null check (amount > 0),
    category    varchar(20)    not null default 'OTHER',
    description varchar(500)   not null default ''
);
create index expenses_business_date_idx on expenses (business_id, date);

-- Уплата налога: quarter 1–4, 0 — за год целиком (микробизнес).
create table tax_payments (
    id          bigint generated always as identity primary key,
    business_id bigint         not null references businesses (id) on delete cascade,
    year        int            not null check (year between 2000 and 2100),
    quarter     int            not null check (quarter between 0 and 4),
    date        date           not null,
    amount      numeric(14, 2) not null check (amount > 0)
);
create index tax_payments_business_year_idx on tax_payments (business_id, year);

-- Сопоставление колонок банковской выписки по отпечатку её заголовка.
create table import_profiles (
    business_id bigint      not null references businesses (id) on delete cascade,
    signature   varchar(64) not null,
    mapping     text        not null, -- JSON: номера колонок, формат даты, валюта
    updated_at  timestamptz not null default now(),
    primary key (business_id, signature)
);

-- Кэш курсов ЦБ РА: общий для всех, прошедшие даты не меняются.
create table fx_rates (
    date       date           not null,
    currency   varchar(3)     not null,
    rate       numeric(16, 6) not null,
    loaded_at  timestamptz    not null default now(),
    primary key (date, currency)
);
