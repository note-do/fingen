# Fingen — правила для оркестратора

Android-приложение учёта доходов и расходов. Пакет: `com.yoshione.fingen`, код в `app/src/main/java/com/yoshione/fingen/`.

Этот файл описывает текущее состояние проекта. При реализации новой функции или значимой миграции обновлять соответствующие разделы в том же коммите.

## Текущее состояние (CloudMD)

### Сборка и версии
- Android Gradle Plugin 9.5.0, Gradle 9.8.2 (`gradle/wrapper`, wrapper в git).
- JDK 25 для запуска Gradle и сборки. Уровень языка и байткода Java 21 (Kotlin-компонент AGP 9.5 не принимает target 25). Код написан в стиле Java 8, новых API не требует.
- `compileSdk` 36, `targetSdk` 36, `minSdk` 26.
- Зависимости AndroidX (appcompat, material, preference, recyclerview, constraintlayout, cardview, legacy-support-v13). `android.useAndroidX=true`. `android.enableJetifier` убран: в `prodDebugRuntimeClasspath` нет артефактов `com.android.support`.
- Флаги `gradle.properties`, которые нужны сейчас:
  - `android.nonFinalResIds=false` — `switch` по `R.id` требует константных id;
  - `android.enableAppCompileTimeRClass=false` — без него AGP 9 не компилирует `switch` по `R.id`.
- `buildConfig` и `resValues` включаются явно в `buildFeatures`; `viewBinding` включён.
- `keystore.properties` больше не требует `inAppLicenseKey` и `inAppLicenseSalt` (нужны `mapsApiKey`, `storeFile`, `storePassword`, `oAuthWebClientID`, `keyAlias`, `keyPassword`).
- ProGuard/R8: `proguard-android-optimize.txt` и `-dontoptimize` в `app/proguard-rules.pro` — поведение release прежнее. Оптимизации выключены, release-сборка не проверялась.

