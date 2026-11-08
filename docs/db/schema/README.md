# Схема локальной БД fingen.db по версиям

Диаграммы PlantUML для каждой версии `DATABASE_VERSION` 16..36 (по одной на файл). Текущая схема — v36 (`v36.puml`). Схема v35, нарисованная вручную, лежит в `../fingen_schema.puml`; она не обновлялась под v36.

Рендеринг: `plantuml -tpng docs/db/schema/v16.puml` (картинки в репозиторий не добавлены). Синтаксис проверен `plantuml -checkonly`.

## Как восстановлены версии

- v16: фикстура `app/src/androidTest/assets/migrations/fingen_v16.sql` (сама восстановлена по `UpdateHelper.tableInfo17`, реальной БД v16 не было).
- v17..v34: DDL миграций `UpdateHelper.update17..update33` (и `DBHelper.onUpgrade`) применён по порядку через `sqlite3` к схеме v16 (`PRAGMA legacy_alter_table=ON`, `foreign_keys=OFF`, как на Android). Данные не переносились, учтена только схема.
- v35: DDL `DBHelper.onCreate` (свежая установка), проверено по `fixtures/small.db`.
- v36: `SchemaV36` (DDL и индексы итогового состояния), миграция — `Migration36`.
- Сверка с фикстурами v21, v23, v30, v34 — таблицы, колонки, типы, NOT NULL, PK, UNIQUE, FK, индексы совпали; отличия описаны ниже.

## Индекс версий

Колонка «Таблиц» — число пользовательских таблиц (без `sqlite_sequence`, `android_metadata`).

| Версия | Файл | Таблиц | Что изменилось относительно предыдущей | Источник | Фикстура-эталон |
|---|---|---|---|---|---|
| 16 | [v16.puml](v16.puml) | 15 | Базовая версия: без служебных колонок синхронизации, без FK и UNIQUE, без индексов, без `ref_Senders`; `log_Transactions.DateTime` и `log_Incoming_SMS.Sender` — TEXT | `tableInfo17` | `fingen_v16.sql` (восстановлена из кода) |
| 17 | [v17.puml](v17.puml) | 16 | + `ref_Senders`; `log_Incoming_SMS.Sender` TEXT (телефон) -> INTEGER (id отправителя) | `update17` | нет |
| 18 | [v18.puml](v18.puml) | 16 | Пересборка всех таблиц: колонки `FBID, TS, Deleted, Dirty, LastEdited`, `UNIQUE (..., Deleted)`, внешние ключи; `DateTime` -> INTEGER; в `ref_Senders` ещё есть inline UNIQUE по `Name` и `PhoneNo` | `update18` | нет |
| 19 | [v19.puml](v19.puml) | 16 | `ref_Senders` пересоздана без inline UNIQUE | `update19` | нет |
| 20 | [v20.puml](v20.puml) | 16 | + `log_Templates.Comment` | `update20` | нет |
| 21 | [v21.puml](v21.puml) | 16 | Только данные (NULL `Comment` -> ''); схема как v20 | `update21` | `fingen_v21.sql` |
| 22 | [v22.puml](v22.puml) | 16 | `ref_Categories.ParentCategory` -> `ParentID`; + `ParentID`, `OrderNumber` в `ref_Payees`, `ref_Projects`, `ref_Locations`, `ref_Departments`; UNIQUE (Name, ParentID, Deleted) | `update22` | нет |
| 23 | [v23.puml](v23.puml) | 16 | + `ref_Currencies.OrderNumber` | `update23` | `fingen_v23.sql` |
| 24 | [v24.puml](v24.puml) | 18 | + `ref_Accounts_Sets`, `log_Accounts_Sets`; индекс `idx` на `log_Transactions` | `update24` | нет |
| 25 | [v25.puml](v25.puml) | 18 | + `ref_SimpleDebts.StartAmount` (NOT NULL DEFAULT 0), `ref_SimpleDebts.Currency` | `update25` | нет |
| 26 | [v26.puml](v26.puml) | 18 | Схема не меняется (создаёт `idx`, только если индекса нет) | `update26` | нет |
| 27 | [v27.puml](v27.puml) | 19 | `FullName`, `SearchString` в 5 справочниках; `SearchString` в `ref_Accounts`, `ref_SimpleDebts`, `log_Templates`; + `log_Running_Balance` и 3 индекса | `update27` | нет |
| 28 | [v28.puml](v28.puml) | 19 | Только данные (пересчёт `log_Running_Balance`); схема как v27 | `onUpgrade` (без метода в `UpdateHelper`) | нет |
| 29 | [v29.puml](v29.puml) | 19 | + `log_Transactions.SearchString`, `ref_Senders.AddCreditLimitToBalance` | `update29` | нет |
| 30 | [v30.puml](v30.puml) | 19 | + `log_Transactions.FN`, `FD`, `FP` | `update30` | `fingen_v30.sql` |
| 31 | [v31.puml](v31.puml) | 19 | + `ref_Projects.Color` | `update31` | нет |
| 32 | [v32.puml](v32.puml) | 21 | + `log_Transactions.Split`; + `ref_Products` (строка `_id = 0`), `log_Products`; индекс `idx_Products` | `update32` | нет |
| 33 | [v33.puml](v33.puml) | 21 | Только данные (`log_Products` для транзакций без товаров); схема как v32 | `update33` | нет |
| 34 | [v34.puml](v34.puml) | 21 | Схема не меняется | `onUpgrade`: `update33` под условием `< 34` | `fingen_v34.sql` |
| 35 | [v35.puml](v35.puml) | 21 | Схема не меняется; в `onUpgrade` добавлен только пересчёт баланса `if (oldVersion < 25)` | `onCreate`, `onUpgrade` | `fixtures/small.db` |
| 36 | [v36.puml](v36.puml) | 21 | Пересборка всех таблиц: FK включаются в `onOpen`; пустые ссылки NULL; `DecimalCount` 0..8; деньги INTEGER в минорных единицах, `ExchangeRate` ×10¹², `Quantity` ×10³; удалены `FBID`, `TS`, `Dirty`, `LastEdited`, `ref_Debts.SrcAccount`, `ref_Locations.Radius`, `ref_Categories.Sign`; `FN`/`FD`/`FP` сохранены; колонки переименованы (`*Id`, `IsClosed`, `IsSplit` и др.); FK у `log_Running_Balance` и `log_Incoming_SMS`; уникальные индексы `WHERE Deleted = 0` | `SchemaV36`, `Migration36` | нет (фикстура v35 есть, ассетов v36 нет) |

