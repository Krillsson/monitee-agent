#!/usr/bin/env bash
#
# usage: launcher-test.sh [shell]
# Runs src/launcher/sysapi-launcher under the given shell (default sh) against a fake JVM and
# a fake native binary, whose behaviour each case picks through FAKE_* variables.
#
# Each check is a condition string evaluated later, so the single quotes are deliberate.
# shellcheck disable=SC2016,SC2034,SC2294
set -uo pipefail

shell="${1:-sh}"
repo="$(cd "$(dirname "$0")/../../.." && pwd)"
work="$(cd "$(mktemp -d)" && pwd -P)"
trap 'rm -rf "$work"' EXIT
failures=0

setup() {
    app="$work/app"
    rm -rf "${work:?}/app" "${work:?}/bin" "${work:?}/calls"
    mkdir -p "$app/lib" "$app/native/linux-amd64" "$work/bin" "$work/calls"
    touch "$app/lib/sysapi-all.jar"
    sed -e "s|@VERSION@|1.2.3|" -e "s|@JVM_ARGS@|-Xmx64m|" -e "s|@NATIVE_ARGS@|-Xmx32m|" \
        "$repo/src/launcher/sysapi-launcher" > "$app/sysapi-launcher"
    sed -i.bak "s|^default_runtime='jvm'|default_runtime='${DEFAULT_RUNTIME:-jvm}'|" "$app/sysapi-launcher"

    cat > "$work/bin/uname" <<'FAKE'
#!/bin/sh
case "$1" in -s) echo "${FAKE_OS:-Linux}" ;; -m) echo "${FAKE_ARCH:-x86_64}" ;; esac
FAKE
    cat > "$work/bin/java" <<FAKE
#!/bin/sh
printf '%s\n' "\$*" > "$work/calls/jvm"
printf '%s\n' "\${SYSAPI_NATIVE_DISABLED_FILE:-}" > "$work/calls/jvm-disabled-file"
exit "\${FAKE_JVM_EXIT:-0}"
FAKE
    cat > "$app/native/linux-amd64/sysapi" <<FAKE
#!/bin/sh
if [ "\$1" = --native-probe ]; then
    case "\${FAKE_PROBE:-ok}" in
        ok) echo "sysapi 1.2.3 native" ;;
        old) echo "sysapi 1.0.0 native" ;;
        cpu) echo "The current machine does not support all of the following CPU features that are required by the image: [AVX2]."; exit 1 ;;
    esac
    exit 0
fi
printf '%s\n' "\$*" > "$work/calls/native"
case "\${FAKE_NATIVE:-exit}" in
    exit) exit "\${FAKE_NATIVE_EXIT:-0}" ;;
    ready-then-exit) echo native > "\$SYSAPI_READY_FILE"; exit "\${FAKE_NATIVE_EXIT:-0}" ;;
    crash) kill -SEGV \$\$ ;;
    serve) echo native > "\$SYSAPI_READY_FILE"; trap 'echo stopped > "$work/calls/native-stopped"; exit 143' TERM; while :; do sleep 0.1; done ;;
esac
FAKE
    chmod +x "$work/bin/"* "$app/sysapi-launcher" "$app/native/linux-amd64/sysapi"
}

launch() {
    (cd "$work" && unset JAVA_HOME && export PATH="$work/bin:$PATH" ${FAKE_JAVA_HOME:+JAVA_HOME="$FAKE_JAVA_HOME"} && "$shell" "$app/sysapi-launcher" "$@" > "$work/calls/output" 2>&1)
    status=$?
}

check() {
    local description="$1"
    shift
    if ! eval "$@"; then
        echo "FAIL [$current]: $description"
        sed 's/^/    | /' "$work/calls/output" 2>/dev/null
        failures=$((failures + 1))
    fi
}

ran_jvm() { [ -f "$work/calls/jvm" ]; }
ran_native() { [ -f "$work/calls/native" ]; }
marker_reason() { [ "$(sed -n 2p "$work/data/native-disabled" 2>/dev/null)" = "$1" ]; }
no_marker() { [ ! -f "$work/data/native-disabled" ]; }

run_case() {
    current="$1"
    shift
    rm -rf "$work/data"
    unset FAKE_JAVA_HOME SYSAPI_RUNTIME FAKE_PROBE FAKE_NATIVE FAKE_NATIVE_EXIT FAKE_JVM_EXIT FAKE_ARCH FAKE_OS DEFAULT_RUNTIME
    "$@"
}

case_jvm_is_the_default() {
    setup
    launch --server.port=1
    check "runs the JVM" ran_jvm
    check "does not touch the native binary" '! ran_native'
    check "passes the arguments on" 'grep -q -- "-Xmx64m -jar $app/lib/sysapi-all.jar --server.port=1" "$work/calls/jvm"'
}

case_rejects_an_unknown_runtime() {
    setup
    export SYSAPI_RUNTIME=fast
    launch
    check "exits 64" '[ "$status" = 64 ]'
    check "starts nothing" '! ran_jvm && ! ran_native'
}

case_auto_runs_native_and_passes_arguments() {
    setup
    export SYSAPI_RUNTIME=auto FAKE_NATIVE=ready-then-exit
    launch --server.port=1
    check "runs native" ran_native
    check "passes native args and arguments" 'grep -q -- "-Xmx32m --server.port=1" "$work/calls/native"'
    check "does not run the JVM" '! ran_jvm'
}

case_auto_without_a_binary_for_this_arch_runs_jvm() {
    setup
    export SYSAPI_RUNTIME=auto FAKE_ARCH=armv7l
    launch
    check "runs the JVM" ran_jvm
    check "says why" 'grep -q "reason=no native binary" "$work/calls/output"'
    check "leaves no marker" no_marker
}

