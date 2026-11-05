/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Runs as a real AOSP init service, statically linked to AOSP Bionic/cutils/log. */
#include <fcntl.h>
#include <stdint.h>
#include "binder.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/types.h>
#include <android/log.h>
#include <cutils/android_reboot.h>
#include <cutils/ashmem.h>
#include <cutils/properties.h>
static void fail(const char *reason) { printf("AOSP-NATIVE-FAIL %s\n", reason); exit(1); }
/* A real cross-process transaction to the original AOSP context manager.
 * This tests its CHECK_SERVICE protocol for an intentionally absent name;
 * it does not claim service registration or Android framework boot. */
static void check_service_manager(void)
{
    struct binder_state *bs = binder_open(128 * 1024);
    unsigned attempt;
    if (!bs) fail("service-manager Binder open");
    for (attempt = 0; attempt < 50; ++attempt) {
        uint32_t buffer[128];
        struct binder_io request, reply;
        int status, valid;
        bio_init(&request, buffer, sizeof(buffer), 0);
        bio_put_uint32(&request, 0); /* StrictMode policy */
        bio_put_string16_x(&request, SVC_MGR_NAME);
        bio_put_string16_x(&request, "qemu.android.intentionally.absent");
        status = binder_call(bs, &request, &reply, BINDER_SERVICE_MANAGER, SVC_MGR_CHECK_SERVICE);
        valid = status == 0 && reply.data_avail == sizeof(uint32_t) && bio_get_uint32(&reply) == 0;
        binder_done(bs, &request, &reply);
        if (valid) {
            binder_close(bs);
            puts("AOSP-SERVICEMANAGER-IPC-OK");
            return;
        }
        usleep(100000); /* Bounded startup race; overall boot has a CI timeout. */
    }
    binder_close(bs);
    fail("service-manager CHECK_SERVICE transaction");
}
int main(void)
{
    int fd = open("/dev/ttyAMA0", O_WRONLY);
    char property[PROPERTY_VALUE_MAX];
    int version = 0;
    struct stat device;
    void *first, *second;
    if (fd < 0) fd = open("/dev/console", O_WRONLY);
    if (fd < 0) return 1;
    dup2(fd, STDOUT_FILENO); dup2(fd, STDERR_FILENO); if (fd > 2) close(fd);
    setvbuf(stdout, NULL, _IONBF, 0);
    printf("AOSP-BIONIC-SERVICE pid=%d ppid=%d\n", getpid(), getppid());
    if (getppid() != 1) fail("service not parented by Android init");
    if (stat("/dev/kmsg", &device) < 0 || !S_ISCHR(device.st_mode))
        fail("kmsg must be a character device, not an init-created regular file");
    puts("AOSP-KMSG-DEVICE-OK");
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
    puts("AOSP-LIBLOG-WRITE-OK");
    check_service_manager();
    puts("AOSP-NATIVE-USERSPACE-PASS");
    android_reboot(ANDROID_RB_POWEROFF, 0, NULL);
    fail("Android poweroff returned");
    return 1;
}
