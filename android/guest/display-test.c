/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Real Linux framebuffer/evdev fixture, no host-generated guest pixels. */
#include <fcntl.h>
#include <glob.h>
#include <linux/fb.h>
#include <linux/input.h>
#include <poll.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>

static uint32_t channel(unsigned int value, struct fb_bitfield f)
{
    return (value >> (8 - f.length)) << f.offset;
}

int main(void)
{
    int fb = -1;
    struct fb_var_screeninfo v;
    struct fb_fix_screeninfo f;
    struct pollfd inputs[16];
    unsigned int count = 0, phase = 0;
    glob_t paths;
    setvbuf(stdout, NULL, _IOLBF, 0);
    for (int attempt = 0; attempt < 150 && fb < 0; attempt++) {
        fb = open("/dev/fb0", O_RDWR | O_CLOEXEC);
        if (fb < 0) usleep(100000);
    }
    if (fb < 0 || ioctl(fb, FBIOGET_VSCREENINFO, &v) || ioctl(fb, FBIOGET_FSCREENINFO, &f)) {
        perror("framebuffer open/info"); return 1;
    }
    if ((v.bits_per_pixel != 16 && v.bits_per_pixel != 32) || !v.xres || !v.yres ||
        v.xres > 2048 || v.yres > 2048 || v.xres_virtual > 4096 || v.yres_virtual > 4096 ||
        v.red.length < 1 || v.red.length > 8 || v.green.length < 1 || v.green.length > 8 ||
        v.blue.length < 1 || v.blue.length > 8 || v.red.offset > 24 || v.green.offset > 24 || v.blue.offset > 24 ||
        v.transp.length > 8 || v.transp.offset > 24 ||
        v.xoffset > 4096 || v.yoffset > 4096 ||
        (uint64_t)(v.xoffset + v.xres) * (v.bits_per_pixel / 8) > f.line_length ||
        !f.smem_len || f.smem_len > 64U * 1024 * 1024 || f.line_length > 65536 ||
        (uint64_t)(v.yoffset + v.yres - 1) * f.line_length + (uint64_t)(v.xoffset + v.xres) * (v.bits_per_pixel / 8) > f.smem_len) {
        fprintf(stderr, "Unsupported framebuffer layout\n"); close(fb); return 1;
    }
    unsigned char *memory = mmap(NULL, f.smem_len, PROT_READ | PROT_WRITE, MAP_SHARED, fb, 0);
    if (memory == MAP_FAILED) { perror("framebuffer mmap"); close(fb); return 1; }
    memset(&paths, 0, sizeof(paths));
    if (glob("/dev/input/event*", 0, NULL, &paths) == 0) {
        for (size_t i = 0; i < paths.gl_pathc && count < 16; i++) {
            int fd = open(paths.gl_pathv[i], O_RDONLY | O_NONBLOCK | O_CLOEXEC);
            if (fd >= 0) { inputs[count].fd = fd; inputs[count].events = POLLIN; inputs[count].revents = 0; count++; }
        }
    }
    globfree(&paths);
    if (!count) {
        fprintf(stderr, "No guest input devices available\n");
        munmap(memory, f.smem_len); close(fb); return 1;
    }
    printf("DISPLAY-READY width=%u height=%u bpp=%u inputs=%u\n", v.xres, v.yres, v.bits_per_pixel, count);
    int running = 1, redraw = 1;
    while (running) {
        if (redraw) {
            for (unsigned int y = 0; y < v.yres; y++) {
                for (unsigned int x = 0; x < v.xres; x++) {
                    unsigned int band = x * 6 / v.xres;
                    unsigned int red = (band == 0 || band == 3 || band == 5) ? 220 : 25;
                    unsigned int green = (band == 1 || band == 3 || band == 4) ? 220 : 25;
                    unsigned int blue = (band == 2 || band == 4 || band == 5) ? 220 : 25;
                    if (y < v.yres / 4) { red = (phase * 71 + x / 8) & 255; green = (phase * 37 + y) & 255; blue = (phase * 103) & 255; }
                    uint32_t pixel = channel(red, v.red) | channel(green, v.green) | channel(blue, v.blue) | channel(255, v.transp);
                    size_t offset = (size_t)(y + v.yoffset) * f.line_length + (size_t)(x + v.xoffset) * (v.bits_per_pixel / 8);
                    memcpy(memory + offset, &pixel, v.bits_per_pixel / 8);
                }
            }
            redraw = 0;
        }
        int ready = poll(inputs, count, 100);
        if (ready < 0) { perror("input poll"); running = 0; continue; }
        for (unsigned int i = 0; i < count; i++) {
            if (!(inputs[i].revents & POLLIN)) continue;
            struct input_event event;
            while (read(inputs[i].fd, &event, sizeof(event)) == (ssize_t)sizeof(event)) {
                if (event.type == EV_KEY) {
                    printf("INPUT-KEY code=%u value=%d\n", event.code, event.value);
                    if (event.value == 1) { phase++; redraw = 1; }
                    if (event.code == KEY_POWER && event.value == 1) running = 0;
                } else if (event.type == EV_ABS) {
                    printf("INPUT-ABS code=%u value=%d\n", event.code, event.value);
                }
            }
        }
    }
    for (unsigned int i = 0; i < count; i++) close(inputs[i].fd);
    munmap(memory, f.smem_len); close(fb);
    return 0;
}
