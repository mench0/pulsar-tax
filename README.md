# Pulsar Tax — API

Помощник ИП в Армении: доходы, налог, сроки, отчёт и счета в одном месте.

В этом репозитории только бэкенд (REST API). Интерфейс (веб, Android, iOS на Flutter) — в отдельном репозитории
[mench0/pulsar-tax-web](https://github.com/mench0/pulsar-tax-web).

**Пошаговая инструкция по установке и запуску всего — бэкенда, веба, Android, iOS и выкладки на сервер:
[pulsar-tax-web/docs/SETUP.md](https://github.com/mench0/pulsar-tax-web/blob/main/docs/SETUP.md).**

Первый продукт экосистемы **Pulsar**. MVP для проверки спроса.

## Что умеет

- **Учётные записи.** Регистрация по почте и паролю, у каждого ИП свои данные.
  Сессия живёт 1 час после входа (`PULSAR_SESSION_TTL`), смена пароля выходит со всех устройств.
- **Налоговый движок с объяснением.** Расчёт за квартал или год по каждой операции: оборот, расходы
  (справочно — базу не уменьшают), налоговая база, налог до и после округления, уплачено, остаток, переплата.
  Ставка берётся по дате операции из редакции правил, действовавшей на эту дату; у каждого правила —
  основание и статус проверки. Снимки расчёта показывают, что и почему изменилось.
- **Банковские выписки (CSV).** Просмотр до сохранения с ошибками и предупреждениями по каждой строке,
  защита от дублей (в том числе со строками прежнего импорта доходов), связь операций с файлом и строкой.
  Тип операции (доход, расход, перевод между своими счетами, возврат, комиссия, уплата налога, прочее)
  предлагается по назначению, контрагенту, знаку и истории решений пользователя — и попадает в учёт
  только после подтверждения. Понимает `1 234,56` и `1,234.56`, разделители `;` `,` и табуляцию,
  кодировки UTF-8 и Windows-1251.
- **Подготовка отчётности.** Проверка готовности (непроверенные операции, курсы, месяцы без выписки, ИНН…),
  версия отчёта с зафиксированными цифрами, выгрузка CSV, инструкция по подаче и отметка пользователя
  «подал сам». Pulsar не подаёт отчёты в КГД и не показывает выдуманных статусов налоговой.
- **Документы.** PDF, PNG, JPEG: реквизиты из текстового слоя PDF или локального распознавания (tesseract),
  с уверенностью и подтверждением пользователем; связь с операциями, счетами и расходами.
- **Сверка счетов.** Частичная и многократная оплата, переплата, возврат покупателю, задолженность и просрочка,
  подсказки сопоставления; одна оплата не попадает в оборот дважды.
- **Налоговый резерв и прогноз.** Начислено, уплачено и осталось по периодам, оценка налога с неоплаченных
  счетов, разница с фактически отложенной суммой; прогноз денег на 1–12 месяцев с диапазоном и допущениями,
  предупреждение о нехватке денег перед налоговым сроком.
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
docker compose up -d          # PostgreSQL на localhost:5432, база и пользователь pulsar, пароль pulsar
./mvnw verify                 # тесты и сборка app/target/pulsar-tax.jar
java -jar app/target/pulsar-tax.jar
```

Без Docker установите PostgreSQL и создайте базу:
`createuser -P pulsar` (пароль `pulsar`) и `createdb -O pulsar pulsar`. Другая база —
через `PULSAR_DB_URL`, `PULSAR_DB_USER`, `PULSAR_DB_PASSWORD`.

В Windows — `mvnw.cmd verify`. API отвечает на http://127.0.0.1:8080. Интерфейс запускается из репозитория
pulsar-tax-web (см. его README): пока API слушает только 127.0.0.1, он принимает запросы фронтенда с любого
локального порта.

Тестам бэкенда своя база не нужна: они поднимают PostgreSQL 17 в контейнере через Testcontainers
(`TestDatabaseConfig`). Для `./mvnw verify` нужен запущенный Docker (Docker Desktop или Docker Engine).

## Выкладка на сервер

Нужна база PostgreSQL 14+. Схему создаёт и обновляет Flyway при запуске (`src/main/resources/db/migration`),
вручную ничего выполнять не нужно.

```bash
createuser -P pulsar && createdb -O pulsar pulsar     # один раз
PULSAR_DB_URL=jdbc:postgresql://localhost:5432/pulsar PULSAR_DB_USER=pulsar PULSAR_DB_PASSWORD=... \
PULSAR_HOST=0.0.0.0 PULSAR_SECURE_COOKIE=1 PULSAR_ALLOWED_ORIGINS=https://app.pulsar.am \
java -jar pulsar-tax.jar
```

Или в Docker: образ API собирается из `Dockerfile`, база — из `docker-compose.yml`
(`PULSAR_DB_PASSWORD=свой-пароль docker compose up -d`):

```bash
docker build -t pulsar-tax-api .
docker run -d --name pulsar-api --network host \
  -e PULSAR_DB_URL=jdbc:postgresql://localhost:5432/pulsar -e PULSAR_DB_USER=pulsar -e PULSAR_DB_PASSWORD=свой-пароль \
  -e PULSAR_HOST=127.0.0.1 -e PULSAR_SECURE_COOKIE=1 -e PULSAR_ALLOWED_ORIGINS=https://app.pulsar.am pulsar-tax-api
```
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
| `PULSAR_SESSION_TTL` | `1h` | Время жизни сессии после входа: `30m`, `8h`, `30d` |
| `PULSAR_SESSION_STORE` | `caffeine` | Хранилище сессий (см. «Сессии») |
| `PULSAR_SECURE_COOKIE` | `0` | `1`, если сервер работает за HTTPS: cookie сессии получит флаг `Secure` |
| `PULSAR_TELEGRAM_TOKEN` | — | Токен бота от @BotFather. Без него напоминания в Telegram выключены |
| `PULSAR_ALLOWED_ORIGINS` | — | Адреса фронтенда через запятую, например `https://app.pulsar.am`. Им разрешены запросы с cookie (CORS) |
| `PULSAR_CBA_URL` | `https://api.cba.am/exchangerates.asmx` | API курсов ЦБ РА |
| `PULSAR_COOKIE_SAMESITE` | `Strict` | `Strict` — фронтенд на том же сайте (поддомен); `None` — на другом домене, требует `PULSAR_SECURE_COOKIE=1` |
| `PULSAR_FIRST_USER_EMAIL`, `PULSAR_FIRST_USER_PASSWORD` | — | Создать первого пользователя при запуске (миграция `R__FirstUser`), например при `PULSAR_SIGNUP=closed` |
| `PULSAR_IMPORT_LEGACY` | — | Папка данных старой версии на файлах — перенести в базу при запуске |
| `PULSAR_OCR_TESSERACT` | — | Путь к tesseract для распознавания изображений документов (в Docker — `/usr/bin/tesseract`) |

Сроки и «сегодня» считаются по времени Еревана, где бы ни стоял сервер.

### Первый пользователь

Миграция `src/main/java/db/migration/R__FirstUser.java` создаёт пользователя из `PULSAR_FIRST_USER_EMAIL` и
`PULSAR_FIRST_USER_PASSWORD` (не короче 8 символов) вместе с пустым профилем ИП. Пароль в репозитории не
хранится — в базу попадает только хэш. Миграция повторяемая: Flyway запускает её снова, только когда
меняется почта; существующего пользователя она не трогает и пароль не сбрасывает. После первого запуска
пароль можно убрать из окружения и сменить в приложении.

Первого пользователя можно задавать вместе с переносом старых данных (`PULSAR_IMPORT_LEGACY`): перенос
пропускает только пользователей, чья почта уже есть в базе.

### Переход со старой версии (данные в JSON-файлах)

Запустите новую версию один раз с `PULSAR_IMPORT_LEGACY=/путь/к/старой/data`. Пользователи, пароли, сессии и все
данные ИП (`users.json`, `users/*.json`) переносятся в базу; пользователь, чья почта в базе уже есть, пропускается,
поэтому повторный запуск ничего не дублирует. Заново входить не нужно. После проверки переменную можно убрать, старую папку — сохранить как архив.

### Работа в сети

Для доступа не с этого компьютера запустите с `PULSAR_HOST=0.0.0.0` за обратным прокси с HTTPS
(Caddy, nginx), `PULSAR_SECURE_COOKIE=1` и адресом фронтенда в `PULSAR_ALLOWED_ORIGINS`.
Схемы размещения фронтенда описаны в README pulsar-tax-web. Бот Telegram работает
через long polling, публичный адрес для него не нужен.

## Данные

Таблицы (`V1__init.sql`): `users`, `businesses` (профиль ИП),
`incomes`, `expenses`, `invoices` и `invoice_items`, `tax_payments`, `import_profiles`, `fx_rates` (кэш курсов ЦБ).
Пароли — PBKDF2-SHA256 с солью. Деньги — `numeric`, без потерь на округлении.
Схему меняет только Flyway: новая миграция — новый файл `V3__что_меняется.sql`; Hibernate лишь проверяет,
что сущности совпадают со схемой. У сущностей с `BaseEntity` есть `created_at` и `last_updated_at` (V2).

### Сессии

Сессии хранятся не в базе, а в `SessionStore`: SHA-256 токена → id пользователя, со временем жизни
`PULSAR_SESSION_TTL` (по умолчанию 1 час). Сейчас реализация одна — `CaffeineSessionStore`, в памяти процесса:
после перезапуска API нужно войти заново, и она подходит, пока API запущен в одном экземпляре.

Переход на Redis (несколько экземпляров API, сессии переживают перезапуск):
1. Добавить `spring-boot-starter-data-redis` и настройки `spring.data.redis.*`.
2. Написать `RedisSessionStore implements SessionStore` с
   `@ConditionalOnProperty(prefix = "pulsar.session", name = "store", havingValue = "redis")`:
   `save` — `SET session:{hash} {userId} EX {ttl}` и `SADD user-sessions:{userId} {hash}`;
   `findUserId` — `GET`; `delete` — `DEL`; `deleteAllForUser` — удалить все ключи из `user-sessions:{userId}`.
3. Запустить с `PULSAR_SESSION_STORE=redis`. Остальной код (вход, фильтр сессий, cookie) не меняется.

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
  service/session/   SessionStore — хранилище сессий (CaffeineSessionStore; позже Redis)
  utils/             время Еревана, пароли, CSV, текущий пользователь
app/src/main/resources/
  application.yml    настройки
  db/migration/      SQL-миграции Flyway
app/src/test/java/am/pulsar/tax/app/
  RulesTest          налоги, сводка, сроки, CSV, разбор чисел и курсов
  ApiTest            API целиком на PostgreSQL в Testcontainers с заглушками ЦБ РА и Telegram
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
| POST | `/api/invoices/{id}/paid?date=2026-08-03` | Отметить оплату. `incomeId` — закрыть счёт уже учтённым доходом; если похожий доход есть, без `newPayment=true` — 409 |
| PUT | `/api/invoices/{id}/einvoice` | Номер электронного счёта |
| GET / POST | `/api/payments?year=2026` | Уплаты налога (`quarter` 1–4, 0 — за год) |
| DELETE | `/api/payments/{id}` | Удалить уплату |
| GET / POST | `/api/expenses?year=2026` | Расходы |
| DELETE | `/api/expenses/{id}` | Удалить расход |
| GET | `/api/rates?date=2026-10-09` | Курсы ЦБ РА на дату (драмов за 1 единицу) |
| GET / PUT | `/api/import-profiles`, `/api/import-profiles/{отпечаток}` | Сохранённые колонки выписок |
| GET | `/api/export/data.json`, `/api/export/incomes.csv`, `/api/export/invoices.csv`, `/api/export/expenses.csv` | Экспорт |
| GET | `/cal/{token}.ics` | Календарь сроков по личной ссылке |
| GET | `/api/tax/calculation?year=2026&quarter=3` | Подробный расчёт: операции, правила с основаниями, шаги, замечания |
| POST / GET | `/api/tax/snapshots` | Сохранить расчёт / список снимков |
| GET | `/api/tax/snapshots/{id}`, `/api/tax/snapshots/{id}/diff` | Снимок и что изменилось с тех пор |
| POST | `/api/bank/statements/preview` | Проверка выписки без сохранения |
| POST / GET | `/api/bank/statements` | Сохранить выписку / список выписок |
| GET | `/api/bank/statements/{id}/transactions` | Операции выписки |
| POST | `/api/bank/statements/{id}/reclassify` | Заново предложить типы неподтверждённым операциям |
| GET | `/api/bank/transactions?from=&to=&status=NEEDS_REVIEW` | Банковские операции |
| POST | `/api/bank/transactions/{id}/review` | Подтвердить тип, исключить или вернуть на проверку |
| POST | `/api/bank/transactions/confirm-confident` | Подтвердить уверенные подсказки |
| GET | `/api/reports/readiness?year=2026&quarter=3` | Готовность данных к отчёту, источники, инструкция |
| POST / GET | `/api/reports` | Подготовить версию отчёта / список |
| GET | `/api/reports/{id}`, `/api/reports/{id}/export.csv` | Отчёт с изменениями после подготовки, выгрузка |
| POST / DELETE | `/api/reports/{id}/filed` | Отметка «подан пользователем» / снять отметку |
| POST / GET | `/api/documents` | Загрузить документ (multipart, поле `file`) / список |
| GET / DELETE | `/api/documents/{id}` | Документ с распознанными значениями и похожими операциями |
| GET | `/api/documents/{id}/file` | Файл документа |
| POST | `/api/documents/{id}/confirm`, `/api/documents/{id}/reject` | Подтвердить значения и связи / отклонить |
| GET | `/api/reconciliation` | Счета, задолженность, несопоставленные поступления, подсказки |
| POST / DELETE | `/api/reconciliation/allocations` | Связать доход (или возврат) со счётом / отменить |
| GET | `/api/planning/reserve` | Налоговый резерв с объяснением |
| GET | `/api/planning/forecast?months=3` | Прогноз денежных потоков |
| PUT | `/api/planning/balances` | Фактически отложено на налоги и остаток денег |

Ошибки возвращаются как `{"error": "текст"}` (пока только на русском). Тела запросов — только
`Content-Type: application/json`. Изменяющие запросы с адреса не из `PULSAR_ALLOWED_ORIGINS` и (при запуске на 127.0.0.1)
запросы с чужим `Host` отклоняются с кодом 403. После 5 неверных паролей вход по этой почте
блокируется на 15 минут.

## Важно про налоги

Правила (`service/calculation/TaxRules.java`) хранятся редакциями с датой вступления в силу. К операции
применяется редакция на её дату; для периодов раньше первой редакции (2025-01-01) расчёт не делается и
явно помечается — новые ставки не переносятся на прошлое. Новая редакция — ещё одна запись `YearRules`.

У каждого значения есть основание (`LegalBasis`) и статус проверки, который виден в расчёте:

| Статус | Значит |
|---|---|
| `OFFICIAL_TEXT` | Сверено с текстом Налогового кодекса на arlis.am |
| `OFFICIAL_SUMMARY` | Подтверждено официальным порталом (hartak.am), текст закона не сверялся |
| `SECONDARY_SOURCES` | Совпадает в нескольких профессиональных обзорах |
| `UNCONFIRMED` | Источники расходятся или не найдены — используется как допущение |

Сейчас **ни одно значение не сверено с текстом кодекса** (arlis.am был недоступен при подготовке):
- ставка 10% для торговли с 01.01.2025 — `OFFICIAL_SUMMARY` (hartak.am);
- 10% для услуг, 1% для High-Tech, пороги 24 и 115 млн ֏, срок 20-го числа после квартала,
  выбор режима до 20 февраля, база — оборот без вычета расходов — `SECONDARY_SOURCES`
  (по пороговой сумме для налога с оборота один источник называет 120 млн);
- ставка 10% для консультаций, инжиниринга, маркетинга, дизайна и перевода, списки запрещённых видов
  деятельности, округление до драма за период, срок годового отчёта микробизнеса — `UNCONFIRMED`.

**Перед работой с клиентами сверьте эти значения с Налоговым кодексом РА и бухгалтером.**
Общая система (НДС, налог на прибыль) не рассчитывается. Подоходный налог и соцплатежи за работников не учитываются.
Возвраты покупателям автоматически оборот не уменьшают — порядок не подтверждён, отчёт просит решения пользователя.
Оборот считается по дате поступления денег.

### Внешние интеграции

- **Банки.** Открытого банкинга с обязательным API в Армении пока нет (ЦБ РА называет его приоритетом,
  регулирование не вступило в силу), официальных API банков для сторонних приложений не найдено.
  Pulsar не подключается к банкам и не просит пароли от интернет-банка — только загрузка выписок.
- **Подача отчётов.** API КГД для подачи отчётов сторонними приложениями не найден. Отчёт готовится для подачи
  пользователем в системе электронной отчётности КГД; адрес системы уточните на src.am.
- **Распознавание.** Локальный tesseract (`PULSAR_OCR_TESSERACT`, в Docker-образе есть) — файлы не уходят
  во внешние сервисы. Сканы PDF без текстового слоя не распознаются — реквизиты вводятся вручную.

## Что дальше

1. Сверить правила с текстом Налогового кодекса и поднять их статус до `OFFICIAL_TEXT`.
2. Готовые настройки импорта для выписок конкретных армянских банков.
3. Распознавание сканов PDF (растеризация страниц для tesseract).
4. Ошибки сервера на армянском и английском.
5. Сброс пароля по почте.
