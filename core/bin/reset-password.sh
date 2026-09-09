#!/bin/bash
#
# Reset a user's password without knowing the current one, bypassing the REST API.
# Thin wrapper over the `reset-password` Maven profile (dev.olegz.vf.core.tool.ResetPasswordTool).
#
# Usage:
#   core/bin/reset-password.sh --username jane
#   core/bin/reset-password.sh --userId 57 --password s3cret
#
# Identify the user with --userId or --username (exactly one).
# New password: --password (else $VF_ADMIN_PASSWORD, else console prompt).
#
# Requires `mvn` on PATH, the upstream modules already in the local repo (run a full
# `mvn install` once if not), and a reachable DB per $VF_HOME/config/properties (jdbc.*).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Scope the reactor to `core` only (no -am): `exec:java` is a direct CLI goal, so it runs on every
# module in the reactor, and only `core` defines the profile's mainClass. `compile` in the same
# invocation still picks up core source changes; upstream deps resolve from the local repo.
#
# Pass args as a comma-separated list (exec.arguments) rather than a whitespace-split string, so a
# value containing spaces stays one argument. A value must not itself contain a comma. Preserve the
# shell's own tokenisation by joining "$@".
MVN_ARGS=(-q -f "$ROOT/pom.xml" -pl core -Preset-password compile exec:java)
if [ "$#" -gt 0 ]; then
    joined="$1"; shift
    for arg in "$@"; do joined="$joined,$arg"; done
    MVN_ARGS+=(-Dexec.arguments="$joined")
fi

exec mvn "${MVN_ARGS[@]}"
