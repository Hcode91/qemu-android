# SPDX-License-Identifier: GPL-2.0-or-later
project('android-fdt-link-check', 'c')
fdt = dependency('libfdt', method: 'pkg-config', static: true,
                 required: true, allow_fallback: false)
executable('fdt-link-check', 'main.c', dependencies: fdt)
