#!/bin/sh
# Perform one check; the Java monitor schedules repeated executions.
# The monitor passes no arguments, so use a public IP address by default.
target=${1:-1.1.1.1}

if ! command -v ping >/dev/null 2>&1; then
    printf '%s\n' 'Ping check failed: ping is not installed.'
    exit 1
fi

# Linux/BusyBox: one numeric-address ping, with a two-second reply timeout.
# Keep output small: the monitor reads only the first stdout line.
if ping -n -c 1 -W 2 "$target" >/dev/null 2>&1; then
    printf 'Ping check passed: %s responded.\n' "$target"
    exit 0
else
    printf 'Ping check failed: %s did not respond or ping could not run.\n' "$target"
    exit 1
fi
