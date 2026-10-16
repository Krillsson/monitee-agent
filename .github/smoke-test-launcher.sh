#!/usr/bin/env bash
#
# Waits for an agent that something else started through the launcher, and fails unless it
# answers its health check and the launcher log says it picked the expected runtime.
#
# usage: smoke-test-launcher.sh <log-file> <expected-runtime> [http-port]
#
set -uo pipefail

log_file="${1:?usage: smoke-test-launcher.sh <log-file> <expected-runtime> [http-port]}"
expected_runtime="${2:?usage: smoke-test-launcher.sh <log-file> <expected-runtime> [http-port]}"
http_port="${3:-8080}"
timeout_seconds=180

health_url="http://localhost:$http_port/actuator/health"
deadline=$((SECONDS + timeout_seconds))
while [ "$SECONDS" -lt "$deadline" ]; do
    if curl --fail --silent "$health_url" > health.json 2>/dev/null && grep -q "Started SysAPIApplicationKt" "$log_file"; then
        if ! grep -q "\[launcher\] runtime=$expected_runtime\b" "$log_file"; then
            echo "The agent is up, but the launcher did not pick runtime=$expected_runtime."
            grep "\[launcher\]" "$log_file"
            exit 1
        fi
        echo "Healthy on $expected_runtime after ${SECONDS}s: $(cat health.json)"
        grep "\[launcher\]" "$log_file"
        exit 0
    fi
    sleep 2
done

echo "The agent did not answer $health_url within ${timeout_seconds}s."
cat "$log_file"
exit 1
