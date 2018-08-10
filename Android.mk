LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# Include res dir from chips
chips_dir := ../../../frameworks/opt/chips/res
color_picker_dir := ../../../frameworks/opt/colorpicker/res
timezonepicker_dir := ../../../frameworks/opt/timezonepicker/res
res_dirs := $(chips_dir) $(color_picker_dir) $(timezonepicker_dir) res
src_dirs := src

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under,$(src_dirs))

LOCAL_STATIC_ANDROID_LIBRARIES := \
    android-support-v4 \
    android-support-v7-appcompat

LOCAL_STATIC_JAVA_LIBRARIES := \
        android-common \
        libchips \
        colorpicker \
        android-opt-timezonepicker \
        calendar-common

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 24

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

LOCAL_PACKAGE_NAME := Calendar

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_USE_AAPT2 := true

LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_FLAGS += --extra-packages com.android.ex.chips
LOCAL_AAPT_FLAGS += --extra-packages com.android.colorpicker
LOCAL_AAPT_FLAGS += --extra-packages com.android.timezonepicker

include $(BUILD_PACKAGE)

