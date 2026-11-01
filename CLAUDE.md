# Fingen — правила для оркестратора

Android-приложение учёта доходов и расходов. Пакет: `com.yoshione.fingen`, код в `app/src/main/java/com/yoshione/fingen/`.

Этот файл описывает текущее состояние проекта. При реализации новой функции или значимой миграции обновлять соответствующие разделы в том же коммите.

## Текущее состояние (CloudMD)

### Сборка и версии
- Android Gradle Plugin 9.5.0, Gradle 9.8.2 (`gradle/wrapper`, wrapper в git).
- JDK 25 для запуска Gradle и сборки. Уровень языка и байткода Java 21 (Kotlin-компонент AGP 9.5 не принимает target 25). Код написан в стиле Java 8, новых API не требует.
- `compileSdk` 36, `targetSdk` 28, `minSdk` 26. `targetSdk` 28 — поведение приложения на устройствах; при его повышении нужна отдельная проверка.
- Зависимости AndroidX (appcompat, material, preference, recyclerview, constraintlayout, cardview, legacy-support-v13). `android.useAndroidX=true`, `android.enableJetifier=true` (устаревший флаг, убрать после замены старых библиотек).
- Флаги `gradle.properties`, которые нужны сейчас:
  - `android.nonFinalResIds=false` — `switch` по `R.id` требует константных id;
  - `android.enableAppCompileTimeRClass=false` — без него AGP 9 не компилирует `switch` по `R.id`.
- `buildConfig` и `resValues` включаются явно в `buildFeatures`; `viewBinding` включён.
- `keystore.properties` больше не требует `inAppLicenseKey` и `inAppLicenseSalt` (нужны `mapsApiKey`, `storeFile`, `storePassword`, `oAuthWebClientID`, `keyAlias`, `keyPassword`).
- ProGuard/R8: `proguard-android-optimize.txt` и `-dontoptimize` в `app/proguard-rules.pro` — поведение release прежнее. Оптимизации выключены, release-сборка не проверялась.

### Библиотеки (актуальные)
- UI: Material 1.14.0 (`com.google.android.material`) и `coordinatorlayout` 1.1.0 (явно); тема AppCompat сохранена (`Theme.AppCompat`, MaterialComponents не используется; используются AppBarLayout, FloatingActionButton, NavigationView, TabLayout, TextInputLayout — работают на AppCompat-теме), `NavigationView` + `DrawerLayout` (drawer), `androidx.preference` (настройки), `FlipView` собственный (`widgets/FlipView`).
- Привязка views: ViewBinding (`*Binding`, `getContentRoot()` в `ToolbarActivity`). ButterKnife удалён. Views из `<include>` и динамические layout берутся через `findViewById`.
- Права: PermissionsDispatcher 4.9.2 (`@RuntimePermissions`).
- DI: Dagger 2.16 (`di/`).
- Реактивность: RxJava 2 (`io.reactivex.rxjava2`), RxAndroid 2.1.0. Переход на RxJava 3 — в плане.
- Dropbox: `dropbox-core-sdk` и `dropbox-android-sdk` 8.0.2, вход через PKCE (`Auth.startOAuth2PKCE`), `DbxCredential` JSON в prefs `com.yoshione.fingen.dropbox`, ключ `dropbox-credential`. Приложение App folder `Fin_Gen`, ключ `zaqelu1694tr792` задан в `app/build.gradle` (resValue `DROPBOX_APP_KEY` и схема `db-<ключ>` в манифесте). Старые токены удаляются однократно (`DropboxClient.migrateLegacyToken`), нужен повторный вход. `minSdk` поднят до 26 из-за `dropbox-android-sdk`.
- События: EventBus 3.1.1.
- Фоновые задачи: WorkManager 2.12.0 (`androidx.work:work-runtime`). `backup/BackupWorker` — ежедневный бэкап в окне 01:00–06:00 локального времени, цепочка OneTime (воркер в конце ставит следующий запуск), регистрация в `FGApplication.onCreate` через `enqueueUniqueWork` с `KEEP`. При ошибке Dropbox — `Result.retry()`, не более 3 попыток.
- Справка: `taptargetview` через `utils/HelpSequence` (вместо MaterialShowCase).
- Липкие заголовки списка транзакций: `stickyheadersrecyclerview` 0.4.3 (вместо HeaderDecor).
- Changelog: `res/raw/changelog.xml` (и `raw-ru`), диалог — `FragmentChangelog` (ListView).
- Сканер и PIN-код: PIN-защита своя — `lock/PinLockManager` и `lock/PinActivity` (lollipin удалён, старые PIN сброшены).
- Лицензии: `licensesdialog` 2.2.0 (`ActivityAbout`, `.show()`; `showAppCompat` в 2.x удалён), список — `res/raw/license.xml` (синхронизировать с зависимостями `app/build.gradle`).
- Прочее: `fastscroll` (JitPack, `@aar`), `actionitembadge`, `iconics-core` (нужен `actionitembadge`), `slidinguppanel`, `airmapview`, `filepicker`, `colorpicker`, `MPAndroidChart` — заброшенные, см. «Миграция в процессе».
- БД: `android.database.sqlite` (framework SQLite). requery удалён.

