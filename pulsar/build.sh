#!/usr/bin/env bash
# Сборка API Pulsar Tax без внешних зависимостей. Нужна только JDK 21+.
#   ./build.sh        — собрать build/pulsar-tax.jar
#   ./build.sh test   — собрать и запустить тесты
set -euo pipefail
cd "$(dirname "$0")"

rm -rf build
mkdir -p build/classes build/test

javac --release 21 -encoding UTF-8 -d build/classes $(find src/main/java -name '*.java')

if [[ "${1:-}" == "test" ]]; then
  javac --release 21 -encoding UTF-8 -cp build/classes -d build/test $(find src/test/java -name '*.java')
  java -Dstdout.encoding=UTF-8 -cp build/classes:build/test am.pulsar.tax.AllTests
fi

jar --create --file build/pulsar-tax.jar --main-class am.pulsar.tax.Main -C build/classes .
echo "Готово: build/pulsar-tax.jar  (запуск: java -jar build/pulsar-tax.jar)"
