# TurboFetch — техдок по стеку

Документ для разработчика: что внутри, как устроены слои, почему именно такие
версии библиотек и где спрятаны грабли. Без маркетинговых формулировок.

---

## 0. Что это за приложение

Нативное Android-приложение, которое скачивает видео и аудио **напрямую с CDN
площадки**. Не агрегатор и не прокси: приложение умеет только разобрать ссылку
и получить прямой URL файла, дальше байты идут с CDN мимо сервера приложения.

Отсюда вся архитектура: нет своего бэкенда, нет серверной части, нет рекламы и
аналитики. Всё, что делает приложение, — резолв, скачивание, склейка и запись
в MediaStore.

---

## 1. Стек

Инструменты:

- Kotlin **2.2.10** — но плагин `kotlin-android` **не подключается**: в AGP 9
  Kotlin встроен, версия берётся из AGP (см. `kotlin { compilerOptions { ... } }`
  в `app/build.gradle.kts`).
- AGP **9.5.1**, KSP **2.2.10-2.0.2**.
- compileSdk **37**, targetSdk **36**, minSdk **26**, JVM target **17**.

Библиотеки (версии — в `gradle/libs.versions.toml`):

- Compose BOM **2026.09.00**, Material3, Navigation Compose **2.10.2**,
  Lifecycle **2.11.0** (runtime, viewmodel-compose, runtime-compose).
- Room **2.8.5** + KSP (компилятор Room), `exportSchema = true`,
  схемы лежат в `app/schemas`.
- WorkManager **2.12.0** — вся фоновая загрузка.
- OkHttp **5.5.1** (в юнит-тестах — `okhttp-jvm`, см. грабли).
- kotlinx-serialization **1.9.0**, coroutines **1.11.0**, Coil **3.3.0**.
- NewPipeExtractor **0.26.5** (с jitpack) — резолвер YouTube.
- `desugar_jdk_libs_nio` **2.1.5** — core library desugaring.

### Почему версии зафиксированы так

Это главная ловушка проекта. Компилятор встроен в AGP и равен 2.2.10, а свежие
версии библиотек требуют stdlib 2.3/2.4, которую этот компилятор не читает:

- `kotlinx-serialization` 1.10+ тянет stdlib 2.3/2.4 → не компилируется,
  поэтому стоит 1.9.0 (stdlib 2.2.0);
- `coil` 3.4+ — та же история → стоит 3.3.0.

Обе причины записаны комментариями прямо в `libs.versions.toml`. Не обновляй
эти версии «на свежую» без проверки: сначала смотри, какой stdlib требует
новый релиз.

---

## 2. Слои и направление зависимостей

```
ui/        Compose-экраны и ViewModel'ы
worker/    DownloadWorker + DownloadScheduler (WorkManager)
domain/    чистый Kotlin: модели, движок, интерфейс резолвера
data/      реализации: Room, OkHttp, резолверы площадок, MediaStore, ffmpeg
core/      AppContainer (DI), Log
```

Правило зависимостей:

- `ui` знает про `domain` и `core`, но не про `data` — ViewModel'ы получают
  зависимости из контейнера и работают с интерфейсами.
- `domain` не знает ни про Android, ни про OkHttp, ни про Room. Здесь лежат
  `DownloadEngine`, `VideoResolver`, `LinkParser` и модели.
- `data` реализует интерфейсы `domain` и ничего не знает про `ui`.
- `worker` — верхний слой над `domain`-движком: он оркестрирует загрузку, но
  сам не знает, как устроены резолверы.

---

## 3. Внедрение зависимостей

Ручной контейнер: `core/di/AppContainer.kt`. Без Hilt/Dagger — проект
небольшой, а KSP-граф на одну базу данных и один HTTP-клиент — лишняя
сложность.

Что важно знать:

- все зависимости — `by lazy`, создаются в первый момент использования;
- `AppContainer` — **единственное место**, где выбирается реализация движка
  (`RealDownloadEngine` против `FakeEngine` для тестов);