### Библиотеки (актуальные)
- UI: Material 1.14.0 (`com.google.android.material`) и `coordinatorlayout` 1.1.0 (явно); тема AppCompat сохранена (`Theme.AppCompat`, MaterialComponents не используется; используются AppBarLayout, FloatingActionButton, NavigationView, TabLayout, TextInputLayout — работают на AppCompat-теме), `NavigationView` + `DrawerLayout` (drawer), `androidx.preference` (настройки), `FlipView` собственный (`widgets/FlipView`). Нижние шторки на экранах транзакций, счетов и бюджета — `BottomSheetBehavior` (корень `FrameLayout`, шторка в отдельном `CoordinatorLayout`, высота `peekHeight` и нижний отступ контента задаются в `SumsTableOnGlobalLayoutListener`; parallax списка бюджета не сохранён).
- Привязка views: ViewBinding (`*Binding`, `getContentRoot()` в `ToolbarActivity`). ButterKnife удалён. Views из `<include>` и динамические layout берутся через `findViewById`.
- Права: PermissionsDispatcher 4.9.2 (`@RuntimePermissions`); для SMS. Доступ к хранилищу для бэкапов не запрашивается.
- Бэкапы: локальная папка выбирается пользователем через SAF (`ACTION_OPEN_DOCUMENT_TREE` + persistable permission), работа через `DocumentsContract` в `utils/BackupFolder` (без `androidx.documentfile`). Архив строится в `cacheDir/backup`, затем копируется в выбранную папку; в кэше остаётся последний файл. Восстановление: список .zip из папки и «Другой файл…» (`ACTION_OPEN_DOCUMENT`), файл копируется в кэш и идёт в прежний `restoreDB`.
- Восстановление БД (`DBHelper.restoreDB`): извлечённый файл проверяется, перед заменой соединение закрывается (синглтон сбрасывается, `-journal`/`-wal`/`-shm` удаляются), текущая БД переименовывается в `fingen.db.bak` (при ошибке замены — откат). После закрытия соединения приложение перезапускается при любом исходе; ошибка замены показывается после перезапуска по флагу `restore_failed` (`ActivityMain.checkRestoreFailed`); при неудачном откате данные в `fingen.db.bak`, при следующем старте создаётся новая пустая `fingen.db` (`onCreate`), автооткат не реализован; `.bak` удаляется после успешного открытия БД в конструкторе `DBHelper` (`DBHelper.deleteRestoreBackup`) только если замена прошла (флаг `restore_bak_pending`, при неудаче сбрасывается в false в той же записи, что и `restore_failed`), при неудачном откате `.bak` не удаляется; если открытие бросает исключение, `.bak` остаётся. Откат на `.bak` при неудачном открытии не реализован. Проверка извлечённого файла: `quick_check` и `user_version` в пределах 16..`DATABASE_VERSION`.
- DI: Dagger 2.60.1 (`di/`).
- Реактивность: RxJava 3.1.12 (`io.reactivex.rxjava3`, `core.Single`), RxAndroid 3.0.2 (`rxjava3.android.schedulers`).
- Шифрование бэкапов: bcprov-jdk18on 1.86 (`utils/winzipaes/impl/*BC`, WinZip AES, PBKDF2-SHA1); проверяется `AESEncrypterRoundTripTest`.
- Утилиты и тесты: commons-lang3 3.21.0, junit 4.13.2.
- Dropbox: `dropbox-core-sdk` и `dropbox-android-sdk` 8.0.2, вход через PKCE (`Auth.startOAuth2PKCE`), `DbxCredential` JSON хранится зашифрованным (`SecureStore`) в prefs `com.yoshione.fingen.dropbox`, ключ `dropbox-credential-enc` (старый открытый `dropbox-credential` мигрирует однократно, `DropboxClient.migrateCredential`). Приложение App folder `Fin_Gen`, ключ `zaqelu1694tr792` задан в `app/build.gradle` (resValue `DROPBOX_APP_KEY` и схема `db-<ключ>` в манифесте). Старые токены удаляются однократно (`DropboxClient.migrateLegacyToken`), нужен повторный вход. `minSdk` поднят до 26 из-за `dropbox-android-sdk`.
- События: EventBus 3.3.1.
- Фоновые задачи: WorkManager 2.12.0 (`androidx.work:work-runtime`). `backup/BackupWorker` — ежедневный бэкап в окне 01:00–06:00 локального времени, цепочка OneTime (следующий запуск ставится только при итоговом результате, не при retry), регистрация в `FGApplication.onCreate` через `enqueueUniqueWork` с `KEEP`. При ошибке Dropbox — `Result.retry()`, не более 3 попыток; `RuntimeException` → `success` (без retry); копия в папку только на первой попытке (`getRunAttemptCount() == 0`).
- Справка: `taptargetview` через `utils/HelpSequence` (вместо MaterialShowCase).
- Липкие заголовки списка транзакций: собственная `widgets/StickyHeaderDecoration` (ItemDecoration; интерфейс `StickyHeaderAdapter` — getHeaderId/onCreateHeaderViewHolder/onBindHeaderViewHolder, реализует `AdapterTransactions`). Готовой поддерживаемой AndroidX-библиотеки нет.
- Changelog: `res/raw/changelog.xml` (и `raw-ru`), диалог — `FragmentChangelog` (ListView).
- Сканер и PIN-код: PIN-защита своя — `lock/PinLockManager` и `lock/PinActivity` (lollipin удалён, старые PIN сброшены).
- Лицензии: `licensesdialog` 2.2.0 (`ActivityAbout`, `.show()`; `showAppCompat` в 2.x удалён), список — `res/raw/license.xml` (синхронизировать с зависимостями `app/build.gradle`).
- Выбор цвета: skydoves `colorpickerview` 2.5.0 (Maven Central, `ColorPickerDialog.Builder`).
- CSV импорт и экспорт: Storage Access Framework (`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`), файл передаётся в `CsvImporter` через временную копию в `getCacheDir()`. `loadFingenCSV`, `loadCustomCSV` и `loadFinancistoCSV` закрывают транзакцию БД в `finally` (раньше при исключении в цикле строк транзакция оставалась открытой).
- Карта выбора места: `play-services-maps` 20.1.0 (`SupportMapFragment` + `OnMapReadyCallback` в `ActivityEditLocation2`, маркер `R.mipmap.icon_location_pin`). Ключ Google Maps не задан: тайлы не грузятся, пока в `keystore.properties` нет `mapsApiKey` (см. «Известные особенности»).
- Быстрая прокрутка: свой `widgets/FgFastScrollRecyclerView` (полоса справа, перетаскивание, bubble с датой для `AdapterTransactions` через `SectionIndexer`); `ContextMenuRecyclerView` наследует его.
- Графики отчётов: MPAndroidChart v4.0.1 (`com.github.PhilJay.MPAndroidChart:MPChartLib`, JitPack; API Kotlin: `Easing.INSTANCE`, `ColorTemplate.INSTANCE`, `highlightValues` не принимает null). Подписи баров — `IndexAxisValueFormatter`, пары доход/расход смещены вручную (±0,2, ширина 0,4). Правило ProGuard `-keep class com.github.mikephil.charting.** { *; }` оставлено (consumer rules в aar нет).
- БД: `android.database.sqlite` (framework SQLite). requery удалён.