## Различия «свежая установка» и «апгрейд»

v35 нарисована по `onCreate`, v17..v34 — по цепочке миграций. Между ними, помимо порядка колонок и регистра `isActive`/`IsActive` (не влияет на SQLite), есть одно содержательное отличие:

- `ref_SimpleDebts.StartAmount`: после `update25` — `NOT NULL DEFAULT 0`, при свежей установке (`onCreate`) — `NOT NULL` без DEFAULT. В `docs/db/fingen_schema.puml` указан DEFAULT 0 (это путь апгрейда).
- Порядок колонок у апгрейженной БД: добавленные `ALTER TABLE` колонки идут в конце (`log_Transactions`: `SearchString, FN, FD, FP, Split`; `ref_Projects.Color` последняя; `log_Templates.Type` до `Comment`); при свежей установке порядок другой. Для SQL по именам колонок это неважно.

## Особенности миграций (описание, код не менялся)

- Порядок вызовов в `DBHelper.onUpgrade`: `update17`..`update27`, пересчёт баланса (`< 28`), `update29`..`update32`, `update33` под условием `oldVersion < 34`, затем ещё один пересчёт баланса под `oldVersion < 25`.
- Метода `update28` нет: для `oldVersion < 28` вызывается только `updateRunningBalance`.
- `update33` вызывается под `< 34`, а не `< 33`; по имени метод относится к v33, фактически это шаг 33 -> 34. Схема v32, v33, v34 и v35 одинакова.
- Пересчёт `log_Running_Balance` при `oldVersion < 25` выполняется трижды (`update27`, блок `< 28`, финальный блок `< 25`).
- Условие `oldVersion < 25` в конце добавлено в версии 2.4.1 (бамп 34 -> 35) и относится к пересчёту баланса, а не к `update25`.
- v36 (`Migration36`): пересборка всех таблиц (новая таблица `X_new`, копирование с преобразованием, переименование); перед первым шагом `prepare` проверяет свободное место и копирует БД в `fingen.db.pre36`; миграция идёт на экране `ActivityDbUpgrade` (`DbUpgradeGate`, фоновый поток, этапы: подготовка, конвертация, баланс, проверка). SMS с `Sender` 0, -1 или висящей ссылкой привязываются к служебному отправителю `(unknown)`; потерянные ссылки заменяются служебными записями `(lost)`. `pre36` удаляется после первого успешного бэкапа (в папку бэкапов или Dropbox). `RunningBalance.rebuild` и `updateFullNames` для миграции — замороженные копии `Frozen36`.
