/* SPDX-License-Identifier: GPL-2.0-or-later */
#include <libfdt.h>

/* Link against an actual libfdt symbol; this probe is never run on the x86 host. */
int main(void)
{
    unsigned char tree[1024];
    return fdt_create_empty_tree(tree, sizeof(tree));
}