### Удалено (не возвращать)
- Fabric / Crashlytics / Answers.
- ФНС-чеки (`fts/`, Retrofit, OkHttp logging, decoro, qrcodereaderview).
- fastscroll (l4digital), lollipin, materialdrawer, xpece support-preference, changeloglib, MaterialShowCase, HeaderDecor (aar), requery, ButterKnife, flipview (библиотека), QuadFlask colorpicker, stickyheadersrecyclerview (Timehop, support-классы; заменён на `StickyHeaderDecoration`), slidinguppanel (заменён на `BottomSheetBehavior`), filepicker (angads25; заменён на SAF).
- `com.evernote:android-job` (`BackupJob`, `BackupTestJob` и их creator-ы), его компоненты в манифесте и ProGuard-правила.
- actionitembadge, iconics-core (бейдж SMS теперь `BadgeDrawable` из Material, создаётся в обёртке темы MaterialComponents, `ActivityMain`).
- `com.airbnb.android:airmapview` (заменён на `play-services-maps`), его запись в `res/raw/license.xml`.
- Мёртвые ресурсы materialdrawer и `material_drawer_*`.
- Платные отчёты и биллинг: `iab` (anjlab), `ActivityPro`, XOR-ключ лицензии, `resValue` `inAppLicenseKey`/`inAppLicenseSalt`. Отчёты бесплатны для всех.

### Миграция в процессе (план)
- Google Drive — отдельная фича позже.
- Обновление библиотек выполнено: Dagger 2.60.1, EventBus 3.3.1, commons-lang3 3.21.0, junit 4.13.2, RxJava 3 и RxAndroid 3, bcprov-jdk18on 1.86.
- БД: тесты миграций с фикстурами версий 16, 21, 23, 30, 34 → 35. `journal_mode` проверен (`JournalModeBackupTest`): на эмуляторе API 37 без явной настройки режим `truncate` (не WAL), `-wal` не создаётся, `backupDB` (только `fingen.db`) содержит все записи — риска потери нет; при включении WAL `backupDB` надо менять (в архив попадает только `fingen.db`, `-wal` не включается); `restoreDB` удаляет `-wal`/`-shm` старой БД при замене. `legacy_alter_table` проверен (`LegacyAlterTableTest`): на эмуляторе API 37 (SQLite 3.50.6) по умолчанию 1, ссылки на `*_old` после миграций 16/21 → 35 не остаются; при OFF остались бы в `log_Templates`, `log_Transactions`, `ref_Accounts`, `ref_Debts`, `ref_budget` (данные те же) — важно при включении `foreign_keys`. Дефекты миграций исправлены (тесты в `DatabaseMigrationTest`): `update29` — NULL `Comment` даёт пустой `SearchString` (раньше NPE); `update17` — отправители из маркеров Type=5 пишутся SQL-вставкой без `SendersDAO` (раньше зависание: DAO ждёт конца апгрейда), опечатка `BalcancePos` исправлена (она ронял бы вставку); `update17` сохраняет все SMS: отправитель ищется среди маркеров по `trim().toLowerCase()` значения `Sender`, иначе создаётся запись `ref_Senders` (Name = PhoneNo = исходное значение, при занятом Name — `<значение> (SMS)`), пустой `Sender` даёт 0. Не проверено на реальных БД и на реальных данных с маркерами Type=5.
- Совместимость бэкапов: читать старые зашифрованные (WinZip AES, PBKDF2-SHA1) и незашифрованные бэкапы. Проверено (`BackupFileCompatibilityTest`): незашифрованный и зашифрованный zip текущей версии читаются через `FileUtils`, неверный пароль даёт `onWrongPassword`, в архиве только `fingen.db`; пароль кодируется `getBytes()` (на Android UTF-8). Нужна реальная старая фикстура (WinZip AES, приложение 2.x, в т.ч. с кириллицей в пароле) — два теста `@Ignore`.
- targetSdk 36 — сделано (PendingIntent, exported, SAF-бэкапы, `POST_NOTIFICATIONS`, edge-to-edge, поворот на планшетах).
- Безопасность: сделано — `CustomIntentReceiver` закрыт, секреты (пароль бэкапа, credential Dropbox) в `SecureStore` и вне Auto Backup. PIN хранится как SHA-256 с солью (`lock/PinLockManager`).
- Убрать `nonFinalResIds`, `enableAppCompileTimeRClass` после перехода (предупреждения AGP).

