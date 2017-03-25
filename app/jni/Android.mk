LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(LOCAL_PATH)/lib/libcrypto.so
LOCAL_MODULE := libcrypto
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(LOCAL_PATH)/lib/libssl.so
LOCAL_MODULE := libssl
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(LOCAL_PATH)/lib/librtmp.so
LOCAL_MODULE := librtmp
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := librtmp_jni
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_SRC_FILES = $(LOCAL_PATH)/librtmp_jni.c
LOCAL_ARM_MODE := arm
LOCAL_LDLIBS := -lz -llog
LOCAL_SHARED_LIBRARIES := librtmp
APP_OPTIM := release
include $(BUILD_SHARED_LIBRARY)