- один `OkHttpClient` на всё приложение: у него общее хранилище cookie, и
  TikTok отдаёт файл по CDN только с cookie того же клиента, который ходил за
  страницей. Отдельный клиент для скачивания «сломал» бы TikTok;
- `TurboFetchDatabase.get(context)` — синглтон на `@Volatile` + `synchronized`.

---

## 4. Данные: Room

Три сущности:

- `TaskEntity` — очередь загрузок (статус, прогресс, формат, приоритет).
- `SettingsEntity` — настройки приложения (качество, имя файла, лимиты).
- `FileEntity` — история скачанного.

База: `TurboFetchDatabase`, версия **3**, `exportSchema = true`. Схемы
экспортируются в `app/schemas` — по ним проверяется, что миграции корректны.

Миграции — в `data/db/Migrations.kt`, все подключаются в билдере
(`.addMigrations(*Migrations.ALL)`). Очередь загрузок и настройки переживают
обновление приложения. Новую версию базы без миграции не кати: схема
экспортируется, и старая сборка не сможет прочитать новую базу.

Поверх DAO — репозитории: `TaskRepository`, `SettingsRepository`,
`FileRepository`.

---

## 5. Домен: движок и резолверы

`domain/engine/DownloadEngine`:

- `probe(url): Result<MediaInfo>` — разобрать ссылку, отдать список форматов;
- `download(request): Flow<DownloadProgress>` — поток событий
  `Preparing → Running(bytes, total) → Finished(file, bytes) | Failed(message, retryable)`.

`domain/resolver/VideoResolver` — интерфейс площадки. Реализации:

- **YouTube** (`data/resolver/youtube/YouTubeResolver.kt`) — NewPipeExtractor:
  отдаёт видео (до 2160p) и звук **отдельными** потоками, их потом склеивает
  `MediaMuxerMerger`. Спарк-тесты на реальную сеть включены флагом.
- **TikTok** (`TikTokResolver.kt`) — разбирает JSON прямо в HTML страницы:
  две схемы разметки, `api-data` и `__UNIVERSAL_DATA_FOR_REHYDRATION__`.
- **X/Twitter** (`XResolver.kt`) — эндпоинт `cdn.syndication.twimg.com`, токен
  в URL не валидируется, слой домена с заменённым.

`domain/link/LinkParser` — из URL определяет `Source`.

Модели: `MediaInfo`, `MediaFormat`, `QualityPreset`, `FileNameTemplate`,
`DownloadTask`, `AppSettings`, `DownloadedFile`.

---

## 6. Загрузка: WorkManager

`worker/DownloadScheduler`:

- `OneTimeWorkRequest` с уникальным именем `download-$taskId` и
  `ExistingWorkPolicy.REPLACE` — повторная постановка заменяет старую, а не
  дублирует;
- constraints: `CONNECTED` или `UNMETERED` (wifiOnly);
- backoff `EXPONENTIAL`, 30 секунд — обрыв связи даёт 30 с → 60 с → 120 с;
- отмена — `cancelUniqueWork`.

`worker/DownloadWorker` — `CoroutineWorker`:

- `MAX_PARALLEL_DOWNLOADS = 3` — больше трёх параллельных загрузок не берём;
- работает как foreground-сервис со своим уведомлением;
- готовый файл пишется в MediaStore через `MediaStoreWriter`, пользователю
  приходит уведомление.

`domain/engine/RealDownloadEngine` — сама загрузка:

- перед запросом считает, сколько байт уже есть, и добавляет
  `Range: bytes=N-`;
- `206` — сервер продолжил с места, `200` — начал заново, тогда файл
  перезаписывается;
- **важно**: при докачке файл открывается на **добавление**
  (`FileOutputStream(file, append = resuming)`). Обычный `File.outputStream()`
  всегда открывает файл на перезапись и обрезал его — докачанный файл
  получался битым;
- `retryable` — для 5xx, 408 и 429 (повтор имеет смысл), остальные 4xx — нет.

---

