#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]] || exit 1
cd /src
export OUT_DIR=/out
export TARGET_PRODUCT=full TARGET_BUILD_VARIANT=eng TARGET_BUILD_TYPE=release
export BUILD_TINY_ANDROID=true
export JAVA_HOME=/usr/lib/jvm/java-6-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version
make --version | head -1
# The native-only build accepts OpenJDK 6, retaining the actual 1.6 version
# checks. No Java compilation or full-framework compatibility is asserted.
make -j2 init qemu_android_native_probe
[[ -s /out/target/product/generic/root/init ]]
[[ -s /out/target/product/generic/root/sbin/qemu_android_native_probe ]]
file /out/target/product/generic/root/init /out/target/product/generic/root/sbin/qemu_android_native_probe
