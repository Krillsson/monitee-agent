#!/bin/sh
export MALLOC_ARENA_MAX="${MALLOC_ARENA_MAX:-2}"
export MALLOC_TRIM_THRESHOLD_="${MALLOC_TRIM_THRESHOLD_:-131072}"
cd "$(dirname "$0")" || exit 1
exec ./sysapi-launcher --spring.config.location=classpath:/config/application.properties,optional:file:config/application.properties "$@"
