#!/usr/bin/env bash
#
# Starts the native binary and fails unless it serves a health check.
# CI otherwise only proves the binary links, not that it runs.
#
# usage: smoke-test-native.sh <binary> [http-port]
# Supplying a port also moves the HTTPS listener, so the script can be run on a
# machine that is already running the agent on the default ports.
#
set -uo pipefail

binary="${1:?usage: smoke-test-native.sh <binary> [http-port]}"
http_port="${2:-8080}"
timeout_seconds=120

overrides=()
if [ "$#" -ge 2 ]; then
    overrides=(--http.port="$http_port" --server.port="$((http_port + 1))")
fi

"$binary" "${overrides[@]+"${overrides[@]}"}" > smoke-test.log 2>&1 &
pid=$!

finish() {
    kill "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
}
trap finish EXIT

health_url="http://localhost:$http_port/actuator/health"
deadline=$((SECONDS + timeout_seconds))
while [ "$SECONDS" -lt "$deadline" ]; do
    if ! kill -0 "$pid" 2>/dev/null; then
        echo "The agent exited before it became healthy."
        cat smoke-test.log
        exit 1
    fi
    if curl --fail --silent "$health_url" > health.json 2>/dev/null; then
        if ! grep -q "Started SysAPIApplicationKt" smoke-test.log; then
            echo "Health endpoint answered but the application never reported startup."
            cat smoke-test.log
            exit 1
        fi
        echo "Healthy after ${SECONDS}s: $(cat health.json)"
        exit 0
    fi
    sleep 2
done

echo "The agent did not answer $health_url within ${timeout_seconds}s."
cat smoke-test.log
exit 1
