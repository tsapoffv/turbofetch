#!/usr/bin/env bash
#
# Сборка ffmpeg для Android (arm64-v8a) с кодировщиком mp3.
#
# Результат кладётся в app/src/main/jniLibs/arm64-v8a/ и в репозиторий не
# попадает: бинарников там 22 МБ, а воспроизвести их этим скриптом проще,
# чем тащить в git.
#
# Требуется:
#   - Android SDK с NDK: ~/Android/Sdk/ndk/27.2.12479018
#   - git, make, cmake, python3
#
# Занимает около часа.

set -euo pipefail

SDK_HOME="${ANDROID_SDK_HOME:-$HOME/Android/Sdk}"
NDK_VERSION="27.2.12479018"
WORK_DIR="${TMPDIR:-/tmp}/ffmpeg-android-maker"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="$PROJECT_DIR/app/src/main/jniLibs/arm64-v8a"

if [ ! -d "$SDK_HOME/ndk/$NDK_VERSION" ]; then
  echo "Не найден NDK $NDK_VERSION в $SDK_HOME/ndk" >&2
  echo "Поставить: sdkmanager \"ndk;$NDK_VERSION\"" >&2
  exit 1
fi

export ANDROID_SDK_HOME="$SDK_HOME"
export ANDROID_NDK_HOME="$SDK_HOME/ndk/$NDK_VERSION"

if [ ! -d "$WORK_DIR" ]; then
  git clone --depth 1 https://github.com/javernaut/ffmpeg-android-maker.git "$WORK_DIR"
fi

cd "$WORK_DIR"
# Только arm64: у пользователя один телефон, 32-битные не нужны.
./ffmpeg-android-maker.sh --target-abis=arm64-v8a --enable-libmp3lame

echo
echo "Копируем в проект..."
mkdir -p "$OUTPUT_DIR"
cp "$WORK_DIR"/output/lib/arm64-v8a/*.so "$OUTPUT_DIR/"

# CLI-бинарник переименовываем в lib*.so: только с таким именем PackageManager
# распакует его в nativeLibraryDir, откуда его можно запустить (exec из
# домашней папки приложения запрещён политикой W^X).
cp "$WORK_DIR"/build/ffmpeg/arm64-v8a/bin/ffmpeg "$OUTPUT_DIR/libffmpegcli.so"
chmod 755 "$OUTPUT_DIR"/*.so

echo "Готово:"
ls -la "$OUTPUT_DIR"
echo
echo "Проверка выравнивания страниц (должно быть 0x4000 = 16 КБ):"
readelf -lW "$OUTPUT_DIR/libavcodec.so" | grep LOAD | head -3
