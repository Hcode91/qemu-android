#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Guest compilation runs only in Actions' >&2; exit 1; }
GUEST_DIR=$(cd "$(dirname "$0")" && pwd)
mkdir -p "${1:?target root required}/usr/bin"
"${HOST_DIR:?}/bin/arm-buildroot-linux-musleabihf-gcc" -O2 -static -Wall -Wextra -Werror \
  "$GUEST_DIR/display-test.c" -o "$1/usr/bin/qemu-display-test"