### Известные особенности
- Внешний API `CustomIntentReceiver` (CREATE_TRANSACTION, CREATE_SMS): открыт только в debug (`app/src/debug/AndroidManifest.xml`, `exported="true"` + intent-filter), в release закрыт (main-манифест `exported="false"`, без intent-filter). Динамической регистрации нет (дубли broadcast); код класса сохранён. Тесты — `androidTest/.../api/CustomIntentApiTest`.
- Uri папки бэкапов — в prefs (`FgConst.PREF_BACKUP_FOLDER_URI`). При смене папки старое persistable-разрешение снимается. Доступность — `BackupFolder.isAvailable` (разрешение в `getPersistedUriPermissions` + папка существует и принимает файлы).
- Папка не выбрана, доступ отозван или копирование не удалось: `BackupWorker` пропускает локальную копию, Dropbox работает как обычно, выставляется `PREF_BACKUP_FOLDER_UNAVAILABLE`. `ActivityMain` один раз за запуск процесса показывает диалог-предупреждение (по флагу или `isAvailable == false`). Флаг сбрасывается при выборе папки и успешной копии.
- Копия перед миграцией БД (`DBHelper.onUpgrade` → `backupDB(false)`) пишется только в `cacheDir/backup`; `backupDB(vacuum, true)` дополнительно копирует в выбранную папку. Тесты миграций смотрят `BackupFolder.getCacheDir`.
- Ключ Google Maps: `manifestPlaceholders.mapsApiKey` в `app/build.gradle` берётся из `keystore.properties` только если там есть все ключи подписи вместе с `mapsApiKey`, иначе пустая строка. Без ключа экран выбора места открывается, но карта пустая (в logcat `Authorization failure`), маркер и тап по карте не проверялись. Для карты: добавить `mapsApiKey` в `keystore.properties`. `my location` включается только при выданном `ACCESS_FINE_LOCATION`.
- Требования targetSdk 36: у компонентов с intent-filter явный `android:exported`; все `PendingIntent` с `FLAG_IMMUTABLE`; `registerReceiver` только через `ContextCompat.registerReceiver` с `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED`; «назад» — `OnBackPressedCallback` через `getOnBackPressedDispatcher()` (не `onBackPressed`).
- Edge-to-edge (targetSdk 36 включает его принудительно): `utils/WindowInsetsHelper` — `WindowCompat.setDecorFitsSystemWindows(false)` и белые иконки status bar на всех API. `ToolbarActivity.setupWindowInsets()`: верхний inset — padding AppBarLayout (цвет primary уходит под status bar), левый/правый/нижний (navigation bar, cutout, IME) — padding корня; insets потребляются. `ActivityMain` переопределяет метод (корень DrawerLayout: AppBar, `buttonsBarContainer`, боковые padding; NavigationView сам обрабатывает insets через `fitsSystemWindows`). `PinActivity` и `ActivitySettings` вызывают helper сами. `fitsSystemWindows` в layout не использовать (кроме NavigationView). Status/navigation bar прозрачные в обеих темах.
- Поворот: `screenOrientation` в манифесте убран (API 36 игнорирует его при sw >= 600dp). `FGApplication.lockPortraitOnPhones` блокирует portrait на телефонах (sw < 600dp) для всех Activity, кроме `ActivityEditTransaction`, `ActivityEditLocation2`, `ActivityShortcut`; на планшетах и складных поворот свободен.
- Разрешения на хранилище (`READ/WRITE_EXTERNAL_STORAGE`) удалены: файлы только через SAF и кэш.
- Обработчики нажатий, которые раньше были `@OnClick`, используют `utils/DebouncingOnClickListener` (защита от двойного нажатия, как у ButterKnife). Новые обработчики, которые открывают экран или сохраняют данные, тоже оборачивать.
- В `AndroidManifest.xml` у `<application>` убран `tools:node="replace"`: компоненты библиотек (WorkManager и др.) попадают в итоговый манифест от самих библиотек.
- Однократная очистка задач старого android-job: `FGApplication.cancelLegacyJobs()` через `JobScheduler`, флаг `PREF_ANDROID_JOB_MIGRATED`. Можно удалить, когда у пользователей не останется версий с android-job.
- Секреты: `utils/SecureStore` — AES-256-GCM, ключ `fingen_secure_v1` в Android Keystore, формат `v1:` + Base64(iv12 || ciphertext+tag), `encrypt`/`decrypt` не бросают (null при ошибке). Файл секретов — prefs `com.yoshione.fingen.secure`. Пароль бэкапа — `backup/BackupPassword` (ключ `backup_password_enc`; флаг переключателя `enable_backup_password` остаётся в обычных prefs; миграция открытого `backup_password` — `BackupPassword.migrate` в `FGApplication.onCreate`, флаг `secure_migrated_v1`; если Keystore недоступен, открытое значение остаётся до следующего запуска). Ключи prefs — в `FgConst`.
- Auto Backup: `allowBackup="true"`; БД и обычные prefs уходят в облако и на новое устройство, а `res/xml/backup_rules.xml` (API 26–30) и `res/xml/data_extraction_rules.xml` (API 31+, cloud-backup и device-transfer) исключают prefs `com.yoshione.fingen.dropbox` и `com.yoshione.fingen.secure`. Ключ Keystore не переносится.
- Потеря ключа Keystore (новое устройство, сброс экрана блокировки на части OEM, сбой): credential Dropbox, который не расшифровался, удаляется — нужен повторный вход. Если защита бэкапа включена, а пароль не прочитан (в т.ч. на новом устройстве после восстановления настроек из облака), `DBHelper.backupDB` возвращает null и бэкап не создаётся ни локально, ни в Dropbox (в т.ч. `BackupWorker`), выставляется флаг `backup_password_warning`; `ActivityBackup` показывает предупреждение, пока пароль не введён заново. Пароль сохраняется по `onPause`, через 1 с после ввода (не на каждое нажатие), при включении переключателя и перед ручным бэкапом. Безопасная копия перед миграцией БД (`onUpgrade`) в таком состоянии тоже пропускается.
- Предупреждения на главном экране (`ActivityMain.checkBackupFolder`, раз за запуск процесса): папка бэкапов (с чекбоксом «Не напоминать», `PREF_BACKUP_FOLDER_REMINDER_OFF`; гасит и предупреждение о неудачной копии, т.к. флаг `PREF_BACKUP_FOLDER_UNAVAILABLE` ставится и при отсутствии папки) и потерянный пароль защиты (`BackupPassword.hasWarning`, не отключается). Переключатель защиты в `ActivityBackup` не включается без пароля (интерфейс и pref совпадают). Разовое сообщение о сбросе PIN старой библиотеки: `PinLockManager.init` ставит `PREF_PIN_RESET_NOTICE_PENDING`, `ActivityMain.checkPinResetNotice` показывает диалог и снимает флаг. Сообщение о неудачном восстановлении БД — `ActivityMain.checkRestoreFailed` (флаг `restore_failed`).
- Разрешения при первом запуске: `POST_NOTIFICATIONS` запрашивается после ответа на запрос SMS (`@NeedsPermission`/`@OnPermissionDenied`/`@OnNeverAskAgain` в `ActivityMain`).
- Однократная очистка настроек ФНС (`fts_login`, `fts_pass`, `fts_credentials`, `fts_do_not_show_again`): `FGApplication.cleanLegacyFtsPrefs`, флаг `PREF_FTS_PREFS_CLEANED`. Можно удалить вместе с ключами `PREF_FTS_*` в `FgConst`.
- `ActivityEditTransaction` и `ActivityModelList` остаются `exported="true"`: они цели ярлыков (`res/xml/shortcuts.xml`, `ActivityShortcut`).
- Тесты: `app/src/androidTest`: `utils/SecureStoreTest` (шифрование, потеря ключа, потоки), `backup/BackupPasswordTest` (миграция, потеря пароля, зашифрованный бэкап), `CsvImporterTest` (импорт Fingen и пользовательского CSV на маленьких файлах, пустой и некорректный файл, транзакция закрыта после ошибки), `SmsParserTest`, `SmsParserExtractionTest` (сумма, знак, баланс, дата и негативные случаи `SmsParser` на текстах SMS), `AccountsDAOTest`, `TransactionsDAOTest`, `DbSnapshot` (снимок `_id` до теста и откат после — тесты не оставляют данных), `DbSnapshotTest`, `dropbox/DropboxClientTest` (хранение credential и миграция V3, без сети), `db/DatabaseMigrationTest` + `MigrationTestHelper` (миграции на отдельных файлах БД из SQL-фикстур `androidTest/assets/migrations/fingen_v<N>.sql`, фикстуры 16, 21, 23, 30, 34 → 35), `ActivityBackupPasswordToggleTest` (переключатель защиты бэкапа), `LegacyFtsPrefsCleanupTest` (очистка prefs ФНС), `lock/PinLockManagerInitTest` (инициализация PIN), `ScreenLaunchTest`, `ScreenLaunchWithDataTest` (запуск экранов, в т.ч. с данными), `FragmentsLaunchTest` (запуск фрагментов), `MainScreenPermissionsTest` (разрешения на главном экране), `RestoreDbTest` (восстановление БД), `db/JournalModeBackupTest`, `db/LegacyAlterTableTest`, `utils/BackupFileCompatibilityTest`, `utils/AESEncrypterRoundTripTest`, `api/CustomIntentApiTest`, вспомогательные `SystemDialogsRule` и `ScreenTestUtils`. Юнит-тестов в `app/src/test` пока нет.
- SMS-парсер (`utils/SmsParser`): дата транзакции — время получения SMS (дата из текста не извлекается); сумма строится через `new BigDecimal(double)`; отправитель, которого нет в БД, даёт модель по умолчанию (`SendersDAO.getSenderByID` не возвращает `null`), разбор идёт по её позициям суммы и баланса.
- Тесты ДАО — проверка сохранения счетов и транзакций; при падении NPE в `DBHelper.updateFullNames` или `createItem` смотреть `SearchString` и закрытие транзакции.

