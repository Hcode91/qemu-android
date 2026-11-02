# Dependency findings and pin changes

These are responses to reported dependency advisories, not claims that a QEMU
isolation vulnerability was reproduced. The gate remains fail-closed and has no
suppression list. CI must validate the replacement pins.

## 2026-11-02: libffi bootstrap and remaining OSV findings

Both runs of `ac43c128104041d47eb4c9020ae676e47da839d5` passed SDK setup and
compiled zlib/PCRE2 for Android ARM64, then failed before libffi compilation:
`configure.ac:219: possibly undefined macro: LT_SYS_SYMBOL_USCORE`.
GNU Libtool defines this macro in `m4/ltdl.m4`, installed by Ubuntu's
`libltdl-dev`. The setup now installs that package and the bootstrap checks that
the macro file exists. No macro probe is disabled or replaced by a guessed value.
Reference: https://github.com/autotools-mirror/libtool/blob/v2.4.7/m4/ltdl.m4

The independent OSV scan still reported findings against zlib 1.3.2 and
PCRE2 10.49. The previous release updates were insufficient. GLib 2.86.5,
libffi 3.4.6 and dtc 1.7.2 had no findings in that scan. Maven dependencies
were not resolved because native compilation had failed.

### zlib CVE-2026-76844 and CVE-2026-85091

The CVE-2026-76844 record references upstream fix
`df84af25dc1942490e1d1c899a07619152a46148`. That commit explicitly addresses
CVE-2026-85091 by resetting the input state after a failed/non-blocking write.
Both records include stable zlib 1.3.2 in the affected range.

The new pin is that exact upstream commit (source reports 1.3.2.1-motley),
87 commits after v1.3.2. This is a development snapshot, not a new stable release.
Its CMake build options were checked against the pinned source.

- https://github.com/CVEProject/cvelistV5/blob/main/cves/2026/76xxx/CVE-2026-76844.json
- https://github.com/CVEProject/cvelistV5/blob/main/cves/2026/85xxx/CVE-2026-85091.json
- https://github.com/madler/zlib/commit/df84af25dc1942490e1d1c899a07619152a46148
- https://github.com/madler/zlib/compare/da607da739fa6047df13e66a2af6b8bec7c2a498...df84af25dc1942490e1d1c899a07619152a46148

### PCRE2 OSV-2026-343

The OSS-Fuzz advisory records `e19e76ad8e5bbdf8a22b69e0f408692329e1e516`
as its fixed boundary. That commit alone is on a branch diverging from the
10.49 release, so selecting it could lose release fixes. The new pin
`80c3e6e98a70a5666a24fbeab4c3f9677a65f8f1` is the upstream merge of the
10.49 release into main: it contains both the release commit and the recorded
OSV fixed boundary. GitHub comparison confirmed zero commits behind the fixed
boundary. Source reports 10.50-DEV; it is not labelled as a stable release.
PCRE2 JIT and the 16/32-bit libraries remain disabled in this build, but no
finding is suppressed on that basis.

- https://github.com/google/oss-fuzz-vulns/blob/main/vulns/pcre2/OSV-2026-343.yaml
- https://github.com/PCRE2Project/pcre2/commit/e19e76ad8e5bbdf8a22b69e0f408692329e1e516
- https://github.com/PCRE2Project/pcre2/commit/80c3e6e98a70a5666a24fbeab4c3f9677a65f8f1
- https://github.com/PCRE2Project/pcre2/compare/e19e76ad8e5bbdf8a22b69e0f408692329e1e516...80c3e6e98a70a5666a24fbeab4c3f9677a65f8f1

Upstream fixes and advisory boundaries can change. These notes describe the
review evidence, not a substitute for the next scan, compilation or device tests.

## Follow-up: native scan passed; GVDB source added to scan scope

Runs 41486471866 and 41486463409 passed the native OSV step for the six
queried commits, and compiled/installed zlib, PCRE2 and libffi for ARM64.
Maven coordinates were still unavailable. GLib then failed preparing GVDB
because a missing origin made its relative Git URL resolve as a local path.

The next change records GLib's existing GVDB gitlink explicitly:
`2b42fc75f09dbe1cd1057580b5782b08f2dcb400`, LGPL-2.1-or-later. Fetch verifies
that it matches the parent tree and expected remote, and security.py includes
it in OSV queries. The previous six-commit pass does not certify the expanded
seven-commit scan. Source archives and provenance retain this nested dependency.

