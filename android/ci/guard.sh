#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
set -euo pipefail
if [[ "${GITHUB_ACTIONS:-}" != true || -z "${GITHUB_RUN_ID:-}" ]]; then
  echo 'Android compilation, packaging, signing and tests are permitted only in GitHub Actions.' >&2
  exit 1
fi
