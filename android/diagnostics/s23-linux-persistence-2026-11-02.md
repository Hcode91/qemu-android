# Owner-supplied Galaxy S23 Linux persistence evidence

Received 2026-11-02 in the project conversation. These are selected, unchanged
lines from the owner's pasted guest serial output, not generated boot messages
or a CI device test. Earlier screenshot evidence showed PERSIST-WRITTEN on the
same fixture disk; the later pasted boot shows PERSIST-READ. The guest ext4 UUID
matches between those captures. The app build version was not included in the
log, so its exact installed commit is not independently established here.

```text
[    0.000000] Linux version 6.18.7 (runner@runnervm48cm3) (arm-buildroot-linux-musleabihf-gcc.br_real (Buildroot -g7fdeadb3) 15.3.0, GNU ld (GNU Binutils) 2.45.1) #1 SMP Mon Nov  2 19:26:28 UTC 2026
[    0.000000] OF: fdt: Machine model: linux,dummy-virt
[    0.066199] smp: Brought up 1 node, 2 CPUs
[    1.160738] virtio_blk virtio0: [vda] 131072 512-byte logical blocks (67.1 MB/64.0 MiB)
[    1.982492] Run /init as init process
QEMU-ANDROID-USERSPACE
[    2.169114] EXT4-fs (vda): mounted filesystem 61de2c74-5075-4f58-bcf0-279267ad6d2b r/w with ordered data mode. Quota mode: disabled.
PERSIST-READ
[    2.276298] EXT4-fs (vda): unmounting filesystem 61de2c74-5075-4f58-bcf0-279267ad6d2b.
QEMU-ANDROID-SHUTDOWN
[    2.317925] reboot: Power down
```

This establishes kernel execution, guest userspace, virtio block access, reading
previously written persistent data and the guest's unmount/power-down path for
this fixture on the owner's device. This pasted capture contains one complete
boot. No repeated restart, kernel panic or reboot loop is shown in it.

Not established: native process exit code, timing/performance measurements,
graphics, controllers, audio, networking, general guest-image compatibility,
Android guest boot or OUYA support. Kernel timestamps are guest timestamps,
not a wall-clock performance benchmark. No full security clearance is implied.