- https://github.com/GNOME/glib/blob/3033f7862c2d3c7569cef2ad4b425cf42e95d4b4/.gitmodules
- https://github.com/GNOME/gvdb/tree/2b42fc75f09dbe1cd1057580b5782b08f2dcb400

## Follow-up: seven-commit scan passed; proxy-libintl fallback pinned

Runs 41487483407 and 41487471503 passed all three fetch regressions and the
seven-commit native OSV scan, including GVDB. GLib then required its libintl
fallback despite `nls=disabled`. Bionic does not supply GNU libintl and automatic
wrap retrieval is intentionally disabled.

The exact upstream wrap pins proxy-libintl to
`33934de09af6a6627eb44e310a8079df009abdbb`. Its upstream-provided implementation
is built with `STUB_ONLY` (untranslated strings), matching the no-translations
configuration. It is now fetched explicitly into GLib's subprojects directory,
checked against the wrap revision/URL/directory, archived with dependency
sources and included in OSV queries. Its source licence is LGPL-2.0-or-later.
This does not replace any emulator functionality.

GitHub reports the original frida/proxy-libintl repository has moved to
frida-forks/proxy-libintl. Metadata records both GLib's original wrap URL and the
canonical fetch URL. The exact commit is retained. No wrap downloads or security
suppressions are enabled. The expanded eight-commit scan is still pending;
Maven dependencies remain unresolved.

- https://github.com/GNOME/glib/blob/3033f7862c2d3c7569cef2ad4b425cf42e95d4b4/subprojects/proxy-libintl.wrap
- https://github.com/frida-forks/proxy-libintl/tree/33934de09af6a6627eb44e310a8079df009abdbb

## Follow-up: native dependencies build; QEMU Bionic memory port begins

Runs 41488567863 and 41488557630 pass four fetch regressions and the eight-commit
native scan. GLib, GVDB and proxy-libintl now compile/install alongside the other
native dependencies. Maven remains unresolved. QEMU configuration fails because
Bionic lacks shm_open and librt.

The downstream port uses qemu_memfd_create for Android's qemu_shm_alloc and skips
the Android-only librt search. The helper sets CLOEXEC, sizes the anonymous fd
and reports allocation/resize failures while closing failed descriptors. Other
hosts retain the original POSIX implementation. This is an API compatibility
change, not a security mitigation or evidence of working guest execution.
SELinux/JIT policy remains unchanged; device verification is still required.

## Follow-up: QEMU compilation reaches block driver statx API mismatch

Run 41490614868 passes QEMU configuration and compiles more than 1,000 native
build targets before block/file-posix.c fails on statx. Its old guard checked
only Linux and STATX_DIOALIGN from kernel headers, not the target libc's declared
and linkable statx API. The NDK API-28 configuration does not expose that API.

The new CONFIG_STATX_DIOALIGN compile/link probe checks the function declaration,
symbol, flags and struct alignment fields. When unavailable, QEMU retains its
existing ioctl, logical-block-size and direct-I/O probing fallbacks. This does
not fabricate disk capabilities, raise the minimum Android API or suppress a
compiler diagnostic. Runtime probing and complete backend compilation/linking
still require validation. Ninja now continues independent compilation after a
failure to gather port errors together, while its nonzero exit still fails CI.

## Follow-up: native link succeeds; unpackaged libfdt dependency rejected

Run 41492571109 completes all 1,992 Ninja targets and links qemu-system-arm for
Android ARM64. The ELF validator then rejects DT_NEEDED libfdt.so because that
library is not part of the Android system or packaged runtime. This check remains
blocking. Its earlier architecture/linker/TEXTREL/RELRO checks passed, while the
later segment-alignment check was not reached. The eight-commit OSV step passes;
Maven remains unresolved.

Dtc uses both_libraries, so default_library=static alone did not prevent a shared
library from being installed and selected. QEMU's Android-specific find_library
now requests static: true. The dependency build enables dtc's static-build option
and verifies libfdt.a exists. The runtime-library allowlist is unchanged. Other
hosts keep their existing selection. Corrected closure, alignment, APK packaging
and runtime still require the next CI/device checks.
