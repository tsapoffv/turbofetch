# TurboFetch — сборка и запуск

## Что нужно на машине

Всё ставится **без sudo**, в пользовательскую папку:

| Что | Путь | Статус |
|---|---|---|
| Android SDK | `~/Android/Sdk` | platform-tools, platforms;android-37.0, build-tools;37.0.0 |
| Gradle | wrapper в проекте (9.9.0) | `./gradlew` |
| JDK | системный 25 | AGP 9.5.1 использует встроенный Kotlin 2.2.10 |

Проверить, что всё на месте:

```bash
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
adb version
```

## Сборка

```bash
cd ~/Documents/turbo-fetch
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 23 юнит-теста
```

## Установка на телефон

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

При первом подключении по USB телефон спросит «Разрешить отладку по USB?» — нужно
подтвердить. Если телефон не виден в `adb devices`, проверь, что кабель именно
data-кабель (многие только на зарядку).

Беспроводной вариант (кабель не нужен): Настройки → О телефоне → 7 раз тапнуть
«Номер сборки» → Параметры разработчика → Беспроводная отладка → Сопрячь с помощью кода.

## Как пользоваться

1. В TikTok / YouTube / X нажимаешь «Поделиться» → TurboFetch.
2. Задача появляется в очереди, через секунду подтягиваются название и доступные форматы.
3. Тап по карточке — шторка: качество, mp3, имя файла.
4. «Скачать» → прогресс → файл в `Movies/TurboFetch` (виден в галерее).

## Куда кладутся файлы

- видео — `Movies/TurboFetch/`
- аудио — `Music/TurboFetch/`

Имена строятся по шаблону из настроек: `{title}`, `{author}`, `{quality}`, `{ext}`,
`{date}`, `{id}`.

## Что пока не работает

- **YouTube** — резолвер не подключён (план: NewPipeExtractor).
- **X/Twitter** — резолвер написан, но не проверен на живом твите с видео.
- **mp3** — требует ffmpeg на устройстве; пока скачивается исходный mp4.
- **Пресеты качества** влияют только на выбор из списка форматов, но TikTok отдаёт
  только один вариант («Оригинал»), так что лестницы качества там нет.

## Логи

В приложении есть свой лог с тегом `TurboFetch`:

```bash
adb logcat -s TurboFetch
```