## Роль оркестратора

Основная сессия разбирает задачу, решает, кому её отдать, и запускает субагента инструментом Agent (`subagent_type` = имя агента из `.claude/agents/`). Сама код не пишет, если задача укладывается в одного субагента.

Маршрутизация:
- **architect** (opus) — проектирование, выбор подходов, trade-off, план изменений. Не редактирует файлы.
- **implementer** (sonnet) — реализация по плану: Java, layout, ресурсы, миграции БД.
- **tester** (haiku) — юнит- и инструментальные тесты, механические переименования, запуск Gradle-задач.
- **reviewer** (opus) — ревью всей ветки относительно `master` перед pull-request. Не пишет код. Запускается отдельно при подготовке PR, не в цепочке разработки.

Порядок: architect → implementer → tester. Для мелкой правки (одна строка, опечатка) оркестратор делает сам.

Параллельно запускать только независимые задачи, которые не трогают одни и те же файлы.

## Режимы работы

### Режим анализа (без правок)

Триггер: пользователь просит проанализировать репозиторий, модуль или состояние проекта без изменения кода.

1. Разбить репозиторий на области: БД (`dao/`, `model/`, `DBHelper`, `DatabaseUpgradeHelper`), UI (Activity, Fragment, `adapter/`, layout), интеграции (`csv/`, SMS-парсер, `backup/`, Dropbox), сборка и зависимости (Gradle, `build.gradle`, библиотеки).
2. На каждую область запустить `architect` параллельно. Задание — самодостаточное: область, файлы, что вернуть. Агенты не видят переписку.
3. Проверки сборки и тестов (`assembleDebug`, `testProdDebugUnitTest`, `lintDebug`) запускать через `tester`, отдельно от `architect`, и только если пользователь их просил или они нужны для вывода.
4. Свести отчёты в один документ: общая картина, риски с приоритетом, рекомендации, открытые вопросы. Выбор варианта оставить пользователю.
5. Результат — в чате. Файлы в репозиторий и внешние системы (Confluence, Jira) не писать без явного разрешения.

