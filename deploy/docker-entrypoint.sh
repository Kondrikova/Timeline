#!/bin/sh
set -e
# Агент подключается только при OTEL_JAVAAGENT_ENABLED=true (observability-оверлей).
# JAVA_TOOL_OPTIONS сбрасываем явно: иначе после запуска со стеком наблюдаемости
# переменная могла остаться в контейнере и продолжала бы грузить агент
# (UnknownHostException: otel-collector), даже при OTEL_JAVAAGENT_ENABLED=false.
unset JAVA_TOOL_OPTIONS
OPTS="-XX:MaxRAMPercentage=75"
if [ "${OTEL_JAVAAGENT_ENABLED}" = "true" ]; then
  OPTS="$OPTS -javaagent:/otel/opentelemetry-javaagent.jar"
fi
exec java $OPTS -jar /app/app.jar