### Удалено (не возвращать)
- Fabric / Crashlytics / Answers.
- ФНС-чеки (`fts/`, Retrofit, OkHttp logging, decoro, qrcodereaderview).
- lollipin, materialdrawer, xpece support-preference, changeloglib, MaterialShowCase, HeaderDecor (aar), requery, ButterKnife, flipview (библиотека).
- `com.evernote:android-job` (`BackupJob`, `BackupTestJob` и их creator-ы), его компоненты в манифесте и ProGuard-правила.
- Мёртвые ресурсы materialdrawer и `material_drawer_*`.
- Платные отчёты и биллинг: `iab` (anjlab), `ActivityPro`, XOR-ключ лицензии, `resValue` `inAppLicenseKey`/`inAppLicenseSalt`. Отчёты бесплатны для всех.

### Миграция в процессе (план)
- Google Drive — отдельная фича позже.
- Заменить заброшенные библиотеки: slidinguppanel → BottomSheet, actionitembadge → BadgeDrawable, airmapview → play-services-maps, filepicker → SAF, MPAndroidChart → v3; colorpicker — обновить.
- RxJava 2 → 3, Dagger и RxAndroid до актуальных, commons-lang3 и bcprov (CVE), junit.
- БД: тесты миграций с фикстурами версий 16, 21, 23, 30, 34 → 35; проверить `legacy_alter_table` (ALTER TABLE RENAME) и `journal_mode`.
- Совместимость бэкапов: читать старые зашифрованные (WinZip AES, PBKDF2-SHA1) и незашифрованные бэкапы.
- targetSdk 28 → 35+: `FLAG_IMMUTABLE` у PendingIntent, `android:exported`, Scoped Storage (бэкапы и CSV), `POST_NOTIFICATIONS`.
- Безопасность: `allowBackup="true"`, экспортированный `CustomIntentReceiver`, пароль бэкапа в SharedPreferences, токен Dropbox в открытых prefs.
- Убрать `android.enableJetifier`, `nonFinalResIds`, `enableAppCompileTimeRClass` после перехода (предупреждения AGP).

### Известные особенности
- Обработчики нажатий, которые раньше были `@OnClick`, используют `utils/DebouncingOnClickListener` (защита от двойного нажатия, как у ButterKnife). Новые обработчики, которые открывают экран или сохраняют данные, тоже оборачивать.
- В `AndroidManifest.xml` у `<application>` убран `tools:node="replace"`: компоненты библиотек (WorkManager и др.) попадают в итоговый манифест от самих библиотек.
- Однократная очистка задач старого android-job: `FGApplication.cancelLegacyJobs()` через `JobScheduler`, флаг `PREF_ANDROID_JOB_MIGRATED`. Можно удалить, когда у пользователей не останется версий с android-job.
- Тесты: `app/src/androidTest`: `SmsParserTest`, `AccountsDAOTest`, `TransactionsDAOTest`, `DbSnapshot` (снимок `_id` до теста и откат после — тесты не оставляют данных), `DbSnapshotTest`, `dropbox/DropboxClientTest` (хранение credential и миграция V3, без сети). Юнит-тестов в `app/src/test` пока нет.
- Тесты ДАО — проверка сохранения счетов и транзакций; при падении NPE в `DBHelper.updateFullNames` или `createItem` смотреть `SearchString` и закрытие транзакции.

## Роль оркестратора

Основная сессия разбирает задачу, решает, кому её отдать, и запускает субагента инструментом Agent (`subagent_type` = имя агента из `.claude/agents/`). Сама код не пишет, если задача укладывается в одного субагента.

Маршрутизация:
- **architect** (opus) — проектирование, выбор подходов, trade-off, план изменений. Не редактирует файлы.
- **implementer** (sonnet) — реализация по плану: Java, layout, ресурсы, миграции БД.
- **tester** (haiku) — юнит- и инструментальные тесты, механические переименования, запуск Gradle-задач.

Порядок: architect → implementer → tester. Для мелкой правки (одна строка, опечатка) оркестратор делает сам.

Параллельно запускать только независимые задачи, которые не трогают одни и те же файлы.

## Режимы работы

### Режим анализа (без правок)

Триггер: пользователь просит проанализировать репозиторий, модуль или состояние проекта без изменения кода.

1. Разбить репозиторий на области: БД (`dao/`, `model/`, `DBHelper`, `DatabaseUpgradeHelper`), UI (Activity, Fragment, `adapter/`, layout), интеграции (`csv/`, SMS-парсер, `backup/`, Dropbox, биллинг), сборка и зависимости (Gradle, `build.gradle`, библиотеки).
2. На каждую область запустить `architect` параллельно. Задание — самодостаточное: область, файлы, что вернуть. Агенты не видят переписку.
3. Проверки сборки и тестов (`assembleDebug`, `testProdDebugUnitTest`, `lintDebug`) запускать через `tester`, отдельно от `architect`, и только если пользователь их просил или они нужны для вывода.
4. Свести отчёты в один документ: общая картина, риски с приоритетом, рекомендации, открытые вопросы. Выбор варианта оставить пользователю.
5. Результат — в чате. Файлы в репозиторий и внешние системы (Confluence, Jira) не писать без явного разрешения.

Субагенты не запускают других субагентов: вся координация через оркестратор.

### Режим разработки

Триггер: задача на изменение кода.

1. `architect` — план.
2. `implementer` — правки по плану, с `assembleDebug`.
3. `tester` — тесты и `testProdDebugUnitTest`, `connectedProdDebugAndroidTest`.
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
