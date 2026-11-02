#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
set -euo pipefail
source android/ci/guard.sh
# Approval refers to an exact commit, never a moving branch name.
[[ "${APPROVED_COMMIT:-}" =~ ^[0-9a-f]{40}$ ]] || { echo 'Set approved_commit input or APPROVED_RELEASE_COMMIT repository variable to the full approved SHA.' >&2; exit 1; }
[[ "$(git rev-parse HEAD)" == "$APPROVED_COMMIT" ]] || { echo 'Release commit is not approved' >&2; exit 1; }
git merge-base --is-ancestor HEAD origin/master || { echo 'Release commit must be reviewed and merged into master first' >&2; exit 1; }
if [[ "$GITHUB_REF_TYPE" == tag ]]; then
  [[ "$GITHUB_REF_NAME" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 1
  [[ "$GITHUB_REF_NAME" == v0.1.0 ]] || { echo 'Update app version and release artifact version together' >&2; exit 1; }
fi
mkdir -p android/reports/release
printf '%s\n' "$APPROVED_COMMIT" > android/reports/release/approved-commit.txt
