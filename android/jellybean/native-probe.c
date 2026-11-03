/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Runs as a real AOSP init service, statically linked to AOSP Bionic/cutils/log. */
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>
#include <sys/types.h>
#include <android/log.h>
#include <cutils/android_reboot.h>
#include <cutils/ashmem.h>
#include <cutils/properties.h>
static void fail(const char *reason) { printf("AOSP-NATIVE-FAIL %s\n", reason); exit(1); }
int main(void)
{
    int fd = open("/dev/ttyAMA0", O_WRONLY);
    char property[PROPERTY_VALUE_MAX];
    int version = 0;
    void *first, *second;
    if (fd < 0) fd = open("/dev/console", O_WRONLY);
    if (fd < 0) return 1;
    dup2(fd, STDOUT_FILENO); dup2(fd, STDERR_FILENO); if (fd > 2) close(fd);
    setvbuf(stdout, NULL, _IONBF, 0);
    printf("AOSP-BIONIC-SERVICE pid=%d ppid=%d\n", getpid(), getppid());
    if (getppid() != 1) fail("service not parented by Android init");
    if (property_get("qemu.android.native_boot", property, "") <= 0 || strcmp(property, "ready"))
        fail("Android property area");
    puts("AOSP-PROPERTY-READ-OK");
    fd = open("/dev/binder", O_RDWR);
    if (fd < 0 || ioctl(fd, 0xc0046209UL, &version) < 0 || version != 7) fail("Binder protocol 7");
    close(fd); puts("AOSP-BIONIC-BINDER-OK");
    fd = ashmem_create_region("qemu-aosp-native", 4096);
    if (fd < 0) fail("cutils ashmem allocation");
    first = mmap(NULL, 4096, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    second = mmap(NULL, 4096, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (first == MAP_FAILED || second == MAP_FAILED) fail("Bionic ashmem mmap");
    strcpy((char *)first, "AOSP shared memory");
    if (strcmp((char *)second, "AOSP shared memory")) fail("ashmem mapping visibility");
    munmap(first, 4096); munmap(second, 4096); close(fd);
    puts("AOSP-BIONIC-ASHMEM-OK");
    if (__android_log_write(ANDROID_LOG_INFO, "QemuAosp", "native service booted") < 0)
        fail("Android liblog write");
    puts("AOSP-LIBLOG-WRITE-OK\nAOSP-NATIVE-USERSPACE-PASS");
    android_reboot(ANDROID_RB_POWEROFF, 0, NULL);
    fail("Android poweroff returned");
    return 1;
}
