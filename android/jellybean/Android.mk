# SPDX-License-Identifier: GPL-2.0-or-later
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := qemu_android_native_probe
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := native-probe.c
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_MODULE_PATH := $(TARGET_ROOT_OUT)/sbin
LOCAL_STATIC_LIBRARIES := libcutils liblog libc
include $(BUILD_EXECUTABLE)