case_auto_on_macos_runs_jvm() {
    setup
    export SYSAPI_RUNTIME=auto FAKE_OS=Darwin
    launch
    check "runs the JVM" ran_jvm
}

case_probe_failure_disables_native() {
    setup
    export SYSAPI_RUNTIME=auto FAKE_PROBE=cpu
    launch
    check "runs the JVM" ran_jvm
    check "never starts native" '! ran_native'
    check "records the platform reason" 'marker_reason platform'
    check "keeps the probe output" 'grep -q AVX2 "$work/data/native-disabled"'
}

case_probe_version_mismatch_disables_native() {
    setup
    export SYSAPI_RUNTIME=auto FAKE_PROBE=old
    launch
    check "runs the JVM" ran_jvm
    check "records the mismatch" 'marker_reason version-mismatch'
}

case_native_limitation_falls_back() {
    setup
    export SYSAPI_RUNTIME=auto FAKE_NATIVE=exit FAKE_NATIVE_EXIT=86
    launch
    check "tried native" ran_native
    check "runs the JVM" ran_jvm
    check "records the native limitation" 'marker_reason native-limitation'
}

case_native_crash_before_ready_falls_back() {
    setup
    export SYSAPI_RUNTIME=auto FAKE_NATIVE=crash
    launch
    check "runs the JVM" ran_jvm
    check "records the crash" 'marker_reason native-crash'
}

case_unclassified_failure_verifies_on_the_jvm() {
    setup
    export SYSAPI_RUNTIME=auto FAKE_NATIVE=exit FAKE_NATIVE_EXIT=1 FAKE_JVM_EXIT=1
    launch
    check "runs the JVM" ran_jvm
    check "leaves the marker to the JVM" no_marker
    check "hands the JVM the marker path" 'grep -q "data/native-disabled" "$work/calls/jvm-disabled-file"'
    check "exits with the JVM's status" '[ "$status" = 1 ]'
}

case_failure_after_ready_is_propagated() {
    setup
    export SYSAPI_RUNTIME=auto FAKE_NATIVE=ready-then-exit FAKE_NATIVE_EXIT=3
    launch
    check "exits 3" '[ "$status" = 3 ]'
    check "does not fall back" '! ran_jvm'
    check "leaves no marker" no_marker
}

case_clean_exit_is_propagated() {
    setup
    export SYSAPI_RUNTIME=auto FAKE_NATIVE=exit FAKE_NATIVE_EXIT=0
    launch
    check "exits 0" '[ "$status" = 0 ]'
    check "does not fall back" '! ran_jvm'
}

case_marker_for_this_version_skips_native() {
    setup
    mkdir -p "$work/data"
    printf '1.2.3\nnative-crash\nearlier\n' > "$work/data/native-disabled"
    export SYSAPI_RUNTIME=auto
    launch
    check "runs the JVM" ran_jvm
    check "does not start native" '! ran_native'
    check "says why" 'grep -q "reason=native-crash" "$work/calls/output"'
}

case_marker_for_an_older_version_is_ignored() {
    setup
    mkdir -p "$work/data"
    printf '1.2.2\nnative-crash\nearlier\n' > "$work/data/native-disabled"
    export SYSAPI_RUNTIME=auto FAKE_NATIVE=ready-then-exit
    launch
    check "runs native again" ran_native
    check "does not run the JVM" '! ran_jvm'
}

case_forced_native_never_falls_back() {
    setup
    export SYSAPI_RUNTIME=native FAKE_NATIVE=exit FAKE_NATIVE_EXIT=86
    launch
    check "exits 86" '[ "$status" = 86 ]'
    check "does not run the JVM" '! ran_jvm'
    check "leaves no marker" no_marker
}

case_forced_native_without_a_binary_fails() {
    setup
    export SYSAPI_RUNTIME=native FAKE_ARCH=armv7l
    launch
    check "exits 1" '[ "$status" = 1 ]'
    check "does not run the JVM" '! ran_jvm'
}

case_terminate_is_forwarded_to_native() {
    setup
    export SYSAPI_RUNTIME=auto FAKE_NATIVE=serve
    (cd "$work" && unset JAVA_HOME && PATH="$work/bin:$PATH" exec "$shell" "$app/sysapi-launcher" > "$work/calls/output" 2>&1) &
    local launcher=$!
    for _ in $(seq 50); do [ -f "$work/data/.native-ready" ] && break; sleep 0.1; done
    kill -TERM "$launcher"
    wait "$launcher"
    status=$?
    check "native was told to stop" '[ -f "$work/calls/native-stopped" ]'
    check "exits with native's status" '[ "$status" = 143 ]'
    check "does not fall back" '! ran_jvm'
}

case_java_home_picks_the_jvm() {
    setup
    mkdir -p "$work/jdk/bin"
    mv "$work/bin/java" "$work/jdk/bin/java"
    export FAKE_JAVA_HOME="$work/jdk"
    launch
    check "runs java from JAVA_HOME" ran_jvm
}

case_jib_image_runs_the_main_class() {
    setup
    rm "$app/lib/sysapi-all.jar"
    echo "com.example.Main" > "$app/jib-main-class-file"
    launch
    check "uses the jib classpath file" 'grep -q -- "-cp @$app/jib-classpath-file com.example.Main" "$work/calls/jvm"'
}

for test_case in $(declare -F | awk '{print $3}' | grep '^case_'); do
    run_case "$test_case" "$test_case"
done

if [ "$failures" -gt 0 ]; then
    echo "$failures launcher check(s) failed under $shell"
    exit 1
fi
echo "All launcher checks passed under $shell"