Субагенты не запускают других субагентов: вся координация через оркестратор.

### Режим разработки

Триггер: задача на изменение кода.

1. `architect` — план.
2. `implementer` — правки по плану, с `assembleDebug`.
3. `tester` — тесты: во время разработки только тесты затронутой области (классы или пакет, которые меняются, через `-Pandroid.testInstrumentationRunnerArguments.class=...`). Полный `connectedProdDebugAndroidTest` — перед коммитом, который завершает фичу или пункт плана, и перед слиянием.
4. Оркестратор проверяет diff, обновляет раздел «Текущее состояние» этого файла и сообщает пользователю результат с фактическим выводом сборки.

## Сборка и тесты

Команды выполнять из корня проекта. Gradle запускать с JDK 25:

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

- `./gradlew assembleDebug` — сборка debug APK (`app/build/outputs/apk/prod/debug/app-prod-debug.apk`).
- `./gradlew testProdDebugUnitTest` — юнит-тесты (`app/src/test`). Пока тестов нет, задача `NO-SOURCE`.
- `./gradlew connectedProdDebugAndroidTest` — инструментальные тесты, нужны устройство или эмулятор (`emulator-5554`, AVD `Medium_Phone_API_37.0`). Прогон удаляет установленное приложение с устройства.
- `./gradlew lintDebug` — lint.

