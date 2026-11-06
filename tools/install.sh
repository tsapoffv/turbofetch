#!/usr/bin/env bash
#
# Сборка и установка на подключённый телефон.
#
#   ./tools/install.sh          — собрать и поставить
#   ./tools/install.sh --build  — только собрать
#
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

cd "$PROJECT_DIR"

if [ ! -x "$ADB" ]; then
  echo "Не найден adb: $ADB" >&2
  echo "Установи SDK: sdkmanager platform-tools" >&2
  exit 1
fi

echo "=== Сборка ==="
ANDROID_HOME="$SDK" ./gradlew assembleDebug

if [ "${1:-}" = "--build" ]; then
  echo "APK: $APK ($(du -h "$APK" | cut -f1))"
  exit 0
fi

if [ ! -f "$APK" ]; then
  echo "APK не собран: $APK" >&2
  exit 1
fi

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo
  echo "Телефон не подключён. Проверь:" >&2
  echo "  - кабель именно data-кабель (зарядка и данные идут разными линиями)" >&2
  echo "  - на Samsung: Настройки → Обслуживание ПО → USB-подключение → «Передача файлов»" >&2
  echo "  - в «Параметрах разработчика» включено «Отладка по USB»" >&2
  echo "  - выключено «Проверка приложений через USB»" >&2
  exit 1
fi

echo
echo "=== Установка ==="
"$ADB" install -r "$APK"

echo
echo "=== Запуск ==="
"$ADB" shell am start -n com.turbofetch.debug/com.turbofetch.MainActivity

echo
echo "Готово. Логи: $ADB logcat -s TurboFetch"
