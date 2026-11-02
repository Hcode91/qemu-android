#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
source "$ROOT/android/ci/guard.sh"
NDK_VERSION=28.2.13676358
API=28
NDK="${ANDROID_SDK_ROOT:?}/ndk/$NDK_VERSION"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$TOOLCHAIN/bin:$PATH"
export CC="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang"
export CXX="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang++"
export AR="$TOOLCHAIN/bin/llvm-ar"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
export NM="$TOOLCHAIN/bin/llvm-nm"
export OBJCOPY="$TOOLCHAIN/bin/llvm-objcopy"
export STRIP="$TOOLCHAIN/bin/llvm-strip"
export CFLAGS='-O2 -fPIC -fstack-protector-strong -D_FORTIFY_SOURCE=2'
export CXXFLAGS="$CFLAGS"
export LDFLAGS='-Wl,-z,relro,-z,now,-z,max-page-size=16384'
WORK="$ROOT/android/.native"
PREFIX="$WORK/prefix"
mkdir -p "$WORK/src" "$PREFIX" "$ROOT/android/reports/native" "$ROOT/android/app/src/main/assets/licenses"
exec > >(tee "$ROOT/android/reports/native/build.log") 2>&1
trap 'find "$WORK" \( -name meson-log.txt -o -name config.log \) -exec cp --parents {} "$ROOT/android/reports/native/" \; || true' EXIT
export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig:$PREFIX/share/pkgconfig"
export PKG_CONFIG_PATH="$PKG_CONFIG_LIBDIR"
# Static dependency lookup is mandatory: never link Ubuntu's host libraries.
cat > "$WORK/pkg-config-static" <<'EOF'
#!/bin/sh
exec pkg-config --static "$@"
EOF
chmod +x "$WORK/pkg-config-static"
export PKG_CONFIG="$WORK/pkg-config-static"
cat > "$WORK/android.ini" <<EOF
[binaries]
c = '$CC'
cpp = '$CXX'
ar = '$AR'
strip = '$STRIP'
pkg-config = '$PKG_CONFIG'
[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'
[properties]
needs_exe_wrapper = true
[built-in options]
c_args = ['-O2', '-fPIC', '-fstack-protector-strong', '-D_FORTIFY_SOURCE=2']
c_link_args = ['-Wl,-z,relro,-z,now,-z,max-page-size=16384']
default_library = 'static'
b_staticpic = true
EOF
python3 "$ROOT/android/ci/fetch-deps.py" "$WORK/src"
cmake_build() {
  local dep="$1"; shift
  cmake -S "$WORK/src/$dep" -B "$WORK/$dep-build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM="android-$API" \
    -DCMAKE_INSTALL_PREFIX="$PREFIX" -DCMAKE_INSTALL_LIBDIR=lib \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DCMAKE_BUILD_TYPE=Release "$@"
  cmake --build "$WORK/$dep-build" --parallel 2
  cmake --install "$WORK/$dep-build"
}
meson_build() {
  local dep="$1"; shift
  meson setup "$WORK/$dep-build" "$WORK/src/$dep" --cross-file "$WORK/android.ini" \
    --prefix "$PREFIX" --libdir lib --wrap-mode=nodownload --buildtype=release "$@"
  meson compile -C "$WORK/$dep-build" -j 2
  meson install -C "$WORK/$dep-build"
}
# Rebuild dependencies from pinned sources. No cached target binaries in release jobs.
cmake_build zlib -DZLIB_BUILD_TESTING=OFF -DZLIB_BUILD_SHARED=OFF -DZLIB_BUILD_STATIC=ON
cmake_build pcre2 -DBUILD_SHARED_LIBS=OFF -DBUILD_STATIC_LIBS=ON -DPCRE2_STATIC_PIC=ON -DPCRE2_BUILD_TESTS=OFF \
  -DPCRE2_BUILD_PCRE2GREP=OFF -DPCRE2_SUPPORT_JIT=OFF -DPCRE2_BUILD_PCRE2_16=OFF -DPCRE2_BUILD_PCRE2_32=OFF
# LT_SYS_SYMBOL_USCORE is supplied by libltdl-dev, not libtool alone.
ACLOCAL_DIR=$(aclocal --print-ac-dir)
[[ -r "$ACLOCAL_DIR/ltdl.m4" ]] || { echo "Missing ltdl.m4: install libltdl-dev" >&2; exit 1; }
(cd "$WORK/src/libffi" && ./autogen.sh)
mkdir -p "$WORK/libffi-build"
(cd "$WORK/libffi-build" && "$WORK/src/libffi/configure" --host=aarch64-linux-android \
  --prefix="$PREFIX" --libdir="$PREFIX/lib" --disable-shared --enable-static --disable-docs && make -j2 && make install)
meson_build glib -Dtests=false -Dinstalled_tests=false -Dnls=disabled -Dlibmount=disabled \
  -Dselinux=disabled -Dxattr=false -Dlibelf=disabled -Dintrospection=disabled \
  -Ddocumentation=false -Dman-pages=disabled -Dsysprof=disabled
meson_build pixman -Dtests=disabled -Dgtk=disabled -Dlibpng=disabled -Dgnuplot=false -Dopenmp=disabled
meson_build dtc -Dtools=false -Dtests=false -Dpython=disabled -Dyaml=disabled -Dstatic-build=true
# dtc builds both library variants; QEMU's Android lookup explicitly selects .a.
[[ -s "$PREFIX/lib/libfdt.a" ]] || { echo 'Static libfdt archive missing' >&2; exit 1; }
# Exercise the same static pkg-config resolution as QEMU before its full build.
meson setup "$WORK/fdt-link-check" "$ROOT/android/ci/fdt-link-check" \
  --cross-file "$WORK/android.ini" --wrap-mode=nodownload --buildtype=release
meson compile -C "$WORK/fdt-link-check" -j 2
"$TOOLCHAIN/bin/llvm-readelf" -W -h -l -d "$WORK/fdt-link-check/fdt-link-check" > "$ROOT/android/reports/native/fdt-link-check.txt"
python3 "$ROOT/android/ci/check-elf.py" "$WORK/fdt-link-check/fdt-link-check" "$TOOLCHAIN/bin/llvm-readelf"
mkdir -p "$WORK/qemu-build"
cd "$WORK/qemu-build"
"$ROOT/configure" --cross-prefix="$TOOLCHAIN/bin/aarch64-linux-android-" \
  --cc="$CC" --cxx="$CXX" --host-cc=gcc \
  --cpu=aarch64 --target-list=arm-softmmu --without-default-features \
  --enable-tcg --disable-kvm --disable-user --disable-tools --disable-guest-agent \
  --disable-docs --disable-plugins --disable-rust --disable-werror --enable-pixman --enable-vnc \
  --disable-linux-aio --disable-linux-io-uring --disable-vhost-net --disable-vhost-kernel \
  --disable-virtfs --disable-slirp --enable-fdt=system --enable-pie \
  --extra-cflags="$CFLAGS -I$PREFIX/include" --extra-ldflags="$LDFLAGS -L$PREFIX/lib"
# Keep compiling independent targets after an error to collect all port failures.
# Ninja still exits nonzero if any required command fails.
ninja -k 0 -j2 qemu-system-arm
DEST="$ROOT/android/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$DEST"
# A PIE executable named lib*.so is extracted by Android's package installer.
# It is launched using exec, never System.loadLibrary/dlopen.
cp qemu-system-arm "$DEST/libqemu-system-arm.so"
"$TOOLCHAIN/bin/llvm-readelf" -h -l -d "$DEST/libqemu-system-arm.so" > "$ROOT/android/reports/native/elf.txt"
python3 "$ROOT/android/ci/check-elf.py" "$DEST/libqemu-system-arm.so" "$TOOLCHAIN/bin/llvm-readelf"
# Preserve exact dependency sources for licence compliance (including static LGPL libraries).
cd "$WORK"
tar -czf "$ROOT/android/reports/native/dependency-sources.tar.gz" src
cp "$ROOT/COPYING" "$ROOT/COPYING.LIB" "$ROOT/android/app/src/main/assets/licenses/"
cp "$ROOT/android/ci/native-deps.json" "$ROOT/android/app/src/main/assets/licenses/"
git -C "$ROOT" archive --format=tar.gz --output="$ROOT/android/reports/native/qemu-source.tar.gz" HEAD
tar -C "$ROOT" -czf "$ROOT/android/reports/native/qemu-subprojects.tar.gz" subprojects
"$CC" --version > "$ROOT/android/reports/native/compiler-version.txt"
python3 "$ROOT/android/ci/provenance.py"
