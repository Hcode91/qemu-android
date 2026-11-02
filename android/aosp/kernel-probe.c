/* SPDX-License-Identifier: GPL-2.0-or-later */
/* ARM EABI Linux syscalls; a kernel ABI probe, not Android userspace. */
static long syscall5(long number, long a, long b, long c, long d, long e)
{
    register long r0 __asm__("r0") = a;
    register long r1 __asm__("r1") = b;
    register long r2 __asm__("r2") = c;
    register long r3 __asm__("r3") = d;
    register long r4 __asm__("r4") = e;
    register long r7 __asm__("r7") = number;
    __asm__ volatile("svc 0" : "+r"(r0) : "r"(r1), "r"(r2), "r"(r3), "r"(r4), "r"(r7) : "memory", "cc");
    return r0;
}
static void say(const char *text)
{
    long length = 0;
    while (text[length]) length++;
    syscall5(4, 1, (long)text, length, 0, 0);
}
static void fail(const char *text)
{
    say("ANDROID-ABI-FAIL "); say(text); say("\n");
    syscall5(1, 1, 0, 0, 0, 0);
    for (;;) {}
}
static long open_device(const char *path)
{
    long fd = syscall5(5, (long)path, 2, 0, 0, 0);
    if (fd < 0) fail(path);
    return fd;
}
static void close_device(long fd) { syscall5(6, fd, 0, 0, 0, 0); }
void _start(void)
{
    long fd, result;
    int protocol = 0;
    static const char log_entry[] = "\004QEMU-Android\0kernel-abi-probe\0";
    say("ANDROID-ABI-PROBE (not Android userspace)\n");
    if (syscall5(21, (long)"devtmpfs", (long)"/dev", (long)"devtmpfs", 0, 0) < 0)
        fail("mount devtmpfs");
    fd = open_device("/dev/binder");
    /* ARM32 _IOWR('b', 9, struct binder_version), protocol required by JB. */
    result = syscall5(54, fd, 0xc0046209UL, (long)&protocol, 0, 0);
    if (result < 0 || protocol != 7) fail("Binder protocol is not 7");
    close_device(fd); say("BINDER-PROTOCOL-7-OK\n");
    fd = open_device("/dev/ashmem");
    /* ARM32 ASHMEM_SET_SIZE takes a size_t value, not a pointer. */
    if (syscall5(54, fd, 0x40047703UL, 4096, 0, 0) < 0 ||
        syscall5(54, fd, 0x7704, 0, 0, 0) != 4096) fail("ashmem size ioctls");
    close_device(fd); say("ASHMEM-SIZE-OK\n");
    /* devtmpfs names; Android ueventd later creates /dev/log/main. */
    fd = open_device("/dev/log_main");
    if (syscall5(54, fd, 0xae05, 0, 0, 0) != 1) fail("logger version 1");
    if (syscall5(4, fd, (long)log_entry, sizeof(log_entry) - 1, 0, 0) != (long)(sizeof(log_entry) - 1))
        fail("logger write");
    close_device(fd); say("ANDROID-LOGGER-V1-OK\n");
    fd = open_device("/dev/fb0"); close_device(fd);
    say("FRAMEBUFFER-DEVICE-OK\nANDROID-ABI-PROBE-PASS\n");
    syscall5(88, 0xfee1deadUL, 672274793, 0x4321fedcUL, 0, 0);
    fail("poweroff returned");
}