Android Studio — только редактор. Сборка и тесты идут через Gradle в терминале.

## Правила для всех агентов

- Код стилистически совпадает с окружающим: именование, комментарии, отступы, идиомы. Новый код — ViewBinding, без ButterKnife. Новые зависимости AndroidX, без support-библиотек.
- При реализации новой функции или значимого изменения обновить раздел «Текущее состояние» этого файла в том же коммите.
- Git: коммит допустим после каждого пункта плана по разрешению пользователя (сообщение по пункту, `Co-Authored-By`). Пуш — только по команде пользователя.
- Не трогать `build/`, `.gradle/`, `.idea/`, `local.properties`, `*.jks`, `keystore.properties`.
- Не менять `gradle.properties`, версии зависимостей и `build.gradle` без явного решения оркестратора.
- Не удалять файлы без явного разрешения пользователя.
- Не делать `git commit`, `git push`, `git reset --hard`, `git checkout --` без разрешения пользователя.
- Не трогать SQL-схему и миграции `DatabaseUpgradeHelper` без плана от architect.
- Отчёт субагента: что сделано, какие файлы изменены, результат сборки/тестов (с фактическим выводом при падении).

## Язык

Общение с пользователем и подготовка описаний — на русском. Имена классов, методов, комментарии в коде — по соглашениям проекта (английский).