## 7. Медиапайплайн

**Склейка видео и звука** — `data/storage/MediaMuxerMerger.kt`: штатные
`MediaExtractor` + `MediaMuxer`, без нативного кода и без перекодирования.
Потоки копируются как есть, максимальный размер сэмпла ограничен 12 МБ.

**Mp3** — `data/media/FfmpegRunner.kt`: сборка ffmpeg под `arm64-v8a` из NDK,
исполняемый файл `libffmpegcli.so` лежит в `jniLibs`. Он запускается из
каталога `nativeLibraryDir` — поэтому в `packaging` включено
`useLegacyPackaging = true`: по умолчанию AGP не распаковывает нативные
библиотеки на диск, а грузит прямо из APK, и запустить оттуда файл нельзя.

**Запись** — `data/storage/MediaStoreWriter.kt`: файл уходит в MediaStore
(внешнее хранилище), оттуда виден другим приложениям.

---

## 8. UI

Compose + Navigation Compose, на каждый экран свой ViewModel:
`FilesScreen` (история скачанного), `QueueScreen` (очередь, с поддержкой
перестановки — `ReorderState`), `SettingsScreen`, `ShrineScreen`.

Тема — `ui/theme/Theme.kt`, общий тег логов — `TurboFetch` (все логи в один
тег, ищутся в logcat одним фильтром).

### Святилище

`ui/shrine` + `domain/thiel` — цепь Маркова второго порядка по корпусу цитат
Питера Тиля. Темы: бизнес, политика, апокалипсис. Цепочка с вероятностью 0.18
перепрыгивает в другую фразу — без этого биграммы почти не повторяются, и цитата
идёт дословно по исходнику. Генератор `Random` с фиксированным зерном, так что
результат воспроизводим.

---

## 9. Сборка и отладка

Требования: JDK 17+, Android SDK (compileSdk 37, build-tools 36), путь к SDK
в `local.properties`.

```bash
./gradlew :app:assembleDebug          # APK: app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Тесты:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest -Dturbofetch.network=true   # + сетевые спайк-тесты
```

Сейчас в проекте 32 юнит-теста, все зелёные; два спайк-теста YouTube
пропускаются по умолчанию — им нужен интернет.

Отладка: `adb logcat -s TurboFetch`.

---

## 10. Грабли (список, чтобы не наступить)

1. **Версии библиотек** — serialization и coil зафиксированы из-за stdlib;
   обновление без проверки = не компилируется.
2. **AGP 9 — Kotlin встроен**, версию Kotlin задаёт AGP, отдельный плагин
   ставить нельзя.
3. **`useLegacyPackaging = true`** — иначе не запустится ffmpeg из APK.
4. **Core library desugaring обязателен** — NewPipeExtractor использует
   `java.time` и `nio`, а minSdk = 26.
5. **`okhttp-jvm` вместо `okhttp-android` в тестах** — в okhttp-android база
   суффиксов лежит в assets, а JVM-тесты assets AAR не видят.
6. **`unitTests.isReturnDefaultValues = true`** — иначе `android.util.Log` в
   юнит-тестах бросает исключение вместо записи.
7. **Room: exportSchema + миграции** — новая версия базы без миграции ломает
   обновление у пользователя.
8. **Докачка** — файл открывать только на добавление, иначе битый файл.
9. **Сортировка очереди пересоздаёт WorkRequest** — `ExistingWorkPolicy.REPLACE`
   следит, чтобы работа не задвоилась.

---

## 11. Куда расти

- **Новая площадка**: реализация `VideoResolver` + ветка в `LinkParser`,
  `Source` и `AppContainer`. Формально — три файла.
- **Тесты на воркер**: `work-testing` с `TestDriver` — сейчас воркер не
  покрыт.
- **Плейлисты**: сейчас резолвер отдаёт одно видео за ссылку.
- **Локализация**: строки пока зашиты в коде, `strings.xml` не заведён.
- **Резервные копии настроек**: сейчас настройки живут только в Room.
