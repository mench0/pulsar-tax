# Pulsar Tax — API

Помощник ИП в Армении: доходы, налог, сроки, отчёт и счета в одном месте.

В этом репозитории только бэкенд (REST API). Интерфейс (веб, Android, iOS на Flutter) — в отдельном репозитории
[mench0/pulsar-tax-web](https://github.com/mench0/pulsar-tax-web).

**Пошаговая инструкция по установке и запуску всего — бэкенда, веба, Android, iOS и выкладки на сервер:
[pulsar-tax-web/docs/SETUP.md](https://github.com/mench0/pulsar-tax-web/blob/main/docs/SETUP.md).**

Первый продукт экосистемы **Pulsar**. MVP для проверки спроса.

## Что умеет

- **Учётные записи.** Регистрация по почте и паролю, у каждого ИП свои данные.
  Вход хранится 30 дней, смена пароля выходит со всех устройств.
- **Загрузка банковской выписки (CSV).** Колонки и формат даты определяются сами, их можно поправить.
  Поступления отмечаются как доход, списания пропускаются, переводы между своими счетами
  и возвраты помечаются для проверки. Повторная загрузка той же выписки не создаёт дублей.
  Понимает `1 234,56` и `1,234.56`, разделители `;` `,` и табуляцию, кодировки UTF-8 и Windows-1251.
- **Доходы в валюте.** USD, EUR, RUB, GBP, GEL с курсом ЦБ РА на дату поступления; налог считается в драмах.
  Курс берётся из API ЦБ РА (`GET /api/rates?date=`), прошедшие даты кэшируются.
- **Копилка на налог.** В сводке — какую долю каждого поступления откладывать (`setAsidePercent`).
- **Расходы** по категориям, в сводке по кварталам. На налог пока не влияют: правила вычета расходов
  из налога с оборота нужно сверить с Налоговым кодексом.
- **Запоминание колонок выписки** для каждого банка (по отпечатку заголовка).
- **Обзор.** Доход, налог, сколько осталось уплатить, сколько осталось до порога режима,
  налог и уплата по кварталам, ближайшие сроки, предупреждения.
- **Отчёт и уплата.** Готовые цифры за квартал (налог с оборота) или год (микробизнес),
  пошаговая инструкция для file-online.taxservice.am, отметка уплаченного налога и остаток к уплате.
- **Напоминания.** Личная ссылка на календарь (.ics) для Google, Apple, Outlook с напоминанием за 3 дня.
  Бот Telegram напоминает за 7, 3 и 1 день и пишет сумму к уплате.
- **Счета на оплату.** Позиции, автономер `PT-2026-001`, печать и PDF, отметка оплаты (доход записывается сам).
  Поле для номера официального электронного счёта из e-invoice.taxservice.am.
- **Проверка режима.** Может ли вид деятельности применять микробизнес или налог с оборота.
- **Языки.** Армянский, русский, английский.
- **Экспорт.** Все данные в JSON, доходы и счета в CSV для Excel или бухгалтера.
- **Резервные копии** — средствами PostgreSQL (`pg_dump`), см. ниже.

## Стек

Java 21, Spring Boot 4 (Web MVC, Security, Data JPA), Hibernate, PostgreSQL, миграции Flyway,
Lombok, MapStruct (маппинг сущность ↔ DTO), QueryDSL (фильтры поиска).
Maven-проект из двух модулей: `dto` — модели запросов и ответов, `app` — приложение.

## Запуск для разработки

Нужны **Java 21+** и **PostgreSQL 14+** (Maven скачается сам через `mvnw`). Проще всего поднять базу в Docker:

```bash
docker compose up -d db       # PostgreSQL на localhost:5432, база и пользователь pulsar, пароль pulsar
./mvnw verify                 # тесты и сборка app/target/pulsar-tax.jar
java -jar app/target/pulsar-tax.jar
```

Без Docker установите PostgreSQL и создайте базу:
`createuser -P pulsar` (пароль `pulsar`) и `createdb -O pulsar pulsar`. Другая база —
через `PULSAR_DB_URL`, `PULSAR_DB_USER`, `PULSAR_DB_PASSWORD`.

В Windows — `mvnw.cmd verify`. API отвечает на http://127.0.0.1:8080. Интерфейс запускается из репозитория
pulsar-tax-web (см. его README): пока API слушает только 127.0.0.1, он принимает запросы фронтенда с любого
локального порта.

Тестам бэкенда своя база не нужна: они поднимают временный PostgreSQL на время прогона (только в тестах,
в jar его нет). Временный PostgreSQL не запускается от root — запускайте тесты от обычного пользователя.

## Выкладка на сервер

Нужна база PostgreSQL 14+. Схему создаёт и обновляет Flyway при запуске (`src/main/resources/db/migration`),
вручную ничего выполнять не нужно.

```bash
createuser -P pulsar && createdb -O pulsar pulsar     # один раз
PULSAR_DB_URL=jdbc:postgresql://localhost:5432/pulsar PULSAR_DB_USER=pulsar PULSAR_DB_PASSWORD=... \
PULSAR_HOST=0.0.0.0 PULSAR_SECURE_COOKIE=1 PULSAR_ALLOWED_ORIGINS=https://app.pulsar.am \
java -jar pulsar-tax.jar
```

Или в Docker вместе с базой: `PULSAR_DB_PASSWORD=свой-пароль docker compose up -d` (см. `docker-compose.yml`).
Проверка работоспособности для балансировщика: `GET /actuator/health`.

База должна быть в кодировке UTF-8 с UTF-8-локалью (так по умолчанию в Docker-образе и в пакетах Linux):
поиск по клиенту без учёта регистра опирается на `lower()` в PostgreSQL.

Резервная копия: `pg_dump -Fc pulsar > pulsar-$(date +%F).dump`, восстановление — `pg_restore -d pulsar файл`.

### Настройки через переменные окружения

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `PULSAR_PORT` | `8080` | Порт |
| `PULSAR_HOST` | `127.0.0.1` | Адрес. `0.0.0.0` откроет доступ из сети |
| `PULSAR_DB_URL` | `jdbc:postgresql://localhost:5432/pulsar` | Адрес базы PostgreSQL |
| `PULSAR_DB_USER`, `PULSAR_DB_PASSWORD` | `pulsar`, `pulsar` | Пользователь и пароль базы. На сервере задайте свой пароль |
| `PULSAR_SIGNUP` | `open` | `closed` — регистрация закрыта, войти могут только существующие пользователи (первого можно создать всегда) |
| `PULSAR_SECURE_COOKIE` | `0` | `1`, если сервер работает за HTTPS: cookie сессии получит флаг `Secure` |
| `PULSAR_TELEGRAM_TOKEN` | — | Токен бота от @BotFather. Без него напоминания в Telegram выключены |
| `PULSAR_ALLOWED_ORIGINS` | — | Адреса фронтенда через запятую, например `https://app.pulsar.am`. Им разрешены запросы с cookie (CORS) |
| `PULSAR_CBA_URL` | `https://api.cba.am/exchangerates.asmx` | API курсов ЦБ РА |
| `PULSAR_COOKIE_SAMESITE` | `Strict` | `Strict` — фронтенд на том же сайте (поддомен); `None` — на другом домене, требует `PULSAR_SECURE_COOKIE=1` |
| `PULSAR_FIRST_USER_EMAIL`, `PULSAR_FIRST_USER_PASSWORD` | — | Создать первого пользователя при запуске (миграция `R__FirstUser`), например при `PULSAR_SIGNUP=closed` |
| `PULSAR_IMPORT_LEGACY` | — | Папка данных старой версии на файлах — перенести в базу при запуске |

Сроки и «сегодня» считаются по времени Еревана, где бы ни стоял сервер.

### Первый пользователь

Миграция `src/main/java/db/migration/R__FirstUser.java` создаёт пользователя из `PULSAR_FIRST_USER_EMAIL` и
`PULSAR_FIRST_USER_PASSWORD` (не короче 8 символов) вместе с пустым профилем ИП. Пароль в репозитории не
хранится — в базу попадает только хэш. Миграция повторяемая: Flyway запускает её снова, только когда
меняется почта; существующего пользователя она не трогает и пароль не сбрасывает. После первого запуска
пароль можно убрать из окружения и сменить в приложении.

Перенос старых данных (`PULSAR_IMPORT_LEGACY`) идёт только в пустую базу — не задавайте его вместе с первым
пользователем.

### Переход со старой версии (данные в JSON-файлах)

Запустите новую версию один раз с `PULSAR_IMPORT_LEGACY=/путь/к/старой/data`. Пользователи, пароли, сессии и все
данные ИП (`users.json`, `users/*.json`) переносятся в пустую базу; если в базе уже есть пользователи, перенос
пропускается. Заново входить не нужно. После проверки переменную можно убрать, старую папку — сохранить как архив.

### Работа в сети

Для доступа не с этого компьютера запустите с `PULSAR_HOST=0.0.0.0` за обратным прокси с HTTPS
(Caddy, nginx), `PULSAR_SECURE_COOKIE=1` и адресом фронтенда в `PULSAR_ALLOWED_ORIGINS`.
Схемы размещения фронтенда описаны в README pulsar-tax-web. Бот Telegram работает
через long polling, публичный адрес для него не нужен.

## Данные

Таблицы (`V1__init.sql`): `users`, `sessions` (только SHA-256 токена), `businesses` (профиль ИП),
`incomes`, `expenses`, `invoices` и `invoice_items`, `tax_payments`, `import_profiles`, `fx_rates` (кэш курсов ЦБ).
Пароли — PBKDF2-SHA256 с солью. Деньги — `numeric`, без потерь на округлении.
Схему меняет только Flyway: новая миграция — новый файл `V3__что_меняется.sql`; Hibernate лишь проверяет,
что сущности совпадают со схемой. У сущностей с `BaseEntity` есть `created_at` и `last_updated_at` (V2).

## Устройство

```
dto/src/main/java/am/pulsar/tax/dto/
  BaseDto, *Dto      ответы API (IncomeDto, InvoiceDto, BusinessDto, UserDto…)
  create/            создание: BaseCreateDto, IncomeCreateDto… (проверки Bean Validation)
  patch/             частичное изменение: BasePatchDto, поля Optional (null — не менять, null в JSON — очистить)
  filter/            поиск: BaseFilterDto (ids, _or_), SearchRequest (фильтр, сортировки, пагинация), SearchResponse
  enums/, enums/sort перечисления и доступные сортировки
  auth/, request/, error/   вход, прочие запросы, ErrorResponseDto {"error": ...}

app/src/main/java/am/pulsar/tax/app/
  controller/        REST API; AbstractCrudController — общие операции сущностей ИП
  service/           AbstractReadOnlyService, AbstractCrudService и сервисы сущностей, вход, сводка
  service/provider/  EntityProvider: создание и изменение сущности из DTO (MapStruct) с проверками
  service/calculation/ налоговые правила (TaxRules), сводка (Summary), сроки и напоминания (Deadlines)
  entity/            сущности JPA, BaseEntity (id, createdAt, lastUpdatedAt)
  repository/        Spring Data JPA + QueryDSL
  mapper/entity/     EntityMapper: сущность → DTO (MapStruct)
  mapper/filter/     SearchMapper: фильтр → предикат QueryDSL, доступ только к записям своего ИП
  integration/       ЦБ РА (курсы с кэшем), Telegram (бот, рассылка напоминаний)
  config/            PulsarProperties, Jackson (числа и даты из полей ввода), веб; config/security — сессии, CORS
  migration/         Java-миграции Flyway (R__FirstUser) и перенос данных старой версии
  exception/         ApiException и наследники, ControllerExceptionHandler
  utils/             время Еревана, пароли, CSV, текущий пользователь
app/src/main/resources/
  application.yml    настройки
  db/migration/      SQL-миграции Flyway
app/src/test/java/am/pulsar/tax/app/
  RulesTest          налоги, сводка, сроки, CSV, разбор чисел и курсов
  ApiTest            API целиком на временном PostgreSQL с заглушками ЦБ РА и Telegram
  LegacyImportTest   перенос старых данных
```

### Общие эндпоинты сущностей

Доходы (`/api/incomes`), расходы (`/api/expenses`), счета (`/api/invoices`) и уплаты (`/api/payments`)
наследуют `AbstractCrudController`, поэтому кроме эндпоинтов из таблицы ниже у каждой есть:

| Метод | Путь | Описание |
|---|---|---|
| GET | `/{id}` | Запись по id |
| POST | `/search` | Поиск: `{"filter": {...}, "sorts": [{"sortName": "DATE", "direction": "DESC"}], "pagination": {"pageNumber": 0, "pageSize": 20}}` → `{"content": [...], "totalElements": ...}` |
| POST | `/count` | Количество по фильтру |
| PATCH | (корень, например `/api/incomes`) | Частичное изменение: `{"id": 1, "description": "…"}` |

Фильтры: `IncomeFilterDto` (year, dateFrom, dateTo, currencies, invoiceIds, importKeys, client), `ExpenseFilterDto`
(year, dateFrom, dateTo, categories), `InvoiceFilterDto` (year, statuses, numbers, client), `TaxPaymentFilterDto`
(years, quarters); во всех — `ids` и `_or_` (вложенные фильтры через ИЛИ). Видны только записи своего ИП.


### API

Все запросы, кроме входа, регистрации и `/api/rules`, требуют сессию:
- **браузер** получает cookie `HttpOnly` и отправляет её с `credentials: 'include'`;
- **мобильное приложение** передаёт `"tokenAuth": true` при входе или регистрации, получает `token`
  в ответе и шлёт его в заголовке `Authorization: Bearer <token>`. Cookie ему не выдаётся.

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login`, `/api/auth/logout` | Регистрация, вход, выход |
| GET | `/api/auth/me` | Текущий пользователь, язык, ссылка на календарь, Telegram |
| PUT | `/api/account` | Язык (`ru`, `en`, `hy`) |
| POST | `/api/account/password` | Смена пароля |
| POST / DELETE | `/api/account/telegram` | Ссылка для привязки Telegram / отвязать |
| GET / PUT | `/api/profile` | Профиль ИП |
| GET | `/api/rules?year=2026` | Пороги, ставки и доступные режимы по видам деятельности |
| GET | `/api/summary?year=2026` | Сводка за год |
| GET / POST | `/api/incomes?year=2026` | Доходы. В валюте: `currency`, `originalAmount`, `rate` |
| POST | `/api/incomes/import` | Пакет доходов из выписки, дубли по `importKey` пропускаются |
| DELETE | `/api/incomes/{id}` | Удалить доход |
| GET / POST | `/api/invoices` | Счета |
| GET / DELETE | `/api/invoices/{id}` | Счёт |
| POST | `/api/invoices/{id}/paid?date=2026-08-03` | Отметить оплату |
| PUT | `/api/invoices/{id}/einvoice` | Номер электронного счёта |
| GET / POST | `/api/payments?year=2026` | Уплаты налога (`quarter` 1–4, 0 — за год) |
| DELETE | `/api/payments/{id}` | Удалить уплату |
| GET / POST | `/api/expenses?year=2026` | Расходы |
| DELETE | `/api/expenses/{id}` | Удалить расход |
| GET | `/api/rates?date=2026-10-09` | Курсы ЦБ РА на дату (драмов за 1 единицу) |
| GET / PUT | `/api/import-profiles`, `/api/import-profiles/{отпечаток}` | Сохранённые колонки выписок |
| GET | `/api/export/data.json`, `/api/export/incomes.csv`, `/api/export/invoices.csv`, `/api/export/expenses.csv` | Экспорт |
| GET | `/cal/{token}.ics` | Календарь сроков по личной ссылке |

Ошибки возвращаются как `{"error": "текст"}` (пока только на русском). Тела запросов — только
`Content-Type: application/json`. Изменяющие запросы с адреса не из `PULSAR_ALLOWED_ORIGINS` и (при запуске на 127.0.0.1)
запросы с чужим `Host` отклоняются с кодом 403. После 5 неверных паролей вход по этой почте
блокируется на 15 минут.

## Важно про налоги

Пороги, ставки и ограничения по видам деятельности (`domain/TaxRules.java`) хранятся по годам
и взяты из открытых обзоров на 2026 год: микробизнес до 24 млн ֏, налог с оборота до 115 млн ֏,
ставка 10% для торговли и услуг, 1% для компаний из реестра High-Tech. Микробизнес недоступен
консультациям, юридическим и бухгалтерским услугам, инжинирингу, рекламе, дизайну и переводу;
налог с оборота — юридическим, бухгалтерским услугам и агентской деятельности.
Новый год правил добавляется одной записью `YearRules` в `TaxRules`.

**Перед работой с клиентами сверьте с Налоговым кодексом РА и бухгалтером:** ставки, запреты,
срок годового отчёта микробизнеса, шаги подачи в file-online и требования к электронным счетам.
Общая система (НДС) в MVP не рассчитывается.

Тексты напоминаний и календаря на армянском (`domain/Deadlines.java`) сделаны без носителя языка — их нужно вычитать.

## Что дальше

1. Готовые настройки импорта для выписок конкретных армянских банков.
2. Курс ЦБ РА подставляется сам (api.cba.am).
3. Ошибки сервера на армянском и английском.
4. Сброс пароля по почте.
5. Учёт расходов, которые можно вычесть из налога с оборота.
