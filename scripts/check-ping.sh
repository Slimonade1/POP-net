#!/usr/bin/env bash
# Linux/iputils check contract: 0 healthy, 1 sick, 2 down, 3 check error.
# Preserve ping's output for storage; map its exit codes to our own contract.
set -uo pipefail
export LC_ALL=C

if [[ $# != 1 || -z $1 || $1 == -* || $1 == *[[:space:]]* ]]; then
    echo 'Usage: check-ping.sh HOST_OR_IP' >&2
    exit 3
fi
if ! command -v ping >/dev/null 2>&1; then
    echo 'Linux ping (iputils) is required on the application host.' >&2
    exit 3
fi

# With a deadline, iputils returns 1 when fewer than all requested packets arrive.
output=$(ping -n -c 3 -W 2 -w 6 -- "$1" 2>&1)
result=$?
printf '%s\n' "$output"

case "$result" in
    0) exit 0 ;;
    1)
        if [[ $output =~ ([0-9]+)[[:space:]]+received ]]; then
            if (( ${BASH_REMATCH[1]} > 0 )); then
                exit 1
            fi
            exit 2
        fi
        echo 'Unable to read ping packet statistics.' >&2
        exit 3
        ;;
    *) exit 3 ;;
esac
