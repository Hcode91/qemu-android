# SPDX-License-Identifier: GPL-2.0-or-later
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := qemu_android_native_probe
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := native-probe.c binder.c
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_MODULE_PATH := $(TARGET_ROOT_OUT)/sbin
LOCAL_STATIC_LIBRARIES := libcutils liblog libc
include $(BUILD_EXECUTABLE)

# Original AOSP service-manager implementation, statically linked for the tiny
# rootfs. Its source bytes are verified against the pinned release project.
include $(CLEAR_VARS)
LOCAL_MODULE := qemu_android_servicemanager
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := service_manager.c binder.c
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_MODULE_PATH := $(TARGET_ROOT_OUT)/sbin
LOCAL_STATIC_LIBRARIES := liblog libc
include $(BUILD_EXECUTABLE)
